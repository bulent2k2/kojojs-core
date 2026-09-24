package scalafiddle.router

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

import upickle.default._

object Config {
  protected val config = ConfigFactory.load().getConfig("fiddle")

  // read the generated version data
  protected val versionProps = new Properties()
  versionProps.load(getClass.getResourceAsStream("/version.properties"))

  val interface            = config.getString("interface")
  val port                 = config.getInt("port")
  val analyticsID          = config.getString("analyticsID")
  val secret               = config.getString("secret")
  val scalaFiddleSourceUrl = config.getString("scalaFiddleSourceUrl")
  val scalaFiddleEditUrl   = config.getString("scalaFiddleEditUrl")

  val scalaVersions    = read[Seq[String]](config.getString("scalaVersions"))
  val defaultLibs      = read[Map[String, Seq[String]]](config.getString("defaultLibs"))
  val extLibs          = read[Map[String, String]](config.getString("extLibs"))
  val refreshLibraries = FiniteDuration(config.getDuration("refreshLibraries").toMillis, TimeUnit.MILLISECONDS)

  val corsOrigins = config.getStringList("corsOrigins").asScala.toSeq

  /** Derleme zamanı damgası; koco-deploy/build.sh yazıyor, start.sh
    * KOCO_SURUM_* olarak geçiriyor. Damgasız kurulumda hepsi boş. */
  object surum {
    private val s = config.getConfig("surum")
    val core   = s.getString("core")
    val dev    = s.getString("dev")
    val editor = s.getString("editor")
    val tarih  = s.getString("tarih")
  }

  object compiler {
    val c    = config.getConfig("compiler")
    val host = c.getString("host")
    val port = c.getInt("port")
  }

  /** Derleyici sağlığı ayarları; açıklamaları reference.conf'ta. */
  object compilerHealth {
    private val h = config.getConfig("compilerHealth")
    private def süre(ad: String) = FiniteDuration(h.getDuration(ad).toMillis, TimeUnit.MILLISECONDS)
    val stallTimeout      = süre("stallTimeout")
    val recycleAfter      = h.getInt("recycleAfter")
    val restartGrace      = süre("restartGrace")
    val checkInitialDelay = süre("checkInitialDelay")
    val checkInterval     = süre("checkInterval")
  }

  val version    = versionProps.getProperty("version")
  val aceVersion = versionProps.getProperty("aceVersion")

  val logoLight = config.getString("logoLight")
  val logoDark  = config.getString("logoDark")

  val extJS       = config.getStringList("extJS").asScala.toSeq
  val extCSS      = config.getStringList("extCSS").asScala.toSeq
  val baseEnv     = config.getString("baseEnv")
  val clientFiles = config.getStringList("clientFiles").asScala.toSeq

  val cacheDir = config.getString("cacheDir")
}
