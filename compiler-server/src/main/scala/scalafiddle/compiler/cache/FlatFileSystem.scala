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

  def close(): Unit = dataChannel.close()
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
    val name = entry.getName
    val dot  = name.lastIndexOf('.')
    val ext  = if (dot < 0) "" else name.substring(dot + 1)
    !entry.isDirectory && validExtensions.contains(ext)
  }

  private def sha1(bytes: Array[Byte]): String =
    java.security.MessageDigest.getInstance("SHA-1").digest(bytes).map("%02x".format(_)).mkString

  def build(location: Path, jars: Seq[(String, InputStream)]): FlatFileSystem = {
    // if metadata already exists, read it in
    val existingJars = if (location.resolve("index.json").toFile.exists()) readMetadata(location) else Seq.empty[FlatJar]

    // içerik karmasını hesaplayabilmek için akışları belleğe al
    val jarBytes = jars.map {
      case (name, stream) =>
        val bytes = try stream.readAllBytes() finally stream.close()
        (name, bytes, sha1(bytes))
    }

    val newJars = jarBytes.filterNot { case (name, _, hash) =>
      existingJars.exists(j => j.name == name && j.hash == hash)
    }

    // make location path
    location.toFile.mkdirs()

    val dataFile = location.resolve("data").toFile

    // adı yeni jar'larca değiştirilmeyen mevcut kayıtlar; değiştirilenlerin
    // veri dosyasındaki baytları ölü kalır
    val survivors = existingJars.filterNot(j => newJars.exists(_._1 == j.name))

    // ölü bayt canlı baytı aşınca veri dosyasını sıkıştır -- yoksa her
    // içeriği değişen jar (ör. yeniden stage'lenen page jar'ı) dosyayı
    // sınırsız büyütür
    val compacted: Seq[FlatJar] = {
      val live = survivors.iterator.flatMap(_.files).map(_.compressedSize.toLong).sum
      val dead = dataFile.length() - live
      if (dead > 0 && dead > live) {
        log.info(s"Compacting cache data file: $dead dead bytes, $live live bytes")
        val tmpFile   = location.resolve("data.tmp").toFile
        val in        = new RandomAccessFile(dataFile, "r").getChannel
        val out       = new FileOutputStream(tmpFile)
        var newOffset = 0L
        val rewritten = try {
          survivors.map { jar =>
            val newFiles = jar.files.map { f =>
              val buf = java.nio.ByteBuffer.allocate(f.compressedSize)
              var pos = f.offset
              while (buf.hasRemaining) {
                val n = in.read(buf, pos)
                if (n < 0) throw new java.io.EOFException(s"Unexpected EOF compacting ${f.path}")
                pos += n
              }
              out.write(buf.array())
              val nf = f.copy(offset = newOffset)
              newOffset += f.compressedSize
              nf
            }
            jar.copy(files = newFiles)
          }
        } finally {
          in.close()
          out.close()
        }
        Files.move(tmpFile.toPath, dataFile.toPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        // eski index yeni ofsetlerle tutarsız; süreç tam burada ölürse bayat
        // index çöp okutmasın diye sıkıştırılmış hali hemen kalıcılaştır
        Files.write(location.resolve("index.json"), write(rewritten).getBytes(StandardCharsets.UTF_8))
        rewritten
      } else survivors
    }

    val fos    = new FileOutputStream(dataFile, true)
    var offset = dataFile.length()

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

    // index her ada tek (güncel) kayıt tutar; eski sürümlerin ölü baytları
    // yukarıdaki sıkıştırma eşiğiyle geri kazanılır
    val finalJars = compacted ++ addedJars
    val json      = write(finalJars)
    Files.write(location.resolve("index.json"), json.getBytes(StandardCharsets.UTF_8))

    new FlatFileSystem(openData(location), finalJars, createIndex(finalJars))
  }
}
