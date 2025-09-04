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

import java.io.File.pathSeparator
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

import sbt.Keys.*
import sbt.Tags
import sbt.{Util as _, *}

import africa.shuwari.sbt.js.JSImports as js
import africa.shuwari.sbt.js.PlatformUtil

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
          val p = viteRawProcess(Some("build"), parameters).value
          logger.info(s"Started Vite build (pid=${p.pid})")
          val exit = p.waitFor()
          if (exit != 0) sys.error(s"Vite build failed (exit code $exit)")
          logger.info(s"Completed Vite build (pid=${p.pid})")
          processTarget
        }
      }
      .dependsOn(js.assemble)
      .value,
    ViteImport.viteProcessInstance := new AtomicReference[Option[ServerState]](None),
    ViteImport.viteConfigFingerprint := Def.task {
      val params = CliParameter.devServerParameters.value.sorted.mkString(" ")
      val source = (js.assemble / target).value.getAbsolutePath
      val mode = ViteImport.mode.value.toString
      val combined = s"$source|$mode|$params"
      def hex(bytes: Array[Byte]) = bytes.map("%02x".format(_)).mkString
      val md = MessageDigest.getInstance("SHA-1")
      hex(md.digest(combined.getBytes("UTF-8")))
    }.value,
    ViteImport.viteServerTag := Tags.Tag("vite-dev-server"),
    Global / onLoad := {
      def exitHook(s: State): State =
        s.addExitHook(
          PlatformUtil.processDestroy(
            (ThisProject / ViteImport.viteProcessInstance).value.getAndSet(None).map(_.process),
            s.log
          )
        )
      (Global / onLoad).value.compose(exitHook)
    },
    ViteImport.vite := Def.taskDyn {
      val ref = ViteImport.viteProcessInstance.value
      val logger = streams.value.log
      val currentFp = ViteImport.viteConfigFingerprint.value
      val parameters = CliParameter.devServerParameters.value
      val modeVal = ViteImport.mode.value
      val env = defaultExtraEnv(modeVal, nodeModulesBasePaths.value)
      val workDir = (js.assemble / target).value
      val exec = ViteImport.viteExecutable.value
      Def
        .task {
          ref.updateAndGet {
            case Some(state) if state.process.isAlive && state.fingerprint == currentFp =>
              logger.info(s"Vite server already running (pid=${state.process.pid})")
              Some(state)
            case Some(state) if state.process.isAlive =>
              logger.info(s"Vite server configuration changed; restarting (old pid=${state.process.pid})")
              PlatformUtil.processDestroy(Some(state.process), logger)
              Some(startServerResolved(exec, parameters, currentFp, env, workDir, logger))
            case _ => Some(startServerResolved(exec, parameters, currentFp, env, workDir, logger))
          }
          ()
        }
        .tag(ViteImport.viteServerTag.value)
        .dependsOn(js.assemble)
    }.value,
    ViteImport.viteStop := {
      val ref = ViteImport.viteProcessInstance.value
      val logger = streams.value.log
      ref.getAndSet(None).foreach { st =>
        logger.info(s"Stopping Vite server (pid=${st.process.pid})")
        PlatformUtil.processDestroy(Some(st.process), logger)
      }
    }
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
  // Raw process creation used by build; dev server adds fingerprint handling
  private def viteRawProcess(command: Option[String],
                             parameters: List[String]): Def.Initialize[Task[java.lang.Process]] =
    viteProcess(command, parameters)

  private def startServerResolved(exec: List[String],
                                  parameters: List[String],
                                  fp: String,
                                  env: Map[String, String],
                                  workDir: File,
                                  logger: sbt.Logger): ServerState = {
    // Run process but also capture early stderr output for diagnostics
    val p = Util.processRun(exec ++ parameters, env, workDir, logger)
    val errStream = p.getErrorStream
    val ringSize = 4096
    val ring = new Array[Byte](ringSize)
    // Track current length of valid bytes in ring without using a mutable var (scalafix DisableSyntax.var)
    val ringLen = new java.util.concurrent.atomic.AtomicInteger(0)
    val reader = new Thread(
      () =>
        try {
          val buf = new Array[Byte](512)
          @annotation.tailrec
          def loop(currentLen: Int): Unit = {
            val n = errStream.read(buf)
            if (n == -1) ()
            else {
              val copyLen = math.min(n, buf.length)
              if (currentLen + copyLen <= ringSize) {
                System.arraycopy(buf, 0, ring, currentLen, copyLen)
                ringLen.set(currentLen + copyLen)
                loop(ringLen.get())
              } else {
                val overflow = (currentLen + copyLen) - ringSize
                // shift existing bytes left to make room
                System.arraycopy(ring, overflow, ring, 0, ringSize - overflow)
                // copy new bytes at the end segment that fits
                System.arraycopy(buf, 0, ring, ringSize - copyLen, copyLen)
                ringLen.set(ringSize) // buffer now full
                loop(ringLen.get())
              }
            }
          }
          loop(0)
        } catch {
          case _: Throwable => ()
        },
      s"vite-stderr-capture-${System.currentTimeMillis()}"
    )
    reader.setDaemon(true)
    reader.start()
    // brief delay to catch immediate failures
    Thread.sleep(400)
    if (!p.isAlive) {
      val exit = p.exitValue()
      val snippet = new String(ring, 0, ringLen.get(), "UTF-8").linesIterator.take(20).mkString("\n")
      logger.error(
        s"Vite dev server failed to start (exit=$exit)\n--- stderr (truncated) ---\n$snippet\n--------------------------")
      sys.error("Vite dev server failed to start (process exited early)")
    }
    logger.info(s"Started Vite server (pid=${p.pid})")
    ServerState(p, fp)
  }

  private def viteExecutable: Def.Initialize[Task[List[String]]] = Def.task {
    val bases = nodeModulesBasePaths.value
    def viteJsPaths: Seq[File] = bases.toSeq.map(_ / "node_modules" / "vite" / "bin" / "vite.js")
    def binShimPaths: Seq[File] =
      bases.toSeq.map(_ / "node_modules" / ".bin" / (if (Util.windows) "vite.cmd" else "vite"))

    // Prefer direct JS entry so we control the node executable, fallback to shim/binary name
    viteJsPaths.find(_.exists()) match {
      case Some(script) => List(Util.nodeExecutable, script.getAbsolutePath)
      case None =>
        binShimPaths.find(_.exists()) match {
          case Some(shim) => List(shim.getAbsolutePath)
          case None       =>
            // Final fallback: rely on PATH (should be augmented) but warn
            streams.value.log.warn("vite script not found in node_modules; falling back to 'vite' on PATH")
            List("vite")
        }
    }
  }

  private def nodeModulesBasePaths = Def.setting(
    Set((js.assemble / target).value, (ThisProject / baseDirectory).value, (LocalRootProject / baseDirectory).value)
  )

  // Memoize computed PATH augmentation for the life of the build session to avoid repeated string massaging
  private[this] val cachedPathKey = new java.util.concurrent.atomic.AtomicReference[Option[(Set[File], String)]](None)
  private def defaultExtraEnv(
    mode: ViteImport.Mode,
    nodeModulesBasePaths: Iterable[File]
  ): Map[String, String] = {
    val pathEnv = if (Util.windows) "Path" else "PATH"
    val baseSet = nodeModulesBasePaths.toSet
    val pathValue = cachedPathKey.get() match {
      case Some((prevBases, value)) if prevBases == baseSet => value
      case _ =>
        val existing = Option(System.getenv(pathEnv)).getOrElse("")
        def binDir(base: File) = (base / "node_modules" / ".bin").getAbsolutePath
        val candidatePaths = baseSet.toList.map(binDir)
        val dedup = (candidatePaths ++ existing.split(pathSeparator).toList.filter(_.nonEmpty))
          .foldLeft(List.empty[String])((acc, p) => if (acc.contains(p)) acc else acc :+ p)
        val joined = dedup.mkString(pathSeparator)
        cachedPathKey.set(Some(baseSet -> joined))
        joined
    }
    val nodeEnv = if (mode == ViteImport.Mode.Production) Map("NODE_ENV" -> "production") else Map.empty[String, String]
    nodeEnv ++ Map(pathEnv -> pathValue)
  }

  private case class CliParameter[A: CliParameter.Encoder](key: String, value: Option[A]) {
    import CliParameter.FlagStyle
    private val enc = implicitly[CliParameter.Encoder[A]]
    def resolve: List[String] = value match {
      case None => Nil
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

        Def.task(
          resolve(
            common ++ List(assetsDir, assetsInlineLimit, ssr, sourcemap, outDir)
          )
        )

      }

  }

}
