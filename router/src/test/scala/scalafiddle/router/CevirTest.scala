package scalafiddle.router

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import upickle.default._

/** `/cevir` is the Turkish-named JSON shape of `/translate`, kept for the iKojo editor (fork-only). The
  * translation itself is tested in TranslateTest; here only that the adapter carries everything over.
  */
class CevirTest extends AnyFunSuite with Matchers {
  val kod = "dez halka = Resim.daire(5)\nsatıryaz(halka)\nyinele(3) { ileri() }\n"

  test("the adapter carries direction, code, report and keywords over from /translate") {
    val c = Cevir.cevir(kod, None).toOption.get
    val t = Translate.translate(kod, None).toOption.get
    c.yon shouldBe t.direction
    c.kod shouldBe t.code
    c.rapor.cevrilen shouldBe t.report.translated
    c.rapor.kalanlar.map(n => (n.ad, n.sayi)) shouldBe t.report.leftovers.map(n => (n.name, n.count))
    c.kalanAnahtarSozcukler shouldBe t.remainingKeywords
    // the one non-trivial field rename: ambiguous{name,chosen,others,line} -> belirsiz{ad,secilen,digerleri,satir}
    t.report.ambiguous should not be empty
    c.rapor.belirsiz.map(b => (b.ad, b.secilen, b.digerleri, b.satir)) shouldBe
      t.report.ambiguous.map(a => (a.name, a.chosen, a.others, a.line))
  }

  test("'oto' and an empty direction detect from the script; an invalid one is a Turkish message") {
    Cevir.cevir("dez a = 1", Some("oto")).toOption.get.yon shouldBe Cevir.tr2en
    Cevir.cevir("dez a = 1", Some("")).toOption.get.yon shouldBe Cevir.tr2en
    Cevir.cevir("dez a = 1", Some("auto")).isLeft shouldBe true // the old /cevir took only "oto"
    Cevir.cevir("dez a = 1", Some("xx")).swap.toOption.get should (include("'xx'") and include("geçersiz yön"))
  }

  test("the JSON keeps the Turkish field names the editor reads") {
    val json = write(Cevir.cevir(kod, None).toOption.get)
    ujson.read(json).obj.keySet should contain allOf ("yon", "kod", "rapor", "kalanAnahtarSozcukler")
  }
}
