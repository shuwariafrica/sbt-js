inThisBuild(
  List(
    organization := "africa.shuwari.sbt",
    description := "Collection of sbt plugins for easy initialisation of uniform organisation wide default project settings.",
    homepage := Some(url("https://github.com/shuwarifrica/sbt-js")),
    version := versionSetting.value,
    dynver := versionSetting.toTaskable.toTask.value,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    publishCredentials,
    scmInfo := ScmInfo(
      url("https://github.com/shuwariafrica/sbt-js"),
      "scm:git:https://github.com/shuwariafrica/sbt-js.git",
      Some("scm:git:git@github.com:shuwariafrica/sbt-js.git")
    ).some,
    startYear := Some(2023),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val `sbt-js` =
  project
    .in(file("modules/sbt-js"))
    .enablePlugins(SbtPlugin)
    .settings(publishSettings*)
    .settings(scriptedSettings*)
    .settings(addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0"))

lazy val `sbt-vite` =
  project
    .in(file("modules/sbt-vite"))
    .enablePlugins(SbtPlugin)
    .settings(publishSettings*)
    .dependsOn(`sbt-js`)

lazy val `sbt-js-documentation` =
  project
    .in(file(".sbt-js-doc"))
    .dependsOn(`sbt-js`)
    .notPublished
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
  .shuwariProject
  .apacheLicensed
  .settings(sonatypeProfile)

def publishCredentials = credentials := List(
  Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    System.getenv("PUBLISH_USER"),
    System.getenv("PUBLISH_USER_PASSPHRASE")
  )
)

def publishSettings = publishCredentials +: pgpSettings ++: List(
  packageOptions += Package.ManifestAttributes(
    "Created-By" -> "Simple Build Tool",
    "Built-By" -> System.getProperty("user.name"),
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> version.value,
    "Specification-Vendor" -> organizationName.value,
    "Implementation-Title" -> name.value,
    "Implementation-Version" -> implementationVersionSetting.value,
    "Implementation-Vendor-Id" -> organization.value,
    "Implementation-Vendor" -> organizationName.value
  ),
  publishTo := sonatypePublishToBundle.value,
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  sonatypeProfile
)

def sonatypeProfile = sonatypeProfileName := "africa.shuwari"

def pgpSettings = List(
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

def baseVersionSetting(appendMetadata: Boolean): Def.Initialize[String] = {
  def baseVersionFormatter(in: sbtdynver.GitDescribeOutput) = {
    def meta =
      if (appendMetadata) s"+${in.commitSuffix.distance}.${in.commitSuffix.sha}"
      else ""

    if (!in.isSnapshot()) in.ref.dropPrefix
    else {
      val parts = {
        def current = in.ref.dropPrefix.split("\\.").map(_.toInt)
        current.updated(current.length - 1, current.last + 1)
      }
      s"${parts.mkString(".")}-SNAPSHOT$meta"
    }
  }
  Def.setting(
    dynverGitDescribeOutput.value.mkVersion(
      baseVersionFormatter,
      "SNAPHOT"
    )
  )
}

def versionSetting = baseVersionSetting(appendMetadata = false)

def implementationVersionSetting = baseVersionSetting(appendMetadata = true)

def scriptedSettings: List[Setting[?]] = List(
  scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++
      Seq("-Dplugin.version=" + version.value)
  },
  scriptedBufferLog := false
)
