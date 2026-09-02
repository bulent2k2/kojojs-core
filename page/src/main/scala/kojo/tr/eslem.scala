package kojo.tr

/**
 * Map'in Türkçesi. İki kapı var:
 *  - `Eşlek` : değişmez Map (collection.immutable.Map) -- uzantı metotlarıyla
 *  - `Eşlem` : değişebilir Map sarmalayıcısı (ekle/çıkar yapılabilen)
 *
 * 2.12 uyarlamaları: `Map.from(...)` -> `Map(... : _*)`, `addOne` -> `+=`,
 * `zip` Iterable alıyor. `dizime` (Dizim sarmalayıcısı) henüz portlanmadı.
 */
trait EşlemYöntemleri extends TemelTürler with BelkiYöntemleri {
  type Eşlek[A, D] = collection.immutable.Map[A, D]

  object Eşlek {
    def apply[A, D](elems: (A, D)*): Eşlek[A, D] = collection.immutable.Map(elems: _*)
    def boş[A, D]: Eşlek[A, D] = collection.immutable.Map.empty[A, D]
  }

  /** Değişebilir eşlem: içine ekleyip çıkarabilirsin. */
  case class Eşlem[A, D](m: collection.mutable.Map[A, D]) {
    type Pair = (A, D)

    def eşli(a: A) = m.contains(a)
    def eşEkle(ikili: Pair) = m += ikili
    def +=(ikili: Pair) = this eşEkle ikili
    def -=(birinci: A) = m -= birinci
    def herbiriİçin(komutlar: ((A, D)) => Birim) = m.foreach(komutlar)
    def herÖgeİçin(komutlar: ((A, D)) => Birim) = m.foreach(komutlar)
    def sayı: Sayı = m.size
    def dizi = m.toSeq
    def al(a: A): Belki[D] = m.get(a)
    def alYoksa(a: A, varsayılanDeğer: => D) = m.getOrElse(a, varsayılanDeğer)
    def apply(a: A) = m(a)

    def anahtarKümesi = m.keySet
    def anahtarlar = m.keys
    def değerler = m.values

    def başı = m.head
    def boyu: Sayı = m.size
    def boşMu: İkil = m.isEmpty
    def doluMu: İkil = m.nonEmpty
    def ele(deneme: ((A, D)) => İkil) = m.filter(deneme)
    def eleDeğilse(deneme: ((A, D)) => İkil) = m.filterNot(deneme)
    def işle[A2, D2](işlev: ((A, D)) => (A2, D2)) = m.map(işlev)
    def soldanKatla[B](z: B)(işlev: (B, Pair) => B): B = m.foldLeft(z)(işlev)
    def sağdanKatla[B](z: B)(işlev: (Pair, B) => B): B = m.foldRight(z)(işlev)
    def yazıYap: Yazı = m.mkString
    def yazıYap(ara: Yazı): Yazı = m.mkString(ara)
    // DİKKAT: m'yi YERİNDE değiştirmez, değiştirilmiş bir KOPYA döndürür.
    // (Değişebilir bir sarmalayıcıda ad yanıltıcı olabilir; yerinde değişiklik
    // için eşEkle/+= kullanın.) 2.13'teki addOne yerine +=.
    def değiştirilmiş(a: A, d: D) = m.clone() += (a -> d)
    def varMı(deneme: ((A, D)) => İkil): İkil = m.exists(deneme)
    def hepsiDoğruMu(deneme: ((A, D)) => İkil): İkil = m.forall(deneme)
    def içeriyorMu(anahtar: A): İkil = m.contains(anahtar)
    def dizine = m.toList
    def diziye = m.toSeq
    def say(işlev: Pair => İkil): Sayı = m.count(işlev)
    def varsayılanDeğerle(d: D) = m.withDefaultValue(d)
  }

  object Eşlem {
    def boş[A, D] = new Eşlem[A, D](collection.mutable.Map.empty[A, D])
    def apply[A, D](elems: (A, D)*) = new Eşlem[A, D](collection.mutable.Map(elems: _*))
    def değişmezden[A, D](m: collection.immutable.Map[A, D]) =
      new Eşlem[A, D](collection.mutable.Map(m.toSeq: _*))
  }

  implicit class EşlekMetotları[A, D](m: Eşlek[A, D]) {
    type Col = Eşlek[A, D]
    type Pair = (A, D)

    def eşli(a: A) = m.contains(a)
    def herbiriİçin(komutlar: ((A, D)) => Birim) = m.foreach(komutlar)
    def herÖgeİçin(komutlar: ((A, D)) => Birim) = m.foreach(komutlar)
    def sayı: Sayı = m.size
    def dizi = m.toSeq
    def al(a: A): Belki[D] = m.get(a)
    def alYoksa(a: A, varsayılanDeğer: => D) = m.getOrElse(a, varsayılanDeğer)

    def anahtarKümesi = m.keySet
    def anahtarlar = m.keys
    def değerler = m.values

    def başı = m.head
    def kuyruğu = m.tail
    def boyu: Sayı = m.size
    def boşMu: İkil = m.isEmpty
    def doluMu: İkil = m.nonEmpty
    def ele(deneme: ((A, D)) => İkil) = m.filter(deneme)
    def eleDeğilse(deneme: ((A, D)) => İkil) = m.filterNot(deneme)
    def işle[A2, D2](işlev: ((A, D)) => (A2, D2)) = m.map(işlev)
    def indirge[B >: Pair](işlem: (B, B) => B): B = m.reduce(işlem)
    def soldanKatla[B](z: B)(işlev: (B, Pair) => B): B = m.foldLeft(z)(işlev)
    def sağdanKatla[B](z: B)(işlev: (Pair, B) => B): B = m.foldRight(z)(işlev)
    def yazıYap: Yazı = m.mkString
    def yazıYap(ara: Yazı): Yazı = m.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = m.mkString(başı, ara, sonu)
    def varMı(deneme: ((A, D)) => İkil): İkil = m.exists(deneme)
    def hepsiDoğruMu(deneme: ((A, D)) => İkil): İkil = m.forall(deneme)
    def hepsiİçinDoğruMu(deneme: ((A, D)) => İkil): İkil = m.forall(deneme)
    def içeriyorMu(anahtar: A): İkil = m.contains(anahtar)

    def alSırayla(n: Sayı) = m.take(n)
    def düşür(n: Sayı) = m.drop(n)

    def dizine = m.toList
    def diziye = m.toSeq
    def kümeye = m.toSet
    def yöneye = m.toVector
    def say(işlev: ((A, D)) => İkil): Sayı = m.count(işlev)
    def ikileSırayla = m.zipWithIndex

    def varsayılanDeğerle(d: D) = m.withDefaultValue(d)
    def öbekle(iş: ((A, D)) => A): Eşlek[A, Eşlek[A, D]] = m.groupBy(iş)
    def değiştirilmiş[D1 >: D](a: A, d: D1): Eşlek[A, D1] = m.updated(a, d)

    def enUfağı[B >: Pair](implicit sıralama: Ordering[B]): Pair = m.min(sıralama)
    def enİrisi[B >: Pair](implicit sıralama: Ordering[B]): Pair = m.max(sıralama)
    def enİrisiİşlevle[B](iş: Pair => B)(implicit k: Ordering[B]): Pair = m.maxBy(iş)(k)
    def enUfağıİşlevle[B](iş: Pair => B)(implicit k: Ordering[B]): Pair = m.minBy(iş)(k)
  }
}
