/** ************************************************************** Copyright © Shuwari Africa Ltd. * * This file is
  * licensed to you under the terms of the Apache * License Version 2.0 (the "License"); you may not use this * file
  * except in compliance with the License. You may obtain * a copy of the License at: * *
  * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in writing, *
  * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, * either express or implied. See the License for the specific * language governing permissions and limitations
  * under the * License. *
  */
package africa.shuwari.sbt.runner

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

import sbt.*
import sbt.util.Logger

import africa.shuwari.sbt.js.PlatformUtil

/** State for a managed server process */
final case class ServerState(
  namespace: String,
  process: java.lang.Process,
  fingerprint: String,
  projectId: String,
  startedAt: Long = System.currentTimeMillis()
)

/** Manages the lifecycle of a background dev-server style process.
  *
  * Instances are normally obtained through [[RunnerToolkit.jsManagedServer]]. They keep track of the project directory,
  * enriched Node.js search paths, and a shared [[ServerState]] so multiple tasks can interact with the same long-lived
  * process.
  */
final class ManagedServer(
  namespace: String,
  stateRef: AtomicReference[Option[ServerState]],
  projectRoot: File,
  nodeModulesPaths: Set[File],
  logger: Logger
) {

  // Project ID to ensure we don't share servers across projects
  private val projectId = projectRoot.getAbsolutePath
  private val startupWaitMillis = 750

  /** Starts (or reuses) the managed process, restarting automatically when the computed fingerprint changes. The
    * fingerprint is derived from the executable, arguments, additional sources, and the owning project to prevent
    * cross-project reuse.
    */
  def start(executable: String): ServerState =
    start(executable, Nil)

  def start(executable: String, args: List[String]): ServerState =
    start(executable, args, Map.empty)

  def start(
    executable: String,
    args: List[String],
    env: Map[String, String]
  ): ServerState =
    start(executable, args, env, Nil)

  def start(
    executable: String,
    args: List[String],
    env: Map[String, String],
    fingerprintSources: List[String]
  ): ServerState = {
    val fingerprint = computeFingerprint(executable :: args ::: fingerprintSources ::: List(projectId))
    val commands = RunnerUtil.findExecutable(executable, nodeModulesPaths, logger) ++ args
    val augmentedEnv = env ++ Map(
      (if (PlatformUtil.isWindows) "Path" else "PATH") -> RunnerUtil.augmentPath(nodeModulesPaths)
    )

    stateRef.updateAndGet {
      case Some(state)
          if state.namespace == namespace &&
            state.projectId == projectId &&
            state.process.isAlive &&
            state.fingerprint == fingerprint =>
        logger.info(s"[$namespace] Server already running (pid=${state.process.pid})")
        Some(state)

      case Some(state)
          if state.namespace == namespace &&
            state.projectId == projectId &&
            state.process.isAlive =>
        logger.info(s"[$namespace] Configuration changed; restarting (old pid=${state.process.pid})")
        PlatformUtil.processDestroy(Some(state.process), logger)
        Some(startProcess(commands, augmentedEnv, fingerprint))

      case Some(state)
          if state.namespace == namespace &&
            state.projectId == projectId =>
        logger.debug(s"[$namespace] Previous server process is dead, starting new one")
        Some(startProcess(commands, augmentedEnv, fingerprint))

      case _ =>
        Some(startProcess(commands, augmentedEnv, fingerprint))
    }.get
  }

  /** Stops the running process if it belongs to the same namespace/project combination. No-op when the server is
    * already stopped.
    */
  def stop(): Unit = {
    val _ = stateRef.getAndUpdate {
      case Some(state) if state.namespace == namespace && state.projectId == projectId =>
        logger.info(s"[$namespace] Stopping server (pid=${state.process.pid})")
        PlatformUtil.processDestroy(Some(state.process), logger)
        None
      case other => other
    }
  }

  /** Convenience for `stop()` followed by `start(...)`, allowing callers to change arguments in one call. */
  def restart(executable: String): ServerState =
    restart(executable, Nil)

  def restart(executable: String, args: List[String]): ServerState =
    restart(executable, args, Map.empty)

  def restart(
    executable: String,
    args: List[String],
    env: Map[String, String]
  ): ServerState =
    restart(executable, args, env, Nil)

  def restart(
    executable: String,
    args: List[String],
    env: Map[String, String],
    fingerprintSources: List[String]
  ): ServerState = {
    stop()
    Thread.sleep(100) // Brief pause to ensure clean shutdown
    start(executable, args, env, fingerprintSources)
  }

  /** @return true when the tracked process is still alive for the current namespace/project. */
  def isRunning: Boolean =
    stateRef.get() match {
      case Some(state)
          if state.namespace == namespace &&
            state.projectId == projectId =>
        state.process.isAlive
      case _ => false
    }

  /** Exposes the underlying [[ServerState]] when the managed server is active for this project. */
  def getState: Option[ServerState] =
    stateRef.get().filter(s => s.namespace == namespace && s.projectId == projectId)

  private def startProcess(commands: List[String], env: Map[String, String], fingerprint: String): ServerState = {
    val process = RunnerUtil.processRun(commands, env, projectRoot, logger)
    val stderrCapture = RunnerUtil.captureProcessErrorStream(process)

    // Wait briefly to catch immediate failures
    Thread.sleep(startupWaitMillis.toLong)
    if (!process.isAlive) {
      val exit = process.exitValue()
      val snippet = stderrCapture.snapshot().linesIterator.take(20).mkString("\n")
      stderrCapture.close()
      if (snippet.nonEmpty) {
        logger.error(
          s"[$namespace] Server failed to start (exit code $exit)\n--- stderr (truncated) ---\n$snippet\n--------------------------")
      }
      sys.error(s"[$namespace] Server failed to start (exit code $exit)")
    }

    stderrCapture.close()
    logger.info(s"[$namespace] Started server (pid=${process.pid})")
    ServerState(namespace, process, fingerprint, projectId)
  }

  private def computeFingerprint(sources: List[String]): String = {
    val combined = sources.mkString("|")
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(combined.getBytes("UTF-8"))
    bytes.take(10).map("%02x".format(_)).mkString
  }
}
