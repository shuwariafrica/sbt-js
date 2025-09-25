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

import java.util.concurrent.atomic.AtomicReference

import sbt.*
import sbt.Keys.*

import africa.shuwari.sbt.js.PlatformUtil

/** Toolkit helpers intended for sbt plugin authors. These were previously exported via [[RunnerImports]] but now live
  * here (or in [[RunnerUtil]]) so end-user imports stay lean.
  */
object RunnerToolkit {

  /** Lazily creates a shared [[AtomicReference]] used to track [[ServerState]] instances. Declare one in
    * `projectSettings` when you need to coordinate lifecycle across multiple tasks.
    */
  def jsServerState(namespace: String): SettingKey[AtomicReference[Option[ServerState]]] =
    SettingKey[AtomicReference[Option[ServerState]]](
      "jsServerState",
      s"Internal: Server state for $namespace"
    )

  /** Provides a dedicated task tag for namespaced servers so plugin tasks can coordinate access. */
  def jsServerTag(namespace: String): Tags.Tag = Tags.Tag(s"js-server-$namespace")

  /** Resolves an executable inside the configured Node.js workspace, falling back to the system PATH when necessary.
    * Returns the full command sequence (node shim included when applicable). Call this from a task to obtain a
    * path-safe command prior to process launch.
    */
  def jsFindExecutable(name: String): Def.Initialize[Task[List[String]]] = Def.task {
    RunnerUtil.findExecutable(name, RunnerImports.jsNodeModules.value, streams.value.log)
  }

  /** Runs an arbitrary Node.js-based command, enriching the PATH with project-local binaries so plugin authors can
    * delegate process execution without duplicating boilerplate. Typical usage is to combine this with
    * [[jsFindExecutable]] so that `scripts` defined in `package.json` are resolved exactly as Node would when invoked
    * via the CLI.
    */
  def jsRunProcess(commands: List[String]): Def.Initialize[Task[java.lang.Process]] =
    jsRunProcess(commands, Map.empty, None)

  def jsRunProcess(
    commands: List[String],
    env: Map[String, String]
  ): Def.Initialize[Task[java.lang.Process]] =
    jsRunProcess(commands, env, None)

  def jsRunProcess(
    commands: List[String],
    env: Map[String, String],
    workingDirectory: Option[File]
  ): Def.Initialize[Task[java.lang.Process]] = Def.task {
    val workDir = workingDirectory.getOrElse(RunnerImports.jsNodeProject.value)
    RunnerUtil.processRun(commands, env, workDir, streams.value.log)
  }

  /** Instantiates a [[ManagedServer]] wired to the project’s Node.js workspace and a namespace-specific
    * [[AtomicReference]]. The returned task produces a ready-to-use controller that handles fingerprinted restarts and
    * logging. Keep the `stateRef` in a [[jsServerState]] so tasks share the same controller instance.
    */
  def jsManagedServer(
    namespace: String,
    stateRef: AtomicReference[Option[ServerState]]
  ): Def.Initialize[Task[ManagedServer]] = Def.task {
    new ManagedServer(
      namespace,
      stateRef,
      RunnerImports.jsNodeProject.value,
      RunnerImports.jsNodeModules.value,
      streams.value.log
    )
  }

  /** Ensures the managed server for `namespace` is torn down when sbt exits by installing an exit hook into the global
    * `onLoad` pipeline. Invoke this once from plugin settings after allocating the corresponding state reference.
    */
  def jsRegisterServerCleanup(
    namespace: String,
    stateRef: AtomicReference[Option[ServerState]]
  ): Setting[State => State] =
    (Global / onLoad) := {
      val previous = (Global / onLoad).value

      def exitHook(state: State): State =
        state.addExitHook {
          stateRef.getAndSet(None).foreach { server =>
            state.log.info(s"Cleaning up $namespace server on exit")
            PlatformUtil.processDestroy(Some(server.process), state.log)
          }
        }

      previous.compose(exitHook)
    }
}
