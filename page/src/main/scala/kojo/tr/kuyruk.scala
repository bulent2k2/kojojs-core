package kojo.tr

import scala.collection.mutable.{Stack, Queue, PriorityQueue}

/**
 * Yığın (Stack), Kuyruk (Queue) ve Öncelik Sırası (PriorityQueue).
 *
 */
trait KuyrukYöntemleri extends TemelTürler {
  type Yığın[T] = Stack[T]
  type Kuyruk[T] = Queue[T]
  type ÖncelikSırası[T] = PriorityQueue[T]

  object Yığın {
    def apply[T](elems: T*): Yığın[T] = Stack(elems: _*)
    def boş[T]: Yığın[T] = Stack.empty[T]
  }

  object Kuyruk {
    def apply[T](elems: T*): Kuyruk[T] = Queue(elems: _*)
    def boş[T]: Kuyruk[T] = Queue.empty[T]
  }

  object ÖncelikSırası {
    def apply[T](elems: T*)(implicit sıralama: Ordering[T]): ÖncelikSırası[T] =
      PriorityQueue(elems: _*)(sıralama)
    def boş[T](implicit sıralama: Ordering[T]): ÖncelikSırası[T] = PriorityQueue.empty[T](sıralama)
  }

  implicit class YığınMetotları[T](d: Yığın[T]) {
    def it(öge: T): Yığın[T] = d.push(öge)
    def çek(): T = d.pop()
    def koy(öge: T): Yığın[T] = d.push(öge) // it ile aynı (kitapçık adı)
    def al(): T = d.pop()          // çek ile aynı (kitapçık adı)
    def tepesi: T = d.top
    def boyu: Sayı = d.size
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
  }

  implicit class KuyrukMetotları[T](d: Kuyruk[T]) {
    def ekle(öge: T): Kuyruk[T] = d += öge
    def çıkar(): T = d.dequeue()
    def baştanAl(): T = d.dequeue() // çıkar ile aynı (kitapçık adı)
    def başı: T = d.head
    def boyu: Sayı = d.size
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
  }

  implicit class ÖncelikSırasıMetotları[T](d: ÖncelikSırası[T]) {
    def ekle(öge: T): ÖncelikSırası[T] = d += öge
    def çıkar(): T = d.dequeue()
    def başı: T = d.head
    def boyu: Sayı = d.size
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
  }
}
