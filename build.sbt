import sbt._
import Keys._
import Settings._

// Faz 3 (bkz. kojojs-dev/oneri-scala-2.13.md): sbt 1 + Scala.js 1.20.2 +
// Scala 2.13.18 (masaüstü kojo ile aynı; yamalı scala-tr derleyicisi bu
// sürümden üretiliyor). Eski sbt-0.13/scalajs-0.6 build'inden başlıca farklar:
//  - scalafmt eklentisi kaldırıldı (sbt 1 sürümü yok, format zaten kapalıydı)
//  - kamon/macro-paradise/kind-projector kaldırıldı
//  - scalajs-tools -> scalajs-linker (compilerServer'ın link API'si yeniden yazıldı)
//  - runtime modülüne scalajs-javalib + scalajs-scalalib eklendi (Scala.js
//    1.15'ten beri java/scala stdlib IR'leri ayrı artefaktlarda)

val commonSettings = Seq(
  scalacOptions := scalacArgs,
  scalaVersion := "2.13.18",
  version := versions.fiddle
)

lazy val root = project
  .in(file("."))
  .aggregate(page, compilerServer, runtime, client, router)

lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % versions.upickle
    )
  )

lazy val client = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared)
  .settings(commonSettings)
  .settings(
    scalacOptions += "-Xasync",
    libraryDependencies ++= Seq(
      "org.scala-js"           %%% "scalajs-dom" % versions.dom,
      "com.github.marklister"  %%% "base64"      % versions.base64,
      "org.scala-lang.modules" %%% "scala-async" % versions.async % "provided",
      // UUID.randomUUID için: Scala.js SecureRandom'ı bilerek ayrı pakete
      // koyuyor (kripto amaçlı değil, fiddle kimliği üretimi için yeterli)
      "org.scala-js" %%% "scalajs-fake-insecure-java-securerandom" % "1.0.0"
    ),
    // rename output always to -opt.js
    artifactPath in (Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value /
      ((moduleName in fastOptJS).value + "-opt.js"))
  )

lazy val page = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % versions.dom,
      "com.lihaoyi"  %%% "scalatags"   % versions.scalatags
    )
  )

// Fiddle classpath'ine kaynak (resource) olarak gömülecek jar'ları toplayan
// yardımcı modül. Scala.js 1.x'te stdlib IR'leri iki ayrı artefakta bölündü:
// javalib (java.*) ve scalalib (scala.*); linker'ın üçüne de ihtiyacı var.
lazy val runtime = project
  .settings(commonSettings)
  .settings(
    autoScalaLibrary := false, // scala-library'yi aşağıda elle, tam adıyla alıyoruz
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-library"        % scalaVersion.value,
      "org.scala-lang" % "scala-reflect"        % scalaVersion.value,
      "org.scala-js"   % "scalajs-library_2.13" % scalaJSVersion,
      "org.scala-js"   % "scalajs-javalib"      % scalaJSVersion,
      "org.scala-js"   % "scalajs-scalalib_2.13" % s"${scalaVersion.value}+$scalaJSVersion"
    )
  )

lazy val compilerServer = project
  .in(file("compiler-server"))
  .dependsOn(shared, page)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(commonSettings)
  .settings(Revolver.settings: _*)
  .settings(
    name := "scalafiddle-core",
    libraryDependencies ++= Seq(
      "org.scala-lang"   % "scala-compiler"   % scalaVersion.value,
      "org.scala-js"     % "scalajs-compiler" % scalaJSVersion cross CrossVersion.full,
      "org.scala-js"     %% "scalajs-linker"  % scalaJSVersion,
      "com.lihaoyi"      %% "scalatags"       % versions.scalatags,
      "com.lihaoyi"      %% "upickle"         % versions.upickle,
      "io.get-coursier"  %% "coursier"        % versions.coursier,
      "org.apache.maven" % "maven-artifact"   % "3.3.9",
      "org.xerial.snappy" % "snappy-java"     % "1.1.10.5"
    ) ++ akka ++ logging,
    (resources in Compile) ++= {
      (managedClasspath in (runtime, Compile)).value.map(_.data) ++ Seq(
        (packageBin in (page, Compile)).value
      )
    },
    javaOptions in reStart ++= Seq("-Xmx3g", "-Xss4m"),
    javaOptions in Universal ++= Seq("-J-Xss4m"),
    resourceGenerators in Compile += Def.task {
      // store build version in a property file
      val file = (resourceManaged in Compile).value / "version.properties"
      val contents =
        s"""
           |version=${version.value}
           |scalaVersion=${scalaVersion.value}
           |scalaJSVersion=$scalaJSVersion
           |aceVersion=${versions.ace}
           |""".stripMargin
      IO.write(file, contents)
      Seq(file)
    }.taskValue,
    scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
    dockerfile in docker := {
      val appDir: File = stage.value
      val targetDir    = "/app"

      new Dockerfile {
        from("eclipse-temurin:21-jre-jammy")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir)
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("scalafiddle"),
        repository = s"scalafiddle-core-${scalaBinaryVersion.value}",
        tag = Some("latest")
      ),
      ImageName(
        namespace = Some("scalafiddle"),
        repository = s"scalafiddle-core-${scalaBinaryVersion.value}",
        tag = Some(version.value)
      )
    )
  )

lazy val router = (project in file("router"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(sbtdocker.DockerPlugin)
  .dependsOn(shared)
  .settings(Revolver.settings: _*)
  .settings(commonSettings)
  .settings(
    name := "scalafiddle-router",
    libraryDependencies ++= Seq(
      "com.lihaoyi"           %% "scalatags"      % versions.scalatags,
      "org.webjars"           % "ace"             % versions.ace,
      "org.webjars"           % "normalize.css"   % "2.1.3",
      "org.webjars"           % "jquery"          % "2.2.2",
      "org.webjars.npm"       % "js-sha1"         % "0.4.0",
      "com.lihaoyi"           %% "upickle"        % versions.upickle,
      "com.github.marklister" %% "base64"         % versions.base64,
      "ch.megard"             %% "akka-http-cors" % "0.4.3"
    ) ++ akka ++ logging,
    javaOptions in reStart ++= Seq("-Xmx1g"),
    scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
    resourceGenerators in Compile += Def.task {
      // store build version in a property file
      val file = (resourceManaged in Compile).value / "version.properties"
      val contents =
        s"""
           |version=${version.value}
           |scalaVersion=${scalaVersion.value}
           |scalaJSVersion=$scalaJSVersion
           |aceVersion=${versions.ace}
           |""".stripMargin
      IO.write(file, contents)
      Seq(file)
    }.taskValue,
    (resources in Compile) ++= {
      // Seq((fullOptJS in (client, Compile)).value.data)
      Seq((fastOptJS in (client, Compile)).value.data)
    },
    dockerfile in docker := {
      val appDir: File = stage.value
      val targetDir    = "/app"

      new Dockerfile {
        from("eclipse-temurin:21-jre-jammy")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir)
        expose(8880)
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("scalafiddle"),
        repository = "scalafiddle-router",
        tag = Some("latest")
      ),
      ImageName(
        namespace = Some("scalafiddle"),
        repository = "scalafiddle-router",
        tag = Some(version.value)
      )
    )
  )
