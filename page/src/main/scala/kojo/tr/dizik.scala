package kojo.tr

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/**
 * Masaüstü Koco'nun `Dizik` (Array) ve `EsnekDizik` (ArrayBuffer) adları
 * (kojo: lite/i18n/tr/dizik.scala). Eski adlandırma `Dizim`/`EsnekDizim`
 * (dizim.scala) sarmalayıcı sınıf kullanıyor; bunlar ise doğrudan Array /
 * ArrayBuffer, masaüstüyle birebir.
 *
 * `DizikMetotları`, masaüstündeki `ArrayMethods` örtük sınıfının karşılığı:
 * çok boyutlu `Dizim`/`Dizik` kurucularının iç katmanı Array olduğu için
 * (`tahta(x)(y) = değer` böyle çalışıyor) betikler o satırlara Türkçe
 * yöntemlerle erişiyor -- `tahta(x).diziye` gibi.
 */
trait DizikYöntemleri extends TemelTürler with DizimYöntemleri {

  type Dizik[T] = Array[T]
  object Dizik {
    def apply[T: ClassTag](ögeler: T*): Dizik[T] = Array(ögeler: _*)
    // parantezli: parantezsiz olsaydı Dizik.boş[Nokta](a, b) çağrısı (a, b)'yi örtük
    // liste sanır (Scala 2 tuzağı) -- "too many arguments" (genart-tri-mesh)
    def boş[T: ClassTag](): Dizik[T] = Array.empty[T]
    def boş[T: ClassTag](b1: Sayı): Dizik[T] = Array.ofDim[T](b1)
    def boş[T: ClassTag](b1: Sayı, b2: Sayı): Dizik[Dizik[T]] = Array.ofDim[T](b1, b2)
    def boş[T: ClassTag](b1: Sayı, b2: Sayı, b3: Sayı): Dizik[Dizik[Dizik[T]]] = Array.ofDim[T](b1, b2, b3)
    def doldur[T: ClassTag](b1: Sayı)(e: => T): Dizik[T] = Array.fill[T](b1)(e)
    def doldur[T: ClassTag](b1: Sayı, b2: Sayı)(e: => T): Dizik[Dizik[T]] = Array.fill[T](b1, b2)(e)
    def doldur[T: ClassTag](b1: Sayı, b2: Sayı, b3: Sayı)(e: => T): Dizik[Dizik[Dizik[T]]] =
      Array.fill[T](b1, b2, b3)(e)
  }

