package kojo.tr

import kojo.doodle.{Color => DRenk}

/**
 * Renklerin Türkçesi.
 *
 * Masaüstünde İKİ renk türü var: `java.awt.Color` (temel renkler) ve
 * `kojo.doodle.Color` (zengin isim listesi). KojoJS'te AWT yok, tek tür var --
 * bu yüzden port SADELEŞİYOR: `Renk` = `kojo.doodle.Color`.
 *
 * `renksiz` masaüstünde KColor.noColor; burada saydam siyah.
 */
trait RenkYöntemleri extends TemelTürler {
  type Renk = DRenk
  /**
   * Masaüstündeki `object Renk` (tr/renk.scala): Türkçe renk yapıcıları.
   * Eskiden burada `val Renk = DRenk` vardı; doodle Color'ın yapıcıları
   * (rgb/rgba/hsl/hsla) korunuyor, adlandırılmış renkler için `renkler` ya da
   * Türkçesi için `Renkler` var.
   */
  object Renk {
    /**
     * Masaüstündeki `Renk(k, y, m)` / `Renk(k, y, m, s)` / `Renk(0xrrggbb)`
     * yapıcıları. (Eskiden `val Renk = DRenk` bunları doodle Color'ın
     * `apply`lerinden alıyordu; `object Renk`e geçince elle eklendi.)
     */
    def apply(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydamlık: Sayı = 255): Renk =
      DRenk.rgba(kırmızı, yeşil, mavi, saydamlık)
    def apply(onaltılık: Uzun): Renk = DRenk(onaltılık)
    def apply(onaltılık: Uzun, saydamlıkVar: İkil): Renk = DRenk(onaltılık, saydamlıkVar)

    def kym(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı): Renk = DRenk.rgb(kırmızı, yeşil, mavi)
    def kyms(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydamlık: Sayı): Renk =
      DRenk.rgba(kırmızı, yeşil, mavi, saydamlık)
    def ada(arıRenk: Kesir, doygunluk: Kesir, açıklık: Kesir): Renk =
      DRenk.hsl(arıRenk, doygunluk, açıklık)
    def adas(arıRenk: Kesir, doygunluk: Kesir, açıklık: Kesir, saydamlık: Kesir): Renk =
      DRenk.hsla(arıRenk, doygunluk, açıklık, saydamlık)
    // İngilizce yapıcılar (eski `val Renk = DRenk` ile uyum)
    def rgb(r: Sayı, g: Sayı, b: Sayı): Renk = DRenk.rgb(r, g, b)
    def rgba(r: Sayı, g: Sayı, b: Sayı, a: Sayı): Renk = DRenk.rgba(r, g, b, a)
    def hsl(h: Kesir, s: Kesir, l: Kesir): Renk = DRenk.hsl(h, s, l)
    def hsla(h: Kesir, s: Kesir, l: Kesir, a: Kesir): Renk = DRenk.hsla(h, s, l, a)
  }
  val renkler = DRenk

  object Renkler {
    val renksiz: Renk = DRenk(0, 0, 0, 0)
    val saydam: Renk = renksiz

    val mavi: Renk = DRenk.blue
    val kırmızı: Renk = DRenk.red
    val sarı: Renk = DRenk.yellow
    val yeşil: Renk = DRenk.green
    val mor: Renk = DRenk.purple
    val pembe: Renk = DRenk.pink
    val kahverengi: Renk = DRenk.brown
    val siyah: Renk = DRenk.black
    val beyaz: Renk = DRenk.white
    val gri: Renk = DRenk.gray
    val koyuGri: Renk = DRenk.darkGray
    val açıkGri: Renk = DRenk.lightGray
    val turuncu: Renk = DRenk.orange
    val morumsu: Renk = DRenk.magenta
    val camgöbeği: Renk = DRenk.cyan

    val altınbaşak: Renk = DRenk.goldenrod
    val altın: Renk = DRenk.gold
    val yeşilimsiSarı: Renk = DRenk.greenYellow
    val zeytin: Renk = DRenk.olive
    val orkidePembesi: Renk = DRenk.orchid
    val somon: Renk = DRenk.salmon
    val denizYeşili: Renk = DRenk.seaGreen
    val kurşunMavisi: Renk = DRenk.slateBlue
    val kurşunGrisi: Renk = DRenk.slateGray
    val turkuaz: Renk = DRenk.turquoise
    val menekşe: Renk = DRenk.violet
    val haki: Renk = DRenk.khaki
    val mercan: Renk = DRenk.coral
    val gökMavisi: Renk = DRenk.skyBlue
    val çelikMavisi: Renk = DRenk.steelBlue
    val beyazlatılmışBadem: Renk = DRenk.blanchedAlmond

    // Koyu renkler: doodle.Color'da vardı, Türkçe adları yoktu (2026-09, ayna
    // sayfaları için eklendi -- ikojo.in örnekleri darkBlue vb. kullanıyor).
    val koyuMavi: Renk = DRenk.darkBlue
    val koyuYeşil: Renk = DRenk.darkGreen
    val koyuKırmızı: Renk = DRenk.darkRed
    val koyuTuruncu: Renk = DRenk.darkOrange
    val koyuCamgöbeği: Renk = DRenk.darkCyan
    val koyuMor: Renk = DRenk.darkMagenta
  }

  // sık kullanılanlar üst düzeyde
  val renksiz = Renkler.renksiz
  val saydam = Renkler.saydam
  val siyah = Renkler.siyah
  val beyaz = Renkler.beyaz
  val açıkGri = Renkler.açıkGri
  val camgöbeği = Renkler.camgöbeği
  val gri = Renkler.gri
  val kahverengi = Renkler.kahverengi
  val koyuGri = Renkler.koyuGri
  val koyuMavi = Renkler.koyuMavi
  val koyuYeşil = Renkler.koyuYeşil
  val koyuKırmızı = Renkler.koyuKırmızı
  val koyuTuruncu = Renkler.koyuTuruncu
  val koyuCamgöbeği = Renkler.koyuCamgöbeği
  val koyuMor = Renkler.koyuMor
  val kırmızı = Renkler.kırmızı
  val mavi = Renkler.mavi
  val mor = Renkler.mor
  val morumsu = Renkler.morumsu
  val pembe = Renkler.pembe
  val sarı = Renkler.sarı
  val turuncu = Renkler.turuncu
  val yeşil = Renkler.yeşil

  /** Renk kur: kırmızı/yeşil/mavi (0-255) ve isteğe bağlı saydamlık. */
  def renkKur(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı): Renk = DRenk(kırmızı, yeşil, mavi)
  def renkKur(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydamlık: Sayı): Renk =
    DRenk(kırmızı, yeşil, mavi, saydamlık)

  implicit class RenkMetotları(r: Renk) {
    def kırmızısı = r.red
    def yeşili = r.green
    def mavisi = r.blue
    def saydamlığı = r.alpha
    // fadeOut/fadeIn: saydamlığı azalt/artır (0-1). Renkli eksen çizgileri gibi
    // yerlerde kullanılıyor (birim çember örneği: yeşil.soluk(0.8)).
    def soluk(oran: Kesir): Renk = r.fadeOut(oran)
    def belirgin(oran: Kesir): Renk = r.fadeIn(oran)
  }
}
