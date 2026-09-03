package kojo.tr

import scala.reflect.ClassTag

/** Vector'ün Türkçesi. */
trait YöneyYöntemleri extends TemelTürler {
  type Yöney[T] = Vector[T]

  object Yöney {
    def apply[T](elemanlar: T*): Yöney[T] = elemanlar.toVector
    def unapplySeq[T](yler: Yöney[T]) = Vector.unapplySeq(yler)
    def boş[T]: Yöney[T] = Vector.empty[T]
    def doldur[T: ClassTag](b1: Sayı)(e: => T) = Vector.fill[T](b1)(e)
  }

  implicit class YöneyMetotları[A](y: Yöney[A]) {
    type Eşlek[K, D] = collection.immutable.Map[K, D]

    def başı: A = y.head
    def kuyruğu: Yöney[A] = y.tail
    def önü: Yöney[A] = y.init
    def sonu: A = y.last
    def boyu: Sayı = y.length
    def boşMu: İkil = y.isEmpty
    def doluMu: İkil = y.nonEmpty
    def ele(deneme: A => İkil): Yöney[A] = y.filter(deneme)
    def eleDeğilse(deneme: A => İkil): Yöney[A] = y.filterNot(deneme)
    def işle[B](işlev: A => B): Yöney[B] = y.map(işlev)
    def düzİşle[B](işlev: A => Yöney[B]): Yöney[B] = y.flatMap(işlev)
    def sıralı(implicit ord: Ordering[A]): Yöney[A] = y.sorted(ord)
    def sırala[B](i: A => B)(implicit ord: Ordering[B]): Yöney[A] = y.sortBy(i)
    def sırayaSok(önce: (A, A) => İkil): Yöney[A] = y.sortWith(önce)
    def indirge[B >: A](işlem: (B, B) => B): B = y.reduce(işlem)
    def soldanKatla[B](z: B)(işlev: (B, A) => B): B = y.foldLeft(z)(işlev)
    def sağdanKatla[B](z: B)(işlev: (A, B) => B): B = y.foldRight(z)(işlev)
    def topla[B >: A](implicit num: Numeric[B]) = y.sum(num)
    def çarp[B >: A](implicit num: Numeric[B]) = y.product(num)
    def yinelemesiz: Yöney[A] = y.distinct
    def güncellenmiş(dizin: Sayı, değer: A): Yöney[A] = y.updated(dizin, değer) // updated
    def yinelemesizİşlevle[B](işlev: A => B): Yöney[A] = y.distinctBy(işlev)
    def yazıYap: Yazı = y.mkString
    def yazıYap(ara: Yazı): Yazı = y.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = y.mkString(başı, ara, sonu)
    def tersi: Yöney[A] = y.reverse
    def değiştir[B >: A](yeri: Sayı, değeri: B): Yöney[B] = y.updated(yeri, değeri)
    def herbiriİçin[S](işlev: A => S): Birim = y.foreach(işlev)
    def varMı(deneme: A => İkil): İkil = y.exists(deneme)
    def hepsiDoğruMu(deneme: A => İkil): İkil = y.forall(deneme)
    def içeriyorMu[B >: A](öge: B): İkil = y.contains(öge)
    def al(n: Sayı): Yöney[A] = y.take(n)
    def düşür(n: Sayı): Yöney[A] = y.drop(n)
    def sırası[B >: A](öge: B): Sayı = y.indexOf(öge)
    def dizine: Dizin[A] = y.toList
    def diziye: Dizi[A] = y.toSeq
    def kümeye: Set[A] = y.toSet
    def yöneye: Yöney[A] = y
    def say(işlev: A => İkil): Sayı = y.count(işlev)
    def dilim(nereden: Sayı, nereye: Sayı): Yöney[A] = y.slice(nereden, nereye)
    def ikileSırayla = y.zipWithIndex
    def öbekle[K](iş: A => K): Eşlek[K, Yöney[A]] = y.groupBy(iş)
    def böl(deneme: A => İkil): (Yöney[A], Yöney[A]) = y.partition(deneme)
    def enUfağı[B >: A](implicit sıralama: Ordering[B]): A = y.min(sıralama)
    def enİrisi[B >: A](implicit sıralama: Ordering[B]): A = y.max(sıralama)
  }
}
