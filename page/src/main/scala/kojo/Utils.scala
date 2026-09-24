package kojo

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.geom.LineString
import com.vividsolutions.jts.geom.PrecisionModel
import pixiscalajs.PIXI
import pixiscalajs.PIXI.Matrix
import pixiscalajs.PIXI.Point
import pixiscalajs.PIXI.Rectangle

import scala.concurrent.{Future, Promise}
import org.scalajs.dom.html.{Image => DomImage}

object Utils {
  def doublesEqual(d1: Double, d2: Double, tol: Double): Boolean = {
    if (d1 == d2) return true
    else if (math.abs(d1 - d2) < tol) return true
    else return false
  }

  def deg2radians(angle: Double) = angle * math.Pi / 180

  def rad2degrees(angle: Double) = angle * 180 / math.Pi

  lazy val Gf = new GeometryFactory

  lazy val pmodel = new PrecisionModel(14)
  def newCoordinate(x: Double, y: Double) = {
    val coord = new Coordinate(x, y)
    pmodel.makePrecise(coord)
    coord
  }

  def printMatrix(m: pixiscalajs.PIXI.Matrix) {
    println(m.tx, m.ty, m.a, m.d, m.b, m.c)
  }

  def printLineString(geom: Geometry): Unit = {
    println(LineString.asString(geom.asInstanceOf[LineString]))
  }

  def distance(p1: Point, p2: Point) =
    math.sqrt(math.pow(p2.x - p1.x, 2) + math.pow(p2.y - p1.y, 2))

  def transformRectangle(rect: Rectangle, m: Matrix): Rectangle = {
    // assume rectangle stays a rectangle!
    val bottomLeft = Point(rect.x, rect.y)
    val bottomRight = Point(rect.x + rect.width, rect.y)
    val topLeft = Point(rect.x, rect.y + rect.height)
    val topRight = Point(rect.x + rect.width, rect.y + rect.height)
    val newBottomLeft = m.apply(bottomLeft)
    val newBottomRight = m.apply(bottomRight)
    val newTopLeft = m.apply(topLeft)
    val newTopRight = m.apply(topRight)
    val minx = math.min(math.min(math.min(newBottomLeft.x, newTopLeft.x), newTopRight.x), newBottomRight.x)
    val miny = math.min(math.min(math.min(newBottomLeft.y, newTopLeft.y), newTopRight.y), newBottomRight.y)
    val maxx = math.max(math.max(math.max(newBottomLeft.x, newTopLeft.x), newTopRight.x), newBottomRight.x)
    val maxy = math.max(math.max(math.max(newBottomLeft.y, newTopLeft.y), newTopRight.y), newBottomRight.y)
    new Rectangle(minx, miny, maxx - minx, maxy - miny)
  }

  def printRectangle(r: Rectangle): Unit = {
    println(s"Rectangle(${r.x}, ${r.y}, ${r.width}, ${r.height})")
  }

  def image(url: String)(implicit kojoWorld: KojoWorld): SubImage = {
    val ret = Promise[DomImage]()
    def init(loader: PIXI.loaders.Loader, any: Any): Unit = {
      val img = loader.resources(url)
      ret.success(img.data.asInstanceOf[DomImage])
    }
    AssetLoader.addAndLoad(url, url, init)
    SubImage(ret.future, None)
  }

  def notSupported(name: String, reason: String) = throw new UnsupportedOperationException(s"$name - operation not available $reason:\n${toString}")
}

// PIXI 4 ile PIXI 5 arasındaki uyum katmanı.
//
// Facade `js.native` olduğu için sürüm farkları DERLEME zamanında görünmüyor;
// hepsi çalışma anında patlıyor. Onun için farkları tek yerde topluyoruz ve
// `PIXI.VERSION`'a bakarak seçiyoruz: aynı derleme hem v4 hem v5 ile çalışıyor.
// Böylece kütüphane dosyasını sunan depo (kojojs-editor) ile kodu sunan depo
// (kojojs-core) birbirini beklemek zorunda kalmıyor, geri dönüş de yalnız
// kütüphane dosyasını geri koymaktan ibaret oluyor.
//
// Tarayıcıda ÖLÇÜLEREK saptandı (pixi 4.8.9 / 5.3.12); v5'te de aynı çalıştığı
// için burada YER ALMAYAN şeyler: PIXI.loaders.Loader/Resource,
// PIXI.interaction (v5 uyumluluk kabukları duruyor), autoDetectRenderer'ın
// seçenek nesnesi biçimi, RenderTexture.create(w, h), renderer.render'ın
// konumlu biçimi, SHAPES sabitleri, Texture.from, setTransform, getBounds.
object PixiUyum {
  import scala.scalajs.js
  import scala.scalajs.js.Dynamic.{global => g}

  /** PIXI 5 (ya da üstü) mü? PIXI.VERSION'ın baş sayısına bakıyoruz. */
  lazy val beşVeÜstü: Boolean = {
    // PIXI hiç yüklenmemiş olabilir (Node altındaki birim testleri böyle koşuyor);
    // o durumda v4 varsayıyoruz -- yani doku dolgusu yok, düz renge düşülür.
    val s =
      if (js.typeOf(js.Dynamic.global.PIXI) == "undefined") "4"
      else g.PIXI.VERSION.asInstanceOf[js.UndefOr[String]].getOrElse("4")
    s.takeWhile(_.isDigit).toIntOption.exists(_ >= 5)
  }

  private def dyn(o: Any): js.Dynamic = o.asInstanceOf[js.Dynamic]

