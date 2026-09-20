package kojo

import com.vividsolutions.jts.geom.Geometry
import kojo.PicCache.freshPics
import kojo.doodle.Color
import pixiscalajs.PIXI

import scala.concurrent.Future

abstract class BasePicSequence(val pics: Seq[Picture]) extends Picture with ReadyPromise {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val tnode = new PIXI.Container()

  lazy val childrenReady: Future[Unit] = {
    val futures = pics map (_.ready)
    futures.reduce { (f1, f2) => for (_ <- f1; _ <- f2) yield () }
  }

  // Note - pic sequences get ready only after draw is called on them
  def layoutChildren(): Unit
  def layout(): Unit = {
    layoutChildren()
    // YALNIZ makeDone korunuyor, layoutChildren DEĞİL. `çiz(g); ...; çiz(g)` kalıbı öteki
    // resim türlerinde çalışıyor, grupta çalışmıyordu: childrenReady TAMAMLANMIŞ bir future
    // olduğu için foreach ikinci çizimde de koşuyor ve makeDone zaten tamamlanmış söze ikinci
    // kez success diyor -> IllegalStateException, SESSİZCE (hata bir future geri çağrısının
    // içinde, betiğe ulaşmıyor, yalnız konsola düşüyor). Sorun #121.
    //
    // NEDEN layout()ün TAMAMINI korumuyoruz: yeniden yerleşim GEREKLİ. Çocuk arada
    // değiştiyse ikinci çizim onu yeni boyuta göre yerleştirmeli -- ölçüldü, HPics(100,60,80),
    // ilk çocuk scale(2):
    //   yalnız makeDone korunur : (0,0) (203,0) (265,0)   yeni boyuta göre  ✅
    //   layout()ün tamamı korunur: (0,0) (102,0) (164,0)   BAYAT, büyüyen çocuk komşusuna biner
    // İlk denememde tamamını korumuştum; #123 incelemesi bu bedeli ölçtü.
    //
    // makeDone'un "tam bir kez" sözleşmesi de böylece bozulmuyor: ikinci kez hiç çağrılmıyor.
    // ÖLÇÜLÜ OLMAK GEREK: bu sınıfta makeDone'u çağıran tek yer burası (ötekiler ImagePic,
    // ImagePicRaw, TextPic, TurtlePicture, VectorGraphicsPic), yani gruplar için "ikinci
    // yerleşim" uyarısı fiilen kalktı -- artık desteklenen bir işlem olduğu için doğrusu da
    // bu. Sözleşme öteki resim türleri için duruyor; grup için bir işaret kaynağı değil.
    //
    // İki çizim ilk yerleşimden ÖNCE gelirse de doğru: iki geri çağrı da kaydolur,
    // birincisi made'i kurar, ikincisi yalnız yerleşimi yeniler.
    if (!made) makeDone()
  }

  def realDraw(): Unit = {
    pics.foreach { p =>
      p.draw()
      tnode.addChild(p.tnode)
    }
    kojoWorld.addLayer(tnode)
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    childrenReady.foreach { _ =>
      layout() // ikinci çizimde de koşuyor; korunan yer layout()ün İÇİ (bkz. yukarı, #121)
    }
  }

  def initGeom() = {
    var pg = pics(0).picGeom
    pics.tail.foreach { pic =>
      pg = pg union pic.picGeom
    }
    pg
  }

  def setFillColor(c: Color): Unit = {
    pics.foreach { p =>
      p.setFillColor(c)
    }
  }

  // Boyayı da ilet -- bkz. PicTransformer.setFillPaint'teki açıklama.
  override def setFillPaint(b: Boya): Unit = {
    pics.foreach { p =>
      p.setFillPaint(b)
    }
  }

  def setPenColor(c: Color): Unit = {
    pics.foreach { p =>
      p.setPenColor(c)
    }
  }

  def setPenThickness(t: Double): Unit = {
    pics.foreach { p =>
      p.setPenThickness(t)
    }
  }

  def erase(): Unit = {
    kojoWorld.removeLayer(tnode)
  }

  def picsCopy = pics.map { _.copy }
}

