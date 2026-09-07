package kojo

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.geom.LineString
import com.vividsolutions.jts.geom.PrecisionModel
import pixiscalajs.PIXI
import pixiscalajs.PIXI.Matrix
import pixiscalajs.PIXI.Point
import pixiscalajs.PIXI.Rectangle

import scala.concurrent.{Future, Promise}
import org.scalajs.dom.html.{Image => DomImage}

object Utils {
  def doublesEqual(d1: Double, d2: Double, tol: Double): Boolean = {
    if (d1 == d2) return true
    else if (math.abs(d1 - d2) < tol) return true
    else return false
  }

  def deg2radians(angle: Double) = angle * math.Pi / 180

  def rad2degrees(angle: Double) = angle * 180 / math.Pi

  lazy val Gf = new GeometryFactory

  lazy val pmodel = new PrecisionModel(14)
  def newCoordinate(x: Double, y: Double) = {
    val coord = new Coordinate(x, y)
    pmodel.makePrecise(coord)
    coord
  }

  def printMatrix(m: pixiscalajs.PIXI.Matrix) {
    println(m.tx, m.ty, m.a, m.d, m.b, m.c)
  }

  def printLineString(geom: Geometry): Unit = {
    println(LineString.asString(geom.asInstanceOf[LineString]))
  }

  def distance(p1: Point, p2: Point) =
    math.sqrt(math.pow(p2.x - p1.x, 2) + math.pow(p2.y - p1.y, 2))

  def transformRectangle(rect: Rectangle, m: Matrix): Rectangle = {
    // assume rectangle stays a rectangle!
    val bottomLeft = Point(rect.x, rect.y)
    val bottomRight = Point(rect.x + rect.width, rect.y)
    val topLeft = Point(rect.x, rect.y + rect.height)
    val topRight = Point(rect.x + rect.width, rect.y + rect.height)
    val newBottomLeft = m.apply(bottomLeft)
    val newBottomRight = m.apply(bottomRight)
    val newTopLeft = m.apply(topLeft)
    val newTopRight = m.apply(topRight)
    val minx = math.min(math.min(math.min(newBottomLeft.x, newTopLeft.x), newTopRight.x), newBottomRight.x)
    val miny = math.min(math.min(math.min(newBottomLeft.y, newTopLeft.y), newTopRight.y), newBottomRight.y)
    val maxx = math.max(math.max(math.max(newBottomLeft.x, newTopLeft.x), newTopRight.x), newBottomRight.x)
    val maxy = math.max(math.max(math.max(newBottomLeft.y, newTopLeft.y), newTopRight.y), newBottomRight.y)
    new Rectangle(minx, miny, maxx - minx, maxy - miny)
  }

  def printRectangle(r: Rectangle): Unit = {
    println(s"Rectangle(${r.x}, ${r.y}, ${r.width}, ${r.height})")
  }

  def image(url: String)(implicit kojoWorld: KojoWorld): SubImage = {
    val ret = Promise[DomImage]()
    def init(loader: PIXI.loaders.Loader, any: Any): Unit = {
      val img = loader.resources(url)
      ret.success(img.data.asInstanceOf[DomImage])
    }
    AssetLoader.addAndLoad(url, url, init)
    SubImage(ret.future, None)
  }

  def notSupported(name: String, reason: String) = throw new UnsupportedOperationException(s"$name - operation not available $reason:\n${toString}")
}

// PIXI 4 ile PIXI 5 arasındaki uyum katmanı.
//
// Facade `js.native` olduğu için sürüm farkları DERLEME zamanında görünmüyor;
// hepsi çalışma anında patlıyor. Onun için farkları tek yerde topluyoruz ve
// `PIXI.VERSION`'a bakarak seçiyoruz: aynı derleme hem v4 hem v5 ile çalışıyor.
// Böylece kütüphane dosyasını sunan depo (kojojs-editor) ile kodu sunan depo
// (kojojs-core) birbirini beklemek zorunda kalmıyor, geri dönüş de yalnız
// kütüphane dosyasını geri koymaktan ibaret oluyor.
//
// Tarayıcıda ÖLÇÜLEREK saptandı (pixi 4.8.9 / 5.3.12); v5'te de aynı çalıştığı
// için burada YER ALMAYAN şeyler: PIXI.loaders.Loader/Resource, PIXI.loader,
// PIXI.interaction (v5 uyumluluk kabukları duruyor), autoDetectRenderer'ın
// seçenek nesnesi biçimi, RenderTexture.create(w, h), renderer.render'ın
// konumlu biçimi, SHAPES sabitleri, Texture.from, setTransform, getBounds.
object PixiUyum {
  import scala.scalajs.js
  import scala.scalajs.js.Dynamic.{global => g}

