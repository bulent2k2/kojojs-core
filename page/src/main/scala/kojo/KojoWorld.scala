package kojo

import java.util.Random
import com.vividsolutions.jts.geom.Coordinate
import kojo.doodle.Color
import org.scalajs.dom.raw.{KeyboardEvent, UIEvent}
import org.scalajs.dom.{WheelEvent, document, html, window}
import pixiscalajs.PIXI
import pixiscalajs.PIXI.{Point, Rectangle, RendererOptions}
import pixiscalajs.PIXI.interaction.InteractionData

import scala.scalajs.js

trait KojoWorld {
  def canvasWidth: Double
  def canvasHeight: Double
  def addLayer(layer: PIXI.Container): Unit
  def removeLayer(layer: PIXI.Container): Unit
  // Bir resmin değiştiğini (taşındı/döndü/boyandı...) bildirir; pişirme
  // (bake) katmanı bunu son-değişim damgası olarak kullanır. Bkz. maybeBake.
  def noteMutation(node: PIXI.DisplayObject): Unit
  def scheduleLater(fn: => Unit): Unit
  def runLater(ms: Double)(fn: => Unit): Unit
  def render(): Unit
  def moveToFront(obj: PIXI.DisplayObject): Unit
  def moveToBack(obj: PIXI.DisplayObject): Unit

  def setBackground(color: Color): Unit
  def frameDeltaTime: Double
  def animate(fn: => Unit): Unit
  def animateWithState[S](initState: S)(nextState: S => S): Unit
  def timer(ms: Long)(fn: => Unit): Unit
  def stopAnimation(): Unit
  def setup(fn: => Unit): Unit

  def drawStage(fillc: Color)(implicit kojoWorld: KojoWorld)
  def bounceVecOffStage(v: Vector2D, p: Picture): Vector2D
  def bouncePicVectorOffPic(pic: Picture, vel: Vector2D, obstacle: Picture, rg: Random): Vector2D
  def stageBorder: Picture
  def stageLeft: Picture
  def stageTop: Picture
  def stageRight: Picture
  def stageBot: Picture
  def stageArea: Picture

  def isKeyPressed(keyCode: Int): Boolean
  def onKeyPress(fn: Int => Unit): Unit
  def onKeyRelease(fn: Int => Unit): Unit
  def stagePosition: Point
  def positionOnStage(data: InteractionData): Point
  def isAMouseButtonPressed: Boolean
  def mouseMoveOnlyWhenInside(on: Boolean): Unit
  def size(width: Double, height: Double): Unit
  def zoomXY(xfactor: Double, yfactor: Double, cx: Double, cy: Double): Unit
  def mouseXY: Point
  def erasePictures(): Unit
  def toggleFullScreenCanvas(): Unit
  def canvasBounds: Rectangle
  def noZoom(): Unit
}

// Pişirme kararının SAF (DOM'suz) çekirdeği -- Node testinde doğrulanabilsin
// diye ayrıldı (KojoWorldImpl'in geri kalanı Swing/PIXI/DOM'a bağlı).
object BakePolicy {
  val bakeAfterFrames = 3
  val bakeChildThreshold = 150

  // Sahne kalabalıklaşınca ve yakınlaştırılmamışken pişir.
  def shouldConsider(childCount: Int, unzoomed: Boolean): Boolean =
    childCount >= bakeChildThreshold && unzoomed

  // Ucuz ön kontrol (ad + durağanlık). Etkileşim kontrolü pahalı (ağaç
  // dolaşımı) olduğundan ayrı: yalnız bunu geçen adaylar için hesaplanır.
  def isStaleByName(name: String, lastMut: Long, frame: Long): Boolean =
    name != "Turtle Layer" && (frame - lastMut > bakeAfterFrames)

  // Bir sahne çocuğu pişmeye aday mı? Kaplumbağa katmanı ve etkileşimli
  // düğümler muaf; yalnızca bakeAfterFrames karedir damgalanmayanlar aday.
  def isStaleCandidate(name: String, interactive: Boolean, lastMut: Long, frame: Long): Boolean =
    isStaleByName(name, lastMut, frame) && !interactive

