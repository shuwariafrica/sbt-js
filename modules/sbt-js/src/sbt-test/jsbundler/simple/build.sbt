

lazy val `sbt-js-simple-test` = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaVersion := "3.3.0"
  )