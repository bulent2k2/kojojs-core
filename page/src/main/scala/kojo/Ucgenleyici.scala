/*
 * Copyright (C) 2026 Lalit Pant <pant.lalit@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package kojo

import libtessjs.libtess

import scala.scalajs.js

/**
 * Bir çokgeni NON_ZERO sarım kuralıyla üçgenler.
 *
 * NEDEN VAR: masaüstü Kojo dolguyu `Path2D.Double` + `Graphics2D.fill` ile
 * yapıyor, yani varsayılan `WIND_NON_ZERO`. ikojo ise PIXI `Graphics`e
 * bırakıyordu, o da earcut kullanıyor ve earcut BASİT (kendini kesmeyen)
 * çokgen varsayıyor. `tan-theta.kojo` gibi kendi üstünden geçen yollarda iki
 * taraf farklı şekil çiziyordu.
 *
 * ÖLÇÜM (oneri-kesisen-dolgu.md): R=100 beş köşeli yıldızın NON_ZERO alanı
 * kapalı formülle 11225.6994; libtess 11225.7, earcut 21266.3 (1.894x fazla).
 * tan-theta'yı masaüstü referansına karşı piksel piksel: earcut %50.07 farklı,
 * libtess %0.21 (yalnız kenar yumuşatma).
 *
 * ÇIKTI BİÇİMİ: düz bir dizi -- her ardışık ALTI sayı bir üçgen
 * (x1,y1, x2,y2, x3,y3). PIXI'ye üçgen üçgen `drawPolygon` ile veriliyor;
 * ölçüldü ki tek bir Mesh'ten ucuz kurulup daha hızlı render oluyor ve
 * doku/gradyan dolgusu üçgen sınırlarını aşarak SÜREKLİ eşleniyor (doku
 * dolgusu dünya uzayında, şekil başına değil).
 */
object Üçgenleyici {

  private var uyarıldı = false
  private var tipUyarıldı = false
  private var hataUyarıldı = false

  /**
   * Kütüphane sayfada yüklü mü.
   *
   * NEDEN GEREKLİ: libtess'i yükleyen sayfalar İKİ DEPODA duruyor
   * (kojojs-dev'de run.html/run5.html, kojojs-editor'da resultframe) ve CI
   * depolar arasını göremiyor. Biri unutulursa küresel `libtess` adı bulunamaz.
   * O durumda ÇÖKMEK yerine earcut'a düşüyoruz: kendini kesen yollar yanlış
   * dolar ama öteki bütün çizim yaşar. Sessiz kalmıyoruz -- konsola bir kez
   * hata basılıyor, çünkü bu ayarlanması gereken gerçek bir eksik.
   */
  def kullanılabilir: Boolean = {
    // DİKKAT: `js.isUndefined(js.Dynamic.global.libtess)` KULLANMAYIN.
    // Scala.js onu çıplak bir `libtess` erişimine indirgiyor ve tanımsız bir
    // küresel ada erişmek JS'te ReferenceError atıyor -- yani "yok mu" diye
    // sorarken çöküyorduk (ölçüldü). `typeof` tanımsız adda atmıyor.
    val var_mı = js.typeOf(js.Dynamic.global.libtess) != "undefined"
    if (!var_mı && !uyarıldı) {
      uyarıldı = true
      js.Dynamic.global.console.error(
        "libtess yüklü değil: kendini kesen yolların dolgusu YANLIŞ olacak " +
          "(NON_ZERO yerine earcut). Sayfa libtess.cat.js'i yüklemeli."
      )
    }
    var_mı
  }

