package scalafiddle.router

import net.kogics.kojo.lite.i18n.tr.Çevirmen
import net.kogics.kojo.lite.i18n.tr.Çevirmen.{İngilizcedenTürkçeyeYön, TürkçedenİngilizceyeYön, Yön}
import upickle.default._

import java.util.concurrent.{Executors, ThreadFactory}
import scala.concurrent.ExecutionContext

/** `POST /cevir`: Koco (Türkçe) betik <-> Kojo (İngilizce) betik (kojojs-dev#183).
  *
  * Çevirmenin kendisi masaüstü Koco'nun kitaplığı (bulent2k2/kojo:
  * lite/i18n/tr/{cevirmen,cevirisozlugu,dict}.scala + iki TSV); burada BAYT BAYT
  * kopya duruyor, elle düzenlenmez (bkz. router/CEVIRMEN.md). Bu dosya yalnız
  * onu HTTP'ye bağlayan ince katman.
  *
  * Çeviri sözcük düzeyinde ve derleyici gerektirmiyor: yamalı scalariform
  * sözcükleyicisi + iki sözlük. İstek başına milisaniyeler; ilk istekte sözlük
  * yüklenir (`Çevirmen.sözlük` tembel).
  */
object Cevir {

  /** Çeviri için AYRI, küçük bir havuz (kojojs-core#58 incelemesi §4.1): çeviri router'ın /compile ve
    * WebSocket yönlendirmesiyle aynı JVM'de koşuyor ve 64 KB'lık bir istek en kötü ~350 ms CPU tutuyor
    * (ölçüldü). Varsayılan dağıtıcıda koşsaydı, sürekli 64 KB gönderen bir istemci onu meşgul ederdi.
    * İki iş parçacığı: çeviri istekleri kendi aralarında kuyruklanır, derleyici işi etkilenmez.
    */
  val ec: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(2, new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val t = new Thread(r, "cevir")
        t.setDaemon(true)
        t
      }
    })
  )

  /** Rapordaki bir ad ve kaç kez geçtiği. */
  case class AdSayisi(ad: String, sayi: Int)
  object AdSayisi { implicit val rw: ReadWriter[AdSayisi] = macroRW }

  /** Birden çok karşılığı olan ve en sık görüleniyle çevrilen ad. */
  case class Belirsiz(ad: String, secilen: String, digerleri: Seq[String], satir: Int)
  object Belirsiz { implicit val rw: ReadWriter[Belirsiz] = macroRW }

  /** `kalanlar`: çevrilmeden kalan ve ÇEVİRMENİN "Türkçe kaldı" dedikleri: Türkçe harf taşıyan ya da
    * sözlükte bilinen adlar (kullanıcının kendi adları ve sözlüğün eksikleri). ASCII bir kullanıcı adı
    * (`halka`) çevrilmeden kalır ama burada listelenmez. Yalnız tr2en'de dolu; en2tr'de boş (İngilizce
    * adlar Koco'da da geçerli, kalmaları kozmetik). Satır numarası yok: çevirmen yalnız sayı tutuyor.
    */
  case class Rapor(cevrilen: Int, kalanlar: Seq[AdSayisi], belirsiz: Seq[Belirsiz])
  object Rapor { implicit val rw: ReadWriter[Rapor] = macroRW }

  /** `kalanAnahtarSozcukler`: çıktıda hâlâ kaynak dilin anahtar sözcüğü var (çeviri eksik). */
  case class Sonuc(yon: String, kod: String, rapor: Rapor, kalanAnahtarSozcukler: Seq[String])
  object Sonuc { implicit val rw: ReadWriter[Sonuc] = macroRW }

  val tr2en = "tr2en"
  val en2tr = "en2tr"

  /** Yön verilmediyse betikten bulunur: Türkçe anahtar sözcük (dez, den, tanım, eğer, ...) varsa
    * Türkçe, yoksa İngilizce. Anahtar sözcük yoksa (yalnız `ileri(10)`) İngilizce sayılır:
    * Türkçe adlar İngilizceye çevrilirken de, İngilizce adlar Türkçeye çevrilirken de kod
    * geçerli kalıyor (İngilizce adlar Koco'da da geçerli), yani yanlış tahmin ucuz.
    */
  def yonBul(kod: String): String =
    if (Çevirmen.kalanAnahtarSözcükler(kod, TürkçedenİngilizceyeYön).nonEmpty) tr2en else en2tr

  def yonOku(ad: String): Option[Yön] = ad match {
    case `tr2en` => Some(TürkçedenİngilizceyeYön)
    case `en2tr` => Some(İngilizcedenTürkçeyeYön)
    case _       => None
  }

  /** `yon`: "tr2en" | "en2tr" | boş/"oto" (betikten bul). Geçersiz değer: Left(ileti). */
  def cevir(kod: String, yon: Option[String]): Either[String, Sonuc] = {
    val ad = yon.filter(y => y.nonEmpty && y != "oto").getOrElse(yonBul(kod))
    yonOku(ad) match {
      case None => Left(s"geçersiz yön: '$ad' (tr2en, en2tr ya da oto olmalı)")
      case Some(y) =>
        val (cikti, rapor) = Çevirmen.çevir(kod, y)
        val kalanlar = y match {
          case TürkçedenİngilizceyeYön => rapor.türkçeKalanlar
          case _                       => Map.empty[String, Int]
        }
        Right(
          Sonuc(
            ad,
            cikti,
            Rapor(
              rapor.çevrilen,
              kalanlar.toSeq.sortBy { case (a, n) => (-n, a) }.map { case (a, n) => AdSayisi(a, n) },
              rapor.belirsiz.map(b => Belirsiz(b.ad, b.seçilen, b.alternatifler, b.satır))
            ),
            Çevirmen.kalanAnahtarSözcükler(cikti, y)
          ))
    }
  }
}
