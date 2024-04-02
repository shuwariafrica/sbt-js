/** *************************************************************** Copyright © Shuwari Africa Ltd. All rights reserved.
  * * * Shuwari Africa Ltd. licenses this file to you under the terms * of the Apache License Version 2.0 (the
  * "License"); you may * not use this file except in compliance with the License. You * may obtain a copy of the
  * License at: * * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in
  * writing, * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR
  * CONDITIONS OF ANY KIND, * either express or implied. See the License for the specific * language governing
  * permissions and limitations under the * License. *
  */
package africa.shuwari.sbt.js

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*

import java.nio.file.Files
import java.nio.file.Path

import sbt.*
import sbt.Keys.*
import sbt.Path.*
import sbt.nio.Keys.*

import africa.shuwari.sbt.js.JSImports as js
import africa.shuwari.sbt.js.Util.*

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
    js.assemble := Def.taskDyn {
      val full = js.fullLink.value
      Def.task {
        val log = streams.value.log
        def logf(file: File) =
          "\"" + file
            .relativeTo((LocalRootProject / baseDirectory).value)
            .getOrElse("External File") + "\""
        def logfMaps(files: Iterable[(File, File)]) =
          files
            .map(p => s"${logf(p._1)} -> ${logf(p._2)}")
            .mkString("\n\t\t", "\n\t\t", "\n")

        val jsTarget = (js.assemble / Keys.target).value
        log.debug(
          s"Using ${js.assemble.key.label} target directory: ${logf(jsTarget)}"
        )
        if (!jsTarget.exists) {
          Files.createDirectories(jsTarget.toPath)
          log.debug(
            s"Created ${js.assemble.key.label} target directory: ${logf(jsTarget)}"
          )
        }

        val jsInputFiles = js.assemble.inputFiles.toSet
        val jsInputFileDirectories = (JSImports.js / sourceDirectories).value.toSet
        log.debug(
          s"Discovered Javascript project files in ${jsInputFileDirectories
              .mkString(", ")}:" + jsInputFiles
            .map(p => logf(p.toFile))
            .mkString("\n\t\t", "\n\t\t", "\n")
        )

        val jsInputFileMappings =
          jsInputFiles
            .map(f => f.toAbsolutePath.normalize.toFile)
            .pair(rebase(jsInputFileDirectories, jsTarget) | flat(jsTarget))
            .toSet

        val scalaJsOutputDir = (if (full) Compile / fullLinkJSOutput else Compile / fastLinkJSOutput).value

        val scalaJsOutput =
          fileTreeView.value.list(scalaJsOutputDir.toGlob / **)
        def scalaJsOutputPaths = scalaJsOutput.map(_._1)
        log.debug(
          "Discovered ScalaJS Linker output files:" + scalaJsOutputPaths
            .map(p => logf(p.toFile))
            .mkString("\n\t\t", "\n\t\t", "\n")
        )

        val scalaJsOutputMappings = scalaJsOutputPaths
          .map(_.toAbsolutePath.normalize.toFile)
          .pair(rebase(scalaJsOutputDir, jsTarget))
          .toSet

        val allMappings = jsInputFileMappings ++ scalaJsOutputMappings

        val existing = {
          // import scala.jdk.CollectionConverters._

          def nodeModules(path: Path) =
            path.toAbsolutePath.normalize.toFile.getAbsolutePath
              .contains("node_modules")

          fileTreeView.value
            .list(allDescendants(jsTarget))
            .flatMap { case (path, _) =>
              if (
                !allMappings.exists(
                  _._2.toPath.toAbsolutePath.normalize == path.toAbsolutePath.normalize
                ) && !nodeModules(path)
              ) {
                IO.delete(path.toFile)
                log.info(
                  "Deleted obsolete Javascript project file:\n\t\t" + logf(
                    path.toFile
                  )
                )
                None
              } else Some(path)
            }
            .toSet
        }

        val jsSourceFileChanges = js.assemble.inputFileChanges

        val jsUpdated = jsInputFileMappings.filter(p =>
          (jsSourceFileChanges.created ++ jsSourceFileChanges.modified)
            .contains(p._1.toPath))
        if (jsUpdated.nonEmpty)
          log.debug(
            "Updated and/or new Javascript input files:" + logfMaps(jsUpdated)
          )

        val scalaJsUpdated = scalaJsOutputMappings.collect {
          case (in, out)
              if existing.contains(out.toPath) && FileInfo
                .lastModified(existing.find(_ == out.toPath).get.toFile)
                .lastModified != FileInfo.lastModified(in).lastModified =>
            log.info(
              FileInfo.lastModified(in) + "\n" + FileInfo
                .lastModified(out)
                .lastModified
            )
            (in, out)
        }
        if (scalaJsUpdated.nonEmpty)
          log.debug(
            "Replacing updated ScalaJS output files:" + logfMaps(scalaJsUpdated)
          )

        val mappings = jsUpdated ++ scalaJsUpdated ++ allMappings.filterNot(p => existing.contains(p._2.toPath))
        if (mappings.nonEmpty)
          log.info(
            "Creating and/or updating Javascript project with mappings:" + logfMaps(
              mappings
            )
          )

        IO.copy(
          mappings,
          CopyOptions(
            overwrite = true,
            preserveLastModified = true,
            preserveExecutable = true
          )
        )
        jsTarget
      }
    }.value
  )

}
