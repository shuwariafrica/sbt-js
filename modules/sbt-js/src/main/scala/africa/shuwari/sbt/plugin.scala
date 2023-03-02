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
package africa.shuwari.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbt.Path._
import sbt.nio.Keys._

import java.nio.file.Files
import java.nio.file.Path

object JSBundlerPlugin extends AutoPlugin {

  override def requires: Plugins = ScalaJSPlugin

  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    val jsPrepare =
      taskKey[File](
        "Compile, link, and prepare project for packaging and/or processing with external tools."
      )

    val jsFullLink =
      settingKey[Boolean](
        "Defines whether \"fullLink\" or \"fastLink\" ScalaJS Linker output is used."
      )

    val js = taskKey[File](
      "Process and/or package assembled project with external tools."
    )

    /** Alias for "[[js]] / `sbt.Keys.target`" */
    def jsTarget = js / target

    /** Alias for "[[jsPrepare]] / `sbt.Keys.target`" */
    def jsPrepareTarget = jsPrepare / target

    /** Alias for "[[jsPrepare]] / `sbt.nio.Keys.fileInputIncludeFilter`" */
    def jsPrepareIncludeFilter = jsPrepare / fileInputIncludeFilter

    /** Alias for "[[jsPrepare]] / `sbt.nio.Keys.fileInputExcludeFilter`" */
    def jsPrepareExcludeFilter = jsPrepare / fileInputExcludeFilter

    /** Alias for "[[js]] / `sbt.Keys.sourceDirectory" */
    def jsSourceDirectory = js / sourceDirectory

    /** Alias for "[[js]] / `sbt.Keys.sourceDirectories`" */
    def jsSourceDirectories = js / sourceDirectories

    /** Alias for "[[jsPrepare]] / `sbt.nio.Keys.fileInputs`" */
    def jsPrepareFileInputs = jsPrepare / fileInputs

  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = List(
    jsFullLink := sys.env
      .get("NODE_ENV")
      .map(_.toLowerCase.trim == "production")
      .getOrElse(false),
    jsSourceDirectory := (Compile / sourceDirectory).value / "js", // TODO: Allow for Test configuration
    jsSourceDirectories := List(jsSourceDirectory.value),
    jsTarget := (Compile / crossTarget).value / s"${js.key.label.toLowerCase}-${normalizedName.value}-assembly${pathSuffix.value}",
    jsPrepareTarget := (Compile / crossTarget).value / s"${jsPrepare.key.label.toLowerCase}-${normalizedName.value}-target${pathSuffix.value}",
    jsPrepareFileInputs := Glob(
      (ThisProject / baseDirectory).value,
      "package*.json"
    ) +: jsSourceDirectories.value.map(allDescendants),
    jsPrepareIncludeFilter := (jsPrepareIncludeFilter ?? PathFilter(
      RecursiveGlob
    )).value,
    jsPrepareExcludeFilter := (jsPrepareExcludeFilter ?? (HiddenFileFilter || DirectoryFilter).toNio).value,
    jsPrepare := Def.taskDyn {
      val full = jsFullLink.value
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

        val target = jsPrepareTarget.value
        log.debug(
          s"Using ${jsPrepare.key.label} target directory: ${logf(target)}"
        )
        if (!target.exists) {
          Files.createDirectories(target.toPath)
          log.debug(
            s"Created ${jsPrepare.key.label} target directory: ${logf(target)}"
          )
        }

        val jsInputFiles = jsPrepare.inputFiles.toSet
        val jsInputFileDirectories = jsSourceDirectories.value.toSet
        log.debug(
          s"Discovered Javascript project files in ${jsInputFileDirectories
              .mkString(", ")}:" + jsInputFiles
            .map(p => logf(p.toFile))
            .mkString("\n\t\t", "\n\t\t", "\n")
        )

        val jsInputFileMappings =
          jsInputFiles
            .map(f => f.toAbsolutePath.normalize.toFile)
            .pair(rebase(jsInputFileDirectories, target) | flat(target))
            .toSet

        val scalaJsOutputDir = (if (full) Compile / fullLinkJSOutput
                                else Compile / fastLinkJSOutput).value

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
          .pair(rebase(scalaJsOutputDir, target))
          .toSet

        val allMappings = jsInputFileMappings ++ scalaJsOutputMappings

        val existing = {
          // import scala.jdk.CollectionConverters._

          def nodeModules(path: Path) =
            path.toAbsolutePath.normalize.toFile.getAbsolutePath
              .contains("node_modules")

          fileTreeView.value
            .list(allDescendants(target))
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

        val jsSourceFileChanges = jsPrepare.inputFileChanges

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
            ((in, out))
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
        target
      }
    }.value
  )

  private def allDescendants(base: File) = Glob(base, **)

  private def pathSuffix =
    Def.setting(s"-${if (jsFullLink.value) "full" else "fast"}-linked")

}