  // Çırpınma sigortası: "en eski izi her kare sil" gibi kalıplar her kare
  // geri almaya yol açar (pişir->sil->geri al->pişir); bu tabandan yavaş.
  // Kısa pencerede art arda bu kadar geri alma olursa pişirmeyi kapat, taban
  // davranışa (hepsi canlı) dön.
  val unbakeStreakWindow = 10 // kare
  val maxUnbakeStreak = 5
  def shouldDisableAfterUnbake(streak: Int): Boolean = streak >= maxUnbakeStreak
}

class KojoWorldImpl extends KojoWorld {
  PIXI.Pixi
  private val fiddleContainer =
    document.getElementById("fiddle-container").asInstanceOf[html.Div]
  private val canvas_holder =
    document.getElementById("canvas-holder").asInstanceOf[html.Div]
  val margin = 4.0
  var canvasWidth = fiddleContainer.clientWidth - margin
  var canvasHeight = fiddleContainer.clientHeight - margin
  var canvasOriginX = -canvasWidth / 2
  var canvasOriginY = -canvasHeight / 2
  var screenWidth = canvasWidth
  var screenHeight = canvasHeight
  private val renderer = PIXI.Pixi.autoDetectRenderer(canvasWidth, canvasHeight, rendererOptions(), noWebGL = false)
  private val interaction = renderer.plugins.interaction
  private val stage = new PIXI.Container()
  window.addEventListener("resize", resize)
  init()

  def init() {
    canvas_holder.appendChild(renderer.view)
//    renderer.context.lineCap = "round"
    render()
    stage.name = "Stage"
    //    stage.width = canvasWidth
    //    stage.height = canvasHeight
    stage.interactive = true
    stage.setTransform(canvasWidth / 2, canvasHeight / 2, 1, -1, 0, 0, 0, 0, 0)
    mouseMoveOnlyWhenInside(true)
    initEvents()
  }

  def toggleFullScreenCanvas(): Unit = {
    try {
      import org.scalajs.dom.experimental.Fullscreen._
      if (window.document.fullscreenElement == null) {
        fiddleContainer.requestFullscreen()
      }
      else {
        window.document.exitFullscreen()
      }
    }
    catch {
      case _: Throwable =>
    }
  }

  def size(w: Double, h: Double): Unit = {
    canvasWidth = w
    canvasHeight = h
    screenWidth = canvasWidth
    screenHeight = canvasHeight
    canvasOriginX = -canvasWidth / 2
    canvasOriginY = -canvasHeight / 2
    //    stage.width = w
    //    stage.height = h
    renderer.resize(w, h)
    stage.setTransform(canvasWidth / 2, canvasHeight / 2, 1, -1, 0, 0, 0, 0, 0)
    resetBake() // doku eski boyut/dönüşüme göre pişmişti; yeniden kurulsun
    render()
  }

  def resize(event: UIEvent): Unit = {
    size(fiddleContainer.clientWidth - margin, fiddleContainer.clientHeight - margin)
  }

  //  def originAt(x: Double, y: Double): Unit = {
  //    stage.setTransform(x, y, 1, -1, 0, 0, 0, 0, 0)
  //    render()
  //  }
  //

  def zoomXY(xfactor: Double, yfactor: Double, cx: Double, cy: Double): Unit = {
    val cw = screenWidth
    val ch = screenHeight
//    stage.setTransform(cw / 2 - cx, ch / 2 + cy, xfactor, -yfactor, 0, 0, 0, 0, 0)
    stage.scale.set(xfactor, -yfactor)
    stage.position.set(cw / 2 - cx * xfactor, ch / 2 + cy * yfactor)
    canvasWidth = cw / xfactor
    canvasHeight = ch / yfactor.abs
    canvasOriginX = cx - canvasWidth / 2
    canvasOriginY = cy - canvasHeight / 2
    resetBake() // yakınlaştırma sahne dönüşümünü değiştirir; pişmiş doku
    // sabit çözünürlükte ekran-uzayı olduğundan geçersiz kalır
    render()
  }

  def canvasBounds: Rectangle = {
    new Rectangle(canvasOriginX, canvasOriginY, canvasWidth, canvasHeight)
  }

