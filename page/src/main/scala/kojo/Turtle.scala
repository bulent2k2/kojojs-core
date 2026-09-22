package kojo

import kojo.doodle.Color
import org.scalajs.dom.window
import pixiscalajs.PIXI
import pixiscalajs.PIXI.Point

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.scalajs.js

/**
 * Komut pompasının saf (DOM/PIXI'siz) durum makinesi -- BakePolicy ile aynı
 * gerekçeyle ayrıldı: Node altında sınanabilsin (bkz. KojoWorld.scala).
 *
 * Pompa şöyle işliyor: `queueHandler` kuyruktan BİR komut alır, komutun gerçek
 * işini yapan `realX` de bitince kendini yeniden zamanlar. Kuyruk boşalınca bu
 * zincir KOPAR; o yüzden kuyruğa yeni bir komut girdiğinde pompanın yeniden
 * başlatılması gerekir. Yoksa kuyruk boşaldıktan SONRA verilen her kaplumbağa
 * komutu -- `canlandır`, `tuşaBasınca`, `zamanlayıcı` gövdelerinden verilenlerin
 * hepsi -- sessizce yutulur.
 */
private[kojo] class PompaDurumu {
  private var başladı = false
  private var boşta = true

  /** Kaynaklar yüklendi, pompa ilk kez çalıştırılıyor. */
  def başlat(): Unit = {
    başladı = true
    boşta = false
  }

  /** Kuyruğa komut girdi. true dönerse pompayı zamanlamak GEREKİR. */
  def komutGirdi(): Boolean =
    if (başladı && boşta) {
      boşta = false
      true
    }
    else false

  /** Pompa kuyruğu boş buldu: zamanlama zinciri burada kopuyor. */
  def kuyrukBoşaldı(): Unit = boşta = true

  def boştaMı: Boolean = boşta
  def başladıMı: Boolean = başladı
}

