package kojo

import scala.collection.mutable.ArrayBuffer

/**
 * Kaplumbağanın O ANDA izlediği boyama çokgeni.
 *
 * NEDEN VAR: PIXI 5'te `Graphics._render` her çizimde `finishPoly()` çağırıp
 * yarım kalan çokgeni OLDUĞU YERDE kapatıyor. Kaplumbağa şekli kenar kenar
 * kuruyor; canlandırma açıkken (varsayılan `animationDelay = 1000`) her kenar
 * ayrı bir kareye düşüyor, yani her kenardan sonra bir render giriyor.
 * Ölçüldü (PIXI 5.3.12, 4 kenarlı kare):
 *
 *   render yok            -> 1 çokgen, 5 nokta   (dolu kare)
 *   her kenarda render    -> 4 çokgen, 2'şer nokta (hiçbiri alan kaplamıyor)
 *   iki kenarda bir render-> 2 çokgen, 3'er nokta
 *
 * Sonuç: `boyamaRenginiKur` varsayılan hızda hiçbir şey doldurmuyordu; yalnız
 * `hızıKur(çokHızlı)` (gecikme 0, bütün şekil tek blokta) çalışıyordu.
 *
 * ÇARE: dolguyu PIXI'nin yarım yoluna emanet etmemek. Noktaları burada
 * tutuyoruz ve şekli her seferinde TAMAMLANMIŞ olarak (`drawPolygon`) yeniden
 * yayınlıyoruz -- tamamlanmış bir şekli render kesemiyor.
 *
 * Bu sınıf saf: PIXI'ye ya da DOM'a dokunmuyor, dolayısıyla Node'da da
 * koşuyor (PompaDurumu ile aynı kalıp).
 */
class BoyamaYolu {
  private val noktalar = ArrayBuffer.empty[(Double, Double)]

  /** Boyama kurulduğunda çokgen sıfırdan başlıyor: eski kenarlar bu boyaya ait değil. */
  def boyaKuruldu(x: Double, y: Double): Unit = {
    noktalar.clear()
    noktalar += ((x, y))
  }

  /**
   * Kalem kalkık taşınma (moveTo / atla / zıpla): çokgen KIRILIR.
   * Masaüstü Kojo da öyle -- araya sıçrama giren bir şekil tek parça sayılmıyor.
   */
  def taşındı(x: Double, y: Double): Unit = {
    noktalar.clear()
    noktalar += ((x, y))
  }

  /** Kalem inik çizgi (lineTo): çokgene bir köşe eklenir. */
  def çizildi(x: Double, y: Double): Unit = {
    if (noktalar.isEmpty) noktalar += ((x, y))
    else if (noktalar.last != ((x, y))) noktalar += ((x, y))
  }

  /** Tuval silindi. */
  def temizle(): Unit = noktalar.clear()

  /** Boyanacak bir alan var mı: en az üç köşe gerekiyor. */
  def alanVarMı: Boolean = noktalar.size >= 3

  /** Çokgenin köşeleri (kopya değil -- yalnız okumak için). */
  def köşeler: collection.Seq[(Double, Double)] = noktalar

  /** PIXI'nin `drawPolygon`'ının istediği düz dizi: x0, y0, x1, y1, ... */
  def düzDizi: Array[Double] = {
    val a = new Array[Double](noktalar.size * 2)
    var i = 0
    noktalar.foreach { case (x, y) =>
      a(i) = x; a(i + 1) = y; i += 2
    }
    a
  }
}

/**
 * Dolgusu bekleyen bir çizer. `KojoWorld` bunları render'a kadar biriktiriyor
 * ve her birini karede EN ÇOK BİR KEZ yayınlıyor.
 *
 * NEDEN VAR: `Turtle.turtlePathLineTo` her kenarda bütün çokgeni yeniden
 * `drawPolygon`a veriyordu. `hızıKur(çokHızlı)` ile bütün kenarlar tek blokta
 * geliyor ve `KojoWorld.render` hepsini tek bir requestAnimationFrame'e
 * topluyor -- yani TEK render'a karşılık n üçgenleme yapılıyor, n-1'i çöpe
 * gidiyor. Ölçüldü (tan-theta.kojo, 241 nokta): 241 yayın, 1 render.
 *
 * Çare: kenar eklenince yalnız "kirli" diye kaydolmak, gerçek yayını
 * render'dan hemen önce yapmak. Şeklin TAMAMLANMIŞ yayınlanması kuralı
 * bozulmuyor (bkz. yukarıdaki BoyamaYolu açıklaması) -- yalnız kaç kez
 * yayınlandığı değişiyor.
 */
trait Boyacı {

  /** Bekleyen dolguyu şimdi yayınla. Yayın idempotent olmalı: aynı çokgeni
    * iki kez yayınlamak, bir kez yayınlamakla aynı sonucu vermeli. */
  private[kojo] def boyayıYayınla(): Unit
}
