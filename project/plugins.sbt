// Faz 3 (bkz. kojojs-dev/oneri-scala-2.13.md): sbt 0.13 + Scala.js 0.6 ->
// sbt 1 + Scala.js 1.x. Ölü http://repo.typesafe.com çözümleyicisi kaldırıldı
// (sbt 1 eklentileri Maven Central'da). scalafmt eklentisi de kaldırıldı
// (eski lucidchart eklentisinin sbt 1 sürümü yok; format işi zaten kapalıydı).
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.8.3")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
