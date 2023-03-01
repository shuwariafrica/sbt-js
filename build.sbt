/****************************************************************
 * Copyright © Shuwari Africa Ltd. All rights reserved.         *
 *                                                              *
 * Shuwari Africa Ltd licenses this file to you under the terms *
 * of the Apache License Version 2.0 (the "License"); you may   *
 * not use this file except in compliance with the License. You *
 * may obtain a copy of the License at:                         *
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

name := "sbt-js-root"

organization := "africa.shuwari.sbt"
shuwariProject
apacheLicensed
startYear := Some(2023)

def commonSettings = List(publishMavenStyle := true)
  
lazy val `sbt-js` =
  project
    .in(file("modules/sbt-js"))
    .enablePlugins(SbtPlugin)
    .settings(addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0"))

lazy val `sbt-vite` =
  project
    .in(file("modules/sbt-vite"))
    .enablePlugins(SbtPlugin)
    .dependsOn(`sbt-js`)
    .settings(
      Keys.run := {
        (Compile / scalacOptions).value.foreach(println)        
      }
    )

lazy val `sbt-js-documentation` =
  project
    .in(file(".sbt-js-doc"))
    .dependsOn(`sbt-js`)
    .enablePlugins(MdocPlugin)
    .settings(
      mdocIn := (LocalRootProject / baseDirectory).value / "modules" / "documentation",
      mdocOut := (LocalRootProject / baseDirectory).value,
      mdocVariables := Map(
        "VERSION" -> version.value
      )
    )

lazy val `sbt-js-root` = project
  .in(file("."))
  .enablePlugins(SbtPlugin)
  .aggregate(`sbt-js`, `sbt-vite`)
  .notPublished
