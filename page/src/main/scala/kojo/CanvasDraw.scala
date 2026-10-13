package kojo

import kojo.doodle.Color
import org.scalajs.dom

import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

/**
 * Eskiz yüzeyi: masaüstü Kojo'nun `CanvasDraw`ının (lite/CanvasDraw.scala) tarayıcı karşılığı.
 * Java2D `Graphics2D` yerine bir HTML `canvas`ın 2B bağlamına çizer. Eskiz tek bir resim ve
 * çizilenler PİKSEL: nesne birikmiyor, bu yüzden istenildiği kadar çizilebilir
 * (bkz. oneri-eskiz.md, SketchPic.scala).
 *
 * KOORDİNATLAR -- masaüstüyle aynı, ölçüldü (Java2DPic): `Graphics2D` ham görüntü
 * koordinatlarında (y aşağı); görüntü, y'si YUKARI olan sahneye `drawImage(img, 0, 0)` ile
 * konuyor, yani görüntünün i. satırı sahnede y = i'de. Sonuç: eskizde (x, y) sahnenin (x, y)
 * noktası, ama görüntü sahnede baş aşağı. Biz de aynısını yapıyoruz: bağlam ham, sprite
 * dönüştürülmüyor (bkz. SketchPic). Yazı baş aşağı görünmesin diye masaüstü gibi
 * `scale(1, -1)` ile çiziliyor.
 *
 * Masaüstüyle aynı kalan davranışlar (CanvasDraw.scala'dan okundu, sınamayla çivilendi):
 *  - `rect(x1, y1, x2, y2)`: 3. ve 4. değer köşe DEĞİL, genişlik ve yükseklik (Rectangle2D.setRect).
 *  - `ellipse(cx, cy, w, h)`: w, h TAM boy (Canvas yarıçap ister: yarısı veriliyor).
 *  - `arc`: açılar RADYAN; Java'nın `Arc2D.OPEN` + ham (y aşağı) koordinat anlamı: 0 saat 3'te,
 *    artan açı ekranda saat yönünün TERSİNE (Canvas'ta açı = -a, `anticlockwise` = uzunluk > 0).
 *  - fırça ucu/birleşimi sabitleri: `ROUND`, `PROJECT` (= kare uç), `SQUARE` (= düz uç!) --
 *    masaüstündeki adlar bilerek korunuyor (CAP_SQUARE/CAP_BUTT karışıklığı).
 *  - `pushMatrix`/`popMatrix` yalnız DÖNÜŞÜMÜ saklar (`save`/`restore` boya ve fırçayı da
 *    saklardı, masaüstü saklamıyor).
 *  - `background` (artalan) dönüşümün tersiyle (0,0)-(w,h) köşelerini taşıyıp dikdörtgen doldurur.
 *
 * Ayrılan yerler: `drawShape(java.awt.Shape)` yok (AWT yok); `blendMode` `java.awt.Composite`
 * yerine Canvas'ın `globalCompositeOperation` adını (`"lighter"`, `"multiply"`, ...) alıyor;
 * `image` henüz yok.
 */