  /**
   * Varlıkları yükleyen PAYLAŞILAN Loader örneği.
   *
   * v4'te `PIXI.loader`, v5'te `PIXI.Loader.shared`. v5 eski adı da
   * sürdürüyor ama okununca konsola uyarı basıyor:
   *   "PIXI.loader instance has moved to PIXI.Loader.shared
   *    Deprecated since v5.0.0"
   * Uyarı doğrudan çocuğun tarayıcı konsoluna çıkıyor.
   *
   * SAYFA BAŞINA BİR KEZ, her okuyuşta değil: PIXI'nin `deprecation()`
   * kapısı iletiyi `warnings[message]` ile eliyor. Ölçüldü: eski ad 1 kez
   * okununca 1 kayıt, 3 kez okununca yine 1. Yani bedel gürültü değil,
   * açılış başına duran tek bir satır.
   *
   * `console.warn` DEĞİL `console.groupCollapsed` ile basılıyor (yığın izi
   * ayrıca warn'a gidiyor). Kancayla arayan biri warn'a bakıp bulamayabilir
   * -- ölçerken tam bu tuzağa düşüldü, o yüzden burada yazıyor.
   *
   * ÖLÇÜLDÜ (başsız Chrome, iki kütüphane dosyasıyla da):
   *   5.3.12 -> PIXI.Loader.shared var, PIXI.loader ile AYNI NESNE
   *             (=== doğru), ve yeni ad okunduğunda uyarı ÇIKMIYOR
   *   4.8.9  -> PIXI.Loader YOK (undefined)
   * Yani yeni ada körlemesine geçmek v4'ü kırardı; seçim buradan yapılıyor.
   *
   * `PIXI.loaders.Loader` TÜR olarak kullanılmaya devam ediyor (imzalarda) --
   * tür konumundan çalışma anında erişim doğmuyor, dolayısıyla `PIXI.loaders`
   * uyumluluk kabuğu da uyandırılmıyor.
   */
  lazy val paylaşılanYükleyici: pixiscalajs.PIXI.loaders.Loader = {
    val y = if (beşVeÜstü) g.PIXI.Loader.shared else g.PIXI.loader
    y.asInstanceOf[pixiscalajs.PIXI.loaders.Loader]
  }

  /**
   * Çizim parçaları: v4'te Graphics'in kendisinde, v5'te geometry'sinde.
   *
   * v5'te ayrıca ÖNCE finishPoly() gerekiyor: moveTo/lineTo ile çizilen yol
   * graphicsData'ya kendiliğinden geçmiyor, `currentPath`te bekliyor. Bunu
   * yapmadan okursak dizi BOŞ gelir ve üstünde dönen boya/kalem/kalınlık
   * döngüleri sessizce hiçbir şeye dokunmaz -- kaplumbağa resimleri v5'te tam
   * bu yüzden boyasız ve varsayılan kalemle çiziliyordu.
   *
   * finishPoly() `currentPath`i null'a çekiyor; sonraki lineTo'yu yoluSürdür
   * kaldığımız noktadan yeniden başlatıyor, yani ikisi birlikte çalışıyor.
   */
  def parçalar(gr: pixiscalajs.PIXI.Graphics): js.Array[js.Dynamic] = {
    val d = dyn(gr)
    val a = if (beşVeÜstü) { d.finishPoly(); d.geometry.graphicsData } else d.graphicsData
    a.asInstanceOf[js.Array[js.Dynamic]]
  }

  /**
   * Resmi, DOLGUSU GÖRÜNMESE DE tıklanabilir yapar (#114).
   *
   * NEDEN: PIXI'nin isabet sınaması yalnız GÖRÜNÜR dolguya bakıyor --
   * `Graphics.containsPoint` `fillStyle.visible` olmayan parçaları atlıyor
   * (v4'te aynı şey `data.fill`). Kalem çizgisi isabet sınamasına hiç
   * girmiyor. Sonuç: dolgusu kurulmamış ya da saydam kurulmuş bir resme
   * `fareyeTıklayınca` bağlanıyor, `interactive` doğru kuruluyor, ama resim
   * hiçbir fare olayı almıyor -- sessizce ölü. Ölçüldü, dördü de ölüydü:
   * dolgusuz daire, saydam dolgulu daire, kaplumbağa çizimi; yalnız görünür
   * dolgulu olan çalışıyordu.
   *
   * ÇARE: isabet alanını açıkça kur ve geometriye SOR -- dolgunun
   * görünürlüğüne bırakma. `hitArea.contains` çağrıldığında, alt ağaçtaki her
   * Graphics'in görünmez dolgularını GEÇİCİ olarak görünür damgalayıp
   * PIXI'nin KENDİ `containsPoint`'ini çağırıyor, sonra damgayı geri alıyoruz.
   * Çevirme eşzamanlı ve aynı çağrının içinde geri alınıyor, yani araya render
   * giremez; ekranda hiçbir şey değişmiyor.
   *
   * NEDEN "dolguyu kalıcı görünür damgala" DEĞİL (daha kısa olurdu): ölçtüm,
   * pahalı. 200 noktalı kendini kesen bir kaplumbağa çiziminde geometri
   * 804 köşe / 1200 dizinden 15.570 / 8.961'e çıkıyor (19x) ve render süresi
   * ikiye katlanıyor -- kimsenin görmediği bir dolgu için libtess üçgenlemesi
   * (#68'in bedeli). Bu yol render'a hiç dokunmuyor.
   *
   * NEDEN SINIR KUTUSU DEĞİL: kutu, şeklin dışını da tıklanabilir yapardı.
   * Ölçüldü: bu yolla r=50 dairede yerel (40,40) -- kutunun içi, dairenin
   * dışı -- isabet ALMIYOR. Kutu yalnız ucuz ELEME olarak kullanılıyor.
   *
   * BEDEL (ölçüldü, 2000 hitTest): yalın dairede satıcı yolu ~6-10 ms, bu yol
   * ~15-19 ms. 2395 parçalı patolojik bir çizimde satıcı ~42-50 ms, bu yol
   * ~108-122 ms; geometri iki ölçümde birebir aynı, yani fark bayrak çevirme.
   * Çağrı başına ~0.04 ms; 60 Hz'de bir resim için saniyede ~2 ms.
   *
   * ELLE KURULMUŞ ALANA DOKUNMUYOR: kumanda kolu çevresine (#113) açık bir
   * PIXI.Circle konuyor; orada bu genel yol devreye girmiyor.
   */
  /**
   * Parçanın çokgeni KAPALI mı -- ilk ve son nokta çakışıyor mu.
   *
   * NEDEN GEREKLİ (#118): PIXI açık bir yolun dolgusunu, yolu örtük olarak
   * kapatarak kuruyor. Yani L biçimli iki kenarlık bir kaplumbağa çiziminin
   * "dolgusu", ÇİZİLMEMİŞ üçüncü kenarla kapanan bir üçgen. O üçgeni isabet
   * alanı saymak, ekranda hiçbir şey olmayan yeri tıklanabilir yapıyordu
   * (ölçüldü: L yolunun içi KENDİSİ dönüyordu). Bu yüzden görünmez dolguyu
   * yalnız KAPALI parçalar için çeviriyoruz.
   *
   * Nokta dizisi olmayan parçalar (daire, elips, dikdörtgen) doğası gereği
   * kapalı. İki noktalı bir parça düz çizgidir, kapalı olamaz.
   *
   * Yarım birimlik tolerans: kaplumbağanın kapattığı kare tam kapanmıyor
   * (ölçüldü: son nokta (0,-0)), kayan nokta artığı kalıyor.
   */
  private def parçaKapalıMı(p: js.Dynamic): Boolean = {
    val nk = p.shape.points
    if (js.isUndefined(nk) || nk == null) true
    else {
      val a = nk.asInstanceOf[js.Array[Double]]
      if (a.length < 6) false
      else {
        val dx = a(0) - a(a.length - 2)
        val dy = a(1) - a(a.length - 1)
        dx * dx + dy * dy <= 0.25
      }
    }
  }

