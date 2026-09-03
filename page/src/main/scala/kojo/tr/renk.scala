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
  val Renk = DRenk
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
