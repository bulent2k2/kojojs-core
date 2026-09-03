package scalafiddle.compiler

import java.io._
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.nio.file.Paths

import org.scalajs.linker.interface.IRFile
import org.scalajs.linker.standard.MemIRFileImpl
import org.slf4j.LoggerFactory

import scala.tools.nsc.io.AbstractFile
import scalafiddle.compiler.cache._
import scalafiddle.shared.ExtLib

/**
  * Loads the jars that make up the classpath of the scala-js-fiddle
  * compiler and re-shapes it into the correct structure to satisfy
  * scala-compile and the Scala.js linker.
  *
  * Faz 3 farkları:
  *  - coursier 1.0/scalaz -> coursier 2.x (Fetch API).
  *  - scalajs-tools VirtualJarFile -> linker 1.x IRFile (MemIRFileImpl);
  *    jar adı+yol IRFile sürümü olarak veriliyor ki artımlı linker
  *    kütüphaneleri her derlemede yeniden işlemesin.
  *  - Java 8 bootFiles korsanlığı kalktı (bkz. GlobalInitCompat: jrt).
  *  - Kütüphane eşlemesi Dependency yerine jar ADI üzerinden (Koco'da dış
  *    kütüphane listesi küçük; ad bazında tekilleştirme yeterli).
  *  - baseLibs, Scala.js 1.15+ stdlib bölünmesini içeriyor: javalib +
  *    scalalib ayrı jar'lar.
  */
class LibraryManager(val depLibs: Seq[ExtLib]) {
  val log     = LoggerFactory.getLogger(getClass)

  val baseLibs = Seq(
    s"/scala-library-${Config.scalaVersion}.jar",
    s"/scala-reflect-${Config.scalaVersion}.jar",
    s"/scalajs-library_${Config.scalaMainVersion}-${Config.scalaJSVersion}.jar",
    s"/scalajs-javalib-${Config.scalaJSVersion}.jar",
    s"/scalajs-scalalib_${Config.scalaMainVersion}-${Config.scalaVersion}+${Config.scalaJSVersion}.jar",
    s"/page_sjs${Config.scalaJSMajorVersion}_${Config.scalaMainVersion}-${Config.version}.jar"
  )

  val sjsVersion = s"_sjs${Config.scalaJSMajorVersion}_${Config.scalaMainVersion}"

  val commonJars: Seq[(String, InputStream)] = {
    log.debug("Loading common libraries...")
    baseLibs.map { name =>
      // coursier önbelleği '+' işaretini %2B olarak kodluyor (scalalib jar
      // adı sürümünde + taşıyor) -- iki adı da dene
      val stream = Option(getClass.getResourceAsStream(name))
        .orElse(Option(getClass.getResourceAsStream(name.replace("+", "%2B"))))
        .getOrElse(throw new Exception(s"Classpath loading failed, jar $name not found"))
      log.debug(s"Loading resource $name")
      name -> stream
    }
  }

  import coursier.{Dependency, Fetch, Module, ModuleName, Organization, Repositories, LocalRepositories}

  private def toDependency(lib: ExtLib): Dependency = {
    val exclusions = Set(
      (Organization("org.scala-lang"), ModuleName("scala-reflect")),
      (Organization("org.scala-lang"), ModuleName("scala-library")),
      (Organization("org.scala-js"), ModuleName(s"scalajs-library_${Config.scalaMainVersion}")),
      (Organization("org.scala-js"), ModuleName(s"scalajs-test-interface_${Config.scalaMainVersion}"))
    )
    val artifactName =
      if (lib.compileTimeOnly) s"${lib.artifact}_${Config.scalaMainVersion}"
      else lib.artifact + sjsVersion
    Dependency(Module(Organization(lib.group), ModuleName(artifactName)), lib.version)
      .withExclusions(exclusions)
  }