  def addLayer(layer: PIXI.Container): Unit = {
    stage.addChild(layer)
    // yeni düğümü bu kareyle damgala: yoksa hiç damgalanmadığından çizildiği
    // karenin sonunda pişer; "kur, birkaç kare sonra hareket ettir" kalıbı
    // pişir->unbake->pişir gel-gitine girerdi. Damgayla bakeAfterFrames kare
    // sessiz kalması gerekir.
    noteMutation(layer)
    render()
  }

  def removeLayer(layer: PIXI.Container): Unit = {
    if (bakedNodes.contains(layer)) {
      // silinen resim pişmiş dokudaydı -> dokuyu yeniden kur
      unbakeAll()
    }
    stage.removeChild(layer)
    render()
  }

  // --- İz/boya pişirme (PERFORMANS 2/2) --------------------------------------
  // canlandır döngüsünde her karede yeni resim çizen betikler (ör. yörünge izi)
  // sahneye durmadan çocuk ekliyor; PERFORMANS 1'den (kare başına tek çizim)
  // sonra bile o tek çizim ekrandaki N nesneyle orantılı, yani iz uzadıkça
  // yavaşlıyor. Çözüm: bir kaç karedir DEĞİŞMEYEN çocukları tek bir
  // RenderTexture'a (bakeTexture) pişirip sahneden çıkarmak. Böylece iz, kaç
  // nokta olursa olsun tek bir sprite (O(1)) olarak çiziliyor; hareket eden
  // cisimler (her kare konumuKur ile değişenler) canlı kalıyor.
  //
  // Sinyal: noteMutation her değişimde çocuğun tnode'una o karenin numarasını
  // damgalıyor. bakeAfterFrames karedir damgalanmayan bir çocuk "durağan boya"
  // sayılıp pişiriliyor. Hareketli cisimler her kare damgalandığından hiç
  // pişmiyor. Pişmiş bir çocuk sonradan değişirse (nadir) unbakeAll ile geri
  // alınıyor. Pişmiş resim NESNESİ geçerli kalır (çarpışma geometrisi tnode'un
  // sahnede olmasına bağlı değil), yalnızca ayrı bir DisplayObject olmaktan
  // çıkar. z-sırası: pişmiş boya en alta (dip katman) düşer.
  private var frameCount: Long = 0
  private var bakeSprite: PIXI.Sprite = _
  private var bakeTexture: PIXI.RenderTexture = _
  private var bakeMatrix: PIXI.Matrix = _
  // LinkedHashSet: geri almada pişirme sırasını koru (z-sırası bozulmasın)
  private val bakedNodes = scala.collection.mutable.LinkedHashSet.empty[PIXI.DisplayObject]

  // Damgayı SAHNE düzeyindeki ataya taşı: GPics/HPics/VPics gibi bileşik
  // resimlerde değişen iç çocuk değil, sahne çocuğu olan konteyner damgalanmalı;
  // yoksa konteyner "durağan" sanılıp pişer ve iç hareket donar. Pişmiş bir
  // düğümün parent'ı null olduğundan döngü orada durur.
  def noteMutation(node: PIXI.DisplayObject): Unit = {
    var n = node
    while (n != null && n.parent != null && (n.parent ne stage)) n = n.parent
    if (n != null) {
      n.asInstanceOf[js.Dynamic].__kojoMut = frameCount.toDouble
      if (bakedNodes.nonEmpty && bakedNodes.contains(n)) {
        unbakeAll()
      }
    }
  }

  // pişirme yalnızca sahne varsayılan (yakınlaştırılmamış) dönüşümdeyken
  // güvenli: bakeMatrix/bakeSprite ölçeği hesaba katmaz. Yakınlaştırmada
  // (zoomXY) resetBake pişmişi canlıya döndürür ve burada pişirme durur.
  private def stageUnzoomed: Boolean =
    stage.scale.x == 1.0 && stage.scale.y == -1.0

