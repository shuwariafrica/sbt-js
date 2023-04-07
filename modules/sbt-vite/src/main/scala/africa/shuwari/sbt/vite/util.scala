/** *************************************************************** Copyright © Shuwari Africa Ltd. All rights reserved.
  * * * Shuwari Africa Ltd. licenses this file to you under the terms * of the Apache License Version 2.0 (the
  * "License"); you may * not use this file except in compliance with the License. You * may obtain a copy of the
  * License at: * * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in
  * writing, * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR
  * CONDITIONS OF ANY KIND, * either express or implied. See the License for the specific * language governing
  * permissions and limitations under the * License. *
  */
package africa.shuwari.sbt
package vite

import sbt.util.Logger
import scala.sys.process._
import java.io.File
import sbt.util.Level

object Util {

  // final val windows = System.getProperty("os.name").toLowerCase.contains("win")

  def classNameToString(cls: Class[_]) = cls.getSimpleName
    .filterNot(_ == '$')
    .toLowerCase

  def viteLogLevel(level: Level.Value) =
    level match {
      case Level.Info  => "info"
      case Level.Warn  => "warn"
      case Level.Error => "error"
      case _           => "info"
    }

  def process(
    commands: List[String],
    env: Map[String, String],
    workingDirectory: File,
    logger: Logger
  ): Process = {
    val cmd =
      if (System.getProperty("os.name").toLowerCase.contains("win"))
        List("powershell.exe", "-Command") ++ commands
      else commands

    scala.sys.process
      .Process(cmd, workingDirectory, env.toList: _*)
      .run(ProcessLogger(fout => logger.info(fout), ferr => logger.error(ferr)))
  }

}
