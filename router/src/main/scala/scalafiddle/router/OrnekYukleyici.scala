package scalafiddle.router

import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{Executors, ThreadFactory}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.matching.Regex

import scalafiddle.shared.{CompilationResponse, EditorAnnotation}

/** `// #yükle` (İngilizce Kojo'da `// #include`) satırlarını EDİTÖRDEN gelen derleme isteğinde
  * genişletir (kojojs-dev: "#yükle editörde de çalışsın").
  *
  * Aynı mantık editörün `/ornek/<yol>` rotasında (kojojs-editor, `koco.OrnekYukleyici`) zaten var; orada
  * dosya sunucuda açılıp editöre HAZIR genişletilmiş metin olarak yükleniyor. Burada kullanıcı `#yükle`
  * satırını KENDİ yazıyor ve metin editörde olduğu gibi kalıyor (masaüstü Kojo'daki gibi): genişletme
  * yalnız derlemeden hemen önce, burada, router'da yapılıyor.
  *
  * NEDEN ROUTER'DA: editörün istemcisi (Play 2.6 / sbt 0.13) CI'da ve geliştirici ortamlarında derlenmiyor;
  * router derleniyor ve sınanıyor. Router ile editör aynı konteynerde ve aynı örnek dizinini (KOCO_ORNEKLER)
  * görüyor. Ayrıca embed gibi editör dışı istemciler de aynı yoldan geçer.
  *
  * Kural (editördeki `OrnekYukleyici.genisletKod` ile aynı): içe alınanlar betiğin BAŞINA (iç içe alınanlar
  * önce), `#yükle` satırı yerinde kalır ama `#Yükle` diye işaretlenir. Satır SAYISI değişmez, yalnız başa
  * `eklenen` satır eklenir; bu yüzden derleyicinin hata satırları `geriEsle` ile editördeki satırlara
  * döndürülüyor.
  *
  * Tarayıcıda yerel dosya yok: içe alınabilen tek şey KOCO_ORNEKLER altındaki yansıtılmış betikler.
  * Hedefler `.kojo` / `.kojo.installed` ile sınırlı ve KÖK DIŞINA ÇIKAMAZ (sembolik bağ dahil): hedef
  * artık kullanıcıdan geliyor, editördeki gibi güvenilir bir dosyadan değil.
  */
object OrnekYukleyici {

  /** Kojo'nun mutlak (`/samples/tr/...`) yollarının kökü, örnek kökünün altında */
  val kaynakKoku: String = "masaustu/src/main/resources"

  /** İçe alınabilen dosya türleri (editördeki `uzantilar` ile aynı) */
  val uzantilar: Seq[String] = Seq(".kojo", ".kojo.installed")

  /** Genişletilmiş toplam içe alma boyutu üst sınırı (karakter). Kullanıcı betiği zaten 64 KB ile sınırlı;
    * bu sınır, küçük bir betiğin çok sayıda büyük dosyayı çağırıp bellek/derleyici zamanı yemesini önler. */
  val enFazlaEklenen: Int = 1024 * 1024

  /** Kaydedilmiş bir yazılımcığı getiren işlev: (kimlik, sürüm) -> ham kaynak (editörün `/raw/<id>/<sürüm>`ü). */
  type FiddleCoz = (String, Int) => Option[String]

  /** Hiçbir yazılımcık getirmeyen çözücü (sınama ve yazılımcık içe almanın kapalı olduğu durum). */
  val fiddleYok: FiddleCoz = (_, _) => None

  /** Tek bir `#yükle` çağrısında en çok kaç ayrı kayıtlı yazılımcık getirilir (editör sunucusuna yük ve
    * derleme gecikmesi sınırı). */
  val enFazlaFiddle: Int = 8

  /** Bir kayıtlı yazılımcığın en çok kaç bayt okunacağı. */
  val enFazlaFiddleBayti: Int = 256 * 1024

