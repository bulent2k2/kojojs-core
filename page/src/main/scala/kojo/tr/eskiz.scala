package kojo.tr

/**
 * Processing tarzı eskiz: `kurulum` bir kez, `çizimDöngüsü` her karede çalışır; ikisi de aynı
 * tuvale (TuvalÇizim) çizer. Masaüstü Koco'nun `Eskiz`iyle (lite/i18n/tr/eskiz.scala) AYNI ad ve
 * imza: masaüstünde yazılan eskiz betikleri burada da derlenir.
 *
 *   sınıf İzler yayar Eskiz {
 *     tanım kurulum(tuval: TuvalÇizim) = tuval.artalan(siyah)
 *     tanım çizimDöngüsü(tuval: TuvalÇizim) = { tuval.boya(kırmızı); tuval.elips(rastgele(100), rastgele(100), 5, 5) }
 *   }
 *   çiz(Resim.eskizden(yeni İzler))
 *
 * Neden eskiz: tuval tek bir resim, çizdikleri piksel. Her çizim için yeni bir Resim nesnesi
 * doğmadığından sahne ve PicCache büyümez. Ayrıntı: kojo.SketchPic, oneri-eskiz.md.
 *
 * Masaüstünde bu özellik yapısal türle (setup/drawLoop) İngilizceye bağlanıyor ve bir
 * `EskizUyarlayıcı` gerekiyor; burada Türkçe katman SketchPic'e işlevleri doğrudan veriyor
 * (yansıma yok).
 */
trait Eskiz {
  def kurulum(tuval: kojo.CanvasDraw): Unit
  def çizimDöngüsü(tuval: kojo.CanvasDraw): Unit
}
