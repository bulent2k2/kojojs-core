/*
 * Copyright (C) 2026 Bülent Başaran <bulent2k2@gmail.com>
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
 *
 */
package kojo

import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

import pixiscalajs.PIXI

/**
 * NON_ZERO dolgu, HİÇ ÜÇGENLEMEDEN: stencil tamponuyla (#147).
 *
 * Bugüne dek kendini kesen bir yolun dolgusu libtess ile üçgenlenip PIXI'ye
 * üçgen başına bir drawPolygon ile veriliyordu; libtess'in bedeli kesişme
 * sayısıyla karesele yakın büyüyor (1000 noktalı gül ~130 ms bu konteynerde,
 * gerçek donanımda 58-112 ms -- #143). Burada üçgenleme yok; iş GPU'ya
 * gidiyor ve nokta sayısıyla doğrusal. OpenGL'in klasik "kesişen çokgen"
 * yöntemi, iki çizim çağrısı:
 *
 *   1. YELPAZE, stencil'e: çokgenin ilk köşesi merkez, ardışık her kenar için
 *      bir üçgen (p0, p_i, p_{i+1}). Renk yazma kapalı; stencil önyüzde
 *      INCR_WRAP, arkayüzde DECR_WRAP. Sonunda her pikselin stencil değeri o
 *      noktanın SARIM SAYISI (mod 256).
 *   2. KAPLAMA: sınır kutusu kadar bir dörtgen, stencilFunc NOTEQUAL 0 --
 *      NON_ZERO tam olarak bu. Geçen piksellerde stencilOp ZERO: temizlik
 *      aynı geçişte, sonraki şekle temiz tampon kalıyor.
 *
 * ÖLÇÜLDÜ (StencilDolguSondaTest, #152): libtess yoluyla piksel farkı boyalı
 * alanın %0.06'sı, yalnız silüet kenarında (kenar yumuşatma örnekleme farkı);
 * kesişmeyen şekilde sıfır; ters yönlerde iki kez sarılan bölge iki yolda da
 * delik (sarım aritmetiği); üç gradyan ve döşeme dokusu, dönüştürülmüş
 * düğüm, yarı saydam renk hepsi eşik altı. Süre 250 noktada 19-34 ms -> 0.1-0.3,
 * 1000'de 119-147 -> 0.3-0.8 ms: iki mertebe.
 *
 * KÜÇÜK ŞEKİLLER BURAYA GELMİYOR (Eşik): bedel şekil başına HER KAREDE iki
 * çizim çağrısı (~10 µs, SwiftShader'da ölçüldü: 500 kare 4 ms, 2000 kare
 * 20 ms), oysa PIXI küçük Graphics'leri tek partide çiziyor (2000 kare 0.8
 * ms). libtess ise şekil başına BİR KEZ ödeniyor ve 64 noktaya kadar en kötü
 * durumda 4 ms (ısınmış, bu konteyner; gerçek donanımda ~5x az). Yani sabit
 * bir tabloda binlerce küçük dolgu için stencil kaybeder, tek bir büyük
 * kesişen dolgu için kazanır. Kural: nokta sayısı Eşik'i aşan şekil stencil,
 * gerisi libtess + Graphics (bkz. Turtle.boyamayıİşle).
 *
 * ARA TAMPONLAR: RenderTexture'lar (pişirme, #96) stencil'siz doğuyor ve
 * stencil eki olmayan tamponda stencil sınaması HEP GEÇER -- kaplama sınır
 * kutusunun tamamını boyar (ölçüldü, %32 fark). `forceStencil` o anki
 * tampona eki takıyor; PIXI'nin maske sistemi de böyle yapıyor. Süzgeç ara
 * tamponu (Soluk) stencil'li geliyor ama ara tamponu SINIRLARDAN
 * boyutluyor: `_calculateBounds` olmadan süzgeç altında hiçbir şey
 * çizilmiyordu (ölçüldü, boyalı 0).
 *
 * BİLİNEN SINIRLAR: sarım 8 bit -- |sarım| = 256 olan piksel boş kalır
 * (INCR_WRAP sıfıra döner). ERİŞİM KOŞULU yalnız `sağ(1)` ile 256 tur atan
 * yol değil, sarım sayısı 256'ya ulaşan HER şekil: örneklerin `gül(nokta,
 * kat, ...)` işlevinde kat = 256. Ölçüldü (#153 incelemesi, ayırıcı imza):
 * 1000 x 255 %0.07 ve 1000 x 257 %0.12 fark (kenar), 1000 x 256 %56 (koca
 * bölge boş), 512 (≡ 0 mod 256) %1.7 -- StencilDolguTest'te çivili. Örneklerin
 * ölçeği (kat = 7) ve canlı ölçüm (4000 x 7) uzağında; kullanıcıya sessiz
 * bir çizim hatası olarak görünür, uyarı yok. PIXI maskeleriyle aynı tamponu
 * paylaşır (iKojo maske kullanmıyor); kenarlar MSAA'ya bağlı. Sarım yönü:
 * sahne Y'yi çeviriyor (ön/arka yüz takla atar, sarım negatiflenir), NOTEQUAL 0
 * simetrik olduğu için fark etmiyor; `durum.culling = false` şart, açılırsa
 * arkayüzler düşer ve iptal hiç çalışmaz.
 */
