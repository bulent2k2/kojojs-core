package kojo.tr

/**
 * Çekirdek nesne yöntemleri.
 *
 * DİKKAT: masaüstündeki özgün dosya TÜRKÇE ANAHTAR KELİMELER kullanıyor --
 * `özellik` (trait), `tanım` (def), `baskın` (override) -- ki bunlar yalnızca
 * yamalı scala-tr derleyicisinde var. KojoJS standart Scala ile derlendiği için
 * anahtar kelimeler İngilizceye çevrildi; ÜYE ADLARI Türkçe kaldı, yani
 * kullanıcının yazdığı kod aynı görünüyor:
 *
 *     class Nokta extends BaskınYazıyaYöntemiyle { def yazıya = "..." }
 */
trait KökTürYöntemleri extends TemelTürler {

  /** toString'i `yazıya` üzerinden veren özellik. */
  trait BaskınYazıyaYöntemiyle {
    def yazıya: Yazı
    override def toString = yazıya
  }

  /** hashCode/equals'ı `kıymaKodu` üzerinden veren özellik. */
  trait Eşsizlik {
    def kıymaKodu: Sayı
    override def hashCode = kıymaKodu
    override def equals(h2: Any) = h2 != null && h2.hashCode == kıymaKodu
  }

  implicit class NesneMetotları(h: Nesne) {
    def kıymaKodu = h.hashCode
    def eşitMi(h2: Her) = h.equals(h2)
    def nesnesiOlarak[T] = h.asInstanceOf[T]
    def yazıya = h.toString
  }

  implicit class HerGönderMetotları(h: HerGönder) {
    def aynıMı(h2: HerGönder): İkil = h eq h2
  }
}
