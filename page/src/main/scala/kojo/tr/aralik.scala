package kojo.tr

/**
 * Range'in Türkçesi.
 *
 * Masaüstünde `Aralık` üst düzey bir case class; burada trait'in İÇİNDE, çünkü
 * paket nesnesini kaldırdık ve `Sayı`/`Dizin` gibi takma adlar TemelTürler'den
 * geliyor. Kullanıcıya `import trTurtle._` ile yine aynı adla ulaşıyor.
 */
trait AralıkYöntemleri extends TemelTürler {

  // Aralık ARTIK BİR TÜR TAKMA ADI (Küme = Set, Dizin = List kalıbı).
  // Eskiden Range'i saran bir case class'tı; o zaman `Aralık(1, 10)` yazan
  // öğrenci ~20 yöntem görüyordu, `1 |-| 10` yazan ise Range olduğu için
  // SıralıDizi/Diz sarmalayıcısının ~110 yöntemini. İki yüz birleşti.
  // Özel gösterim (`Aralık(1, 4, 7)`) yazı()/yazıya olarak aşağıda duruyor.
  type Aralık = Range


  object Aralık {
    def apply(ilki: Sayı, sonuncu: Sayı, adım: Sayı = 1): Range = Range(ilki, sonuncu, adım)
    def kapalı(ilki: Sayı, sonuncu: Sayı, adım: Sayı = 1): Range = Range.inclusive(ilki, sonuncu, adım)
    def kesirden(ilki: Kesir, sonuncu: Kesir, adım: Kesir) = Range.BigDecimal(ilki, sonuncu, adım)
    // kesirden ile aynı; "açık aralık" olduğunu adında belirten takma ad
    def kesirdenAçık(ilki: Kesir, sonuncu: Kesir, adım: Kesir) = Range.BigDecimal(ilki, sonuncu, adım)
    def kesirdenKapalı(ilki: Kesir, sonuncu: Kesir, adım: Kesir) =
      Range.BigDecimal.inclusive(ilki, sonuncu, adım)
  }

  implicit class RangeMetotları(r: Range) {
    // Aralık case class'ından taşınanlar (eski adlar korunuyor)
    def ilki: Sayı = r.start
    def sonuncu: Sayı = r.end
    def adımı: Sayı = r.step
    def adım: Sayı = r.step
    def uzunluğu: Sayı = r.size
    def başı: Sayı = r.head
    def sonu: Sayı = r.last
    def herÖgeİçin(komutlar: Sayı => Birim): Birim = r.foreach(komutlar)
    // Öğrenci dostu gösterim: toString ezilemez (Aralık artık tür takma adı),
    // ama bu yöntem eski çıktıyı verir.
    def yazı(): Yazı = yazıya
    def yazıya: Yazı = {
      val gövde =
        if (r.size <= 10) r.mkString("(", ", ", ")")
        else {
          val (b, s2) = (r.take(5), r.drop(r.size - 5))
          b.mkString("(", ", ", " ...") + s2.mkString(" ", ", ", ")")
        }
      s"Aralık$gövde"
    }
    def adım(c: Sayı): Range = r by c
    def diziye = r.toSeq
    def dizine = r.toList
    def boyu = r.length
    def içindeMi(s: Sayı) = r.contains(s)

    def işle[B](f: Sayı => B) = r.map(f)
    def elekle(deneme: Sayı => İkil) = r.withFilter(deneme)
    def düzİşle[B](f: Sayı => YinelenebilirBirKere[B]) = r.flatMap(f)
    def herbiriİçin(f: Sayı => Unit) = r.foreach(f)
    def indirge(iş: (Sayı, Sayı) => Sayı): Sayı = r.toSeq.reduce(iş)
    def soldanKatla[B](z: B)(iş: (B, Sayı) => B): B = r.toSeq.foldLeft(z)(iş)
    def sağdanKatla[B](z: B)(iş: (Sayı, B) => B): B = r.toSeq.foldRight(z)(iş)
  }
}
