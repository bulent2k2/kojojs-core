package kojo.tr

/**
 * Masaüstü Koco'nun `Yazıyüzü` ailesi (trInit.scala: `type Yazıyüzü = java.awt.Font`,
 * `yazıyüzü(ad, boy[, biçem])`, `yazıyüzleri`). Tarayıcıda AWT yok; PIXI metin
 * stiline (font-family + font-size) eşleniyor. `biçem` masaüstünde Font.BOLD/ITALIC
 * bit maskesi; burada da aynı sayılar (1 kalın, 2 eğik, 3 ikisi).
 */
trait YazıyüzüYöntemleri extends TemelTürler {
  // İngilizce Font ile AYNI tür (kojo.Font, bkz. kojo/Font.scala): `Picture.text(s, Font(...), renk)` ile
  // `Resim.yazı(s, yazıyüzü(...), renk)` birbirinin yazı yüzünü kabul eder. Eskiden burada ayrı bir
  // case class vardı (ad, boy, biçem); alan adları aşağıdaki örtük sınıfla (ad, boy, biçem) aynen duruyor.
  type Yazıyüzü = kojo.Font
  object Yazıyüzü {
    val DÜZ = 0
    val KALIN = 1
    val EĞİK = 2
    def apply(ad: Yazı, boy: Sayı, biçem: Sayı = 0): Yazıyüzü = kojo.Font(ad, boy, biçem)
    def unapply(yy: kojo.Font): Option[(Yazı, Sayı, Sayı)] = Some((yy.name, yy.size, yy.style))
  }
  implicit class YazıyüzüMetotları(yy: kojo.Font) {
    def ad: Yazı = yy.name
    def boy: Sayı = yy.size
    def biçem: Sayı = yy.style
    def kalınMı: İkil = (yy.style & Yazıyüzü.KALIN) != 0
    def eğikMi: İkil = (yy.style & Yazıyüzü.EĞİK) != 0
  }
  // `Font(ad, boy)` Türkçe betiklerde de çalışır, ama BURADAN değil: betik önsözü `builtins._` ve
  // `trTurtle._`'i aynı düzeyde içe aktarıyor; iki ayrı `Font` "reference to Font is ambiguous"
  // olurdu. Tek `Font` İngilizce yüzeyde (kojo.syntax.Builtins.Font) ve aynı türü (kojo.Font) üretiyor.
  // Masaüstünde `Yazıyüzü = java.awt.Font` olduğu için `Font(ad, boy)` oradan da gelir; AWT'nin üç
  // bağımsız değişkenlisi (ad, BİÇEM, boy) sırayı değiştiriyor, sessizce yanlış yorumlanmasın diye
  // sunulmadı -- üç değişkenli için yazıyüzü(...).
  def yazıyüzü(adı: Yazı, boyu: Sayı): Yazıyüzü = Yazıyüzü(adı, boyu)
  def yazıyüzü(adı: Yazı, boyu: Sayı, biçem: Sayı): Yazıyüzü = Yazıyüzü(adı, boyu, biçem)
  /** Her tarayıcıda bulunan genel aileler; masaüstünde sistem yazıyüzleri listelenir. */
  def yazıyüzleri: Dizin[Yazı] = List("sans-serif", "serif", "monospace", "cursive", "fantasy",
    "Arial", "Helvetica", "Verdana", "Georgia", "Times New Roman", "Courier New")
}