  /** Masaüstündeki `ArrayMethods` (kojo: lite/i18n/tr/dizik.scala). */
  implicit class DizikMetotları[T](d: Dizik[T]) {
    type Belki[B] = Option[B]
    type Col = Dizik[T]
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
    // Array.map ClassTag istiyor (dizi türü çalışma zamanında somut)
    def işle[A](işlev: T => A)(implicit ct: ClassTag[A]): Dizik[A] = d.map(işlev)(ct)
    def işleYerinde(işlev: T => T): Dizik[T] = d.mapInPlace(işlev)
    def düzİşle[A: ClassTag](işlev: T => Dizik[A]): Dizik[A] = d.flatMap(işlev)
    def sıralı(implicit ord: Ordering[T]): Col = d.sorted(ord)
    def sırala[A](i: T => A)(implicit ord: Ordering[A]): Col = d.sortBy(i)
    def sırayaSok(önce: (T, T) => İkil): Col = d.sortWith(önce)
    def indirge[B >: T](işlem: (B, B) => B): B = d.reduce(işlem)
    def soldanKatla[T2](z: T2)(işlev: (T2, T) => T2): T2 = d.foldLeft(z)(işlev)
    def sağdanKatla[T2](z: T2)(işlev: (T, T2) => T2): T2 = d.foldRight(z)(işlev)
    def topla[T2 >: T](implicit num: scala.math.Numeric[T2]) = d.sum(num)
    def çarp[T2 >: T](implicit num: scala.math.Numeric[T2]) = d.product(num)
    def yinelemesiz = d.distinct
    def yinelemesizİşlevle[T2](işlev: T => T2): Col = d.distinctBy(işlev)
    def yazıYap: Yazı = d.mkString
    def yazıYap(ara: Yazı): Yazı = d.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(başı, ara, sonu)
    def tersi = d.reverse
    def değiştir[S >: T: ClassTag](yeri: Sayı, değeri: S): Dizik[S] = d.updated(yeri, değeri)
    def değiştirYerinde(yeri: Sayı, değeri: T): Birim = d.update(yeri, değeri)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def içeriyorMu(öge: T): İkil = d.contains(öge)
    def içeriyorMuDilim(dilim: Col): İkil = d.containsSlice(dilim)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)
    def sırası(öge: T): Sayı = d.indexOf(öge)
    def sırası(öge: T, başlamaNoktası: Sayı): Sayı = d.indexOf(öge, başlamaNoktası)
    def sırasıSondan(öge: T): Sayı = d.lastIndexOf(öge)
    def sırasıSondan(öge: T, sonNokta: Sayı): Sayı = d.lastIndexOf(öge, sonNokta)
    def dizine = d.toList
    def diziye = d.toSeq
    def kümeye = d.toSet
    def yöneye = d.toVector
    def dizime[S >: T](implicit delil: ClassTag[S]): Dizim[S] = new Dizim(d.toArray(delil))
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def say(işlev: T => İkil): Sayı = d.count(işlev)
    def dilim(nereden: Sayı, nereye: Sayı) = d.slice(nereden, nereye)
    def ikile[S](öbürü: YinelenebilirBirKere[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)
    def enUfağı[B >: T](implicit sıralama: math.Ordering[B]): T = d.min(sıralama)
    def enUfağı[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): T = d.minBy(iş)(karşılaştırma)
    def enİrisi[B >: T](implicit sıralama: math.Ordering[B]): T = d.max(sıralama)
    def enİrisi[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): T = d.maxBy(iş)(karşılaştırma)
  
    // --- uçlar, arama --------------------------------------------------
    // Dizik = Array: yöntemleri ArrayOps'tan geliyor, o yüzden küme
    // ötekilerden biraz dar (ör. findLast, corresponds, indexOfSlice yok).
    // Yeni bir dizik ÜRETEN yöntemler ClassTag ister; onu ayrıca alıyoruz.
    def başıBelki: Belki[T] = d.headOption
    def sonuBelki: Belki[T] = d.lastOption
    def bul(deneme: T => İkil): Belki[T] = d.find(deneme)
    def nerede(deneme: T => İkil): Sayı = d.indexWhere(deneme)
    def nerede(deneme: T => İkil, başlamaNoktası: Sayı): Sayı = d.indexWhere(deneme, başlamaNoktası)
    def neredeSondan(deneme: T => İkil): Sayı = d.lastIndexWhere(deneme)
    def sıralar: Range = d.indices
    def başındaMı[S >: T](dizi: Dizik[S]): İkil = d.startsWith(dizi)
    def sonundaMı[S >: T](dizi: Dizik[S]): İkil = d.endsWith(dizi)

    // --- bölme, öbekleme -----------------------------------------------
    def böl(deneme: T => İkil)(implicit delil: ClassTag[T]): (Col, Col) = d.partition(deneme)
    def bölİşle[A1: ClassTag, A2: ClassTag](işlev: T => Either[A1, A2]): (Dizik[A1], Dizik[A2]) = d.partitionMap(işlev)
    def bölDoğruKaldıkça(deneme: T => İkil)(implicit delil: ClassTag[T]): (Col, Col) = d.span(deneme)
    def bölYerinden(yeri: Sayı)(implicit delil: ClassTag[T]): (Col, Col) = d.splitAt(yeri)
    def öbekli(boy: Sayı)(implicit delil: ClassTag[T]): Yineleyici[Col] = d.grouped(boy)
    def kayarÖbekli(boy: Sayı)(implicit delil: ClassTag[T]): Yineleyici[Col] = d.sliding(boy)
    def kayarÖbekli(boy: Sayı, adım: Sayı)(implicit delil: ClassTag[T]): Yineleyici[Col] = d.sliding(boy, adım)
    def öbekleİşle[K, B: ClassTag](anahtar: T => K)(değer: T => B): Eşlek[K, Dizik[B]] = d.groupMap(anahtar)(değer)
    def kombinasyonlar(ögeSayısı: Sayı)(implicit delil: ClassTag[T]): Yineleyici[Col] = d.combinations(ögeSayısı)
    def permütasyonlar(implicit delil: ClassTag[T]): Yineleyici[Col] = d.permutations
    def kuyruklar(implicit delil: ClassTag[T]): Yineleyici[Col] = d.tails
    def önler(implicit delil: ClassTag[T]): Yineleyici[Col] = d.inits

    // --- katlama, tarama -----------------------------------------------
    def katla[S >: T](z: S)(işlev: (S, S) => S): S = d.fold(z)(işlev)
    def tara[S >: T: ClassTag](z: S)(işlev: (S, S) => S): Dizik[S] = d.scan(z)(işlev)
    def taraSoldan[B: ClassTag](z: B)(işlev: (B, T) => B): Dizik[B] = d.scanLeft(z)(işlev)
    def taraSağdan[B: ClassTag](z: B)(işlev: (T, B) => B): Dizik[B] = d.scanRight(z)(işlev)

    // --- ekleme, çıkarma -----------------------------------------------
    def sonunaEkle[S >: T: ClassTag](öge: S): Dizik[S] = d.appended(öge)
    def önüneEkle[S >: T: ClassTag](öge: S): Dizik[S] = d.prepended(öge)
    def sonunaEkleHepsini[S >: T: ClassTag](öbürü: YinelenebilirBirKere[S]): Dizik[S] = d.appendedAll(öbürü)
    def önüneEkleHepsini[S >: T: ClassTag](öbürü: YinelenebilirBirKere[S]): Dizik[S] = d.prependedAll(öbürü)
    def uzat[S >: T: ClassTag](boy: Sayı, öge: S): Dizik[S] = d.padTo(boy, öge)
    def yama[S >: T: ClassTag](nereden: Sayı, yenisi: YinelenebilirBirKere[S], kaçTane: Sayı): Dizik[S] =
      d.patch(nereden, yenisi, kaçTane)
    def fark[S >: T](öbürü: Dizi[S])(implicit delil: ClassTag[T]): Col = d.diff(öbürü)
    def kesişim[S >: T](öbürü: Dizi[S])(implicit delil: ClassTag[T]): Col = d.intersect(öbürü)

    // --- seçme, düzleştirme, ikili işlemler ----------------------------
    def seçİşle[B: ClassTag](işlev: PartialFunction[T, B]): Dizik[B] = d.collect(işlev)
    def seçİşleİlk[B](işlev: PartialFunction[T, B]): Belki[B] = d.collectFirst(işlev)
    def düzleştir[B](implicit delil: T => YinelenebilirBirKere[B], delil2: ClassTag[B]): Dizik[B] = d.flatten(delil, delil2)
    def devrik[B](implicit delil: T => Dizik[B], delil2: ClassTag[B]): Dizik[Dizik[B]] = d.transpose(delil)
    def ikiliyiAç[A1, A2](implicit delil: T => (A1, A2), d1: ClassTag[A1], d2: ClassTag[A2]): (Dizik[A1], Dizik[A2]) =
      d.unzip(delil, d1, d2)
    def ikileHepsini[B, S >: T: ClassTag](öbürü: Yinelenebilir[B], buDolgu: S, oDolgu: B): Dizik[(S, B)] =
      d.zipAll(öbürü, buDolgu, oDolgu)
    def tersİşle[B: ClassTag](işlev: T => B)(implicit delil: ClassTag[T]): Dizik[B] = d.reverse.map(işlev)
}

  type EsnekDizik[T] = ArrayBuffer[T]
  object EsnekDizik {
    def apply[T](ögeler: T*): EsnekDizik[T] = ArrayBuffer(ögeler: _*)
    def boş[T]: EsnekDizik[T] = ArrayBuffer.empty[T]
    def doldur[T](b1: Sayı)(e: => T): EsnekDizik[T] = ArrayBuffer.fill[T](b1)(e)
    def doldur[T](b1: Sayı, b2: Sayı)(e: => T): EsnekDizik[EsnekDizik[T]] = ArrayBuffer.fill[T](b1, b2)(e)
    def doldur[T](b1: Sayı, b2: Sayı, b3: Sayı)(e: => T): EsnekDizik[EsnekDizik[EsnekDizik[T]]] =
      ArrayBuffer.fill[T](b1, b2, b3)(e)
    def diziden[T](dizi: IterableOnce[T]): EsnekDizik[T] = ArrayBuffer.from(dizi)
  }
}
