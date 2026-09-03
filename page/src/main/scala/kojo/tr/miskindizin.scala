package kojo.tr

/**
 * "Miskin" (tembel/lazy) dizin. 2.13 (Faz 2): masaüstüyle aynı, gerçek
 * `LazyList` (2.12 döneminde `Stream` ile taklit ediliyordu).
 */
trait MiskinDizinYöntemleri extends TemelTürler {
  type MiskinDizin[C] = LazyList[C]

  object MiskinDizin {
    def ekle[A](diziler: Yinelenebilir[A]*): MiskinDizin[A] =
      diziler.foldLeft(LazyList.empty[A])((acc, d) => acc #::: d.to(LazyList))
    def sürekli[A](öge: => A): MiskinDizin[A] = LazyList.continually(öge)
    def boş[A]: MiskinDizin[A] = LazyList.empty[A]
    def doldur[A](s: Sayı)(öge: => A): MiskinDizin[A] = LazyList.fill(s)(öge)
    def sayalım(başlangıç: Sayı, kaçarKaçar: Sayı = 1): MiskinDizin[Sayı] =
      LazyList.from(başlangıç, kaçarKaçar)
    def yinele[S](başlangıç: => S)(işlev: S => S): MiskinDizin[S] =
      LazyList.iterate(başlangıç)(işlev)
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
