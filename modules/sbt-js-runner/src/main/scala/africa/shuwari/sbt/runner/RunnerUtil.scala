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

import java.io.File.pathSeparator
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import sbt.*
import sbt.util.Logger

import africa.shuwari.sbt.js.PlatformUtil

object RunnerUtil {

  /** Finds the Node.js project directory by searching the provided candidate roots for `package.json`. */
  def findNodeProject(searchPaths: Set[File], logger: Logger): File = {
    val candidates = searchPaths.map(_ / "package.json")
    candidates.find(_.exists()) match {
      case Some(pkg) =>
        val dir = pkg.getParentFile
        logger.debug(s"Found Node.js project at: ${dir.getAbsolutePath}")
        dir
      case None =>
        sys.error(
          s"Unable to find Node.js project. No package.json found in: ${searchPaths.map(_.getAbsolutePath).mkString(", ")}")
    }
  }

  /** Resolves the best command sequence to invoke `name`, preferring local node_modules shims over PATH. */
  def findExecutable(name: String, nodeModulesPaths: Set[File], logger: Logger): List[String] = {
    val execName = PlatformUtil.executableName(name)

    // Strategy 1: Direct JS entry point (preferred for control)
    val jsEntries = nodeModulesPaths.toSeq.map(_ / "node_modules" / name / "bin" / s"$name.js")
    jsEntries.find(_.exists()) match {
      case Some(script) =>
        logger.debug(s"Found $name script at: ${script.getAbsolutePath}")
        List(PlatformUtil.node, script.getAbsolutePath)
      case None =>
        // Strategy 2: node_modules/.bin shim
        val shimPaths = nodeModulesPaths.toSeq.map(_ / "node_modules" / ".bin" / execName)
        shimPaths.find(_.exists()) match {
          case Some(shim) =>
            logger.debug(s"Found $name shim at: ${shim.getAbsolutePath}")
            List(shim.getAbsolutePath)
          case None =>
            // Strategy 3: Global installation on PATH
            logger.warn(s"$name not found in node_modules; falling back to PATH")
            List(execName)
        }
    }
  }

  /** Builds a PATH string that prioritises each candidate root’s `node_modules/.bin` directory. */
  def augmentPath(nodeModulesPaths: Set[File]): String = {
    val pathEnv = if (PlatformUtil.isWindows) "Path" else "PATH"
    val existing = Option(System.getenv(pathEnv)).getOrElse("")
    val binDirs = nodeModulesPaths.map(_ / "node_modules" / ".bin").map(_.getAbsolutePath)
    val segments = (binDirs ++ existing.split(pathSeparator).filter(_.nonEmpty)).toList
    // Deduplicate while preserving order
    segments
      .foldLeft(List.empty[String]) { (acc, path) =>
        if (acc.contains(path)) acc else acc :+ path
      }
      .mkString(pathSeparator)
  }

  /** Launches an external process with inherited stdio and an sbt-friendly logging context. */
  def processRun(commands: List[String],
                 env: Map[String, String],
                 workingDirectory: File,
                 logger: Logger): java.lang.Process = {
    val finalCommands = if (PlatformUtil.isWindows) {
      prependWindowsShell(commands, logger)
    } else {
      commands
    }

    logger.debug(s"Running command: ${finalCommands.mkString(" ")}")

    val pb = new ProcessBuilder(finalCommands*)
    env.foreach { case (k, v) => pb.environment().put(k, v) }
    pb.directory(workingDirectory)
    pb.redirectOutput(Redirect.INHERIT)
    pb.redirectError(Redirect.INHERIT)

    logger.debug(s"Working directory: ${pb.directory.getAbsolutePath}")
    logger.debug(s"Environment additions: ${env.mkString(", ")}")

    pb.start()
  }

  /** Lightweight capture of early stderr output for troubleshooting fast-failing processes. */
  final class ProcessErrorCapture private[runner] (snapshotFn: () => String, closeFn: () => Unit)
      extends AutoCloseable {

    /** Returns the buffered stderr collected so far. Call this when a process fails during start-up to surface the last
      * few kilobytes of diagnostic output.
      */
    def snapshot(): String = snapshotFn().trim

    /** Stops the reader thread. Always invoke this once the owning process is healthy or terminated. */
    override def close(): Unit = closeFn()
  }

