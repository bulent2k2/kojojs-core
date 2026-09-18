package kojo

import scala.collection.mutable.ArrayBuffer

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Geometry

import kojo.Utils.newCoordinate
import kojo.doodle.Color

object TurtlePicture {
  var turtle: GlobalTurtleForPicture = _
  var turtle0: Turtle = _

  def apply(fn: Turtle => Unit)(implicit kojoWorld: KojoWorld): TurtlePicture = {
    val tp = new TurtlePicture(fn)
    tp.make()
    tp
  }

  def apply(fn: => Unit)(implicit kojoWorld: KojoWorld): GlobalTurtlePicture = {
    val tp = new GlobalTurtlePicture(fn)
    turtle.globalTurtle = tp.turtle
    tp.make()
    turtle.globalTurtle = turtle0
    tp
  }
}

class TurtlePicture private[kojo] (fn: Turtle => Unit)(implicit val kojoWorld: KojoWorld)
  extends Picture with ReadyPromise {
  val turtle = new Turtle(0, 0, true)
  val picLayer = turtle.turtleLayer
  val tnode = picLayer
  val noColor = Color(0, 0, 0, 0)

  def make(): Unit = {
    turtle.setAnimationDelay(0)
    turtle.setFillColor(noColor)
    invisible()
    fn(turtle)
    turtle.sync { () =>
      makeDone()
      visible()
    }
  }

  // erase() bekleyen dolguyu düşürüyor (sahne dışına boşa üçgenleme olmasın,
  // #68). Ama düşürülen yayın BİLGİ taşıyor: hiç yayınlanmamış bir dolgu öyle
  // KAYBOLUYOR ve resim yeniden çizilince dolgusuz görünüyor. #106 bunu
  // getirdi, #109'un incelemesinde ölçüldü:
  //
  //   çiz -> boşalt -> sil -> çiz -> boşalt : dolgu duruyor  (ilk yayın olmuş)
  //   çiz -> sil -> çiz -> boşalt           : dolgu YOK      (hiç yayın olmadı)
  //
  // İkincisi ulaşılabilir, çünkü draw/erase eşzamansız (ready.foreach) ama
  // mikro-görevler bir sonraki kareden ÖNCE koşuyor: `çiz(r); r.sil(); çiz(r)`
  // diyen düz bir betik tam o sıraya giriyor.
  //
  // O yüzden düşürdüğümüzü hatırlıyoruz ve yeniden çizimde yeniden
  // kirletiyoruz. Koşullu: her çizimde kirletmek, bir kez çizilen resme
  // fazladan bir üçgenleme bindirirdi.
  private var dolguDüşürüldü = false

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  def realDraw(): Unit = {
    // erasePictures() yolu Picture.erase()'ten geçmiyor, yani dolguDüşürüldü'yü
    // kuramıyor -- elinde katman var, çizer yok. O yüzden bilgiyi katmanın
    // üzerinden okuyoruz (#109). addLayer imleri siliyor, o yüzden ÖNCE.
    val katmandanDüştü = PixiUyum.düşenBoyaVarMı(tnode)
    kojoWorld.addLayer(tnode)
    if (dolguDüşürüldü || katmandanDüştü) {
      dolguDüşürüldü = false
      kojoWorld.boyaKirlendi(turtle)
    }
  }

  // GL kaynaklarını burada bırakmıyoruz: removeLayer katmanın ALTINDAKİ bütün
  // Graphics'lerin geometrisini dispose ediyor (bkz. PixiUyum.glKaynaklarınıBırak,
  // sorun #91). Eskiden burada kapalı bir `turtle.turtlePath.destroy()` duruyordu;
  // destroy yanlış araçtı -- resmi yeniden çizilemez hale getirirdi ve yalnız tek
  // bir parçayı kapsıyordu.
  def erase(): Unit = {
    ready.foreach { _ =>
      // Bekleyen dolguyu ÖNCE düşür: picLayer kaplumbağanın kendi katmanı,
      // yani buradan sonra çizer sahnede değil. Düşürülmezse bir sonraki
      // boyalarıBoşalt() onu yine yayınlıyor -- sahnede olmayan bir şeklin
      // çokgeni bir kez daha üçgenleniyor (#68; n büyük ve kesişen
      // şekillerde bu ~95 ms).
      //
      // Çizer başına, küresel değil: ötekilerin bekleyeni durmalı, yoksa
      // başkasının dolgusu sessizce yok olur (bkz. TembelSilmeTest).
      if (kojoWorld.bekleyenBoyayıUnut(turtle)) dolguDüşürüldü = true
      kojoWorld.removeLayer(picLayer)
    }
  }

  def initGeom(): Geometry = {
    val cab = new ArrayBuffer[Coordinate]
    val points = turtle.turtlePathPoints
    if (points.size > 1) {
      points.foreach { pt =>
        cab += newCoordinate(pt._1, pt._2)
      }
    }
    if (cab.size == 1) {
      cab += cab(0)
    }
    import scala.scalajs.js.JSConverters._
    Utils.Gf.createLineString(cab.toJSArray)
  }

  // Kaplumbağanın çizimi ARTIK tek bir Graphics değil: her tamamlanan şekil
  // kendi düğümünü alıyor (sorun #86, şekil başına katmanlama). Biçem
  // dönüştürücüleri bu yüzden PARÇALARIN HEPSİNE uygulanmalı -- tek başına
  // turtle.turtlePath yalnız o anda çizilmekte olan parçayı tutuyor.
  // DOLGU biçemi bütün parçalara: kalem izleri de dolgu taşıyabiliyor
  // (nokta() daireleri, açık boyama). KALEM biçemi yalnız kalem parçalarına:
  // dolgu düğümleri çizgisiz doğuyor ve onlara kalem yazmak üçgenleme
  // dikişini görünür kılıyor (bkz. Turtle.kalemParçaları).
  private def dolguParçaları = turtle.çizimParçaları
  private def kalemParçaları = turtle.kalemParçaları

  def setFillColor(c: Color): Unit = {
    ready.foreach { u =>
      dolguParçaları.foreach(g => PixiUyum.boyayıKur(g, c.toRGBDouble, c.alpha.get))
      kojoWorld.render()
    }
  }

  override def setFillPaint(b: Boya): Unit = {
    ready.foreach { u =>
      dolguParçaları.foreach(g => PixiUyum.boyayıKurBoya(g, b) { () => kojoWorld.render() })
      kojoWorld.render()
    }
  }

  def setPenColor(c: Color): Unit = {
    ready.foreach { u =>
      kalemParçaları.foreach(g => PixiUyum.kalemiKur(g, c.toRGBDouble, c.alpha.get))
      kojoWorld.render()
    }
  }

  def setPenThickness(t: Double): Unit = {
    ready.foreach { u =>
      kalemParçaları.foreach(g => PixiUyum.kalemKalınlığınıKur(g, t))
      kojoWorld.render()
    }
  }

  def copy = TurtlePicture(fn)
}

class GlobalTurtlePicture private[kojo] (fn: => Unit)(implicit kojoWorld: KojoWorld)
  extends TurtlePicture(_ => fn) {

  override def copy = TurtlePicture(fn)
}