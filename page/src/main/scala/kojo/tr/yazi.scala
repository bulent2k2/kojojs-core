package kojo.tr

/**
 * String'in Türkçesi.
 *
 * 2.13 (Faz 2): 2.12 uyarlamaları söküldü -- distinctBy ve toIntOption ailesi
 * artık gerçekleri. Not:
 *  - `dizime` / `eşleme` masaüstünde Dizim/Eşlem sarmalayıcılarına dönüyor;
 *    onlar henüz portlanmadı, bu yüzden düz Array/Map döndürüyoruz.
 */
trait YazıYöntemleri extends TemelTürler with BelkiYöntemleri with HarfYöntemleri {
  type EsnekYazı = collection.mutable.StringBuilder

  object Yazı {
    val tırnak = """ " """.trim
    type Harf = Char
    def olarak(n: Nesne) = String.valueOf(n)
    def olarak(n: Kesir) = String.valueOf(n)
    def olarak(n: Sayı) = String.valueOf(n)
    def olarak(n: İkil) = if (n) "doğru" else "yanlış"
    def olarak(n: Harf) = String.valueOf(n)
    def olarak(n: UfakKesir) = String.valueOf(n)
    def olarak(n: Uzun) = String.valueOf(n)
    def olarak(n: Array[Char]) = String.valueOf(n)
    def olarak(n: Array[Char], nereden: Sayı, kaçTane: Sayı) = String.valueOf(n, nereden, kaçTane)
  }

  implicit class YazıMetotları(y: Yazı) {
    type Harf = Char

    def başı: Harf = y.head
    def kuyruğu: Yazı = y.tail
    def önü: Yazı = y.init
    def sonu: Harf = y.last
    def boyu: Sayı = y.length
    def boşMu: İkil = y.isEmpty
    def doluMu: İkil = y.nonEmpty

    def ele(deneme: Harf => İkil): Yazı = y.filter(deneme)
    def eleDeğilse(deneme: Harf => İkil): Yazı = y.filterNot(deneme)
    def işle(işlev: Harf => Harf): Yazı = y.map(işlev)
    def düzİşle(işlev: Harf => Yazı): Yazı = y.flatMap(işlev)
    def sıralı(implicit ord: Ordering[Harf]): Yazı = y.sorted(ord)
    def sırala[A](i: Harf => A)(implicit ord: Ordering[A]): Yazı = y.sortBy(i)
    def sırayaSok(önce: (Harf, Harf) => İkil): Yazı = y.sortWith(önce)
    def indirge[B >: Harf](işlem: (B, B) => B): B = y.reduce(işlem)
    def soldanKatla[T2](z: T2)(işlev: (T2, Harf) => T2): T2 = y.foldLeft(z)(işlev)
    def sağdanKatla[T2](z: T2)(işlev: (Harf, T2) => T2): T2 = y.foldRight(z)(işlev)
    def yinelemesiz: Yazı = y.distinct
    def yinelemesizİşlevle[T2](işlev: Harf => T2): Yazı = y.distinctBy(işlev)
    def yazıYap: Yazı = y.mkString
    def yazıYap(ara: Yazı): Yazı = y.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = y.mkString(başı, ara, sonu)
    def tersi: Yazı = y.reverse
    def değiştir(yeri: Sayı, değeri: Harf): Yazı = y.updated(yeri, değeri)
    def herbiriİçin[S](işlev: Harf => S): Birim = y.foreach(işlev)
    def varMı(deneme: Harf => İkil): İkil = y.exists(deneme)
    def hepsiDoğruMu(deneme: Harf => İkil): İkil = y.forall(deneme)
    def hepsiİçinDoğruMu(deneme: Harf => İkil): İkil = y.forall(deneme)
    def içeriyorMu(dilim: Yazı): İkil = y.contains(dilim)
    def al(n: Sayı): Yazı = y.take(n)
    def alDoğruKaldıkça(deneme: Harf => İkil): Yazı = y.takeWhile(deneme)
    def alSağdan(n: Sayı): Yazı = y.takeRight(n)
    def düşür(n: Sayı): Yazı = y.drop(n)
    def düşürDoğruKaldıkça(deneme: Harf => İkil): Yazı = y.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Yazı = y.dropRight(n)
    def sırası(dilim: Yazı): Sayı = y.indexOf(dilim)
    def sırası(dilim: Yazı, başlamaNoktası: Sayı): Sayı = y.indexOf(dilim, başlamaNoktası)
    def sırasıSondan(dilim: Yazı): Sayı = y.lastIndexOf(dilim)
    def say(işlev: Harf => İkil): Sayı = y.count(işlev)

    // yazı'ya özel
    def kısalt: Yazı = y.trim
    def değiştir(a: Harf, b: Harf): Yazı = y.replace(a, b)
    def değiştir(xler: Yazı, yler: Yazı): Yazı = y.replace(xler, yler)
    def değiştirHepsini(xler: Yazı, yler: Yazı): Yazı = y.replaceAll(xler, yler)
    def değiştirİlkini(xler: Yazı, yler: Yazı): Yazı = y.replaceFirst(xler, yler)
    def böl(delim: Harf): Dizin[Yazı] = y.split(delim).toList
    def böl(delim: Yazı, enÇokParça: Sayı = 0): Dizin[Yazı] = y.split(delim, enÇokParça).toList
    // Türkçe i/İ kuralı -- bkz. harf.scala
    def büyükHarfe: Yazı = y.map(trBüyüt)
    def küçükHarfe: Yazı = y.map(trKüçült)
    def ilkHarfiBüyült: Yazı = if (y.isEmpty) y else trBüyüt(y.head) + y.tail
    def kıyasla(öbürü: Yazı): Sayı = y.compareTo(öbürü)
    def harf(sıra: Sayı): Harf = y.charAt(sıra)
    def parçası(nereden: Sayı): Yazı = y.substring(nereden)
    def parçası(nereden: Sayı, nereye: Sayı): Yazı = y.substring(nereden, nereye)
    def başındaMı(öbürü: Yazı): İkil = y.startsWith(öbürü)
    def sonundaMı(öbürü: Yazı): İkil = y.endsWith(öbürü)
    def kenarPayınıÇıkar: Yazı = y.stripMargin
    def eşlenirMi(düzenliDeyiş: Yazı): İkil = y.matches(düzenliDeyiş)

    def ikile = y.toBoolean
    def lokmaya = y.toByte
    def kesire = y.toDouble
    def ufakKesire = y.toFloat
    def sayıya = y.toInt
    def kısaya = y.toShort
    def dizine = y.toList
    def diziye = y.toSeq
    def kümeye = y.toSet
    def yöneye = y.toVector

    def sayıyaBelki: Belki[Sayı] = y.toIntOption
    def kesireBelki: Belki[Kesir] = y.toDoubleOption
    def ufakKesireBelki: Belki[UfakKesir] = y.toFloatOption
    def lokmayaBelki: Belki[Lokma] = y.toByteOption
    def kısayaBelki: Belki[Kısa] = y.toShortOption
    def ikileBelki: Belki[İkil] = y.toBooleanOption

    def öbekle(iş: Harf => Harf): collection.immutable.Map[Harf, Yazı] = y.groupBy(iş)
  }

  implicit class EsnekYazıMetotları(ey: EsnekYazı) {
    def boşMu = ey.size == 0
    def doluMu = ey.size != 0
    def boyu = ey.size
    def sil() = ey.clear()
    def ekle[T](x: T) = ey.append(x)
    def yazıya = ey.toString
    def sayıya = ey.toString.toInt
  }
}