  // etkileşimli düğümler (ya da etkileşimli torunu olanlar) pişirilmez:
  // sahneden çıkınca PIXI isabet testi onları görmez, fare olayları ölür.
  private def hasInteractive(d: PIXI.DisplayObject): Boolean = {
    d.interactive || (d match {
      case c: PIXI.Container => c.children.exists(hasInteractive)
      case _                 => false
    })
  }

  private def ensureBakeLayer(): Unit = {
    if (bakeSprite == null) {
      bakeTexture = js.Dynamic.global.PIXI.RenderTexture
        .create(canvasWidth, canvasHeight, js.undefined, renderer.resolution)
        .asInstanceOf[PIXI.RenderTexture]
      bakeSprite = new PIXI.Sprite(bakeTexture)
      bakeSprite.name = "Bake Layer"
      // bakeTexture ekran uzayında (piksel) birikiyor; bakeSprite sahne
      // dönüşümünü (öteleme w/2,h/2 + y-ters) tersine çevirsin ki pikseller
      // yerli yerine otursun.
      bakeSprite.scale.set(1, -1)
      // görünür sahne-yerel pencere [canvasOriginX, +canvasWidth] x ...;
      // merkez her zaman 0 DEĞİL (originAt/zoom sonrası). canvasOrigin kullan.
      bakeSprite.position.set(canvasOriginX, canvasOriginY + canvasHeight)
      stage.addChildAt(bakeSprite, 0)
      // sahne-yerel -> ekran-piksel dönüşümü. renderer.render(c, doku) çocuğu
      // KİMLİK ebeveyne bağlayıp sahne-yerel çizer; bu matrisi projeksiyon
      // olarak vererek ekran-uzayına taşıyoruz. (yalnızca yakınlaştırılmamış
      // durumda pişiriyoruz, o yüzden ölçek 1/-1.)
      bakeMatrix = new PIXI.Matrix()
      bakeMatrix.set(1, 0, 0, -1, -canvasOriginX, canvasOriginY + canvasHeight)
    }
  }

  private var bakeDisabled = false
  private def maybeBake(): Unit = {
    if (bakeDisabled) return
    try maybeBakeUnsafe()
    catch {
      case t: Throwable =>
        // pişirme bir performans iyileştirmesi; başarısız olursa animasyonu
        // ASLA durdurma, sessizce devre dışı bırak (canlı çocuklarla sürer)
        bakeDisabled = true
        try resetBake() catch { case _: Throwable => }
    }
  }

  private def maybeBakeUnsafe(): Unit = {
    val kids = stage.children
    // kalabalık + yakınlaştırılmamış (bakeMatrix/bakeSprite ölçeği hesaba katmaz)
    if (!BakePolicy.shouldConsider(kids.length, stageUnzoomed)) return
    ensureBakeLayer()
    val toBake = scala.collection.mutable.ArrayBuffer.empty[PIXI.DisplayObject]
    var i = 0
    while (i < kids.length) {
      val c = kids(i)
      if (c ne bakeSprite) {
        val stamp = c.asInstanceOf[js.Dynamic].__kojoMut
        val last = if (js.isUndefined(stamp)) -1L else stamp.asInstanceOf[Double].toLong
        // "Turtle Layer": kaplumbağa/Picture{} katmanları (Turtle.init hepsine
        // bu adı verir) muaf. Etkileşimli düğümler de muaf (isabet testi).
        // Ucuz ad/durağanlık kontrolünü ÖNCE yap; pahalı hasInteractive ağaç
        // dolaşımını yalnız o kontrolü geçen adaylar için çalıştır.
        if (BakePolicy.isStaleByName(c.name, last, frameCount) && !hasInteractive(c)) {
          toBake += c
        }
      }
      i += 1
    }
    if (toBake.nonEmpty) {
      toBake.foreach { c =>
        // bakeMatrix'i projeksiyon olarak ver: çocuk sahne-yerelden ekran
        // pikseline taşınıp dokuya iner. clear=false -> iz birikir.
        renderer.asInstanceOf[js.Dynamic].render(c, bakeTexture, false, bakeMatrix, false)
        stage.removeChild(c)
        bakedNodes += c
      }
      render()
    }
  }

