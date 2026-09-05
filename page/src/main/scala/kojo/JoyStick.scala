package kojo

import kojo.syntax.Builtins

class JoyStick(radius: Double)(builtins: Builtins) {
  import builtins._
  val perimeter = Picture.circle(radius)
  perimeter.setFillColor(ColorMaker.rgb(120, 120, 120).fadeOut(0.2))
  perimeter.setPenColor(cm.black)
  perimeter.setPenThickness(4)

  val control = Picture.circle(radius / 2)
  control.setPenColor(noColor)
  control.setFillColor(ColorMaker.white.fadeOut(0.2))

  val origin = Picture.circle(radius / 5)

  def setPerimeterColor(c: Color): Unit = {
    perimeter.setFillColor(c)
  }

  def setPerimeterPenColor(c: Color): Unit = {
    perimeter.setPenColor(c)
  }

  def setControlColor(c: Color): Unit = {
    control.setFillColor(c)
  }

  val zeroVec = Vector2D(0, 0)
  private var currentVec = zeroVec
  perimeter.onMouseDrag { (x, y) =>
    val op = origin.position
    val dx = x - op.x
    val dy = y - op.y
    val vec = Vector2D(dx, dy).limit(radius / 2)
    control.setPosition(op.x + vec.x, op.y + vec.y)
    currentVec = vec
  }
  perimeter.onMouseRelease { (x, y) =>
    val op = origin.position
    control.setPosition(op.x, op.y)
    currentVec = zeroVec
  }
  control.forwardInputTo(perimeter)

  def draw(): Unit = {
    perimeter.draw()
    control.draw()
  }

  def setPosition(x: Double, y: Double): Unit = setPostiion(x, y)
  def setPostiion(x: Double, y: Double): Unit = {
    perimeter.setPosition(x, y)
    control.setPosition(x, y)
    origin.setPosition(x, y)
  }

  def currentVector = currentVec

  def movePlayerHelper(player: Picture, scaleVelocity: Double = 1, directionConstraint: kojo.Vector2D = null) = {
    val vel = if (directionConstraint == null)
      currentVector * scaleVelocity else currentVector.project(directionConstraint) * scaleVelocity
    player.offset(vel)
    vel
  }

  def movePlayer(player: Picture, scaleVelocity: Double = 1, directionConstraint: kojo.Vector2D = null): Unit = {
    movePlayerHelper(player, scaleVelocity, directionConstraint)
  }

  def movePlayerWithinStage(player: Picture, scaleVelocity: Double = 1, directionConstraint: kojo.Vector2D = null): Unit = {
    movePlayerHelper(player, scaleVelocity, directionConstraint)

    // Oyuncuyu sahne sınırları içine TEK ADIMDA kıstır -- döngü ve collidesWith
    // YOK. Eski "while (collidesWith) offset(...)" döngüsü, kare içinde bayat kalan
    // localTransform yüzünden hiç "çıktı" demiyordu: sonsuz döngü (tüm sayfa donar)
    // ya da bir guard'la itip resmi sahne dışına yolluyordu (yok olur).
    // Picture.preDrawHook artık matrisi her değişimde tazelediği için `bounds`
    // TAZE; kenarları doğrudan ondan alırız -- merkezli-resim varsayımı yok, yani
    // kaplumbağa-çizimli (Resim { … }) oyuncular da doğru kıstırılır.
    val cb = canvasBounds
    val b = player.bounds
    val dx = math.max(cb.x - b.x, math.min(0.0, cb.x + cb.width - (b.x + b.width)))
    val dy = math.max(cb.y - b.y, math.min(0.0, cb.y + cb.height - (b.y + b.height)))
    if (dx != 0 || dy != 0) player.offset(dx, dy)
  }
}
