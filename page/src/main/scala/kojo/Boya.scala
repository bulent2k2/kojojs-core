package kojo

import kojo.doodle.Color
import org.scalajs.dom
import pixiscalajs.PIXI

import scala.scalajs.js

/**
 * Boya: bir şeklin içini dolduran şey. İki türü var --
 *  - DüzBoya: tek renk (eskiden beri olan davranış)
 *  - DokuBoya: bir PIXI dokusu + o dokuyu şeklin yerel koordinatlarına oturtan
 *    matris. Gradyanlar ve DokumaBoya bununla yapılıyor.
 *
 * Neden doku: PIXI'de gradyan diye bir dolgu yok. Tuvalde (canvas) bir gradyan
 * çizip onu dokuya çeviriyoruz, sonra fillStyle.texture olarak takıyoruz.
 * PIXI 4'te fillStyle nesnesi -- dolayısıyla doku dolgusu -- yok; orada
 * DokuBoya ilk rengine düşüyor (bkz. PixiUyum.boyayıKur).
 */
sealed trait Boya
case class DüzBoya(renk: Color) extends Boya

/**
 * @param doku    dolgu dokusu
 * @param matris  doku pikselinden ŞEKLİN YEREL koordinatına eşleme.
 *                Ölçüldü (PIXI 5.3.12): yerel = matris * doku_pikseli, yani
 *                birim matriste doku her N birimde bir tekrarlıyor.
 * @param yedek   PIXI 4'te ya da doku yüklenemezse kullanılacak düz renk
 */
case class DokuBoya(doku: PIXI.Texture, matris: PIXI.Matrix, yedek: Color) extends Boya

object Boya {
  private def tuval(g: Int, y: Int): dom.html.Canvas = {
    val c = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    c.width = g
    c.height = y
    c
  }

  private def css(c: Color): String = {
    val r = c.toRGBA
    s"rgba(${r.r.get}, ${r.g.get}, ${r.b.get}, ${c.alpha.get})"
  }

  // Gradyan rampasının çözünürlüğü. 256 hem yumuşak hem ucuz; ayrıca ikinin
  // kuvveti (POT) olması önemli: PIXI 5'in TextureSystem'i WebGL1'de POT
  // olmayan dokuda REPEAT/MIRRORED_REPEAT sarmasını sessizce CLAMP'a çeviriyor,
  // yani dalgalıDevam eski tarayıcılarda kaybolurdu (inceleme notu).
  private val RampaBoyu = 256
  private val ŞeritYüksekliği = 2

  private def dokuYap(c: dom.html.Canvas, dalgalıDevam: Boolean): PIXI.Texture = {
    // facade'daki Texture.from yalnız Image alıyor; tuval de geçerli bir kaynak,
    // onun için doğrudan global üzerinden çağırıyoruz.
    val t = js.Dynamic.global.PIXI.Texture.from(c.asInstanceOf[js.Any]).asInstanceOf[PIXI.Texture]
    // MIPMAP KAPALI olmalı: gradyan dokusu şeklin üstünde çok küçültülerek
    // örneklendiğinde mipmap bütün rampayı tek bir ortalama renge indiriyor --
    // kırmızıdan maviye geçiş düz mora, gökkuşağı düz kahverengiye dönüyordu.
    t.asInstanceOf[js.Dynamic].baseTexture.mipmap = js.Dynamic.global.PIXI.MIPMAP_MODES.OFF
    // dalgalıDevam = masaüstündeki cyclic: gradyan uçlarından sonra yansıyarak
    // sürsün. Aksi halde uçtaki renk sabitlensin (CLAMP). İkisini de AÇIKÇA
    // kuruyoruz; varsayılana güvenmiyoruz (v5'te settings.WRAP_MODE = CLAMP).
    val td = t.asInstanceOf[js.Dynamic]
    val mod = js.Dynamic.global.PIXI.WRAP_MODES
    td.baseTexture.wrapMode = if (dalgalıDevam) mod.MIRRORED_REPEAT else mod.CLAMP
    t
  }

  // Girdi denetimi v4 dalında da koşsun diye ayrı: yanlış çağrı iki sürümde de
  // aynı hatayı versin.
  private def denetle(dağılım: Seq[Double], renkler: Seq[Color]): Unit = {
    require(dağılım.length == renkler.length,
      s"dağılım ve renkler aynı uzunlukta olmalı (${dağılım.length} ile ${renkler.length} verildi)")
    require(dağılım.nonEmpty, "en az bir renk durağı gerekli")
  }

  private def duraklarıÇiz(gr: js.Dynamic, dağılım: Seq[Double], renkler: Seq[Color]): Unit = {
    dağılım.zip(renkler).foreach { case (yer, renk) =>
      gr.addColorStop(math.max(0.0, math.min(1.0, yer)), css(renk))
    }
  }

