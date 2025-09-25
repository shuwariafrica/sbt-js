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
package africa.shuwari.sbt.runner

import sbt.*

import africa.shuwari.sbt.js.JSBundlerPlugin

/** Auto plugin that exposes [[RunnerImports]] to build users and wires the default settings supplied by
  * [[RunnerDefaults]]. Downstream plugins such as sbt-vite should depend on this plugin rather than re-implementing the
  * wiring.
  */
object JsRunnerPlugin extends AutoPlugin {
  val autoImport = RunnerImports
  override def requires: Plugins = JSBundlerPlugin
  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[?]] = RunnerDefaults.projectSettings
}
