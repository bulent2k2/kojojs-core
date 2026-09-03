import sbt._

/**
  * Application settings. Configure the build for your application here.
  * You normally don't have to touch the actual build definition after this.
  */
object Settings {

  /** Options for the scala compiler */
  val scalacArgs = Seq(
    "-Xlint",
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  /** Declare global dependency versions here to avoid mismatches in multi part dependencies */
  object versions {
    val fiddle = "1.1.1"

    // Faz 3: 2.13.18 / Scala.js 1.x uyumlu hat. kamon, macro-paradise ve
    // kind-projector tamamen kaldırıldı (2.13'te gereksiz / 2.13 sürümleri yok).
    val scalatest = "3.2.19"
    val akka      = "2.5.32"  // 2.13 destekleyen son 2.5 hattı
    val akkaHttp  = "10.1.15" // 2.13 destekleyen 10.1 hattı
    val upickle   = "1.6.0"
    val ace       = "1.2.2"
    val dom       = "1.2.0"   // raw paketi hâlâ mevcut; 2.x sıçraması ayrı iş
    val scalatags = "0.9.4"
    val async     = "1.0.1"   // -Xasync derleyici bayrağı ister
    val coursier  = "2.1.24"
    val base64    = "0.3.0"
  }

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor"  % versions.akka,
    "com.typesafe.akka" %% "akka-stream" % versions.akka,
    "com.typesafe.akka" %% "akka-slf4j"  % versions.akka,
    "com.typesafe.akka" %% "akka-http"   % versions.akkaHttp
  )

  val logging = Seq(
    "net.logstash.logback" % "logstash-logback-encoder" % "5.0",
    "ch.qos.logback"       % "logback-classic"          % "1.2.3"
  )
}
