package scalafiddle.compiler

import java.net.{URL, URLClassLoader}

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.reflect.io
import scala.tools.nsc
import scala.tools.nsc.Settings
import scala.tools.nsc.classpath.{AggregateClassPath, FileUtils, VirtualDirectoryClassPath}
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.util.PathResolver
import scala.util.Try

/**
  * Faz 3 notları (2.12 sürümünden farklar):
  *  - Tek dosya: scala-2.11/scala-2.12 kaynak dizinleri kalktı, hedef yalnızca 2.13.
  *  - Eklenti Scala.js 1.x'te org.scalajs.nscplugin.ScalaJSPlugin.
  *  - macro-paradise ve kind-projector kaldırıldı (2.13'te gereksiz).
  *  - JDK sınıfları: eski kod java.lang'ı Java 8'in sun.boot.class.path'inden
  *    (LibraryManager bootFiles) alıyordu; Java 9+ ile o yol yok. Artık
  *    PathResolver'ın verdiği platform classpath'i (jrt) aggregate'e ekleniyor.
  */
object GlobalInitCompat {
  val log = LoggerFactory.getLogger(getClass)

  private def inMemClassloader(libs: Seq[io.AbstractFile]): ClassLoader = {
    new URLClassLoader(new Array[URL](0), this.getClass.getClassLoader) {
      private val classCache = mutable.Map.empty[String, Option[Class[_]]]

      override def findClass(name: String): Class[_] = {
        def findClassInLibs(): Option[AbstractFile] = {
          val parts = name.split('.')
          libs
            .map(dir => {
              Try {
                parts
                  .dropRight(1)
                  .foldLeft[AbstractFile](dir)((parent, next) => parent.lookupName(next, directory = true))
                  .lookupName(parts.last + ".class", directory = false)
              } getOrElse null
            })
            .find(_ != null)
        }

        val res = classCache.getOrElseUpdate(
          name,
          findClassInLibs().map { f =>
            val data = f.toByteArray
            this.defineClass(name, data, 0, data.length)
          }
        )
        res match {
          case None =>
            log.error("Not Found Class " + name)
            throw new ClassNotFoundException()
          case Some(cls) =>
            cls
        }
      }

      override def close() = {}
    }
  }

  private final def lookupPath(base: AbstractFile)(pathParts: Seq[String], directory: Boolean): AbstractFile = {
    var file: AbstractFile = base
    for (dirPart <- pathParts.init) {
      file = file.lookupName(dirPart, directory = true)
      if (file == null)
        return null
    }

    file.lookupName(pathParts.last, directory = directory)
  }

  private def buildClassPath(absFile: AbstractFile) =
    new VirtualDirectoryClassPath(new VirtualDirectory(absFile.name, None) {
      override def iterator = absFile.iterator

      override def lookupName(name: String, directory: Boolean) = absFile.lookupName(name, directory)

      override def subdirectoryNamed(name: String) = absFile.subdirectoryNamed(name)
    }) {
      override def getSubDir(packageDirName: String): Option[AbstractFile] = {
        Option(lookupPath(absFile)(packageDirName.split('/').toIndexedSeq, directory = true))
      }

      override def findClassFile(className: String): Option[AbstractFile] = {
        val relativePath = FileUtils.dirPath(className) + ".class"
        Option(lookupPath(absFile)(relativePath.split('/').toIndexedSeq, directory = false))
      }
    }

  // JDK'nın kendi sınıfları (java.*): jrt üzerinden. Bir kez çözülür.
  private lazy val jdkClassPath: Seq[ClassPath] = {
    val settings = new Settings
    new PathResolver(settings, new nsc.CloseableRegistry).result match {
      case AggregateClassPath(entries) => entries
      case single                      => List(single)
    }
  }

  private def makeClassPath(libs: Seq[io.AbstractFile]): ClassPath =
    AggregateClassPath(libs.map(buildClassPath) ++ jdkClassPath)

  def initGlobal(settings: Settings, reporter: StoreReporter, libs: Seq[io.AbstractFile]): nsc.Global = {
    val cp = makeClassPath(libs)
    val cl = inMemClassloader(libs)

    new nsc.Global(settings, reporter) { g =>
      override def classPath = cp

      override lazy val plugins = List[Plugin](
        new org.scalajs.nscplugin.ScalaJSPlugin(this)
      )

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        override val global    = g
        override val settings  = g.settings
        override def classPath = cp
      }

      // 2.13: makro classloader kancası Analyzer'dan Global'e taşındı
      override def findMacroClassLoader(): ClassLoader = cl
    }
  }

  def initInteractiveGlobal(settings: Settings,
                            reporter: StoreReporter,
                            libs: Seq[io.AbstractFile]): nsc.interactive.Global = {
    val cp = makeClassPath(libs)
    new nsc.interactive.Global(settings, reporter) { g =>
      override def classPath = cp

      override lazy val plugins = List[Plugin](
        new org.scalajs.nscplugin.ScalaJSPlugin(this)
      )

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        override val global    = g
        override val settings  = g.settings
        override def classPath = cp
      }

      // 2.13: makro classloader kancası Analyzer'dan Global'e taşındı
      private val cl = inMemClassloader(libs)
      override def findMacroClassLoader(): ClassLoader = cl
    }
  }
}
