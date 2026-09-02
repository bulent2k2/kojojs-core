package kojo.tr

import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuffer

/**
 * Array ve ArrayBuffer sarmalayıcıları (eski adlandırma; dizik.scala daha yeni).
 *
 * 2.12 uyarlamaları: `ArrayBuffer.from(...)` -> `ArrayBuffer(... : _*)`,
 * `filterInPlace` (2.13) -> `retain`.
 */
trait DizimYöntemleri extends TemelTürler {

  class EsnekDizim[T](val a: ArrayBuffer[T]) {
    type Col = EsnekDizim[T]
    type C2[A] = EsnekDizim[A]
    def apply(yer: Sayı) = a(yer)
    def sayı = a.size
    def boyu = a.size
    def ekle(öge: T) = { a.append(öge); this }
    def +=(öge: T) = ekle(öge)
    def çıkar(yer: Sayı) = a.remove(yer)
    def sil() = a.clear()
    def dizi = a.toSeq
    def diziye = a.toSeq
    def dizine = a.toList
    def boşMu: İkil = a.isEmpty
    def doluMu: İkil = a.nonEmpty
    def ele(deneme: T => İkil): Col = new EsnekDizim(a.filter(deneme))
    // 2.13'te filterInPlace var; 2.12'de ArrayBuffer'da ne o ne de retain
    // bulunuyor, elle yapıyoruz.
    def eleYerinde(deneme: T => İkil): this.type = {
      val kalan = a.filter(deneme)
      a.clear()
      a ++= kalan
      this
    }
    def işle[B](işlev: T => B): C2[B] = new EsnekDizim(a.map(işlev))
    def herbiriİçin[B](işlev: T => B): Birim = a.foreach(işlev)
    override def toString = a.mkString("EsnekDizim(", ", ", ")")
  }

  object EsnekDizim {
    def apply[T](ögeler: T*) = new EsnekDizim[T](ArrayBuffer(ögeler: _*))
    def boş[T] = new EsnekDizim[T](ArrayBuffer.empty[T])
  }

  class Dizim[T](val a: Array[T]) {
    def diziye = a.toSeq
    def dizine = a.toList
    def boyu = a.length
    def apply(b1: Sayı) = a(b1)
    def güncelle(yer: Sayı, değer: T): Birim = a(yer) = değer
    def yazıya = toString
    override def toString = a.mkString("Dizim(", ", ", ")")
  }

  object Dizim {
    def apply[T: ClassTag](ögeler: T*) = new Dizim(ögeler.toArray)
    def boş[T: ClassTag] = new Dizim(Array.empty[T])
    def boşBoyutlu[T: ClassTag](b1: Sayı) = new Dizim(Array.ofDim[T](b1))
    def doldur[T: ClassTag](b1: Sayı)(e: => T) = new Dizim(Array.fill[T](b1)(e))
  }
}