  /**
   * (x1,y1)'den (x2,y2)'ye giden çok duraklı doğrusal gradyan.
   *
   * Rampayı yatay bir şerit olarak çizip matrisle (x1,y1)->(x2,y2)
   * doğrultusuna oturtuyoruz:
   *   matris = öteleme(x1,y1) * döndürme(açı) * ölçek(uzunluk/RampaBoyu)
   */
  def doğrusalÇoklu(
    x1: Double, y1: Double, x2: Double, y2: Double,
    dağılım: Seq[Double], renkler: Seq[Color], dalgalıDevam: Boolean
  ): Boya = {
    denetle(dağılım, renkler)
    // PIXI 4'te fillStyle -- dolayısıyla doku dolgusu -- yok. Kırılmak yerine
    // ilk renge düşüyoruz; kütüphane v5'e geçtiğinde gradyan kendiliğinden gelir.
    if (!PixiUyum.beşVeÜstü) return DüzBoya(renkler.head)
    // Rampa iki satırlık bir ŞERİT: satırlar aynı olduğu için dik yönde
    // sarmanın (CLAMP ya da MIRRORED_REPEAT) hiçbir görünür etkisi yok, gradyan
    // dik yönde sonsuza uzuyor. Kare doku aynı sonucu 128 kat bellekle verirdi
    // (inceleme notu). İki satır, bir satırın bazı sürücülerde yol açtığı
    // örnekleme tuhaflıklarına karşı ucuz bir güvence.
    val c = tuval(RampaBoyu, ŞeritYüksekliği)
    val ctx = c.getContext("2d").asInstanceOf[js.Dynamic]
    val gr = ctx.createLinearGradient(0, 0, RampaBoyu, 0)
    duraklarıÇiz(gr, dağılım, renkler)
    ctx.fillStyle = gr
    ctx.fillRect(0, 0, RampaBoyu, ŞeritYüksekliği)

    val dx = x2 - x1
    val dy = y2 - y1
    val uzunluk = math.sqrt(dx * dx + dy * dy)
    val m = new PIXI.Matrix()
    if (uzunluk > 0) {
      // Ölçek iki eksende de aynı: rampa (x1,y1)->(x2,y2) doğrultusunda tam
      // `uzunluk` birim kaplasın.
      val ö = uzunluk / RampaBoyu
      m.scale(ö, ö)
      m.rotate(math.atan2(dy, dx))
      m.translate(x1, y1)
    }
    DokuBoya(dokuYap(c, dalgalıDevam), m, renkler.head)
  }

  def doğrusal(x1: Double, y1: Double, renk1: Color, x2: Double, y2: Double, renk2: Color,
    dalgalıDevam: Boolean): Boya =
    doğrusalÇoklu(x1, y1, x2, y2, Vector(0.0, 1.0), Vector(renk1, renk2), dalgalıDevam)

  /**
   * Merkezden dışarı doğru çok duraklı gradyan. Rampayı bir kare tuvale gerçek
   * yarıçapıyla çizip matrisi yalnız sol-üst köşeye ötelemek en yalını: böylece
   * doku pikseli ile yerel birim bire bir, matris hesabı da tek bir öteleme.
   */
  def merkezdenÇoklu(
    merkezX: Double, merkezY: Double, yarıçap: Double,
    dağılım: Seq[Double], renkler: Seq[Color], dalgalıDevam: Boolean
  ): Boya = {
    denetle(dağılım, renkler)
    if (!PixiUyum.beşVeÜstü) return DüzBoya(renkler.head)
    val r = math.max(1.0, yarıçap)
    // Doku boyu yarıçaptan BAĞIMSIZ ve POT: hem sarma kipi WebGL1'de de
    // korunuyor (bkz. RampaBoyu), hem de büyük yarıçaplarda bellek patlamıyor
    // (eskiden n = 2*yarıçap idi, tavan 1024x1024 = 4 MB). Ölçek matriste.
    val n = RampaBoyu
    val c = tuval(n, n)
    val ctx = c.getContext("2d").asInstanceOf[js.Dynamic]
    val gr = ctx.createRadialGradient(n / 2.0, n / 2.0, 0, n / 2.0, n / 2.0, n / 2.0)
    duraklarıÇiz(gr, dağılım, renkler)
    ctx.fillStyle = gr
    ctx.fillRect(0, 0, n, n)

    val m = new PIXI.Matrix()
    m.scale(2 * r / n, 2 * r / n)
    m.translate(merkezX - r, merkezY - r)
    DokuBoya(dokuYap(c, dalgalıDevam), m, renkler.head)
  }

  def merkezden(merkezX: Double, merkezY: Double, renk1: Color, yarıçap: Double, renk2: Color,
    dalgalıDevam: Boolean): Boya =
    merkezdenÇoklu(merkezX, merkezY, yarıçap, Vector(0.0, 1.0), Vector(renk1, renk2), dalgalıDevam)

  /**
   * Bir imge dosyasını döşeme boyası olarak kullanır (masaüstündeki
   * DokumaBoya / TexturePaint). (x, y) döşemenin başlangıç köşesi.
   *
   * Doku eşzamansız yükleniyor: PIXI.Texture.from hemen bir doku döndürüyor,
   * imge gelince kendini tazeliyor. Döşeme için sarma kipi REPEAT olmalı.
   */
  def dokuma(dosya: String, x: Double, y: Double): Boya = {
    if (!PixiUyum.beşVeÜstü) return DüzBoya(Color.gray)
    val t = js.Dynamic.global.PIXI.Texture.from(dosya).asInstanceOf[PIXI.Texture]
    t.asInstanceOf[js.Dynamic].baseTexture.wrapMode = js.Dynamic.global.PIXI.WRAP_MODES.REPEAT
    val m = new PIXI.Matrix()
    m.translate(x, y)
    DokuBoya(t, m, Color.gray)
  }
}