class CanvasDraw private[kojo] (
    canvas: dom.html.Canvas,
    val width: Double,
    val height: Double,
    seedFn: Long => Unit
) {
  private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val c = ctx.asInstanceOf[js.Dynamic]

  // masaüstü: CapJoin.CAP_ROUND / CAP_SQUARE / CAP_BUTT, JOIN_MITER / JOIN_BEVEL
  val ROUND = 1
  val PROJECT = 2
  val SQUARE = 0
  val MITER = 0
  val BEVEL = 2

  private var fillCss: String = null
  private var strokeCss: String = Color.red.toCanvas
  private var strokeThickness = 2.0
  private var textFontValue: Font = Font("Sans Serif", 15)
  private var matrices = List.empty[js.Array[Double]]
  private var _loop = true

  // masaüstü varsayılanları: ROUND uç, MITER birleşim, kalınlık 2
  c.lineCap = "round"
  c.lineJoin = "miter"
  c.lineWidth = strokeThickness

  def loop: Boolean = _loop
  def noLoop(): Unit = { _loop = false }

  def randomSeed(s: Long): Unit = seedFn(s)

  // --- boya ve fırça ---------------------------------------------------------
  private def rgbaCss(r: Int, g: Int, b: Int, a: Int): String = s"rgba($r, $g, $b, ${a / 255.0})"

  def noStroke(): Unit = { strokeCss = null }
  def noFill(): Unit = { fillCss = null }
  def fill(r: Int, g: Int, b: Int, a: Int = 255): Unit = { fillCss = rgbaCss(r, g, b, a) }
  def fill(col: Color): Unit = { fillCss = col.toCanvas }
  def stroke(r: Int, g: Int, b: Int, a: Int = 255): Unit = { strokeCss = rgbaCss(r, g, b, a) }
  def stroke(col: Color): Unit = { strokeCss = col.toCanvas }

  def strokeWeight(n: Double): Unit = {
    strokeThickness = n
    c.lineWidth = n
  }

  def strokeCap(n: Int): Unit = {
    c.lineCap = n match {
      case 0 => "butt"
      case 1 => "round"
      case 2 => "square"
      case _ => throw new IllegalArgumentException(s"unknown stroke cap: $n")
    }
  }

  def strokeJoin(n: Int): Unit = {
    c.lineJoin = n match {
      case 0 => "miter"
      case 1 => "round"
      case 2 => "bevel"
      case _ => throw new IllegalArgumentException(s"unknown stroke join: $n")
    }
  }

  /** `mode`: Canvas `globalCompositeOperation` adı ("source-over", "lighter", "multiply", ...). */
  def blendMode(mode: String): Unit = { c.globalCompositeOperation = mode }

  // --- artalan ---------------------------------------------------------------
  private def withCurrentPaint(css: String)(f: => Unit): Unit = {
    c.fillStyle = css
    f
  }

  def background(r: Int, g: Int, b: Int, a: Int = 255): Unit = backgroundCss(rgbaCss(r, g, b, a))
  def background(col: Color): Unit = backgroundCss(col.toCanvas)

  // masaüstü bgRectCoords: (0,0) ve (w,h) köşelerini dönüşümün TERSİYLE taşı, aradaki
  // dikdörtgeni doldur. Dönüşüm döndürülmüşse yalnız bu iki köşe kullanılır (masaüstüyle aynı).
  private def backgroundCss(css: String): Unit = {
    val m = c.getTransform()
    val inv = m.inverse()
    val p0 = inv.transformPoint(js.Dynamic.literal(x = 0.0, y = 0.0))
    val p1 = inv.transformPoint(js.Dynamic.literal(x = width, y = height))
    val x0 = p0.x.asInstanceOf[Double]; val y0 = p0.y.asInstanceOf[Double]
    val x1 = p1.x.asInstanceOf[Double]; val y1 = p1.y.asInstanceOf[Double]
    withCurrentPaint(css) { ctx.fillRect(x0, y0, x1 - x0, y1 - y0) }
  }

  // --- şekiller --------------------------------------------------------------
  /** Yol kurulduktan sonra: dolgu (varsa), sonra fırça (varsa) -- masaüstü drawShape sırası. */
  private def paint(): Unit = {
    if (fillCss != null) {
      c.fillStyle = fillCss
      ctx.fill()
    }
    if (strokeCss != null) {
      c.strokeStyle = strokeCss
      ctx.stroke()
    }
  }

  def ellipse(cx: Double, cy: Double, w: Double, h: Double): Unit = {
    ctx.beginPath()
    c.ellipse(cx, cy, w / 2, h / 2, 0.0, 0.0, 2 * math.Pi)
    paint()
  }

  /** `start`, `extent` radyan; çerçeve ellipse() ile aynı (merkez, TAM en, TAM boy). */
  def arc(cx: Double, cy: Double, w: Double, h: Double, start: Double, extent: Double): Unit = {
    ctx.beginPath()
    c.ellipse(cx, cy, w / 2, h / 2, 0.0, -start, -(start + extent), extent > 0)
    paint()
  }

  def line(x1: Double, y1: Double, x2: Double, y2: Double): Unit = {
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    paint()
  }

  /** Dikkat: 3. ve 4. değer genişlik ve yükseklik (köşe değil), masaüstüyle aynı. */
  def rect(x1: Double, y1: Double, x2: Double, y2: Double): Unit = {
    ctx.beginPath()
    ctx.rect(x1, y1, x2, y2)
    paint()
  }

  def point(x: Double, y: Double): Unit = line(x - 0.01 / 2, y, x + 0.01 / 2, y)

  // --- yazı ------------------------------------------------------------------
  def textFont(f: Font): Unit = { textFontValue = f }

  private def fontCss(f: Font): String = {
    val family = f.name.replace(" ", "").toLowerCase match {
      case "sansserif" | "dialog" | "dialoginput" => "sans-serif"
      case "serif"                                => "serif"
      case "monospaced"                           => "monospace"
      case _                                      => "\"" + f.name + "\""
    }
    val bold = if ((f.style & 1) != 0) "bold " else ""
    val italic = if ((f.style & 2) != 0) "italic " else ""
    s"$italic$bold${f.size}px $family"
  }

  // masaüstü drawText: yazı FIRÇA renginde, y çevrilerek (baş aşağı sahne) çiziliyor.
  def text(s: String, x: Double, y: Double): Unit = {
    if (strokeCss != null) c.fillStyle = strokeCss
    c.font = fontCss(textFontValue)
    pushMatrix()
    scale(1, -1)
    ctx.fillText(s, x, -y)
    popMatrix()
  }

  // --- dönüşümler ------------------------------------------------------------
  def translate(x: Double, y: Double): Unit = ctx.translate(x, y)
  def rotate(angle: Double): Unit = ctx.rotate(angle)
  def rotateDegrees(angle: Double): Unit = rotate(angle.toRadians)
  def scale(f: Double): Unit = ctx.scale(f, f)
  def scale(fx: Double, fy: Double): Unit = ctx.scale(fx, fy)

  def pushMatrix(): Unit = {
    val m = c.getTransform()
    matrices = js.Array[Double](
      m.a.asInstanceOf[Double], m.b.asInstanceOf[Double], m.c.asInstanceOf[Double],
      m.d.asInstanceOf[Double], m.e.asInstanceOf[Double], m.f.asInstanceOf[Double]
    ) :: matrices
  }

  def popMatrix(): Unit = {
    val m = matrices.head
    ctx.setTransform(m(0), m(1), m(2), m(3), m(4), m(5))
    matrices = matrices.tail
  }

  // --- köşe noktalarıyla şekil (vertexShapeSupport.scala'nın Canvas karşılığı) --
  private sealed trait ShapeVertex
  private case class Vertex(x: Double, y: Double) extends ShapeVertex
  private case class CurveVertex(x: Double, y: Double) extends ShapeVertex
  private case class QuadVertex(x1: Double, y1: Double, x2: Double, y2: Double) extends ShapeVertex
  private case class BezierVertex(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double)
      extends ShapeVertex
  private case class ArcVertex(x: Double, y: Double, angle: Double) extends ShapeVertex

  private var shapeVertices: ArrayBuffer[ShapeVertex] = _

  private def checkBegin(): Unit =
    require(shapeVertices != null, "Do a beginShape() before adding vertices to a shape")

  def beginShape(): Unit = { shapeVertices = ArrayBuffer.empty[ShapeVertex] }
  def vertex(x: Double, y: Double): Unit = { checkBegin(); shapeVertices.append(Vertex(x, y)) }
  def quadraticVertex(cx: Double, cy: Double, x2: Double, y2: Double): Unit = {
    checkBegin(); shapeVertices.append(QuadVertex(cx, cy, x2, y2))
  }
  def bezierVertex(cx1: Double, cy1: Double, cx2: Double, cy2: Double, x2: Double, y2: Double): Unit = {
    checkBegin(); shapeVertices.append(BezierVertex(cx1, cy1, cx2, cy2, x2, y2))
  }
  def curveVertex(x: Double, y: Double): Unit = { checkBegin(); shapeVertices.append(CurveVertex(x, y)) }
  def arcVertex(x: Double, y: Double, angle: Double): Unit = {
    checkBegin(); shapeVertices.append(ArcVertex(x, y, angle))
  }
  def vertexRt(r: Double, theta: Double): Unit = {
    val t = theta.toRadians
    vertex(r * math.cos(t), r * math.sin(t))
  }
  def curveVertexRt(r: Double, theta: Double): Unit = {
    val t = theta.toRadians
    curveVertex(r * math.cos(t), r * math.sin(t))
  }

  def endShape(): Unit = {
    require(shapeVertices != null && shapeVertices.nonEmpty, "A shape should have at least one vertex")
    var firstVertex = true
    var curX = 0.0
    var curY = 0.0
    def checkCurveFirstVertex(): Unit =
      if (firstVertex) throw new RuntimeException("A curved shape should start with a vertex")

    val coordX = new Array[Float](4)
    val coordY = new Array[Float](4)
    val drawX = new Array[Float](4)
    val drawY = new Array[Float](4)
    val curveVertices = ArrayBuffer.empty[CurveVertex]

    ctx.beginPath()
    shapeVertices.foreach {
      case Vertex(x, y) =>
        if (firstVertex) { ctx.moveTo(x, y); firstVertex = false }
        else ctx.lineTo(x, y)
        curX = x; curY = y
      case cv @ CurveVertex(_, _) =>
        curveVertices.append(cv)
        val n = curveVertices.length
        if (n > 3) {
          var i = 0
          while (i < 4) {
            coordX(i) = curveVertices(n - 4 + i).x.toFloat
            coordY(i) = curveVertices(n - 4 + i).y.toFloat
            i += 1
          }
          VertexShapeSupport.curveToBezierMatrix.mult(coordX, drawX)
          VertexShapeSupport.curveToBezierMatrix.mult(coordY, drawY)
          if (firstVertex) { ctx.moveTo(drawX(0), drawY(0)); firstVertex = false }
          ctx.bezierCurveTo(drawX(1), drawY(1), drawX(2), drawY(2), drawX(3), drawY(3))
          curX = drawX(3); curY = drawY(3)
        }
      case QuadVertex(x1, y1, x2, y2) =>
        checkCurveFirstVertex(); ctx.quadraticCurveTo(x1, y1, x2, y2); curX = x2; curY = y2
      case BezierVertex(x1, y1, x2, y2, x3, y3) =>
        checkCurveFirstVertex(); ctx.bezierCurveTo(x1, y1, x2, y2, x3, y3); curX = x3; curY = y3
      case ArcVertex(x, y, angle) =>
        checkCurveFirstVertex()
        arcTo(curX, curY, x, y, angle)
        curX = x; curY = y
    }
    shapeVertices = null
    paint()
  }

  // masaüstü Rich2DPath.arc: (curX,curY)'den (x,y)'ye `angleOfArc` derecelik dairesel yay.
  private def arcTo(x1: Double, y1: Double, x2: Double, y2: Double, angleOfArc: Double): Unit = {
    val arcAngle = -(angleOfArc % 360)
    val mx = (x1 + x2) / 2
    val my = (y1 + y2) / 2
    val dx = (x2 - x1) / 2
    val dy = (y2 - y1) / 2
    val len = math.hypot(dx, dy)
    val ux = dx / len
    val uy = dy / len
    val lpx = uy
    val lpy = -ux
    val t =
      if (math.abs(arcAngle) == 0.0) len
      else if (math.abs(arcAngle) == 180.0) 0.0
      else len / math.tan(math.toRadians(arcAngle) / 2.0)
    val cx = mx + lpx * t
    val cy = my + lpy * t
    val radius = math.hypot(cx - x1, cy - y1)
    // Java: başlangıç açısı derece, saat yönünün tersine, ham koordinatta = -atan2; Canvas'ta açı = atan2
    val startAngle = math.atan2(y1 - cy, x1 - cx)
    c.ellipse(cx, cy, radius, radius, 0.0, startAngle, startAngle - math.toRadians(arcAngle), arcAngle > 0)
  }
}