class Turtle(x: Double, y: Double, forPic: Boolean = false, costume: String = null)(implicit kojoWorld: KojoWorld)
  extends TurtleAPI
  with RichTurtleCommands
  with Boyacı {
  private[kojo] val turtleLayer = new PIXI.Container()
  private var turtleImage: PIXI.Container = _
  // Boyama AYRI bir yolda. Neden: PIXI 5'te her render yarım kalan çokgeni
  // finishPoly() ile olduğu yerde kapatıyor; kaplumbağa şekli kenar kenar
  // kurduğu ve canlandırma açıkken her kenar ayrı kareye düştüğü için dolgu
  // hiç oluşmuyordu (bkz. BoyamaYolu). Burada şekil her seferinde TAMAMLANMIŞ
  // bir drawPolygon olarak yayınlanıyor -- onu render kesemiyor.
  // `boyamaYolu` yalnız O ANDA çizilmekte olan şekli gösteriyor ve her köşede
  // yeniden yayınlanıyor; tamamlanan şekiller ise ŞEKİL BAŞINA kendi
  // Graphics'ine yazılıyor (bkz. çizimParçaları).
  private[kojo] val boyamaYolu = new PIXI.Graphics()
  private val boyamaÇokgeni = new BoyamaYolu

  // ŞEKİL BAŞINA DÜĞÜM (sorun #86). Eskiden tamamlanmış dolguların TAMAMI tek
  // `boyamaBitmiş`te, kalem izinin TAMAMI tek `turtlePath`te birikiyordu ve
  // katman sırası bir kez kuruluyordu: dolgular altta, kalem üstte. Sonuç:
  // ÖNCE çizilen bir şeklin kenarlığı SONRA çizilen bir şeklin dolgusunun
  // üstünde kalıyordu. Ölçüldü: 10 kare çizen betikte turtleLayer'ın 4 çocuğu
  // vardı -- "Turtle Fill (done)" (27 dolgu parçası) ve "Turtle Path" (40
  // çizgi parçası) -- yani bütün kenarlıklar bütün dolguların üstünde.
  //
  // Artık her tamamlanan şekil kendi dolgu düğümünü alıyor ve O ŞEKLİN kalem
  // izinin hemen ALTINA konuyor; kalem izi de orada donuyor, üstüne yeni bir
  // canlı yol açılıyor. Katman sırası böylece çizilme sırasıyla örtüşüyor:
  //   dolgu_1, kalem_1, dolgu_2, kalem_2, ..., boyamaYolu, canlı kalem, simge
  // Masaüstü Kojo'nun şekil-başına-PNode modeli de böyle.
  //
  // Maliyeti ölçüldü (PIXI 5, aynı çizim iki yerleşimle): şekil başına düğüm
  // çizim ÇAĞRISINI artırmıyor (PIXI küçük Graphics'leri tek partide
  // topluyor: 1 çağrı, tek biriktiricide 2), CPU tarafı ise şekil sayısıyla
  // büyüyor -- 100 şekilde 0.1, 500'de 0.6, 1000'de 1.0 ms/render (ortanca).
  // 16.7 ms'lik kare bütçesinin içinde; üstelik render istek üzerine
  // (KojoWorld.render -> requestAnimationFrame), yani biten çizim bedava.
  private[kojo] var turtlePath = new PIXI.Graphics()
  // Kalem izleri ve tamamlanmış dolgular AYRI listelerde. Tek liste olmaz:
  // dolgu düğümleri bilerek ÇİZGİSİZ doğuyor (`lineStyle(0,0,0)` -- kenarlığı
  // kalem çiziyor), ve TurtlePicture'ın KALEM dönüştürücüleri her parçaya
  // `lineStyle.visible = true` yazıyor. Karışık listeye uygulanınca dolgunun
  // ÜÇGENLEME DİKİŞİ görünür bir çizgiye dönüşüyordu: ölçüldü, dolgu
  // düğümünün üç parçası (w=0, görünür=false) iken (w=8, görünür=true) oldu ve
  // `kalemKalınlığı(8) * kalemRengi(yeşil) -> Resim{dolgulu kare}` mavi karenin
  // içinden kalın yeşil bir köşegen geçirdi.
  private[kojo] val kalemParçaları = ArrayBuffer[PIXI.Graphics]()
  private[kojo] val dolguParçaları = ArrayBuffer[PIXI.Graphics]()
  /** Dolgu biçemi için hepsi: kalem izleri de dolgu taşıyabiliyor (nokta()
    * daireleri, açık boyama). */
  private[kojo] def çizimParçaları: Seq[PIXI.Graphics] =
    kalemParçaları.toSeq ++ dolguParçaları.toSeq
  private[kojo] val turtlePathPoints = ArrayBuffer[(Double, Double)]()
  var prevMoveTo: Option[Point] = None
  // PIXI 5'te yol, çizimler arasında boşaltılabildiğinden (bkz.
  // PixiUyum.yoluSürdür) kalemin son noktasını kendimiz tutuyoruz.
  private var sonYolX = x
  private var sonYolY = y

  private def turtlePathMoveTo(x: Double, y: Double): Unit = {
    boyamayıİşle() // kalem kalkık taşınma çokgeni bitiriyor
    boyamaÇokgeni.taşındı(x, y)
    boyamayıKirlet()
    turtlePath.moveTo(x, y)
    sonYolX = x; sonYolY = y
    prevMoveTo = Some(Point(x, y))
    //    turtlePathPoints += ((x, y))
  }

  private def turtlePathLineTo(x: Double, y: Double): Unit = {
    prevMoveTo.foreach { pt =>
      turtlePathPoints += ((pt.x, pt.y))
      prevMoveTo = None
    }

    PixiUyum.yoluSürdür(turtlePath, sonYolX, sonYolY)
    turtlePath.lineTo(x, y)
    sonYolX = x; sonYolY = y
    turtlePathPoints += ((x, y))
    boyamaÇokgeni.çizildi(x, y)
    boyamayıKirlet()
  }

  /**
   * Boyama çokgenini yeniden yayınlar.
   *
   * Her kenarda bütün çokgeni yeniden çizmek pahalı görünüyor ama nokta listesi
   * boya değişince ve kalem kalkık taşınmada sıfırlanıyor, yani n tek bir şeklin
   * köşe sayısı -- kare için 5. Şeklin TAMAMINI tek seferde yayınlamak, yarım
   * yolu renderın kesmesine karşı tek güvenilir yol.
   */
  /** O anki çokgen bittiyse kalıcı katmana yaz -- sonraki clear() onu silmesin. */
  private def boyamayıİşle(): Unit = {
    if (fillBoya != null && boyamaÇokgeni.alanVarMı) {
      val dolgu = new PIXI.Graphics()
      dolgu.name = "Turtle Fill"
      dolgu.lineStyle(0, 0, 0)
      PixiUyum.boyamayaBaşla(dolgu, fillBoya)(() => kojoWorld.render())
      // bitti = true: KALICI düğüm, yani şekil tamamlandı -- not gerçek nokta
      // sayısını söyleyebilir (bkz. ÜçgenlemeUyarısı, #125).
      üçgenleriÇiz(dolgu, bitti = true)
      dolgu.endFill()
      PixiUyum.tazele(dolgu)
      // Dolgu, O ŞEKLİN kalem izinin hemen ALTINA: kenarlık kendi dolgusunun
      // üstünde kalsın, ama sonraki şeklin dolgusu bu kenarlığı örtebilsin.
      turtleLayer.addChildAt(dolgu, turtleLayer.getChildIndex(turtlePath))
      dolguParçaları += dolgu
      kalemYolunuDondur()
    }
  }

  /** Şekil bitti: o ana dek biriken kalem izi olduğu yerde donuyor, üstüne
    * yeni bir canlı yol açılıyor. Sıranın kuyruğu her zaman
    * [boyamaYolu, canlı kalem, simge] olmalı, o yüzden üçü yeniden üste alınıyor
    * (PIXI'de var olan bir çocuğu addChild etmek onu en üste taşır). */
  private def kalemYolunuDondur(): Unit = {
    turtlePath = new PIXI.Graphics()
    turtlePath.name = "Turtle Path"
    kalemParçaları += turtlePath
    turtleLayer.addChild(boyamaYolu)
    turtleLayer.addChild(turtlePath)
    if (!forPic && turtleImage != null) turtleLayer.addChild(turtleImage)
    // Yeni çizer biçemsiz doğuyor; KALEMİ geri koyuyoruz -- dolguyu DEĞİL.
    // Kalem yoluna açık bir beginFill koymak, PIXI'nin her çizimde çağırdığı
    // finishPoly'nin o yolu kapatıp DOLU çokgen olarak boyamasına yol açıyor:
    // kalem izi, altındaki gerçek dolgunun üstünü kendi rengiyle örtüyor.
    // Dolgu boyamaYolu'nun (ve biten şekiller için boyamayıİşle'nin) işi.
    // Ölçüldü (#126, ornekler/14-agir-dolgu.kojo): burada fillBoya hâlâ ESKİ
    // renk olduğu için ikinci şeklin kalem yolu MAVİ dolguyla doğuyor ve
    // altındaki kırmızı dolguyu örtüyor -- iki gül de mavi görünüyor.
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    // Yolun sürekliliği: yoluSürdür boş yolu zaten sonYol'dan başlatıyor, ama
    // arada moveTo gelmeyen yollar (realSetFillPaint) için burada da koyuyoruz.
    turtlePath.moveTo(sonYolX, sonYolY)
  }

  /**
   * Dolgu bayatladı. Yayını YAPMIYOR, yalnız sıraya koyuyor: gerçek
   * `drawPolygon` render'dan hemen önce, kare başına bir kez çalışıyor
   * (bkz. Boyacı ve KojoWorld.boyalarıBoşalt).
   *
   * Eskiden burada doğrudan yayın vardı ve bu yöntem HER KENARDA çağrıldığı
   * için n kenarlı bir şekil n kez üçgenleniyordu -- oysa çokHızlı'da bütün
   * kenarlar tek render'a düşüyor. Ölçüldü (tan-theta, 241 nokta): 241 yayın,
   * 1 render.
   */
  private def boyamayıKirlet(): Unit = kojoWorld.boyaKirlendi(this)

  /**
   * Bekleyen dolguyu şimdi yayınla. Şekil her zaman TAMAMLANMIŞ olarak
   * (`drawPolygon`) yayınlanıyor -- bu kural değişmedi, yalnız kaç kez
   * yayınlandığı değişti. İdempotent: `clear()` ile başlıyor.
   */
  private[kojo] def boyacıKatmanı: PIXI.Container = turtleLayer

  // Katmana AÇIK olarak "silindi" imi konmuşsa hayır. İmi yalnız silme
  // yolları koyuyor, yalnız addLayer kaldırıyor (bkz. PixiUyum.Silindiİmi).
  // Katmanın `parent`'ının null olmasına BAKMIYORUZ: pişirme de düğümü sahne
  // dışında tutuyor ve çizim yolu noteMutation çağırmadığı için çizmekte olan
  // bir resim pişebilir -- `parent` çıkarımı onu silinmiş sanardı (#109).
  private[kojo] def boyasıSürüyor: Boolean = !PixiUyum.katmanSilindiMi(turtleLayer)

  private[kojo] def boyayıYayınla(): Unit = {
    boyamaYolu.clear()
    if (fillBoya != null && boyamaÇokgeni.alanVarMı) {
      boyamaYolu.lineStyle(0, 0, 0) // kenarlığı kalem çiziyor, dolgunun kendi çizgisi olmasın
      PixiUyum.boyamayaBaşla(boyamaYolu, fillBoya)(() => kojoWorld.render())
      // Şekil çokgen olarak bitmedi (bitti = false), ama BÜYÜMEYİ bırakmış
      // olabilir: yayın kare sınırında olduğu için bu soru tam burada
      // sorulabiliyor (bkz. şekilDurmuş).
      üçgenleriÇiz(boyamaYolu, bitti = false, durdu = şekilDurmuş)
      boyamaYolu.endFill()
    }
    PixiUyum.tazele(boyamaYolu)
  }

  /**
   * Bu şekle bir daha nokta eklenmeyecek mi -- yani biriken dolgu süresi
   * NİHAİ mi, kesin biçimde ("N ms sürdü (M nokta)") bildirilebilir mi?
   *
   * İki koşul: pompa boşta (kuyruk boşaldı, zamanlama zinciri koptu) VE
   * dışarıdan komut gelemez (`KojoWorld.komutGelebilir`: canlandırma,
   * zamanlayıcı, girdi işleyicisi). `Resim{}` çizerinde ikinci koşul yok:
   * gövde `make()` içinde bir kez koşuyor, `çiz`/`sil` yeniden çizer ama
   * nokta eklemez; yani resmin şekli kuyruğu boşalınca canlandırma içinde de
   * bitmiştir (#143, 1. hipotez -- ölçüldü, `UcgenlemeResimTest`).
   *
   * NEDEN YAYIN ANINDA SORULUYOR, BOŞALMA ANINDA DEĞİL (#143'ün ölçümü):
   * pompa (#131) düz bir `yinele { ileri; sağ }` döngüsünde her komutu
   * kuyruğa girer girmez bitiriyor, yani kuyruk HER KOMUTTAN SONRA boşalıyor
   * -- boşalma "betik bitti" demek değil, "bu komut bitti" demek. İlk sürüm
   * boşalmada bir im kuruyor, sonraki yayın imi görünce kesin konuşuyordu.
   * Bütçe dolup pompa şeklin ortasında kareye teslim edince o kare YARIM
   * şekli yayınlıyor ve im oradaydı: 251 noktalık gül için "30 ms sürdü (21
   * nokta)" -- #134'ün "(193 nokta)" kusurunun yeni pompadaki yolu
   * (`UcgenlemeDilimTest`). Yayın ise HER ZAMAN kare sınırında
   * (`flushRender` bir rAF; sınamalar kareyi elle veriyor): kullanıcının
   * eşzamanlı betiği çoktan bitmiş, kuyruk hâlâ boşsa daha komut yalnız
   * `komutGelebilir`in saydığı yollardan gelir. Kararı oraya taşımak imi
   * ve boşalma anındaki bütün akıl yürütmeyi gereksiz kılıyor.
   *
   * `başladıMı` şart: giysi yüklenene dek pompa "boşta" ama hiç çalışmamış;
   * o evrede yayın olmaz ama olsaydı da "durdu" sayılmamalıydı.
   */
  private def şekilDurmuş: Boolean =
    pompa.başladıMı && pompa.boştaMı && (forPic || !kojoWorld.komutGelebilir)

  /**
   * Kare sınırında, bekleyen yayınlar yapıldıktan SONRA: şekil durmuşsa ve
   * bir yayın yoktu (son komutlar nokta eklemedi -- `sağ`, `sync`, `görün`),
   * biriken süreyi şimdi bildir. Yayın olduysa o zaten `durdu` ile konuştu
   * ya da konuşmadı; `bildirildi` ikinci notu keser.
   */
  override private[kojo] def durmaDenetimi(): Unit =
    if (şekilDurmuş) ÜçgenlemeUyarısı.şekilDurdu(şekilBirikimi)

  /**
   * Dolgu çokgenini NON_ZERO ile üçgenleyip PIXI'ye verir.
   *
   * NEDEN ÜÇGEN ÜÇGEN: PIXI'ye tek bir çokgen verirsek onu earcut üçgenliyor
   * ve earcut BASİT (kendini kesmeyen) çokgen varsayıyor -- masaüstü ise
   * `Path2D.Double` + `fill`, yani NON_ZERO. Kendini kesen yollarda iki taraf
   * farklı şekil çiziyordu (bkz. Ucgenleyici, oneri-kesisen-dolgu.md).
   *
   * BURADA ÖNCE "MESH DAHA PAHALI" YAZIYORDU (0.275 ms / 0.56 ms). O ölçüm
   * YANLIŞTI -- ısıtılmamış bir çizicide alınmış: Mesh'in ilk kurulumu shader
   * derlemesi + geometri yüklemesi yüzünden bir kerelik 14-30 ms ödüyor ve o
   * bedel Mesh'in hanesine yazılmış. Isıtılmış ölçümde (üç koşu, dönüşümlü
   * sıra, #125) Mesh HER İKİ YÖNDE de ucuz:
   *
   *   250 nokta x 7 kat   A kur 0.6-1.2  ren 2.2-3.4   | B kur 0.3-0.7 ren 0.2-0.3
   *   1000 nokta x 7 kat  A kur 1.6-5.0  ren 10.6-21.0 | B kur 0.5-2.3 ren 0.4
   *   toplam oran (A/B): 250'de 5-6 kat, 1000'de 10-14 kat
   *
   * Sayılar SwiftShader (yazılımsal çizici) üstünde; gerçek GPU'da render
   * tarafı küçülebilir, kurulum tarafı CPU olduğu için değişmemeli. Bağımsız
   * bir ikinci ölçüm daha BÜYÜK oran buldu (#129 incelemesi) ve farkın ölçüm
   * gölgesi olmadığını `gl.finish` ile, iki yolun aynı pikselleri çizdiğini
   * `readPixels` ile doğruladı; yani buradaki oran ihtiyatlı.
   *
   * SOĞUKTA KAZANÇ YOK: tek şekil çizip duran bir betikte iki yol birbirinin
   * gürültüsü içinde (yukarıdaki bir kerelik bedel yüzünden). Kazanç ikinci
   * şekilden itibaren başlıyor.
   *
   * NEDEN HÂLÂ ÜÇGEN ÜÇGEN: dördü de iKojo'nun Graphics'e bağlı yerleri, ve
   * bir mesh denemesi bu sırayla çarpar (hepsi #129 incelemesinde ölçüldü):
   *
   *   1. `PixiUyum.tazele` PATLAR. Bu yöntemin iki çağıranı da (satır 148 ve
   *      216) hemen ardından onu çağırıyor; `tazele` v5 yolunda
   *      `geometry.invalidate()` diyor, o da GraphicsGeometry'ye ait --
   *      PIXI.Geometry'de yok. İlk çarpılacak duvar bu, ve sesli çarpıyor.
   *   2. `glKaynaklarınıBırak` SESSİZCE atlar (en ciddisi). Kapısı
   *      `typeof finishPoly == "function"`; Mesh'te o yok, yani geometrisi
   *      hiç `dispose` edilmez -- #91/#95'te kapatılan GL sızıntısı geri gelir.
   *   3. Doku ve gradyan dolgusu. `beginTextureFill` eşlemeyi ŞEKLİN YEREL
   *      uzayında ve ŞEKİL BAŞINA yapıyor, o yüzden dolgu üçgen sınırlarını
   *      aşarak sürekli görünüyor. (Burada eskiden "DÜNYA uzayında" yazıyordu;
   *      yanlıştı, #132 incelemesi §2'de ölçüldü: düğüm 7 piksel kaydırılınca
   *      görüntü SAF ÖTELEME oluyor, yani desen şekille birlikte taşınıyor.
   *      `DokuBoya`nın kendi belgesi de "şeklin YEREL koordinatına" diyor.
   *      Ayrım kritik: "dünya" diye okuyan biri UV hesabına `worldTransform`u
   *      katar ve resim taşındıkça dolgusu YÜZEN bir mesh yazar.)
   *      Mesh'in kendi shader'ı ve UV'leri olur, süreklilik bedava gelmez --
   *      ama pahalı da değil: köşe başına tek `matris.applyInverse` ile sonuç
   *      Graphics yoluyla BİT BİREBİR aynı çıkıyor, dönüştürülmüş düğümde de
   *      (ölçüldü, `MeshUvSondaTest`).
   *   4. İsabet alanı -- ama sanıldığı gibi değil, ve KÜÇÜK. `Mesh.containsPoint`
   *      VAR ve gerçek bir üçgen sınaması yapıyor; kıran şey PIXI değil,
   *      `Utils.isabetAlanınıKur` içindeki kendi kapımız: `containsPoint`
   *      yalnız `graphicsData.length > 0` iken soruluyor, Mesh'te o dizi yok,
   *      dolayısıyla dolgu tıklanamaz olurdu. Düzeltme küçük: kapıya
   *      "graphicsData yoksa doğrudan sor" diye tek bir dal. DİKKAT --
   *      #118/#119'un kalem şeridi sınaması (`şeritteMi`) bu kapının
   *      İÇİNDE duruyor, yani mesh'i KAPSAMIYOR: mesh kapıyı hiç açmadığı
   *      için oraya hiç varılmaz. (#118'in nedeni başkaydı: orada kapı
   *      açıktı, PIXI kalem şeridini sınamıyordu.) Yan kazanç:
   *      Mesh'te `fillStyle.visible` olmadığı için #114/#116'nın görünmez
   *      dolgu çevirme dansı (ve 19 katlık üçgenleme bedeli) gereksizleşir.
   *
   *   PİŞİRME BU LİSTEDE DEĞİL: arandı, dayanağı yok. BakePolicy saf işlev
   *   (ad/interactive/lastMut/frame) ve pişirme herhangi bir DisplayObject'i
   *   dokuya çiziyor; mesh aynen pişerdi.
   *
   * YANİ BU DÖNGÜ ÖLÇÜLMÜŞ BİR TERCİH (#125, "yapılmayacak" diye kapandı):
   * mesh dilimin kendisinde 5-14 kat ucuz, ama dilim gülün ≤ %5-10'u --
   * #131'den sonra ölçüldü, drawPolygon kurulumu + render 1000 noktada
   * 7-13 ms / 131-141 ms, libtess %58. Uçtan uca kazanç ≤ %8, altı bağ ve
   * üç sav karşılığında. Bir sonraki kaldıraç üçgenlemeyi hızlandırmak
   * değil hiç üçgenlememek: stencil tamponuyla NON_ZERO dolgu, kayıt #147.
   */
  private def üçgenleriÇiz(gr: PIXI.Graphics, bitti: Boolean, durdu: Boolean = false): Unit = {
    if (!Üçgenleyici.kullanılabilir) {
      // Kütüphane sayfada yok. Çökmek yerine eski davranışa düşüyoruz: kendini
      // kesen yollar yanlış dolar ama öteki her şey yaşar. Konsola hata basıldı.
      gr.drawPolygon(scala.scalajs.js.Array(boyamaÇokgeni.düzDizi: _*))
      return
    }
    // Süre ÖLÇÜLÜYOR: pahalı dolguyu kullanıcıya bildirmek için (#68). Nokta
    // sayısına bakmak yetmiyor -- kesişmeyen 4000 nokta 6 ms, kesişen 1000
    // nokta 95 ms. Bedeli iki performance.now(); bkz. ÜçgenlemeUyarısı.
    val düz = boyamaÇokgeni.düzDizi
    val t0 = ÜçgenlemeUyarısı.saat()
    val ü = Üçgenleyici.nonzero(düz)
    ÜçgenlemeUyarısı.üçgenlemeBitti(şekilBirikimi, ÜçgenlemeUyarısı.saat() - t0, düz.length / 2, bitti, durdu)
    var i = 0
    while (i + 5 < ü.length) {
      gr.drawPolygon(scala.scalajs.js.Array(ü(i), ü(i + 1), ü(i + 2), ü(i + 3), ü(i + 4), ü(i + 5)))
      i += 6
    }
  }

  /** Bu ÇİZERİN şekil birikimi -- küresel olamaz, bkz. ŞekilBirikimi (#130). */
  private val şekilBirikimi = new ŞekilBirikimi

  private val tempForwardPath = new PIXI.Graphics()

  private var penWidth = 2d
  private var penColor = Color.red
  // Dolgu artık düz renk DEĞİL Boya: gradyan ve dokuma da olabiliyor.
  private var fillBoya: Boya = _
  private var penFontSize = 15
  private var penFontFamily: String = null
  private var penIsUp = false
  private var animationDelay = 1000l
  private val savedPosHe = new mutable.Stack[(PIXI.Point, Double)]
  private val savedStyles = new mutable.Stack[(Color, Boya, Double, Int, Boolean)]

  var commandQs = mutable.Queue.empty[Command] :: Nil
  private val pompa = new PompaDurumu

  // Kuyruğa eklemenin TEK giriş noktası: pompa boştaysa yeniden başlatır.
  // Bütün `commandQ.enqueue` çağrıları buradan geçmeli.
  //
  // YENİ KOMUT EKLERKEN: komutu işleyen realX MUTLAKA sonunda pompayı yeniden
  // zamanlamalı (`kojoWorld.scheduleLater(queueHandler)`) -- erken `return`
  // yollarında da. Unutulursa pompa `boşta = false` takılı kalır, `komutGirdi`
  // hep false döner ve kaplumbağa KALICI olarak donar (bkz. realArc2'nin
  // a == 0 yolu, bu yüzden düzeltildi).
  //
  // Not: `scheduleLater` işi dilime sığdığı sürece EŞZAMANLI koşturuyor
  // (KojoWorld.DilimMs, #131), yani kuyruk boşken verilen bir komut pompayı
  // kullanıcının çağrı yığınının içinde çalıştırabilir (canlandırma gecikmesi
  // 0 ise komut aynı karede biter). Sonuç doğru; yalnız pompanın her zaman
  // eşzamansız başladığı varsayılmasın.
  private def sıraya(komut: Command): Unit = {
    commandQ.enqueue(komut)
    if (pompa.komutGirdi()) kojoWorld.scheduleLater(queueHandler)
  }

  // giysi (costume) verilmişse kaplumbağa simgesi yerine o imge yüklenir;
  // yükleyici anahtarı ImagePic'teki gibi url'nin kendisi
  private val costumeKey = if (costume == null) "turtle32" else costume
  AssetLoader.addAndLoad(costumeKey, if (costume == null) "assets/images/turtle32.png" else costume, init)

  private def init(loader: PIXI.loaders.Loader, any: Any) {
    turtleLayer.name = "Turtle Layer"
    if (!forPic) {
      kojoWorld.addLayer(turtleLayer)
    }
    turtleImage = loadTurtle(x, y, loader)
    // Ad BakePolicy'de: KojoWorldImpl.kaplumbağaKatmanıMı gerçek kaplumbağayı
    // Picture{} katmanlarından bu çocuğa bakarak ayırıyor (öneAl'ın hedef sırası).
    turtleImage.name = BakePolicy.turtleIconName

    boyamaYolu.name = "Turtle Fill (in progress)"
    turtleLayer.addChild(boyamaYolu)
    turtlePath.name = "Turtle Path"
    turtleLayer.addChild(turtlePath)
    if (kalemParçaları.isEmpty) kalemParçaları += turtlePath
    if (!forPic) {
      turtleLayer.addChild(turtleImage)
    }
    initTurtleLayer()
    pompa.başlat()
    kojoWorld.runLater(0)(queueHandler)
  }

  private def initTurtleLayer(): Unit = {
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    turtlePathMoveTo(x, y)
    turtleImage.position.set(x, y)
    turtleImage.rotation = Utils.deg2radians(90)
  }

  // private[kojo]: TurkishTurtle bunları `sync` ile kuyruğun doğru noktasında
  // okuyor (konumuOku / yönüOku). Dışarıya AÇILMIYOR -- anlık bir okuma
  // kuyruktaki komutlardan önceki değeri verirdi.
  private[kojo] def position = turtleImage.position

  private def headingRadians = turtleImage.rotation

  private[kojo] def heading = Utils.rad2degrees(headingRadians)

  private def loadTurtle(x: Double, y: Double, loader: PIXI.loaders.Loader): PIXI.Container = {
    val turtle = {
      val rasterTurtle = new PIXI.Sprite(loader.resources(costumeKey).texture)
      if (costume == null) {
        rasterTurtle.position.set(-16, -16) // 32x32 kaplumbağa simgesini ortala
        rasterTurtle.alpha = 0.7
      }
      else {
        // Giysi imgesi: ImagePic'teki gibi y ekseninde çevriliyor (dünya ters,
        // çevrilmezse imge baş aşağı görünür), ayrıca konteynerin merkezine
        // oturtuluyor -- dönme/konum hep bu merkeze göre işliyor.
        rasterTurtle.setTransform(
          -rasterTurtle.width / 2, rasterTurtle.height / 2, 1, -1, 0, 0, 0, 0, 0)
      }
      rasterTurtle
    }
    val turtleHolder = new PIXI.Container()
    turtleHolder.addChild(turtle)
    turtleHolder
  }

  // Yüklü giysi listesi ve sıradaki (birsonrakiGiysi için); giysi ölçeği
  // giysi değişince korunsun diye ayrı tutuluyor.
  private var costumes: Vector[String] = Vector.empty
  private var costumeIndex = 0
  private var costumeScale = 1.0

  // Giysiyi yükleyip kaplumbağa simgesinin yerine koyar. Yükleme eşzamansız
  // olduğu için kuyruğu ANCAK yükleme bittikten sonra sürdürüyoruz; yoksa
  // sonraki komutlar eski simgeyle çalışırdı.
  private def realSetCostume(url: String): Unit = {
    AssetLoader.addAndLoad(url, url, { (loader: PIXI.loaders.Loader, _: Any) =>
      // Yükleme başarısızsa (404, bozuk resim) resource.error dolu ve texture
      // tanımsız olur; new Sprite(undefined) burada patlar ve KUYRUK TIKANIR --
      // komut kuyruğu bir daha ilerlemediğinden betiğin geri kalanı hiç
      // koşmaz. Onun için dokuyu kullanmadan önce denetliyoruz; hata varsa
      // giysi değişmiyor ama kuyruk normal akışına devam ediyor.
      val res = loader.resources(url).asInstanceOf[js.Dynamic]
      val doku = if (js.isUndefined(res) || res == null) js.undefined else res.texture
      if (js.isUndefined(doku) || doku == null) {
        println(s"Uyarı: giysi yüklenemedi: $url")
      }
      else {
        val s = new PIXI.Sprite(doku.asInstanceOf[PIXI.Texture])
        // loadTurtle'daki giysi yolunun aynısı: y'de çevir, merkeze otur, ölçekle
        s.setTransform(
          -s.width * costumeScale / 2, s.height * costumeScale / 2,
          costumeScale, -costumeScale, 0, 0, 0, 0, 0)
        turtleImage.removeChildren()
        turtleImage.addChild(s)
        kojoWorld.noteMutation(turtleImage)
        kojoWorld.render()
      }
      kojoWorld.scheduleLater(queueHandler)
    })(kojoWorld)
  }

  private def realSetCostumes(urls: Vector[String]): Unit = {
    costumes = urls
    costumeIndex = 0
    if (urls.isEmpty) kojoWorld.scheduleLater(queueHandler) else realSetCostume(urls(0))
  }

  private def realNextCostume(): Unit = {
    if (costumes.isEmpty) kojoWorld.scheduleLater(queueHandler)
    else {
      costumeIndex = (costumeIndex + 1) % costumes.length
      realSetCostume(costumes(costumeIndex))
    }
  }

  private def realScaleCostume(factor: Double): Unit = {
    costumeScale = costumeScale * factor
    if (turtleImage.children.length > 0) {
      val s = turtleImage.getChildAt(0).asInstanceOf[PIXI.Sprite]
      // Yalnız ölçeği çarpıyoruz; konumu da aynı çarpanla güncelleyince simge
      // merkezde kalıyor. İşaretlere DOKUNMUYORUZ: kaplumbağa simgesi (+1) ile
      // giysi (y'de -1, çevrilmiş) farklı işaret taşıyor, ikisi de korunmalı.
      s.position.set(s.position.x * factor, s.position.y * factor)
      s.scale.set(s.scale.x * factor, s.scale.y * factor)
      kojoWorld.noteMutation(turtleImage)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  def forward(n: Double): Unit = {
    sıraya(Forward(n))
  }

  def hop(n: Double): Unit = {
    sıraya(Hop(n))
  }

  def turn(angle: Double): Unit = {
    sıraya(Turn(angle))
  }

  def setAnimationDelay(delay: Long): Unit = {
    // Eksi gecikme, canlandırmayı SONSUZ döngüye sokar: realForward'ın
    // `animationDelay == 0` kestirmesi eksi değeri yakalamaz, delayFor eksi
    // değeri olduğu gibi döndürür (`< 1` dalı), dolayısıyla `frac` her karede
    // eksi çıkar ve `frac > 1` bitiş koşulu HİÇ sağlanmaz. queueHandler bir
    // daha zamanlanmaz: kaplumbağa kalıcı olarak donar ve tuvale tuvalden
    // taşan turuncu bir çizgi kalır. Ölçüldü (2026-09, PIXI 5 + Chromium):
    // aDelay=-1, kare=1..4 için frac=-7.5, -24.1, -74.1, -90.9, hepsinde
    // tamam=false; ekran 1. saniyeden 6. saniyeye kadar bayt bayt aynı.
    // Masaüstü Kojo da eksi gecikmeyi reddediyor (turtle/Turtle.scala:368).
    if (delay < 0) {
      throw new IllegalArgumentException("Canlandırma hızı eksi olamaz. Anında çizim için 0 ver.")
    }
    sıraya(SetAnimationDelay(delay))
  }

  def setPenThickness(t: Double): Unit = {
    sıraya(SetPenThickness(t))
  }

  def setPenColor(color: Color): Unit = {
    sıraya(SetPenColor(color))
  }

  def setPenFontSize(n: Int): Unit = {
    sıraya(SetPenFontSize(n))
  }

  override def setPenFontFamily(name: String): Unit = {
    sıraya(SetPenFontFamily(name))
  }

  override def dot(diameter: Int): Unit = {
    sıraya(Dot(diameter))
  }

  def setFillPaint(boya: Boya): Unit = {
    sıraya(SetFillPaint(boya))
  }

  def setFillColor(color: Color): Unit = {
    sıraya(SetFillColor(color))
  }

  // Kalemin şu an inik olup olmadığı ve canlandırma gecikmesi: masaüstünde
  // kalemİnikMi / canlandırmaHızı bunları okuyor. Kuyruğa GİRMEZ; kuyruktaki
  // komutlar bunları değiştirebileceği için okunan değer "şu ana kadar
  // kuyruğa alınanlardan sonraki" değil, "şu anki" durumdur.
  def penIsDown: Boolean = !penIsUp
  def animationDelayMs: Long = animationDelay

  def changePosition(x: Double, y: Double): Unit = {
    sıraya(ChangePosition(x, y))
  }

  // ---- giysi (costume) ----
  def setCostume(url: String): Unit = sıraya(SetCostume(url))
  def setCostumes(urls: String*): Unit = sıraya(SetCostumes(urls.toVector))
  def nextCostume(): Unit = sıraya(NextCostume)
  def scaleCostume(factor: Double): Unit = sıraya(ScaleCostume(factor))

  def setPosition(x: Double, y: Double): Unit = {
    sıraya(SetPosition(x, y))
  }

  def setHeading(theta: Double): Unit = {
    sıraya(SetHeading(Utils.deg2radians(theta)))
  }

  def moveTo(x: Double, y: Double): Unit = {
    sıraya(MoveTo(x, y))
  }

  def arc2(r: Double, a: Double): Unit = {
    sıraya(Arc2(r, a))
  }

  def write(text: String): Unit = {
    sıraya(Write(text))
  }

  def towards(other: Turtle): Unit = {
    sıraya(TowardsTurtle(other))
  }

  def towards(x: Double, y: Double): Unit = {
    sıraya(Towards(x, y))
  }

  def savePosHe(): Unit = {
    sıraya(SavePosHe)
  }

  def restorePosHe(): Unit = {
    sıraya(RestorePosHe)
  }

  def saveStyle(): Unit = {
    sıraya(SaveStyle)
  }

  def restoreStyle(): Unit = {
    sıraya(RestoreStyle)
  }

  def clear(): Unit = {
    // Yeni koşumun başı: sorun #73'ün notu yeniden düşebilsin (eşzamanlı kol;
    // gerekçe DuraklamaUyarısı.unut'ta).
    DuraklamaUyarısı.unut()
    sıraya(Clear)
  }

  def pause(seconds: Double): Unit = {
    // forPic DEĞİL: Resim{} gövdesindeki durakla o resmin kendi kuyruğunda ve
    // orada gerçekten geciktiriyor -- not düşmek yanlış alarm olurdu (#73).
    if (!forPic) DuraklamaUyarısı.duraklama()
    sıraya(Pause(seconds))
  }

  def penUp(): Unit = {
    sıraya(PenUp)
  }

  def penDown(): Unit = {
    sıraya(PenDown)
  }

  def invisible(): Unit = {
    sıraya(Invisible)
  }

  def visible(): Unit = {
    sıraya(Visible)
  }

  private[kojo] def sync(fn: () => Unit): Unit = {
    sıraya(Sync(fn))
  }

  private def queueHandler(): Unit = {
    if (commandQ.size == 0) {
      // Kuyruk boşaldı. Daha komut GELEMEYECEKSE betik bitmiş demektir ve bu
      // şekle bir daha nokta eklenmez -- biriken dolgu süresi artık nihai,
      // bildirilebilir (#134). Gelebiliyorsa susuyoruz; hangi üç yoldan
      // gelebildiği ve niye üçünün de sayılması gerektiği `komutGelebilir`de
      // yazılı (#140 incelemesi §1). Canlandırma o yolların yalnız biri, ve
      // en sık olanı: boşalma orada kare başına 1.63 kez oluyor. Resim{}
      // çizeri için kapı yok: gövdesi bitti, canlandırma ona nokta eklemez.
      // Karar BURADA verilmiyor, kare sınırında (bkz. şekilDurmuş): burası
      // düz bir döngüde her komuttan sonra çalışıyor. Yalnız aday yazılıyor.
      // Zincir burada kopuyor; bundan sonraki ilk komut pompayı yeniden başlatır.
      pompa.kuyrukBoşaldı()
      if (forPic || !kojoWorld.komutGelebilir) kojoWorld.kuyrukBoşaldı(this)
    }
    else {
      commandQ.dequeue() match {
        case Forward(n)  => realForward(n, penIsUp)
        case Hop(n)      => realForward(n, true)
        case Turn(angle) => realLeft(angle)
        case SetAnimationDelay(delay) =>
          animationDelay = delay; kojoWorld.scheduleLater(queueHandler)
        case SetPenThickness(t) => realSetPenThickness(t)
        case SetPenColor(c)     => realSetPenColor(c)
        case SetFillColor(c)    => realSetFillColor(c)
        case SetFillPaint(b)    => realSetFillPaint(b)
        case SetPosition(x, y)  => realSetPosition(x, y)
        case ChangePosition(x, y) =>
          realSetPosition(turtleImage.position.x + x, turtleImage.position.y + y)
        case SetCostume(url)     => realSetCostume(url)
        case SetCostumes(urls)   => realSetCostumes(urls)
        case NextCostume         => realNextCostume()
        case ScaleCostume(f)     => realScaleCostume(f)
        case SetHeading(theta)  => realSetHeading(theta)
        case MoveTo(x, y)       => realMoveTo(x, y)
        case Arc2(r, a)         => realArc2(r, a)
        case PopQ               => realPopQ()
        case Write(text)        => realWriteText(text)
        case SetPenFontSize(n)  => realSetPenFontSize(n)
        case SetPenFontFamily(f) => realSetPenFontFamily(f)
        case Dot(çap)           => realDot(çap)
        case Towards(x, y)      => realTowards(x, y)
        case TowardsTurtle(o)   => realTowards(o.position.x, o.position.y)
        case SavePosHe          => realSavePosHe()
        case RestorePosHe       => realRestorePosHe()
        case SaveStyle          => realSaveStyle()
        case RestoreStyle       => realRestoreStyle()
        case Clear              => realClear()
        case Pause(seconds)     => realPause(seconds)
        case PenUp              => realPenUpDown(true)
        case PenDown            => realPenUpDown(false)
        case Sync(fn)           => realSync(fn)
        case Invisible          => realInvisible()
        case Visible            => realVisible()
      }
    }
  }

  private def realSetPenThickness(t: Double): Unit = {
    penWidth = t
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    kojoWorld.scheduleLater(queueHandler)
  }

  val noColor = Color(0, 0, 0, 0)
  private def realSetPenColor(color0: Color): Unit = {
    val color = if (color0 == null) noColor else color0
    penColor = color
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPenFontSize(n: Int): Unit = {
    penFontSize = n
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPenFontFamily(f: String): Unit = {
    penFontFamily = f
    kojoWorld.scheduleLater(queueHandler)
  }

  // Masaüstü Kojo noktayı "kalem kalınlığı kadar minik bir ileri adım" ile
  // çiziyor; PIXI'de çizgi ucu varsayılan olarak düz olduğu için o yöntem köşeli
  // bir leke bırakırdı. Bunun yerine kalem rengiyle dolu bir daire çiziyoruz.
  private def realDot(çap: Double): Unit = {
    if (!penIsUp) {
      val x = turtleImage.position.x
      val y = turtleImage.position.y
      turtlePath.lineStyle(0, 0, 0) // dairenin kenarlığı olmasın
      turtlePath.beginFill(penColor.toRGBDouble, penColor.alpha.get)
      turtlePath.drawCircle(x, y, çap / 2)
      turtlePath.endFill()
      // kalemin durumunu geri koy -- dolguyu DEĞİL (bkz. kalemYolunuDondur, #126)
      turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
      turtlePathMoveTo(x, y)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetFillColor(color0: Color): Unit =
    realSetFillPaint(DüzBoya(if (color0 == null) noColor else color0))

  private def realSetFillPaint(boya: Boya): Unit = {
    // start new path
    turtlePath.lineStyle(penWidth, penColor.toRGBDouble, penColor.alpha.get)
    // set new fill
    boyamayıİşle() // biten çokgen ESKİ boyasıyla kalıcıya yazılsın
    fillBoya = boya
    // turtlePath'e beginFill YAPMIYORUZ: dolgu artık boyamaYolu'nun işi.
    // Eskiden buradaki beginFill yarım bir çokgen açıyordu ve ilk render onu
    // kesiyordu -- şeklin dolması yalnız gecikme 0 iken (bütün kenarlar tek
    // blokta) rastlantıyla çalışıyordu.
    boyamaÇokgeni.boyaKuruldu(turtleImage.position.x, turtleImage.position.y)
    boyamayıKirlet()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetPosition(x: Double, y: Double): Unit = {
    turtleImage.position.x = x
    turtleImage.position.y = y
    turtlePathMoveTo(x, y)
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSetHeading(theta: Double): Unit = {
    turtleImage.rotation = theta
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realForwardNoAnim(n: Double, hop: Boolean): Unit = {
    val p0x = position.x
    val p0y = position.y
    val (pfx, pfy) = TurtleHelper.posAfterForward(p0x, p0y, headingRadians, n)
    if (hop) {
      turtlePathMoveTo(pfx, pfy)
    }
    else {
      turtlePathLineTo(pfx, pfy)
    }
    PixiUyum.tazele(turtlePath)
    turtleImage.position.x = pfx
    turtleImage.position.y = pfy
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realForward(n: Double, hop: Boolean): Unit = {
    if (animationDelay == 0) {
      realForwardNoAnim(n, hop)
      return
    }

    turtleLayer.addChild(tempForwardPath)
    val p0x = position.x
    val p0y = position.y
    val (pfx, pfy) = TurtleHelper.posAfterForward(p0x, p0y, headingRadians, n)
    val aDelay = TurtleHelper.delayFor(n, animationDelay)
    //      println(s"($p0x, $p0y) -> ($pfx, $pfy) [$aDelay]")
    val startTime = window.performance.now()

    def forwardFrame(frameTime: Double): Unit = {
      val elapsedTime = frameTime - startTime
      val frac = elapsedTime / aDelay
      //        println(s"Fraction: $frac")

      if (frac > 1) {
        if (hop) {
          turtlePathMoveTo(pfx, pfy)
        }
        else {
          tempForwardPath.clear()
          turtleLayer.removeChild(tempForwardPath)
          turtlePathLineTo(pfx, pfy)
        }
        PixiUyum.tazele(turtlePath)
        turtleImage.position.x = pfx
        turtleImage.position.y = pfy
        kojoWorld.render()
        kojoWorld.scheduleLater(queueHandler)
      }
      else {
        val currX = p0x * (1 - frac) + pfx * frac
        val currY = p0y * (1 - frac) + pfy * frac
        if (!hop) {
          tempForwardPath.clear()
          tempForwardPath.lineStyle(penWidth, Color.orange.toRGBDouble, 1)
          tempForwardPath.moveTo(p0x, p0y)
          tempForwardPath.lineTo(currX, currY)
          //          tempGraphics.clearDirty += 1
        }
        turtleImage.position.x = currX
        turtleImage.position.y = currY
        window.requestAnimationFrame(forwardFrame)
      }
      kojoWorld.render()
    }

    window.requestAnimationFrame(forwardFrame)
  }

  private def realLeft(angle: Double): Unit = {

    def leftFrame(): Unit = {
      val angleRads = Utils.deg2radians(angle)
      turtleImage.rotation += angleRads
      kojoWorld.render()
      kojoWorld.scheduleLater(queueHandler)
    }

    leftFrame()
  }

  private def realArc2(r: Double, a: Double) {
    // a == 0: çizecek yay yok. pushQ'dan ÖNCE çıkıyoruz ve pompayı yeniden
    // zamanlıyoruz -- eskiden pushQ'dan sonra dönülüyordu, yani hem kuyruk
    // yığınında boş bir çerçeve kalıyor hem de zamanlama zinciri kopuyordu
    // (yay(r, 0)'dan sonraki bütün komutlar sessizce yutuluyordu).
    if (a == 0) {
      kojoWorld.scheduleLater(queueHandler)
      return
    }
    pushQ()

    def x(t: Double) = r * math.cos(t.toRadians)

    def y(t: Double) = r * math.sin(t.toRadians)

    def makeArc() {
      val head = heading
      if (r != 0) {
        val pos = position
        var currAngle = 0.0
        val trans = new PIXI.Matrix
        trans.translate(-r, 0)
        trans.rotate((head - 90).toRadians)
        trans.translate(pos.x, pos.y)
        val step = if (a > 0) 3 else -3
        val pt = new Point(0, 0)
        val aabs = a.abs
        val aabsFloor = aabs.floor
        while (currAngle.abs < aabsFloor) {
          currAngle += step
          // account for step size > 1
          while (currAngle.abs > aabsFloor) currAngle -= step / step.abs
          pt.set(x(currAngle), y(currAngle))
          trans(pt, pt)
          moveTo(pt.x, pt.y)
        }
        if (a.floor != a) {
          currAngle += (aabs - aabs.floor) * step
          pt.set(x(currAngle), y(currAngle))
          trans(pt, pt)
          moveTo(pt.x, pt.y)
        }
      }
      if (a > 0) {
        setHeading(head + a)
      }
      else {
        setHeading(head + 180 + a)
      }
    }

    makeArc()
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realMoveTo(x: Double, y: Double) {
    pushQ()
    val newTheta = towardsHelper(x, y)
    setHeading(newTheta.toDegrees)
    val d = distanceTo(x, y)
    forward(d)
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realWriteText(text: String): Unit = {
    if (!penIsUp) {
      val pixiText = new PIXI.Text(text)
      pixiText.setTransform(0, 0, 1, -1, 0, 0, 0, 0, 0)
      pixiText.position = position
      pixiText.rotation = (heading - 90).toRadians
      pixiText.style.fontSize = penFontSize
      if (penFontFamily != null) pixiText.style.asInstanceOf[scala.scalajs.js.Dynamic].fontFamily = penFontFamily
      pixiText.style.fill = penColor.toCanvas
      turtleLayer.addChild(pixiText)
      kojoWorld.render()
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSavePosHe(): Unit = {
    val pos = position
    savedPosHe.push((PIXI.Point(pos.x, pos.y), heading))
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realRestorePosHe(): Unit = {
    // realRestoreStyle ile aynı tehlike: eşleşen savePosHe yoksa pop fırlatır ve
    // istisna queueHandler içinde olduğu için komut pompası ölür.
    if (savedPosHe.isEmpty) {
      kojoWorld.scheduleLater(queueHandler)
      return
    }
    pushQ()
    val (newPosition, newHeading) = savedPosHe.pop()
    setPosition(newPosition.x, newPosition.y)
    setHeading(newHeading)
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  // the heading is computed here, inside the queue, so that it sees the position
  // left by the commands queued before this one
  private def realTowards(x: Double, y: Double): Unit = {
    turtleImage.rotation = towardsHelper(x, y)
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSaveStyle(): Unit = {
    savedStyles.push((penColor, fillBoya, penWidth, penFontSize, penIsUp))
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realRestoreStyle(): Unit = {
    // Eşleşen saveStyle olmadan çağrılırsa pop fırlatır; istisna queueHandler'ın
    // içinde olduğu için scheduleLater'a HİÇ ulaşılmaz ve komut pompası ölür --
    // sonraki bütün kaplumbağa komutları sessizce hiçbir şey yapmaz.
    if (savedStyles.isEmpty) {
      kojoWorld.scheduleLater(queueHandler)
      return
    }
    pushQ()
    val (color, fill, width, fontSize, penWasUp) = savedStyles.pop()
    setPenColor(color)
    setFillPaint(fill)
    setPenThickness(width)
    setPenFontSize(fontSize)
    if (penWasUp) penUp() else penDown()
    popQ()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realClear(): Unit = {
    // Şekil başına düğümler: donmuş parçaları katmandan da çıkar, yoksa
    // sil() sonrası eski çizim ekranda kalırdı. destroy() de şart -- ama
    // yalnız BİR ŞEKİL SINIFI için, ölçüldü.
    //
    // Tek biriktirici modelinde clear() yeten iki kalıcı çizer vardı; şimdi
    // her şekil YENİ Graphics doğuruyor. PIXI küçük geometrileri tek partide
    // topluyor, o yüzden onlar kendi GL kaynaklarını HİÇ almıyor: her karede
    // sil()+altı kare çizen bir canlandır döngüsünde renderer'ın
    // managedGeometries/managedBuffers sayıları destroy'lu da destroy'suz da
    // 120 kare boyunca 1/2'de sabit kaldı -- yani orada bırakılacak bir şey
    // yok.
    //
    // Parti DIŞI kalan çok köşeli şekiller (ölçümde 180 kenarlı) ise kendi
    // geometrisini ve tamponlarını alıyor, ve orada fark büyük (120 kare):
    //   destroy YOK : geometri 24 -> 172, tampon 48 -> 344   (doğrusal)
    //   destroy VAR : geometri 2-6, tampon 4-12              (sınırlı)
    // Deponun kendi notu da aynı gerekçeyi taşıyor: KojoWorld'de
    // bakeTexture.destroy(true).
    //
    // Çıkarılanlara başka kimse tutunmuyor: listeler hemen aşağıda boşalıyor
    // ve CANLI yol (dışarıdan TurtlePicture'ın tuttuğu turtlePath) bilerek
    // atlanıyor.
    (kalemParçaları ++ dolguParçaları).foreach { g =>
      if (g ne turtlePath) { turtleLayer.removeChild(g); g.destroy() }
    }
    kalemParçaları.clear()
    dolguParçaları.clear()
    kalemParçaları += turtlePath
    turtlePath.clear()
    turtlePathPoints.clear()
    boyamaYolu.clear()
    boyamaÇokgeni.temizle()
    şekilBirikimi.unut() // yarım şeklin birikimi sonrakine taşınmasın
    kojoWorld.bekleyenBoyayıUnut(this) // KENDİ yolunu sildi; ötekilerinki dursun
    initTurtleLayer()
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realPause(seconds: Double): Unit = {
    val t0 = window.performance.now()
    def pump(frameTime: Double): Unit = {
      if (frameTime - t0 > seconds * 1000) {
        kojoWorld.scheduleLater(queueHandler)
      }
      else {
        window.requestAnimationFrame(pump)
      }
    }
    pump(t0)
  }

  private def realPenUpDown(up: Boolean): Unit = {
    if (up) {
      penIsUp = true
    }
    else {
      penIsUp = false
    }
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realInvisible(): Unit = {
    turtleImage.visible = false
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realVisible(): Unit = {
    turtleImage.visible = true
    kojoWorld.render()
    kojoWorld.scheduleLater(queueHandler)
  }

  private def distanceTo(x: Double, y: Double): Double = {
    TurtleHelper.distance(position.x, position.y, x, y)
  }

  /**
   * Masaüstü Turtle.distanceTo(other): iki kaplumbağa arasındaki uzaklık.
   *
   * DİKKAT -- bu ANLIK bir okuma, kuyruğa girmiyor: değer döndürdüğü için
   * `çevir`/`noktayaDön` gibi komut kuyruğuna konamıyor. Yani daha işlenmemiş
   * komutlar varsa (ör. az önce `ileri(100)` dediysen) uzaklık o komutlardan
   * ÖNCEKİ konumlara göre hesaplanır. Kesin sonuç gerekiyorsa konumları
   * TurkishTurtle.konumuOku ile kuyruğun doğru noktasında okuyun.
   */
  def distanceTo(other: Turtle): Double =
    distanceTo(other.position.x, other.position.y)

  private def towardsHelper(x: Double, y: Double): Double = {
    TurtleHelper.thetaTowards(position.x, position.y, x, y, headingRadians)
  }

  private def commandQ = commandQs.head

  private def pushQ(): Unit = {
    commandQs = mutable.Queue.empty[Command] :: commandQs
  }

  private def popQ(): Unit = {
    sıraya(PopQ)
  }

  private def realPopQ(): Unit = {
    assert(commandQ.size == 0)
    commandQs = commandQs.tail
    kojoWorld.scheduleLater(queueHandler)
  }

  private def realSync(fn: () => Unit) = {
    fn()
    kojoWorld.scheduleLater(queueHandler)
  }
}
