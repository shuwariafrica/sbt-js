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

import africa.shuwari.sbt.js.JSImports as js
import sbt.Keys.*
import sbt.{Util as _, *}

import java.io.File.pathSeparator
import java.util.concurrent.atomic.AtomicReference

object DefaultSettings {

  def projectSettings: List[Setting[?]] = plugin ++ common ++ run ++ build

  def plugin: List[Setting[?]] = List(
    ViteImport.viteExecutable := viteExecutable.value,
    ViteImport.viteBuild := Def
      .taskDyn {
        val processTarget = (ViteImport.viteBuild / target).value
        val logger = streams.value.log
        val parameters = "--emptyOutDir true" +: CliParameter.buildParameters.value
        Def.task {
          val process = viteProcess(Some("build"), parameters).value
          logger.info(s"Started Vite Build process: ${process.pid}")
          process.waitFor()
          logger.info(s"Completed  Vite Build process: ${process.pid}")
          processTarget
        }
      }
      .dependsOn(js.assemble)
      .value,
    ViteImport.viteProcessInstance := new AtomicReference(None),
    Global / onLoad := {
      def exitHook(s: State): State =
        s.addExitHook(Util.processDestroy((ThisProject / ViteImport.viteProcessInstance).value.getAndSet(None), s.log))
      (Global / onLoad).value.compose(exitHook)
    },
    ViteImport.vite := Def.taskDyn {
      val reference = ViteImport.viteProcessInstance.value
      val logger = streams.value.log
      val parameters =
        CliParameter.devServerParameters.value // FIXME Notify if existing process used
      Def
        .task {
          if (reference.get.isEmpty) {
            val process = viteProcess(None, parameters).value
            logger.info(s"Started Vite process: ${process.pid}")
            reference.set(Some(process))
          } else {
            logger.info(s"Vite process active: ${reference.get.get.pid}")
          }
        }
        .dependsOn(js.assemble)
    }.value,
    ViteImport.viteStop := Util.processDestroy(
      ViteImport.viteProcessInstance.value.getAndSet(None),
      streams.value.log
    )
  )

  private def common: List[Setting[?]] = List(
    ViteImport.base := None,
    ViteImport.config := None,
    ViteImport.force := None,
    ViteImport.logLevel := Level.Info,
    ViteImport.mode := (if (js.fullLink.value)
                          ViteImport.Mode.Production
                        else ViteImport.Mode.Development)
  )

  private def run: List[Setting[?]] = List(
    ViteImport.host := None,
    ViteImport.port := None,
    ViteImport.strictPort := None,
    ViteImport.cors := None
  )

  private def build: List[Setting[?]] = List(
    ViteImport.viteBuild / target := (js.js / target).value,
    ViteImport.assetsDir := None,
    ViteImport.assetsInlineLimit := None,
    ViteImport.ssr := None,
    ViteImport.sourcemap := None,
    ViteImport.minify := None
  )

  private def viteProcess(command: Option[String], parameters: List[String]): Def.Initialize[Task[java.lang.Process]] =
    Def.taskDyn {
      val source = (js.assemble / target).value
      Def.task(
        Util.processRun(
          ViteImport.viteExecutable.value ++ (command.toList ++: parameters),
          defaultExtraEnv(ViteImport.mode.value, nodeModulesBasePaths.value),
          source,
          (ThisProject / ViteImport.vite / streams).value.log
        )
      )
    }

  private def viteExecutable: Def.Initialize[Task[List[String]]] = Def.task {
    if (!Util.windows) List("vite")
    else {
      val viteScriptPaths = nodeModulesBasePaths.value.map(_ / "node_modules" / "vite" / "bin" / "vite.js")
      val viteScript = viteScriptPaths.find(_.exists()).map(_.getAbsolutePath)
      viteScript match {
        case Some(s) => List(Util.nodeExecutable, s)
        case _       => sys.error(Messages.viteScriptNotFound(viteScriptPaths))
      }
    }
  }

  private def nodeModulesBasePaths = Def.setting(
    Set((js.assemble / target).value, (ThisProject / baseDirectory).value, (LocalRootProject / baseDirectory).value)
  )

