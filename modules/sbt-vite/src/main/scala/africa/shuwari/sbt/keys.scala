/*****************************************************************
 * Copyright © Shuwari Africa Ltd. All rights reserved.          *
 *                                                               *
 * Shuwari Africa Ltd. licenses this file to you under the terms *
 * of the Apache License Version 2.0 (the "License"); you may    *
 * not use this file except in compliance with the License. You  *
 * may obtain a copy of the License at:                          *
 *                                                               *
 *     https://www.apache.org/licenses/LICENSE-2.0               *
 *                                                               *
 * Unless required by applicable law or agreed to in writing,    *
 * software distributed under the License is distributed on an   *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,  *
 * either express or implied. See the License for the specific   *
 * language governing permissions and limitations under the      *
 * License.                                                      *
 *****************************************************************/
package africa.shuwari.sbt

import sbt._
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object ViteKeys {
  @inline private def doc(str: String) =
    str + " See https://vitejs.dev/guide/cli.html."

  // Vite Common Keys

  val base = SettingKey[Option[String]](
    "viteBase",
    doc("Option corresponding to Vite's \"--base\" setting.")
  )

  val config = SettingKey[Option[String]](
    "viteConfig",
    doc("Option corresponding to Vite's \"--config\" setting.")
  )

  // val debug = SettingKey[Option[Boolean]](
  //   "viteDebug",
  //   doc("Option corresponding to Vite's \"--debug\" setting.")
  // )

  val force = SettingKey[Option[Boolean]](
    "viteForce",
    doc("Option corresponding to Vite's \"--force\" setting.")
  )

  val logLevel = SettingKey[Level.Value](
    "viteLogLevel",
    doc("Option corresponding to Vite's \"--logLevel\" setting.")
  )

  val mode = SettingKey[Mode](
    "viteMode",
    doc("Option corresponding to Vite's \"--mode\" setting.")
  )

  // Vite Development Server Keys

  val host = SettingKey[Option[String]](
    "viteHost",
    doc("Option corresponding to Vite's \"--host\" setting.")
  )

  val port = SettingKey[Option[Int]](
    "vitePort",
    doc("Option corresponding to Vite's \"--port\" setting.")
  )

  val strictPort = SettingKey[Option[Boolean]](
    "viteStrictPort",
    doc("Option corresponding to Vite's \"--strictPort\" setting.")
  )

  val cors = SettingKey[Option[Boolean]](
    "viteCors",
    doc("Option corresponding to Vite's \"--cors\" setting.")
  )

  // Vite Build Keys
  val assetsDir = SettingKey[Option[String]](
    "viteAssetsDir",
    doc("Option corresponding to Vite's \"--assetsDir\" setting.")
  )

  val assetsInlineLimit = SettingKey[Option[Int]](
    "viteAssetsInlineLimit",
    doc("Option corresponding to Vite's \"--assetsInlineLimit\" setting.")
  )

  val ssr = SettingKey[Option[String]](
    "viteSsr",
    doc("Option corresponding to Vite's \"--ssr\" setting.")
  )

  val sourcemap = SettingKey[Option[Boolean]](
    "viteSourcemap",
    doc("Option corresponding to Vite's \"--sourcemap\" setting.")
  )

  val minify = SettingKey[Option[Minifier]](
    "viteMinify",
    doc("Option corresponding to Vite's \"--minify\" setting.")
  )

  // Plugin Keys

  private[sbt] val viteProcessInstances =
    settingKey[AtomicReference[List[scala.sys.process.Process]]](
      "Currently running Vite process instances."
    )

  val useNpx = SettingKey[Boolean](
    "viteUseNpx",
    "Defines whether \"npx\" should be used to execute Vite."
  )

  val viteVersion = settingKey[Option[String]](
    "Defines the version of Vite executed if \"useNpx\" is specified as \"true\". Uses latest version of Vite if not specified."
  )

  val viteExecutable = taskKey[List[String]](
    "If specified, defines the command used to execute Vite. Will cause \"useNpx\" and \"viteVersion\" to be ignored."
  )

  val vite = taskKey[Unit](doc("Executes Vite's development server"))
  val viteBuild = taskKey[File](doc("Executes Vite's \"build\" command."))
  val viteStop = taskKey[Unit]("Shuts down any running instances of Vite's development server.")
  // val vitePreview = taskKey[File](doc("Executes Vite's \"preview\" command."))

  sealed trait ConfigurationItemResolver[A] {
    def resolve(param: A): String
  }

  sealed trait Mode extends Product with Serializable

  object Mode extends ConfigurationItemResolver[Mode] {
    case object Development extends Mode
    case object Production extends Mode

    def resolve(param: Mode) = param match {
      case Development => "development"
      case Production  => "production"
    }
  }

  sealed trait Minifier extends Product with Serializable

  object Minifier extends ConfigurationItemResolver[Minifier] {

    case object ESBuild extends Minifier
    case object Terser extends Minifier
    case object Default extends Minifier
    case object Disabled extends Minifier

    def resolve(param: Minifier): String = param match {
      case ESBuild  => "esbuild"
      case Terser   => "terser"
      case Default  => "true"
      case Disabled => "false"
    }

  }

}
