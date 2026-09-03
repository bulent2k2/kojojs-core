package kojo.tr

/**
 * Seq'in Türkçesi -- çocuklar için en çok kullanılan koleksiyon.
 *
 * 2.13 (Faz 2): masaüstüyle aynı yapı -- `Diz` (collection.Seq) ve `Dizi`
 * (immutable Seq) için AYRI implicit class'lar (masaüstündeki
 * colSeqYöntemleri/SeqYöntemleri ikilisinin karşılığı). Bir List/Vector için
 * ikisi de uygulanabilir; derleyici daha özgülü (DiziMetotları) seçer.
 */
trait DiziYöntemleri extends TemelTürler {

  object Dizi {
    def apply[B](ögeler: B*): Dizi[B] = ögeler.toSeq
    def unapplySeq[B](dizi: Dizi[B]) = Seq.unapplySeq(dizi)
    def boş[B]: Dizi[B] = Seq.empty[B]
    def doldur[B](n1: Sayı)(f: Sayı => B) = Seq.tabulate(n1)(f)
    def doldur[B](n1: Sayı, n2: Sayı)(f: (Sayı, Sayı) => B) = Seq.tabulate(n1, n2)(f)
    def doldur[B](n1: Sayı, n2: Sayı, n3: Sayı)(f: (Sayı, Sayı, Sayı) => B) = Seq.tabulate(n1, n2, n3)(f)
  }

  object Diz {
    def apply[B](ögeler: B*): Diz[B] = ögeler.toSeq
    def unapplySeq[B](dizi: Diz[B]) = collection.Seq.unapplySeq(dizi)
    def doldur[B](n1: Sayı)(f: Sayı => B) = Seq.tabulate(n1)(f)
  }

  // Masaüstündeki colSeqYöntemleri'nin karşılığı: collection.Seq (Diz) --
  // mutable Seq'ler de dahil. Değişmez Seq'lerde derleyici aşağıdaki daha
  // özgül DiziMetotları'nı seçer.
  implicit class DizMetotları[T](d: Diz[T]) {
    type Col = Diz[T]
    type C2[B] = Diz[B]
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
    def işle[A](işlev: T => A): C2[A] = d.map(işlev)
    def düzİşle[A](işlev: T => C2[A]): C2[A] = d.flatMap(işlev)
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
    def yazıYap(baş: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(baş, ara, sonu)
    def tersi: Col = d.reverse
    def değiştir[S >: T](yeri: Sayı, değeri: S): C2[S] = d.updated(yeri, değeri)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def içeriyorMu[S >: T](öge: S): İkil = d.contains(öge)
    def içeriyorMuDilim(dilim: Diz[T]): İkil = d.containsSlice(dilim)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)
    def sırası[S >: T](öge: S): Sayı = d.indexOf(öge)
    def sırasıSondan[S >: T](öge: S): Sayı = d.lastIndexOf(öge)

    def dizine: Dizin[T] = d.toList
    def diziye: Dizi[T] = d.toSeq
    def kümeye: Set[T] = d.toSet
    def yöneye: Vector[T] = d.toVector
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def say(işlev: T => İkil): Sayı = d.count(işlev)

    def dilim(nereden: Sayı, nereye: Sayı): Col = d.slice(nereden, nereye)
    def ikile[S](öbürü: Yinelenebilir[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)
    def öbekli(boy: Sayı): Yineleyici[Col] = d.grouped(boy)
    def böl(deneme: T => İkil): (Col, Col) = d.partition(deneme)

    def enUfağı[B >: T](implicit sıralama: Ordering[B]): T = d.min(sıralama)
    def enUfağıİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.minBy(iş)(k)
    def enİrisi[B >: T](implicit sıralama: Ordering[B]): T = d.max(sıralama)
    def enİrisiİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.maxBy(iş)(k)
  }

  implicit class DiziMetotları[T](d: Dizi[T]) {
    type Col = Dizi[T]
    type C2[B] = Dizi[B]
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
    def işle[A](işlev: T => A): C2[A] = d.map(işlev)
    def düzİşle[A](işlev: T => C2[A]): C2[A] = d.flatMap(işlev)
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
    def yazıYap(baş: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(baş, ara, sonu)
    def tersi: Col = d.reverse
    def değiştir[S >: T](yeri: Sayı, değeri: S): C2[S] = d.updated(yeri, değeri)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def içeriyorMu[S >: T](öge: S): İkil = d.contains(öge)
    def içeriyorMuDilim(dilim: Dizi[T]): İkil = d.containsSlice(dilim)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)
    def sırası[S >: T](öge: S): Sayı = d.indexOf(öge)
    def sırasıSondan[S >: T](öge: S): Sayı = d.lastIndexOf(öge)

    def dizine: Dizin[T] = d.toList
    def diziye: Dizi[T] = d.toSeq
    def kümeye: Set[T] = d.toSet
    def yöneye: Vector[T] = d.toVector
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def say(işlev: T => İkil): Sayı = d.count(işlev)

    def dilim(nereden: Sayı, nereye: Sayı): Col = d.slice(nereden, nereye)
    def ikile[S](öbürü: Yinelenebilir[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)
    def öbekli(boy: Sayı): Yineleyici[Col] = d.grouped(boy)
    def böl(deneme: T => İkil): (Col, Col) = d.partition(deneme)

    def enUfağı[B >: T](implicit sıralama: Ordering[B]): T = d.min(sıralama)
    def enUfağıİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.minBy(iş)(k)
    def enİrisi[B >: T](implicit sıralama: Ordering[B]): T = d.max(sıralama)
    def enİrisiİşlevle[B](iş: T => B)(implicit k: Ordering[B]): T = d.maxBy(iş)(k)
  }

  implicit class YineleyiciMetotları[T](d: Yineleyici[T]) {
    def işle[B](işlev: T => B): Yineleyici[B] = d.map(işlev)
    def herbiriİçin[B](işlev: T => B): Birim = d.foreach(işlev)
    def dizine: Dizin[T] = d.toList
    def diziye: Dizi[T] = d.toSeq
    def boyu: Sayı = d.size
  }
}
