sys.props.get("plugin.version") match {
  case Some(x) => plugin(x)
  case _       => plugin(versions.previous)
}

def plugin(version: String) = addSbtPlugin("africa.shuwari.sbt" % "sbt-vite" % version)

def versions = new {
  final val previous = "0.12.1-SNAPSHOT"
}