object GPics {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new GPics(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new GPics(freshPics(pics))
}

class GPics(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {}
  def copy = new GPics(picsCopy)
}

object GPicsCentered {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new GPicsCentered(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new GPicsCentered(freshPics(pics))
}

class GPicsCentered(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {
    var prevPic: Option[Picture] = None
    pics.foreach { pic =>
      prevPic match {
        case Some(ppic) =>
          val pbounds = ppic.bounds
          val bounds = pic.bounds
          val tx = pbounds.x - bounds.x + (pbounds.width - bounds.width) / 2
          val ty = pbounds.y - bounds.y + (pbounds.height - bounds.height) / 2
          pic.offset(tx, ty)
        case None =>
      }
      prevPic = Some(pic)
    }
  }

  def copy = new GPicsCentered(picsCopy)
}

object HPics {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new HPics(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new HPics(freshPics(pics))
}

class HPics(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {
    var prevPic: Option[Picture] = None
    pics.foreach { pic =>
      prevPic match {
        case Some(ppic) =>
          val pbounds = ppic.bounds
          val bounds = pic.bounds
          val tx = pbounds.x + pbounds.width - bounds.x
          pic.offset(tx, 0)
        case None =>
      }
      prevPic = Some(pic)
    }
  }

  def copy = new HPics(picsCopy)
}

object HPicsCentered {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new HPicsCentered(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new HPicsCentered(freshPics(pics))
}

class HPicsCentered(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {
    var prevPic: Option[Picture] = None
    pics.foreach { pic =>
      prevPic match {
        case Some(ppic) =>
          val pbounds = ppic.bounds
          val bounds = pic.bounds
          val tx = pbounds.x + pbounds.width - bounds.x
          val ty = pbounds.y - bounds.y + (pbounds.height - bounds.height) / 2
          pic.offset(tx, ty)
        case None =>
      }
      prevPic = Some(pic)
    }
  }

  def copy = new HPicsCentered(picsCopy)
}

object VPics {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new VPics(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new VPics(freshPics(pics))
}

class VPics(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {
    var prevPic: Option[Picture] = None
    pics.foreach { pic =>
      prevPic match {
        case Some(ppic) =>
          val pbounds = ppic.bounds
          val bounds = pic.bounds
          val ty = pbounds.y + pbounds.height - bounds.y
          pic.offset(0, ty)
        case None =>
      }
      prevPic = Some(pic)
    }
  }

  def copy = new VPics(picsCopy)
}

object VPicsCentered {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new VPicsCentered(freshPics(pics))
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new VPicsCentered(freshPics(pics))
}

class VPicsCentered(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  def layoutChildren(): Unit = {
    var prevPic: Option[Picture] = None
    pics.foreach { pic =>
      prevPic match {
        case Some(ppic) =>
          val pbounds = ppic.bounds
          val bounds = pic.bounds
          val tx = pbounds.x - bounds.x + (pbounds.width - bounds.width) / 2
          val ty = pbounds.y + pbounds.height - bounds.y
          pic.offset(tx, ty)
        case None =>
      }
      prevPic = Some(pic)
    }
  }

  def copy = new VPicsCentered(picsCopy)
}

object BatchPics {
  def apply(pics: collection.Seq[Picture])(implicit kojoWorld: KojoWorld) = new BatchPics(pics.toSeq)
  def apply(pics: Picture*)(implicit kojoWorld: KojoWorld) = new BatchPics(pics)
}

class BatchPics(pics: Seq[Picture])(implicit val kojoWorld: KojoWorld) extends BasePicSequence(pics) {
  // GÖSTERİLEN resmi görünür, ötekileri görünmez yapıyor -- "tail'i gizle" DEĞİL.
  // Eskiden `pics.tail.invisible()` diyordu, yani pics.head'in görünür olmasına güvenip
  // indeksi hiç okumuyordu. Yerleşim ikinci çizimde yeniden koştuğunda (#121) bu
  // GÖRÜNÜRLÜĞÜ SIFIRLIYOR -- ölçüldü, showNext bir kez ilerledikten sonra:
  //   ilk çizim            true,false,false
  //   showNext ilerletince false,true,false
  //   layoutChildren yine  false,false,false   <- hiçbiri görünmüyor, currPicIndex=1
  // Yani hiçbiri görünmeyen bir ara oluşuyor ve sonraki showNext pics(2)'ye atlayarak
  // pics(1)'i hiç göstermiyor. İndeksi okuyunca yöntem idempotent oluyor.
  //
  // YAN ETKİSİ, bilinçli: görünürlük artık İDDİA EDİLİYOR, yalnız kuyruk gizlenmiyor. Bu
  // yüzden gösterilen resmi elle `görünmez()` yapıp grubu yeniden çizmek onu GERİ GETİRİYOR
  // (master'da o yol istisna atıyor ve gizli kalıyordu). Bir yığın resmi için "yerleşim"
  // tam olarak bu demek. GrupYenidenCizimTest'te çivili (#123 incelemesi §4b).
  def layoutChildren(): Unit = pics.zipWithIndex.foreach { case (p, i) =>
    if (i == currPicIndex) p.visible() else p.invisible()
  }

  var currPicIndex = 0
  var lastDraw = System.currentTimeMillis

  def currentPicture = pics(currPicIndex)

  override def showNext(gap: Long) = {
    val currTime = System.currentTimeMillis
    if (currTime - lastDraw > gap) {
      pics(currPicIndex).invisible()
      currPicIndex += 1
      if (currPicIndex == pics.size) {
        currPicIndex = 0
      }
      pics(currPicIndex).visible()
      lastDraw = currTime
    }
  }

  override def picGeom: Geometry = {
    if (!made) {
      return null
    }
    pgTransform.transform(pics(currPicIndex).picGeom)
  }

  def copy = new BatchPics(picsCopy)
}

class PicScreen {
  import scala.collection.mutable.ArrayBuffer

  val pics = ArrayBuffer.empty[Picture]
  var drawn = false
  var showCmd: Option[() => Unit] = None
  var hideCmd: Option[() => Unit] = None

  def add(ps: Picture*): Unit = {
    ps.foreach { pics.append(_) }
  }

  def add(ps: Iterable[Picture]): Unit = {
    ps.foreach { pics.append(_) }
  }

  private def draw(): Unit = {
    pics.foreach { _.draw() }
  }

  def hide(): Unit = {
    pics.foreach { _.invisible() }
    hideCmd.foreach { c =>
      c()
    }
  }

  private def unhide(): Unit = {
    pics.foreach { _.visible() }
  }

  def show(): Unit = {
    if (!drawn) {
      draw()
      drawn = true
    }
    else {
      unhide()
    }

    showCmd.foreach { c =>
      c()
    }
  }

  def erase(): Unit = {
    pics.foreach { _.erase() }
  }

  def onShow(cmd: => Unit): Unit = {
    showCmd = Some(() => cmd)
  }

  def onHide(cmd: => Unit): Unit = {
    hideCmd = Some(() => cmd)
  }
}
