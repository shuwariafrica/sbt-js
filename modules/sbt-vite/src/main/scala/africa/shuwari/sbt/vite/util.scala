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

import java.lang
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import sbt.*
import sbt.util.Level
import sbt.util.Logger

object Util {

  final val windows = System.getProperty("os.name").toLowerCase.contains("win")
  final val nodeExecutable = if (windows) "node.exe" else "node"

  def viteLogLevel(level: Level.Value) =
    level match {
      case Level.Info  => "info"
      case Level.Warn  => "warn"
      case Level.Error => "error"
      case _           => "info"
    }

  def processDestroy(processes: Iterable[java.lang.Process], logger: Logger): Unit =
    processes
      .foreach { p =>
        if (p.isAlive) {
          logger.debug(s"Awaiting process termination: ${p.pid}")
          def destroy(handler: ProcessHandle): Unit = {
            handler.destroy()
            ()
          }
          val descendents = p.descendants()
          descendents.iterator.asScala.foreach(destroy)
          descendents.close()
          p.destroy()
          p.waitFor(500, TimeUnit.MILLISECONDS)
          p.destroy()
          p.getErrorStream.close()
          p.getOutputStream.close()
          p.getInputStream.close()
          p.exitValue
          logger.debug(s"Stopped process: ${p.pid}")
        }
      }

  def processRun(commands: List[String],
                 env: Map[String, String],
                 workingDirectory: File,
                 logger: Logger): lang.Process = {
    val finalCommands = {

      def prependWindowsShell(commands: List[String], logger: Logger): List[String] =
        if (!windows) commands
        else {
          logger.debug("Operating in Windows environment.")

          def pwsh = Try(new ProcessBuilder("pwsh.exe -version").start) match {
            case Success(p) =>
              p.destroy()
              p.waitFor(500, TimeUnit.MILLISECONDS)
              true
            case Failure(_) => false
          }

          val windowsShell = if (pwsh) "pwsh.exe" else "powershell.exe"
          logger.debug(s"""Using "$windowsShell" as shell environment.""")
          windowsShell +: ("-Command" +: commands)
        }

      val cmd = prependWindowsShell(commands, logger)
      logger.debug(s"Using process commands and parameters:${cmd.mkString(" ")}")
      cmd
    }
    val pb = new java.lang.ProcessBuilder(finalCommands*)
    env.foreach(pair => pb.environment().put(pair._1, pair._2))
    pb.directory(workingDirectory)
    pb.redirectOutput(Redirect.INHERIT)
    pb.redirectError(Redirect.INHERIT)
    logger.debug(s"Starting external process with commands: ${pb.command.asScala.mkString("\"", " ", "\"")}")
    logger.debug(s"Using external process environment:  ${pb.environment.asScala
        .map { case (k, v) => s"$k=$v${System.lineSeparator()}" }
        .mkString(" ")}")
    pb.environment().asScala.foreach { case (k, v) => logger.debug(s"$k=$v") }
    logger.debug(s"""Using external process working directory:  "${pb.directory.getAbsolutePath}"""")
    pb.start()
  }

}
