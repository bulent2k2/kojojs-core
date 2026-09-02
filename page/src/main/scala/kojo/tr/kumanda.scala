package kojo.tr

/**
 * Kumanda kolu (JoyStick) -- dokunmatik/fare ile oyuncu sürmek için.
 *
 * Masaüstündeki `kumanda.scala` ile neredeyse birebir; tek fark sarmalayıcı
 * açmanın gerekmemesi: burada `Resim` = kojo.Picture ve `Yöney2B` =
 * kojo.Vector2D takma adları olduğu için `.p` / `.v` alanlarına gerek yok.
 */
trait KumandaYöntemleri extends TemelTürler with ResimYöntemleri with Yöney2BYöntemleri with RenkYöntemleri {
  type KumandaKolu = kojo.JoyStick

  /** Verilen yarıçapta bir kumanda kolu yapar. Sonra `çiz()` demeyi unutma. */
  def kumandaKolu(yarıçap: Kesir): KumandaKolu = kb.joystick(yarıçap)

  implicit class KumandaKoluMetotları(k: KumandaKolu) {
    def çiz(): Birim = k.draw()

    /** Kolun o andaki yönü ve gücü. */
    def yöney: Yöney2B = k.currentVector

    def kondur(x: Kesir, y: Kesir): Birim = k.setPosition(x, y)
    def konumuKur(x: Kesir, y: Kesir): Birim = k.setPosition(x, y)

    def oynat(oyuncu: Resim, hız: Kesir = 1.0): Birim = k.movePlayer(oyuncu, hız)
    def oynat(oyuncu: Resim, hız: Kesir, yönKısıtı: Yöney2B): Birim = k.movePlayer(oyuncu, hız, yönKısıtı)
    def oynatSahneİçinde(oyuncu: Resim, hız: Kesir = 1.0): Birim = k.movePlayerWithinStage(oyuncu, hız)
    def oynatSahneİçinde(oyuncu: Resim, hız: Kesir, yönKısıtı: Yöney2B): Birim =
      k.movePlayerWithinStage(oyuncu, hız, yönKısıtı)

    def çevreRenginiKur(r: Renk): Birim = k.setPerimeterColor(r)
    def çevreKalemRenginiKur(r: Renk): Birim = k.setPerimeterPenColor(r)
    def kolRenginiKur(r: Renk): Birim = k.setControlColor(r)
  }
}
