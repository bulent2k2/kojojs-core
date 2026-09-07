package kojo

import kojo.doodle.Color
import org.scalajs.dom.window
import pixiscalajs.PIXI
import pixiscalajs.PIXI.Point

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

class Turtle(x: Double, y: Double, forPic: Boolean = false, costume: String = null)(implicit kojoWorld: KojoWorld)
  extends TurtleAPI
  with RichTurtleCommands {
  private[kojo] val turtleLayer = new PIXI.Container()
  private var turtleImage: PIXI.Container = _
  private[kojo] val turtlePath = new PIXI.Graphics()
  private[kojo] val turtlePathPoints = ArrayBuffer[(Double, Double)]()
  var prevMoveTo: Option[Point] = None
  private def turtlePathMoveTo(x: Double, y: Double): Unit = {
    turtlePath.moveTo(x, y)
    prevMoveTo = Some(Point(x, y))
    //    turtlePathPoints += ((x, y))
  }

  private def turtlePathLineTo(x: Double, y: Double): Unit = {
    prevMoveTo.foreach { pt =>
      turtlePathPoints += ((pt.x, pt.y))
      prevMoveTo = None
    }

    turtlePath.lineTo(x, y)
    turtlePathPoints += ((x, y))
  }

  private val tempForwardPath = new PIXI.Graphics()

  private var penWidth = 2d
  private var penColor = Color.red
  private var fillColor: Color = _
  private var penFontSize = 15
  private var penFontFamily: String = null
  private var penIsUp = false
  private var animationDelay = 1000l
  private val savedPosHe = new mutable.Stack[(PIXI.Point, Double)]
  private val savedStyles = new mutable.Stack[(Color, Color, Double, Int, Boolean)]

  var commandQs = mutable.Queue.empty[Command] :: Nil

  // giysi (costume) verilmişse kaplumbağa simgesi yerine o imge yüklenir;
  // yükleyici anahtarı ImagePic'teki gibi url'nin kendisi
  private val costumeKey = if (costume == null) "turtle32" else costume
  AssetLoader.addAndLoad(costumeKey, if (costume == null) "assets/images/turtle32.png" else costume, init)

  private def init(loader: PIXI.loaders.Loader, any: Any) {
    turtleLayer.name = "Turtle Layer"
    if (!forPic) {
      kojoWorld.addLayer(turtleLayer)
    }
    turtleImage = loadTurtle(x, y, loader)
    turtleImage.name = "Turtle Icon"

    turtlePath.name = "Turtle Path"
    turtleLayer.addChild(turtlePath)
    if (!forPic) {
      turtleLayer.addChild(turtleImage)
    }
    initTurtleLayer()
    kojoWorld.runLater(0)(queueHandler)
  }

  private def initTurtleLayer(): Unit = {
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    turtlePathMoveTo(x, y)
    turtleImage.position.set(x, y)
    turtleImage.rotation = Utils.deg2radians(90)
  }

  // private[kojo]: TurkishTurtle bunları `sync` ile kuyruğun doğru noktasında
  // okuyor (konumuOku / yönüOku). Dışarıya AÇILMIYOR -- anlık bir okuma
  // kuyruktaki komutlardan önceki değeri verirdi.
  private[kojo] def position = turtleImage.position

  private def headingRadians = turtleImage.rotation

  private[kojo] def heading = Utils.rad2degrees(headingRadians)

  private def loadTurtle(x: Double, y: Double, loader: PIXI.loaders.Loader): PIXI.Container = {
    val turtle = {
      val rasterTurtle = new PIXI.Sprite(loader.resources(costumeKey).texture)
      if (costume == null) {
        rasterTurtle.position.set(-16, -16) // 32x32 kaplumbağa simgesini ortala
        rasterTurtle.alpha = 0.7
      }
      else {
        // Giysi imgesi: ImagePic'teki gibi y ekseninde çevriliyor (dünya ters,
        // çevrilmezse imge baş aşağı görünür), ayrıca konteynerin merkezine
        // oturtuluyor -- dönme/konum hep bu merkeze göre işliyor.
        rasterTurtle.setTransform(
          -rasterTurtle.width / 2, rasterTurtle.height / 2, 1, -1, 0, 0, 0, 0, 0)
      }
      rasterTurtle
    }
    val turtleHolder = new PIXI.Container()
    turtleHolder.addChild(turtle)
    turtleHolder
  }

  // Yüklü giysi listesi ve sıradaki (birsonrakiGiysi için); giysi ölçeği
  // giysi değişince korunsun diye ayrı tutuluyor.
  private var costumes: Vector[String] = Vector.empty
  private var costumeIndex = 0
  private var costumeScale = 1.0

  // Giysiyi yükleyip kaplumbağa simgesinin yerine koyar. Yükleme eşzamansız
  // olduğu için kuyruğu ANCAK yükleme bittikten sonra sürdürüyoruz; yoksa
  // sonraki komutlar eski simgeyle çalışırdı.
  private def realSetCostume(url: String): Unit = {
    AssetLoader.addAndLoad(url, url, { (loader: PIXI.loaders.Loader, _: Any) =>
      // Yükleme başarısızsa (404, bozuk resim) resource.error dolu ve texture
      // tanımsız olur; new Sprite(undefined) burada patlar ve KUYRUK TIKANIR --
      // komut kuyruğu bir daha ilerlemediğinden betiğin geri kalanı hiç
      // koşmaz. Onun için dokuyu kullanmadan önce denetliyoruz; hata varsa
      // giysi değişmiyor ama kuyruk normal akışına devam ediyor.
      val res = loader.resources(url).asInstanceOf[js.Dynamic]
      val doku = if (js.isUndefined(res) || res == null) js.undefined else res.texture
      if (js.isUndefined(doku) || doku == null) {
        println(s"Uyarı: giysi yüklenemedi: $url")
      }
      else {
        val s = new PIXI.Sprite(doku.asInstanceOf[PIXI.Texture])
        // loadTurtle'daki giysi yolunun aynısı: y'de çevir, merkeze otur, ölçekle
        s.setTransform(
          -s.width * costumeScale / 2, s.height * costumeScale / 2,
          costumeScale, -costumeScale, 0, 0, 0, 0, 0)
        turtleImage.removeChildren()
        turtleImage.addChild(s)
        kojoWorld.noteMutation(turtleImage)
        kojoWorld.render()
      }
      kojoWorld.scheduleLater(queueHandler)
    })(kojoWorld)
  }

  private def realSetCostumes(urls: Vector[String]): Unit = {
    costumes = urls
    costumeIndex = 0
    if (urls.isEmpty) kojoWorld.scheduleLater(queueHandler) else realSetCostume(urls(0))
  }

  private def realNextCostume(): Unit = {
    if (costumes.isEmpty) kojoWorld.scheduleLater(queueHandler)
    else {
      costumeIndex = (costumeIndex + 1) % costumes.length
      realSetCostume(costumes(costumeIndex))
    }
  }

  private def realScaleCostume(factor: Double): Unit = {
    costumeScale = costumeScale * factor
    if (turtleImage.children.length > 0) {
      val s = turtleImage.getChildAt(0).asInstanceOf[PIXI.Sprite]
      // Yalnız ölçeği çarpıyoruz; konumu da aynı çarpanla güncelleyince simge
      // merkezde kalıyor. İşaretlere DOKUNMUYORUZ: kaplumbağa simgesi (+1) ile
      // giysi (y'de -1, çevrilmiş) farklı işaret taşıyor, ikisi de korunmalı.
      s.position.set(s.position.x * factor, s.position.y * factor)
      s.scale.set(s.scale.x * factor, s.scale.y * factor)
      kojoWorld.noteMutation(turtleImage)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  def forward(n: Double): Unit = {
    commandQ.enqueue(Forward(n))
  }

  def hop(n: Double): Unit = {
    commandQ.enqueue(Hop(n))
  }

  def turn(angle: Double): Unit = {
    commandQ.enqueue(Turn(angle))
  }

  def setAnimationDelay(delay: Long): Unit = {
    commandQ.enqueue(SetAnimationDelay(delay))
  }

  def setPenThickness(t: Double): Unit = {
    commandQ.enqueue(SetPenThickness(t))
  }

  def setPenColor(color: Color): Unit = {
    commandQ.enqueue(SetPenColor(color))
  }

  def setPenFontSize(n: Int): Unit = {
    commandQ.enqueue(SetPenFontSize(n))
  }

  override def setPenFontFamily(name: String): Unit = {
    commandQ.enqueue(SetPenFontFamily(name))
  }

  override def dot(diameter: Int): Unit = {
    commandQ.enqueue(Dot(diameter))
  }

  def setFillColor(color: Color): Unit = {
    commandQ.enqueue(SetFillColor(color))
  }

  // Kalemin şu an inik olup olmadığı ve canlandırma gecikmesi: masaüstünde
  // kalemİnikMi / canlandırmaHızı bunları okuyor. Kuyruğa GİRMEZ; kuyruktaki
  // komutlar bunları değiştirebileceği için okunan değer "şu ana kadar
  // kuyruğa alınanlardan sonraki" değil, "şu anki" durumdur.
  def penIsDown: Boolean = !penIsUp
  def animationDelayMs: Long = animationDelay

  def changePosition(x: Double, y: Double): Unit = {
    commandQ.enqueue(ChangePosition(x, y))
  }

  // ---- giysi (costume) ----
  def setCostume(url: String): Unit = commandQ.enqueue(SetCostume(url))
  def setCostumes(urls: String*): Unit = commandQ.enqueue(SetCostumes(urls.toVector))
  def nextCostume(): Unit = commandQ.enqueue(NextCostume)
  def scaleCostume(factor: Double): Unit = commandQ.enqueue(ScaleCostume(factor))

  def setPosition(x: Double, y: Double): Unit = {
    commandQ.enqueue(SetPosition(x, y))
  }

  def setHeading(theta: Double): Unit = {
    commandQ.enqueue(SetHeading(Utils.deg2radians(theta)))
  }

  def moveTo(x: Double, y: Double): Unit = {
    commandQ.enqueue(MoveTo(x, y))
  }

  def arc2(r: Double, a: Double): Unit = {
    commandQ.enqueue(Arc2(r, a))
  }

  def write(text: String): Unit = {
    commandQ.enqueue(Write(text))
  }

  def towards(other: Turtle): Unit = {
    commandQ.enqueue(TowardsTurtle(other))
  }

  def towards(x: Double, y: Double): Unit = {
    commandQ.enqueue(Towards(x, y))
  }

  def savePosHe(): Unit = {
    commandQ.enqueue(SavePosHe)
  }

  def restorePosHe(): Unit = {
    commandQ.enqueue(RestorePosHe)
  }

  def saveStyle(): Unit = {
    commandQ.enqueue(SaveStyle)
  }

  def restoreStyle(): Unit = {
    commandQ.enqueue(RestoreStyle)
  }

  def clear(): Unit = {
    commandQ.enqueue(Clear)
  }

  def pause(seconds: Double): Unit = {
    commandQ.enqueue(Pause(seconds))
  }

  def penUp(): Unit = {
    commandQ.enqueue(PenUp)
  }

  def penDown(): Unit = {
    commandQ.enqueue(PenDown)
  }

  def invisible(): Unit = {
    commandQ.enqueue(Invisible)
  }

  def visible(): Unit = {
    commandQ.enqueue(Visible)
  }

  private[kojo] def sync(fn: () => Unit): Unit = {
    commandQ.enqueue(Sync(fn))
  }

  private def queueHandler(): Unit = {
    if (commandQ.size > 0) {
      commandQ.dequeue() match {
        case Forward(n)  => realForward(n, penIsUp)
        case Hop(n)      => realForward(n, true)
        case Turn(angle) => realLeft(angle)
        case SetAnimationDelay(delay) =>
          animationDelay = delay; kojoWorld.scheduleLater(queueHandler)
        case SetPenThickness(t) => realSetPenThickness(t)
        case SetPenColor(c)     => realSetPenColor(c)
        case SetFillColor(c)    => realSetFillColor(c)
        case SetPosition(x, y)  => realSetPosition(x, y)
        case ChangePosition(x, y) =>
          realSetPosition(turtleImage.position.x + x, turtleImage.position.y + y)
        case SetCostume(url)     => realSetCostume(url)
        case SetCostumes(urls)   => realSetCostumes(urls)
        case NextCostume         => realNextCostume()
        case ScaleCostume(f)     => realScaleCostume(f)
        case SetHeading(theta)  => realSetHeading(theta)
        case MoveTo(x, y)       => realMoveTo(x, y)
        case Arc2(r, a)         => realArc2(r, a)
        case PopQ               => realPopQ()
        case Write(text)        => realWriteText(text)
        case SetPenFontSize(n)  => realSetPenFontSize(n)
        case SetPenFontFamily(f) => realSetPenFontFamily(f)
        case Dot(çap)           => realDot(çap)
        case Towards(x, y)      => realTowards(x, y)
        case TowardsTurtle(o)   => realTowards(o.position.x, o.position.y)
        case SavePosHe          => realSavePosHe()
        case RestorePosHe       => realRestorePosHe()
        case SaveStyle          => realSaveStyle()
        case RestoreStyle       => realRestoreStyle()
        case Clear              => realClear()
        case Pause(seconds)     => realPause(seconds)
        case PenUp              => realPenUpDown(true)
        case PenDown            => realPenUpDown(false)
        case Sync(fn)           => realSync(fn)
        case Invisible          => realInvisible()
        case Visible            => realVisible()
      }
    }
  }

  private def realSetPenThickness(t: Double): Unit = {
    penWidth = t
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    kojoWorld.scheduleLater(queueHandler)
  }

  val noColor = Color(0, 0, 0, 0)
  private def realSetPenColor(color0: Color): Unit = {
    val color = if (color0 == null) noColor else color0
    penColor = color
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPenFontSize(n: Int): Unit = {
    penFontSize = n
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPenFontFamily(f: String): Unit = {
    penFontFamily = f
    kojoWorld.scheduleLater(queueHandler)
  }

  // Masaüstü Kojo noktayı "kalem kalınlığı kadar minik bir ileri adım" ile
  // çiziyor; PIXI'de çizgi ucu varsayılan olarak düz olduğu için o yöntem köşeli
  // bir leke bırakırdı. Bunun yerine kalem rengiyle dolu bir daire çiziyoruz.
  private def realDot(çap: Double): Unit = {
    if (!penIsUp) {
      val x = turtleImage.position.x
      val y = turtleImage.position.y
      turtlePath.lineStyle(0, 0, 0) // dairenin kenarlığı olmasın
      turtlePath.beginFill(penColor.toRGBDouble, penColor.alpha.get)
      turtlePath.drawCircle(x, y, çap / 2)
      turtlePath.endFill()
      // kalemin ve varsa kullanıcının açık boyamasının durumunu geri koy
      turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
      if (fillColor != null) turtlePath.beginFill(fillColor.toRGBDouble, fillColor.alpha.get)
      turtlePathMoveTo(x, y)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetFillColor(color0: Color): Unit = {
    val color = if (color0 == null) noColor else color0
    // start new path
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    // set new fill
    fillColor = color
    turtlePath.beginFill(fillColor.toRGBDouble, fillColor.alpha.get)
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPosition(x: Double, y: Double): Unit = {
    turtleImage.position.x = x
    turtleImage.position.y = y
    turtlePathMoveTo(x, y)
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetHeading(theta: Double): Unit = {
    turtleImage.rotation = theta
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realForwardNoAnim(n: Double, hop: Boolean): Unit = {
    val p0x = position.x
    val p0y = position.y
    val (pfx, pfy) = TurtleHelper.posAfterForward(p0x, p0y, headingRadians, n)
    if (hop) {
      turtlePathMoveTo(pfx, pfy)
    }
    else {
      turtlePathLineTo(pfx, pfy)
    }
    turtlePath.clearDirty += 1
    turtleImage.position.x = pfx
    turtleImage.position.y = pfy
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realForward(n: Double, hop: Boolean): Unit = {
    if (animationDelay == 0) {
      realForwardNoAnim(n, hop)
      return
    }

    turtleLayer.addChild(tempForwardPath)
    val p0x = position.x
    val p0y = position.y
    val (pfx, pfy) = TurtleHelper.posAfterForward(p0x, p0y, headingRadians, n)
    val aDelay = TurtleHelper.delayFor(n, animationDelay)
    //      println(s"($p0x, $p0y) -> ($pfx, $pfy) [$aDelay]")
    val startTime = window.performance.now()

    def forwardFrame(frameTime: Double): Unit = {
      val elapsedTime = frameTime - startTime
      val frac = elapsedTime / aDelay
      //        println(s"Fraction: $frac")

      if (frac > 1) {
        if (hop) {
          turtlePathMoveTo(pfx, pfy)
        }
        else {
          tempForwardPath.clear()
          turtleLayer.removeChild(tempForwardPath)
          turtlePathLineTo(pfx, pfy)
        }
        turtlePath.clearDirty += 1
        turtleImage.position.x = pfx
        turtleImage.position.y = pfy
        kojoWorld.render()
        kojoWorld.scheduleLater(queueHandler)
      }
      else {
        val currX = p0x * (1 - frac) + pfx * frac
        val currY = p0y * (1 - frac) + pfy * frac
        if (!hop) {
          tempForwardPath.clear()
          tempForwardPath.lineStyle(penWidth, Color.orange.toRGBDouble, 1)
          tempForwardPath.moveTo(p0x, p0y)
          tempForwardPath.lineTo(currX, currY)
          //          tempGraphics.clearDirty += 1
        }
        turtleImage.position.x = currX
        turtleImage.position.y = currY
        window.requestAnimationFrame(forwardFrame)
      }
      kojoWorld.render()
    }

    window.requestAnimationFrame(forwardFrame)
  }

  private def realLeft(angle: Double): Unit = {

    def leftFrame(): Unit = {
      val angleRads = Utils.deg2radians(angle)
      turtleImage.rotation += angleRads
      kojoWorld.render()
      kojoWorld.scheduleLater(queueHandler)
    }

    leftFrame()
  }

  private def realArc2(r: Double, a: Double) {
    pushQ()
    if (a == 0) {
      return
    }

    def x(t: Double) = r * math.cos(t.toRadians)

    def y(t: Double) = r * math.sin(t.toRadians)

    def makeArc() {
      val head = heading
      if (r != 0) {
        val pos = position
        var currAngle = 0.0
        val trans = new PIXI.Matrix
        trans.translate(-r, 0)
        trans.rotate((head - 90).toRadians)
        trans.translate(pos.x, pos.y)
        val step = if (a > 0) 3 else -3
        val pt = new Point(0, 0)
        val aabs = a.abs
        val aabsFloor = aabs.floor
        while (currAngle.abs < aabsFloor) {
          currAngle += step
          // account for step size > 1
          while (currAngle.abs > aabsFloor) currAngle -= step / step.abs
          pt.set(x(currAngle), y(currAngle))
          trans(pt, pt)
          moveTo(pt.x, pt.y)
        }
        if (a.floor != a) {
          currAngle += (aabs - aabs.floor) * step
          pt.set(x(currAngle), y(currAngle))
          trans(pt, pt)
          moveTo(pt.x, pt.y)
        }
      }
      if (a > 0) {
        setHeading(head + a)
      }
      else {
        setHeading(head + 180 + a)
      }
    }

    makeArc()
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realMoveTo(x: Double, y: Double) {
    pushQ()
    val newTheta = towardsHelper(x, y)
    setHeading(newTheta.toDegrees)
    val d = distanceTo(x, y)
    forward(d)
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realWriteText(text: String): Unit = {
    if (!penIsUp) {
      val pixiText = new PIXI.Text(text)
      pixiText.setTransform(0, 0, 1, -1, 0, 0, 0, 0, 0)
      pixiText.position = position
      pixiText.rotation = (heading - 90).toRadians
      pixiText.style.fontSize = penFontSize
      if (penFontFamily != null) pixiText.style.asInstanceOf[scala.scalajs.js.Dynamic].fontFamily = penFontFamily
      pixiText.style.fill = penColor.toCanvas
      turtleLayer.addChild(pixiText)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSavePosHe(): Unit = {
    val pos = position
    savedPosHe.push((PIXI.Point(pos.x, pos.y), heading))
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realRestorePosHe(): Unit = {
    // realRestoreStyle ile aynı tehlike: eşleşen savePosHe yoksa pop fırlatır ve
    // istisna queueHandler içinde olduğu için komut pompası ölür.
    if (savedPosHe.isEmpty) {
      kojoWorld.scheduleLater(queueHandler)
      return
    }
    pushQ()
    val (newPosition, newHeading) = savedPosHe.pop()
    setPosition(newPosition.x, newPosition.y)
    setHeading(newHeading)
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  // the heading is computed here, inside the queue, so that it sees the position
  // left by the commands queued before this one
  private def realTowards(x: Double, y: Double): Unit = {
    turtleImage.rotation = towardsHelper(x, y)
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSaveStyle(): Unit = {
    savedStyles.push((penColor, fillColor, penWidth, penFontSize, penIsUp))
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realRestoreStyle(): Unit = {
    // Eşleşen saveStyle olmadan çağrılırsa pop fırlatır; istisna queueHandler'ın
    // içinde olduğu için scheduleLater'a HİÇ ulaşılmaz ve komut pompası ölür --
    // sonraki bütün kaplumbağa komutları sessizce hiçbir şey yapmaz.
    if (savedStyles.isEmpty) {
      kojoWorld.scheduleLater(queueHandler)
      return
    }
    pushQ()
    val (color, fill, width, fontSize, penWasUp) = savedStyles.pop()
    setPenColor(color)
    setFillColor(fill)
    setPenThickness(width)
    setPenFontSize(fontSize)
    if (penWasUp) penUp() else penDown()
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realClear(): Unit = {
    turtlePath.clear()
    turtlePathPoints.clear()
    initTurtleLayer()
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realPause(seconds: Double): Unit = {
    val t0 = window.performance.now()
    def pump(frameTime: Double): Unit = {
      if (frameTime - t0 > seconds * 1000) {
        kojoWorld.scheduleLater(queueHandler)
      }
      else {
        window.requestAnimationFrame(pump)
      }
    }
    pump(t0)
  }

  private def realPenUpDown(up: Boolean): Unit = {
    if (up) {
      penIsUp = true
    }
    else {
      penIsUp = false
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realInvisible(): Unit = {
    turtleImage.visible = false
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realVisible(): Unit = {
    turtleImage.visible = true
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def distanceTo(x: Double, y: Double): Double = {
    TurtleHelper.distance(position.x, position.y, x, y)
  }

  private def towardsHelper(x: Double, y: Double): Double = {
    TurtleHelper.thetaTowards(position.x, position.y, x, y, headingRadians)
  }

  private def commandQ = commandQs.head

  private def pushQ(): Unit = {
    commandQs = mutable.Queue.empty[Command] :: commandQs
  }

  private def popQ(): Unit = {
    commandQ.enqueue(PopQ)
  }

  private def realPopQ(): Unit = {
    assert(commandQ.size == 0)
    commandQs = commandQs.tail
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSync(fn: () => Unit) = {
    fn()
    kojoWorld.scheduleLater(queueHandler)
  }
}
