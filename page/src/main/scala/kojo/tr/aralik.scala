package kojo.tr

/**
 * Range'in Türkçesi.
 *
 * Masaüstünde `Aralık` üst düzey bir case class; burada trait'in İÇİNDE, çünkü
 * paket nesnesini kaldırdık ve `Sayı`/`Dizin` gibi takma adlar TemelTürler'den
 * geliyor. Kullanıcıya `import trTurtle._` ile yine aynı adla ulaşıyor.
 */
trait AralıkYöntemleri extends TemelTürler {

  case class Aralık(ilki: Sayı, sonuncu: Sayı, adım: Sayı = 1) {
    val r = Range(ilki, sonuncu, adım)
    // lazy: boş aralıkta (Aralık(5,5), Aralık(1,5,-1)) head/last fırlatıyor.
    // Strict val olsalardı sadece .uzunluğu soran bir betik bile NESNE
    // KURULURKEN patlardı.
    lazy val başı = r.head
    lazy val sonu = r.last
    val uzunluğu = r.size
    def boyu = r.size
    def içindeMi(s: Sayı) = r.contains(s)
    def dizine: Dizin[Sayı] = r.toList
    def diziye: Dizi[Sayı] = r.toSeq
    def yazı() = toString()
    def yazıya() = toString()
    def herÖgeİçin(komutlar: Sayı => Birim) = r.foreach(komutlar)

    override def toString() = {
      val yazı =
        if (r.size <= 10) r.mkString("(", ", ", ")")
        else {
          val (b, s) = (r.take(5), r.drop(r.size - 5))
          b.mkString("(", ", ", " ...") + s.mkString(" ", ", ", ")")
        }
      s"Aralık$yazı"
    }

    // for-comprehension için Scala adları
    def map[B](f: Sayı => B) = r.map(f)
    def withFilter(pred: Sayı => İkil) = r.withFilter(pred)
    def flatMap[B](f: Sayı => YinelenebilirBirKere[B]) = r.flatMap(f)
    def foreach(f: Sayı => Unit) = r.foreach(f)

    def işle[B](f: Sayı => B) = r.map(f)
    def elekle(deneme: Sayı => İkil) = r.withFilter(deneme)
    def düzİşle[B](f: Sayı => YinelenebilirBirKere[B]) = r.flatMap(f)
    def herbiriİçin(f: Sayı => Unit) = r.foreach(f)
    def indirge(iş: (Sayı, Sayı) => Sayı): Sayı = diziye.reduce(iş)
    def soldanKatla[B](z: B)(iş: (B, Sayı) => B): B = diziye.foldLeft(z)(iş)
    def sağdanKatla[B](z: B)(iş: (Sayı, B) => B): B = diziye.foldRight(z)(iş)
  }

  object Aralık {
    def kapalı(ilki: Sayı, sonuncu: Sayı, adım: Sayı = 1) =
      new Aralık(ilki, if (adım > 0) sonuncu + 1 else sonuncu - 1, adım)
    def kesirden(ilki: Kesir, sonuncu: Kesir, adım: Kesir) = Range.BigDecimal(ilki, sonuncu, adım)
    def kesirdenAçık(ilki: Kesir, sonuncu: Kesir, adım: Kesir) = Range.BigDecimal(ilki, sonuncu, adım)
    def kesirdenKapalı(ilki: Kesir, sonuncu: Kesir, adım: Kesir) =
      Range.BigDecimal.inclusive(ilki, sonuncu, adım)
  }

  implicit class RangeMetotları(r: Range) {
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
