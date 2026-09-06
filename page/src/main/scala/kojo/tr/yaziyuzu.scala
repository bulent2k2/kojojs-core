package kojo.tr

/**
 * Masaüstü Koco'nun `Yazıyüzü` ailesi (trInit.scala: `type Yazıyüzü = java.awt.Font`,
 * `yazıyüzü(ad, boy[, biçem])`, `yazıyüzleri`). Tarayıcıda AWT yok; PIXI metin
 * stiline (font-family + font-size) eşleniyor. `biçem` masaüstünde Font.BOLD/ITALIC
 * bit maskesi; burada da aynı sayılar (1 kalın, 2 eğik, 3 ikisi).
 */
trait YazıyüzüYöntemleri extends TemelTürler {
  case class Yazıyüzü(ad: Yazı, boy: Sayı, biçem: Sayı = 0) {
    def kalınMı: İkil = (biçem & Yazıyüzü.KALIN) != 0
    def eğikMi: İkil = (biçem & Yazıyüzü.EĞİK) != 0
  }
  object Yazıyüzü {
    val DÜZ = 0
    val KALIN = 1
    val EĞİK = 2
  }
  /**
   * Masaüstünde `Yazıyüzü = java.awt.Font` olduğu için betikler `Font(ad, boy)`
   * de yazabiliyor. Yalnız iki bağımsız değişkenli biçim var: AWT'nin üç
   * bağımsız değişkenlisi (ad, BİÇEM, boy) sırayı değiştiriyor, sessizce yanlış
   * yorumlanmasın diye bilerek sunulmadı -- üç değişkenli için yazıyüzü(...).
   */
  object Font {
    def apply(ad: Yazı, boy: Sayı): Yazıyüzü = Yazıyüzü(ad, boy)
  }
  def yazıyüzü(adı: Yazı, boyu: Sayı): Yazıyüzü = Yazıyüzü(adı, boyu)
  def yazıyüzü(adı: Yazı, boyu: Sayı, biçem: Sayı): Yazıyüzü = Yazıyüzü(adı, boyu, biçem)
  /** Her tarayıcıda bulunan genel aileler; masaüstünde sistem yazıyüzleri listelenir. */
  def yazıyüzleri: Dizin[Yazı] = List("sans-serif", "serif", "monospace", "cursive", "fantasy",
    "Arial", "Helvetica", "Verdana", "Georgia", "Times New Roman", "Courier New")
}