  // Pişmiş çocukları sahneye geri koyar ve pişmiş dokuyu atar. Herhangi bir
  // yerden (kullanıcı betiği, fare tekerleği zoom'u, pencere boyutu...)
  // çağrılabildiği için KENDİ güvenlik ağı var: patlarsa pişirmeyi devre dışı
  // bırak, animasyonu asla durdurma.
  //
  // NOT: PIXI 4.8.9 RenderTexture'da clear() YOK; bu yüzden dokuyu boşaltmak
  // yerine yok edip bakeSprite'ı kaldırıyoruz. Sonraki pişirme ensureBakeLayer
  // ile yeniden yaratır (hem WebGL hem Canvas renderer'da çalışır).
  //
  // Sınırlama (bilinçli): geri alma ya hep ya hiç. Kalabalık sahnede yanıp
  // sönen (görünür/görünmez) tek bir durağan resim her geçişte tüm dokuyu
  // yeniden pişirtir. İleride düğüm-bazlı geri alma düşünülebilir.
  private var lastUnbakeFrame = -1000L
  private var unbakeStreak = 0
  private def unbakeAll(): Unit = {
    if (bakedNodes.isEmpty) return
    // çırpınma takibi: kısa pencerede art arda geri alma sayılır. Yalnız
    // animasyon çalışırken: frameCount yalnız animateHelper'da arttığından,
    // stopAnimation SONRASI bir betiğin birçok pişmiş resmi silmesi/değiştirmesi
    // aynı "kare"de sayılıp sigortayı boşuna tetiklerdi (o KojoWorld için
    // pişirmeyi kalıcı kapatırdı). Animasyon dururken tek-seferlik geri almalar
    // çırpınma değildir.
    if (animating) {
      unbakeStreak = if (frameCount - lastUnbakeFrame < BakePolicy.unbakeStreakWindow) unbakeStreak + 1 else 0
      lastUnbakeFrame = frameCount
    }
    try {
      // pişirme sırasını koruyarak bakeSprite'ın hemen üstüne (dibe) koy;
      // canlı çocuklar üstte kalır -- pişmeden önceki z-sırasıyla tutarlı
      var i = 1 // 0 = bakeSprite
      bakedNodes.foreach { c => stage.addChildAt(c, i); i += 1 }
      bakedNodes.clear()
      if (bakeSprite != null) { stage.removeChild(bakeSprite); bakeSprite = null }
      // destroy(true): BaseRenderTexture + GL framebuffer'ını da bırak. Argümansız
      // destroy yalnız Texture kabuğunu koparır, render hedefi GL belleği sızar
      // (PIXI doku çöp toplayıcısı render hedeflerini atlar). RenderTexture'ın
      // no-arg destroy'unu gölgelememek için Texture'a yükselt.
      if (bakeTexture != null) { bakeTexture.asInstanceOf[PIXI.Texture].destroy(true); bakeTexture = null }
      render()
      // sürekli geri alma pişirmeyi kârsızlaştırır -> tabana dön (kalıcı).
      if (BakePolicy.shouldDisableAfterUnbake(unbakeStreak)) bakeDisabled = true
    }
    catch {
      case _: Throwable =>
        // son çare: ortada patlarsa geri eklenemeyen düğümler sahneden düşebilir
        // (kabul edilebilir; pişirme zaten kapanıyor). Animasyon durmaz.
        bakeDisabled = true
        bakedNodes.clear()
    }
  }

  // boyut/yakınlaştırma/silme pişmiş dokuyu geçersiz kılar. unbakeAll zaten
  // pişmişleri geri koyup dokuyu atıyor; ayrıca bir şey kalmadıysa no-op.
  private def resetBake(): Unit = {
    unbakeAll()
    if (bakeSprite != null) {
      stage.removeChild(bakeSprite)
      bakeSprite = null
    }
    if (bakeTexture != null) {
      bakeTexture.asInstanceOf[PIXI.Texture].destroy(true) // GL framebuffer'ı da bırak (sızıntı)
      bakeTexture = null
    }
  }

  def erasePictures(): Unit = {
    resetBake() // pişmiş boyayı da temizle (yoksa dokuda hayalet kalır)
    val children = stage.children.toBuffer
    children.foreach { c =>
      if (c.name != "Turtle Layer") {
        stage.removeChild(c)
      }
    }
    render()
  }

