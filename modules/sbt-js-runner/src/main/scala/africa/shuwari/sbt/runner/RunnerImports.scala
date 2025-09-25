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

/** Public keys intended for build authors working in their `*.sbt` files. Plugin-only helpers now live in
  * [[RunnerToolkit]] (or [[RunnerUtil]]) to keep this surface area focused on end-user needs.
  */
object RunnerImports {
  val jsNodeProject = taskKey[File]("Discovers the Node.js project root directory")
  val jsNodeModules = taskKey[Set[File]]("Paths to search for node_modules")
  val jsNodeExecutable = settingKey[String]("Path to the node executable")
}
