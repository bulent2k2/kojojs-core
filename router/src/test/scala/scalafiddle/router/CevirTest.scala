package scalafiddle.router

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import upickle.default._

/** `/cevir`'in çekirdeği (kojojs-dev#183 Aşama 2). Çevirmenin kendi davranışları
  * masaüstü deposunda sınanıyor (CevirmenTest, CevirmenDerlemeTest); burada yalnız
  * "bu sunucuda, bu sınıf yolunda, bu kaynaklarla çalışıyor mu" ve ince katmanın
  * kuralları: yön bulma, geçersiz yön, JSON şekli.
  */
class CevirTest extends AnyFunSuite with Matchers {
  val halka =
    """tanım kenar(x0: Kesir, y0: Kesir): Birim =
      |  için (i <- 1 to 10) noktayaGit(x0 * i / 10, y0 * i / 10)
      |dez halka = Resim { kalemKalınlığınıKur(0); kenar(10, 20) }
      |çiz(halka)
      |halka.fareyeTıklayınca { (x, y) => satıryaz(f"$x%.2f") }
      |yinele(3) { ileri() }
      |""".stripMargin

  test("Türkçe betik İngilizceye çevriliyor; yön betikten bulunuyor") {
    val s = Cevir.cevir(halka, None).toOption.get
    s.yon shouldBe Cevir.tr2en
    s.kod should include("def kenar(x0: Double, y0: Double): Unit =")
    s.kod should include("val halka = Picture {")
    s.kod should include("setPenThickness(0)")
    s.kod should include("repeat(3) { forward() }")
    s.kod should include("halka.onMouseClick")
    s.kalanAnahtarSozcukler shouldBe empty
    s.rapor.cevrilen should be > 10
  }

  test("İngilizce betik Türkçeye çevriliyor; Türkçe anahtar sözcük yoksa İngilizce sayılır") {
    val s = Cevir.cevir("val r = Picture.circle(50)\ndraw(r)\nrepeat(3) { forward(10) }\n", None).toOption.get
    s.yon shouldBe Cevir.en2tr
    s.kod should include("dez r = Resim.daire(50)")
    s.kod should include("çiz(r)")
    s.kod should include("yinele(3) { ileri(10) }")
    Cevir.yonBul("ileri(10)") shouldBe Cevir.en2tr
    Cevir.yonBul("dez a = 1") shouldBe Cevir.tr2en
  }

  test("açık yön otomatiği ezer; 'oto' ve boş yön betikten bulur") {
    Cevir.cevir("dez a = 1", Some("en2tr")).toOption.get.yon shouldBe Cevir.en2tr
    Cevir.cevir("dez a = 1", Some("oto")).toOption.get.yon shouldBe Cevir.tr2en
    Cevir.cevir("dez a = 1", Some("")).toOption.get.yon shouldBe Cevir.tr2en
  }

  test("geçersiz yön Left; ileti değeri söylüyor") {
    Cevir.cevir("dez a = 1", Some("xx")).swap.toOption.get should include("'xx'")
  }

  test("kullanıcının kendi adları çevrilmiyor; Türkçe harfli olanlar raporda 'kalanlar'da, sayısıyla") {
    // Rapor yalnız ÇEVİRMENİN "Türkçe kaldı" dediklerini listeler: Türkçe harf (ı ş ğ ö ü ç) taşıyan ya da
    // sözlükte bilinen adlar. `halka` gibi ASCII bir kullanıcı adı çevrilmeden kalır ama listelenmez
    // (İngilizce bir ad da olabilir; ölçüldü).
    val s = Cevir.cevir("dez başlık = \"a\"\nsatıryaz(başlık)\nsatıryaz(başlık + başlık)\n", None).toOption.get
    s.kod should include("val başlık")
    s.rapor.kalanlar should contain(Cevir.AdSayisi("başlık", 4))
    val h = Cevir.cevir(halka, None).toOption.get
    h.kod should include("val halka")
    h.rapor.kalanlar.map(_.ad) should not contain "halka"
  }

  test("iKojo'ya özgü konumuOku / yönüOku -> readPosition / readHeading (kojo#79)") {
    val s = Cevir.cevir("dez a = 1\nkonumuOku { n => satıryaz(n.x) }\nyönüOku { y => satıryaz(y) }\n", None).toOption.get
    s.kod should include("readPosition {")
    s.kod should include("readHeading {")
  }

  test("iKojo'ya özgü soluk / renkliYazı / canlandırmayıDurdur ve kullanıcının zaman değişkeni (kojo#84)") {
    val k = "dez a = yeşil.soluk(0.8)\nden zaman = 0\nzaman += 1\nçiz(kalemKalınlığı(2) -> Resim.renkliYazı(\"x\", 40, mavi))\ncanlandırmayıDurdur()\n"
    val s = Cevir.cevir(k, None).toOption.get
    s.kod should include("green.fadeOut(0.8)")
    s.kod should include("var zaman = 0")
    s.kod should include("zaman += 1")
    s.kod should include("penThickness(2) -> Picture.textu(\"x\", 40, blue)")
    s.kod should include("stopAnimation()")
  }

  test("notaÇalarıKapat / notaÇalarıDurdur <-> stopNotePlayer, iki yönde (kojo#81)") {
    val e = Cevir.cevir("dez a = 1\nnotaÇalarıKapat()\nnotaÇalarıDurdur()\n", None).toOption.get
    e.kod should include("stopNotePlayer()\nstopNotePlayer()")
    // eşit oyda asıl ad (Kapat) seçiliyor: ceviri-kurallar.tsv
    val t = Cevir.cevir("stopNotePlayer()\nplayNote(60, 500)\n", None).toOption.get
    t.kod should include("notaÇalarıKapat()")
    t.kod should include("notaÇal(60, 500)")
  }

  test("göster (üye) visible, Mp3Çalar.durdur stopMp3, Dizim.boş boyutlu ofDim (kojo#82)") {
    val e = Cevir.cevir("dez a = 1\nateş.göster()\nfrenSesiÇalar.durdur()\ndurdur()\nDizim.boş[Nokta](3, 4)\n", None).toOption.get
    e.kod should include("ateş.visible()")
    e.kod should include("frenSesiÇalar.stopMp3()")
    e.kod should include("\nstopAnimation()\n")
    e.kod should include("Array.ofDim[Point](3, 4)")
  }

  test("sonuç JSON'a yazılıp geri okunuyor (istemcinin göreceği şekil)") {
    val s = Cevir.cevir(halka, None).toOption.get
    val json = write(s)
    ujson.read(json).obj.keySet should contain allOf ("yon", "kod", "rapor", "kalanAnahtarSozcukler")
    read[Cevir.Sonuc](json) shouldBe s
  }

  test("Türkçe karakterler (ı ş ğ ö ü ç İ) bozulmadan geçiyor") {
    val s = Cevir.cevir("dez çiçekAdı = \"İstanbul\"\nsatıryaz(çiçekAdı)\n", None).toOption.get
    s.kod should include("\"İstanbul\"")
    s.kod should include("çiçekAdı")
  }

  test("çeviri havuzu ayrı ve daemon: varsayılan dağıtıcıyı meşgul etmez") {
    import scala.concurrent.{Await, Future}
    import scala.concurrent.duration._
    val (ad, daemon) = Await.result(
      Future((Thread.currentThread.getName, Thread.currentThread.isDaemon))(Cevir.ec), 5.seconds)
    ad shouldBe "cevir"
    daemon shouldBe true
  }
}
