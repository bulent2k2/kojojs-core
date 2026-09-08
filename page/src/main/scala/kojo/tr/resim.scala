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
trait ResimYöntemleri extends TemelTürler with RenkYöntemleri with NoktaYöntemleri with Yöney2BYöntemleri
    with YazıyüzüYöntemleri {
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
    // masaüstü: Resim.noktadan { gn => gn.başla(); gn.nokta(x, y); ...; gn.bitir() } (VertexShape)
    def noktadan(işlev: GeoNokta => Birim): Resim = kb.Picture.fromPath(g => işlev(new GeoNokta(g)))
    // masaüstü: Resim.yazı(içerik, yazıyüzü[, renk]) -- Yazıyüzü ailesi PIXI metin stiline
    def yazı(içerik: Her, yy: Yazıyüzü): Resim = new kojo.TextPic(içerik, yy.boy, Renkler.siyah, yy.ad)
    // masaüstü Picture.arc: kaplumbağa yayı (başlangıç merkezde, kuzeye bakar)
    def yay(yarıçap: Kesir, açı: Kesir): Resim = kb.PictureT(t => t.arc(yarıçap, açı))
    def yazı(içerik: Her, yy: Yazıyüzü, renk: Renk): Resim = new kojo.TextPic(içerik, yy.boy, renk, yy.ad)

    def çiz(r: Resim): Birim = r.draw()
    def önyükle(adres: Yazı): Birim = kb.preloadImage(adres)
    // Sözlük adları (picCol/picRow/picStack): resimSütunu/Satırı/Yığını ile aynı.
    def diziDikey(resimler: Resim*): Resim = kb.picCol(resimler: _*)
    def diziYatay(resimler: Resim*): Resim = kb.picRow(resimler: _*)
    def dizi(resimler: Resim*): Resim = kb.picStack(resimler: _*)
    // masaüstü takma adları (Devre 1) -- sağda ikojo/İngilizce karşılığı
    def düz(en: Kesir, boy: Kesir): Resim = kb.Picture.line(en, boy)
    def köşegen(en: Kesir, boy: Kesir): Resim = kb.Picture.line(en, boy)
    def yatay(boy: Kesir): Resim = kb.Picture.hline(boy)
    def dikey(boy: Kesir): Resim = kb.Picture.vline(boy)
    def yazıRenkli(içerik: Her, yazıBoyu: Sayı, renk: Renk): Resim = kb.Picture.textu(içerik, yazıBoyu, renk)
    def satır(r: => Resim, kaçTane: Sayı): Resim = kb.picRow(Seq.fill(kaçTane)(r): _*) // picture.row
    def sütun(r: => Resim, kaçTane: Sayı): Resim = kb.picCol(Seq.fill(kaçTane)(r): _*) // picture.col
    def küme(rd: Resim*): Resim = kb.picBatch(rd: _*) // picBatch
    def küme(rd: collection.Seq[Resim]): Resim = kb.picBatch(rd.toSeq: _*)
    def dizi(rd: collection.Seq[Resim]): Resim = kb.picStack(rd.toSeq: _*)
    def diziDikey(rd: collection.Seq[Resim]): Resim = kb.picCol(rd.toSeq: _*)
    def diziYatay(rd: collection.Seq[Resim]): Resim = kb.picRow(rd.toSeq: _*)
    def diziDüzenli(rd: collection.Seq[Resim]): Resim = kb.picStackCentered(rd.toSeq: _*)
    def diziDikeyDüzenli(rd: collection.Seq[Resim]): Resim = kb.picColCentered(rd.toSeq: _*)
    def diziYatayDüzenli(rd: collection.Seq[Resim]): Resim = kb.picRowCentered(rd.toSeq: _*)
    def sil(): Birim = kb.erasePictures()
    // sahne kenarları: çizSahne(...) çağrılmadan null (bkz. TurkishTurtle.sahneKurulduMu)
    def tuvalSınırları: Resim = sahne("Resim.tuvalSınırları", kb.stageBorder)
    def tuvalinSınırları: Resim = tuvalSınırları
    def tuval: Resim = tuvalSınırları
    def tuvalinSolu: Resim = sahne("Resim.tuvalinSolu", kb.stageLeft)
    def tuvalinSağı: Resim = sahne("Resim.tuvalinSağı", kb.stageRight)
    def tuvalinTavanı: Resim = sahne("Resim.tuvalinTavanı", kb.stageTop)
    def tuvalinTabanı: Resim = sahne("Resim.tuvalinTabanı", kb.stageBot)
    def tuvalBölgesi: Resim = sahne("Resim.tuvalBölgesi", kb.stageArea)
    private def sahne(ad: Yazı, r: Resim): Resim =
      if (r == null)
        throw new ÇalışmaSırasıKuralDışı(s"$ad için önce sahneyi çizmelisin: çizSahne(siyah).")
      else r
    def diziDikeyDüzenli(resimler: Resim*): Resim = kb.picColCentered(resimler: _*)
    def diziYatayDüzenli(resimler: Resim*): Resim = kb.picRowCentered(resimler: _*)
    def diziDüzenli(resimler: Resim*): Resim = kb.picStackCentered(resimler: _*)
  }

  // ---- çizim yardımcıları ----
  def çiz(resimler: Resim*): Birim = kb.draw(resimler: _*)
  def çiz(resimler: collection.Seq[Resim]): Birim = kb.draw(resimler) // masaüstü çiz(Diz[Resim]); çiz(yöney.işle(f))
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
  /** Gradyan ya da dokuma boyasıyla doldurur (Renk.doğrusalDeğişim, DokumaBoya, ...). */
  def boyaRengi(boya: Boya): Dönüştürücü = kb.fillPaint(boya)
  def kalemKalınlığı(k: Kesir): Dönüştürücü = kb.penThickness(k)
  def kalemBoyu(k: Kesir): Dönüştürücü = kb.penThickness(k) // masaüstü adı (resim.scala KalemBoyuBD)
  // masaüstünün bağımsız dönüştürücü adları (trInit: döndür/büyüt/götür = *BD)
  def döndür(açı: Kesir): Dönüştürücü = kb.rot(açı)
  def büyüt(oran: Kesir): Dönüştürücü = kb.scale(oran)
  def büyüt(xOranı: Kesir, yOranı: Kesir): Dönüştürücü = kb.scaleXY_experimental(xOranı, yOranı)
  def götür(n: Nokta): Dönüştürücü = kb.trans(n.x, n.y)
  def götür(yy: Yöney2B): Dönüştürücü = kb.trans(yy.x, yy.y)
  // masaüstünde saydamlığı ÇARPAR (opacMod); burada kurar -- tek katman için aynı sonuç
  def saydamlık(oran: Kesir): Dönüştürücü = kb.postDrawTransform(_.setOpacity(oran))

  /**
   * Masaüstü `GeoYol` = java.awt.geom.GeneralPath; burada PIXI.Graphics. Türkçe yol
   * komutları örtük sınıfla geliyor (masaüstündeki GeoYolYöntemleri gibi), böylece
   * `Resim.yoldan { yol => yol.kondur(0, 0); yol.doğruÇiz(50, 50) }` aynen çalışır.
   */
  type GeoYol = pixiscalajs.PIXI.Graphics
  implicit class GeoYolYöntemleri(yol: GeoYol) {
    def kondur(x: Kesir, y: Kesir): Birim = yol.moveTo(x, y)
    def doğruÇiz(x: Kesir, y: Kesir): Birim = yol.lineTo(x, y)
    def eğriÇiz(x: Kesir, y: Kesir, araX: Kesir, araY: Kesir): Birim = yol.quadraticCurveTo(araX, araY, x, y)
    def başaDön(): Birim = yol.asInstanceOf[scala.scalajs.js.Dynamic].closePath() // facade'de yok, PIXI'de var
  }

  /** Masaüstü `GeoNokta` = net.kogics.kojo.core.VertexShape (başla / nokta / bitir). */
  class GeoNokta(g: pixiscalajs.PIXI.Graphics) {
    private var ilk = true
    def başla(): Birim = { ilk = true }
    def nokta(x: Kesir, y: Kesir): Birim = {
      if (ilk) { g.moveTo(x, y); ilk = false } else g.lineTo(x, y)
    }
    def açısalNokta(boyu: Kesir, açısı: Kesir): Birim =
      nokta(boyu * math.cos(açısı.toRadians), boyu * math.sin(açısı.toRadians))
    def bitir(): Birim = {}
  }
  // Sözlük alias'ları (aynı işlevler): götür=öteleme(trans), yaklaşXY=tuvaliYakınlaştır(zoomXY)
  def götür(x: Kesir, y: Kesir): Dönüştürücü = kb.trans(x, y)
  def yaklaşXY(xÇarpan: Kesir, yÇarpan: Kesir, mx: Kesir, my: Kesir): Birim = kb.zoomXY(xÇarpan, yÇarpan, mx, my)
  /** Yakınlaştırma ve kaydırmayı başlangıç durumuna döndürür. */
  def yaklaşmayıSil(): Birim = kb.resetView()
  /** Tuvali dünya birimiyle kaydırır (masaüstü tuvaliKaydır). */
  def tuvaliKaydır(x: Kesir, y: Kesir): Birim = kb.scroll(x, y)
  /** Tuvali verilen açı kadar döndürür (masaüstü tuvaliDöndür). */
  def tuvaliDöndür(açı: Kesir): Birim = kb.viewRotate(açı)

  // ---- oyun / tuval (sözlük adları) ----
  def rastgeleDiziden[T](dizi: collection.Seq[T]): T = kb.randomFrom(dizi)   // randomFrom
  /**
   * Ağırlıklı seçim: `ağırlıklar` dizinin her ögesinin seçilme payı
   * (toplamları 1 olmak zorunda değil; değilse oranlanır).
   * Masaüstü Koco'daki `rastgeleDiziden(dizi, ağırlıklar)` ile aynı imza.
   */
  def rastgeleDiziden[T](dizi: collection.Seq[T], ağırlıklar: collection.Seq[Kesir]): T =
    kb.randomFrom(dizi, ağırlıklar)
  def sırayaSok(saniye: Kesir)(kod: => Birim): Birim = kb.schedule(saniye)(kod) // schedule
  def yaklaşmayaİzinVerme(): Birim = kb.disablePanAndZoom()                  // disablePanAndZoom
  def görünümüSıfırla(): Birim = kb.resetView()                              // resetView (merkez + zoom 1)
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
    // masaüstü Resim yöntemleri (sınıf içi): götür = translate, kondur = setPosition
    def konumuKur(n: Nokta): Birim = r.setPosition(n.x, n.y)
    def kondur(x: Kesir, y: Kesir): Birim = r.setPosition(x, y)
    def kondur(n: Nokta): Birim = r.setPosition(n.x, n.y)
    def götür(x: Kesir, y: Kesir): Birim = r.translate(x, y)
    def götür(n: Nokta): Birim = r.translate(n.x, n.y)
    def götür(yy: Yöney2B): Birim = r.translate(yy.x, yy.y)
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
    /** Gradyan ya da dokuma boyası (resim üstünde). */
    def boyamaRenginiKur(boya: Boya): Birim = r.setFillPaint(boya)
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
    // masaüstü takma adları (Devre 1; kojo lite/i18n/tr/resim.scala)
    def fareyeBasınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMousePress((x, y) => işlev(x, y))
    def fareyiBırakınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseRelease((x, y) => işlev(x, y))
    def fareGirince(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseEnter((x, y) => işlev(x, y))
    def fareÇıkınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseExit((x, y) => işlev(x, y))
    def fareyeTıklıyınca(işlev: (Kesir, Kesir) => Birim): Birim = r.onMouseClick((x, y) => işlev(x, y)) // masaüstündeki yazım
    def saydamlığıKur(oran: Kesir): Birim = r.setOpacity(oran)
    def saydamlık: Kesir = r.tnode.alpha
    def ardaAl(): Birim = r.moveToBack()
    def girdiyiAktar(öbürü: Resim): Birim = r.forwardInputTo(öbürü)
    def sonrakiniGöster(ara: Uzun = 100): Birim = r.showNext(ara)
    def çarpıştı(öbürü: Resim): İkil = r.collidesWith(öbürü)
    def çarptıMı(öbürü: Resim): İkil = r.collidesWith(öbürü)
    def çarpışma(başkaları: Dizi[Resim]): Option[Resim] = r.collision(başkaları) // Belki[Resim]
    def çarpışmalar(başkaları: Set[Resim]): Set[Resim] = r.collisions(başkaları) // Küme[Resim]
    def uzaklık(öbürü: Resim): Kesir = r.distanceTo(öbürü)
    def çizili: İkil = r.made // isDrawn
    def büyütmeOranı: (Kesir, Kesir) = (r.tnode.scale.x, r.tnode.scale.y) // scaleFactor
    // masaüstünde bu dört ad resmi yerinde değiştirir; burada dönüşümlü kopya döner
    // (aynı zincirleme kullanım: `resim.veBoya(kırmızı).veKondur(10, 10)`)
    def veBoya(renk: Renk): Resim = r.withFillColor(renk)
    def veKalemRengiyle(renk: Renk): Resim = r.withPenColor(renk)
    def veKalemKalınlığıyla(boy: Kesir): Resim = r.withPenThickness(boy)
    def veKondur(x: Kesir, y: Kesir): Resim = r.withPosition(x, y)
    def veBüyüt(oran: Kesir): Resim = r.withScaling(oran)
    def veGötür(x: Kesir, y: Kesir): Resim = r.withTranslation(x, y)
    def veÇiz(): Birim = r.draw()
    // Devre 6: küçük kapanışlar
    def görünür: İkil = r.isVisible // masaüstü adı (görünürMü ile aynı)
    /**
     * Yazı resminin içeriğini değiştirir (skor, sayaç...); masaüstü Resim.güncelle.
     *
     * `götür(10, 20) -> Resim.yazı("0")` gibi bir dönüşüm zinciri TextPic değil,
     * onu saran bir PicTransformer döner; bu yüzden sarmalları soyup asıl yazı
     * resmini arıyoruz.
     */
    def güncelle(yeniVeri: Her): Birim = yazıResmi(r) match {
      case Some(t) => t.update(yeniVeri)
      case None => throw new ÇalışmaSırasıKuralDışı(
        "güncelle yalnız yazı resimlerinde çalışır (Resim.yazı / Resim.yazıRenkli ile yapılanlarda)")
    }
    private def yazıResmi(p: Resim): Option[kojo.TextPic] = p match {
      case t: kojo.TextPic        => Some(t)
      case d: kojo.PicTransformer => yazıResmi(d.tpic)
      case _                      => None
    }
    // Devre 2: küçük özellikler
    def hızınıDönüştür(yy: Yöney2B): Yöney2B = yy.rotate(r.heading) // transv: yöneyi resmin yönüne çevir
    def tepkiVer(işlev: Resim => Birim): Birim = kb.animate(işlev(r)) // react: her karede
    def canlan(işlev: Resim => Birim): Birim = tepkiVer(işlev)
  }
}
