sys.props.get("plugin.version") match {
  case Some(x) => plugin(x)
  case _ => plugin(versions.previous)
}

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.2")

def plugin(version: String) = addSbtPlugin("africa.shuwari.sbt" % "sbt-js" % version)

def versions = new {
  final val previous = "0.12.0"
}