  private def defaultExtraEnv(
    mode: ViteImport.Mode,
    nodeModulesBasePaths: Iterable[File]
  ): Map[String, String] = {
    def nodeEnv = if (mode == ViteImport.Mode.Production) Some("NODE_ENV" -> "production") else None
    def appendPathEnv = Some {
      val pathEnv = if (Util.windows) "Path" else "PATH"
      def path = System.getenv(pathEnv)
      def binDir(base: File) = base / "node_modules" / ".bin"
      def nodePaths = nodeModulesBasePaths.map(binDir(_).getAbsolutePath)
      def paths = s"${nodePaths.mkString(pathSeparator)}$pathSeparator$path"
      pathEnv -> paths
    }
    List(nodeEnv, appendPathEnv).flatten
  }.toMap

  private case class CliParameter[A: CliParameter.Encoder](
    key: String,
    value: Option[A]
  ) {
    def resolve: String =
      value
        .map(v => s"""--$key ${implicitly[CliParameter.Encoder[A]].apply(v)}""")
        .getOrElse("")
  }

  private object CliParameter {
    sealed trait Encoder[A] extends (A => String)

    object Encoder {

      def apply[A](f: A => String): Encoder[A] = new Encoder[A] {
        def apply(v: A): String = f(v)
      }

      implicit def boolean: Encoder[Boolean] =
        Encoder(v => if (v) "true" else "false")

      implicit def string: Encoder[String] =
        Encoder(str => str)

      implicit def number: Encoder[Int] = Encoder(_.toString)

      implicit def logLevel: Encoder[Level.Value] = Encoder(
        Util.viteLogLevel
      )

      implicit def minifier: Encoder[ViteImport.Minifier] =
        Encoder(
          ViteImport.Minifier.resolve
        )

      implicit def mode: Encoder[ViteImport.Mode] = Encoder(
        ViteImport.Mode.resolve
      )

      implicit def file: Encoder[File] = Encoder(
        _.getAbsolutePath
      )

    }

    def apply[A: Encoder](pair: (String, Option[A])): CliParameter[A] =
      CliParameter(pair._1, pair._2)(implicitly[Encoder[A]])

    private def resolve(col: List[CliParameter[?]]): List[String] =
      col.map(_.resolve).filter(_.nonEmpty)

    private def commonCliParameters: Def.Initialize[Task[List[CliParameter[?]]]] = Def.taskDyn {
      val base = CliParameter("base" -> ViteImport.base.value)
      val config = CliParameter("config" -> ViteImport.config.value)
      val force = CliParameter("force" -> ViteImport.force.value)
      val logLevel = CliParameter("logLevel", Some(ViteImport.logLevel.value))
      val mode = CliParameter("mode", Some(ViteImport.mode.value))
      val clearScreen = CliParameter("clearScreen", Some(false))
      Def.task(List(base, config, force, logLevel, mode, clearScreen))
    }

    def devServerParameters: Def.Initialize[Task[List[String]]] =
      Def.taskDyn {
        val common = commonCliParameters.value
        val host = CliParameter("host" -> ViteImport.host.value)
        val port = CliParameter("port" -> ViteImport.port.value)
        val strictPort = CliParameter("strictPort" -> ViteImport.strictPort.value)
        val cors = CliParameter("cors", ViteImport.cors.value)
        Def.task(resolve(common ++ List(host, port, strictPort, cors)))
      }

    def buildParameters: Def.Initialize[Task[List[String]]] =
      Def.taskDyn {
        val common = commonCliParameters.value

        val assetsDir = CliParameter("assetsDir" -> ViteImport.assetsDir.value)
        val assetsInlineLimit = CliParameter(
          "assetsInlineLimit" -> ViteImport.assetsInlineLimit.value
        )
        val ssr = CliParameter("ssr" -> ViteImport.ssr.value)
        val sourcemap = CliParameter("sourcemap" -> ViteImport.sourcemap.value)
        val outDir = CliParameter(
          "outDir" -> Some((js.js / sbt.Keys.target).value)
        )

        Def.task(
          resolve(
            common ++ List(assetsDir, assetsInlineLimit, ssr, sourcemap, outDir)
          )
        )

      }

  }

}