  def loadLibraries(libs: Seq[ExtLib]) = {
    log.debug(s"Loading: $libs")

    val repositories = Seq(
      LocalRepositories.ivy2Local,
      Repositories.central
    )

    // her kütüphaneyi (geçişli bağımlılıklarıyla) ayrı ayrı çöz ki
    // ExtLib -> jar listesi eşlemesi elde kalsın
    val resolved: Seq[(ExtLib, Seq[File])] = libs.map { lib =>
      val files = Fetch()
        .withRepositories(repositories)
        .addDependencies(toDependency(lib))
        .run()
      lib -> files.filter(_.getName.endsWith(".jar"))
    }

    val extJars = resolved.flatMap(_._2).distinctBy(_.getName)

    // acquire an exclusive lock to prevent others from updating the FFS at the same time
    Paths.get(Config.libCache).toFile.mkdirs()
    val lockFile       = Paths.get(Config.libCache).resolve("ffs.lck").toFile
    val lockChannel    = new RandomAccessFile(lockFile, "rw").getChannel
    var lock: FileLock = null
    try {
      while (lock == null) {
        try {
          lock = lockChannel.tryLock()
        } catch {
          case e: OverlappingFileLockException =>
            lock = null
        }
        if (lock == null) {
          print("\rAcquiring lock...")
          Thread.sleep(1000)
        }
      }

      val extStreams = extJars.map(f => (f.getName, new FileInputStream(f): InputStream))
      val ffs        = FlatFileSystem.build(Paths.get(Config.libCache), extStreams ++ commonJars)
      val absffs     = new AbstractFlatFileSystem(ffs)

      val commonLibs = commonJars.map { case (name, _) => name -> absffs.roots(name) }
      val extLibMap = resolved.map {
        case (lib, files) => lib -> files.map(f => f.getName -> absffs.roots(f.getName)).toMap
      }.toMap

      (commonLibs, extLibMap, ffs)
    } finally {
      lock.release()
      lockChannel.close()
    }
  }

  /**
    * External libraries loaded from repository
    */
  log.debug("Loading external libraries")
  val (commonLibs, extLibraries, ffs) = loadLibraries(depLibs)

  /**
    * In memory cache of all the jars used in the compiler.
    */
  val commonLibraries4compiler: Seq[AbstractFile] = commonLibs.map { case (_, jar) => jar.root }

  private def extJars(extLibs: Set[ExtLib]): Seq[(String, AbstractFlatJar)] =
    extLibs.toSeq.flatMap(lib => extLibraries.getOrElse(lib, Map.empty).toSeq).distinctBy(_._1)

  def compilerLibraries(extLibs: Set[ExtLib]): Seq[AbstractFile] = {
    commonLibraries4compiler ++ extJars(extLibs).map(_._2.root)
  }

  /**
    * Linker 1.x için IRFile listesi. Jar adı + dosya yolu IRFile "sürümü"
    * olarak verilir; jar içerikleri sunucu ömrü boyunca sabit olduğundan
    * artımlı linker önbelleği bunlara güvenebilir.
    */
  private def irFilesOfJar(jarName: String, jar: FlatJar): Seq[IRFile] = {
    jar.files.filter(_.path.endsWith(".sjsir")).map { file =>
      val content = ffs.load(jar, file.path)
      new MemIRFileImpl(s"$jarName:${file.path}", org.scalajs.ir.Version.fromUTF8String(org.scalajs.ir.UTF8String(s"$jarName:${file.path}")), content)
    }
  }

  val linkerCaches = new LRUCache[Seq[IRFile]]("IRFiles")

  def linkerLibraries(extLibs: Set[ExtLib]): Seq[IRFile] = {
    this.synchronized {
      linkerCaches.getOrUpdate(
        extLibs, {
          val commonIr = commonLibs.flatMap { case (name, absJar) => irFilesOfJar(name, absJar.flatJar) }
          val extIr    = extJars(extLibs).flatMap { case (name, absJar) => irFilesOfJar(name, absJar.flatJar) }
          commonIr ++ extIr
        }
      )
    }
  }
}