  /** PIXI 5 (ya da üstü) mü? PIXI.VERSION'ın baş sayısına bakıyoruz. */
  lazy val beşVeÜstü: Boolean = {
    // PIXI hiç yüklenmemiş olabilir (Node altındaki birim testleri böyle koşuyor);
    // o durumda v4 varsayıyoruz -- yani doku dolgusu yok, düz renge düşülür.
    val s =
      if (js.typeOf(js.Dynamic.global.PIXI) == "undefined") "4"
      else g.PIXI.VERSION.asInstanceOf[js.UndefOr[String]].getOrElse("4")
    s.takeWhile(_.isDigit).toIntOption.exists(_ >= 5)
  }

  private def dyn(o: Any): js.Dynamic = o.asInstanceOf[js.Dynamic]

  /**
   * Çizim parçaları: v4'te Graphics'in kendisinde, v5'te geometry'sinde.
   *
   * v5'te ayrıca ÖNCE finishPoly() gerekiyor: moveTo/lineTo ile çizilen yol
   * graphicsData'ya kendiliğinden geçmiyor, `currentPath`te bekliyor. Bunu
   * yapmadan okursak dizi BOŞ gelir ve üstünde dönen boya/kalem/kalınlık
   * döngüleri sessizce hiçbir şeye dokunmaz -- kaplumbağa resimleri v5'te tam
   * bu yüzden boyasız ve varsayılan kalemle çiziliyordu.
   *
   * finishPoly() `currentPath`i null'a çekiyor; sonraki lineTo'yu yoluSürdür
   * kaldığımız noktadan yeniden başlatıyor, yani ikisi birlikte çalışıyor.
   */
  def parçalar(gr: pixiscalajs.PIXI.Graphics): js.Array[js.Dynamic] = {
    val d = dyn(gr)
    val a = if (beşVeÜstü) { d.finishPoly(); d.geometry.graphicsData } else d.graphicsData
    a.asInstanceOf[js.Array[js.Dynamic]]
  }

  /**
   * Geometriyi yeniden kurdurur. v4 iki sayacı artırmakla yetiniyordu;
   * v5'te bunlar Graphics'te değil geometry'de ve invalidate() ile işliyor.
   */
  def tazele(gr: pixiscalajs.PIXI.Graphics): Unit = {
    val d = dyn(gr)
    if (beşVeÜstü) d.geometry.invalidate()
    else {
      d.dirty = d.dirty.asInstanceOf[Double] + 1
      d.clearDirty = d.clearDirty.asInstanceOf[Double] + 1
    }
  }

