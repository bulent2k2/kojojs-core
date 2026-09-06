package kojo.tr

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/**
 * Masaüstü Koco'nun `Dizik` (Array) ve `EsnekDizik` (ArrayBuffer) adları
 * (kojo: lite/i18n/tr/dizik.scala). Burada yalnız tür takma adları ve
 * kurucular var; masaüstündeki `ArrayMethods` yöntem sarmalayıcısı (başı,
 * kuyruğu, ele, ...) Devre 2'ye bırakıldı. Eski adlandırma `Dizim`/`EsnekDizim`
 * (dizim.scala) sarmalayıcı sınıf kullanıyor; bunlar ise doğrudan Array /
 * ArrayBuffer, masaüstüyle birebir.
 */
trait DizikYöntemleri extends TemelTürler {

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
