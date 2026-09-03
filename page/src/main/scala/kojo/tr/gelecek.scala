package kojo.tr

import scala.concurrent.{ExecutionContext, Future}

/**
 * Gelecek (Future) -- henüz hazır olmayan bir değeri temsil eder.
 *
 * Masaüstündeki `gelecek.scala`dan alınmayanlar ve nedenleri:
 *  - `JGelecek` (java.util.concurrent.Future): Scala.js javalib'inde yok.
 *  - `PEtkinlik` (edu.umd.cs.piccolo.activities.PActivity): Piccolo masaüstü
 *    çizim kütüphanesi; KojoJS PIXI kullanıyor.
 *  - `Gelecek.olmaz` (Future.never): 2.12'de mevcut değil.
 *
 * KojoJS'te tarayıcı tek iş parçacıklı olduğu için `İşletimBağlamı.küresel`
 * gerçekte olayları sıraya alır, paralel çalıştırmaz.
 */
trait GelecekYöntemleri extends TemelTürler {
  type Gelecek[T] = Future[T]
  type İşletimBağlamı = ExecutionContext

  object İşletimBağlamı {
    lazy val küresel: İşletimBağlamı = ExecutionContext.global
  }

  /**
   * Hazır örtük işletim bağlamı.
   *
   * Kullanıcının kendisi `implicit val ib: İşletimBağlamı = ...` YAZMAMALI:
   * fiddle'da bu satır `builtins`le aynı gövdeye düşüyor ve derleyici
   * "recursive value builtins needs type" hatası veriyor (örtüğün türünü
   * çözmek için trTurtle, onun için de builtins gerekiyor). Burada üye olarak
   * verince `import trTurtle._` ile kendiliğinden geliyor.
   *
   * Aynı nedenle `import scala.concurrent.ExecutionContext.Implicits.global`
   * da YAZILMAMALI (internetteki Future örneklerinin çoğunda var): bu örtükle
   * çakışır ve `ambiguous implicit values` hatası verir. Gerek de yok.
   */
  implicit lazy val küreselİşletimBağlamı: İşletimBağlamı = ExecutionContext.global

  object Gelecek {
    def başarılı[T](sonuç: T): Gelecek[T] = Future.successful(sonuç)
    // KASITLI daraltma: Future.failed aslında Throwable alıyor. Çocuk API'sinde
    // KuralDışı (Exception) yeterli ve masaüstü Koco'daki gelecek.scala ile aynı.
    def başarısız[T](hata: KuralDışı): Gelecek[T] = Future.failed(hata)
  }

  implicit class GelecekMetotları[T](g: Gelecek[T]) {
    def işle[A](işlev: T => A)(implicit ex: İşletimBağlamı): Gelecek[A] = g.map(işlev)(ex)
    def düzİşle[A](işlev: T => Gelecek[A])(implicit ex: İşletimBağlamı): Gelecek[A] = g.flatMap(işlev)(ex)
    def ele(deneme: T => İkil)(implicit ex: İşletimBağlamı): Gelecek[T] = g.filter(deneme)(ex)
    def elekle(deneme: T => İkil)(implicit ex: İşletimBağlamı): Gelecek[T] = g.withFilter(deneme)(ex)
  }
}