class StencilDolgu extends PIXI.Container {
  import StencilDolgu._

  private def d: js.Dynamic = this.asInstanceOf[js.Dynamic]

  // İm: PixiUyum'un kapıları (isabet alanı, GL bırakma) Graphics'i `finishPoly`
  // ile tanıyor; bu düğümü de bununla. JS özelliği, js.Dynamic'ten okunuyor.
  d.kojoStencilDolgu = true

  private var düz: Array[Double] = Array.empty
  private var boya: Boya = _
  private var x0 = 0.0; private var y0 = 0.0; private var x1 = 0.0; private var y1 = 0.0
  private var boş = true
  private var yelpazeGeo: js.Dynamic = null
  private var kutuGeo: js.Dynamic = null
  private var kaplamaShader: js.Dynamic = null
  private var kaplamaDokulu = false

  // Artımlı kurulum (#155): yelpaze KAPASİTELİ, `kurulu` kadar noktası
  // geçerli; sınır kutusunun ham uçları koşarak tutuluyor (x0..y1 bir piksel
  // taşırılmış türevleri). Sayaçlar sınama için: kaç nokta yelpazeye yazıldı
  // (doğrusallık savı), kaç kez yeniden ayrıldı (O(log n) savı).
  private var yelpaze: Float32Array = null
  private var kurulu = 0
  private var enKüçükX = 0.0; private var enKüçükY = 0.0; private var enBüyükX = 0.0; private var enBüyükY = 0.0
  final private[kojo] var yüklenenNokta = 0
  final private[kojo] var yenidenAyırma = 0

  /**
   * Boş: hiçbir şey çizmez (kalem kalkık taşınma, boya değişimi, sil()).
   * Kapasiteli yelpaze DURUYOR (yeniden ayırma yok), ama bir sonraki `kur`
   * baştan kurar: eski kuyruk yeni şekle sızmaz.
   */
  def temizle(): Unit = { boş = true; düz = Array.empty; kurulu = 0 }

  def noktaSayısı: Int = düz.length / 2

  /** O anki boya (testler için; `bırak`tan sonra da okunabilir). */
  final private[kojo] def şimdikiBoya: Boya = boya

