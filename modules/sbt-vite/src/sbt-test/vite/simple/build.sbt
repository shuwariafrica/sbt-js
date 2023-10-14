val Dependencies = new {
  val laminar = Def.setting("com.raquo" %%% "laminar" % "15.0.1")
  val `scalajs-dom` = Def.setting("org.scala-js" %%% "scalajs-dom" % "2.6.0")
  val `web-components-ui5` = Def.setting("be.doeraene" %%% "web-components-ui5" % "1.10.0")
}

lazy val `sbt-js-simple-test` = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
//    logLevel := util.Level.Debug,
    scalaJSUseMainModuleInitializer := true,
    scalaVersion := "3.3.0",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    libraryDependencies ++= List(
      Dependencies.laminar.value,
      Dependencies.`scalajs-dom`.value,
      Dependencies.`web-components-ui5`.value
    ),
    TaskKey[Unit]("getEnvs", "") := sys.props.foreach(println)
  )
