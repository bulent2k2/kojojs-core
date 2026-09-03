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
  *  - Kütüphane eşlemesi jar ADI üzerinden; modül çakışmaları (aynı modülün
  *    farklı sürümleri) ComparableVersion ile en yüksek sürüme çözülür
  *    (coursier 1'deki eski çözümlemenin karşılığı).
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
  import org.apache.maven.artifact.versioning.ComparableVersion

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
    // ExtLib -> jar listesi eşlemesi elde kalsın; modül+sürüm bilgisini de
    // koru ki çakışmalar sürüme göre çözülebilsin
    val resolved: Seq[(ExtLib, Seq[(Module, String, File)])] = libs.map { lib =>
      val result = Fetch()
        .withRepositories(repositories)
        .addDependencies(toDependency(lib))
        .runResult()
      val jars = result.detailedArtifacts.collect {
        case (dep, _, _, file) if file.getName.endsWith(".jar") => (dep.module, dep.version, file)
      }
      lib -> jars
    }

    // aynı modül birden çok sürümle çözülmüşse en yükseği kazanır; elenenler
    // loglanır. Her kütüphanenin classpath'i kazanan jar'a yönlendirilir.
    val chosenByModule: Map[Module, File] = resolved
      .flatMap(_._2)
      .groupBy(_._1)
      .map {
        case (module, candidates) =>
          val byVersion = candidates.distinctBy(_._2)
          val chosen    = byVersion.maxBy(c => new ComparableVersion(c._2))
          if (byVersion.size > 1)
            log.warn(
              s"Multiple versions of $module resolved: ${byVersion.map(_._2).sorted.mkString(", ")} -- using ${chosen._2}")
          module -> chosen._3
      }

    val extJars = chosenByModule.values.toSeq.distinctBy(_.getName)

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
        case (lib, jars) =>
          lib -> jars.map {
            case (module, _, _) =>
              val f = chosenByModule(module)
              f.getName -> absffs.roots(f.getName)
          }.toMap
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
    * Linker 1.x için IRFile listesi. IRFile "sürümü" jar İÇERİĞİNİN sha-1'i
    * + dosya yolundan türetilir: aynı adla farklı içerik gelirse (ör. yeni
    * deploy'da değişen page jar'ı) artımlı linker önbelleği bayatlamaz.
    * (hash alanı boş olan eski önbellek kayıtları için jar adına düşülür.)
    */
  private def irFilesOfJar(jarName: String, jar: FlatJar): Seq[IRFile] = {
    val jarTag = if (jar.hash.nonEmpty) jar.hash else jarName
    jar.files.filter(_.path.endsWith(".sjsir")).map { file =>
      val content = ffs.load(jar, file.path)
      val version = org.scalajs.ir.Version.fromUTF8String(org.scalajs.ir.UTF8String(s"$jarTag:${file.path}"))
      new MemIRFileImpl(s"$jarName:${file.path}", version, content)
    }
  }

  /** Önbellek veri kanalını kapatır; yönetici değiştirilirken çağrılır. */
  def close(): Unit = ffs.close()

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