  /**
   * Çokgeni ve boyayı kur; yeniden çağrılabilir (büyüyen şekil her yayında).
   *
   * ARTIMLI (#155): büyüyen şekil yalnız KUYRUĞUNDAN büyüyor (kalem kalkık
   * taşınma ve boya değişimi çokgeni sıfırlıyor, `temizle`), yani yeni dizi
   * çoğu zaman eskisinin uzantısı. İlk sürüm her yayında bütün öneki baştan
   * kuruyordu -- yayın başına doğrusal ama şekil başına süperdoğrusal,
   * çünkü yayın sayısı da nokta sayısıyla büyüyor: 40 000 noktalı gülde
   * 167 000 nokta-yüklemesi, kurulum ~111 ms (SwiftShader, #154 incelemesi
   * §4). Ölçüldü ki bedelin %95'ten fazlası CPU tarafında (yelpaze dizisi +
   * taze 960 KB'lık ayırmanın çöpü), GL yüklemesi 40 000'de 0.3-0.5 ms --
   * o yüzden yalnız CPU tarafı artımlı, tampon yine `Buffer.update` ile
   * bütünüyle yükleniyor (kısmi `bufferSubData` gerekmedi).
   *
   * UZANTI KARARI ucuz: yeni dizi eskisinden kısa değil VE ilk nokta VE eski
   * son nokta aynı yerde. Şüphede baştan kur. Uzantıysa yalnız yeni noktalar
   * için üçgen (p0, p_{i}, p_{i+1}) yazılır, sınır kutusu yalnız onlarla
   * genişler; yelpaze kapasiteli ve geometrik büyüyor (yeniden ayırma
   * O(log n), eski önek kopyalanıyor). Çizim `kurulu` kadar üçgenle sınırlı
   * (bkz. _render), kapasitenin kuyruğu hiç çizilmiyor.
   */
  def kur(yeniDüz: Array[Double], yeniBoya: Boya)(tazeleyici: () => Unit): Unit = {
    val n = yeniDüz.length / 2
    if (n < 3) { düz = yeniDüz; boş = true; kurulu = 0; return }
    val uzantı = !boş && kurulu >= 3 && n >= kurulu &&
      yeniDüz(0) == düz(0) && yeniDüz(1) == düz(1) &&
      yeniDüz(2 * kurulu - 2) == düz(2 * kurulu - 2) && yeniDüz(2 * kurulu - 1) == düz(2 * kurulu - 1)
    val başla = if (uzantı) kurulu else 0
    düz = yeniDüz; boş = false
    // Kapasite: en az 6 * (n - 2) float; ikiye katlayarak büyür, eski önek taşınır.
    val gerekli = 6 * (n - 2)
    if (yelpaze == null || yelpaze.length < gerekli) {
      var kapasite = if (yelpaze == null) 6 * math.max(n - 2, 64) else yelpaze.length
      while (kapasite < gerekli) kapasite *= 2
      val yeni = new Float32Array(kapasite)
      if (başla > 2) yeni.set(yelpaze.subarray(0, 6 * (başla - 2)))
      yelpaze = yeni; yenidenAyırma += 1
    }
    val px = düz(0).toFloat; val py = düz(1).toFloat
    var i = math.max(başla - 1, 1); var k = 6 * (i - 1)
    while (i < n - 1) {
      yelpaze(k) = px; yelpaze(k + 1) = py
      yelpaze(k + 2) = düz(2 * i).toFloat; yelpaze(k + 3) = düz(2 * i + 1).toFloat
      yelpaze(k + 4) = düz(2 * i + 2).toFloat; yelpaze(k + 5) = düz(2 * i + 3).toFloat
      i += 1; k += 6
    }
    if (başla == 0) { enKüçükX = Double.MaxValue; enKüçükY = Double.MaxValue; enBüyükX = Double.MinValue; enBüyükY = Double.MinValue }
    i = başla
    while (i < n) {
      val x = düz(2 * i); val y = düz(2 * i + 1)
      if (x < enKüçükX) enKüçükX = x; if (x > enBüyükX) enBüyükX = x; if (y < enKüçükY) enKüçükY = y; if (y > enBüyükY) enBüyükY = y
      i += 1
    }
    yüklenenNokta += n - başla
    kurulu = n
    // Kaplama sınır kutusundan bir piksel taşıyor: kenar örnekleri.
    x0 = enKüçükX - 1; y0 = enKüçükY - 1; x1 = enBüyükX + 1; y1 = enBüyükY + 1
    if (yelpazeGeo == null) {
      yelpazeGeo = js.Dynamic.newInstance(P.Geometry)()
      yelpazeGeo.addAttribute("aVertexPosition", yelpaze, 2)
      kutuGeo = js.Dynamic.newInstance(P.Geometry)()
      kutuGeo.addAttribute("aVertexPosition", kutuDizisi(), 2)
      kutuGeo.addAttribute("aUv", new Float32Array(12), 2)
    }
    else {
      // Aynı dizi olsa da `update`: PIXI kirli imini böyle koyuyor; yeniden
      // ayrıldıysa yeni diziyi de böyle öğreniyor.
      yelpazeGeo.getBuffer("aVertexPosition").update(yelpaze)
      kutuGeo.getBuffer("aVertexPosition").update(kutuDizisi())
    }
    boyayıDeğiştir(yeniBoya)(tazeleyici)
  }

