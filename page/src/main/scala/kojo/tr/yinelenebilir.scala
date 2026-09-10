/*
 * Copyright (C) 2026
 *   Bulent Basaran <ben@scala.org> https://github.com/bulent2k2
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */
package kojo.tr

/**
 * DÜŞÜK ÖNCELİKLİ taban: her topluluğun üstündeki Yinelenebilir (Iterable).
 *
 * Neden ayrı bir trait: Eşlek de bir Yinelenebilir'dir, yani `ele`, `dizine`
 * gibi adlarda EşlekMetotları ile bu sarmalayıcı ikisi birden uygulanabiliyor
 * ve derleyici "ambiguous implicit" diyor. Scala örtük sarmalayıcıları
 * kalıtıma göre sıralar: ALT trait'teki üsttekini yener. Bu yüzden özgül
 * topluluk trait'leri bunu genişletiyor.
 */
trait YinelenebilirYöntemleri extends TemelTürler with DizimYöntemleri {
  /**
   * Yinelenebilir (Iterable) -- Diz/Dizi'nin ÜSTÜNDEKİ tür.
   *
   * Neden gerekli: Eşlek.anahtarlar ve Eşlek.değerler bir Iterable veriyor,
   * Diz değil. Bu sarmalayıcı olmadan `Eşlek("a" -> 1).anahtarlar.dizine`
   * derlenmiyordu -- sözlükteki "sınanmış" örneklerden biri tam da buydu.
   * Masaüstündeki dizi.scala:IterableMethods'un karşılığı.
   */
  implicit class YinelenebilirMetotları[T](d: Yinelenebilir[T]) {
    type Col = Yinelenebilir[T]
    type C2[B] = Yinelenebilir[B]
    type Eşlek[A, D] = collection.immutable.Map[A, D]

    def ele(deneme: T => İkil): Col = d.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Col = d.filterNot(deneme)
    def işle[A](işlev: T => A): C2[A] = d.map(işlev)
    def düzİşle[A](işlev: T => C2[A]): C2[A] = d.flatMap(işlev)
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)

    def dizine = d.toList
    def diziye = d.toSeq
    def kümeye = d.toSet
    def yöneye = d.toVector
    def dizime[S >: T](implicit delil: scala.reflect.ClassTag[S]): Dizim[S] = new Dizim(d.toArray(delil))
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def ikile[S](öbürü: YinelenebilirBirKere[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: (T) => A): Eşlek[A, Col] = d.groupBy(iş)
  }

  /**
   * bellekli'nin verdiği yineleyici. Yukarıdaki bütün yöntemler buna da
   * uygulanıyor (BufferedIterator bir Yineleyici'dir); tek eksik, TÜKETMEDEN
   * öne bakmayı sağlayan head idi.
   */
  implicit class BellekliYineleyiciMetotları[T](b: collection.BufferedIterator[T]) {
    // başı okumak yineleyiciyi İLERLETMEZ -- sıradaki'den farkı bu
    def başı: T = b.head
  }
}
