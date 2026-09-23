package kojo

import kojo.doodle.Color
import kojo.syntax.Builtins
import pixiscalajs.PIXI

import scala.collection.mutable

object AssetLoader {
  // Pixi.loader DEĞİL: v5'te o ad her okunuşta konsola deprecation uyarısı
  // bastırıyor. Uyum katmanı v5'te PIXI.Loader.shared'ı, v4'te PIXI.loader'ı
  // veriyor; ölçüldü, v5'te ikisi aynı nesne (bkz. PixiUyum).
  private val loader = PixiUyum.paylaşılanYükleyici
  case class QEntry(name: String, url: String, doneFn: (PIXI.loaders.Loader, Any) => Unit)

  val queue = mutable.Queue.empty[QEntry]
  private var loadProgress: LoadProgress = _

  def loading = loadProgress != null && loadProgress.isActive

  def showLoading()(implicit kojoWorld: KojoWorld): Unit = {
    if (loadProgress == null) {
      loadProgress = new LoadProgress
    }
    loadProgress.show()
  }

  def hideLoading(): Unit = {
    loadProgress.hide()
  }

  def addAndLoad(name: String, url: String, doneFn: (PIXI.loaders.Loader, Any) => Unit)(implicit kojoWorld: KojoWorld): Unit = {
    showLoading()

    def checkQ(): Unit = {
      if (queue.nonEmpty) {
        val qe = queue.dequeue()
        addAndLoad(qe.name, qe.url, qe.doneFn)
      }
      else {
        hideLoading()
      }
    }

    if (!loader.loading) {
      val resVal = loader.resources(name)
      if (resVal == ()) {
        loader.add(name, url)
        loader.load { (loader, any) =>
          doneFn(loader, any)
          checkQ()
        }
      }
      else {
        doneFn(loader, resVal)
        checkQ()
      }
    }
    else {
      queue.enqueue(QEntry(name, url, doneFn))
    }
  }
}

class LoadProgress(implicit kw: KojoWorld) {
  var loadingPic: Picture = {
    val pic = new TextPic("Loading...", 30, Color.lightBlue)
    pic.draw()
    pic
  }

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  loadingPic.ready.foreach { _ =>
    val b = loadingPic.bounds
    val bg = new RectanglePic(b.width + 10, b.height + 10)
    val bgColor = Color.rgb(20, 20, 20)
    bg.setPenColor(Color.black)
    bg.setFillColor(bgColor)
    val fg = new TextPic("Loading...", 30, Color.lightSteelBlue)
    val pics = new GPicsCentered(Seq(bg, fg))
    pics.translate(-b.width / 2 - 5, -b.height / 2 - 5)
    loadingPic.erase()
    loadingPic = pics
    pics.draw()
    pics.invisible()
  }

  def show(): Unit = {
    loadingPic.visible()
    loadingPic.moveToFront()
  }

  def hide(): Unit = {
    loadingPic.invisible()
  }

  def isActive = loadingPic.isVisible
}
