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
package africa.shuwari.sbt.vite

import java.io.File
import sbt.util.Level
import africa.shuwari.sbt.ViteKeys

sealed trait ViteConfiguration {

  /** Public base path */
  def base: Option[String]

  /** Use specified config file */
  def config: Option[File]

  /** Force the optimizer to ignore the cache and re-bundle. */
  def force: Option[Boolean]

  /** Use specified config file */
  def logLevel: Level.Value

  /** Use specified config file */
  def mode: ViteKeys.Mode

}

final case class BuildConfiguration(
  base: Option[String],
  config: Option[File],
  force: Option[Boolean],
  logLevel: Level.Value,
  mode: ViteKeys.Mode,
  target: Option[String],
  assetsDir: Option[String],
  assetsInlineLimit: Option[Int],
  ssr: Option[String],
  sourcemap: Option[Boolean],
  minify: Option[ViteKeys.Minifier],
  manifest: Option[String],
  ssrManifest: Option[String],
  emptyOutDir: Option[Boolean]
) extends ViteConfiguration

final case class RunConfiguration(
  base: Option[String],
  config: Option[File],
  force: Option[Boolean],
  logLevel: Level.Value,
  mode: ViteKeys.Mode
) extends ViteConfiguration
