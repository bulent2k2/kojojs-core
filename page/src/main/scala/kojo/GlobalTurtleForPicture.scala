package kojo

import kojo.Speed.Speed
import kojo.doodle.Color

class GlobalTurtleForPicture extends TurtleAPI {
  var globalTurtle: Turtle = _
  def forward(n: Double): Unit = globalTurtle.forward(n)
  def hop(n: Double): Unit = globalTurtle.hop(n)
  def turn(angle: Double): Unit = globalTurtle.turn(angle)
  def setAnimationDelay(delay: Long): Unit = globalTurtle.setAnimationDelay(delay)
  def setPenThickness(t: Double): Unit = globalTurtle.setPenThickness(t)
  def setPenColor(color: Color): Unit = globalTurtle.setPenColor(color)
  def setPenFontSize(n: Int): Unit = globalTurtle.setPenFontSize(n)
  override def setPenFontFamily(name: String): Unit = globalTurtle.setPenFontFamily(name)
  override def dot(diameter: Int): Unit = globalTurtle.dot(diameter)
  def setFillColor(color: Color): Unit = globalTurtle.setFillColor(color)
  def setPosition(x: Double, y: Double): Unit = globalTurtle.setPosition(x, y)
  def changePosition(x: Double, y: Double): Unit = globalTurtle.changePosition(x, y)
  def penIsDown: Boolean = globalTurtle.penIsDown
  def animationDelayMs: Long = globalTurtle.animationDelayMs
  def setCostume(url: String): Unit = globalTurtle.setCostume(url)
  def setCostumes(urls: String*): Unit = globalTurtle.setCostumes(urls: _*)
  def nextCostume(): Unit = globalTurtle.nextCostume()
  def scaleCostume(factor: Double): Unit = globalTurtle.scaleCostume(factor)
  def setHeading(theta: Double): Unit = globalTurtle.setHeading(theta)
  def moveTo(x: Double, y: Double): Unit = globalTurtle.moveTo(x, y)
  def arc2(r: Double, a: Double): Unit = globalTurtle.arc2(r, a)
  def write(text: String): Unit = globalTurtle.write(text)
  def towards(x: Double, y: Double): Unit = globalTurtle.towards(x, y)
  def savePosHe(): Unit = globalTurtle.savePosHe()
  def restorePosHe(): Unit = globalTurtle.restorePosHe()
  def saveStyle(): Unit = globalTurtle.saveStyle()
  def restoreStyle(): Unit = globalTurtle.restoreStyle()
  override def home(): Unit = globalTurtle.home()
  def clear(): Unit = globalTurtle.clear()
  def cleari(): Unit = globalTurtle.cleari()
  def pause(seconds: Double): Unit = globalTurtle.pause(seconds)
  def penUp(): Unit = globalTurtle.penUp()
  def penDown(): Unit = globalTurtle.penDown()
  def invisible(): Unit = globalTurtle.invisible()
  def visible(): Unit = globalTurtle.visible()

  def right(): Unit = globalTurtle.right()
  def left(): Unit = globalTurtle.left()
  def right(angle: Double): Unit = globalTurtle.right(angle)
  def left(angle: Double): Unit = globalTurtle.left(angle)
  def back(n: Double): Unit = globalTurtle.back(n)
  def forward(): Unit = globalTurtle.forward()
  def hop(): Unit = globalTurtle.hop()
  def back(): Unit = globalTurtle.back()
  def setSpeed(speed: Speed): Unit = globalTurtle.setSpeed(speed)
  def left(angle: Double, rad: Double): Unit = globalTurtle.left(angle, rad)
  def right(angle: Double, rad: Double): Unit = globalTurtle.right(angle, rad)
  def turn(angle: Double, rad: Double): Unit = globalTurtle.turn(angle, rad)
  def arc(r: Double, a: Double): Unit = globalTurtle.arc(r, a)
  def circle(r: Double): Unit = globalTurtle.circle(r)
}
