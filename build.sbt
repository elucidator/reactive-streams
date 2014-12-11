import scalariform.formatter.preferences._

organization  := "com.xebia"

name := "reactive-stream-exploration"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

scalacOptions := Seq("-encoding", "utf8",
                     "-target:jvm-1.8",
                     "-feature",
                     "-language:implicitConversions",
                     "-language:postfixOps",
                     "-unchecked",
                     "-deprecation",
                     "-Xlog-reflective-calls",
                     "-Ywarn-unused",
                     "-Ywarn-unused-import")

val akkaVersion = "2.3.7"
val sprayVersion = "1.3.2"

libraryDependencies ++= Seq(
    "com.typesafe.akka"         %% "akka-actor"                     % akkaVersion,
    "com.typesafe.akka"         %% "akka-slf4j"                     % akkaVersion,
    "com.typesafe.akka" 	    %% "akka-persistence-experimental"  % akkaVersion,
    "com.typesafe.akka"		    %% "akka-stream-experimental" 		% "1.0-M1" withSources(),
    "com.typesafe.akka"         %% "akka-cluster"                   % akkaVersion,
    "com.typesafe.akka"         %% "akka-contrib"                   % akkaVersion,
    "io.spray"                  %% "spray-can"                      % sprayVersion,
    "io.spray"                  %% "spray-client"                   % sprayVersion,
    "io.spray"                  %% "spray-routing"                  % sprayVersion,
    "io.spray"                  %% "spray-json"                     % "1.3.1",
    "ch.qos.logback"            %  "logback-classic"                % "1.1.2",
    "com.typesafe.akka"         %% "akka-testkit"                   % akkaVersion    % "test",
    "io.spray"                  %% "spray-testkit"                  % sprayVersion   % "test",
    "org.scalatest"             %% "scalatest"                      % "2.2.2"        % "test",
    "commons-io"                %  "commons-io"                     % "2.4"          % "test",
	"joda-time"                 %  "joda-time"                      % "2.5",
  	"org.joda"                  %  "joda-convert"                   % "1.7"
)

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, false)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 90)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)
  .setPreference(RewriteArrowSymbols, true)


