package kojo.tr

/**
 * List'in Türkçesi.
 *
 * Masaüstündeki `ParalelDiziYöntemleri` (ve `Dizin.paralel`) buraya ALINMADI:
 * `scala.collection.parallel` Scala.js'te HİÇ yok -- tarayıcı tek iş parçacıklı.
 * Bu, portun kalıcı bir kaybı: tarayıcıda karşılığı olmadığı için şim (shim)
 * bile yazılamaz.
 */
trait DizinYöntemleri extends TemelTürler {
  val Boş = collection.immutable.Nil

  object Dizin {
    def apply[A](ögeler: A*): Dizin[A] = ögeler.toList
    def unapplySeq[A](list: Dizin[A]) = List.unapplySeq(list)
    def boş[A]: Dizin[A] = Nil
    def doldur[A](n: Sayı)(f: Sayı => A): Dizin[A] = List.tabulate(n)(f)
  }

  implicit class DizinMetotları[T](d: Dizin[T]) {
    type Col = Dizin[T]
    type Eşlek[A, D] = collection.immutable.Map[A, D]

    def başı: T = d.head
    def kuyruğu: Col = d.tail
    def önü: Col = d.init
    def sonu: T = d.last
    def boyu: Sayı = d.length
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def ele(deneme: T => İkil): Col = d.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Col = d.filterNot(deneme)
    def işle[A](işlev: T => A): Dizin[A] = d.map(işlev)
    def düzİşle[A](işlev: T => Dizin[A]): Dizin[A] = d.flatMap(işlev)
    def sıralı(implicit ord: Ordering[T]): Col = d.sorted(ord)
    def sırala[A](i: T => A)(implicit ord: Ordering[A]): Col = d.sortBy(i)
    def sırayaSok(önce: (T, T) => İkil): Col = d.sortWith(önce)
    def indirge[B >: T](işlem: (B, B) => B): B = d.reduce(işlem)
    def soldanKatla[T2](z: T2)(işlev: (T2, T) => T2): T2 = d.foldLeft(z)(işlev)
    def sağdanKatla[T2](z: T2)(işlev: (T, T2) => T2): T2 = d.foldRight(z)(işlev)
    def topla[T2 >: T](implicit num: Numeric[T2]) = d.sum(num)
    def çarp[T2 >: T](implicit num: Numeric[T2]) = d.product(num)
    def yinelemesiz: Col = d.distinct
    def yinelemesizİşlevle[T2](işlev: T => T2): Col = d.distinctBy(işlev)
    def yazıYap: Yazı = d.mkString
    def yazıYap(ara: Yazı): Yazı = d.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(başı, ara, sonu)
    def tersi: Col = d.reverse
    def değiştir[S >: T](yeri: Sayı, değeri: S): Dizin[S] = d.updated(yeri, değeri)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def içeriyorMu[S >: T](öge: S): İkil = d.contains(öge)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)
    def sırası[S >: T](öge: S): Sayı = d.indexOf(öge)
    def sırasıSondan[S >: T](öge: S): Sayı = d.lastIndexOf(öge)
    def dizine: Dizin[T] = d
    def diziye: Dizi[T] = d.toSeq
    def kümeye: Set[T] = d.toSet
    def yöneye: Vector[T] = d.toVector
    def say(işlev: T => İkil): Sayı = d.count(işlev)
    def dilim(nereden: Sayı, nereye: Sayı): Col = d.slice(nereden, nereye)
    def ikile[S](öbürü: Yinelenebilir[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)
    def böl(deneme: T => İkil): (Col, Col) = d.partition(deneme)
    def enUfağı[B >: T](implicit sıralama: Ordering[B]): T = d.min(sıralama)
    def enİrisi[B >: T](implicit sıralama: Ordering[B]): T = d.max(sıralama)
    def enUfağıİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.minBy(iş)(k)
    def enİrisiİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.maxBy(iş)(k)
  }
}
