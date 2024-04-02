/** *************************************************************** Copyright © Shuwari Africa Ltd. All rights reserved.
  * * * Shuwari Africa Ltd. licenses this file to you under the terms * of the Apache License Version 2.0 (the
  * "License"); you may * not use this file except in compliance with the License. You * may obtain a copy of the
  * License at: * * https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or agreed to in
  * writing, * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR
  * CONDITIONS OF ANY KIND, * either express or implied. See the License for the specific * language governing
  * permissions and limitations under the * License. *
  */
package africa.shuwari.sbt.js

import sbt.*
import sbt.Def
import sbt.Keys.baseDirectory
import sbt.Keys.crossTarget
import sbt.Keys.normalizedName

import africa.shuwari.sbt.js.JSImports as js
import africa.shuwari.sbt.js.Util.allDescendants
import africa.shuwari.sbt.js.Util.pathSuffix

object defaults {

  def jsFullLink: Boolean = sys.env
    .get("NODE_ENV")
    .exists(_.toLowerCase.trim == "production")

  def sourceDirectory: Def.Initialize[File] = Def.setting((Compile / Keys.sourceDirectory).value / js.js.key.label)

  def sourceDirectories: Def.Initialize[List[sbt.File]] = Def.setting(List(js.jsSource.value))

  def targetDirectory: Def.Initialize[File] = Def.setting(
    (Compile / crossTarget).value / s"${js.js.key.label.toLowerCase}-${normalizedName.value}-assembly${pathSuffix.value}")

  def assemblyTargetDirectory: Def.Initialize[File] = Def.setting(
    (Compile / crossTarget).value / s"${js.assemble.key.label.toLowerCase}-${normalizedName.value}-target${pathSuffix.value}"
  )

  def assemblyFileInputs: Def.Initialize[List[Glob]] = Def.setting(
    Glob((ThisProject / baseDirectory).value, "package*.json") +: (JSImports.js / Keys.sourceDirectories).value
      .map(allDescendants)
      .toList
  )

  def nodeProjectDirectory: Def.Initialize[Task[File]] = Def.task {
    val directories =
      Set(js.jsSource.value, (ThisProject / baseDirectory).value, (LocalRootProject / baseDirectory).value)
    directories
      .map(_ / "package.json")
      .find(_.exists())
      .map(_.getParentFile)
      .getOrElse(sys.error("Unable to determine npm project directory. No \"package.json\" file found in " +
        s"${directories.map("\"" + _ + "\"").mkString("", " or ", "")}"))
  }

//  private def pathEnv: String = if (Properties.isWin) "Path" else "PATH"
}
