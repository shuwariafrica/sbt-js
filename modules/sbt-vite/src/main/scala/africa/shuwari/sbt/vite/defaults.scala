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

import sbt.Keys._
import sbt.{Util => _, _}
import java.io.File.pathSeparator

import JSBundlerPlugin.{autoImport => js}
import scala.sys.process._
import java.util.concurrent.atomic.AtomicReference

object DefaultSettings {

  def apply() = plugin ++ common ++ run ++ build

  def pluginGlobal = List(
    onLoad := ((s: State) =>
      s.addExitHook(
        shutdownProcesses(
          (ThisProject / ViteKeys.viteProcessInstances).value
            .getAndSet(List.empty),
          s.log
        )
      ))
  )

  def plugin = List(
    ViteKeys.viteExecutable := viteCommand(
      ViteKeys.useNpx.value,
      ViteKeys.viteVersion.value.getOrElse("latest")
    ),
    ViteKeys.useNpx := false,
    ViteKeys.viteVersion := None,
    ViteKeys.viteBuild := Def
      .taskDyn {
        val processTarget = (ViteKeys.viteBuild / target).value
        val logger = streams.value.log
        val parameters = CliParameter.buildParameters.value
        Def.task {
          val process = viteBuildProcess(parameters).value
          logger.info(s"Started Vite Build process: ${process}")
          process.exitValue
          logger.info(s"Completed  Vite Build process: ${process}")
          processTarget
        }
      }
      .dependsOn(JSBundlerPlugin.autoImport.jsPrepare)
      .value,
    ViteKeys.viteProcessInstances := new AtomicReference(List.empty),
    // ViteKeys.viteBuild := viteBuildProcess(CliParameter.devServer.value),
    ViteKeys.vite := Def.taskDyn {
      val reference = ViteKeys.viteProcessInstances.value
      val logger = streams.value.log
      val parameters =
        CliParameter.devServerParameters.value
      Def.task {
        val process = viteDevProcess(parameters).value
        shutdownProcesses(reference.getAndSet(List.empty), logger)
        logger.info(s"Started Vite process: ${process}")
        reference.set(List(process))
      }
    }.value,
    ViteKeys.viteStop := shutdownProcesses(
      ViteKeys.viteProcessInstances.value.getAndSet(List.empty),
      streams.value.log
    )
  )

  def common = List(
    ViteKeys.base := None,
    ViteKeys.config := None,
    ViteKeys.force := None,
    ViteKeys.logLevel := Level.Info,
    ViteKeys.mode := (if (js.jsFullLink.value)
                        ViteKeys.Mode.Production
                      else ViteKeys.Mode.Development)
  )

  def run = List(
    ViteKeys.host := None,
    ViteKeys.port := None,
    ViteKeys.strictPort := None,
    ViteKeys.cors := None
  )

  def build = List(
    ViteKeys.viteBuild / target := js.jsTarget.value,
    ViteKeys.assetsDir := None,
    ViteKeys.assetsInlineLimit := None,
    ViteKeys.ssr := None,
    ViteKeys.sourcemap := None,
    ViteKeys.minify := None
  )

  private def viteDevProcess(parameters: List[String]) =
    viteProcess("", parameters)

  private def viteBuildProcess(parameters: List[String]) =
    viteProcess("build", parameters)

  private def viteProcess(command: String, parameters: List[String]) =
    Def.taskDyn {
      val executable = ViteKeys.viteExecutable.value
      val source = js.jsPrepareTarget.value
      val logger = streams.value.log
      val env = defaultEnv(
        sys.env,
        ViteKeys.mode.value,
        ViteKeys.useNpx.value,
        source,
        (ThisProject / baseDirectory).value,
        (LocalRootProject / baseDirectory).value
      )
      Def.task(
        process(
          executable,
          command,
          parameters,
          env,
          source,
          logger
        )
      )
    }

  private def shutdownProcesses(processes: List[Process], logger: Logger) =
    processes
      .foreach { p =>
        logger.info(s"Stopping Vite process: $p")
        p.destroy()
        p.exitValue
        logger.info(s"Stopped Vite process: $p")
      }

  private def viteCommand(useNpx: Boolean, viteVersion: String): List[String] =
    if (useNpx) List("npx", "--yes", s"vite@$viteVersion") else List("vite")