  val MaxBurst = 100
  var burstCount = 0
  def scheduleLater(fn: => Unit): Unit = {
    burstCount += 1
    if (burstCount < MaxBurst) {
      fn
    }
    else {
      window.setTimeout(() => fn, 0)
      burstCount = 0
    }
  }

  def runLater(ms: Double)(fn: => Unit): Unit = {
    window.setTimeout(() => fn, ms)
  }

  // PERFORMANS: render() her resim değişiminde çağrılıyor (taşı/döndür/boya/çiz
  // hepsi transformDone -> render zincirinden geçer). Eskiden her çağrı ANINDA
  // tam bir sahne çizimiydi; canlandır döngüsündeki tipik bir kare 5-10 tam
  // çizim yapıyordu (üç-cisim benzetiminde ölçüldü: kare başına 6). Artık
  // render() yalnızca işaret koyar; gerçek renderer.render(stage) kare başına
  // EN FAZLA BİR kez koşar. Ölçülen etki: kare başına çizim 6 -> 1, üç-cisim
  // fiddle'ında ~2 kat FPS. Görsel fark yok: tarayıcı zaten kareler arasında
  // boyamaz.
  //
  // Bekleyen çizim iki yoldan boşalır:
  //  - canlandır DIŞINDA (tek seferlik çizim/etkileşim): kaydedilen rAF'te.
  //  - canlandır İÇİNDE: animateHelper her kare fn'den sonra flushRender()
  //    çağırır. Bu olmadan, rAF geri çağrısı içinde kaydedilen istek tarayıcı
  //    tarafından BİR SONRAKİ kareye kuyruklanır ve ekran hep bir adım geride
  //    kalırdı (~16 ms gecikme). flushRender bekleyeni aynı karede boşaltır.
  private var renderPending = false
  private var renderHandle = 0
  def render(): Unit = {
    if (!renderPending) {
      renderPending = true
      renderHandle = window.requestAnimationFrame(_ => flushRender())
    }
  }

  private def flushRender(): Unit = {
    if (renderPending) {
      renderPending = false
      window.cancelAnimationFrame(renderHandle)
      renderer.render(stage)
    }
  }

  def moveToFront(obj: PIXI.DisplayObject): Unit = {
    // pişmiş düğüm sahnede değil: removeChild null döner, addChild(null) çöker.
    // önce geri al ki gerçek düğüm sahnede olsun.
    if (bakedNodes.contains(obj)) unbakeAll()
    val c = stage.removeChild(obj)
    stage.addChild(c)
    render()
  }

  def moveToBack(obj: PIXI.DisplayObject): Unit = {
    if (bakedNodes.contains(obj)) unbakeAll()
    val c = stage.removeChild(obj)
    stage.addChildAt(c, 0)
    render()
  }

  def rendererOptions(
    antialias:         Boolean = true,
    resolution:        Double  = 1,
    backgroundColor:   Int     = 0xFFFFFF,
    clearBeforeRender: Boolean = true
  ): RendererOptions = {
    js.Dynamic
      .literal(
        antialias = antialias,
        resolution = resolution,
        backgroundColor = backgroundColor,
        clearBeforeRender = clearBeforeRender
      )
      .asInstanceOf[RendererOptions]
  }

  def setBackground(color: Color): Unit = {
    renderer.backgroundColor = color.toRGBDouble
  }

  var animating = false
  def notAssetLoading = !AssetLoader.loading
  var timers = Vector.empty[Int]
  private var prevFrameTime: Double = _

  def frameDeltaTime: Double = {
    val currFrameTime = System.currentTimeMillis() / 1000.0
    if (prevFrameTime == -1) {
      prevFrameTime = currFrameTime
      0
    }
    else {
      val delta = currFrameTime - prevFrameTime
      prevFrameTime = currFrameTime
      delta
    }
  }

  def animate(fn: => Unit): Unit = {
    animating = true
    prevFrameTime = -1
    animateHelper(fn)
  }

