package libtessjs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/**
 * libtess.js cephesi -- GLU tessellator'ın JS'e taşınmış hâli.
 *
 * NEDEN VAR: PIXI `Graphics` çokgenleri earcut ile üçgenliyor ve earcut'ın
 * kendi belgesi girdinin BASİT (kendini kesmeyen) olduğunu varsaydığını, aksi
 * hâlde sonucun "noticeably wrong" olabileceğini söylüyor. Masaüstü Kojo ise
 * `Path2D.Double` + `Graphics2D.fill` kullanıyor, yani NON_ZERO sarım kuralı.
 * Ayrıntı ve ölçümler: oneri-kesisen-dolgu.md.
 *
 * SÜRÜM: 1.2.2 (19 Aralık 2015; kütüphane bakımsız ama işi dar ve donmuş).
 * Lisans: SGI Free Software License B 2.0 -- metni Expat/MIT ile aynı, deponun
 * GPLv3'üyle uyumlu. Telif notu lib/libtess-LICENSE.txt'te.
 *
 * DİKKAT -- neden `libtess.cat.js`, `libtess.min.js` değil: yayımlanmış
 * küçültülmüş yapı Closure çıktısı ve `window`a TEK HARFLİ 70 küresel ad
 * bırakıyor (`t`, `H`, `W`, `n`, `x`, `z`, ...). Ölçüldü: cat.js yalnız
 * `libtess`i tanımlıyor, min.js 70 ad. Sayfadaki sıradan bir `var W` bile
 * kütüphaneyi içeriden patlatıyor.
 */
@js.native
@JSGlobal("libtess")
object libtess extends js.Object {
  def GluTesselator: js.Dynamic = js.native
  val gluEnum: GluEnum = js.native
  val windingRule: WindingRule = js.native
  val primitiveType: PrimitiveType = js.native
}

@js.native
trait GluEnum extends js.Object {
  val GLU_TESS_WINDING_RULE: Double = js.native
  val GLU_TESS_BEGIN: Double = js.native
  val GLU_TESS_END: Double = js.native
  val GLU_TESS_ERROR: Double = js.native
  val GLU_TESS_COMBINE: Double = js.native
  val GLU_TESS_VERTEX_DATA: Double = js.native
}

@js.native
trait WindingRule extends js.Object {
  val GLU_TESS_WINDING_ODD: Double = js.native
  val GLU_TESS_WINDING_NONZERO: Double = js.native
}

@js.native
trait PrimitiveType extends js.Object {
  val GL_TRIANGLES: Double = js.native
}
