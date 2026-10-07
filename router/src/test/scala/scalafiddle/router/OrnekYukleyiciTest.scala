package scalafiddle.router

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scalafiddle.shared.{CompilationResponse, EditorAnnotation}

/** Editördeki `// #yükle` genişletmesi (router tarafı). Editörün `/ornek` rotasındaki `OrnekYukleyici`
  * ile AYNI kuralları izlemeli; üstüne kullanıcıdan gelen hedefler için güvenlik sınırları ve satır
  * eşlemesi sınanıyor.
  */
class OrnekYukleyiciTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  var kok: Path   = _
  var dis: Path   = _ // kök DIŞINDA bir dosya (sızma sınaması)

  private def yaz(goreli: String, icerik: String): Path = {
    val p = kok.resolve(goreli)
    Files.createDirectories(p.getParent)
    Files.write(p, icerik.getBytes(StandardCharsets.UTF_8))
    p
  }

  override def beforeAll(): Unit = {
    kok = Files.createTempDirectory("ornekler-router")
    dis = Files.createTempFile("kok-disi", ".kojo")
    Files.write(dis, "dez sızdı = 1\n".getBytes(StandardCharsets.UTF_8))
    val r = "masaustu/src/main/resources"
    yaz(s"$r/samples/tr/tanimlar.kojo", "dez sayfa = 1\n")
    yaz(s"$r/samples/tr/turler.kojo", "// #yükle /samples/tr/tanimlar\ndez türler = 2\n")
    yaz(s"$r/samples/tr/dongu-a.kojo", "// #yükle /samples/tr/dongu-b\ndez a = 1\n")
    yaz(s"$r/samples/tr/dongu-b.kojo", "// #yükle /samples/tr/dongu-a\ndez b = 2\n")
    yaz(s"$r/samples/tr/metin.txt", "gizli\n")
    yaz("01-ilk-adimlar.kojo", "sil()\n")
    yaz("othello/tr/otello.kojo", "// #yükle tr/ana\nsatıryaz(ana)\n")
    yaz("othello/tr/ana.kojo", "dez ana = 7\n")
    // kök içinden dışarı sembolik bağ
    Files.createSymbolicLink(kok.resolve(s"$r/samples/tr/bag.kojo"), dis)
  }

  override def afterAll(): Unit = {
    Files.deleteIfExists(dis)
  }

  /** Editörün gönderdiği biçim: sarmalayıcı + gövde. `// $FiddleStart` / `// $FiddleEnd` arası kullanıcı. */
  private def sar(govde: String*): String =
    (Seq("import fiddle.Fiddle.println", "object ScalaFiddle {", "  // $FiddleStart") ++ govde ++
      Seq("  // $FiddleEnd", "}")).mkString("\n")

  private def genislet(govde: String*) = OrnekYukleyici.expandEditorSource(kok, sar(govde: _*))

  test("#yükle yoksa dokunulmaz (None): derleme eskisi gibi") {
    OrnekYukleyici.expandEditorSource(kok, sar("sil()", "ileri(10)")) shouldBe None
  }

  test("işaretler yoksa (tam program) dokunulmaz") {
    OrnekYukleyici.expandEditorSource(kok, "// #yükle /samples/tr/tanimlar\nobject X\n") shouldBe None
  }

  test("içe alınan, gövdenin başına eklenir; #yükle satırı yerinde kalır ve #Yükle diye işaretlenir") {
    val g = genislet("satıryaz(sayfa)", "// #yükle /samples/tr/tanimlar", "ileri(10)").get
    val s = g.kaynak.split("\n").toVector
    s should contain("dez sayfa = 1")
    // içerik gövdeden ÖNCE, FiddleStart'tan hemen sonra
    val bas = s.indexWhere(_.contains("$FiddleStart"))
    s(bas + 1) should startWith("// --- #yükle /samples/tr/tanimlar başı (")
    s.indexOf("dez sayfa = 1") should be < s.indexOf("satıryaz(sayfa)")
    s should contain("// #Yükle /samples/tr/tanimlar -- içeriği yukarıya alındı")
    g.eklenen shouldBe 4 // başı + 2 satır (dosya sonundaki boş satır editördeki gibi korunur) + sonu
    g.ekleneBasi shouldBe bas + 1
  }

  test("iç içe: içeridekinin içe aldıkları kendi dosyasının başına gelir") {
    val g = genislet("// #yükle /samples/tr/turler").get
    val s = g.kaynak.split("\n").toVector
    s.indexOf("dez sayfa = 1") should be < s.indexOf("dez türler = 2")
    g.dosyalar.count(_.endsWith("tanimlar.kojo")) should be >= 1
  }

  test("göreli hedef kökten çözülür; uzantısız ada .kojo eklenir") {
    val g = genislet("// #yükle othello/tr/otello").get
    g.kaynak should include("dez ana = 7")
    g.kaynak should include("satıryaz(ana)")
  }

  test("aynı dosya iki kez alınmaz; döngü korunur") {
    val g = genislet("// #yükle /samples/tr/tanimlar", "// #yükle /samples/tr/tanimlar").get
    g.kaynak.split("\n").count(_ == "dez sayfa = 1") shouldBe 1
    g.kaynak should include("-- daha önce alındı")
    val d = genislet("// #yükle /samples/tr/dongu-a").get
    d.kaynak.split("\n").count(_ == "dez a = 1") shouldBe 1
    d.kaynak.split("\n").count(_ == "dez b = 2") shouldBe 1
  }

  test("#include de aynı: İngilizce Kojo") {
    val g = genislet("// #include /samples/tr/tanimlar").get
    g.kaynak should include("dez sayfa = 1")
    g.kaynak should include("// #Include /samples/tr/tanimlar -- içeriği yukarıya alındı")
  }

  test("bulunamayan, ev dizini ve boş hedefler: açıklayıcı yorum + uyarı, derleme sürer") {
    val g = genislet("// #yükle /yok/yok", "// #yükle ~/x.kojo").get
    g.kaynak should include("// #Yükle /yok/yok -- dosya bulunamadı, içe alınmadı")
    g.kaynak should include("// #Yükle ~/x.kojo -- ev dizini (~) tarayıcıda yok")
    g.eklenen shouldBe 0
    g.uyarilar.size shouldBe 2
  }

  test("GÜVENLİK: kök dışına çıkma, mutlak yol, sembolik bağ, yanlış uzantı reddedilir") {
    // ../ ile kaçış (kök dışındaki gerçek bir .kojo dosyası)
    val kacis = "../" + dis.getFileName.toString
    val adlar = Seq(
      s"// #yükle $kacis",
      s"// #yükle ../../../../../../../../${dis.toAbsolutePath.toString.stripPrefix("/")}",
      s"// #yükle ${dis.toAbsolutePath}", // mutlak yol: kök altında aranır, orada yok
      "// #yükle /samples/tr/bag", // kök içinden dışarı sembolik bağ
      "// #yükle /samples/tr/metin.txt", // .kojo değil
      "// #yükle /etc/passwd",
      "// #yükle ..\\..\\x.kojo"
    )
    adlar.foreach { satir =>
      val g = genislet(satir).get
      g.kaynak should not include "sızdı"
      g.kaynak should not include "gizli"
      g.eklenen shouldBe 0
      g.uyarilar should not be empty
    }
  }

  test("satır sayısı korunur: yalnız başa 'eklenen' satır eklenir") {
    val oz = sar("a", "// #yükle /samples/tr/tanimlar", "b")
    val g  = OrnekYukleyici.expandEditorSource(kok, oz).get
    g.kaynak.split("\n").length shouldBe oz.split("\n").length + g.eklenen
  }

  // ---- hata satırlarını geri eşleme ----
  private def hata(satir: Int, metin: String = "boom") = EditorAnnotation(satir, 4, Seq(metin), "error")

  test("geriEsle: eklenenin ÖNÜNDEKİ satırlar aynı, ARKASINDAKİLER eklenen kadar yukarı") {
    val g    = genislet("sil()", "// #yükle /samples/tr/tanimlar", "ileri(10)").get
    val yanit = CompilationResponse(None, Seq(hata(1), hata(g.ekleneBasi + g.eklenen + 2)), "log")
    val d    = OrnekYukleyici.geriEsle(g, yanit)
    d.annotations.map(_.row) shouldBe Seq(1, g.ekleneBasi + 2) // 2. satır ileri(10)'un özgün satırı
    d.annotations.map(_.text) shouldBe Seq(Seq("boom"), Seq("boom"))
  }

  test("geriEsle: içe alınan dosyadaki hata ilk #yükle satırına bağlanır, [dosya] önekiyle") {
    val g     = genislet("sil()", "// #yükle /samples/tr/tanimlar", "ileri(10)").get
    val yanit = CompilationResponse(None, Seq(EditorAnnotation(g.ekleneBasi + 1, 3, Seq("not found: value x", "  satır", "  ^"), "error")), "log")
    val d     = OrnekYukleyici.geriEsle(g, yanit)
    d.annotations.head.text.drop(1) shouldBe Seq("  satır", "  ^") // önek yalnız ilk satırda
    d.annotations.head.row shouldBe g.ilkYukleSatiri
    d.annotations.head.col shouldBe 0
    d.annotations.head.text.head should startWith("[masaustu/src/main/resources/samples/tr/tanimlar.kojo] ")
    d.annotations.head.text.head should endWith("not found: value x")
  }

  test("geriEsle: uyarılar log'un başına yazılır; genişletilmeyen yanıtta log olduğu gibi") {
    val g = genislet("// #yükle /yok/yok").get
    OrnekYukleyici.geriEsle(g, CompilationResponse(None, Nil, "L")).log shouldBe
      "// #Yükle /yok/yok -- dosya bulunamadı, içe alınmadı\nL"
    val h = genislet("// #yükle /samples/tr/tanimlar").get
    OrnekYukleyici.geriEsle(h, CompilationResponse(None, Nil, "L")).log shouldBe "L"
  }

  test("geriEsle: derleme BAŞARISIZSA içe alınamayan #yükle için o satıra uyarı eklenir; BAŞARILIYSA eklenmez") {
    val oz = sar("sil()", "// #yükle /yok/yok", "satıryaz(x)")
    val g  = OrnekYukleyici.expandEditorSource(kok, oz).get
    val ozV = oz.split("\n").toVector
    val basarisiz = OrnekYukleyici.geriEsle(g, CompilationResponse(None, Seq(hata(5, "not found: value x")), ""))
    val uyari = basarisiz.annotations.filter(_.tpe == "warning")
    uyari.size shouldBe 1
    ozV(uyari.head.row).trim shouldBe "// #yükle /yok/yok" // uyarı ilgili özgün satırda
    uyari.head.text.head shouldBe "#Yükle /yok/yok -- dosya bulunamadı, içe alınmadı"
    // hata satırı da özgün satırda
    ozV(basarisiz.annotations.find(_.tpe == "error").get.row).trim shouldBe "satıryaz(x)"
    OrnekYukleyici.geriEsle(g, CompilationResponse(Some("js"), Nil, "")).annotations shouldBe empty
  }

  test("iç içe içe alınamayan, üst düzey #yükle satırına bağlanır") {
    val oz = sar("sil()", "// #yükle /samples/tr/dongu-a", "ileri(1)")
    val g  = OrnekYukleyici.expandEditorSource(kok, oz).get
    // dongu-a, dongu-b'yi alır; dongu-b tekrar dongu-a'yı ister: "daha önce alındı" -- uyarı DEĞİL
    g.uyarilar shouldBe empty
    // içeride gerçekten eksik bir hedef
    java.nio.file.Files.write(
      kok.resolve("masaustu/src/main/resources/samples/tr/eksik-ister.kojo"),
      "// #yükle /yok/ic\ndez e = 1\n".getBytes(StandardCharsets.UTF_8)
    )
    val oz2 = sar("sil()", "// #yükle /samples/tr/eksik-ister")
    val g2  = OrnekYukleyici.expandEditorSource(kok, oz2).get
    g2.uyarilar.map(_._1) shouldBe Seq(4) // 'sil()' 3. satır; üst düzey #yükle 4. satır
    g2.uyarilar.head._2 should include("/yok/ic")
  }

  test("uçtan uca satır uyuşması: hata, kullanıcının kendi satırında çıkar") {
    // derleyici genişletilmiş kaynakta 'ileri(10)' satırına hata verirse, editörde de o satır olmalı
    val govde = Seq("sil()", "// #yükle /samples/tr/tanimlar", "ileri(10)")
    val oz    = sar(govde: _*)
    val g     = OrnekYukleyici.expandEditorSource(kok, oz).get
    val gen   = g.kaynak.split("\n").toVector
    val ozV   = oz.split("\n").toVector
    val genSatir = gen.indexOf("  ileri(10)".trim) match { case -1 => gen.indexWhere(_.trim == "ileri(10)"); case i => i }
    val d = OrnekYukleyici.geriEsle(g, CompilationResponse(None, Seq(hata(genSatir)), ""))
    ozV(d.annotations.head.row).trim shouldBe "ileri(10)"
  }

  // ---- kayıtlı yazılımcık bağlantıları (/sf/<kimlik>/<sürüm>) ----

  /** Editörün /raw ucunun döndürdüğü biçim: tam kaynak + eklenen $FiddleDependency/$ScalaVersion satırları. */
  private def kayit(govde: String*): String =
    (sar(govde: _*) + "\n// $FiddleDependency org.scala-js %%% scalajs-dom % 1.2.0\n// $ScalaVersion 2.13\n")

  private val tabloya: OrnekYukleyici.FiddleCoz = {
    case ("rNLmJw9", 2) => Some(kayit("  dez ortak = 42", "  def selam() = satıryaz(ortak)"))
    case ("rNLmJw9", 0) => Some(kayit("  dez ilk = 1"))
    case ("Ic00000", 1) => Some(kayit("// #yükle /samples/tr/tanimlar", "dez icten = sayfa"))
    case ("Marksiz", 1) => Some("dez isaretsiz = 1\n")
    case _              => None
  }
  private val hostlar = Set("ikojo.fly.dev", "localhost")

  private def fiddleliGenislet(govde: String*) =
    OrnekYukleyici.expandEditorSource(kok, sar(govde: _*), tabloya, hostlar)

  test("fiddleHedefi: tam adres, yalnız yol, yolun başı olmadan; sürüm verilmezse 0") {
    val f = OrnekYukleyici.fiddleHedefi _
    f("/sf/rNLmJw9/2") shouldBe Some((None, "rNLmJw9", 2))
    f("sf/rNLmJw9/2") shouldBe Some((None, "rNLmJw9", 2))
    f("https://ikojo.fly.dev/sf/rNLmJw9/2") shouldBe Some((Some("ikojo.fly.dev"), "rNLmJw9", 2))
    f("HTTPS://IKOJO.fly.dev/sf/rNLmJw9/2?zrc=abc#x") shouldBe Some((Some("ikojo.fly.dev"), "rNLmJw9", 2))
    f("/sf/rNLmJw9") shouldBe Some((None, "rNLmJw9", 0)) // editörün /sf/:id rotası da 0
    f("/sf/rNLmJw9/") shouldBe Some((None, "rNLmJw9", 0))
  }

  test("fiddleHedefi: bağlantı OLMAYANLAR (dosya yolları, kısa kimlik, fazlalık) None") {
    val f = OrnekYukleyici.fiddleHedefi _
    Seq("/samples/tr/tanimlar", "othello/tr/otello", "/sf/kisa/1", "/sf/rNLmJw9/2/fazla", "sf/rNLmJw9/2.kojo",
        "/sf/rNLmJw9/x", "ftp://ikojo.fly.dev/sf/rNLmJw9/2", "/sf/rNLmJw9/9999999").foreach(h => f(h) shouldBe None)
  }

  test("yukleVarMi: yalnız satır başındaki #yükle/#include (ucuz ön denetim)") {
    OrnekYukleyici.yukleVarMi("sil()\n  // #yükle /sf/rNLmJw9/2\n") shouldBe true
    OrnekYukleyici.yukleVarMi("// #include x\n") shouldBe true
    OrnekYukleyici.yukleVarMi("sil() // #yükle x\n") shouldBe false
    OrnekYukleyici.yukleVarMi("// yükle\n") shouldBe false
  }

  test("govdeCikar: yalnız FiddleStart/End arası; işaretsizde None") {
    OrnekYukleyici.govdeCikar(kayit("  a", "  b")).get shouldBe Vector("  a", "  b")
    OrnekYukleyici.govdeCikar("dez x = 1\n") shouldBe None
  }

  test("SF: `/sf/<kimlik>/<sürüm>` gövdenin başına eklenir; sarmalayıcı ve $Fiddle... satırları GİRMEZ") {
    val g = fiddleliGenislet("satıryaz(1)", "// #yükle /sf/rNLmJw9/2", "selam()").get
    g.kaynak should include("dez ortak = 42")
    g.kaynak should include("// --- #yükle /sf/rNLmJw9/2 başı (sf/rNLmJw9/2) ---")
    g.kaynak should include("// #Yükle /sf/rNLmJw9/2 -- içeriği yukarıya alındı")
    g.kaynak.split("\n").count(_.contains("object ScalaFiddle")) shouldBe 1 // içe alınanın sarmalayıcısı yok
    g.kaynak.split("\n").count(_.contains("FiddleDependency")) shouldBe 0
    g.uyarilar shouldBe empty
    g.dosyalar.distinct shouldBe Vector("sf/rNLmJw9/2")
  }

  test("SF: tam adres (bu sitenin adı) ve localhost kabul; yalnız yol kabul; yol başında / olmadan da") {
    Seq("https://ikojo.fly.dev/sf/rNLmJw9/2", "http://localhost:9000/sf/rNLmJw9/2", "/sf/rNLmJw9/2", "sf/rNLmJw9/2")
      .foreach { h =>
        val g = fiddleliGenislet(s"// #yükle $h").get
        g.kaynak should include("dez ortak = 42")
        g.uyarilar shouldBe empty
      }
  }

  test("SF: başka sunucunun adresi İÇE ALINMAZ (aynı kimlik bizde olsa bile): uyarı") {
    val g = fiddleliGenislet("// #yükle https://baska.example.com/sf/rNLmJw9/2").get
    g.kaynak should not include "dez ortak = 42"
    g.eklenen shouldBe 0
    g.uyarilar.head._2 should include("adres bu sunucunun değil (baska.example.com)")
  }

  test("SF: yok olan kayıt ve işaretsiz kayıt: uyarı, derleme sürer") {
    val g = fiddleliGenislet("// #yükle /sf/Yok0000/1", "// #yükle /sf/Marksiz/1").get
    g.eklenen shouldBe 0
    g.uyarilar.map(_._2) shouldBe Seq(
      "// #Yükle /sf/Yok0000/1 -- sf/Yok0000/1 bulunamadı, içe alınmadı",
      "// #Yükle /sf/Marksiz/1 -- sf/Marksiz/1 okunamadı, içe alınmadı"
    )
  }

  test("SF: sürüm verilmezse 0; aynı kayıt iki kez (farklı yazımla) bir kez alınır") {
    val g = fiddleliGenislet("// #yükle /sf/rNLmJw9", "// #yükle https://ikojo.fly.dev/sf/rNLmJw9/0").get
    g.kaynak.split("\n").count(_.trim == "dez ilk = 1") shouldBe 1
    g.kaynak should include("-- daha önce alındı")
  }

  test("SF: kayıtlı yazılımcığın İÇİNDEKİ #yükle (dosya) de genişler, hata kaynağı kendi etiketiyle") {
    val g = fiddleliGenislet("// #yükle /sf/Ic00000/1").get
    g.kaynak should include("dez sayfa = 1") // /samples/tr/tanimlar
    g.kaynak should include("dez icten = sayfa")
    g.dosyalar should contain("sf/Ic00000/1")
    g.dosyalar.exists(_.endsWith("tanimlar.kojo")) shouldBe true
  }

  test("SF: bir seferde en çok enFazlaFiddle kayıt getirilir; getirici sayısı bununla sınırlı") {
    var getirilen = 0
    val sayan: OrnekYukleyici.FiddleCoz = (id, v) => { getirilen += 1; Some(kayit("dez x = 1")) }
    val ids = Seq("A000001", "A000002", "A000003", "A000004", "A000005", "A000006", "A000007", "A000008", "A000009", "A000010")
    val g = OrnekYukleyici.expandEditorSource(kok, sar(ids.map(i => s"// #yükle /sf/$i/1"): _*), sayan, hostlar).get
    getirilen shouldBe OrnekYukleyici.enFazlaFiddle
    g.uyarilar.size shouldBe ids.size - OrnekYukleyici.enFazlaFiddle
    g.uyarilar.head._2 should include("en çok")
  }

  test("SF: kendini içe alan kayıt döngü yapmaz") {
    val dongu: OrnekYukleyici.FiddleCoz = (id, v) => Some(kayit(s"// #yükle /sf/$id/$v", "dez d = 1"))
    val g = OrnekYukleyici.expandEditorSource(kok, sar("// #yükle /sf/Dongu00/3"), dongu, hostlar).get
    g.kaynak.split("\n").count(_.trim == "dez d = 1") shouldBe 1
    g.kaynak should include("-- daha önce alındı")
  }

  test("SF geriEsle: içe alınan kayıttaki hata, #yükle satırında [sf/…] önekiyle") {
    val oz = sar("sil()", "// #yükle /sf/rNLmJw9/2", "selam()")
    val g  = OrnekYukleyici.expandEditorSource(kok, oz, tabloya, hostlar).get
    val d  = OrnekYukleyici.geriEsle(g, CompilationResponse(None, Seq(hata(g.ekleneBasi + 1, "boom")), ""))
    oz.split("\n")(d.annotations.head.row).trim shouldBe "// #yükle /sf/rNLmJw9/2"
    d.annotations.head.text.head shouldBe "[sf/rNLmJw9/2] boom"
  }

  // ---- HTTP getirici (gerçek yerel sunucuya karşı) ----
  private def sunucuyla[T](f: String => T): T = {
    import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
    val sv = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0)
    def yanit(he: HttpExchange, kod: Int, govde: Array[Byte]): Unit = {
      he.sendResponseHeaders(kod, if (govde.isEmpty) -1 else govde.length.toLong)
      if (govde.nonEmpty) he.getResponseBody.write(govde)
      he.close()
    }
    sv.createContext("/raw/", new HttpHandler {
      def handle(he: HttpExchange): Unit = he.getRequestURI.getPath match {
        case "/raw/rNLmJw9/2" => yanit(he, 200, kayit("dez ortak = 42").getBytes("UTF-8"))
        case "/raw/Buyuk00/1" => yanit(he, 200, Array.fill[Byte](OrnekYukleyici.enFazlaFiddleBayti + 10)('a'.toByte))
        case "/raw/Yavas00/1" => Thread.sleep(1500); yanit(he, 200, "x".getBytes)
        case "/raw/Yonlen0/1" => he.getResponseHeaders.add("Location", "http://127.0.0.1:1/"); yanit(he, 302, Array.empty)
        case _                => yanit(he, 404, Array.empty)
      }
    })
    sv.start()
    try f(s"http://127.0.0.1:${sv.getAddress.getPort}/raw/") finally sv.stop(0)
  }

  test("httpFiddleCoz: 200 -> kaynak; 404, yönlendirme, aşırı büyük gövde ve zaman aşımı -> None") {
    sunucuyla { taban =>
      val c = OrnekYukleyici.httpFiddleCoz(taban, zamanAsimiMs = 400)
      c("rNLmJw9", 2).get should include("dez ortak = 42")
      c("Yok0000", 1) shouldBe None
      c("Yonlen0", 1) shouldBe None // yönlendirme izlenmez
      c("Buyuk00", 1) shouldBe None
      c("Yavas00", 1) shouldBe None
    }
  }

  test("uçtan uca: expandEditorSource + httpFiddleCoz (gerçek HTTP)") {
    sunucuyla { taban =>
      val g = OrnekYukleyici
        .expandEditorSource(kok, sar("// #yükle https://ikojo.fly.dev/sf/rNLmJw9/2"),
                            OrnekYukleyici.httpFiddleCoz(taban, 2000), hostlar).get
      g.kaynak should include("dez ortak = 42")
      g.uyarilar shouldBe empty
    }
  }
}
