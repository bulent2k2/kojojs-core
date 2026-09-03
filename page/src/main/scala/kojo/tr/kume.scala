package kojo.tr

/**
 * Set'in Türkçesi.
 *
 * `dizime` / `eşleme` masaüstünde Dizim/Eşlem sarmalayıcılarına dönüyor; onlar
 * henüz portlanmadı, düz Array/Map dönüyoruz.
 */
trait KümeYöntemleri extends TemelTürler {
  type Küme[T] = Set[T]

  object Küme {
    def apply[T](elemanlar: T*): Küme[T] = elemanlar.toSet
    def boş[T] = Set.empty[T]
  }

  implicit class KümeMetotları[T](d: Küme[T]) {
    type Col = Küme[T]
    type Eşlek[A, D] = collection.immutable.Map[A, D]

    def başı: T = d.head
    def kuyruğu: Col = d.tail
    def boyu: Sayı = d.size
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def ele(deneme: T => İkil): Col = d.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Col = d.filterNot(deneme)
    def işle[A](işlev: T => A): Küme[A] = d.map(işlev)
    def düzİşle[A](işlev: T => Küme[A]): Küme[A] = d.flatMap(işlev)
    def indirge[B >: T](işlem: (B, B) => B): B = d.reduce(işlem)
    def soldanKatla[T2](z: T2)(işlev: (T2, T) => T2): T2 = d.foldLeft(z)(işlev)
    def sağdanKatla[T2](z: T2)(işlev: (T, T2) => T2): T2 = d.foldRight(z)(işlev)
    def topla[T2 >: T](implicit num: Numeric[T2]) = d.sum(num)
    def çarp[T2 >: T](implicit num: Numeric[T2]) = d.product(num)
    def yazıYap: Yazı = d.mkString
    def yazıYap(ara: Yazı): Yazı = d.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(başı, ara, sonu)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def içeriyorMu(öge: T): İkil = d.contains(öge)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)

    def dizine = d.toList
    def diziye = d.toSeq
    def kümeye = d.toSet
    def yöneye = d.toVector
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def say(işlev: T => İkil): Sayı = d.count(işlev)

    def dilim(nereden: Sayı, nereye: Sayı) = d.slice(nereden, nereye)
    def ikile[S](öbürü: Yinelenebilir[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)

    def enUfağı[B >: T](implicit sıralama: Ordering[B]): T = d.min(sıralama)
    def enUfağıİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.minBy(iş)(k)
    def enİrisi[B >: T](implicit sıralama: Ordering[B]): T = d.max(sıralama)
    def enİrisiİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.maxBy(iş)(k)

    def altKümeleri(ögeSayısı: Sayı): Yineleyici[Col] = d.subsets(ögeSayısı)
    def boş: Col = d.empty
    def böl(deneme: T => İkil): (Col, Col) = d.partition(deneme)
    def kesişim(öbürü: Küme[T]): Küme[T] = d.intersect(öbürü)
    def bileşim(öbürü: Küme[T]): Küme[T] = d.union(öbürü)
    def fark(öbürü: Küme[T]): Küme[T] = d.diff(öbürü)
    def öbekli(boy: Sayı): Yineleyici[Col] = d.grouped(boy)
  }
}
