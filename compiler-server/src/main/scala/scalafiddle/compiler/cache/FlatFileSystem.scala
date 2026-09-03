package scalafiddle.compiler.cache

import java.io.{FileOutputStream, InputStream, RandomAccessFile}
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipInputStream}

import org.slf4j.LoggerFactory
import org.xerial.snappy.Snappy
import upickle.default._

case class FlatFile(path: String, offset: Long, compressedSize: Int, origSize: Int)

object FlatFile { implicit val rw: ReadWriter[FlatFile] = macroRW }

// hash: jar İÇERİĞİNİN sha-1'i. Önbellek ada değil ad+içeriğe göre tekilleşir;
// aynı adla farklı içerik (ör. yeni deploy'da değişen page jar'ı) eski sürümün
// yerine geçer. Bu olmadan bayat offset'ler çöp veri okutuyordu (Faz 3'te
// dakikalarca askıda kalan derleme olarak ortaya çıktı).
case class FlatJar(name: String, files: Seq[FlatFile], hash: String = "")

object FlatJar { implicit val rw: ReadWriter[FlatJar] = macroRW }

// Faz 3: larray (2.13 sürümü yok) yerine java.nio FileChannel -- her dosya
// için ilgili bölge okunup Snappy ile bellek içinde açılıyor.
class FlatFileSystem(dataChannel: FileChannel, val jars: Seq[FlatJar], val index: Map[String, FlatFile]) {

  def exists(path: String) = index.contains(path)

  // jar başına yol -> dosya indeksi; eski kodun her okumada yaptığı doğrusal
  // tarama (25k+ öge) soğuk derlemeyi dakikalara çıkarıyordu
  private lazy val jarIndex: Map[String, Map[String, FlatFile]] =
    jars.iterator.map(j => j.name -> j.files.iterator.map(f => f.path -> f).toMap).toMap

  def load(flatJar: FlatJar, path: String): Array[Byte] = {
    load(jarIndex(flatJar.name)(path))
  }

  def load(path: String): Array[Byte] = {
    load(index(path))
  }

  def load(file: FlatFile): Array[Byte] = {
    val compressed = java.nio.ByteBuffer.allocate(file.compressedSize)
    var pos        = file.offset
    while (compressed.hasRemaining) {
      val n = dataChannel.read(compressed, pos)
      if (n < 0) throw new java.io.EOFException(s"Unexpected EOF reading ${file.path}")
      pos += n
    }
    Snappy.uncompress(compressed.array())
  }

  def filter(f: Set[String]): FlatFileSystem = {
    val newJars = jars.filter(j => f.contains(j.name))
    new FlatFileSystem(dataChannel, newJars, FlatFileSystem.createIndex(newJars))
  }
}

object FlatFileSystem {
  val log = LoggerFactory.getLogger(getClass)

  def apply(location: Path): FlatFileSystem = {
    location.toFile.mkdirs()
    val jars                         = readMetadata(location)
    val index: Map[String, FlatFile] = createIndex(jars)
    new FlatFileSystem(openData(location), jars, index)
  }

  private def openData(location: Path): FileChannel =
    new RandomAccessFile(location.resolve("data").toFile, "r").getChannel

  private def createIndex(jars: Seq[FlatJar]): Map[String, FlatFile] = {
    jars.iterator.flatMap(_.files.map(file => (file.path, file))).toMap
  }

  private def readMetadata(location: Path): Seq[FlatJar] = {
    read[Seq[FlatJar]](new String(Files.readAllBytes(location.resolve("index.json")), StandardCharsets.UTF_8))
  }

  private val validExtensions = Set("class", "sjsir")
  private def validFile(entry: ZipEntry) = {
    val ext = entry.getName.split('.').last
    !entry.isDirectory && validExtensions.contains(ext)
  }

  private def sha1(bytes: Array[Byte]): String =
    java.security.MessageDigest.getInstance("SHA-1").digest(bytes).map("%02x".format(_)).mkString

  def build(location: Path, jars: Seq[(String, InputStream)]): FlatFileSystem = {
    // if metadata already exists, read it in
    val existingJars = if (location.resolve("index.json").toFile.exists()) readMetadata(location) else Seq.empty[FlatJar]

    // içerik karmasını hesaplayabilmek için akışları belleğe al
    val jarBytes = jars.map { case (name, stream) => (name, stream.readAllBytes(), ()) }
      .map { case (name, bytes, _) => (name, bytes, sha1(bytes)) }

    val newJars = jarBytes.filterNot { case (name, _, hash) =>
      existingJars.exists(j => j.name == name && j.hash == hash)
    }

    // make location path
    location.toFile.mkdirs()

    val dataFile = location.resolve("data").toFile
    val fos      = new FileOutputStream(dataFile, true)
    var offset   = dataFile.length()

    // read through all new JARs, append contents to data and create metadata
    val addedJars = newJars.map { case (name, bytes, hash) =>
      log.debug(s"Extracting JAR $name")
      val jarStream = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))
      val entries = Iterator
        .continually(jarStream.getNextEntry)
        .takeWhile(_ != null)
        .filter(validFile)

      val files = entries.map { entry =>
        // read and compress the file
        val content    = jarStream.readAllBytes()
        val compressed = Snappy.compress(content)
        fos.write(compressed)
        val ff = FlatFile(entry.getName, offset, compressed.length, content.length)
        offset += compressed.length
        ff
      }.toList
      jarStream.close()
      FlatJar(name, files, hash)
    }
    fos.close()

    // aynı adın eski (farklı içerikli) kaydı düşer -- veri dosyasındaki eski
    // baytlar ölü kalır, sorun değil; index her ada tek (güncel) kayıt tutar
    val finalJars = existingJars.filterNot(j => addedJars.exists(_.name == j.name)) ++ addedJars
    val json      = write(finalJars)
    Files.write(location.resolve("index.json"), json.getBytes(StandardCharsets.UTF_8))

    new FlatFileSystem(openData(location), finalJars, createIndex(finalJars))
  }
}
