/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package africa.shuwari.sbt.runner

import sbt.*
import sbt.Keys.*

import africa.shuwari.sbt.js.JSImports as js
import africa.shuwari.sbt.js.PlatformUtil

/** Baseline settings consumed by `JsRunnerPlugin`. */
object RunnerDefaults {

  /** Default wiring for the core [[RunnerImports]] keys. It discovers candidate `node_modules` directories, resolves
    * the active Node project, and exposes the system Node executable.
    */
  def projectSettings: Seq[Setting[?]] = Seq(
    RunnerImports.jsNodeModules := {
      val candidates = Set(
        (js.assemble / target).value,
        (ThisProject / baseDirectory).value,
        (LocalRootProject / baseDirectory).value
      )
      // Filter to only existing directories
      candidates.filter(_.exists())
    },
    RunnerImports.jsNodeProject :=
      RunnerUtil.findNodeProject(
        RunnerImports.jsNodeModules.value,
        streams.value.log
      ),
    RunnerImports.jsNodeExecutable := PlatformUtil.node
  )
}
