package kojo.tr

/** Option'ın Türkçesi. */
trait BelkiYöntemleri extends TemelTürler {
  type Belki[T] = Option[T]
  type Biri[T] = Some[T]
  val Hiçbiri = None

  object Biri {
    def apply[T](elem: T): Belki[T] = Some(elem)
    def unapply[T](b: Belki[T]) = b match {
      case None    => Hiçbiri
      case Some(n) => Some(n)
    }
  }

  def varMı[T](o: Belki[T]): İkil = o.isDefined
  def yokMu[T](o: Belki[T]): İkil = o.isEmpty

  implicit class BelkiMetotları[T](protected val b: Belki[T]) {
    def al = b.get
    def alYoksa[T2 >: T](t: => T2): T2 = b.getOrElse(t)

    def varMı: İkil = b.nonEmpty
    def yokMu: İkil = b.isEmpty
    def boşMu: İkil = b.isEmpty
    def doluMu: İkil = b.nonEmpty

    def işle[A](işlev: T => A): Belki[A] = b.map(işlev)
    def düzİşle[A](işlev: T => Option[A]): Belki[A] = b.flatMap(işlev)
    def ele(deneme: T => İkil): Belki[T] = b.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Belki[T] = b.filterNot(deneme)
    def elekle(deneme: T => İkil) = b.withFilter(deneme)

    def dizine: Dizin[T] = b.toList
  }
}
