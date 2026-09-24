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
  // DÜZ ve KAPASİTELİ (#155): x0, y0, x1, y1, ... ilk sürüm (Double, Double)
  // ikililerini ArrayBuffer'da tutuyor ve `düzDizi` her yayında hepsini
  // kutudan çıkarıp yeni diziye döküyordu -- büyüyen şekilde yayın başına
  // O(n), şekil başına Σ önek; 40 000 noktalı gülde altı yayın için 7-34 ms
  // (SwiftShader), yani stencil kurulumunun kendisi kadar. Şimdi ekleme O(1)
  // amortize, `düzDizi` tek bir sayısal kopya.
  private var xy: Array[Double] = new Array[Double](256)
  private var sayı = 0 // nokta sayısı; xy'nin ilk 2 * sayı hücresi geçerli

  private def ekle(x: Double, y: Double): Unit = {
    if (2 * sayı + 2 > xy.length) {
      val yeni = new Array[Double](xy.length * 2)
      System.arraycopy(xy, 0, yeni, 0, 2 * sayı)
      xy = yeni
    }
    xy(2 * sayı) = x; xy(2 * sayı + 1) = y; sayı += 1
  }

  /** Boyama kurulduğunda çokgen sıfırdan başlıyor: eski kenarlar bu boyaya ait değil. */
  def boyaKuruldu(x: Double, y: Double): Unit = { sayı = 0; ekle(x, y) }

  /**
   * Kalem kalkık taşınma (moveTo / atla / zıpla): çokgen KIRILIR.
   * Masaüstü Kojo da öyle -- araya sıçrama giren bir şekil tek parça sayılmıyor.
   */
  def taşındı(x: Double, y: Double): Unit = { sayı = 0; ekle(x, y) }

  /** Kalem inik çizgi (lineTo): çokgene bir köşe eklenir (ardışık aynı nokta eklenmez). */
  def çizildi(x: Double, y: Double): Unit =
    if (sayı == 0 || xy(2 * sayı - 2) != x || xy(2 * sayı - 1) != y) ekle(x, y)

  /** Tuval silindi. */
  def temizle(): Unit = sayı = 0

  /** Boyanacak bir alan var mı: en az üç köşe gerekiyor. */
  def alanVarMı: Boolean = sayı >= 3

  /** Çokgenin köşeleri (kopya; sınamalar için). */
  def köşeler: collection.Seq[(Double, Double)] = {
    val b = ArrayBuffer.empty[(Double, Double)]
    var i = 0
    while (i < sayı) { b += ((xy(2 * i), xy(2 * i + 1))); i += 1 }
    b
  }

  /** PIXI'nin `drawPolygon`'ının istediği düz dizi: x0, y0, x1, y1, ... (kopya, tam boy). */
  def düzDizi: Array[Double] = {
    val a = new Array[Double](sayı * 2)
    System.arraycopy(xy, 0, a, 0, sayı * 2)
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

  /**
   * Kare sınırında, yayınlar yapıldıktan sonra: kuyruğu boşalmış bir çizerin
   * şekli durduysa biriken dolgu süresini bildir (#134/#143). Yalnız
   * `KojoWorld.kuyrukBoşaldı` ile aday yazılan çizerlere soruluyor; kuyruğu
   * olmayan çizer (sınama sahteleri) aday olmuyor, varsayılan boş.
   */
  private[kojo] def durmaDenetimi(): Unit = ()

  /**
   * Bu çizerin dolgusunun indiği katman. Silme yolları (erasePictures,
   * removeLayer) sahneden çıkardıkları katmanın çizerini bunun üzerinden
   * bulup bekleyen boya sırasından düşürüyor -- yoksa silinmiş bir resmin
   * dolgusu kuyruğu boşaldıkça yeniden yeniden üçgenleniyor (sorun #109).
   */
  private[kojo] def boyacıKatmanı: pixiscalajs.PIXI.Container

  /**
   * Bu çizerin dolgusu hâlâ yayınlanmalı mı?
   *
   * Katmanına AÇIK olarak "silindi" imi konmuşsa hayır: resim silinmiş, kalan
   * yayınlar görünmeyen bir şekli üçgenliyor (sorun #109). İm hiç konmamışsa
   * evet -- Resim{} gövdesi çiz()'den ÖNCE çalışıyor, o yayınlar kesilirse
   * dolgu hiç oluşmaz.
   *
   * İmi koyan tek yer silme yolları, kaldıran tek yer addLayer
   * (bkz. PixiUyum.Silindiİmi). Katmanın `parent`'ının null olmasına
   * BAKMIYORUZ: pişirme de düğümü sahne dışında tutuyor (#96/#102) ve çizim
   * yolu noteMutation çağırmadığı için çizmekte olan bir resim pişebilir --
   * `parent` çıkarımı onu "silinmiş" sanıp dolgusunu sessizce düşürürdü.
   */
  private[kojo] def boyasıSürüyor: Boolean
}
