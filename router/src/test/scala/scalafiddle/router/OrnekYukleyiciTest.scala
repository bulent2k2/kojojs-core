package scalafiddle.router

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

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
}
