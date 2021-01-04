name := "streaming"

version := "0.1"

scalaVersion := "2.13.4"

libraryDependencies ++=
  List(
    "org.apache.httpcomponents" % "httpclient" % "4.5.13",
    "org.scalactic" %% "scalactic" % "3.2.2",
    "org.scalatest" %% "scalatest" % "3.2.2" % "test",
    "org.mockito" % "mockito-core" % "2.7.19" % Test,
    "org.mockito" %% "mockito-scala" % "1.16.3",
    "commons-io" % "commons-io" % "2.6"
  )
