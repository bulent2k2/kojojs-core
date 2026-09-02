package kojo.tr

import pixiscalajs.PIXI

/**
 * Nokta ve Dikdörtgen -- geometrik temel türler.
 *
 * Bunlar olmadan Türkçe API'den ÇEVRİLMEMİŞ İngilizce türler sızıyordu:
 * `resim.konum` bir `PIXI.Point`, `resim.sınırları` bir `PIXI.Rectangle`
 * döndürüyordu. Masaüstü Koco'da `type Nokta = Point` ve
 * `type Dikdörtgen = Rectangle` var; burada KojoJS'in PIXI karşılıklarına
 * bağlanıyorlar.
 *
 * PIXI.Point'in x/y'si `var` -- yani nokta DEĞİŞEBİLİR. `resim.konum`'un
 * döndürdüğü nokta resmin canlı konumudur, kopyası değil; saklamak istersen
 * `kopyası` kullan.
 */
trait NoktaYöntemleri extends TemelTürler with BelkiYöntemleri {
  type Nokta = PIXI.Point
  type Dikdörtgen = PIXI.Rectangle

  object Nokta {
    def apply(x: Kesir, y: Kesir): Nokta = new PIXI.Point(x, y)
    def unapply(n: Nokta): Belki[(Kesir, Kesir)] = if (n == null) None else Some((n.x, n.y))
    val sıfır: Nokta = new PIXI.Point(0, 0)
  }

  object Dikdörtgen {
    def apply(x: Kesir, y: Kesir, en: Kesir, boy: Kesir): Dikdörtgen =
      new PIXI.Rectangle(x, y, en, boy)
  }

  implicit class NoktaMetotları(n: Nokta) {
    def kopyası: Nokta = new PIXI.Point(n.x, n.y)
    def uzaklığı(öbürü: Nokta): Kesir =
      math.sqrt(math.pow(öbürü.x - n.x, 2) + math.pow(öbürü.y - n.y, 2))
    def açısı(öbürü: Nokta): Kesir = math.toDegrees(math.atan2(öbürü.y - n.y, öbürü.x - n.x))
    def taşınmış(dx: Kesir, dy: Kesir): Nokta = new PIXI.Point(n.x + dx, n.y + dy)
    def yazıya: Yazı = s"Nokta(${n.x}, ${n.y})"
  }

  implicit class DikdörtgenMetotları(d: Dikdörtgen) {
    def eni: Kesir = d.width
    def boyu: Kesir = d.height
    def solu: Kesir = d.x
    def altı: Kesir = d.y
    def sağı: Kesir = d.x + d.width
    def üstü: Kesir = d.y + d.height
    def merkezi: Nokta = new PIXI.Point(d.x + d.width / 2, d.y + d.height / 2)
    def içeriyorMu(nokta: Nokta): İkil =
      nokta.x >= d.x && nokta.x <= d.x + d.width &&
        nokta.y >= d.y && nokta.y <= d.y + d.height
    def yazıya: Yazı = s"Dikdörtgen(${d.x}, ${d.y}, ${d.width}, ${d.height})"
  }
}
