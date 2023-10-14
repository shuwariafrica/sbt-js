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

import africa.shuwari.sbt.JSBundlerPlugin
import africa.shuwari.sbt.vite
import sbt.*

object VitePlugin extends AutoPlugin {
  object autoImport {
    final val vite = ViteImport
  }
  override def requires: Plugins = JSBundlerPlugin

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[?]] = vite.DefaultSettings.projectSettings

}