  /**
   * Boya rengi. v4'te parçanın düz alanları (fillColor/fillAlpha), v5'te bir
   * fillStyle nesnesi -- ve v5'te ayrıca `visible` bayrağı var: şekil
   * beginFill(renk, 0) ile çizilmişse stil görünmez damgalanıyor, yalnız rengi
   * değiştirmek yetmiyor.
   */
  def boyayıKur(gr: pixiscalajs.PIXI.Graphics, renk: Double, saydamlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) {
        gd.fillStyle.color = renk
        gd.fillStyle.alpha = saydamlık
        gd.fillStyle.visible = saydamlık > 0
      }
      else {
        gd.fillColor = renk
        gd.fillAlpha = saydamlık
      }
    }
    tazele(gr)
  }

  /**
   * Boyayı kurar: düz renk ya da doku (gradyan / dokuma).
   *
   * Doku dolgusu yalnız PIXI 5'te var -- fillStyle nesnesi v4'te yok. v4'te
   * DokuBoya zaten kurulurken DüzBoya'ya düşüyor (bkz. Boya), yine de burada
   * yedek renge düşerek ikinci bir güvence bırakıyoruz.
   */
  def boyayıKurBoya(gr: pixiscalajs.PIXI.Graphics, boya: Boya)(tazeleyici: () => Unit): Unit = boya match {
    case DüzBoya(renk) =>
      boyayıKur(gr, renk.toRGBDouble, renk.alpha.get)
    case DokuBoya(doku, matris, yedek) =>
      if (!beşVeÜstü) boyayıKur(gr, yedek.toRGBDouble, yedek.alpha.get)
      else {
        // DİKKAT: fillStyle.matrix, yerel koordinattan doku koordinatına giden
        // eşlemeyi tutuyor, yani bizim tuttuğumuzun TERSİNİ. Graphics.beginTextureFill
        // bu tersi kendisi alıyor; biz fillStyle'ı doğrudan değiştirdiğimiz için
        // burada elle almamız gerek. Alınmazsa gradyan yanlış ölçekte ve yanlış
        // yerde çıkıyor (ölçüldü: 160 birimlik rampa 410 birime yayılıyordu).
        // Doku henüz yüklenmemişse (DokumaBoya bir dosyadan geliyor) PIXI 5'in
        // validateBatching'i HİÇBİR batch kurmuyor: Graphics o kareyi bomboş
        // çiziyor -- düz renkli parçalar ve kalem dahil. Yüklendiğinde kendi
        // başına bir çizim tetiklenmediği için, durağan bir sahnede şekil
        // hiç görünmüyordu. Yüklemeyi dinleyip bir çizim istiyoruz.
        // (geometry.dirty != cacheDirty kaldığından tazele() gerekmiyor.)
        val bt = dyn(doku).baseTexture
        if (!bt.valid.asInstanceOf[Boolean]) bt.once("loaded", () => tazeleyici())
        val ters = dyn(matris).clone().invert()
        parçalar(gr).foreach { gd =>
          gd.fillStyle.texture = doku.asInstanceOf[js.Any]
          gd.fillStyle.matrix = ters.asInstanceOf[js.Any]
          // Doku rengi bozulmasın diye çarpan beyaz ve tam donuk olmalı.
          gd.fillStyle.color = 0xffffff
          gd.fillStyle.alpha = 1.0
          gd.fillStyle.visible = true
        }
        tazele(gr)
      }
  }

  /** Kalem rengi -- boyayıKur'un kalem karşılığı. */
  def kalemiKur(gr: pixiscalajs.PIXI.Graphics, renk: Double, saydamlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) {
        gd.lineStyle.color = renk
        gd.lineStyle.alpha = saydamlık
        gd.lineStyle.visible = true
      }
      else {
        gd.lineColor = renk
        gd.lineAlpha = saydamlık
      }
    }
    tazele(gr)
  }

  /** Kalem kalınlığı. */
  def kalemKalınlığınıKur(gr: pixiscalajs.PIXI.Graphics, kalınlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) gd.lineStyle.width = kalınlık else gd.lineWidth = kalınlık
    }
    tazele(gr)
  }

  /** Doku uv'lerini tazeler (alt-imge kırpmasından sonra). */
  def uvTazele(doku: pixiscalajs.PIXI.Texture): Unit = {
    val d = dyn(doku)
    if (beşVeÜstü) d.updateUvs() else d._updateUvs()
  }

  /**
   * PIXI 4 -> 5'in en sinsi farkı; göç kılavuzunda geçmiyor ve derleme
   * zamanında hiç görünmüyor:
   *   v4 moveTo(x, y) poligonu HEMEN drawShape ile graphicsData'ya koyuyordu;
   *     lineTo o yerleşmiş şeklin noktalarını yerinde büyütüyordu -- yani her
   *     lineTo'dan sonraki çizim izi gösteriyordu.
   *   v5 moveTo(x, y) poligonu `currentPath`te BEKLETİYOR; graphicsData'ya
   *     ancak finishPoly() ile ve YALNIZ points.length > 2 iken geçiyor. Her
   *     çizim finishPoly() çağırıyor ve tam 2 noktalı yolu SİLİYOR
   *     (currentPath.points.length = 0).
   * Kaplumbağa tam bu kalıpta çiziyor (moveTo, çiz, lineTo, çiz, ...), yani
   * v5'te kalem izi hiç çıkmıyordu. Ölçüldü: aynı betikte v4 graphicsData=1,
   * v5 graphicsData=0.
   *
   * Çare: lineTo'dan önce yolun boşaltılmış olup olmadığına bakıp, boşaltılmışsa
   * kaldığımız noktadan yeniden başlatmak. Çizimler arasındaki kesimler yine
   * tek poligonda toplanıyor -- kesim başına ayrı şekil üretmiyoruz.
   */
  def yoluSürdür(gr: pixiscalajs.PIXI.Graphics, sonX: Double, sonY: Double): Unit =
    if (beşVeÜstü) {
      val yol = dyn(gr).currentPath
      val boş = js.isUndefined(yol) || yol == null ||
        yol.points.asInstanceOf[js.Array[Double]].length < 2
      if (boş) gr.moveTo(sonX, sonY)
    }
}