  def animateHelper(fn: => Unit): Unit = {
    window.requestAnimationFrame { t =>
      if (notAssetLoading) {
        frameCount += 1
        fn
        maybeBake()
        flushRender() // bu karede birikeni hemen boşalt (bir kare gecikme olmasın)
      }
      if (animating) {
        animateHelper(fn)
      }
    }
  }

  def animateWithState[S](initState: S)(nextState: S => S): Unit = {
    var state = initState
    animate {
      state = nextState(state)
    }
  }

  def setup(fn: => Unit): Unit = {
    window.requestAnimationFrame { _ =>
      if (notAssetLoading) {
        fn
      }
      else {
        setup(fn)
      }
    }
  }

  def stopAnimation(): Unit = {
    animating = false
    timers foreach { t =>
      window.clearInterval(t)
    }
    timers = Vector.empty[Int]
  }

  def timer(ms: Long)(fn: => Unit): Unit = {
    prevFrameTime = -1
    val handle = window.setInterval({ () =>
      if (notAssetLoading) {
        fn
      }
    }, ms)
    timers = timers :+ handle
  }

  lazy val noPic = TurtlePicture { t =>
  }(this)
  @volatile var stageBorder: Picture = _
  @volatile var stageLeft: Picture = _
  @volatile var stageTop: Picture = _
  @volatile var stageRight: Picture = _
  @volatile var stageBot: Picture = _
  @volatile var stageArea: Picture = _

  def clearStage() {
    stageBorder = noPic
    stageLeft = noPic
    stageTop = noPic
    stageRight = noPic
    stageBot = noPic
  }

  def drawStage(fillc: Color)(implicit kojoWorld: KojoWorld) {
    def left(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.forward(size)
    }
    def top(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.right()
      t.forward(size)
    }
    def right(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.right(180)
      t.forward(size)
    }
    def bottom(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.left()
      t.forward(size)
    }

    val cb = canvasBounds

    stageLeft = left(canvasHeight)
    stageLeft.translate(cb.x, cb.y)

    stageTop = top(canvasWidth)
    stageTop.translate(cb.x, cb.y + cb.height)

    stageRight = right(canvasHeight)
    stageRight.translate(cb.x + cb.width, cb.y + cb.height)

    stageBot = bottom(canvasWidth)
    stageBot.translate(cb.x + cb.width, cb.y)

    stageArea = TurtlePicture { t =>
      t.setFillColor(fillc)
      t.setPenColor(Color.darkGray)
      for (_ <- 1 to 2) {
        t.forward(canvasHeight)
        t.right()
        t.forward(canvasWidth)
        t.right()
      }
    }
    stageArea.translate(cb.x, cb.y)

    stageBorder = GPics(
      stageLeft,
      stageTop,
      stageRight,
      stageBot
    )

    stageArea.draw()
    stageBorder.draw()
  }

  def bounceVecOffStage(v: Vector2D, p: Picture): Vector2D = {
    val topCollides = p.collidesWith(stageTop)
    val leftCollides = p.collidesWith(stageLeft)
    val botCollides = p.collidesWith(stageBot)
    val rightCollides = p.collidesWith(stageRight)

    val c = v.magnitude / math.sqrt(2)
    if (topCollides && leftCollides)
      Vector2D(c, -c)
    else if (topCollides && rightCollides)
      Vector2D(-c, -c)
    else if (botCollides && leftCollides)
      Vector2D(c, c)
    else if (botCollides && rightCollides)
      Vector2D(-c, c)
    else if (topCollides)
      Vector2D(v.x, -v.y)
    else if (botCollides)
      Vector2D(v.x, -v.y)
    else if (leftCollides)
      Vector2D(-v.x, v.y)
    else if (rightCollides)
      Vector2D(-v.x, v.y)
    else
      v
  }

  def collidesWithStage(p: Picture): Boolean = {
    val stageparts = List(stageTop, stageBot, stageLeft, stageRight)
    p.collision(stageparts).isDefined
  }

