name := "sbt-js-root"

organization := "africa.shuwari.sbt"
shuwariProject
apacheLicensed
startYear := Some(2023)
scmInfo := ScmInfo(
  url("https://github.com/shuwariafrica/sbt-js"),
  "scm:git:https://github.com/shuwariafrica/sbt-js.git",
  Some("scm:git:git@github.com:shuwariafrica/sbt-js.git")
).some

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
