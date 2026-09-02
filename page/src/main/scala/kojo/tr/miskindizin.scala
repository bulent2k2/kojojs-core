package kojo.tr

/**
 * "Miskin" (tembel/lazy) dizin.
 *
 * 2.12 UYARLAMASI: masaüstü `LazyList` kullanıyor, o 2.13'te geldi. 2.12'deki
 * karşılığı `Stream` -- aynı tembel semantik, farklı ad. `MiskinDizin` takma adı
 * sayesinde KULLANICI kodu iki tarafta da aynı yazılıyor.
 *
 * Fark: Stream'in başı hevesli (head eager), LazyList'te o da tembel. Sonsuz
 * diziler ve `sayalım`/`yinele` gibi kullanımlar etkilenmiyor.
 */
trait MiskinDizinYöntemleri extends TemelTürler {
  type MiskinDizin[C] = Stream[C]

  object MiskinDizin {
    def ekle[A](diziler: Yinelenebilir[A]*): MiskinDizin[A] =
      diziler.foldLeft(Stream.empty[A])((acc, d) => acc #::: d.toStream)
    def sürekli[A](öge: => A): MiskinDizin[A] = Stream.continually(öge)
    def boş[A]: MiskinDizin[A] = Stream.empty[A]
    def doldur[A](s: Sayı)(öge: => A): MiskinDizin[A] = Stream.fill(s)(öge)
    def sayalım(başlangıç: Sayı, kaçarKaçar: Sayı = 1): MiskinDizin[Sayı] =
      Stream.from(başlangıç, kaçarKaçar)
    def yinele[S](başlangıç: => S)(işlev: S => S): MiskinDizin[S] =
      Stream.iterate(başlangıç)(işlev)
  }

  implicit class MiskinDizinMetotları[T](d: MiskinDizin[T]) {
    type Col = MiskinDizin[T]
    def başı: T = d.head
    def kuyruğu: Col = d.tail
    def boyu: Sayı = d.length
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def ele(deneme: T => İkil): Col = d.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Col = d.filterNot(deneme)
    def işle[A](işlev: T => A): MiskinDizin[A] = d.map(işlev)
    def düzİşle[A](işlev: T => MiskinDizin[A]): MiskinDizin[A] = d.flatMap(işlev)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def indirge[B >: T](işlem: (B, B) => B): B = d.reduce(işlem)
    def topla[T2 >: T](implicit num: Numeric[T2]) = d.sum(num)
    def dizine: Dizin[T] = d.toList
    def diziye: Dizi[T] = d.toSeq
    def yazıYap(ara: Yazı): Yazı = d.mkString(ara)
    def say(işlev: T => İkil): Sayı = d.count(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
  }
}