  private def kutuKöşeX: js.Array[Double] = js.Array(x0, x1, x1, x0, x1, x0)
  private def kutuKöşeY: js.Array[Double] = js.Array(y0, y0, y1, y0, y1, y1)

  private def kutuDizisi(): Float32Array = {
    val kutu = new Float32Array(12); val kx = kutuKöşeX; val ky = kutuKöşeY
    var i = 0
    while (i < 6) { kutu(2 * i) = kx(i).toFloat; kutu(2 * i + 1) = ky(i).toFloat; i += 1 }
    kutu
  }

  /**
   * Boyayı değiştir; geometri kalır (TurtlePicture'ın `boyalı`sı, #86'nın
   * "her parçaya uygula" kuralı). Doku dolgusunda UV yalnız kaplamanın dört
   * köşesinde: yerel -> `matris.applyInverse` -> doku pikseli -> 0..1;
   * eşleme afin olduğu için ara değerleme tam (MeshUvSondaTest'in gerekçesi).
   *
   * Doku henüz YÜKLENMEMİŞSE (`Boya.dokuma` dosyadan, #40) düz yedek renkle
   * başlıyor, yükleme bitince dokuya geçip bir çizim istiyor -- Graphics
   * yolundaki `fillStyle` yamasının düğüm içi karşılığı. Yer tutucu doku
   * gerekmiyor: düğüm başına tek boya var, kimlik sorusu yok.
   */
  def boyayıDeğiştir(yeniBoya: Boya)(tazeleyici: () => Unit): Unit = {
    boya = yeniBoya
    if (boş) return
    boya match {
      case DüzBoya(renk) => düzKur(renk)
      case DokuBoya(doku, matris, yedek) =>
        val bt = doku.asInstanceOf[js.Dynamic].baseTexture
        if (bt.valid.asInstanceOf[Boolean]) dokuKur(doku, matris)
        else {
          düzKur(yedek)
          val bekleyen = boya
          PixiUyum.yüklemeyiBekle(bt)(
            oldu = () => { if (boya eq bekleyen) { dokuKur(doku, matris); tazeleyici() } },
            olmadı = dosya => {
              println(s"Uyarı: dokuma boyası yüklenemedi: $dosya -- düz renkle kalıyor")
              tazeleyici()
            }
          )
        }
    }
  }

  private def düzKur(renk: kojo.doodle.Color): Unit = {
    val rgb = renk.toRGBDouble.toInt; val a = renk.alpha.get
    val r = ((rgb >> 16) & 0xff) / 255.0; val g = ((rgb >> 8) & 0xff) / 255.0; val b = (rgb & 0xff) / 255.0
    // Ön çarpımlı alfa: tuval premultipliedAlpha ile açık (KojoWorld.rendererOptions).
    val uColor = js.Array(r * a, g * a, b * a, a)
    if (kaplamaShader == null || kaplamaDokulu) {
      kaplamaShader = js.Dynamic.newInstance(P.Shader)(düzProgram, js.Dynamic.literal(uColor = uColor, uAlpha = 1.0))
      kaplamaDokulu = false
    }
    else kaplamaShader.uniforms.uColor = uColor
  }

