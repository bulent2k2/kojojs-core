package kojo.tr

import scala.scalajs.js

/**
 * Masaüstü Koco'nun `BuAn()` yapısı (kojo: lite/i18n/tr/buan.scala).
 *
 * Masaüstündeki gerçekleştirim `java.util.Calendar` (Türkçesi `Takvim`)
 * üzerine kurulu; tarayıcıda o sınıf yok, bu yüzden burada JavaScript'in
 * `Date` nesnesiyle yazıldı. Alan adları ve anlamları masaüstüyle aynı.
 *
 * `Takvim`, `Tarih` ve `SaatDilimi` türlerinin kendileri hâlâ yok -- onlar
 * Java kütüphanesi ve ayrı bir iş.
 */
trait BuAnYöntemleri extends TemelTürler {
  case class BuAn() {
    private val d = new js.Date()
    val saniye: Sayı = d.getSeconds().toInt
    val dakika: Sayı = d.getMinutes().toInt
    val saat: Sayı = d.getHours().toInt
    val gün: Sayı = d.getDate().toInt
    /** Ay 1..12 (JavaScript 0'dan sayar, masaüstündeki gibi 1'e çekiyoruz). */
    val ay: Sayı = d.getMonth().toInt + 1
    val yıl: Sayı = d.getFullYear().toInt
    /** Tarayıcının yerel ayarına göre okunur biçim. */
    def İngilizce: Yazı = d.toString()
    def hepsi: Yazı = f"$yıl-$ay%02d-$gün%02d $saat%02d:$dakika%02d:$saniye%02d"
    override def toString: Yazı = hepsi
  }
}
