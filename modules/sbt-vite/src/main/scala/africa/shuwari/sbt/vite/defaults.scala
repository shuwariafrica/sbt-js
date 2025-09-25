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
package africa.shuwari.sbt
package vite

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

import sbt.Keys.*
import sbt.{Util as _, *}

import africa.shuwari.sbt.js.JSImports as js
import africa.shuwari.sbt.js.PlatformUtil
import africa.shuwari.sbt.runner.ManagedServer
import africa.shuwari.sbt.runner.RunnerImports
import africa.shuwari.sbt.runner.RunnerToolkit
import africa.shuwari.sbt.runner.RunnerUtil

object DefaultSettings {

  private val pathEnvKey: String = if (PlatformUtil.isWindows) "Path" else "PATH"

  def projectSettings: List[Setting[?]] = plugin ++ common ++ run ++ build

  private def plugin: List[Setting[?]] = List(
    ViteImport.viteExecutable := defaultViteExecutable.value,
    ViteImport.viteBuild := viteBuildTask.value,
    ViteImport.viteProcessInstance := new AtomicReference[Option[africa.shuwari.sbt.runner.ServerState]](None),
    ViteImport.viteConfigFingerprint := viteFingerprintTask.value,
    ViteImport.viteServerTag := RunnerBridge.serverTag,
    Global / onLoad := registerServerCleanup.value,
    ViteImport.vite := viteDevServerTask.value,
    ViteImport.viteStop := viteStopTask.value
  )

  private def viteBuildTask: Def.Initialize[Task[File]] =
    Def
      .task {
        val processTarget = (ViteImport.viteBuild / target).value
        val logger = streams.value.log
        val parameters = "--emptyOutDir true" +: CliParameter.buildParameters.value
        val nodeModules = RunnerImports.jsNodeModules.value
        val env = defaultExtraEnv(ViteImport.mode.value, nodeModules)
        val workDir = (js.assemble / target).value
        val commands = appendArgs(ViteImport.viteExecutable.value, "build" :: parameters)
        val process = RunnerUtil.processRun(commands, env, workDir, logger)
        logger.info(s"Started Vite build (pid=${process.pid})")
        val exit = process.waitFor()
        if (exit != 0) sys.error(s"Vite build failed (exit code $exit)")
        logger.info(s"Completed Vite build (pid=${process.pid})")
        processTarget
      }
      .dependsOn(js.assemble)

  private def viteDevServerTask: Def.Initialize[Task[Unit]] =
    Def
      .task {
        val logger = streams.value.log
        val nodeModules = RunnerImports.jsNodeModules.value
        val projectRoot = RunnerImports.jsNodeProject.value
        val stateRef = ViteImport.viteProcessInstance.value
        val managed = new ManagedServer(
          RunnerBridge.serverNamespace,
          stateRef,
          projectRoot,
          nodeModules,
          logger
        )
        val fingerprint = ViteImport.viteConfigFingerprint.value
        val parameters = CliParameter.devServerParameters.value
        val env = defaultExtraEnv(ViteImport.mode.value, nodeModules)
        val (command, prefixArgs) = splitExecutable(ViteImport.viteExecutable.value)
        val args = prefixArgs ++ parameters
        managed.start(command, args, env, List(fingerprint))
        ()
      }
      .tag(RunnerBridge.serverTag)
      .dependsOn(js.assemble)

  private def viteStopTask: Def.Initialize[Task[Unit]] = Def.task {
    val logger = streams.value.log
    val nodeModules = RunnerImports.jsNodeModules.value
    val projectRoot = RunnerImports.jsNodeProject.value
    val stateRef = ViteImport.viteProcessInstance.value
    val managed = new ManagedServer(
      RunnerBridge.serverNamespace,
      stateRef,
      projectRoot,
      nodeModules,
      logger
    )
    managed.stop()
  }

  private def registerServerCleanup: Def.Initialize[State => State] = Def.setting {
    val previous = (Global / onLoad).value

    def exitHook(state: State): State =
      state.addExitHook {
        (ThisProject / ViteImport.viteProcessInstance).value.getAndSet(None).foreach { server =>
          state.log.info(s"Cleaning up ${RunnerBridge.serverNamespace} server on exit")
          PlatformUtil.processDestroy(Some(server.process), state.log)
        }
      }

    previous.compose(exitHook)
  }

  private def viteFingerprintTask: Def.Initialize[Task[String]] = Def.task {
    val params = CliParameter.devServerParameters.value.sorted.mkString(" ")
    val source = (js.assemble / target).value.getAbsolutePath
    val mode = ViteImport.mode.value.toString
    val combined = s"$source|$mode|$params"
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(combined.getBytes("UTF-8")).take(10).map("%02x".format(_)).mkString
  }

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

  private def defaultViteExecutable: Def.Initialize[Task[List[String]]] =
    RunnerToolkit.jsFindExecutable("vite")

  private def splitExecutable(spec: List[String]): (String, List[String]) =
    spec match {
      case head :: tail => (head, tail)
      case Nil          => ("vite", Nil)
    }

  private def appendArgs(base: List[String], extra: List[String]): List[String] = base ++ extra

  private[this] val cachedPathKey =
    new java.util.concurrent.atomic.AtomicReference[Option[(Set[File], String)]](None)

  private def defaultExtraEnv(mode: ViteImport.Mode, nodeModules: Set[File]): Map[String, String] = {
    val pathValue = cachedPathKey.get() match {
      case Some((prevBases, value)) if prevBases == nodeModules => value
      case _                                                    =>
        val computed = RunnerUtil.augmentPath(nodeModules)
        cachedPathKey.set(Some(nodeModules -> computed))
        computed
    }
    val nodeEnv = if (mode == ViteImport.Mode.Production) Map("NODE_ENV" -> "production") else Map.empty[String, String]
    nodeEnv + (pathEnvKey -> pathValue)
  }

  private case class CliParameter[A: CliParameter.Encoder](key: String, value: Option[A]) {
    import CliParameter.FlagStyle
    private val enc = implicitly[CliParameter.Encoder[A]]
    def resolve: List[String] = value match {
      case None    => Nil
      case Some(v) =>
        CliParameter.flagStyle(key) match {
          case FlagStyle.Presence =>
            v match {
              case b: java.lang.Boolean => if (b.booleanValue()) List(s"--$key") else Nil
              case other                => List(s"--$key=${enc(other)}")
            }
          case FlagStyle.Value => List(s"--$key=${enc(v)}")
        }
    }
  }

  private object CliParameter {
    sealed trait FlagStyle
    object FlagStyle { case object Presence extends FlagStyle; case object Value extends FlagStyle }
    private val flagMetadata: Map[String, FlagStyle] = Map(
      "strictPort" -> FlagStyle.Presence,
      "cors" -> FlagStyle.Presence,
      "force" -> FlagStyle.Presence,
      "clearScreen" -> FlagStyle.Presence,
      "https" -> FlagStyle.Presence,
      "emptyOutDir" -> FlagStyle.Presence
    ).withDefaultValue(FlagStyle.Value)
    def flagStyle(key: String): FlagStyle = flagMetadata(key)
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
      col.flatMap(_.resolve)

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
        val minify = CliParameter("minify" -> ViteImport.minify.value)
        val target = CliParameter("target" -> ViteImport.target.value)

        Def.task(
          resolve(
            common ++ List(assetsDir, assetsInlineLimit, ssr, sourcemap, outDir, minify, target)
          )
        )

      }

  }

}
