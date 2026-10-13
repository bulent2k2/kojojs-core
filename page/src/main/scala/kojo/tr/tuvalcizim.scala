package kojo.tr

/**
 * Eskizin çizim yüzeyi: masaüstü `TuvalÇizim` (= lite.CanvasDraw) ile aynı adlar
 * (lite/i18n/tr/tuvalcizim.scala). Gerçeği kojo.CanvasDraw (HTML canvas).
 *
 * Masaüstünden AYRILANLAR (AWT yok): `şekil(java.awt.Shape)` ve `imge(...)` yok;
 * `karışımKipi` `java.awt.Composite` yerine Canvas'ın karışım adını alır
 * (ör. `tuval.karışımKipi("lighter")`).
 */
trait TuvalÇizimYöntemleri extends TemelTürler with RenkYöntemleri with YazıyüzüYöntemleri {
  type TuvalÇizim = kojo.CanvasDraw
  type Eskiz = kojo.tr.Eskiz

  implicit class canvasDrawMethods(tç: TuvalÇizim) {
    def fırça(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydam: Sayı = 255): Birim = tç.stroke(kırmızı, yeşil, mavi, saydam)
    def fırça(r: Renk): Birim = tç.stroke(r)
    def fırçasız(): Birim = tç.noStroke()
    def fırçaAğırlığı(k: Kesir): Birim = tç.strokeWeight(k)
    def boya(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydam: Sayı = 255): Birim = tç.fill(kırmızı, yeşil, mavi, saydam)
    def boya(renk: Renk): Birim = tç.fill(renk)
    def boyasız(): Birim = tç.noFill()
    def artalan(kırmızı: Sayı, yeşil: Sayı, mavi: Sayı, saydam: Sayı = 255): Birim = tç.background(kırmızı, yeşil, mavi, saydam)
    def artalan(renk: Renk): Birim = tç.background(renk)

    def elips(x: Kesir, y: Kesir, en: Kesir, boy: Kesir): Birim = tç.ellipse(x, y, en, boy)
    def yay(merkezX: Kesir, merkezY: Kesir, enYarıçapı: Kesir, boyYarıçapı: Kesir, başlangıçAçısı: Kesir, yayınAçısı: Kesir): Birim =
      tç.arc(merkezX, merkezY, enYarıçapı, boyYarıçapı, başlangıçAçısı, yayınAçısı)
    def nokta(x: Kesir, y: Kesir): Birim = tç.point(x, y)
    def çizgi(x1: Kesir, y1: Kesir, x2: Kesir, y2: Kesir): Birim = tç.line(x1, y1, x2, y2)
    def dikdörtgen(x1: Kesir, y1: Kesir, x2: Kesir, y2: Kesir): Birim = tç.rect(x1, y1, x2, y2)
    def yazıYüzü(yy: Yazıyüzü): Birim = tç.textFont(yy)
    def yazı(yazı: Yazı, x: Kesir, y: Kesir): Birim = tç.text(yazı, x, y)

    // Rastgelelik ve döngü
    def rastgeleTohum(tohum: Uzun): Birim = tç.randomSeed(tohum)
    def döngüyüDurdur(): Birim = tç.noLoop()
    def döngüdeMi: İkil = tç.loop

    // Dönüşümler: götür/döndür/büyüt, Resim'dekilerle aynı adlar.
    // Tuvalde döndürRadyan radyanla, döndürDerece derece ile çalışır (rotate / rotateDegrees).
    def götür(x: Kesir, y: Kesir): Birim = tç.translate(x, y)
    def döndürRadyan(açı: Kesir): Birim = tç.rotate(açı)
    def döndürDerece(derece: Kesir): Birim = tç.rotateDegrees(derece)
    def büyüt(çarpan: Kesir): Birim = tç.scale(çarpan)
    def büyüt(xÇarpanı: Kesir, yÇarpanı: Kesir): Birim = tç.scale(xÇarpanı, yÇarpanı)
    def dönüşümüKaydet(): Birim = tç.pushMatrix()
    def dönüşümüGeriYükle(): Birim = tç.popMatrix()

    // Fırça ucu ve birleşimi, karışım kipi
    def fırçaUcu(uç: Sayı): Birim = tç.strokeCap(uç)
    def fırçaBirleşimi(birleşim: Sayı): Birim = tç.strokeJoin(birleşim)
    def yuvarlakUç: Sayı = tç.ROUND        // fırçaUcu(tuval.yuvarlakUç)
    def projeUç: Sayı = tç.PROJECT
    def kareUç: Sayı = tç.SQUARE
    def sivriBirleşim: Sayı = tç.MITER     // fırçaBirleşimi(tuval.sivriBirleşim)
    def eğikBirleşim: Sayı = tç.BEVEL
    /** Masaüstünde `java.awt.Composite`; burada Canvas karışım adı: "source-over", "lighter", "multiply", ... */
    def karışımKipi(kip: Yazı): Birim = tç.blendMode(kip)

    // Köşe noktalarıyla şekil çizmek
    def şekilBaşla(): Birim = tç.beginShape()
    def şekilBitir(): Birim = tç.endShape()
    def köşe(x: Kesir, y: Kesir): Birim = tç.vertex(x, y)
    def ikinciDereceKöşe(mx: Kesir, my: Kesir, x2: Kesir, y2: Kesir): Birim = tç.quadraticVertex(mx, my, x2, y2)
    def bezierKöşesi(mx1: Kesir, my1: Kesir, mx2: Kesir, my2: Kesir, x2: Kesir, y2: Kesir): Birim =
      tç.bezierVertex(mx1, my1, mx2, my2, x2, y2)
    def eğriKöşesi(x: Kesir, y: Kesir): Birim = tç.curveVertex(x, y)
    def yayKöşesi(x: Kesir, y: Kesir, açı: Kesir): Birim = tç.arcVertex(x, y, açı)
    def açısalKöşe(boyu: Kesir, açısı: Kesir): Birim = tç.vertexRt(boyu, açısı)
    def açısalEğriKöşesi(boyu: Kesir, açısı: Kesir): Birim = tç.curveVertexRt(boyu, açısı)
  }
}
