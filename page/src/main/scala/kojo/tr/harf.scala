package kojo.tr

/**
 * Char'ın Türkçesi.
 *
 * Masaüstündeki `ArraySeq.ofChar` / `ArrayCharSequence` / `SeqCharSequence`
 * takma adları buraya alınmadı: ilki 2.13'e özgü, diğer ikisi çocuklar için
 * pratik değeri olmayan iç türler.
 */
trait HarfYöntemleri extends TemelTürler {
  type Harf = Char

  object Harf {
    def sayıMı(h: Harf): İkil = h.isDigit
    def harfMi(h: Harf): İkil = h.isLetter
    def kutuyaKoy(h: Harf) = Char.box(h)
    def kutudanÇıkar(h: HerGönder) = Char.unbox(h)
    def sayıya(h: Harf) = Char.char2int(h)
    def uzuna(h: Harf) = Char.char2long(h)
    def kesire(h: Harf) = Char.char2double(h)
    def ufakKesire(h: Harf) = Char.char2float(h)
    // masaüstünde ters yazılmış (enUfağı = MaxValue); burada düzeltildi
    val enUfağı = Char.MinValue
    val enİrisi = Char.MaxValue
  }

  implicit class HarfMetotları(h: Harf) {
    def yazıya: Yazı = h.toString
    def büyükHarfe: Harf = h.toUpper
    def küçükHarfe: Harf = h.toLower
    def sayıya: Sayı = h.toInt
    def kesire: Kesir = h.toDouble
    def sayıMı: İkil = h.isDigit
    def harfMi: İkil = h.isLetter
    def boşlukMu: İkil = h.isWhitespace
    def küçükHarfMi: İkil = h.isLower
    def büyükHarfMi: İkil = h.isUpper
  }
}