  private def dokuKur(doku: PIXI.Texture, matris: PIXI.Matrix): Unit = {
    val m = matris.asInstanceOf[js.Dynamic]
    val dd = doku.asInstanceOf[js.Dynamic]
    val dokuEn = dd.width.asInstanceOf[Double]; val dokuBoy = dd.height.asInstanceOf[Double]
    val uv = new Float32Array(12); val nokta = js.Dynamic.newInstance(P.Point)(0, 0)
    val kx = kutuKöşeX; val ky = kutuKöşeY
    var i = 0
    while (i < 6) {
      m.applyInverse(js.Dynamic.literal(x = kx(i), y = ky(i)), nokta)
      uv(2 * i) = (nokta.x.asInstanceOf[Double] / dokuEn).toFloat
      uv(2 * i + 1) = (nokta.y.asInstanceOf[Double] / dokuBoy).toFloat
      i += 1
    }
    kutuGeo.getBuffer("aUv").update(uv)
    if (kaplamaShader == null || !kaplamaDokulu) {
      kaplamaShader = js.Dynamic.newInstance(P.Shader)(dokuProgram, js.Dynamic.literal(uSampler = doku.asInstanceOf[js.Any], uAlpha = 1.0))
      kaplamaDokulu = true
    }
    else kaplamaShader.uniforms.uSampler = doku.asInstanceOf[js.Any]
  }

  /**
   * Nokta (yerel uzayda) şeklin NON_ZERO dolgusunun içinde mi -- sarım sayısı,
   * O(n). İsabet alanı için (`PixiUyum.isabetAlanınıKur`); üçgen gerekmiyor,
   * Graphics yolundaki `fillStyle.visible` çevirme dansı (#114/#116) da.
   */
  def içindeMi(x: Double, y: Double): Boolean = {
    if (boş) return false
    val n = düz.length / 2
    var sarım = 0
    var i = 0
    while (i < n) {
      val j = if (i + 1 == n) 0 else i + 1
      val xi = düz(2 * i); val yi = düz(2 * i + 1); val xj = düz(2 * j); val yj = düz(2 * j + 1)
      val sol = (xj - xi) * (y - yi) - (x - xi) * (yj - yi)
      if (yi <= y) { if (yj > y && sol > 0) sarım += 1 }
      else if (yj <= y && sol < 0) sarım -= 1
      i += 1
    }
    sarım != 0
  }

  /**
   * GL kaynaklarını bırak (#91'in kuralı: `dispose`, `destroy` değil -- düğüm
   * yeniden çizilirse geometri kendiliğinden yüklenir). Gradyan dokusu da
   * (#95): yalnız `Boya.dokuYap`ın imlediği dokular; dosyadan gelen döşeme
   * URL başına paylaşılıyor, ona dokunulmuyor.
   */
  def bırak(): Unit = {
    if (yelpazeGeo != null) { yelpazeGeo.dispose(); kutuGeo.dispose() }
    boya match {
      case DokuBoya(doku, _, _) =>
        val taban = doku.asInstanceOf[js.Dynamic].baseTexture
        if (!js.isUndefined(taban) && taban != null &&
          taban.selectDynamic(Boya.GradyanDokusuİmi).asInstanceOf[js.UndefOr[Boolean]].contains(true) &&
          js.typeOf(taban.dispose) == "function") taban.dispose()
      case _ =>
    }
  }

  /** Sınırlar: süzgeç ara tamponu, pişirme ve isabet elemesi buna bakıyor. */
  def _calculateBounds(): Unit =
    if (!boş) d._bounds.addFrame(d.transform, x0, y0, x1, y1)

