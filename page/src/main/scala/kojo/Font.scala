package kojo

/**
 * Yazı yüzü: masaüstü Kojo'nun `Font(ad, boy)` ve Koco'nun `Yazıyüzü`'sünün TARAYICI karşılığı.
 * İngilizce `Font(ad, boy)` ve Türkçe `yazıyüzü(ad, boy[, biçem])` AYNI türü üretir
 * (Türkçe `Yazıyüzü` bunun takma adı), böylece `Picture.text(s, Font(...), renk)` ile
 * `Resim.yazı(s, yazıyüzü(...), renk)` birbirinin yazı yüzünü kabul eder.
 *
 * AWT yok: yazı yüzü PIXI metin stiline (font-family + font-size) eşlenir. `style` masaüstündeki
 * java.awt.Font biçem bit maskesi (0 düz, 1 kalın, 2 eğik) ama TarayıcıDA ÇİZİMDE KULLANILMIYOR:
 * kalın/eğik yazı şimdilik düz çizilir (Türkçe `biçem` de öyleydi, davranış aynı kaldı).
 */
final case class Font(name: String, size: Int, style: Int = 0)
