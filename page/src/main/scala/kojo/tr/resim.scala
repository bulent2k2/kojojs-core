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
trait ResimYöntemleri extends TemelTürler with RenkYöntemleri with NoktaYöntemleri with Yöney2BYöntemleri {
  protected def kb: kojo.syntax.Builtins
  // Picture.image / draw gibi metotlar örtük KojoWorld istiyor; Builtins'in
  // kendi kojoWorld'ü dışarıdan erişilebilir değil, o yüzden ayrıca alıyoruz.
  protected implicit def kd: kojo.KojoWorld

  type Resim = kojo.Picture

  object Resim {
    /**
     * Blok biçimi: kaplumbağa komutlarıyla resim yapar --
     * `Resim { yinele(4) { ileri(60); sağ() } }`
     *
     * İngilizce `Picture { ... }` ile aynı şey. Bu OLMADAN kullanıcı Türkçe
     * yazarken İngilizce `Picture` yazmak zorunda kalıyordu.
     *
     * Çalışması TurkishTurtle'ın turtle0'a değil GlobalTurtleForPicture'a
     * bağlanmasına dayanıyor: blok içindeki Türkçe komutların resme yönlenmesi
     * için TurtlePicture globalTurtle'ı takas ediyor.
     */
    def apply(komutlar: => Birim): Resim = kb.Picture(komutlar)

    def dikdörtgen(en: Kesir, boy: Kesir): Resim = kb.Picture.rectangle(en, boy)
    def kare(en: Kesir): Resim = kb.Picture.rectangle(en, en)
    def daire(yarıçap: Kesir): Resim = kb.Picture.circle(yarıçap)
    def elips(xYarıçap: Kesir, yYarıçap: Kesir): Resim = kb.Picture.ellipse(xYarıçap, yYarıçap)
    def dikdörtgenİçiElips(en: Kesir, boy: Kesir): Resim = kb.Picture.ellipseInRect(en, boy)
    def çizgi(en: Kesir, boy: Kesir): Resim = kb.Picture.line(en, boy)
    def yatayÇizgi(n: Kesir): Resim = kb.Picture.hline(n)
    def dikeyÇizgi(n: Kesir): Resim = kb.Picture.vline(n)
    def yazı(içerik: Her, yazıBoyu: Sayı = 15): Resim = kb.Picture.text(içerik, yazıBoyu)
    def renkliYazı(içerik: Her, boy: Sayı, renk: Renk): Resim = kb.Picture.textu(içerik, boy, renk)
    def imge(adres: Yazı): Resim = kb.Picture.image(adres)
    def imge(adres: Yazı, zarf: Resim): Resim = kb.Picture.image(adres, zarf)
    def yatayBoşluk(boşluk: Kesir): Resim = kb.Picture.hgap(boşluk)
    def dikeyBoşluk(boşluk: Kesir): Resim = kb.Picture.vgap(boşluk)
    def yoldan(işlev: pixiscalajs.PIXI.Graphics => Birim): Resim = kb.Picture.fromPath(işlev)

    def çiz(r: Resim): Birim = r.draw()
    def önyükle(adres: Yazı): Birim = kb.preloadImage(adres)
    // Sözlük adları (picCol/picRow/picStack): resimSütunu/Satırı/Yığını ile aynı.
    def diziDikey(resimler: Resim*): Resim = kb.picCol(resimler: _*)
    def diziYatay(resimler: Resim*): Resim = kb.picRow(resimler: _*)
    def dizi(resimler: Resim*): Resim = kb.picStack(resimler: _*)
    def diziDikeyDüzenli(resimler: Resim*): Resim = kb.picColCentered(resimler: _*)
    def diziYatayDüzenli(resimler: Resim*): Resim = kb.picRowCentered(resimler: _*)
    def diziDüzenli(resimler: Resim*): Resim = kb.picStackCentered(resimler: _*)
  }

  // ---- çizim yardımcıları ----
  def çiz(resimler: Resim*): Birim = kb.draw(resimler: _*)
  def çizMerkezde(r: Resim): Birim = kb.drawCentered(r)
  def çizSahne(boya: Renk): Birim = kb.drawStage(boya)
  // zoomXY: tuvali x ve y'de ölçekle ve (mx,my) merkeze kaydır. Birim çember
  // gibi örnekler ekranı telefona sığdırmak için kullanıyor.
  def tuvaliYakınlaştır(xÇarpan: Kesir, yÇarpan: Kesir, mx: Kesir, my: Kesir): Birim =
    kb.zoomXY(xÇarpan, yÇarpan, mx, my)

  // ---- birleştirilebilir dönüşümler (serbest işlev) ----
  // İngilizce trans/rot/penColor... karşılığı. `*` ile zincirlenir, `->` ile
  // resme uygulanır: boyaRengi(mavi) * kalemRengi(siyah) -> Resim.daire(30)
  def öteleme(x: Kesir, y: Kesir): Dönüştürücü = kb.trans(x, y)
  def döndürme(açı: Kesir): Dönüştürücü = kb.rot(açı)
  def büyütme(k: Kesir): Dönüştürücü = kb.scale(k)
  def kalemRengi(renk: Renk): Dönüştürücü = kb.penColor(renk)
  def boyaRengi(renk: Renk): Dönüştürücü = kb.fillColor(renk)
  def kalemKalınlığı(k: Kesir): Dönüştürücü = kb.penThickness(k)
  // Sözlük alias'ları (aynı işlevler): götür=öteleme(trans), yaklaşXY=tuvaliYakınlaştır(zoomXY)
  def götür(x: Kesir, y: Kesir): Dönüştürücü = kb.trans(x, y)
  def yaklaşXY(xÇarpan: Kesir, yÇarpan: Kesir, mx: Kesir, my: Kesir): Birim = kb.zoomXY(xÇarpan, yÇarpan, mx, my)