  def bouncePicVectorOffPic(pic: Picture, vel: Vector2D, obstacle: Picture, rg: Random): Vector2D = {
    // returns points on the obstacle that contain the given collision coordinate
    def obstacleCollPoints(c: Coordinate): Option[js.Array[Coordinate]] = {
      obstacle.picGeom.getCoordinates.sliding(2).find { cs =>
        val xcheck = if (cs(0).x > cs(1).x)
          cs(0).x >= c.x && c.x >= cs(1).x
        else
          cs(0).x <= c.x && c.x <= cs(1).x

        val ycheck = if (cs(0).y > cs(1).y)
          cs(0).y >= c.y && c.y >= cs(1).y
        else
          cs(0).y <= c.y && c.y <= cs(1).y
        xcheck && ycheck
      }
    }
    // returns vector for obstacle boundary segment that contains the collision point
    def obstacleCollVector(c: Coordinate) = makeVectorFromCollPoints(obstacleCollPoints(c))

    // creates a vector out of two (collision) points
    def makeVectorFromCollPoints(cps: Option[js.Array[Coordinate]]) = cps match {
      case Some(cs) =>
        Vector2D(cs(0).x - cs(1).x, cs(0).y - cs(1).y)
      case None =>
        println("Warning: unable to determine collision vector; generating random vector")
        Vector2D(rg.nextDouble, rg.nextDouble)
    }

    def collisionVector = {
      val pt = obstacle.intersection(pic)
      val iCoords = pt.getCoordinates

      if (iCoords.length == 0) {
        Vector2D(rg.nextDouble, rg.nextDouble).normalize
      }
      else {
        if (iCoords.length == 1) {
          val cv1 = obstacleCollVector(iCoords(0))
          cv1.normalize
        }
        else {
          val c1 = iCoords(0)
          val c2 = iCoords(iCoords.length - 1)
          makeVectorFromCollPoints(Some(js.Array(c1, c2))).normalize
        }
      }
    }
    def pullbackCollision() = {
      val velNorm = vel.normalize
      val v2 = velNorm.rotate(180)
      val velMag = vel.magnitude
      var pulled = 0
      while (pic.collidesWith(obstacle) && pulled < velMag) {
        pic.offset(v2)
        pulled += 1
      }
      pic.offset(velNorm)
    }

    pullbackCollision()
    val cv = collisionVector
    vel.bounceOff(cv)
  }

  var zoomEnabled = true
  def noZoom(): Unit = {
    zoomEnabled = false
  }

  val pressedKeys = new collection.mutable.HashSet[Int]

  def initEvents(): Unit = {
    def keyDown(e: KeyboardEvent): Unit = {
      pressedKeys.add(e.keyCode)
    }
    def keyUp(e: KeyboardEvent): Unit = {
      pressedKeys.remove(e.keyCode)
    }
    var zoomf = 1.0
    def mouseWheel(e: WheelEvent): Unit = {
      if (zoomEnabled) {
        val direction = e.deltaY
        if (direction > 0) {
          zoomf = zoomf * 0.9
        }
        else {
          zoomf = zoomf * 1.1
        }
        zoomXY(zoomf, zoomf, 0, 0)
      }
    }
    window.addEventListener("keydown", keyDown(_), false)
    window.addEventListener("keyup", keyUp(_), false)
    window.addEventListener("wheel", mouseWheel(_), false)
  }

  def isKeyPressed(keyCode: Int) = pressedKeys.contains(keyCode)
  def stagePosition = stage.position
  def positionOnStage(data: InteractionData) = data.getLocalPosition(stage)
  def isAMouseButtonPressed = interaction.mouse.buttons > 0
  def mouseMoveOnlyWhenInside(on: Boolean): Unit = {
    interaction.moveWhenInside = on
  }
  def mouseXY = interaction.mouse.getLocalPosition(stage)

  def onKeyPress(fn: Int => Unit): Unit = {
    def keyDown(e: KeyboardEvent): Unit = {
      fn(e.keyCode)
    }
    window.addEventListener("keydown", keyDown(_), false)
  }
  def onKeyRelease(fn: Int => Unit): Unit = {
    def keyUp(e: KeyboardEvent): Unit = {
      fn(e.keyCode)
    }
    window.addEventListener("keyup", keyUp(_), false)
  }
}
