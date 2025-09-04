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
package africa.shuwari.sbt.js

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*

import java.nio.file.Files

import sbt.*
import sbt.Keys.*
import sbt.nio.Keys.*

import africa.shuwari.sbt.js.JSImports as js

object JSBundlerPlugin extends AutoPlugin {

  override def requires: Plugins = ScalaJSPlugin

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    final val js = JSImports
  }

  override def projectSettings: Seq[Setting[_]] = List(
    js.fullLink := defaults.jsFullLink,
    js.jsSource := defaults.sourceDirectory.value,
    js.js / sourceDirectories := defaults.sourceDirectories.value,
    js.js / target := defaults.targetDirectory.value,
    js.moduleDirectory := defaults.nodeProjectDirectory.value,
    js.assemble / target := defaults.assemblyTargetDirectory.value,
    js.assemble / fileInputs := defaults.assemblyFileInputs.value,
    js.assemble / fileInputIncludeFilter := ((js.assemble / fileInputIncludeFilter) ?? PathFilter(
      RecursiveGlob
    )).value,
    js.assemble / fileInputExcludeFilter := ((js.assemble / fileInputExcludeFilter) ?? (HiddenFileFilter || DirectoryFilter).toNio).value,
    // Incremental assembly using sbt.io.Sync for strict incrementality
    js.assemble := Def.taskDyn {
      // Ensure appropriate linker runs before assembly
      val fullLinkEnabled = js.fullLink.value
      val linkTask = if (fullLinkEnabled) Compile / fullLinkJS else Compile / fastLinkJS
      Def
        .task {
          import sbt.io._
          val log = streams.value.log
          val targetDir = (js.assemble / target).value
          Files.createDirectories(targetDir.toPath)

          val fullOut = (Compile / fullLinkJSOutput).value
          val fastOut = (Compile / fastLinkJSOutput).value
          val linkerOutputDir = if (fullLinkEnabled) fullOut else fastOut
          val staticSourceRoots = (JSImports.js / sourceDirectories).value.toSet

          val staticFiles = js.assemble.inputFiles.map(_.toFile)
          import sbt.Path._
          val staticMappings = staticFiles.pair(rebase(staticSourceRoots, targetDir) | flat(targetDir))
          val linkerMappings = fileTreeView.value
            .list(linkerOutputDir.toGlob / **)
            .map(_._1.toFile)
            .pair(rebase(linkerOutputDir, targetDir))
          val allMappings = (staticMappings ++ linkerMappings).distinct

          val mappingSources = allMappings.map(_._1).toSet

          val cacheDir = streams.value.cacheDirectory / "js-assemble"
          val cached = FileFunction.cached(cacheDir, FilesInfo.lastModified, FilesInfo.exists) { (_: Set[File]) =>
            // Remove stale outputs (those in target not present in new mapping sources), except preserved
            val existing = Path.allSubpaths(targetDir).map(_._1).filter(_.isFile).toSet
            val mappedTargets = allMappings.map(_._2).toSet
            val stale = existing.diff(mappedTargets)
            if (stale.nonEmpty) {
              stale.foreach { f => IO.delete(f); log.info(s"${js.assemble.key.label}: removed stale ${f.getName}") }
            }
            // Copy updated/new
            allMappings.foreach { case (in, out) =>
              if (!out.exists() || in.lastModified() > out.lastModified()) {
                IO.createDirectory(out.getParentFile)
                IO.copyFile(in, out, preserveLastModified = true)
              }
            }
            mappedTargets
          }
          cached(mappingSources)
          targetDir
        }
        .dependsOn(linkTask)
    }.value
  )

}
