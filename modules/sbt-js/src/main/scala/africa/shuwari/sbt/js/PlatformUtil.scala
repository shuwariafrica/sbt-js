/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package africa.shuwari.sbt.js

import java.util.concurrent.TimeUnit

import scala.util.Properties

import sbt.Logger

/** Cross-platform helpers for locating node-related executables and terminating process trees robustly.
  */
private[sbt] object PlatformUtil {

  val isWindows: Boolean = Properties.isWin

  def executableName(command: String): String =
    if (isWindows) {
      command match {
        case c if c.endsWith(".exe") || c.endsWith(".cmd") || c.endsWith(".bat") => c
        case "node"                                                              => "node.exe"
        case other => s"$other.cmd" // npm places shim .cmd files under node_modules/.bin
      }
    } else command

  val node: String = executableName("node")

  /** Attempt graceful then forced termination of a process and its descendants.
    */
  def processDestroy(process: Option[java.lang.Process], log: Logger): Unit =
    process.foreach { p =>
      if (p.isAlive) {
        val pid = p.pid()
        try {
          log.debug(s"Terminating process tree (pid=$pid)...")
          // Graceful first
          p.descendants().forEach { ph => ph.destroy(); () }
          p.destroy()
          if (!p.waitFor(5, TimeUnit.SECONDS)) {
            log.warn(s"Process (pid=$pid) did not exit gracefully; forcing...")
            p.descendants().forEach { ph => ph.destroyForcibly(); () }
            p.destroyForcibly()
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
              log.error(s"Process (pid=$pid) may still be alive after forceful termination")
            }
          }
        } catch {
          case e: Exception =>
            log.error(s"Error terminating process (pid=$pid): ${e.getMessage}")
            try p.destroyForcibly()
            catch { case _: Throwable => () }
        }
      }
    }
}
