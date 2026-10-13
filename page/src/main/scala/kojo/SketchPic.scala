package kojo

import com.vividsolutions.jts.geom.Geometry
import kojo.Utils.newCoordinate
import kojo.doodle.Color
import org.scalajs.dom
import pixiscalajs.PIXI

import scala.scalajs.js

/**
 * Eskiz resmi: tek bir HTML `canvas` + tek bir PIXI `Sprite` (masaüstü Java2DPic'in karşılığı).
 *
 * Çizilenler PİKSEL; kareler arasında yüzey SİLİNMİYOR (`artalan` çağrılmazsa çizimler birikir,
 * iz bırakma bu). Sahnede her zaman tek bir düğüm var, çizim ne kadar uzarsa uzasın: Resim
 * nesnesi birikmediğinden `PicCache` ve sahne büyümüyor. Seçenek ve gerekçe: oneri-eskiz.md.
 *
 * ZAMANLAMA (masaüstüyle aynı): `kurulum` resim İLK çizildiğinde bir kez, `çizimDöngüsü` her
 * karede. `noLoop()` yalnız BU eskizin döngüsünü durdurur; dünyanın canlandırması sürer.
 *
 * DOKU: `PIXI.Texture.from` KULLANILMIYOR (önbelleğe girip hiç çıkmıyor, bkz. Boya.dokuYap).
 * Her kare `baseTexture.update()` ile yüzey yeniden yükleniyor; pişirmeye (KojoWorld.maybeBake)
 * karşı: katman her karede `noteMutation` ile damgalanıyor, yani çalışırken pişmez; `noLoop`
 * sonrası durağan kalınca pişebilir, ki sorun değil (piksel olarak aynı).
 *
 * @param ölçekÇarpanı yüzey `genişlik × f`, `yükseklik × f` piksel; sprite 1/f ölçeklenir
 *                    (daha ince çizgi için). Kullanıcının koordinatları yüzey pikselidir.
 */
class SketchPic(
    kurulum: CanvasDraw => Unit,
    döngü: CanvasDraw => Unit,
    ölçekÇarpanı: Double = 1,
    tohumla: Long => Unit = _ => ()
)(implicit val kojoWorld: KojoWorld)
    extends Picture
    with ReadyPromise {

  private val f = if (ölçekÇarpanı > 0) ölçekÇarpanı else 1.0
  private val yüzeyEni = math.max(1, math.round(kojoWorld.canvasWidth * f).toInt)
  private val yüzeyBoyu = math.max(1, math.round(kojoWorld.canvasHeight * f).toInt)

  private val tuval: dom.html.Canvas = {
    val c = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    c.width = yüzeyEni
    c.height = yüzeyBoyu
    c
  }

  /** Testler piksel okumak için ham tuvale buradan ulaşıyor. */
  private[kojo] def tuvalElemanı: dom.html.Canvas = tuval

  /** Kullanıcıya verilen yüzey. */
  val cd = new CanvasDraw(tuval, yüzeyEni, yüzeyBoyu, tohumla)

  private var bt: js.Dynamic = _

  private val sprite: PIXI.Sprite = {
    val P = js.Dynamic.global.PIXI
    val seçenekler = js.Dynamic.literal(mipmap = P.MIPMAP_MODES.OFF)
    val taban = js.Dynamic.newInstance(P.BaseTexture)(tuval.asInstanceOf[js.Any], seçenekler)
    taban.asInstanceOf[js.Dynamic].updateDynamic("__kocoEskizDokusu")(true)
    bt = taban
    val doku = js.Dynamic.newInstance(P.Texture)(taban)
    val s = js.Dynamic.newInstance(P.Sprite)(doku).asInstanceOf[PIXI.Sprite]
    // y yukarı sahnede ham tuval satırı i, sahnede y = i'de -- bkz. CanvasDraw (Java2DPic gibi)
    s.setTransform(0, 0, 1 / f, 1 / f, 0, 0, 0, 0, 0)
    s
  }
  private val katman = new PIXI.Container
  katman.addChild(sprite)
  val tnode = katman

  private var kuruldu = false
  // Her (yeniden) çizimde yeni bir döngü kuruluyor; eskileri `nesil` karşılaştırmasıyla etkisiz kalıyor.
  // Neden: KojoWorld.animate'in döngüyü durduracak tutamacı yok; `durdur()` sonrası sil+çiz ile
  // yeniden çizilen eskiz, döngü bir kez kuruldu diye canlanmıyordu. Eski (ölü ya da canlı) zincirler
  // her karede boş dönüyor ve dünya kapanana ya da canlandırma durana kadar sürüyor -- animate'in
  // genel davranışı; yüzeyin belleği de o zamana kadar tutuluyor.
  private var nesil = 0
  private var silindi = false

  makeDone()

  private def yükle(): Unit = bt.update()

  /** Bir kare: `çizimDöngüsü` + doku yüklemesi. Testler canlandırma olmadan kare sürmek için çağırır. */
  private[kojo] def adım(): Unit = {
    if (!silindi && cd.loop) {
      döngü(cd)
      yükle()
      kojoWorld.noteMutation(tnode)
      kojoWorld.render()
    }
  }

  override def realDraw(): Unit = {
    silindi = false
    if (!kuruldu) {
      kuruldu = true
      kurulum(cd)
      yükle()
    }
    kojoWorld.addLayer(katman)
    nesil += 1
    val benim = nesil
    kojoWorld.animate { if (benim == nesil) adım() }
  }

  def erase(): Unit = {
    silindi = true
    kojoWorld.removeLayer(katman)
    // GL tarafını bırak: yüzey bir daha çizilirse kendiliğinden yeniden yüklenir
    if (js.typeOf(bt.dispose) == "function") bt.dispose()
  }

  override def setFillColor(c: Color): Unit = {}
  override def setPenColor(c: Color): Unit = {}
  override def setPenThickness(t: Double): Unit = {}

  override def initGeom(): Geometry = {
    import scala.scalajs.js.JSConverters._
    val en = yüzeyEni / f
    val boy = yüzeyBoyu / f
    val cab = Seq(
      newCoordinate(0, 0), newCoordinate(0, boy), newCoordinate(en, boy), newCoordinate(en, 0), newCoordinate(0, 0)
    )
    Utils.Gf.createLineString(cab.toJSArray)
  }

  def copy: Picture = new SketchPic(kurulum, döngü, ölçekÇarpanı, tohumla)
}
