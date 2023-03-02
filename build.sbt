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

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

def commonSettings = List(publishMavenStyle := true)

lazy val `sbt-js` =
  project
    .in(file("modules/sbt-js"))
    .enablePlugins(SbtPlugin)
    .settings(publishSettings)
    .settings(addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0"))

lazy val `sbt-vite` =
  project
    .in(file("modules/sbt-vite"))
    .enablePlugins(SbtPlugin)
    .settings(publishSettings)
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
    "Implementation-Version" -> version.value, // FIXME
    "Implementation-Vendor-Id" -> organization.value,
    "Implementation-Vendor" -> organizationName.value
  ),
  publishTo := sonatypePublishToBundle.value,
  developers := List(
    Developer(
      id = "shuwaridev",
      name = "Shuwari Developer Team",
      email = "dev at shuwari com",
      url = url("https://shuwari.com/dev")
    )
  ),
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  sonatypeCredentialHost := "s01.oss.sonatype.org",
)

def pgpSettings = List(
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)