  /** Getirmeler bu havuzda (router'ın dağıtıcısı bloke olmasın); yalnız `#yükle` içeren istekler kullanır. */
  val ec: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(4, new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val t = new Thread(r, "yukle")
        t.setDaemon(true)
        t
      }
    })
  )

  private val yukleRE: Regex = """^\s*//\s*#(yükle|include)\s+(\S.*?)\s*$""".r

  // `/sf/<kimlik>/<sürüm>`: tam adres (`https://ikojo.fly.dev/sf/rNLmJw9/2`, `http://localhost:9000/sf/…`: kapı
  // numarası yok sayılır) ya da yalnız yol (`/sf/rNLmJw9/2`, `sf/rNLmJw9/2`); sürüm verilmezse 0 (editörün `/sf/:id` rotası da öyle). Kimlik biçimi editörünki
  // (ApiGuard.fiddleKimligiMi: 7 harf/rakam). Sondaki `?zrc=…`/`#…` yok sayılır.
  private val fiddleRE: Regex =
    """^(?:(?i:https?)://([^/\s?#:]+)(?::\d+)?)?/?sf/([0-9A-Za-z]{7})(?:/(\d{1,6}))?/?(?:[?#]\S*)?$""".r

  /** `#yükle` hedefi kayıtlı bir yazılımcık bağlantısıysa (sunucu adı, kimlik, sürüm). */
  def fiddleHedefi(hedef: String): Option[(Option[String], String, Int)] =
    hedef match {
      case fiddleRE(host, id, ver) => Some((Option(host).map(_.toLowerCase), id, Option(ver).fold(0)(_.toInt)))
      case _                       => None
    }

  /** Metinde (satır başında) herhangi bir `#yükle`/`#include` satırı var mı: ucuz ön denetim, yoksa genişletme
    * için ayrı iş parçacığına geçilmez. */
  def yukleVarMi(kaynak: String): Boolean = yukleSatiriRE.findFirstIn(kaynak).isDefined
  private val yukleSatiriRE: Regex = """(?m)^\s*//\s*#(yükle|include)\s+\S""".r

  // Editör istemcisiyle (FiddleEditor.extractCode) ve editördeki OrnekYukleyici ile aynı işaretler
  private val fiddleStartRE: Regex = """\s*// \$FiddleStart\s*$""".r
  private val fiddleEndRE: Regex   = """\s*// \$FiddleEnd\s*$""".r

  /** Genişletme sonucu. Satır numaraları 0'dan başlar (derleyicinin `EditorAnnotation.row`'u gibi).
    *
    * @param kaynak       derleyiciye gidecek genişletilmiş kaynak
    * @param ekleneBasi   genişletilmiş kaynakta eklenen bloğun ilk satırı
    * @param eklenen      eklenen satır sayısı (bloğun uzunluğu)
    * @param ilkYukleSatiri özgün kaynakta ilk `#yükle` satırı (içe alınan koddaki hatalar buraya bağlanır)
    * @param dosyalar     eklenen bloğun her satırının ait olduğu dosya (en içteki), uzunluk = `eklenen`
    * @param uyarilar     içe alınamayanlar: (özgün kaynakta ilgili `#yükle` satırı, açıklama). İç içe olanlar,
    *                     onları çağıran üst düzey `#yükle` satırına bağlanır.
    */
  case class Genisletme(
      kaynak: String,
      ekleneBasi: Int,
      eklenen: Int,
      ilkYukleSatiri: Int,
      dosyalar: Vector[String],
      uyarilar: Seq[(Int, String)]
  )

  /** Kaynakta `$FiddleStart`/`$FiddleEnd` arasında `#yükle` varsa genişletir; yoksa (ya da işaret yoksa) None:
    * hiçbir şeye dokunulmaz, derleme eskisi gibi. `kok` örnek dizini. */
  def expandEditorSource(
      kok: Path,
      kaynak: String,
      fiddleCoz: FiddleCoz = fiddleYok,
      izinliHostlar: Set[String] = Set("localhost")
  ): Option[Genisletme] = {
    val satirlar = kaynak.split("\n", -1).toVector
    val bas      = satirlar.indexWhere(s => fiddleStartRE.unapplySeq(s).isDefined)
    val son      = satirlar.indexWhere(s => fiddleEndRE.unapplySeq(s).isDefined)
    if (bas < 0 || son <= bas) None
    else {
      val govdeAraligi = (bas + 1) until son
      val ilk          = govdeAraligi.find(i => yukleRE.unapplySeq(satirlar(i)).isDefined)
      ilk.map { ilkSatir =>
        val d       = new Durum(kok.toAbsolutePath.normalize, fiddleCoz, izinliHostlar.map(_.toLowerCase))
        val eklenen = mutable.ArrayBuffer[(String, String)]() // (satır, ait olduğu dosya)
        val yeniGovde = govdeAraligi.map { i =>
          satirlar(i) match {
            case yukleRE(pragma, hedef) =>
              d.satir = i
              val a = ic(d, d.kok.resolve("-"), pragma, hedef, eklenen)
              s"// #${pragma.capitalize} $hedef -- $a" // Kojo da işlenmiş satırı böyle işaretliyor: #Yükle
            case s => s
          }
        }
        val sonuc =
          satirlar.take(bas + 1) ++ eklenen.map(_._1) ++ yeniGovde ++ satirlar.drop(son)
        Genisletme(
          kaynak = sonuc.mkString("\n"),
          ekleneBasi = bas + 1,
          eklenen = eklenen.size,
          ilkYukleSatiri = ilkSatir,
          dosyalar = eklenen.map(_._2).toVector,
          uyarilar = d.uyarilar.toList
        )
      }
    }
  }

  /** Genişletilmiş kaynaktaki derleme hatalarını editördeki (özgün) satırlara döndürür.
    *   - eklenen bloğun ÖNÜNDEKİ satırlar aynı;
    *   - eklenen bloğun İÇİNDEKİLER ilk `#yükle` satırına bağlanır, metne `[dosya]` öneki konur
    *     (editörde o dosyanın satırı yok; hata yine de görünür kalsın);
    *   - bloğun ARKASINDAKİLER `eklenen` kadar yukarı kayar.
    * `log` (derleyicinin ham metni) olduğu gibi kalır; istemci onu satır eşlemesinde kullanmıyor. */
  def geriEsle(g: Genisletme, yanit: CompilationResponse): CompilationResponse = {
    val bitis = g.ekleneBasi + g.eklenen
    val ek    = if (g.uyarilar.isEmpty) "" else g.uyarilar.map { case (_, t) => t }.mkString("", "\n", "\n")
    val esle = yanit.annotations.map { a =>
      if (a.row < g.ekleneBasi) a
      else if (a.row < bitis) {
        val dosya = g.dosyalar.lift(a.row - g.ekleneBasi).getOrElse("")
        // önek yalnız ilk satıra: derleyicinin iletisi çok satırlı (ileti, kod satırı, ^ işareti)
        val metin = a.text.zipWithIndex.map { case (t, i) => if (i == 0) s"[$dosya] $t" else t }
        a.copy(row = g.ilkYukleSatiri, col = 0, text = metin)
      } else a.copy(row = a.row - g.eklenen)
    }
    // Derleme BAŞARILIYSA istemci açıklamaları göstermiyor; başarısızsa kullanıcı "tanımsız ad" hatalarının
    // nedenini (içe alınamayan dosya) görsün diye ilgili #yükle satırına uyarı konur.
    val uyari =
      if (yanit.jsCode.isEmpty) g.uyarilar.map { case (r, t) => EditorAnnotation(r, 0, Seq(t.stripPrefix("// ")), "warning") }
      else Nil
    yanit.copy(annotations = esle ++ uyari, log = ek + yanit.log)
  }

  /** Genişletme boyunca taşınan değişken durum. */
  private final class Durum(val kok: Path, val fiddleCoz: FiddleCoz, val izinliHostlar: Set[String]) {
    val alinanlar = mutable.HashSet[String]() // dosya yolu ya da "sf:<kimlik>/<sürüm>"
    val uyarilar  = mutable.ArrayBuffer[(Int, String)]()
    var toplam    = 0 // içe alınan toplam karakter (iç içe olanlar dahil)
    var fiddleler = 0 // getirilen kayıtlı yazılımcık sayısı
    var satir     = 0 // işlenen üst düzey #yükle satırının özgün kaynaktaki numarası
  }

  /** `#yükle` hedefini çözer ve içeriğini (özyineli) `eklenen`e katar; satırın açıklamasını döndürür. */
  private def ic(
      d: Durum,
      icAlan: Path,
      pragma: String,
      hedef: String,
      eklenen: mutable.ArrayBuffer[(String, String)]
  ): String = {
    val isaret = pragma.capitalize
    def uyari(neden: String): String = {
      d.uyarilar += ((d.satir, s"// #$isaret $hedef -- $neden"))
      neden
    }
    if (hedef.startsWith("~")) uyari("ev dizini (~) tarayıcıda yok; bu dosya masaüstü Koco'ya özel, içe alınmadı")
    else
      fiddleHedefi(hedef) match {
        case Some((host, id, ver)) => fiddleAl(d, pragma, hedef, host, id, ver, eklenen, uyari)
        case None =>
          hedefCoz(d.kok, icAlan, hedef) match {
            case Some(p) if d.alinanlar.contains(p.toString) => "daha önce alındı"
            case Some(_) if d.toplam > enFazlaEklenen =>
              uyari(s"içe alma sınırı (${enFazlaEklenen / 1024} KB) aşıldı, içe alınmadı")
            case Some(p) =>
              d.alinanlar += p.toString
              val goreli = d.kok.relativize(p).toString.replace('\\', '/')
              oku(p) match {
                case Some(icerik) =>
                  blokEkle(d, pragma, hedef, goreli, p, icerik.split("\n", -1).toVector, eklenen)
                  "içeriği yukarıya alındı"
                case None => uyari(s"$goreli okunamadı, içe alınmadı")
              }
            case None => uyari("dosya bulunamadı, içe alınmadı")
          }
      }
  }

  /** Kayıtlı bir yazılımcık bağlantısını (`/sf/<kimlik>/<sürüm>`) içe alır. Adres başka bir sunucunun ise
    * (kullanıcı oradan getirmeyi bekler ama biz kendi kaydımızdan getirirdik) REDDEDİLİR: yanlış betik yüklemekten
    * iyidir. Getirme yalnız yapılandırılmış editör sunucusundan (kimlik ve sürüm doğrulanmış sabit biçimde),
    * yani dışarıya istek açmıyor. */
  private def fiddleAl(
      d: Durum,
      pragma: String,
      hedef: String,
      host: Option[String],
      id: String,
      ver: Int,
      eklenen: mutable.ArrayBuffer[(String, String)],
      uyari: String => String
  ): String = {
    val etiket = s"sf/$id/$ver"
    val anahtar = s"sf:$id/$ver"
    if (host.exists(h => !d.izinliHostlar.contains(h)))
      uyari(s"adres bu sunucunun değil (${host.get}), içe alınmadı")
    else if (d.alinanlar.contains(anahtar)) "daha önce alındı"
    else if (d.toplam > enFazlaEklenen) uyari(s"içe alma sınırı (${enFazlaEklenen / 1024} KB) aşıldı, içe alınmadı")
    else if (d.fiddleler >= enFazlaFiddle) uyari(s"bir seferde en çok $enFazlaFiddle kayıtlı betik alınır, içe alınmadı")
    else {
      d.alinanlar += anahtar
      d.fiddleler += 1
      d.fiddleCoz(id, ver) match {
        case None => uyari(s"$etiket bulunamadı, içe alınmadı")
        case Some(ham) =>
          govdeCikar(ham.replace("\r", "")) match {
            case Some(govde) =>
              blokEkle(d, pragma, hedef, etiket, d.kok.resolve("-"), govde, eklenen)
              "içeriği yukarıya alındı"
            case None => uyari(s"$etiket okunamadı, içe alınmadı")
          }
      }
    }
  }

  /** Kayıtlı bir yazılımcığın tam kaynağından yalnız `$FiddleStart`/`$FiddleEnd` arasını (kullanıcının
    * kodunu) alır; işaret yoksa None (sarmalayıcıyı da içe almak derlemeyi bozar). */
  def govdeCikar(kaynak: String): Option[Vector[String]] = {
    val satirlar = kaynak.split("\n", -1).toVector
    val bas      = satirlar.indexWhere(s => fiddleStartRE.unapplySeq(s).isDefined)
    val son      = satirlar.indexWhere(s => fiddleEndRE.unapplySeq(s).isDefined)
    if (bas < 0 || son <= bas) None else Some(satirlar.slice(bas + 1, son))
  }

  /** Bir dosyanın/yazılımcığın satırlarını işler (kendi içe aldıkları başa, gövde arkaya; editördeki
    * genisletKod gibi) ve `eklenen`e `başı`/`sonu` işaretleriyle ekler. `icAlan`: içeride göreli `#yükle`
    * hedeflerinin çözüleceği yer (dosya için kendi yolu; yazılımcık için kök). */
  private def blokEkle(
      d: Durum,
      pragma: String,
      hedef: String,
      etiket: String,
      icAlan: Path,
      satirlar: Vector[String],
      eklenen: mutable.ArrayBuffer[(String, String)]
  ): Unit = {
    d.toplam += satirlar.map(_.length + 1).sum
    val icEklenen = mutable.ArrayBuffer[(String, String)]()
    val icGovde   = mutable.ArrayBuffer[(String, String)]()
    satirlar.foreach {
      case yukleRE(pr, h) =>
        val a = ic(d, icAlan, pr, h, icEklenen)
        icGovde += ((s"// #${pr.capitalize} $h -- $a", etiket))
      case satir => icGovde += ((satir, etiket))
    }
    eklenen += ((s"// --- #$pragma $hedef başı ($etiket) ---", etiket))
    eklenen ++= icEklenen
    eklenen ++= icGovde
    eklenen += ((s"// --- #$pragma $hedef sonu ---", etiket))
  }

  /** Editörün `/raw/<kimlik>/<sürüm>` ucundan kayıtlı yazılımcığı getirir (`tabanUrl` = `scalaFiddleSourceUrl`,
    * sonu `/` ile). Süre ve boyut sınırlı; 200 dışı, hata ya da büyük gövde = None. Bloke eder: `ec` havuzunda
    * çağrılmalı. */
  def httpFiddleCoz(tabanUrl: String, zamanAsimiMs: Int = 3000): FiddleCoz = (id, ver) =>
    Try {
      val url = URI.create(s"${tabanUrl.stripSuffix("/")}/$id/$ver").toURL
      val c   = url.openConnection().asInstanceOf[HttpURLConnection]
      c.setConnectTimeout(zamanAsimiMs)
      c.setReadTimeout(zamanAsimiMs)
      c.setInstanceFollowRedirects(false)
      try {
        if (c.getResponseCode != 200) None
        else {
          val in  = c.getInputStream
          val buf = new java.io.ByteArrayOutputStream()
          val b   = new Array[Byte](8192)
          var n   = in.read(b)
          while (n >= 0 && buf.size <= enFazlaFiddleBayti) { buf.write(b, 0, n); n = in.read(b) }
          if (buf.size > enFazlaFiddleBayti) None else Some(new String(buf.toByteArray, StandardCharsets.UTF_8))
        }
      } finally c.disconnect()
    }.toOption.flatten

  private def oku(dosya: Path): Option[String] =
    Try {
      if (Files.size(dosya) > enFazlaEklenen) None
      else Some(new String(Files.readAllBytes(dosya), StandardCharsets.UTF_8).replace("\r", ""))
    }.toOption.flatten

  /** Kojo'nun addKojoExtension'ı: dosya adında nokta yoksa `.kojo` ekle */
  private def kojoUzantisi(ad: String): String = {
    val salt = ad.substring(ad.lastIndexOf('/') + 1)
    if (salt.contains(".")) ad else ad + ".kojo"
  }

  /** `#yükle` hedefini dosyaya çevirir (editördeki ile aynı arama sırası). Mutlak (`/...`) hedef
    * `<kök>/masaustu/src/main/resources` (yedek: kökün kendisi) altında; göreli hedef içe alan dosyanın
    * dizininde, sonra üst dizinlerinde (köke kadar) aranır.
    *
    * Editördeki sürümden FARKI (hedef kullanıcıdan geliyor): yalnız `uzantilar` türleri; ve adayın GERÇEK yolu
    * (sembolik bağlar çözülmüş) kök içinde kalmalı. */
  private def hedefCoz(kok: Path, icAlan: Path, hedef0: String): Option[Path] = {
    val hedef = kojoUzantisi(hedef0)
    if (hedef.contains("\u0000") || hedef.contains("\\") || !uzantilar.exists(hedef.endsWith)) None
    else {
      val adaylar: Seq[Path] =
        if (hedef.startsWith("/")) {
          val goreli = hedef.dropWhile(_ == '/')
          Seq(kok.resolve(kaynakKoku).resolve(goreli), kok.resolve(goreli))
        } else {
          val dizinler = Iterator
            .iterate(icAlan.getParent)(_.getParent)
            .takeWhile(d => d != null && d.startsWith(kok))
            .toList
          dizinler.map(_.resolve(hedef))
        }
      val kokGercek = Try(kok.toRealPath()).toOption
      adaylar
        .map(_.normalize)
        .find { p =>
          p.startsWith(kok) && Files.isRegularFile(p) &&
          kokGercek.exists(k => Try(p.toRealPath()).toOption.exists(_.startsWith(k)))
        }
    }
  }
}