  /** Starts a background reader that records up to `bufferSize` bytes of stderr output. Call `close()` to release
    * resources once the process is deemed healthy, or `snapshot()` to retrieve the captured text when start-up fails.
    */
  def captureProcessErrorStream(process: java.lang.Process): ProcessErrorCapture =
    captureProcessErrorStream(process, 8192, 25L)

  def captureProcessErrorStream(process: java.lang.Process, bufferSize: Int): ProcessErrorCapture =
    captureProcessErrorStream(process, bufferSize, 25L)

  def captureProcessErrorStream(
    process: java.lang.Process,
    bufferSize: Int,
    pollMillis: Long
  ): ProcessErrorCapture = {
    val in = process.getErrorStream
    val buffer = new Array[Byte](bufferSize.max(1))
    val scratch = new Array[Byte](math.min(512, bufferSize.max(1)))
    val lock = new ReentrantLock()
    val cursor = Array.ofDim[Int](1) // write position
    val sizeArr = Array.ofDim[Int](1) // current size
    val running = new AtomicBoolean(true)

    def append(bytes: Array[Byte], n: Int): Unit = {
      lock.lock()
      try {
        @annotation.tailrec
        def loop(index: Int): Unit =
          if (index < n) {
            buffer(cursor(0)) = bytes(index)
            cursor(0) = (cursor(0) + 1) % buffer.length
            if (sizeArr(0) < buffer.length) sizeArr(0) += 1
            loop(index + 1)
          }

        loop(0)
      } finally lock.unlock()
    }

    val reader = new Thread(
      () =>
        try {
          @annotation.tailrec
          def loop(done: Boolean): Unit = {
            val continue = !done && (running.get() || process.isAlive)
            if (continue) {
              val nextDone =
                if (!running.get() && process.isAlive) {
                  true
                } else {
                  val available = in.available()
                  if (available > 0) {
                    val toRead = math.min(available, scratch.length)
                    val read = in.read(scratch, 0, toRead)
                    if (read == -1) true
                    else {
                      if (read > 0) append(scratch, read)
                      false
                    }
                  } else if (!process.isAlive) {
                    val read = in.read(scratch)
                    if (read <= 0) {
                      true
                    } else {
                      append(scratch, read)
                      false
                    }
                  } else {
                    Thread.sleep(pollMillis)
                    false
                  }
                }

              loop(nextDone)
            }
          }

          loop(done = false)
        } catch {
          case _: InterruptedException => ()
          case _: Throwable            => ()
        },
      s"sbt-js-runner-stderr-${System.currentTimeMillis()}"
    )

    reader.setDaemon(true)
    reader.start()

    def snapshot(): String = {
      lock.lock()
      try {
        val size = sizeArr(0)
        if (size == 0) ""
        else {
          val output = new Array[Byte](size)
          val start = (cursor(0) - size + buffer.length) % buffer.length
          if (start + size <= buffer.length)
            System.arraycopy(buffer, start, output, 0, size)
          else {
            val first = buffer.length - start
            System.arraycopy(buffer, start, output, 0, first)
            System.arraycopy(buffer, 0, output, first, size - first)
          }
          new String(output, StandardCharsets.UTF_8)
        }
      } finally lock.unlock()
    }

    def closeCapture(): Unit =
      if (running.getAndSet(false)) reader.interrupt()

    new ProcessErrorCapture(() => snapshot(), () => closeCapture())
  }

  private def prependWindowsShell(commands: List[String], logger: Logger): List[String] = {
    val pwshAvailable = Try(new ProcessBuilder("pwsh.exe", "-version").start()) match {
      case Success(p) =>
        p.destroy()
        p.waitFor(500, TimeUnit.MILLISECONDS)
        true
      case Failure(_) => false
    }

    val shell = if (pwshAvailable) "pwsh.exe" else "powershell.exe"
    logger.debug(s"Using Windows shell: $shell")
    shell :: "-Command" :: commands
  }
}