  def _render(renderer: js.Dynamic): Unit = {
    if (boş) return
    renderer.batch.flush()
    val gl = renderer.gl
    val dünya = d.worldTransform.toArray(true)
    renderer.state.set(durum)
    renderer.framebuffer.forceStencil()
    // 1. Yelpaze -> stencil (sarım sayısı). Renk yazılmıyor.
    yelpazeShader.uniforms.translationMatrix = dünya
    renderer.shader.bind(yelpazeShader)
    renderer.geometry.bind(yelpazeGeo, yelpazeShader)
    gl.enable(gl.STENCIL_TEST)
    gl.stencilMask(0xff)
    gl.colorMask(false, false, false, false)
    gl.stencilFunc(gl.ALWAYS, 0, 0xff)
    gl.stencilOpSeparate(gl.FRONT, gl.KEEP, gl.KEEP, gl.INCR_WRAP)
    gl.stencilOpSeparate(gl.BACK, gl.KEEP, gl.KEEP, gl.DECR_WRAP)
    // Yalnız kurulu üçgenler: yelpaze kapasiteli, kuyruğu çöp (#155).
    renderer.geometry.draw(gl.TRIANGLES, 3 * (kurulu - 2), 0)
    // 2. Kaplama: sarım != 0 boyanır, stencil sıfırlanır.
    gl.colorMask(true, true, true, true)
    gl.stencilFunc(gl.NOTEQUAL, 0, 0xff)
    gl.stencilOp(gl.KEEP, gl.KEEP, gl.ZERO)
    kaplamaShader.uniforms.translationMatrix = dünya
    kaplamaShader.uniforms.uAlpha = d.worldAlpha
    renderer.shader.bind(kaplamaShader)
    renderer.geometry.bind(kutuGeo, kaplamaShader)
    renderer.geometry.draw(gl.TRIANGLES)
    gl.disable(gl.STENCIL_TEST)
  }
}

object StencilDolgu {
  private val P = js.Dynamic.global.PIXI

  /**
   * Nokta sayısı eşiği: bunu AŞAN şekil stencil, gerisi libtess + Graphics.
   * Gerekçe ve ölçüm sınıfın belgesinde ("küçük şekiller buraya gelmiyor").
   */
  val Eşik = 64

  private val yelpazeVert =
    """attribute vec2 aVertexPosition;
      |uniform mat3 projectionMatrix; uniform mat3 translationMatrix;
      |void main(){ gl_Position = vec4((projectionMatrix * translationMatrix * vec3(aVertexPosition, 1.0)).xy, 0.0, 1.0); }""".stripMargin
  private val boşFrag = "void main(){ gl_FragColor = vec4(0.0); }"
  private val düzFrag = "precision mediump float; uniform vec4 uColor; uniform float uAlpha; void main(){ gl_FragColor = uColor * uAlpha; }"
  private val dokuVert =
    """attribute vec2 aVertexPosition; attribute vec2 aUv;
      |uniform mat3 projectionMatrix; uniform mat3 translationMatrix;
      |varying vec2 vUv;
      |void main(){ gl_Position = vec4((projectionMatrix * translationMatrix * vec3(aVertexPosition, 1.0)).xy, 0.0, 1.0); vUv = aUv; }""".stripMargin
  private val dokuFrag = "precision mediump float; varying vec2 vUv; uniform sampler2D uSampler; uniform float uAlpha; void main(){ gl_FragColor = texture2D(uSampler, vUv) * uAlpha; }"

  // Programlar paylaşılıyor (bir kez derleniyor); yelpaze shader'ının tek
  // uniform'u translationMatrix, o da her çizimde kuruluyor -- paylaşılabilir.
  private lazy val yelpazeShader: js.Dynamic = P.Shader.from(yelpazeVert, boşFrag, js.Dynamic.literal())
  private lazy val düzProgram: js.Dynamic = P.Program.from(yelpazeVert, düzFrag)
  private lazy val dokuProgram: js.Dynamic = P.Program.from(dokuVert, dokuFrag)
  private lazy val durum: js.Dynamic = {
    val s = js.Dynamic.newInstance(P.State)()
    s.blend = true; s.culling = false; s.depthTest = false
    s
  }
}
