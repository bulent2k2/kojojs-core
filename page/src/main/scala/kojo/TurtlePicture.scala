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

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  def realDraw(): Unit = {
    kojoWorld.addLayer(tnode)
  }

  def erase(): Unit = {
    ready.foreach { _ =>
      kojoWorld.removeLayer(picLayer)
      //      turtle.turtlePath.destroy()
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