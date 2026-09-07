package kojo

import kojo.doodle.Color
import pixiscalajs.PIXI.Graphics

trait VectorGraphicsPic extends Picture with ReadyPromise {
  def makePic(graphics: Graphics)
  val path = new Graphics()
  val noColor = Color(0, 0, 0)
  path.lineStyle(2, Color.red.toRGBDouble, 1.0)
  path.beginFill(noColor.toRGBDouble, 0.0)
  makePic(path)
  val tnode = path
  makeDone()

  def realDraw(): Unit = {
    kojoWorld.addLayer(tnode)
  }

  def erase(): Unit = {
    kojoWorld.removeLayer(tnode)
  }

  override def setFillColor(c: Color): Unit = {
    PixiUyum.boyayıKur(path, c.toRGBDouble, c.alpha.get)
    kojoWorld.noteMutation(tnode)
    kojoWorld.render()
  }

  override def setFillPaint(b: Boya): Unit = {
    PixiUyum.boyayıKurBoya(path, b) { () =>
      kojoWorld.noteMutation(tnode); kojoWorld.render()
    }
    kojoWorld.noteMutation(tnode)
    kojoWorld.render()
  }

  override def setPenColor(c: Color): Unit = {
    PixiUyum.kalemiKur(path, c.toRGBDouble, c.alpha.get)
    kojoWorld.noteMutation(tnode)
    kojoWorld.render()
  }

  override def setPenThickness(t: Double): Unit = {
    PixiUyum.kalemKalınlığınıKur(path, t)
    kojoWorld.noteMutation(tnode)
    kojoWorld.render()
  }
}