  private def defaultEnv(
    env: Map[String, String],
    mode: ViteKeys.Mode,
    useNpx: Boolean,
    preparedAppDirectory: File,
    projectDirectory: File,
    rootDirectory: File
  ): Map[String, String] = {
    def nodeEnv = if (mode == ViteKeys.Mode.Production)
      env + ("NODE_ENV" -> "production")
    else env
    if (useNpx) nodeEnv
    else {
      def path = env.get("PATH").get
      def binDir(base: File) = base / "node_modules" / ".bin"
      def binPaths = List(preparedAppDirectory, projectDirectory, rootDirectory)
        .map(binDir)
        .mkString("", pathSeparator, pathSeparator)
      nodeEnv + ("PATH" -> (binPaths + path))
    }
  }

  private def process(
    executable: List[String],
    command: String,
    parameters: List[String],
    env: Map[String, String],
    workingDirectory: File,
    logger: Logger
  ): Process = {
    def processLogger(logger: Logger) =
      ProcessLogger(fout => logger.info(fout), ferr => logger.error(ferr))

    val commandList =
      if (System.getProperty("os.name").toLowerCase.contains("win"))
        List(
          "powershell.exe",
          "-Command"
        ) ++ executable ++ (command +: parameters)
      else List("sh", "-c") ++ (executable ++ (command +: parameters))

    logger.info(env.mkString("\n"))

    logger.info(commandList.mkString("\n\n"))

    Process(commandList, workingDirectory, env.toList: _*)
      .run(processLogger(logger))
  }

  sealed private trait ParameterDecoder[A] extends (A => String)

  private object ParameterDecoder {

    def apply[A](f: (A => String)) = new ParameterDecoder[A] {
      def apply(v: A): String = f(v)
    }

    implicit def boolean: ParameterDecoder[Boolean] =
      ParameterDecoder(v => if (v == true) "true" else "false")

    implicit def string: ParameterDecoder[String] =
      ParameterDecoder((str: String) => str)

    implicit def number: ParameterDecoder[Int] = ParameterDecoder(_.toString)

    implicit def logLevel: ParameterDecoder[Level.Value] = ParameterDecoder(
      Util.viteLogLevel
    )

    implicit def minifier: ParameterDecoder[ViteKeys.Minifier] =
      ParameterDecoder(
        ViteKeys.Minifier.resolve
      )

    implicit def mode: ParameterDecoder[ViteKeys.Mode] = ParameterDecoder(
      ViteKeys.Mode.resolve
    )

    implicit def file: ParameterDecoder[File] = ParameterDecoder(
      _.getAbsolutePath
    )

  }

  private case class CliParameter[A: ParameterDecoder](
    key: String,
    value: Option[A]
  ) {
    def resolve =
      value
        .map(v => s"$key ${implicitly[ParameterDecoder[A]].apply(v)}")
        .getOrElse("")
  }

  private object CliParameter {

    def apply[A: ParameterDecoder](pair: (String, Option[A])): CliParameter[A] =
      CliParameter(pair._1, pair._2)(implicitly[ParameterDecoder[A]])

    private def resolve(col: List[CliParameter[_]]) =
      col.map(_.resolve).filter(_.nonEmpty)

    def commonCliParameters = Def.taskDyn {
      val base = CliParameter("base" -> ViteKeys.base.value)
      val config = CliParameter("config" -> ViteKeys.config.value)
      val force = CliParameter("force" -> ViteKeys.force.value)
      val logLevel = CliParameter("logLevel", Some(ViteKeys.logLevel.value))
      val mode = CliParameter("mode", Some(ViteKeys.mode.value))
      val clearScreen = CliParameter("clearScreen", Some(false))
      Def.task(List(base, config, force, logLevel, mode, clearScreen))
    }

    def devServerParameters =
      Def.taskDyn {
        val common = commonCliParameters.value
        val host = CliParameter("host" -> ViteKeys.host.value)
        val port = CliParameter("port" -> ViteKeys.port.value)
        val strictPort = CliParameter("strictPort" -> ViteKeys.strictPort.value)
        val cors = CliParameter("cors", ViteKeys.cors.value)
        Def.task(resolve(common ++ List(host, port, strictPort, cors)))
      }

    def buildParameters =
      Def.taskDyn {
        val common = commonCliParameters.value

        val assetsDir = CliParameter("assetsDir" -> ViteKeys.assetsDir.value)
        val assetsInlineLimit = CliParameter(
          "assetsInlineLimit" -> ViteKeys.assetsInlineLimit.value
        )
        val ssr = CliParameter("ssr" -> ViteKeys.ssr.value)
        val sourcemap = CliParameter("sourcemap" -> ViteKeys.sourcemap.value)
        val target = CliParameter(
          "target" -> Some((JSBundlerPlugin.autoImport.js / sbt.Keys.target).value)
        )

        Def.task(
          resolve(
            common ++ List(assetsDir, assetsInlineLimit, ssr, sourcemap, target)
          )
        )

      }

  }

}
