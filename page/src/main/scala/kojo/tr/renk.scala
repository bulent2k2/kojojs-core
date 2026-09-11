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

    // ---- gradyanlar (masaüstü tr/renk.scala ile aynı imzalar) ----
    // Bunlar düz Renk değil Boya döndürüyor; boyaRengi(...) ikisini de alıyor.
    // Koordinatlar resmin YEREL koordinatları -- masaüstündeki gibi.

    /** (x1,y1)'den (x2,y2)'ye giden iki renkli doğrusal geçiş. */
    def doğrusalDeğişim(
      x1: Kesir, y1: Kesir, renk1: Renk,
      x2: Kesir, y2: Kesir, renk2: Renk,
      dalgalıDevam: İkil = yanlış
    ): Boya = kojo.Boya.doğrusal(x1, y1, renk1, x2, y2, renk2, dalgalıDevam)

    /** Çok duraklı doğrusal geçiş: `dağılım` 0..1 arası konumlar. */
    def doğrusalÇokluDeğişim(
      x1: Kesir, y1: Kesir, x2: Kesir, y2: Kesir,
      dağılım: Dizi[Kesir], renkler: Dizi[Renk],
      dalgalıDevam: İkil = yanlış
    ): Boya = kojo.Boya.doğrusalÇoklu(x1, y1, x2, y2, dağılım, renkler, dalgalıDevam)

    /** Merkezden dışarı doğru iki renkli geçiş. */
    def merkezdenDışarıDoğruDeğişim(
      merkezX: Kesir, merkezY: Kesir, renk1: Renk,
      yarıçap: Kesir, renk2: Renk,
      dalgalıDevam: İkil = yanlış
    ): Boya = kojo.Boya.merkezden(merkezX, merkezY, renk1, yarıçap, renk2, dalgalıDevam)

    /** Merkezden dışarı doğru çok duraklı geçiş. */
    def merkezdenDışarıDoğruÇokluDeğişim(
      merkezX: Kesir, merkezY: Kesir, yarıçap: Kesir,
      dağılım: Dizi[Kesir], renkler: Dizi[Renk],
      dalgalıDevam: İkil = yanlış
    ): Boya = kojo.Boya.merkezdenÇoklu(merkezX, merkezY, yarıçap, dağılım, renkler, dalgalıDevam)
  }

  /** Masaüstündeki kısa takma ad: RenkDD / RenkDoğrusalDeğişim. */
  def RenkDD(x1: Kesir, y1: Kesir, renk1: Renk, x2: Kesir, y2: Kesir, renk2: Renk,
    dalgalıDevam: İkil = yanlış): Boya =
    Renk.doğrusalDeğişim(x1, y1, renk1, x2, y2, renk2, dalgalıDevam)
  def RenkDoğrusalDeğişim(x1: Kesir, y1: Kesir, renk1: Renk, x2: Kesir, y2: Kesir, renk2: Renk,
    dalgalıDevam: İkil = yanlış): Boya =
    Renk.doğrusalDeğişim(x1, y1, renk1, x2, y2, renk2, dalgalıDevam)

  /**
   * Bir imge dosyasını döşeme boyası olarak kullanır (masaüstü DokumaBoya).
   * (x, y) döşemenin başladığı köşe.
   */
  def DokumaBoya(dosya: Yazı, x: Kesir, y: Kesir): Boya = kojo.Boya.dokuma(dosya, x, y)
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
    // Önce yalnız gereken altısı eklenmişti; artık masaüstündeki (kojo
    // tr/renk.scala) koyu ailenin TAMAMI burada.
    val koyuMavi: Renk = DRenk.darkBlue
    val koyuCamgöbeği: Renk = DRenk.darkCyan
    val koyuAltınbaşak: Renk = DRenk.darkGoldenrod
    val koyuKlasikGri: Renk = DRenk.darkGrayClassic
    val koyuYeşil: Renk = DRenk.darkGreen
    val koyuHaki: Renk = DRenk.darkKhaki
    val koyuMorumsu: Renk = DRenk.darkMagenta
    val koyuZeytinYeşili: Renk = DRenk.darkOliveGreen
    val koyuTuruncu: Renk = DRenk.darkOrange
    val koyuOrkidePembesi: Renk = DRenk.darkOrchid
    val koyuKırmızı: Renk = DRenk.darkRed
    val koyuSomon: Renk = DRenk.darkSalmon
    val koyuDenizYeşili: Renk = DRenk.darkSeaGreen
    val koyuKurşunMavisi: Renk = DRenk.darkSlateBlue
    val koyuKurşunGrisi: Renk = DRenk.darkSlateGray
    val koyuTurkuaz: Renk = DRenk.darkTurquoise
    val koyuMenekşe: Renk = DRenk.darkViolet

    // darkMagenta'nın asıl adı koyuMorumsu: masaüstündeki ad bu ve `morumsu`
    // (= magenta) ile tutarlı. Burada bir süre yalnız koyuMor vardı; onu
    // kullanan yazılımcıklar kırılmasın diye takma ad olarak duruyor.
    @deprecated("masaüstüyle aynı ada geçildi: koyuMorumsu kullanın", "Eylül 2026")
    val koyuMor: Renk = koyuMorumsu

    // Açık renkler: masaüstündeki ailenin tamamı. Renk değerleri
    // doodle/CommonColors.scala'da zaten vardı, eksik olan yalnız adlardı.
    val açıkMavi: Renk = DRenk.lightBlue
    val açıkMercan: Renk = DRenk.lightCoral
    val açıkCamgöbeği: Renk = DRenk.lightCyan
    val açıkAltınbaşakSarısı: Renk = DRenk.lightGoldenrodYellow
    val açıkYeşil: Renk = DRenk.lightGreen
    val açıkPembe: Renk = DRenk.lightPink
    val açıkSomon: Renk = DRenk.lightSalmon
    val açıkDenizYeşili: Renk = DRenk.lightSeaGreen
    val açıkGökMavisi: Renk = DRenk.lightSkyBlue
    val açıkKurşunGrisi: Renk = DRenk.lightSlateGray
    val açıkÇelikMavisi: Renk = DRenk.lightSteelBlue
    val açıkSarı: Renk = DRenk.lightYellow
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
  val koyuMorumsu = Renkler.koyuMorumsu
  @deprecated("masaüstüyle aynı ada geçildi: koyuMorumsu kullanın", "Eylül 2026")
  val koyuMor = Renkler.koyuMorumsu
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
    // Renk çemberinde döndürme ve açıklık ayarı (masaüstü tr/renk.scala:98-102).
    // spinBy masaüstünde var, doodle'ın bu kopyasında yok; oransal döndürmeyi
    // spin ile veriyoruz (oran 0-1 -> 0-360 derece), sonuç aynı.
    def çevir(açı: Kesir): Renk = r.spin(açı)
    def çevirOranla(oran: Kesir): Renk = r.spin(oran * 360)
    def dahaAçıkYap(açıklık: Kesir): Renk = r.lighten(açıklık)
    def dahaKoyuYap(koyuluk: Kesir): Renk = r.darken(koyuluk)
  }
}