  // ---- oyun / tuval (sözlük adları) ----
  def rastgeleDiziden[T](dizi: collection.Seq[T]): T = kb.randomFrom(dizi)   // randomFrom
  def sırayaSok(saniye: Kesir)(kod: => Birim): Birim = kb.schedule(saniye)(kod) // schedule
  def yaklaşmayaİzinVerme(): Birim = kb.disablePanAndZoom()                  // disablePanAndZoom
  def tümEkran(): Birim = kb.toggleFullScreenCanvas()                        // toggleFullScreenCanvas
  def oyunSüresiniGöster(sınırSn: Sayı, bitişİletisi: Yazı, renk: Renk = Renkler.siyah, yazıBoyu: Sayı = 15): Birim =
    kb.showGameTime(sınırSn, bitişİletisi, renk, yazıBoyu)                   // showGameTime
  def ekranTazelemeHızınıGöster(renk: Renk = Renkler.siyah, yazıBoyu: Sayı = 15): Birim =
    kb.showFps(renk, yazıBoyu, "çerçeve/saniye: ")                          // showFps (çerçeve/saniye)
  def ekranTazelemeHızınıKur(saniyedeKaçKere: Sayı): Birim = kb.setRefreshRate(saniyedeKaçKere) // setRefreshRate
  def ada(ton: Kesir, doygunluk: Kesir, açıklık: Kesir): Renk = kojo.doodle.Color.hsl(ton, doygunluk, açıklık) // cm.hsl
  def resimleriSil(): Birim = kb.erasePictures() // erasePictures
  def kur(işlev: => Birim): Birim = kb.setup(işlev) // setup

  // ---- resimleri diz (satır / sütun / yığın) ----
  def resimSatırı(resimler: Resim*): Resim = kb.picRow(resimler: _*)
  def resimSütunu(resimler: Resim*): Resim = kb.picCol(resimler: _*)
  def resimYığını(resimler: Resim*): Resim = kb.picStack(resimler: _*)
  def resimSatırıOrtalı(resimler: Resim*): Resim = kb.picRowCentered(resimler: _*)
  def resimSütunuOrtalı(resimler: Resim*): Resim = kb.picColCentered(resimler: _*)
  def resimYığınıOrtalı(resimler: Resim*): Resim = kb.picStackCentered(resimler: _*)
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
    /**
     * Resmi DÜNYA koordinatlarında kaydırır -- konuma dx,dy ekler, başka
     * hiçbir şeye bakmaz. Hareket eden nesneler (oyunlar) için doğru olan bu.
     */
    def kaydır(dx: Kesir, dy: Kesir): Birim = r.offset(dx, dy)
    def kaydır(yöney: Yöney2B): Birim = r.offset(yöney.x, yöney.y)

    /**
     * `kaydır` ile AYNI (ikisi de offset).
     *
     * Neden `translate` değil: `translate` resmin KENDİ çerçevesinde taşıyor --
     * resim döndürülmüşse "sağa 5" ekranda eğik çıkıyor. Çocuğun `taşı`dan
     * beklediği dünya çerçevesinde hareket, o da `offset`.
     *
     * DÜZELTME (2026-09-02): burada önce "translate tekrarlı canlandırmada
     * bozuk" yazıyordu. Yanlıştı. 05-sekme-oyunu'ndaki donma translate'ten
     * değil, `sahnedenSek`in sahne kenarları kurulmadan çağrılıp TypeError
     * atmasından geliyordu; yan yana ölçümde translate ile offset aynı
     * hareketi verdi. Gerçek nedeni TurkishTurtle.sahneKurulduMu anlatıyor.
     *
     * Gerçekten yerel çerçevede taşıma gerekirse `resim.translate(...)`
     * hâlâ erişilebilir.
     */
    def taşı(dx: Kesir, dy: Kesir): Birim = r.offset(dx, dy)
    def taşı(yöney: Yöney2B): Birim = r.offset(yöney.x, yöney.y)
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
    /**
     * DİKKAT: `taşı` ile AYNI ÇERÇEVEDE DEĞİL. `taşı`/`kaydır` dünya
     * koordinatlarında hareket ettiriyor (offset), `taşınmış` ise resmin kendi
     * çerçevesinde (translate). Döndürülmemiş bir resimde ikisi aynı; 45 derece
     * döndürülmüş bir resimde `taşı(10, 0)` ekranda sağa, `taşınmış(10, 0)`
     * çapraza gider.
     *
     * `taşınmış` tek seferlik ve zincirlenebilir olduğu için yerel çerçeve
     * burada genelde istenen şey (resmi kendi yönünde kaydırmak).
     */
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
    def fareyeTıklayınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseClick((x, y) => işlev(x, y)) // onMouseClick
    def fareyleSürükleyince(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseDrag((x, y) => işlev(x, y)) // onMouseDrag
    def fareyiSürükleyince(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseDrag((x, y) => işlev(x, y)) // sözlük adı (fareyle ile aynı)
    def fareBasılınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMousePress((x, y) => işlev(x, y)) // onMousePress
  }
}
