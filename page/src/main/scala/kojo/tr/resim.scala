package kojo.tr

/**
 * Resim (Picture) API'sinin Türkçesi.
 *
 * Masaüstündeki `resim.scala` 463 satırın büyük bölümünü GÖRÜNTÜ FİLTRELERİNE
 * ayırıyor (`com.jhlabs.image.LightFilter`, `BufferedImageOp`, gürültü, örgü,
 * ışıklar...). Bunların hiçbiri Scala.js'te yok, o yüzden buraya alınmadı.
 * Alınanlar: resim üretme, dönüşümler, çizim ve çarpışma -- yani çocukların
 * oyun ve çizim için gerçekten kullandığı kısım.
 *
 * `builtins`e ihtiyaç var (Picture fabrikası onun içinde bir iç nesne), o yüzden
 * soyut `kb` üyesi TurkishTurtle tarafından sağlanıyor.
 */
trait ResimYöntemleri extends TemelTürler with RenkYöntemleri with NoktaYöntemleri {
  protected def kb: kojo.syntax.Builtins
  // Picture.image / draw gibi metotlar örtük KojoWorld istiyor; Builtins'in
  // kendi kojoWorld'ü dışarıdan erişilebilir değil, o yüzden ayrıca alıyoruz.
  protected implicit def kd: kojo.KojoWorld

  type Resim = kojo.Picture

  object Resim {
    def dikdörtgen(en: Kesir, boy: Kesir): Resim = kb.Picture.rectangle(en, boy)
    def kare(en: Kesir): Resim = kb.Picture.rectangle(en, en)
    def daire(yarıçap: Kesir): Resim = kb.Picture.circle(yarıçap)
    def elips(xYarıçap: Kesir, yYarıçap: Kesir): Resim = kb.Picture.ellipse(xYarıçap, yYarıçap)
    def dikdörtgenİçiElips(en: Kesir, boy: Kesir): Resim = kb.Picture.ellipseInRect(en, boy)
    def çizgi(en: Kesir, boy: Kesir): Resim = kb.Picture.line(en, boy)
    def yatayÇizgi(n: Kesir): Resim = kb.Picture.hline(n)
    def dikeyÇizgi(n: Kesir): Resim = kb.Picture.vline(n)
    def yazı(içerik: Her, yazıBoyu: Sayı = 15): Resim = kb.Picture.text(içerik, yazıBoyu)
    def imge(adres: Yazı): Resim = kb.Picture.image(adres)
    def imge(adres: Yazı, zarf: Resim): Resim = kb.Picture.image(adres, zarf)
    def yatayBoşluk(boşluk: Kesir): Resim = kb.Picture.hgap(boşluk)
    def dikeyBoşluk(boşluk: Kesir): Resim = kb.Picture.vgap(boşluk)
    def yoldan(işlev: pixiscalajs.PIXI.Graphics => Birim): Resim = kb.Picture.fromPath(işlev)

    def çiz(r: Resim): Birim = r.draw()
    def önyükle(adres: Yazı): Birim = kb.preloadImage(adres)
  }

  // ---- çizim yardımcıları ----
  def çiz(resimler: Resim*): Birim = kb.draw(resimler: _*)
  def çizMerkezde(r: Resim): Birim = kb.drawCentered(r)
  def çizSahne(boya: Renk): Birim = kb.drawStage(boya)
  def çizMerkezdeYazı(mesaj: Yazı, renk: Renk = Renkler.siyah, yazıBoyu: Sayı = 15): Birim =
    kb.drawCenteredMessage(mesaj, renk, yazıBoyu)

  implicit class ResimMetotları(r: Resim) {
    // görünürlük ve çizim
    def çiz(): Birim = r.draw()
    def sil(): Birim = r.erase()
    def göster(): Birim = r.visible()
    def gizle(): Birim = r.invisible()
    def görünürMü: İkil = r.isVisible
    def kopyası: Resim = r.copy
    def öneAl(): Birim = r.moveToFront()
    def arkayaAt(): Birim = r.moveToBack()

    // konum ve yön
    def konum: Nokta = r.position
    def doğrultu: Kesir = r.heading
    def konumuKur(x: Kesir, y: Kesir): Birim = r.setPosition(x, y)
    def açıyaDön(açı: Kesir): Birim = r.setHeading(açı)
    def döndür(açı: Kesir): Birim = r.rotate(açı)
    def döndürMerkezli(açı: Kesir, x: Kesir, y: Kesir): Birim = r.rotateAboutPoint(açı, x, y)
    def taşı(dx: Kesir, dy: Kesir): Birim = r.translate(dx, dy)
    def büyüt(oran: Kesir): Birim = r.scale(oran)
    def büyüklüğünüKur(oran: Kesir): Birim = r.setScale(oran)
    def yansıtX(): Birim = r.flipX()
    def yansıtY(): Birim = r.flipY()
    def saydamlığınıKur(oran: Kesir): Birim = r.setOpacity(oran)
    def kalemRenginiKur(renk: Renk): Birim = r.setPenColor(renk)
    def boyamaRenginiKur(renk: Renk): Birim = r.setFillColor(renk)
    def kalemKalınlığınıKur(k: Kesir): Birim = r.setPenThickness(k)

    // yeni resim döndüren dönüşümler (zincirlenebilir)
    def döndürülmüş(açı: Kesir): Resim = r.withRotation(açı)
    def döndürülmüşMerkezli(açı: Kesir, x: Kesir, y: Kesir): Resim = r.withRotationAround(açı, x, y)
    def taşınmış(x: Kesir, y: Kesir): Resim = r.withTranslation(x, y)
    def büyütülmüş(oran: Kesir): Resim = r.withScaling(oran)
    def boyalı(renk: Renk): Resim = r.withFillColor(renk)
    def kalemRenkli(renk: Renk): Resim = r.withPenColor(renk)
    def kalemKalınlıklı(k: Kesir): Resim = r.withPenThickness(k)
    def saydamlıklı(oran: Kesir): Resim = r.withOpacity(oran)
    def konumlu(x: Kesir, y: Kesir): Resim = r.withPosition(x, y)
    def xYansımalı: Resim = r.withFlippedX
    def yYansımalı: Resim = r.withFlippedY

    // çarpışma ve sınırlar
    def sınırları: Dikdörtgen = r.bounds
    def çarpışıyorMu(öbürü: Resim): İkil = r.collidesWith(öbürü)
  }
}