  /** Noktanın doğru PARÇASINA uzaklığının karesi (doğrunun değil: uçlar sınırlı). */
  private def uzaklıkKare(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double = {
    val dx = x2 - x1
    val dy = y2 - y1
    val boy2 = dx * dx + dy * dy
    val t = if (boy2 == 0) 0.0 else math.max(0.0, math.min(1.0, ((x - x1) * dx + (y - y1) * dy) / boy2))
    val px = x1 + t * dx
    val py = y1 + t * dy
    (x - px) * (x - px) + (y - py) * (y - py)
  }

  /**
   * Nokta parçanın KALEM ŞERİDİNİN içinde mi (#118).
   *
   * PIXI'nin isabet sınaması yalnız dolguya bakıyor ("only deal with fills"),
   * kalem çizgisini hiç sınamıyor. Alanı olmayan bir yol -- Resim.çizgi,
   * Resim.yatayÇizgi, kaplumbağa çizgisi -- bu yüzden sessizce ölüydü.
   *
   * MASAÜSTÜ KOJO NE YAPIYOR (kaynaktan okundu, tahmin değil): Piccolo'nun
   * PPath.intersects'i dolgu sınaması başarısız olunca STROKE'LANMIŞ ŞEKLİ
   * sınıyor, ve PInputManager isabeti `PCamera.pick(x, y, 1)` ile, yani
   * 1 birimlik bir payla arıyor. Buradaki şerit tam onun karşılığı:
   * kalem kalınlığının yarısı + 1 birim pay.
   */
  private def şeritteMi(p: js.Dynamic, x: Double, y: Double, pay: Double): Boolean = {
    val nk = p.shape.points
    if (js.isUndefined(nk) || nk == null) false
    else {
      val a = nk.asInstanceOf[js.Array[Double]]
      val kalınlık =
        if (beşVeÜstü) p.lineStyle.width.asInstanceOf[Double]
        else p.lineWidth.asInstanceOf[Double]
      val yarı = kalınlık / 2 + pay
      val eşik = yarı * yarı
      var i = 0
      var bulundu = false
      while (i + 3 < a.length && !bulundu) {
        if (uzaklıkKare(x, y, a(i), a(i + 1), a(i + 2), a(i + 3)) <= eşik) bulundu = true
        i += 2
      }
      bulundu
    }
  }

  def isabetAlanınıKur(kök: pixiscalajs.PIXI.DisplayObject): Unit = {
    val k = dyn(kök)
    if (!js.isUndefined(k.hitArea) && k.hitArea != null) return // elle kurulan alan üstün

    def sor(n: js.Dynamic, küresel: js.Dynamic, pay: Double): Boolean = {
      var bulundu = false
      // Stencil dolgusu (#147): geometrisi yok, üçgeni yok; sarım sayısını
      // kendisi hesaplıyor (StencilDolgu.içindeMi). Görünmez dolgu çevirme
      // dansı ona gerekmiyor -- dolgusu görünür değilse zaten çizilmiyor.
      if (n.kojoStencilDolgu.asInstanceOf[js.UndefOr[Boolean]].contains(true)) {
        val yerel = n.toLocal(küresel)
        bulundu = n.içindeMi(yerel.x, yerel.y).asInstanceOf[Boolean]
      }
      else if (js.typeOf(n.containsPoint) == "function") {
        val gd =
          if (beşVeÜstü) {
            val geo = n.geometry
            if (js.isUndefined(geo) || geo == null) null
            else geo.graphicsData.asInstanceOf[js.Array[js.Dynamic]]
          }
          else n.graphicsData.asInstanceOf[js.Array[js.Dynamic]]
        if (gd != null && !js.isUndefined(gd) && gd.length > 0) {
          var çevrilen = 0
          var i = 0
          while (i < gd.length) {
            val p = gd(i)
            val görünür =
              if (beşVeÜstü) p.fillStyle.visible.asInstanceOf[Boolean]
              else p.fill.asInstanceOf[Boolean]
            // AÇIK parçanın dolgusunu çevirmiyoruz: o dolgu, çizilmemiş bir
            // kapanış kenarıyla kurulmuş hayalet bir alan (#118).
            if (!görünür && parçaKapalıMı(p)) {
              if (beşVeÜstü) p.fillStyle.visible = true else p.fill = true
              p.kojoDolguÇevrildi = true
              çevrilen += 1
            }
            else p.kojoDolguÇevrildi = false
            i += 1
          }
          // try/finally: geri alma bu döngüde ATLANMAMASI gereken tek şey.
          // containsPoint'in patlaması beklenmez, ama atlanırsa görünmez
          // dolgular KALICI olarak görünür kalır -- yani hem ekran değişir hem
          // de 19x üçgenleme bedeli sürekli hâle gelir.
          try bulundu = n.containsPoint(küresel).asInstanceOf[Boolean]
          finally if (çevrilen > 0) {
            i = 0
            while (i < gd.length) {
              val p = gd(i)
              if (p.kojoDolguÇevrildi.asInstanceOf[Boolean]) {
                if (beşVeÜstü) p.fillStyle.visible = false else p.fill = false
              }
              i += 1
            }
          }
          // Dolgu tutmadıysa KALEM ŞERİDİNE bak: alanı olmayan yollar (#118).
          if (!bulundu) {
            val yerel = n.toLocal(küresel)
            val yx = yerel.x.asInstanceOf[Double]
            val yy = yerel.y.asInstanceOf[Double]
            var j = 0
            while (j < gd.length && !bulundu) {
              val p = gd(j)
              val kalemGörünür =
                if (beşVeÜstü) p.lineStyle.visible.asInstanceOf[Boolean]
                else !js.isUndefined(p.lineWidth) && p.lineWidth.asInstanceOf[Double] > 0
              if (kalemGörünür && şeritteMi(p, yx, yy, pay)) bulundu = true
              j += 1
            }
          }
        }
      }
      if (bulundu) true
      else {
        val ç = n.children.asInstanceOf[js.Array[js.Dynamic]]
        if (js.isUndefined(ç) || ç == null) false
        else {
          var i = 0
          var b = false
          while (i < ç.length && !b) { b = sor(ç(i), küresel, pay); i += 1 }
          b
        }
      }
    }

    k.hitArea = js.Dynamic.literal(
      contains = js.Any.fromFunction2 { (x: Double, y: Double) =>
        // Şeridin payı EKRAN biriminde (masaüstündeki pick halo'su gibi), yerel
        // birime dünya ölçeğine bölerek çevriliyor. Ölçek iki eksende ayrı
        // okunuyor ve KÜÇÜĞÜ alınıyor: eşit olmayan ölçekte (büyütXY) pay iki
        // eksende de en az 1 ekran birimi kalsın.
        val dt = k.worldTransform
        val öx = math.sqrt(
          dt.a.asInstanceOf[Double] * dt.a.asInstanceOf[Double] +
            dt.b.asInstanceOf[Double] * dt.b.asInstanceOf[Double]
        )
        val öy = math.sqrt(
          dt.c.asInstanceOf[Double] * dt.c.asInstanceOf[Double] +
            dt.d.asInstanceOf[Double] * dt.d.asInstanceOf[Double]
        )
        val ölçek = math.min(öx, öy)
        val pay = if (ölçek > 0) 1.0 / ölçek else 1.0

        // ucuz eleme: yerel sınır kutusunun dışındaki nokta için geometriyi hiç
        // gezme. Kutu, kararı veren sınamayla AYNI payla genişletiliyor. Sabit
        // 1 birim kullanmak küçültülmüş resimlerde elemeyi şeritten dar
        // bırakıyordu (ölçüldü: ölçek 0.05'te şeridin eşiği 21 yerel birim ama
        // eleme 2 birimde kesiyordu, yani etkin pay 1 ekran birimi değil
        // ölçek x 1 ekran birimiydi) -- eleme, kararı veren sınamadan dar olamaz.
        val s = k.getLocalBounds()
        val sx = s.x.asInstanceOf[Double] - pay
        val sy = s.y.asInstanceOf[Double] - pay
        if (x < sx || y < sy ||
            x > sx + s.width.asInstanceOf[Double] + 2 * pay ||
            y > sy + s.height.asInstanceOf[Double] + 2 * pay)
          false
        else sor(k, k.toGlobal(js.Dynamic.newInstance(js.Dynamic.global.PIXI.Point)(x, y)), pay)
      }
    )
  }

  /**
   * Geometriyi yeniden kurdurur. v4 iki sayacı artırmakla yetiniyordu;
   * v5'te bunlar Graphics'te değil geometry'de ve invalidate() ile işliyor.
   */
  def tazele(gr: pixiscalajs.PIXI.Graphics): Unit = {
    val d = dyn(gr)
    if (beşVeÜstü) d.geometry.invalidate()
    else {
      d.dirty = d.dirty.asInstanceOf[Double] + 1
      d.clearDirty = d.clearDirty.asInstanceOf[Double] + 1
    }
  }

  /**
   * Boya rengi. v4'te parçanın düz alanları (fillColor/fillAlpha), v5'te bir
   * fillStyle nesnesi -- ve v5'te ayrıca `visible` bayrağı var: şekil
   * beginFill(renk, 0) ile çizilmişse stil görünmez damgalanıyor, yalnız rengi
   * değiştirmek yetmiyor.
   */
  def boyayıKur(gr: pixiscalajs.PIXI.Graphics, renk: Double, saydamlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) {
        gd.fillStyle.color = renk
        gd.fillStyle.alpha = saydamlık
        gd.fillStyle.visible = saydamlık > 0
      }
      else {
        gd.fillColor = renk
        gd.fillAlpha = saydamlık
      }
    }
    tazele(gr)
  }

  /**
   * Boyayı kurar: düz renk ya da doku (gradyan / dokuma).
   *
   * Doku dolgusu yalnız PIXI 5'te var -- fillStyle nesnesi v4'te yok. v4'te
   * DokuBoya zaten kurulurken DüzBoya'ya düşüyor (bkz. Boya), yine de burada
   * yedek renge düşerek ikinci bir güvence bırakıyoruz.
   */
  def boyayıKurBoya(gr: pixiscalajs.PIXI.Graphics, boya: Boya)(tazeleyici: () => Unit): Unit = boya match {
    case DüzBoya(renk) =>
      boyayıKur(gr, renk.toRGBDouble, renk.alpha.get)
    case DokuBoya(doku, matris, yedek) =>
      if (!beşVeÜstü) boyayıKur(gr, yedek.toRGBDouble, yedek.alpha.get)
      else {
        // DİKKAT: fillStyle.matrix, yerel koordinattan doku koordinatına giden
        // eşlemeyi tutuyor, yani bizim tuttuğumuzun TERSİNİ. Graphics.beginTextureFill
        // bu tersi kendisi alıyor; biz fillStyle'ı doğrudan değiştirdiğimiz için
        // burada elle almamız gerek. Alınmazsa gradyan yanlış ölçekte ve yanlış
        // yerde çıkıyor (ölçüldü: 160 birimlik rampa 410 birime yayılıyordu).
        // Doku henüz yüklenmemişse (DokumaBoya bir dosyadan geliyor) PIXI 5'in
        // validateBatching'i HİÇBİR batch kurmuyor: Graphics o kareyi bomboş
        // çiziyor -- düz renkli parçalar ve kalem dahil. Yüklendiğinde kendi
        // başına bir çizim tetiklenmediği için, durağan bir sahnede şekil
        // hiç görünmüyordu. Yüklemeyi dinleyip bir çizim istiyoruz.
        // (geometry.dirty != cacheDirty kaldığından tazele() gerekmiyor.)
        val bt = dyn(doku).baseTexture
        if (!bt.valid.asInstanceOf[Boolean])
          yüklemeyiBekle(bt)(
            oldu = () => tazeleyici(),
            olmadı = dosya => {
              println(s"Uyarı: dokuma boyası yüklenemedi: $dosya -- düz renge dönülüyor")
              düzBoyayaDön(gr, yedek)
              tazeleyici()
            }
          )
        val ters = dyn(matris).clone().invert()
        parçalar(gr).foreach { gd =>
          gd.fillStyle.texture = doku.asInstanceOf[js.Any]
          gd.fillStyle.matrix = ters.asInstanceOf[js.Any]
          // Doku rengi bozulmasın diye çarpan beyaz ve tam donuk olmalı.
          gd.fillStyle.color = 0xffffff
          gd.fillStyle.alpha = 1.0
          gd.fillStyle.visible = true
        }
        tazele(gr)
      }
  }

  /**
   * Doku henüz yüklenmemişse yüklemenin sonucunu bekler.
   *
   * Neden gerekiyor: PIXI 5'in validateBatching'i geçersiz dokulu bir fillStyle
   * görünce HİÇBİR batch kurmuyor; Graphics o kareyi bomboş çiziyor -- düz
   * renkli parçalar ve kalem dahil. Yükleme bitince kendiliğinden bir çizim
   * tetiklenmediği için durağan sahnede şekil hiç görünmüyordu.
   *
   * Neden OLAY değil SÖZ: `once("loaded"/"error")` yalnız dosyanın İLK
   * kullanımında çalışıyor. Texture.from başarısız yüklemeyi de önbellekte
   * tutuyor; aynı bozuk dosya ikinci kez kullanıldığında aynı BaseTexture
   * dönüyor ve artık hiçbir olay gelmiyor (ölçüldü: 2. kullanımda "loaded" da
   * "error" da gelmiyor, valid hâlâ false). Bu yalnız "betiği iki kez çalıştır"
   * durumu değil: TurtlePicture.setFillPaint boyayı ready.foreach içinde,
   * yani çizim bittikten sonra kuruyor; o sırada 404 çoktan gelmiş oluyor.
   * resource.load() ise aynı sözü döndürüyor ve GEÇ abone olana da çalışıyor
   * (ölçüldü: bozuk kaynağa sonradan load() hemen reddediyor). Söz yolu
   * ayrıca dinleyici sızıntısı bırakmıyor: olay yolunda başarı durumunda
   * "error" kapanışı (ya da tersi) önbellekteki BaseTexture üstünde sonsuza
   * dek kalıp gr ile tazeleyici'yi tutuyordu.
   */
  private[kojo] def yüklemeyiBekle(bt: js.Dynamic)(oldu: () => Unit, olmadı: String => Unit): Unit = {
    val kaynak = bt.resource
    val dosya = {
      val u = kaynak.url
      if (js.isUndefined(u) || u == null) "(dosya adı bilinmiyor)" else u.toString
    }
    def başarı(x: js.Any): Unit = oldu()
    def başarısızlık(x: js.Any): Unit = olmadı(dosya)
    try {
      kaynak.load().applyDynamic("then")(
        ((başarı _): js.Function1[js.Any, Unit]).asInstanceOf[js.Any],
        ((başarısızlık _): js.Function1[js.Any, Unit]).asInstanceOf[js.Any]
      )
    }
    catch {
      // resource.load() olmayan bir kaynak türü: eski olay yoluna düş.
      case _: Throwable =>
        bt.once("loaded", () => oldu())
        bt.once("error", () => olmadı(dosya))
    }
  }

  /**
   * Doku dolgusunu bırakıp düz renge döner. Yalnız rengi kurmak YETMİYOR:
   * geçersiz doku fillStyle'da kaldığı sürece validateBatching hiçbir batch
   * kurmuyor ve şekil çizilmiyor. Dokuyu beyaza (yani dolgu-yok anlamına gelen
   * Texture.WHITE'a) çevirmek gerekiyor.
   */
  private def düzBoyayaDön(gr: pixiscalajs.PIXI.Graphics, yedek: kojo.doodle.Color): Unit = {
    parçalar(gr).foreach { gd =>
      gd.fillStyle.texture = js.Dynamic.global.PIXI.Texture.WHITE
      gd.fillStyle.matrix = null
    }
    boyayıKur(gr, yedek.toRGBDouble, yedek.alpha.get)
  }

  /**
   * Doku dolgusunu BAŞLATIR (kaplumbağa yolu için).
   *
   * boyayıKurBoya'dan farkı: orada şekil çizilmiş oluyor ve graphicsData
   * üstündeki fillStyle değiştiriliyor; burada şekil daha çizilmedi, PIXI'nin
   * kendi beginTextureFill'i kullanılıyor. Bunun bir sonucu var:
   * beginTextureFill matrisin TERSİNİ kendisi alıyor (ölçüldü), yani buraya
   * doğal (doku -> yerel) matrisi veriyoruz -- boyayıKurBoya'da elle ters
   * aldığımızın tersine.
   *
   * PIXI 4'te beginTextureFill yok; orada yedek düz renge düşülüyor.
   *
   * Doku daha YÜKLENMEMİŞSE (DokumaBoya bir dosyadan geliyor) beginTextureFill'i
   * hemen çağıramayız: validateBatching geçersiz dokulu TEK bir parça görünce
   * Graphics'in tamamı için batch kurmuyor, ve kaplumbağanın bütün çizimi tek
   * Graphics (turtlePath) olduğu için yükleme bitene dek daha önce çizilmiş her
   * şey de kayboluyordu (ölçüldü: 8,5 sn boyunca sahne boş, #40 incelemesi).
   * Çözüm: yedek renkle ama bu boyaya ÖZGÜ bir yer tutucu dokuyla başlıyoruz --
   * Texture.WHITE'ın baseTexture'ından yeni bir Texture: geçerli (beyaz, renkle
   * çarpılınca yedek renk) ama kimliği tek. beginTextureFill ve FillStyle.clone
   * dokuyu referansla taşıdığından, yükleme bitince "texture eq yerTutucu" olan
   * parçalar tam olarak bu boyayla çizilenlerdir; yalnız onlar dokuya çevrilir,
   * başka boyaların parçalarına dokunulmaz. Yükleme başarısızsa parçalar zaten
   * yedek renkte, yalnız uyarı basılır.
   */
  def boyamayaBaşla(gr: pixiscalajs.PIXI.Graphics, boya: Boya)(tazeleyici: () => Unit): Unit =
    boya match {
      case DüzBoya(renk) =>
        gr.beginFill(renk.toRGBDouble, renk.alpha.get)
      case DokuBoya(doku, matris, yedek) =>
        if (!beşVeÜstü) gr.beginFill(yedek.toRGBDouble, yedek.alpha.get)
        else {
          val bt = dyn(doku).baseTexture
          if (bt.valid.asInstanceOf[Boolean]) {
            dyn(gr).beginTextureFill(js.Dynamic.literal(
              texture = doku.asInstanceOf[js.Any],
              matrix = matris.asInstanceOf[js.Any],
              color = 0xffffff,
              alpha = 1.0
            ))
          }
          else {
            val yerTutucu: js.Any =
              js.Dynamic.newInstance(g.PIXI.Texture)(g.PIXI.Texture.WHITE.baseTexture)
            dyn(gr).beginTextureFill(js.Dynamic.literal(
              texture = yerTutucu,
              matrix = null,
              color = yedek.toRGBDouble,
              alpha = yedek.alpha.get
            ))
            // fillStyle.matrix yerel -> doku yönünde tutulur (bkz. boyayıKurBoya);
            // beginTextureFill bu tersi kendisi alıyordu, burada elle alıyoruz.
            val ters = dyn(matris).clone().invert()
            def dokuyaÇevir(fs: js.Dynamic): Unit =
              if (fs.texture.asInstanceOf[js.Any] eq yerTutucu) {
                fs.texture = doku.asInstanceOf[js.Any]
                fs.matrix = ters.asInstanceOf[js.Any]
                fs.color = 0xffffff
                fs.alpha = 1.0
              }
            yüklemeyiBekle(bt)(
              oldu = () => {
                parçalar(gr).foreach(gd => dokuyaÇevir(gd.fillStyle))
                // Şu anki boya hâlâ bu yer tutucuysa sonraki şekiller de dokuyla çizilsin.
                dokuyaÇevir(dyn(gr)._fillStyle)
                tazele(gr)
                tazeleyici()
              },
              olmadı = dosya => {
                println(s"Uyarı: dokuma boyası yüklenemedi: $dosya -- düz renkle kalıyor")
                tazeleyici()
              }
            )
          }
        }
    }

  /** Kalem rengi -- boyayıKur'un kalem karşılığı. */
  def kalemiKur(gr: pixiscalajs.PIXI.Graphics, renk: Double, saydamlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) {
        gd.lineStyle.color = renk
        gd.lineStyle.alpha = saydamlık
        gd.lineStyle.visible = true
      }
      else {
        gd.lineColor = renk
        gd.lineAlpha = saydamlık
      }
    }
    tazele(gr)
  }

  /** Kalem kalınlığı. */
  def kalemKalınlığınıKur(gr: pixiscalajs.PIXI.Graphics, kalınlık: Double): Unit = {
    parçalar(gr).foreach { gd =>
      if (beşVeÜstü) gd.lineStyle.width = kalınlık else gd.lineWidth = kalınlık
    }
    tazele(gr)
  }

  /** Doku uv'lerini tazeler (alt-imge kırpmasından sonra). */
  def uvTazele(doku: pixiscalajs.PIXI.Texture): Unit = {
    val d = dyn(doku)
    if (beşVeÜstü) d.updateUvs() else d._updateUvs()
  }

  /**
   * PIXI 4 -> 5'in en sinsi farkı; göç kılavuzunda geçmiyor ve derleme
   * zamanında hiç görünmüyor:
   *   v4 moveTo(x, y) poligonu HEMEN drawShape ile graphicsData'ya koyuyordu;
   *     lineTo o yerleşmiş şeklin noktalarını yerinde büyütüyordu -- yani her
   *     lineTo'dan sonraki çizim izi gösteriyordu.
   *   v5 moveTo(x, y) poligonu `currentPath`te BEKLETİYOR; graphicsData'ya
   *     ancak finishPoly() ile ve YALNIZ points.length > 2 iken geçiyor. Her
   *     çizim finishPoly() çağırıyor ve tam 2 noktalı yolu SİLİYOR
   *     (currentPath.points.length = 0).
   * Kaplumbağa tam bu kalıpta çiziyor (moveTo, çiz, lineTo, çiz, ...), yani
   * v5'te kalem izi hiç çıkmıyordu. Ölçüldü: aynı betikte v4 graphicsData=1,
   * v5 graphicsData=0.
   *
   * Çare: lineTo'dan önce yolun boşaltılmış olup olmadığına bakıp, boşaltılmışsa
   * kaldığımız noktadan yeniden başlatmak. Çizimler arasındaki kesimler yine
   * tek poligonda toplanıyor -- kesim başına ayrı şekil üretmiyoruz.
   */
  def yoluSürdür(gr: pixiscalajs.PIXI.Graphics, sonX: Double, sonY: Double): Unit =
    if (beşVeÜstü) {
      val yol = dyn(gr).currentPath
      val boş = js.isUndefined(yol) || yol == null ||
        yol.points.asInstanceOf[js.Array[Double]].length < 2
      if (boş) gr.moveTo(sonX, sonY)
    }

  /**
   * Graphics'in dolgu boyalarındaki gradyan dokularını bırakır (sorun #95).
   *
   * Gradyan (`Boya`) dolgunun BaseTexture'ı geometriden AYRI bir kaynak:
   * katmanı sahneden çıkarmak onu çizicinin `managedTextures` dizisinden
   * düşürmüyor, düşüren tek şey `dispose()`. Ölçüldü (gradyan dolgulu
   * canlandır döngüsü, 120 kare): bırakmadan doku sayacı 0'dan 130'a doğrusal
   * çıkıyor, bırakınca 3'te duruyor.
   *
   * Geometride olduğu gibi `dispose()`, `destroy()` DEĞİL -- ve burada bu daha
   * da önemli, çünkü `Boya` kullanıcının elinde yaşıyor olabilir: aynı `b`yi
   * birden çok resme vermek geçerli. `dispose()` yalnız GL yüklemesini
   * bırakıyor, tuval KAYNAĞI (`resource`) duruyor ve doku bir daha çizilirse
   * kendiliğinden geri yükleniyor. Ölçüldü: çizim sonrası sayaç 2, dispose
   * sonrası 1, aynı Boya yeniden çizilince yine 2 ve `resource` ayakta. Yani
   * paylaşılan bir Boya'yı bırakmak başkasının çizimini BOZMUYOR, olsa olsa
   * bir yeniden yüklemeye mal oluyor. `destroy()` ile ayrım burada: o da
   * sayacı düşürüp yeniden yüklenmiş gibi gösteriyor ve `valid` yine true
   * kalıyor, ama `resource`u koparıyor -- doku bir daha asla üretilemiyor
   * (ölçüldü; sınamadaki çivi bu yüzden `resource`, `valid` değil).
   *
   * YALNIZ İMLİ DOKULAR: yalnız `Boya.dokuYap`ın ürettiği gradyan dokuları
   * bırakılıyor (`Boya.GradyanDokusuİmi`). `Texture.WHITE` gibi paylaşılan
   * PIXI dokularına ve `Boya.dokuma`nın dosyadan yüklediği dokuya
   * dokunulmuyor: ikincisi URL başına önbellekte paylaşılıyor, her karede
   * bırakıp yeniden yüklemek gereksiz iş olurdu.
   */
  private def gradyanDokularınıBırak(geometri: js.Dynamic): Unit = {
    val veri = geometri.graphicsData
    if (!js.isUndefined(veri) && veri != null) {
      veri.asInstanceOf[js.Array[js.Dynamic]].foreach { gd =>
        val fs = gd.fillStyle
        if (!js.isUndefined(fs) && fs != null) {
          val doku = fs.texture
          if (!js.isUndefined(doku) && doku != null) {
            val taban = doku.baseTexture
            if (!js.isUndefined(taban) && taban != null &&
              taban.selectDynamic(Boya.GradyanDokusuİmi).asInstanceOf[js.UndefOr[Boolean]].contains(true) &&
              js.typeOf(taban.dispose) == "function") taban.dispose()
          }
        }
      }
    }
  }

  /**
   * Sahneden çıkan bir düğümün (ve altındakilerin) GL kaynaklarını bırakır.
   *
   * NEDEN GEREKLİ: PIXI 5 bir Graphics'in geometrisini ÇİZİCİNİN
   * `geometry.managedGeometries` / `managedBuffers` haritalarında tutuyor.
   * Düğümü sahneden çıkarmak -- hatta JS nesnesini çöpe vermek -- o kayıtları
   * düşürmüyor; tek düşüren şey `dispose()`. Her karede resimleriSil() +
   * yeniden çizim yapan bir canlandır döngüsünde sayaçlar bu yüzden sınırsız
   * büyüyordu (sorun #91; tarayıcıda ölçüldü, aşağıdaki sayılar oradan).
   *
   * NEDEN destroy() DEĞİL de dispose(): `destroy()` düğümü kullanılamaz hale
   * getirir; `resimleriSil(); çiz(r)` gibi AYNI resmi yeniden çizen bir kalıbı
   * bozardı (masaüstü Kojo'da o kalıp çalışıyor). `dispose()` yalnız GL
   * tarafını bırakıyor: şekil verisi CPU'da duruyor ve düğüm bir daha
   * çizilirse geometri kendiliğinden yeniden yükleniyor.
   * (Turtle.realClear'daki destroy() farklı: orada çıkarılan parçalara hiç
   * kimse tutunmuyor.)
   *
   * YALNIZ Graphics: Sprite/Mesh gibi düğümlerin geometrisi PAYLAŞILMIŞ
   * olabiliyor, onu bırakmak başkasının çizimini bozardı. Bir düğümün Graphics
   * olup olmadığını deponun başka yerlerindeki ölçütle anlıyoruz: finishPoly
   * işlevi var mı.
   *
   * GEOMETRİ VE GRADYAN DOKUSU: gradyan (`Boya`) dolgunun BaseTexture'ı ayrı
   * bir kaynak; onu da bırakıyoruz (bkz. gradyanDokularınıBırak, sorun #95).
   * Eskiden yalnız geometri bırakılıyordu ve gradyan dolgulu aynı döngüde
   * geometri/tampon/sahne tavanlanırken doku sayacı doğrusal büyümeyi
   * sürdürüyordu.
   */
  def glKaynaklarınıBırak(düğüm: Any): Unit =
    if (beşVeÜstü && düğüm != null) {
      val d = dyn(düğüm)
      // Stencil dolgusu (#147) kendi geometrilerini ve gradyan dokusunu
      // kendisi bırakıyor; `finishPoly` kapısından geçmez, o yüzden ayrı dal
      // -- #125'in "sessizce atlar" uyarısı tam bu satır için yazılmıştı.
      if (d.kojoStencilDolgu.asInstanceOf[js.UndefOr[Boolean]].contains(true)) d.bırak()
      else if (js.typeOf(d.finishPoly) == "function") {
        val geo = d.geometry
        if (!js.isUndefined(geo) && geo != null) {
          gradyanDokularınıBırak(geo)
          if (js.typeOf(geo.dispose) == "function") geo.dispose()
        }
      }
      val çocuklar = d.children
      if (!js.isUndefined(çocuklar) && çocuklar != null) {
        çocuklar.asInstanceOf[js.Array[js.Dynamic]].foreach(glKaynaklarınıBırak)
      }
    }

  // --- "Bu katman silindi" imi ------------------------------------------------
  //
  // Sahneden silinmiş bir resmin çizeri bekleyen boya sırasına GERİ GİRMESİN
  // diye (#109): kaplumbağanın komut kuyruğu silmeden sonra da boşalmaya devam
  // ediyor ve her kenar boyaKirlendi'yi yeniden çağırıyor. Sıradan bir kez
  // düşürmek bu geri girişi kapatmıyor (ölçüm: KojoWorld.katmanınBoyasınıUnut
  // yanındaki not) -- ikisi birlikte gerekiyor.
  //
  // NEDEN AÇIK BİR İM, katmanın `parent`'ının null olmasına BAKMAK DEĞİL:
  // pişirme de düğümleri sahne dışında tutuyor (#96/#102) ve çizim yolu
  // (turtlePathLineTo) noteMutation çağırmıyor -- yani çizmekte olan bir
  // resmin katmanı durağan görünüp pişebilir. `parent`'a bakan bir çıkarım onu
  // "silinmiş" sayıp dolgusunu sessizce düşürürdü. Bu im yalnız SİLME
  // yollarının (removeLayer, erasePictures) koyduğu, yalnız addLayer'ın
  // kaldırdığı bir bayrak; pişirme stage.removeChild/addChildAt'ı DOĞRUDAN
  // çağırıyor, yani ime hiç dokunmuyor. Tehlike tasarımdan siliniyor.
  //
  // NEDEN KATMANIN ÜZERİNDE, KojoWorld'de bir küme değil: küme silinmiş
  // katmanlara gönderme tutardı (sızıntı). İm katmanla birlikte ölüyor.
  // (Aynı deyim Boya.GradyanDokusuİmi'nde de kullanılıyor.)
  //
  // İÇ İÇE RESİMLER: silme yolunda yalnız VERİLEN katman değil, altındaki
  // katmanlar da imleniyor (bkz. altAğacıSilindiİmle, #115). Bir resim bir
  // GPics'in içindeyse sahneden çıkan düğüm grubun kabı; çizerin kendi
  // turtleLayer'ı onun ALTINDA kalıyor ve eskiden ne imleniyordu ne sıradan
  // düşüyordu -- ölçüldü: 40 karede çıplak iki gül 74 yayın, aynı ikisi bir
  // GPics içindeyken 512-533. İmi KALDIRAN yol (addLayer) zaten çocuk başına
  // çalışıyor: BasePicSequence.realDraw her çocuk için p.draw() çağırıyor,
  // o da addLayer'dan geçiyor.
  private val Silindiİmi = "__kocoKatmanSilindi"

  // İKİNCİ İM: "bu katmanın BEKLEYEN yayını düşürüldü".
  //
  // Düşürülen yayın BİLGİ taşıyor: hiç yayınlanmamış bir dolgu öyle kaybolur
  // ve resim yeniden çizilince dolgusuz görünür. #111 bu telafiyi
  // `Picture.erase()` yolu için ekledi (TurtlePicture.dolguDüşürüldü); aynı
  // kusur `resimleriSil()` kapısından da geliyordu, çünkü o Picture.erase()'ten
  // geçmiyor -- elinde katman var, çizer yok. Bu im o bilgiyi katmanın üzerinde
  // taşıyor, realDraw okuyup yeniden kirletiyor (#109, #112 incelemesi).
  //
  // "Silindi"den AYRI bir im, çünkü koşulları farklı: her silinen katman
  // imleniyor ama yalnız GERÇEKTEN bir yayın düşürülen katmanın yeniden
  // kirletilmesi gerekiyor. Her çizimde kirletmek, bir kez çizilen her resme
  // fazladan bir üçgenleme bindirirdi (#111'in altını çizdiği ayrım).
  private val DüşenBoyaİmi = "__kocoDüşenBoya"

  private def imKoy(o: Any, im: String): Unit = if (o != null) dyn(o).updateDynamic(im)(true)
  private def imSil(o: Any, im: String): Unit = if (o != null) dyn(o).updateDynamic(im)(false)
  private def imVarMı(o: Any, im: String): Boolean =
    o != null && dyn(o).selectDynamic(im).asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)

  /** Katmanı "sahneden silindi" diye imle (silme yolları çağırıyor). */
  def katmanıSilindiİmle(katman: Any): Unit = imKoy(katman, Silindiİmi)

  /** Katman silinmiş mi? İm hiç konmamışsa HAYIR -- yeni kurulmuş bir katman
    * (Resim{} gövdesi çiz()'den ÖNCE çalışıyor) canlı sayılmalı, yoksa dolgu
    * hiç oluşmaz. */
  def katmanSilindiMi(katman: Any): Boolean = imVarMı(katman, Silindiİmi)

  /** Bu katmanın bekleyen yayını GERÇEKTEN düşürüldü diye imle. */
  def düşenBoyayıİmle(katman: Any): Unit = imKoy(katman, DüşenBoyaİmi)

  /** Düşürülmüş bir yayın var mı? realDraw, addLayer imleri silmeden ÖNCE okuyor. */
  def düşenBoyaVarMı(katman: Any): Boolean = imVarMı(katman, DüşenBoyaİmi)

  /**
   * `katman`ı VE altındaki bütün düğümleri "silindi" diye imler (#115).
   *
   * Neden alt ağaç: bir GPics (BasePicSequence) silindiğinde sahneden çıkan
   * düğüm grubun kabı, ama dolgusu bekleyen çizerlerin katmanı onun altındaki
   * çocuklar. Yalnız kabı imlemek onları durdurmuyordu: kaplumbağanın komut
   * kuyruğu boşaldıkça her kenar boyaKirlendi'yi yeniden çağırıp çizeri sıraya
   * geri koyuyordu.
   *
   * Graphics/Sprite düğümlerini AYIKLAMIYORUZ: PIXI 5'te ikisi de Container'dan
   * türüyor, yani ucuz ve güvenilir bir ayırt edici yok. Fazladan im koymak
   * zararsız -- imi yalnız `boyasıSürüyor` (turtleLayer üstünde) ve
   * `düşenBoyaVarMı` (tnode üstünde) okuyor; öteki düğümlerde kimse bakmıyor.
   * Yürüyüşün kendisi glKaynaklarınıBırak'ın zaten yaptığı yürüyüşün aynısı.
   */
  def altAğacıSilindiİmle(düğüm: Any): Unit =
    if (düğüm != null) {
      katmanıSilindiİmle(düğüm)
      val çocuklar = dyn(düğüm).children
      if (!js.isUndefined(çocuklar) && çocuklar != null) {
        çocuklar.asInstanceOf[js.Array[js.Dynamic]].foreach(altAğacıSilindiİmle)
      }
    }

  /** Katman (yeniden) sahneye giriyor: her iki imi de kaldır. addLayer çağırıyor. */
  def katmanınSilindiİminiSil(katman: Any): Unit = {
    imSil(katman, Silindiİmi)
    imSil(katman, DüşenBoyaİmi)
  }
}
