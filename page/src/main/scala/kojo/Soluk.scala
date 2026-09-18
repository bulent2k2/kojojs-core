package kojo

import scala.scalajs.js

/**
 * `soluk(n)` / `fade(n)`: resmi ÜSTTEN AŞAĞI n piksel boyunca söndürür, n'den
 * aşağısını hiç çizmez.
 *
 * Masaüstü Kojo'daki karşılığı `picture.fade` (picimage.scala'daki
 * `FadeImageOp`): resmi bir BufferedImage'a çizip alfa maskesi uyguluyor --
 * 0. satırda alfa 1, n. satırda 0, altında 0. Java2D'de y AŞAĞI büyüdüğü için
 * "0. satır" resmin GÖRSEL ÜSTÜ. Burada da öyle.
 *
 * NEDEN SÜZGEÇ (filter), MASKE DEĞİL: PIXI'de Graphics maskesi ikili
 * (stencil) -- gradyan alfa veremiyor. Sprite maskesi verebiliyor ama doku
 * üretmek, sahne grafiğine yerleştirmek ve y-ters sahne dönüşümünü elde
 * çözmek gerekiyordu. Süzgeç bunların hiçbirini istemiyor: PIXI resmi zaten
 * kendi sınırları kadar bir ara dokuya çiziyor ve `vTextureCoord` o dokunun
 * ÜSTÜNDEN başlıyor -- masaüstündeki satır düzeninin aynısı. Ölçüldü
 * (2026-09, PIXI 5.3.12, Chromium/SwiftShader; boy=100, mavi dikdörtgen):
 *   satır  10 -> alfa 231     (beklenen 255*(1-10/100)  = 229.5)
 *   satır  25 -> alfa 193     (beklenen 191.2)
 *   satır  50 -> alfa 129     (beklenen 127.5)
 *   satır  75 -> alfa  65     (beklenen 63.7)
 *   satır  99 -> alfa   4     (beklenen 2.5)
 *   satır 110 -> alfa   0     (n'den aşağısı silinmiş)
 * Yarım pikselik +1.5'lik sapma örnekleme merkezinden geliyor.
 *
 * DİKKAT -- `highp`: `inputSize` PIXI'nin VARSAYILAN köşe (vertex) kabuğunda
 * da bildirilmiş ve orada highp. Parça (fragment) kabuğunun varsayılan
 * duyarlığı mediump; aynı uniform iki kabukta FARKLI duyarlıkla bildirilirse
 * GLSL ES bağlamayı reddediyor. Ölçüldü: `uniform vec4 inputSize;` ile
 * "Could not initialize shader" (gl.VALIDATE_STATUS false, gl.getError() 0),
 * `uniform highp vec4 inputSize;` ile sorunsuz. Duyarlık niteleyicisini
 * SİLMEYİN.
 */
object Soluk {
  // n EKRAN PİKSELİDİR, resmin kendi birimi değil: `y` süzgecin giriş
  // dokusundan (ekran uzayı) okunuyor. Ölçüldü (SolukTest): ekranda aynı boyu
  // kaplayan iki resimden biri `büyüt(2)` ile büyütülmüş olsa da şerit ikisinde
  // de 100 ekran pikseli. Masaüstüyle bu noktada KARŞILAŞTIRILMADI.
  //
  // `boy <= 0` -> her şey silinir. Masaüstü KAYNAĞINDAN okundu (ölçülmedi):
  // FadeImageOp'ta `fillRect(0, 0, width, n)` n=0'da sıfır yükseklikli, yani
  // hiçbir şey boyamıyor; ardından gelen `fillRect(0, n, width, height - n)`
  // alfa 0 ve DST_IN ile resmin tamamını siliyor. (Aynı noktadan başlayıp biten
  // GradientPaint kurulur ama hiç kullanılmaz -- patlamıyor.)
  //
  // `resolution`u da PIXI'nin genel uniform kümesi veriyor. Bugün her zaman 1
  // (KojoWorld.rendererOptions varsayılanı), ama 2 olursa inputSize.y gerçek
  // piksel cinsinden iki katına çıkardı ve n görsel olarak yarıya inerdi;
  // bölme onu bağımsız kılıyor. (resolution=1'de ölçüldü; 2 muhakemeyle.)
  private val Parça =
    """
      |varying vec2 vTextureCoord;
      |uniform sampler2D uSampler;
      |uniform highp vec4 inputSize;
      |uniform highp float resolution;
      |uniform highp float boy;
      |void main(void) {
      |  vec4 c = texture2D(uSampler, vTextureCoord);
      |  float y = vTextureCoord.y * inputSize.y / resolution;
      |  float a = boy > 0.0 ? clamp(1.0 - y / boy, 0.0, 1.0) : 0.0;
      |  gl_FragColor = c * a;
      |}
    """.stripMargin

  /**
   * PIXI 5 gerekiyor; PIXI 4'te None dönüyoruz ve çağıran resmi olduğu gibi
   * bırakıyor -- Boya'nın PIXI 4'te düz renge düşmesiyle aynı yaklaşım.
   *
   * SÜRÜM DENETİMİ `PixiUyum.beşVeÜstü` İLE, `P.Filter` yokluğuyla DEĞİL.
   * İlk yazımda öyleydi ve yanlıştı; deponun kendi paketlerinde ölçüldü:
   *   lib/pixi.min.js  (4.8.9): "Filter" geçiyor,  "inputSize" HİÇ geçmiyor
   *   lib/pixi5.min.js        : "inputSize" var
   * Yani `PIXI.Filter` v4'te de var, kurucu da üç argümanlı -- yokluk sınaması
   * hiç tetiklenmez, süzgeç kurulur, ama kabuk v4'ün sağlamadığı `inputSize`ı
   * bildirdiği için o uniform 0 kalır: y = 0, a = clamp(1 - 0/boy) = 1, yani
   * HİÇ SÖNME OLMAZ. Görsel sonuç kazara "resmi olduğu gibi bırak"a benziyordu
   * ama tasarım değil tesadüftü. Depoda sürüm denetimi için tek yer
   * PixiUyum.beşVeÜstü; buranın ondan sapmaması gerekiyor.
   *
   * Kabuk kaynağı her çağrıda aynı olduğu için PIXI'nin Program önbelleği
   * derlemeyi bir kez yapıyor; her resim yalnız kendi uniform'unu taşıyor.
   */
  def süzgeç(n: Double): Option[js.Dynamic] =
    if (!PixiUyum.beşVeÜstü) None
    else {
      val P = js.Dynamic.global.PIXI
      try Some(js.Dynamic.newInstance(P.Filter)(js.undefined, Parça, js.Dictionary("boy" -> n)))
      catch { case _: Throwable => None }
    }
}