  /**
   * `düz` = x0,y0, x1,y1, ... nokta listesi. Dönen dizi üçgen köşeleri.
   *
   * Üçten az nokta varsa alan yok: boş dizi.
   */
  def nonzero(düz: Array[Double]): Array[Double] = {
    if (düz.length < 6) return Array.empty[Double]

    val çıktı = js.Array[Double]()
    val ts = js.Dynamic.newInstance(libtess.GluTesselator)()

    // GLU_TESS_BEGIN kayıtlı DEĞİLSE kütüphane çağrı sırasında patlıyor.
    // GLU_TESS_COMBINE de şart: kesişme noktalarında yeni köşe üretiliyor --
    // zaten bütün mesele o.
    val köşeGeldi: js.Function2[js.Array[Double], js.Array[Double], Unit] =
      (d, birikim) => { birikim.push(d(0), d(1)); () }
    val boşluk: js.Function0[Unit] = () => ()
    val birleştir: js.Function1[js.Array[Double], js.Array[Double]] =
      c => js.Array(c(0), c(1), c(2))

    // BEGIN yalnız kaydolmuş olmak için değil: çıktıyı ardışık ALTILI olarak
    // bağımsız üçgenlere bölmemiz, kütüphanenin GL_TRIANGLES döndürmesine
    // dayanıyor. Kaynakta bu garanti var ama KOŞULLU:
    //   libtess.cat.js:128  "GL_TRIANGLE_STRIP and GL_TRIANGLE_FAN are no
    //                        longer returned since 1.1.0"
    //   libtess.cat.js:1411 GL_TRIANGLES ile çağrılan yer
    //   libtess.cat.js:1453 GL_LINE_LOOP -- GLU_TESS_BOUNDARY_ONLY açıkken
    // Biz BOUNDARY_ONLY'yi açmıyoruz, ama biri açarsa çıktı köşe döngüsü olur
    // ve altılı bölme sessizce saçmalar. Varsayımı yorumla değil SAVLA
    // tutuyoruz: bir daha olursa konsolda görünür.
    val başladı: js.Function1[Double, Unit] = tip => {
      if (tip != libtess.primitiveType.GL_TRIANGLES && !tipUyarıldı) {
        tipUyarıldı = true
        js.Dynamic.global.console.error(
          "libtess GL_TRIANGLES dışında bir ilkel döndürdü (" + tip +
            "). Dolgu altılı üçgen varsayımına dayanıyor; çıktı yanlış olacak."
        )
      }
      ()
    }

    // Hata geri çağırması KAYDEDİLMEZSE kütüphane hatayı sessizce yutuyor
    // (libtess.cat.js:3829 callErrorCallback -- errorCallback_ yoksa hiçbir şey
    // yapmıyor). Üçgenleme bir hata verirse dolgu boş ya da yanlış çıkar ve
    // hiçbir yerde iz kalmazdı.
    // Bir kez bas: canlandırma içinde bozuk bir yol her karede hata verir ve
    // susturmasız bir ileti konsolu doldurup asıl iletiyi görünmez kılar.
    // `uyarıldı` ve `tipUyarıldı` ile aynı kalıp.
    val hataOldu: js.Function1[Double, Unit] = e => {
      if (!hataUyarıldı) {
        hataUyarıldı = true
        js.Dynamic.global.console.error("libtess üçgenleme hatası: " + e)
      }
      ()
    }

    ts.gluTessCallback(libtess.gluEnum.GLU_TESS_VERTEX_DATA, köşeGeldi)
    ts.gluTessCallback(libtess.gluEnum.GLU_TESS_BEGIN, başladı)
    ts.gluTessCallback(libtess.gluEnum.GLU_TESS_END, boşluk)
    ts.gluTessCallback(libtess.gluEnum.GLU_TESS_COMBINE, birleştir)
    ts.gluTessCallback(libtess.gluEnum.GLU_TESS_ERROR, hataOldu)
    ts.gluTessProperty(libtess.gluEnum.GLU_TESS_WINDING_RULE, libtess.windingRule.GLU_TESS_WINDING_NONZERO)
    ts.gluTessNormal(0, 0, 1)

    ts.gluTessBeginPolygon(çıktı)
    ts.gluTessBeginContour()
    var i = 0
    while (i < düz.length) {
      val v = js.Array(düz(i), düz(i + 1), 0.0)
      ts.gluTessVertex(v, js.Array(düz(i), düz(i + 1)))
      i += 2
    }
    ts.gluTessEndContour()
    ts.gluTessEndPolygon()

    çıktı.toArray
  }

  /** Üçgen köşelerinin kapladığı toplam alan -- sınamalar için. */
  def alan(üçgenler: Array[Double]): Double = {
    var a = 0.0
    var i = 0
    while (i + 5 < üçgenler.length) {
      val x1 = üçgenler(i); val y1 = üçgenler(i + 1)
      val x2 = üçgenler(i + 2); val y2 = üçgenler(i + 3)
      val x3 = üçgenler(i + 4); val y3 = üçgenler(i + 5)
      a += math.abs((x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1)) / 2
      i += 6
    }
    a
  }
}
