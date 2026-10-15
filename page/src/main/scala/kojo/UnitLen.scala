package kojo

/**
 * Çizim birimi (masaüstü `core.UnitLen`): `clearWithUL(Cm)` / `silVeÇizimBiriminiKur(santim)`.
 * `pikselOranı`: 1 birim kaç EKRAN pikseli. Masaüstü Dpi'yi ekrandan okuyor; tarayıcıda CSS
 * pikseli 96 dpi sayılıyor (1 inç = 96 px, 1 cm = 96/2.54 px).
 */
sealed abstract class UnitLen(val pikselOranı: Double)
case object Pixel extends UnitLen(1.0)
case object Cm extends UnitLen(96.0 / 2.54)
case object Inch extends UnitLen(96.0)
