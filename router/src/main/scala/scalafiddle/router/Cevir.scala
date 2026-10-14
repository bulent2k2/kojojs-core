package scalafiddle.router

import upickle.default._

import scala.concurrent.ExecutionContext

/** `POST /cevir`: the Turkish-named JSON shape of `/translate`, kept for the iKojo editor's "Çevir"
  * button (kojojs-dev#183) until the editor moves to `/translate`. FORK-ONLY: this file is not part
  * of the upstream contribution. It adapts `Translate` and holds no translation logic.
  */
object Cevir {

  val ec: ExecutionContext = Translate.ec

  /** Rapordaki bir ad ve kaç kez geçtiği. */
  case class AdSayisi(ad: String, sayi: Int)
  object AdSayisi { implicit val rw: ReadWriter[AdSayisi] = macroRW }

  /** Birden çok karşılığı olan ve en sık görüleniyle çevrilen ad. */
  case class Belirsiz(ad: String, secilen: String, digerleri: Seq[String], satir: Int)
  object Belirsiz { implicit val rw: ReadWriter[Belirsiz] = macroRW }

  case class Rapor(cevrilen: Int, kalanlar: Seq[AdSayisi], belirsiz: Seq[Belirsiz])
  object Rapor { implicit val rw: ReadWriter[Rapor] = macroRW }

  case class Sonuc(yon: String, kod: String, rapor: Rapor, kalanAnahtarSozcukler: Seq[String])
  object Sonuc { implicit val rw: ReadWriter[Sonuc] = macroRW }

  val tr2en = Translate.tr2en
  val en2tr = Translate.en2tr

  def yonBul(kod: String): String = Translate.detectDirection(kod)

  /** `yon`: "tr2en" | "en2tr" | boş/"oto" (betikten bul). Geçersiz değer: Left(ileti). */
  def cevir(kod: String, yon: Option[String]): Either[String, Sonuc] = {
    // Eski adlandırma: "oto"; yeni uçta "auto". İkisi de betikten bulur.
    val direction = yon.map(y => if (y == "oto") Translate.auto else if (y == Translate.auto) "?" else y)
    Translate.translate(kod, direction).left.map(_ => s"geçersiz yön: '${yon.getOrElse("")}' (tr2en, en2tr ya da oto olmalı)").map { r =>
      Sonuc(
        r.direction,
        r.code,
        Rapor(
          r.report.translated,
          r.report.leftovers.map(n => AdSayisi(n.name, n.count)),
          r.report.ambiguous.map(a => Belirsiz(a.name, a.chosen, a.others, a.line))
        ),
        r.remainingKeywords
      )
    }
  }
}
