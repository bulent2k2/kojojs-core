package com.vividsolutions.jts.geom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("jsts.geom.GeometryFactory")
class GeometryFactory extends js.Object {
  def createLineString(coords: js.Array[Coordinate]): LineString = js.native
  // jsts'te var (GeometryFactory.createPoint(Coordinate)), burada bildirilmemişti.
  // TurkishTurtle.dokunuyorMu kaplumbağanın konumunu nokta geometrisine çevirmek
  // için kullanıyor.
  def createPoint(coord: Coordinate): Geometry = js.native
  def createPolygon(coords: js.Array[Coordinate]): Geometry = js.native
  def createGeometryCollection(geometries: js.Array[Geometry]): Geometry = js.native
}
