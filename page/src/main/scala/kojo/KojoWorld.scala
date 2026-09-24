package kojo

import java.util.Random
import com.vividsolutions.jts.geom.Coordinate
import kojo.doodle.Color
import org.scalajs.dom.raw.{KeyboardEvent, UIEvent}
import org.scalajs.dom.{WheelEvent, document, html, window}
import pixiscalajs.PIXI
import pixiscalajs.PIXI.{Point, Rectangle, RendererOptions}
import pixiscalajs.PIXI.interaction.{InteractionData, InteractionEvent}

import scala.scalajs.js

trait KojoWorld {
  def canvasWidth: Double
  def canvasHeight: Double
  def addLayer(layer: PIXI.Container): Unit
  def removeLayer(layer: PIXI.Container): Unit
  // Bir resmin değiştiğini (taşındı/döndü/boyandı...) bildirir; pişirme
  // (bake) katmanı bunu son-değişim damgası olarak kullanır. Bkz. maybeBake.
  def noteMutation(node: PIXI.DisplayObject): Unit
  def scheduleLater(fn: => Unit): Unit
  def runLater(ms: Double)(fn: => Unit): Unit
  def render(): Unit

  /**
   * Dolgu notunun durumu (saat, sayaç, zaman kapısı), DÜNYA BAŞINA (#149).
   * Canlıda tek dünya var, davranış aynı; sınamada her dünya kendi
   * durumunu taşıyor -- bkz. ÜçgenlemeRaporu.
   */
  private[kojo] lazy val üçgenlemeRaporu: ÜçgenlemeRaporu = new ÜçgenlemeRaporu()

  /**
   * Dünyayı KAPAT (#149): bundan sonra rapor susar, bekleyen boyalar ve durma
   * adayları düşer. KojoWorldImpl ayrıca pompayı, çizimi ve canlandırmayı
   * durduruyor. Canlıda çağrılmıyor (sayfa tek dünya taşıyor, sayfayla ölüyor);
   * sınama fikstürleri bir sonraki dünyayı kurmadan önce ve takım sonunda
   * çağırıyor -- yoksa bir takımın dünyası (giysisi geç yüklenen kaplumbağa,
   * rAF döngüsü) sonraki takımın sırasında koşmaya devam ediyordu.
   * İdempotent.
   */
  private[kojo] def kapat(): Unit = {
    üçgenlemeRaporu.sustur()
    bekleyenBoyacılar.clear()
    durmaAdayları.clear()
  }

  // --- Bekleyen dolgular (tembel üçgenleme) ---------------------------------
  //
  // Kaplumbağa her kenarda dolgu çokgeninin TAMAMINI yeniden yayınlıyordu.
  // Şeklin tamamlanmış yayınlanması gerekiyor (bkz. BoyamaYolu), ama her
  // KENARDA yayınlanması gerekmiyor: render zaten bir requestAnimationFrame'e
  // toplanıyor, yani n kenar tek render'a düşüyor ve n-1 üçgenleme çöpe
  // gidiyor. Burada kirli çizerler biriktiriliyor; gerçek yayın render'dan
  // hemen önce, çizer başına bir kez yapılıyor.
  //
  // LinkedHashSet: aynı çizer kaç kez kirlenirse kirlensin bir kez yayınlanır
  // (asıl kazanç bu), ve kirlenme sırası korunur -- katman sırası önemli.
  private val bekleyenBoyacılar = scala.collection.mutable.LinkedHashSet.empty[Boyacı]

  /** Bekleyen boya sırasının boyu -- sınama dikişi (bkz. #109 savı). */
  private[kojo] def bekleyenBoyaSayısı: Int = bekleyenBoyacılar.size

  /**
   * Şimdiye dek yapılmış dolgu yayını sayısı -- sınama dikişi.
   *
   * #109'in savı bunu okuyor: silinmiş bir resim için yayın SÜRMEMELİ.
   * Sırayı okumak yetmiyordu, çünkü flushRender sırayı kare sınırından önce
   * boşaltıyor ve sınama hep 0 görüyordu (kırma sınamasıyla anlaşıldı).
   */
  private[kojo] var yayınSayısı = 0L

  /**
   * Bu dünya stencil dolgu çizebilir mi (#147): PIXI 5 ve stencil'li bir
   * WebGL bağlamı. false ise bütün dolgular libtess + Graphics'te kalıyor --
   * PIXI 4, stencil'siz bağlam, tuval çizici, ve sınama dünyası
   * (TestKojoWorld: çizicisi yok). KojoWorldImpl çizici kurulunca bağlamdan
   * okuyor; sınamalar elle çevirebilir.
   */
  private[kojo] var stencilDolgu = false

  // --- Kuyruğu boşalan çizerler (durma denetimi, #134/#143) ------------------
  //
  // "Kuyruk boşaldı" tek başına "betik bitti" demek değil: pompa (#131) düz
  // bir döngüde her komutu girer girmez bitiriyor, kuyruk her komuttan sonra
  // boşalıyor. Karar kare sınırında veriliyor (Turtle.şekilDurmuş); burada
  // yalnız kimin sorulacağı tutuluyor. Aday olmak bir kare istiyor -- yoksa
  // son komutları nokta eklemeyen bir betiğin (`sağ`, `sync`) notu, hiçbir
  // yayın kare istemediği için hiç düşmezdi.
  private val durmaAdayları = scala.collection.mutable.LinkedHashSet.empty[Boyacı]

  /** Çizerin kuyruğu boşaldı: kare sınırında durma denetimine girsin. */
  private[kojo] def kuyrukBoşaldı(b: Boyacı): Unit =
    if (durmaAdayları.add(b)) render()

  /** Çizerin dolgusu bayatladı: sıraya al ve bir render iste. */
  private[kojo] def boyaKirlendi(b: Boyacı): Unit = {
    // Silinmiş bir resmin çizeri sıraya GERİ GİRMESİN. Katmanı sıradan bir kez
    // düşürmek yetmiyor: kaplumbağanın komut kuyruğu silmeden SONRA da
    // boşalmaya devam ediyor ve her kenar burayı yeniden çağırıp çizeri sıraya
    // geri koyuyor (sorun #109). Ölçüldü, 40 karede: yalnız düşürme 77/82/118
    // -- sızıntı zamanlamaya bağlı; bu kapıyla 76/76/76, yani tam canlı iş.
    if (b.boyasıSürüyor) {
      bekleyenBoyacılar += b
      render()
    }
  }

  /**
   * Bekleyen dolguları yayınla. GERÇEK render'dan hemen önce çağrılmalı --
   * `KojoWorldImpl.flushRender` bunu yapıyor. Sınamalarda, gerçek bir
   * render'ın gireceği yerde elle çağrılıyor.
   */
  private[kojo] def boyalarıBoşalt(): Unit = {
    if (bekleyenBoyacılar.nonEmpty) {
      // Yayın sırasında yeniden kirlenme olabilir; önce kopyala ve boşalt ki
      // o kirlenme SONRAKİ kareye kalsın, burada sonsuz döngü olmasın.
      val sıra = bekleyenBoyacılar.toList
      bekleyenBoyacılar.clear()
      sıra.foreach { b => yayınSayısı += 1; b.boyayıYayınla() }
    }
    // Yayınlardan SONRA: yayını olan çizer kararı yayında verdi; olmayan
    // burada veriyor (bkz. Turtle.durmaDenetimi).
    if (durmaAdayları.nonEmpty) {
      val adaylar = durmaAdayları.toList
      durmaAdayları.clear()
      adaylar.foreach(_.durmaDenetimi())
    }
  }

  /**
   * Sahneden ÇIKARILAN bir katmanın çizerini bekleyen sıradan düşür.
   *
   * Yoksa silinmiş bir resmin dolgusu, kaplumbağanın komut kuyruğu boşaldıkça
   * yeniden yeniden üçgenleniyor: görünmeyen bir şekil için tam maliyet.
   * Ölçüldü (sorun #109; resimleriSil + canlandır, 120 nokta x 7 kat, 40 kare):
   * 80 canlı yayın 0 ms, 317-361 ÖLÜ yayın 512-531 ms -- üçgenleme süresinin
   * ~%96'sı sahnede olmayan şekillere gidiyordu.
   *
   * Yalnız verilen katmanın çizerini düşürüyor: hayatta kalan kaplumbağaların
   * bekleyen boyası duruyor (erasePictures'ın kendi notunun altını çizdiği
   * ayrım). Çizer ileride yeniden kirlenirse boyaKirlendi onu sıraya geri
   * koyar, yani bilgi kaybı yok.
   */
  private[kojo] def katmanınBoyasınıUnut(katman: PIXI.Container): Unit = {
    // Önce İMLE, sonra düşür -- iki ayrı sızıntıyı iki ayrı şey kapatıyor:
    //   düşürme : sırada DURAN yayını atıyor (asıl kazanç; tek başına
    //             40 karede 162-364'ten 77-118'e indiriyor)
    //   im      : çizerin sıraya GERİ girmesini engelliyor (komut kuyruğu
    //             boşaldıkça her kenar boyaKirlendi'yi yeniden çağırıyor);
    //             kalan 0-42'lik zamanlamaya bağlı sızıntıyı kapatıp sayıyı
    //             belirlenimci 76'ya, yani tam canlı işe çiviliyor
    // İkisi tek yerde duruyor ki ileride ayrı düşmesinler.
    //
    // İKİSİ DE ALT AĞACA YÜRÜR (#115). Eskiden ikisi de yalnız `katman`ın
    // KENDİSİNE bakıyordu; bir resim bir GPics'in içindeyse sahneden çıkan
    // düğüm grubun kabı, çizerin kendi turtleLayer'ı ise onun ALTINDA, yani
    // ne im tutuyordu ne `ne katman` eşleşiyordu. Ölçüldü, 40 kare:
    //   çıplak iki gül      74 / 74 / 74
    //   GPics(gül, gül)    512 / 533   ->  düzeltmeden sonra çıplakla aynı
    PixiUyum.altAğacıSilindiİmle(katman)
    if (bekleyenBoyacılar.nonEmpty) {
      // Çizer başına düşürüyoruz, küresel değil: hayatta kalan kardeşlerin
      // bekleyeni durmalı (#106/#111'in kuralı). Alt ağaç ölçütü de çizer
      // başına -- katmanı `katman`ın altındaysa düşer, değilse durur.
      val düşenler = bekleyenBoyacılar.filter(b => altındaMı(b.boyacıKatmanı, katman)).toList
      if (düşenler.nonEmpty) {
        bekleyenBoyacılar --= düşenler
        // GERÇEKTEN bir yayın düştüyse o çizerin KENDİ katmanına yaz: hiç
        // yayınlanmamış bir dolgu bu düşmeyle KAYBOLUYOR, resim yeniden
        // çizilince dolgusuz görünüyordu (#111'in erase() yolu için kapattığı
        // kusurun aynısı, öteki kapıdan). TurtlePicture.realDraw imi okuyup
        // yeniden kirletiyor. Grup kabına değil ÇOCUĞA yazılıyor: realDraw
        // çocuğun kendi tnode'una bakıyor (#115).
        düşenler.foreach(b => PixiUyum.düşenBoyayıİmle(b.boyacıKatmanı))
      }
    }
  }

  /** `alt`, `üst`ün kendisi mi ya da altındaki bir düğüm mü? */
  private def altındaMı(alt: PIXI.DisplayObject, üst: PIXI.Container): Boolean = {
    var d: PIXI.DisplayObject = alt
    var bulundu = false
    while (d != null && !bulundu) {
      if (d eq üst) bulundu = true else d = d.parent
    }
    bulundu
  }

  /**
   * BU çizerin bekleyen dolgusunu düşür -- kendi yolunu sildiği için.
   *
   * Çizer başına: küresel bir düşürme, dolgusu duran BAŞKA bir çizerin
   * boyasını sessizce yok ediyordu (A'nın bekleyeni, B `sil()` deyince
   * düşüyordu). Yayın istekliyken böyle bir pencere yoktu; tembelleşince
   * açıldı.
   */
  /** true: gerçekten bekleyen bir yayın vardı ve düşürüldü. Çağıranın bunu
    * bilmesi gerekiyor -- düşürülen yayın BİLGİ taşıyor (bkz.
    * TurtlePicture.erase). */
  private[kojo] def bekleyenBoyayıUnut(b: Boyacı): Boolean = bekleyenBoyacılar.remove(b)
  def moveToFront(obj: PIXI.DisplayObject): Unit
  def moveToBack(obj: PIXI.DisplayObject): Unit

  def setBackground(color: Color): Unit
  def setBackgroundH(c1: Color, c2: Color): Unit
  def setBackgroundV(c1: Color, c2: Color): Unit
  def frameDeltaTime: Double
  def frameCounter: Long
  def animate(fn: => Unit): Unit
  def animateWithState[S](initState: S)(nextState: S => S): Unit
  def timer(ms: Long)(fn: => Unit): Unit
  def setRefreshRate(fps: Int): Unit
  def stopAnimation(): Unit

  /**
   * Komut kuyruğu boşaldıktan SONRA daha komut gelebilir mi.
   *
   * `Turtle.şekilDurmuş` kare sınırında, kuyruk boşken buna bakıyor (#134):
   * false ise betik bitmiştir, şekle bir daha nokta eklenmez, ve biriken
   * dolgu süresi NİHAİDİR -- ancak o zaman "şu kadar sürdü (N nokta)" diye
   * kesin konuşulabilir.
   *
   * ÜÇ KAYNAK, ve üçü de sayılmak zorunda:
   *
   *   canlandırma  `canlandır` döngüsü dönüyor; bir sonraki kare yine çizer.
   *                Ölçüldü (#134): 250x7 gül, 30 kare -- döngüde 49 boşalmanın
   *                49'unda da `animating` true, tek atışlık betikte tek
   *                boşalmada false. Ayrım temiz.
   *   zamanlayıcı  `timer(ms)`: kuyruk TIKLAR ARASINDA boşalıyor, ve bir
   *                sonraki tık nokta ekleyecek. `timers` bunu tam tutuyor
   *                (`stopAnimation` temizliyor).
   *   girdi        `tuşaBasınca` / resim fare işleyicileri: kuyruk TUŞLAR
   *                ARASINDA boşalıyor. `ornekler/11-acilar-ve-radyan.kojo`
   *                böyle bir betik.
   *
   * İLK SÜRÜM YALNIZ BİRİNCİSİNİ SAYIYORDU (#140 incelemesi §1) ve ötekiler
   * için notu yanlış yere götürüyordu: 17 noktaya büyüyecek bir şekil için
   * "30 ms sürdü (5 NOKTA)" yazıyor, üstelik `bildirildi` imini koyduğu için
   * `erkenÇarpan`ın sonradan düşeceği DÜRÜST notu ("şimdilik N nokta; şekil
   * büyüdükçe artacak") büsbütün susturuyordu. Bu, #125'te bir kez
   * düzeltilmiş hatanın ta kendisi, ters yönde.
   *
   * EMİN DEĞİLSEK SUSUYORUZ: bu yüzden girdi imi bir kez kurulunca geri
   * ALINMIYOR. Pencereye eklenen tuş dinleyicileri zaten hiç kaldırılmıyor,
   * yani "artık yok" diyebileceğimiz bir an yok. Bedeli: aynı sayfada girdi
   * işleyicisi kuran bir betikten sonra boşalma yolu susar (`erkenÇarpan`
   * çalışmayı sürdürür). Yanlış sayı basmaktansa susmak yeğ -- deponun kendi
   * tarihi bunu söylüyor.
   */
  private[kojo] def komutGelebilir: Boolean

  /**
   * "Bu betik bir girdi işleyicisi kurdu" -- yani kuyruk boşalsa bile
   * kullanıcı bir tuşa/fareye dokununca daha komut gelebilir.
   *
   * Tek bir yerden çağrılması gerekmiyor, ama her ÇAĞRILMAYAN yer sessiz bir
   * kusur: o betikte not yanlış sayıyla kesin konuşur. Bugünkü çağıranlar:
   * `onKeyPress`, `onKeyRelease`, ve `Picture.etkileşimeAç` (bütün resim fare
   * işleyicilerinin tek boğazı).
   */
  private[kojo] def girdiİşleyicisiKaydedildi(): Unit
  def setup(fn: => Unit): Unit

  def drawStage(fillc: Color)(implicit kojoWorld: KojoWorld)
  // SOMUT (trait'te): KojoWorldImpl ile TestKojoWorld aynı kodu paylaşsın; eskiden
  // ikisinde ayrı kopya vardı ve test kopyası eski kalmıştı. Yalnız sahne kenarları
  // + Picture API kullanır.
  //
  // Bir hız bileşenini YALNIZ o kenara DOĞRU giderken ters çevir. Yoksa top kenara
  // girip yansıdıktan sonra hâlâ çakışıyorken tekrar yansır -> sağa-sola salınıp
  // kenara yapışır ve kenar boyunca kayar. (Kojo y-ekseni yukarı: üst = yüksek y,
  // alt = düşük y.) Büyüklük korunur; köşede iki bileşen birden döner (doğal yansıma).
  //
  // NOT -- masaüstü Kojo'dan bilinçli ayrılık: masaüstü `picture.bounceVecOffStage`
  // köşede sabit 45° (|v|/√2) verir ve aynı yapışma sorunu orada da var. Bu fizik
  // olarak doğru olan; masaüstüne de taşınması önerilir.
  def bounceVecOffStage(v: Vector2D, p: Picture): Vector2D = {
    val topCollides = p.collidesWith(stageTop)
    val leftCollides = p.collidesWith(stageLeft)
    val botCollides = p.collidesWith(stageBot)
    val rightCollides = p.collidesWith(stageRight)
    var nvx = v.x
    var nvy = v.y
    if (leftCollides && v.x < 0) nvx = -v.x
    if (rightCollides && v.x > 0) nvx = -v.x
    if (topCollides && v.y > 0) nvy = -v.y
    if (botCollides && v.y < 0) nvy = -v.y
    Vector2D(nvx, nvy)
  }
  def bouncePicVectorOffPic(pic: Picture, vel: Vector2D, obstacle: Picture, rg: Random): Vector2D
  def stageBorder: Picture
  def stageLeft: Picture
  def stageTop: Picture
  def stageRight: Picture
  def stageBot: Picture
  def stageArea: Picture

  def isKeyPressed(keyCode: Int): Boolean
  def onKeyPress(fn: Int => Unit): Unit
  def onKeyRelease(fn: Int => Unit): Unit
  def stagePosition: Point
  def positionOnStage(data: InteractionData): Point
  def isAMouseButtonPressed: Boolean
  def mouseMoveOnlyWhenInside(on: Boolean): Unit
  def size(width: Double, height: Double): Unit
  def zoomXY(xfactor: Double, yfactor: Double, cx: Double, cy: Double): Unit
  def resetView(): Unit
  def mouseXY: Point
  def erasePictures(): Unit
  def scroll(x: Double, y: Double): Unit
  def viewRotate(açı: Double): Unit
  def showAxes(): Unit
  def hideAxes(): Unit
  def showGrid(): Unit
  def hideGrid(): Unit
  def toggleFullScreenCanvas(): Unit
  def canvasBounds: Rectangle
  def noZoom(): Unit
}

// Pişirme kararının SAF (DOM'suz) çekirdeği -- Node testinde doğrulanabilsin
// diye ayrıldı (KojoWorldImpl'in geri kalanı Swing/PIXI/DOM'a bağlı).
object BakePolicy {
  val bakeAfterFrames = 3
  val bakeChildThreshold = 150

  val turtleLayerName = "Turtle Layer"
  val decorLayerName = "Decor Layer"
  // Gerçek kaplumbağanın simgesi. Turtle.init bu adı yalnız forPic OLMAYAN
  // kaplumbağaya veriyor, yani bir "Turtle Layer"ın gerçek kaplumbağa mı yoksa
  // Picture{} katmanı mı olduğunu ayırt eden şey bu (bkz. tepeSırası).
  // Sabit burada: Turtle.scala ile KojoWorldImpl iki ayrı yerde düz metin
  // yazsaydı biri değişince kaplumbağaKatmanıMı sessizce false dönerdi ve
  // hiçbir sınama yakalamazdı -- saf işlev bayrağı üreten yeri sınamıyor.
  val turtleIconName = "Turtle Icon"

  // Sahne kalabalıklaşınca ve yakınlaştırılmamışken pişir.
  def shouldConsider(childCount: Int, unzoomed: Boolean): Boolean =
    childCount >= bakeChildThreshold && unzoomed

  // Ucuz ön kontrol (kimlik + durağanlık). Etkileşim kontrolü pahalı (ağaç
  // dolaşımı) olduğundan ayrı: yalnız bunu geçen adaylar için hesaplanır.
  //
  // GERÇEK kaplumbağa pişirme dışı: pişmek onu sahneden çıkarıp dokuya gömer,
  // bir daha hareket edemez. Süs katmanı da öyle: sonraki eksenleriGizle /
  // ızgarayıGizle görünür bir etki yapamaz, ekranda hayalet eksen kalır.
  //
  // Ölçüt ADA DEĞİL bayrağa bakıyor (sorun #96). Eskiden `name != turtleLayerName`
  // yazıyordu, ama Turtle.init o adı forPic kaplumbağalara DA veriyor: Resim{}
  // katmanları da "Turtle Layer" adını taşıyor ve muafiyete takılıp HİÇ
  // pişmiyorlardı. Ölçüldü (canlandır döngüsü, 500 durağan Resim{}, 90 kare):
  // ad ölçütüyle sahne çocuğu 501'de sabit kalıyordu, yani baskılamanın
  // performans kazancı tam da kalabalık sahnede devre dışıydı.
  // Bayrağı çağıran taraf hesaplıyor (BakePolicy.gerçekKaplumbağaMı), tıpkı
  // kaplumbağaÖncesiTepe'nin yaptığı gibi -- burası saf kalsın diye.
  //
  // Bayrak ADI GEÇEREK (by-name) alınıyor ve EN SONA konuyor: hesaplaması
  // çocuk adlarından bir dizi kuruyor ve bu sınıftaki her düğümün adı zaten
  // "Turtle Layer", yani hedeflediğimiz kalabalık sahnede kısa devre hiç
  // tutmuyor. Durağanlık kontrolü bedavaya yakın; önce o eleyince taze
  // düğümler için dizi hiç kurulmuyor. (Ölçümde tek koşuda binlerce "taze"
  // eleme görülüyor -- karşılığı o kadar boşa dizi olurdu.)
  def isStaleCheap(gerçekKaplumbağa: => Boolean, name: String, lastMut: Long, frame: Long): Boolean =
    name != decorLayerName && (frame - lastMut > bakeAfterFrames) && !gerçekKaplumbağa

  // Bir sahne çocuğu pişmeye aday mı? Gerçek kaplumbağa, süs ve etkileşimli
  // düğümler muaf; yalnızca bakeAfterFrames karedir damgalanmayanlar aday.
  def isStaleCandidate(gerçekKaplumbağa: => Boolean, name: String, interactive: Boolean,
      lastMut: Long, frame: Long): Boolean =
    isStaleCheap(gerçekKaplumbağa, name, lastMut, frame) && !interactive

  // "arkaya at"ın hedef sırası: baştaki süs katmanlarının (eksen/ızgara) hemen
  // üstü. Süs, tuvalin süsü -- kullanıcının çizimi değil -- ve süsKatmanı onu
  // hep 0'a takıyor; en altta kalsın.
  //
  // Pişirme katmanına ayrıcalık YOK: bakeSprite'ın dokusu, pişirilen resimlerin
  // durduğu yer dışında saydam; düğümü onun altına koymak yalnız o pişmiş
  // RESİMLERİN arkasına koyar -- "arkaya at" zaten budur.
  def dipSırası(adlar: collection.Seq[String]): Int = {
    var i = 0
    while (i < adlar.length && adlar(i) == decorLayerName) i += 1
    i
  }

  /**
   * Bir sahne çocuğu GERÇEK kaplumbağanın katmanı mı?
   *
   * Ad TEK BAŞINA yetmez -- Turtle.init `forPic` olsun olmasın her katmana
   * "Turtle Layer" adını veriyor, yani Resim{} katmanları da o adı taşıyor.
   * Ayırt edici şey içerik: simge yalnız gerçek kaplumbağaya ekleniyor.
   *
   * Karar burada, çünkü ada bakmak iki yerde birden yanlış sonuç veriyordu:
   * tepeSırası'nda (resim öteki resimlerin altına düşüyordu) ve
   * erasePictures'ta (Resim{} katmanları HİÇ silinmiyordu -- sorun #91).
   * Üçüncü yer de (pişirme adaylığı, isStaleCheap) artık buna bakıyor -- eskiden
   * ada bakıyordu ve Resim{} katmanları hiç pişmiyordu (sorun #96).
   */
  // Çocuk adları IterableOnce: çağıran taraf TEMBEL verebilsin (bir Iterator),
  // dizi kurmak zorunda kalmasın. Sorun #96 bu işlevi sıcak yola soktu (her
  // kare, her durağan çocuk) ve heves eden `.map` orada ölçülebilir bir yük:
  // 500 resimli 95 karelik koşuda 125.626 çağrı, 251.252 çocuk adı dizgisi
  // (inceleme #102). `contains` zaten ilk eşleşmede duruyor; girdiyi de tembel
  // vermek diziyi tümden kaldırıyor. Sınamalar Seq geçiyor -- o da IterableOnce.
  def gerçekKaplumbağaMı(ad: String, çocukAdları: => IterableOnce[String]): Boolean =
    ad == turtleLayerName && çocukAdları.iterator.contains(turtleIconName)

  // "öne al"ın hedef sırası: SONDAKİ kaplumbağa katmanlarının hemen ALTI.
  //
  // Sahnede kaplumbağa katmanını tepede tutan bir şey yok (addLayer yalnız
  // stage.addChild yapıyor), o yüzden düğümü koşulsuz sona eklemek onu
  // kaplumbağa simgesinin de üstüne çıkarıyor ve simge kayboluyordu.
  // Masaüstünde bu olmuyor: orada kaplumbağa katmanı ayrı ve hep üstte.
  // Bu, dipSırası'nın simetriği.
  //
  // Ad yetmiyor: Turtle.init KENDİ katmanına da Picture{} katmanlarına da
  // "Turtle Layer" adını veriyor, yani ada bakmak resmi öteki RESİMLERİN de
  // altına atardı. Ayırt edici şey içerik -- gerçek kaplumbağanın katmanında
  // turtleIconName çocuğu var (Turtle.init onu yalnız forPic olmayana ekliyor).
  // Bu yüzden burası adları değil, çağıranın hesapladığı bayrakları alıyor.
  def tepeSırası(kaplumbağaMı: collection.Seq[Boolean]): Int = {
    var i = kaplumbağaMı.length
    while (i > 0 && kaplumbağaMı(i - 1)) i -= 1
    i
  }

  // Çırpınma sigortası: "en eski izi her kare sil" gibi kalıplar her kare
  // geri almaya yol açar (pişir->sil->geri al->pişir); bu tabandan yavaş.
  // Kısa pencerede art arda bu kadar geri alma olursa pişirmeyi kapat, taban
  // davranışa (hepsi canlı) dön.
  val unbakeStreakWindow = 10 // kare
  val maxUnbakeStreak = 5
  def shouldDisableAfterUnbake(streak: Int): Boolean = streak >= maxUnbakeStreak
}

class KojoWorldImpl extends KojoWorld {
  PIXI.Pixi
  private val fiddleContainer =
    document.getElementById("fiddle-container").asInstanceOf[html.Div]
  private val canvas_holder =
    document.getElementById("canvas-holder").asInstanceOf[html.Div]
  val margin = 4.0
  var canvasWidth = fiddleContainer.clientWidth - margin
  var canvasHeight = fiddleContainer.clientHeight - margin
  var canvasOriginX = -canvasWidth / 2
  var canvasOriginY = -canvasHeight / 2
  var screenWidth = canvasWidth
  var screenHeight = canvasHeight
  // Seçenek nesnesi biçimi: PIXI 5'in tek biçimi, PIXI 4 de kabul ediyor
  // (tarayıcıda ölçüldü: her ikisinde de 64x32 istendiğinde 64x32 çizici).
  // private[kojo]: KaynakSizintisiTest çizicinin kendi sayaçlarını
  // (geometry.managedGeometries / managedBuffers) okuyor -- sorun #91'in
  // ölçüsü o sayaçlar.
  private[kojo] val renderer = PIXI.Pixi.autoDetectRenderer(rendererOptions(canvasWidth, canvasHeight))
  // Stencil dolgu (#147): PIXI 5 VE stencil'li bağlam; elle kapatılabilir
  // (canlıda geri dönüş: tarayıcı konsolunda `localStorage.kojoDolgu =
  // "libtess"`, bkz. libtessİstendi). Elle kapalıysa panele bir satır: ayar
  // yenilemede duruyor ve hiçbir iz bırakmıyor -- #147 §7'nin "hep stencil"
  // karışıklığı tam bu sessizlikten çıktı.
  private val elleLibtess = libtessİstendi
  stencilDolgu = PixiUyum.beşVeÜstü && {
    val gl = renderer.asInstanceOf[js.Dynamic].gl
    !js.isUndefined(gl) && gl != null && gl.getContextAttributes().stencil.asInstanceOf[Boolean]
  } && !elleLibtess
  if (elleLibtess)
    ÜçgenlemeUyarısı.paneleYaz(
      "Eski dolgu yolu (libtess) elle açık: localStorage.kojoDolgu. Kaldırmak için konsolda: delete localStorage.kojoDolgu")

  /**
   * Eski yol elle istendi mi? İki kanal:
   *
   *  1. `localStorage.kojoDolgu = "libtess"` (tarayıcı konsolunda; silmek
   *     için `delete localStorage.kojoDolgu`). GÜVENİLİR kanal bu: tuval
   *     editörün aynı kökenli çerçevesinde koşuyor, depo ortak, ve ayar
   *     sayfa yenilenince de duruyor.
   *  2. Adreste `dolgu=libtess`. Editörün yönlendiricisi (`AppRouter`,
   *     `notFound -> Redirect.Replace`) sorgulu adresi kök sayfaya çevirip
   *     sorguyu DÜŞÜRÜYOR -- çoğu zaman çerçeve kurulmadan önce; canlıda
   *     ölçüldü (#147 §7): parametre "hep stencil" verdi. Kanal duruyor
   *     (yalın sayfa, sınama) ama editörde ona güvenilmez.
   *
   * Depo yalnız KENDİ penceresinden okunuyor: localStorage köken başına,
   * aynı köken aynı depo, çapraz/opak köken ikisinde de patlıyor -- üst
   * pencerenin deposunun bu pencereninkinden farklı cevap verebileceği bir
   * yapılandırma yok (#154 incelemesi ölçtü). Sorgu ise üst pencereden de
   * okunuyor: çerçevenin kendi adresi `/resultframe?theme=light`. try/catch
   * süs değil: `allow-same-origin` olmayan bir çerçevede kendi deposuna
   * erişim de SecurityError atıyor.
   */
  private def libtessİstendi: Boolean = {
    def depo(w: js.Dynamic): Boolean =
      try w.localStorage.getItem("kojoDolgu").asInstanceOf[String] == "libtess"
      catch { case _: Throwable => false }
    def sorgu(w: js.Dynamic): Boolean =
      try w.location.search.asInstanceOf[String].contains("dolgu=libtess")
      catch { case _: Throwable => false }
    val kendi = js.Dynamic.global.window
    val üst = kendi.parent
    val üstBaşka = üst.asInstanceOf[js.Any] ne kendi.asInstanceOf[js.Any]
    depo(kendi) || sorgu(kendi) || (üstBaşka && sorgu(üst))
  }
  private val interaction = renderer.plugins.interaction
  // private[kojo]: KaynakSizintisiTest sahnedeki çocuk sayısını sayıyor (#91).
  private[kojo] val stage = new PIXI.Container()
  window.addEventListener("resize", resize)
  init()

  def init() {
    canvas_holder.appendChild(renderer.view)
//    renderer.context.lineCap = "round"
    render()
    stage.name = "Stage"
    //    stage.width = canvasWidth
    //    stage.height = canvasHeight
    stage.interactive = true
    stage.setTransform(canvasWidth / 2, canvasHeight / 2, 1, -1, 0, 0, 0, 0, 0)
    mouseMoveOnlyWhenInside(true)
    initEvents()
  }

  def toggleFullScreenCanvas(): Unit = {
    try {
      import org.scalajs.dom.experimental.Fullscreen._
      if (window.document.fullscreenElement == null) {
        fiddleContainer.requestFullscreen()
      }
      else {
        window.document.exitFullscreen()
      }
    }
    catch {
      case _: Throwable =>
    }
  }

  def size(w: Double, h: Double): Unit = {
    canvasWidth = w
    canvasHeight = h
    screenWidth = canvasWidth
    screenHeight = canvasHeight
    canvasOriginX = -canvasWidth / 2
    canvasOriginY = -canvasHeight / 2
    //    stage.width = w
    //    stage.height = h
    renderer.resize(w, h)
    stage.setTransform(canvasWidth / 2, canvasHeight / 2, 1, -1, 0, 0, 0, 0, 0)
    resetBake() // doku eski boyut/dönüşüme göre pişmişti; yeniden kurulsun
    render()
  }

  def resize(event: UIEvent): Unit = {
    size(fiddleContainer.clientWidth - margin, fiddleContainer.clientHeight - margin)
  }

  //  def originAt(x: Double, y: Double): Unit = {
  //    stage.setTransform(x, y, 1, -1, 0, 0, 0, 0, 0)
  //    render()
  //  }
  //

  def zoomXY(xfactor: Double, yfactor: Double, cx: Double, cy: Double): Unit = {
    val cw = screenWidth
    val ch = screenHeight
//    stage.setTransform(cw / 2 - cx, ch / 2 + cy, xfactor, -yfactor, 0, 0, 0, 0, 0)
    stage.scale.set(xfactor, -yfactor)
    stage.position.set(cw / 2 - cx * xfactor, ch / 2 + cy * yfactor)
    canvasWidth = cw / xfactor
    canvasHeight = ch / yfactor.abs
    canvasOriginX = cx - canvasWidth / 2
    canvasOriginY = cy - canvasHeight / 2
    resetBake() // yakınlaştırma sahne dönüşümünü değiştirir; pişmiş doku
    // sabit çözünürlükte ekran-uzayı olduğundan geçersiz kalır
    render()
  }

  def canvasBounds: Rectangle = {
    new Rectangle(canvasOriginX, canvasOriginY, canvasWidth, canvasHeight)
  }

  def addLayer(layer: PIXI.Container): Unit = {
    stage.addChild(layer)
    // (yeniden) sahneye giren katman silinmiş değil: "silindi" imini kaldır.
    // İmi koyan tek yer silme yolları, kaldıran tek yer burası (#109).
    //
    // İSTİSNA -- süsKatmanı: eksen/ızgara katmanını addLayer'dan geçmeden,
    // doğrudan `stage.addChildAt(g, 0)` ile geri takıyor. Yani resimleriSil()
    // onu sahneden çıkarıp "silindi" diye imliyor ve o im BİR DAHA KALKMIYOR.
    // Bugün zararsız: süs katmanı bir Graphics, çizeri yok, imine kimse
    // bakmıyor. #115'in alt ağaç yürüyüşü imlenen kümeyi büyüttüğü için
    // yazılı duruyor -- ileride süs katmanına bir çizer bağlanırsa burası
    // sessizce yanlış davranır.
    PixiUyum.katmanınSilindiİminiSil(layer)
    // yeni düğümü bu kareyle damgala: yoksa hiç damgalanmadığından çizildiği
    // karenin sonunda pişer; "kur, birkaç kare sonra hareket ettir" kalıbı
    // pişir->unbake->pişir gel-gitine girerdi. Damgayla bakeAfterFrames kare
    // sessiz kalması gerekir.
    noteMutation(layer)
    render()
  }

  def removeLayer(layer: PIXI.Container): Unit = {
    if (bakedNodes.contains(layer)) {
      // silinen resim pişmiş dokudaydı -> dokuyu yeniden kur
      unbakeAll()
    }
    stage.removeChild(layer)
    // Sahneden çıkmak GL kaynağını bırakmıyor; bırakan tek şey dispose (#91).
    PixiUyum.glKaynaklarınıBırak(layer)
    katmanınBoyasınıUnut(layer) // #109: silinen resmin dolgusu yayınlanmaya devam etmesin
    render()
  }

  // --- İz/boya pişirme (PERFORMANS 2/2) --------------------------------------
  // canlandır döngüsünde her karede yeni resim çizen betikler (ör. yörünge izi)
  // sahneye durmadan çocuk ekliyor; PERFORMANS 1'den (kare başına tek çizim)
  // sonra bile o tek çizim ekrandaki N nesneyle orantılı, yani iz uzadıkça
  // yavaşlıyor. Çözüm: bir kaç karedir DEĞİŞMEYEN çocukları tek bir
  // RenderTexture'a (bakeTexture) pişirip sahneden çıkarmak. Böylece iz, kaç
  // nokta olursa olsun tek bir sprite (O(1)) olarak çiziliyor; hareket eden
  // cisimler (her kare konumuKur ile değişenler) canlı kalıyor.
  //
  // Sinyal: noteMutation her değişimde çocuğun tnode'una o karenin numarasını
  // damgalıyor. bakeAfterFrames karedir damgalanmayan bir çocuk "durağan boya"
  // sayılıp pişiriliyor. Hareketli cisimler her kare damgalandığından hiç
  // pişmiyor. Pişmiş bir çocuk sonradan değişirse (nadir) unbakeAll ile geri
  // alınıyor. Pişmiş resim NESNESİ geçerli kalır (çarpışma geometrisi tnode'un
  // sahnede olmasına bağlı değil), yalnızca ayrı bir DisplayObject olmaktan
  // çıkar. z-sırası: pişmiş boya, pişirilen en ALTTAKİ çocuğun yerine (bakeSprite)
  // iner; ondan aşağıdaki muaf canlı çocuklar (ör. çizSahne dolgusu) altında kalır.
  private var frameCount: Long = 0
  def frameCounter: Long = frameCount
  private var bakeSprite: PIXI.Sprite = _
  private var bakeTexture: PIXI.RenderTexture = _
  private var bakeMatrix: PIXI.Matrix = _
  // LinkedHashSet: geri almada pişirme sırasını koru (z-sırası bozulmasın)
  private val bakedNodes = scala.collection.mutable.LinkedHashSet.empty[PIXI.DisplayObject]

  // Damgayı SAHNE düzeyindeki ataya taşı: GPics/HPics/VPics gibi bileşik
  // resimlerde değişen iç çocuk değil, sahne çocuğu olan konteyner damgalanmalı;
  // yoksa konteyner "durağan" sanılıp pişer ve iç hareket donar. Pişmiş bir
  // düğümün parent'ı null olduğundan döngü orada durur.
  def noteMutation(node: PIXI.DisplayObject): Unit = {
    var n = node
    while (n != null && n.parent != null && (n.parent ne stage)) n = n.parent
    if (n != null) {
      n.asInstanceOf[js.Dynamic].__kojoMut = frameCount.toDouble
      if (bakedNodes.nonEmpty && bakedNodes.contains(n)) {
        // pişmişken değişti: bundan sonra pişirme adayı olmasın (thrash önlemi)
        n.asInstanceOf[js.Dynamic].__kojoNoBake = true
        unbakeAll()
      }
    }
  }

  // pişirme yalnızca sahne varsayılan (yakınlaştırılmamış, döndürülmemiş)
  // dönüşümdeyken güvenli: bakeMatrix/bakeSprite ne ölçeği ne de dönüşü hesaba
  // katıyor. Yakınlaştırmada (zoomXY) ve döndürmede (viewRotate) resetBake
  // pişmişi canlıya döndürür ve burada pişirme durur.
  private def stageUnzoomed: Boolean =
    stage.scale.x == 1.0 && stage.scale.y == -1.0 && stage.rotation == 0.0

  // etkileşimli düğümler (ya da etkileşimli torunu olanlar) pişirilmez:
  // sahneden çıkınca PIXI isabet testi onları görmez, fare olayları ölür.
  private def hasInteractive(d: PIXI.DisplayObject): Boolean = {
    d.interactive || (d match {
      case c: PIXI.Container => c.children.exists(hasInteractive)
      case _                 => false
    })
  }

  // atIndex: sprite'ın sahnedeki z-konumu. 0 DEĞİL -- pişirilen en alttaki
  // çocuğun yerine girer. Yoksa pişirmeden muaf canlı çocuklar (ör. çizSahne'nin
  // opak "Turtle Layer" dolgusu stageArea) pişmiş her şeyi örtüyordu: iz
  // noktaları pişer pişmez siyah dolgunun ARKASINDA kaybolup "siliniyordu".
  // SINIR: sprite'ın yeri İLK pişirmede sabitlenir; sonraki pişirmeler aynı
  // dokuya/z'ye gider. İlk pişirmeden SONRA sahneye eklenen muaf bir canlı çocuk
  // (oyun ortasında çizilen Resim{} figürü, etkileşimli düğüm) için terslik
  // sürer: ondan sonra çizilip pişen noktalar onun altına iner. Genel çözüm
  // bant başına ayrı sprite (düğüm-bazlı geri almayla birlikte, gelecek işi).
  private def ensureBakeLayer(atIndex: Int): Unit = {
    if (bakeSprite == null) {
      bakeTexture = js.Dynamic.global.PIXI.RenderTexture
        .create(canvasWidth, canvasHeight, js.undefined, renderer.resolution)
        .asInstanceOf[PIXI.RenderTexture]
      bakeSprite = new PIXI.Sprite(bakeTexture)
      bakeSprite.name = "Bake Layer"
      // bakeTexture ekran uzayında (piksel) birikiyor; bakeSprite sahne
      // dönüşümünü (öteleme w/2,h/2 + y-ters) tersine çevirsin ki pikseller
      // yerli yerine otursun.
      bakeSprite.scale.set(1, -1)
      // görünür sahne-yerel pencere [canvasOriginX, +canvasWidth] x ...;
      // merkez her zaman 0 DEĞİL (originAt/zoom sonrası). canvasOrigin kullan.
      bakeSprite.position.set(canvasOriginX, canvasOriginY + canvasHeight)
      stage.addChildAt(bakeSprite, math.max(0, math.min(atIndex, stage.children.length)))
      // sahne-yerel -> ekran-piksel dönüşümü. renderer.render(c, doku) çocuğu
      // KİMLİK ebeveyne bağlayıp sahne-yerel çizer; bu matrisi projeksiyon
      // olarak vererek ekran-uzayına taşıyoruz. (yalnızca yakınlaştırılmamış
      // durumda pişiriyoruz, o yüzden ölçek 1/-1.)
      bakeMatrix = new PIXI.Matrix()
      bakeMatrix.set(1, 0, 0, -1, -canvasOriginX, canvasOriginY + canvasHeight)
    }
  }

  private var bakeDisabled = false
  private def maybeBake(): Unit = {
    if (bakeDisabled) return
    try maybeBakeUnsafe()
    catch {
      case t: Throwable =>
        // pişirme bir performans iyileştirmesi; başarısız olursa animasyonu
        // ASLA durdurma, sessizce devre dışı bırak (canlı çocuklarla sürer)
        bakeDisabled = true
        try resetBake() catch { case _: Throwable => }
    }
  }

  private def maybeBakeUnsafe(): Unit = {
    val kids = stage.children
    // kalabalık + yakınlaştırılmamış (bakeMatrix/bakeSprite ölçeği hesaba katmaz)
    if (!BakePolicy.shouldConsider(kids.length, stageUnzoomed)) return
    val toBake = scala.collection.mutable.ArrayBuffer.empty[PIXI.DisplayObject]
    var i = 0
    while (i < kids.length) {
      val c = kids(i)
      if (c ne bakeSprite) {
        val stamp = c.asInstanceOf[js.Dynamic].__kojoMut
        val last = if (js.isUndefined(stamp)) -1L else stamp.asInstanceOf[Double].toLong
        // GERÇEK kaplumbağa muaf; Resim{} katmanları DEĞİL (ikisi de "Turtle Layer"
        // adını taşıyor, ayırt eden şey "Turtle Icon" çocuğu -- sorun #96).
        // Etkileşimli düğümler de muaf (isabet testi).
        // Ucuz kimlik/durağanlık kontrolünü ÖNCE yap; pahalı hasInteractive ağaç
        // dolaşımını yalnız o kontrolü geçen adaylar için çalıştır.
        // kaplumbağaKatmanıMı da ada bakıp kısa devre yapıyor, yani çocuk adları
        // dizisi yalnız "Turtle Layer" adlı çocuklar için kuruluyor.
        // __kojoNoBake: bir kez pişirilip sonra değişen düğüm (her saniye güncellenen
        // FPS/skor yazısı gibi) bir daha pişirilmez -- yoksa her değişimde tüm iz
        // çözülüp yeniden pişiyordu (bkz. noteMutation). SINIR: bayrak kalıcıdır,
        // resetBake dahil hiçbir yol temizlemez. Tek seferlik toplu değişiklik
        // (pişmiş 500 noktanın rengini bir kez değiştirmek) o düğümleri sonsuza dek
        // canlı bırakır (O(N) çizime dönüş). İyileştirme adayı: bayrak yerine sayaç
        // (ilk geri almada yeniden pişmeye izin, 2.-3.'de dışla) ya da uzun süre
        // durağan kalanın bayrağını silmek.
        val noBake = js.DynamicImplicits.truthValue(c.asInstanceOf[js.Dynamic].__kojoNoBake)
        if (!noBake && BakePolicy.isStaleCheap(kaplumbağaKatmanıMı(c), c.name, last, frameCount)
          && !hasInteractive(c)) {
          toBake += c
        }
      }
      i += 1
    }
    if (toBake.nonEmpty) {
      // sprite, pişirilen en ALTTAKİ çocuğun z-konumuna girer (toBake sahne
      // sırasında toplandı, head en alttaki)
      ensureBakeLayer(stage.children.indexOf(toBake.head))
      toBake.foreach { c =>
        // bakeMatrix'i projeksiyon olarak ver: çocuk sahne-yerelden ekran
        // pikseline taşınıp dokuya iner. clear=false -> iz birikir.
        renderer.asInstanceOf[js.Dynamic].render(c, bakeTexture, false, bakeMatrix, false)
        stage.removeChild(c)
        bakedNodes += c
      }
      render()
    }
  }

  // Pişmiş çocukları sahneye geri koyar ve pişmiş dokuyu atar. Herhangi bir
  // yerden (kullanıcı betiği, fare tekerleği zoom'u, pencere boyutu...)
  // çağrılabildiği için KENDİ güvenlik ağı var: patlarsa pişirmeyi devre dışı
  // bırak, animasyonu asla durdurma.
  //
  // NOT: PIXI 4.8.9 RenderTexture'da clear() YOK; bu yüzden dokuyu boşaltmak
  // yerine yok edip bakeSprite'ı kaldırıyoruz. Sonraki pişirme ensureBakeLayer
  // ile yeniden yaratır (hem WebGL hem Canvas renderer'da çalışır).
  //
  // Sınırlama (bilinçli): geri alma ya hep ya hiç. Kalabalık sahnede yanıp
  // sönen (görünür/görünmez) tek bir durağan resim her geçişte tüm dokuyu
  // yeniden pişirtir. İleride düğüm-bazlı geri alma düşünülebilir.
  private var lastUnbakeFrame = -1000L
  private var unbakeStreak = 0
  private def unbakeAll(): Unit = {
    if (bakedNodes.isEmpty) return
    // çırpınma takibi: kısa pencerede art arda geri alma sayılır. Yalnız
    // animasyon çalışırken: frameCount yalnız animateHelper'da arttığından,
    // stopAnimation SONRASI bir betiğin birçok pişmiş resmi silmesi/değiştirmesi
    // aynı "kare"de sayılıp sigortayı boşuna tetiklerdi (o KojoWorld için
    // pişirmeyi kalıcı kapatırdı). Animasyon dururken tek-seferlik geri almalar
    // çırpınma değildir.
    if (animating) {
      unbakeStreak = if (frameCount - lastUnbakeFrame < BakePolicy.unbakeStreakWindow) unbakeStreak + 1 else 0
      lastUnbakeFrame = frameCount
    }
    try {
      // pişirme sırasını koruyarak bakeSprite'ın hemen üstüne (onun bandına) koy;
      // sprite pişirilen en alttaki çocuğun yerinde olduğundan bu, pişme öncesi
      // z-sırasını korur
      var i = if (bakeSprite == null) 0 else stage.children.indexOf(bakeSprite) + 1 // sprite'ın hemen üstü
      bakedNodes.foreach { c => stage.addChildAt(c, i); i += 1 }
      bakedNodes.clear()
      if (bakeSprite != null) { stage.removeChild(bakeSprite); bakeSprite = null }
      // destroy(true): BaseRenderTexture + GL framebuffer'ını da bırak. Argümansız
      // destroy yalnız Texture kabuğunu koparır, render hedefi GL belleği sızar
      // (PIXI doku çöp toplayıcısı render hedeflerini atlar). RenderTexture'ın
      // no-arg destroy'unu gölgelememek için Texture'a yükselt.
      if (bakeTexture != null) { bakeTexture.asInstanceOf[PIXI.Texture].destroy(true); bakeTexture = null }
      render()
      // sürekli geri alma pişirmeyi kârsızlaştırır -> tabana dön (kalıcı).
      if (BakePolicy.shouldDisableAfterUnbake(unbakeStreak)) bakeDisabled = true
    }
    catch {
      case _: Throwable =>
        // son çare: ortada patlarsa geri eklenemeyen düğümler sahneden düşebilir
        // (kabul edilebilir; pişirme zaten kapanıyor). Animasyon durmaz.
        bakeDisabled = true
        bakedNodes.clear()
    }
  }

  // boyut/yakınlaştırma/silme pişmiş dokuyu geçersiz kılar. unbakeAll zaten
  // pişmişleri geri koyup dokuyu atıyor; ayrıca bir şey kalmadıysa no-op.
  private def resetBake(): Unit = {
    unbakeAll()
    if (bakeSprite != null) {
      stage.removeChild(bakeSprite)
      bakeSprite = null
    }
    if (bakeTexture != null) {
      bakeTexture.asInstanceOf[PIXI.Texture].destroy(true) // GL framebuffer'ı da bırak (sızıntı)
      bakeTexture = null
    }
  }

  def erasePictures(): Unit = {
    // Silinmeyen tek şey GERÇEK kaplumbağaların katmanı. Ölçüt ADA BAKMAK
    // DEĞİL: Turtle.init "Turtle Layer" adını Resim{} katmanlarına da veriyor,
    // ve ada bakan eski sürüm bu yüzden resim katmanlarını HİÇ SİLMİYORDU.
    //
    // Bu bir kaynak sızıntısından ÖNCE bir DOĞRULUK kusuruydu: resimleriSil()
    // Resim{} ile çizilmiş resimleri silmiyor, ekranda bırakıyordu (ölçüldü:
    // master'da çağrıdan sonra resim.tnode.parent hâlâ sahne, görünür=true).
    // Sahnenin ve GL kaynaklarının her karede büyümesi onun yan ürünüydü.
    // (Sorun #91, KaynakSizintisiTest. Doku tarafı için #95, pişirme kolu #96.)
    //
    // Bekleyen dolguları KÜRESEL olarak düşürmüyoruz: hayatta kalan katmanlar
    // duran kaplumbağaların katmanları, hepsini düşürmek onların boyasını
    // sessizce yok etmek olurdu (bkz. TembelSilmeTest).
    //
    // Ama katmanı silinen çizer de düşürülmüyor ve BU BEDAVA DEĞİL. Eskiden
    // öyle yazıyordu ("kopmuş bir Graphics'e yayın yapar; zararsız"); o not
    // #94'ten önce, Resim{} katmanlarının buradan HİÇ ÇIKMADIĞI dünyada
    // yazılmıştı. Artık çıkıyorlar, yani gerekçe değişti:
    //
    //   DOĞRULUK bakımından gerçekten zararsız -- görsel bir şey bozulmuyor,
    //   ve GL kaynağı da geri yüklenmiyor (ölçüldü: çizici sahne dışı katmanı
    //   ziyaret etmiyor, sayaçlar 0/0'da kalıyor).
    //   MALİYET bakımından değil: bir sonraki boyalarıBoşalt() sahnede olmayan
    //   şeklin ÇOKGENİNİ BAŞTAN üçgenliyor (n büyük ve kesişen şekillerde
    //   ~95 ms, bkz. #68).
    //
    // TurtlePicture.erase() bunu çizer başına düşürerek kapattı; burada
    // kapanmadı, çünkü erasePictures Picture.erase()'ten geçmiyor -- sahne
    // çocuklarını doğrudan atıyor, yani elinde katman var, çizer yok.
    //
    // BURADAKİ maliyeti ölçüldü (#109): her karede resimleriSil() + 10 dolu
    // resim çizen döngüde dolgu yayınlarının YARISI sahne dışına gidiyor ve
    // boyalarıBoşalt()'ın maliyetinin %96-99'u boşa (120 kenarda kare başına
    // ~1.34 ms -> ~0.02 ms). Sebebi draw()'un eşzamansızlığı: resmin dolgusu
    // bekleyene girdiğinde bir sonraki karenin resimleriSil()'i onu çoktan
    // sahneden çıkarmış oluyor. İki aday yol #109'da: yayın anında koruma
    // (Turtle.boyayıYayınla katmanı sahnede değilse çıksın) ya da burada
    // çizer başına düşürme.
    //
    // BU DALDA (b) yolu uygulandı: katmanınBoyasınıUnut hem katmanı "silindi"
    // diye imliyor hem de çizeri sıradan düşürüyor (Boyacı artık katmanına
    // gönderme taşıyor), yani erasePictures da kapalı. İm düşürmenin ÜSTÜNE
    // geliyor: düşürme sırada duran yayını atıyor, im çizerin sıraya geri
    // girmesini engelliyor (ölçüm: katmanınBoyasınıUnut'un yanındaki not).
    resetBake() // pişmiş boyayı da temizle (yoksa dokuda hayalet kalır)
    val children = stage.children.toBuffer
    children.foreach { c =>
      if (!kaplumbağaKatmanıMı(c)) {
        stage.removeChild(c)
        PixiUyum.glKaynaklarınıBırak(c) // bkz. removeLayer / #91
        katmanınBoyasınıUnut(c.asInstanceOf[PIXI.Container]) // #109
      }
    }
    render()
  }

  /**
   * Komut pompası: bir kareye kaç ms komut işi sığdırılıyor.
   *
   * Sınamalar tarayabilsin diye `var` (#131 ölçüt 2: değer ölçümle seçildi,
   * tablo `KuyrukPompasiTest`te). Birim SAYI DEĞİL SÜRE, bilerek: eski
   * `MaxBurst = 100` "100 komutta bir nefes" diyordu ama bir komutun bedeli
   * sabit değil; tepkisellik komut sayısıyla değil bloklanan süreyle ölçülür.
   */
  private[kojo] var DilimMs = 8.0
  private var pompaDönüyor = false
  private var kareBekleniyor = false
  private val bekleyenİşler = scala.collection.mutable.Queue.empty[() => Unit]

  /**
   * TRAMPOLİN (#131). İş doğrudan çağrılmıyor: kuyruğa giriyor, ve en dıştaki
   * çağrı bir `while` ile boşaltıyor. İç içe gelen `scheduleLater` (bir
   * komutun içinden, ör. `realSync`in kullanıcı geri çağrımından başka bir
   * kaplumbağaya komut) yalnız kuyruğa ekliyor, yığın büyümüyor. Eskiden iş
   * doğrudan çağrılıyor, `queueHandler` bir komut işleyip yine buraya
   * geliyordu -- karşılıklı özyineleme, derinlik = MaxBurst; 750 çalışıyor,
   * 1000 kırılıyordu. İlk iş yine EŞZAMANLI koşuyor: kuyruk boşken verilen
   * komut, eskisi gibi, kullanıcının çağrı yığınının içinde biter. Bunun
   * bir sonucu: düz bir `yinele { ileri; sağ }` döngüsünde pompa her komutu
   * kuyruğa girer girmez bitirdiği için HER KOMUT KENDİ KOŞUSU olur (ölçüldü:
   * 400k komut, 391k koşu, koşu başına ~1.7 µs). Bütçe koşular arası
   * biriktiğinden bu dilimi delmiyor -- bütçe dolunca komut kuyruğa girer,
   * kare sürdürür.
   *
   * KUYRUK tek yuva değil, bilerek: bir komutun içinden iki ayrı zamanlama
   * gelebiliyor (kullanıcı geri çağrımı başka kaplumbağayı uyandırır, sonra
   * `realSync` kendi pompasını yeniden zamanlar). Tek yuva ikincisini ezerdi
   * ve o pompa `boşta = false`ta takılı kalıp KALICI donardı -- `sıraya`nın
   * notundaki kusurun ta kendisi.
   *
   * Görünür tek fark: eskiden iç içe gelen iş HEMEN (özyinelemeyle) koşuyordu,
   * şimdi içinde bulunduğu komut bitince. Yani bir `konumuOku` geri çağrımı
   * içinde başka kaplumbağaya `ileri` deyip aynı geri çağrımda onun konumunu
   * okuyan kod artık eski konumu görür. O davranış zaten güvenilmezdi:
   * sayaç tam sınırdaysa bugün de ertelenirdi.
   */
  def scheduleLater(fn: => Unit): Unit = {
    if (kapandı) return // kapanmış dünya komut kabul etmiyor (#149)
    bekleyenİşler.enqueue(() => fn)
    if (!pompaDönüyor && !kareBekleniyor) pompayıSürdür()
  }

  private var kapandı = false

  /**
   * #149: temel sınıfınkine ek olarak pompayı (kuyruk boşalıyor, yeni komut
   * alınmıyor), çizimi (bekleyen rAF iptal) ve canlandırmayı durduruyor.
   * Zamanlanmış rAF geri çağrıları yine gelebilir; hepsi `kapandı`ya bakıp
   * hiçbir şey yapmadan dönüyor.
   */
  override private[kojo] def kapat(): Unit = {
    super.kapat()
    kapandı = true
    bekleyenİşler.clear()
    stopAnimation()
    if (renderPending) { renderPending = false; window.cancelAnimationFrame(renderHandle) }
  }

  /**
   * NEFES = SONRAKİ KARE (#131'in asıl kaldıracı). Eskiden nefes
   * `setTimeout(0)` ile alınıyordu ve tarayıcı iç içe zamanlayıcıyı 4 ms'ye
   * kenetliyor: hoplama başına ölçülen 4.2 ms, 250 noktalı gülde 5 hoplama =
   * ~20 ms, gülün %80'i -- üstelik o 4 ms'de kare de gelmiyordu (nefes
   * kısaydı). Kenetlenmeyen makro görev (MessageChannel) denendi ve
   * ELENDİ: hoplama 0 ms ama rAF aç kalıyor -- ölçüldü, kare aralığı 190
   * ms'ye çıktı ve güller boyanmadan siliniyordu. Nefesin sebebi zaten kare
   * (çizim, girdi); o yüzden dilim dolunca doğrudan bir sonraki kareye
   * teslim ediliyor. Kare gizli sekmede gelmez; `canlandır` ve `durakla` da
   * öyle, tutarlı.
   */
  private def kareyeTeslim(): Unit = {
    kareBekleniyor = true
    window.requestAnimationFrame { _ => kareBekleniyor = false; harcananMs = 0; if (!kapandı) pompayıSürdür() }
  }

  /**
   * BÜTÇE KARELER ARASI BİRİKİYOR, koşu başına değil. İlk sürüm dilim saatini
   * her koşuda sıfırlıyordu ve bir sınama bunu boşa çıkardı: gülleri Future
   * zinciriyle süren kod her gülü 2 ms'lik ayrı bir koşu yapıyor, devamı
   * mikro görev, arada olay döngüsüne hiç dönülmüyor -- 394 ms boyunca sıfır
   * kare, ve hiçbir koşu tek başına 8 ms'ye ulaşmadığı için pompa hiç teslim
   * etmiyordu. Doğru soru "bu koşu ne kadar sürdü" değil, "son kareden beri
   * ne kadar harcandı".
   *
   * Sıfırlama iki yoldan: (1) kalp atışı -- pompa iş yaptıysa bir rAF
   * isteniyor, o gelince sayaç sıfır (kare oldu); (2) iki koşu arasında
   * 2 ms'den uzun boşluk -- mikro görev zincirinde boşluk ~0.01 ms, gerçek
   * bir görev sınırından geçildiyse olay döngüsü zaten nefes almıştır.
   * İkincisi olmasa canlandır'ın kare başındaki koşusu, kalp atışı henüz
   * gelmemişse önceki karenin harcamasını devralırdı.
   */
  private var harcananMs = 0.0
  private var sonKoşuBitişi = 0.0
  private var kalpAtışıBekleniyor = false

  /**
   * TANI (sınama dikişi, `yayınSayısı` gibi): koşu sayısı, dilimi 8 ms'den
   * fazla aşan koşu sayısı, en uzun koşu. Pay 8, 10 değil (#145 incelemesi
   * §4): 8 + 10 = 18 ms bir kare bütçesinin (16.7) üstündeydi, yani her
   * koşuda 17 ms harcayıp her karede bir vsync kaçıran bir pompa savı
   * geçerdi; 8 + 8 = 16 karenin altında kalıyor ve GC payı yine var. Pompanın sözü "tek bir koşu
   * dilimi aşmaz -- bir komutun kendi süresi kadar pay hariç"; kare aralığı
   * ise tarayıcının işi. Tam takımda yük altında ölçüldü: 102-166 ms'lik
   * kare boşlukları sırasında en uzun koşu 8-14 ms, ve 14 ms'lik koşunun
   * içinde tek bir `Forward` 14 ms sürmüştü -- sıradan bir komutun
   * yapamayacağı şey, koşunun ortasına düşen bir GC durması. Bu yüzden sav
   * en uzun koşuya değil AŞAN KOŞU SAYISINA bakıyor: tek durma geçer, dilimi
   * yok sayan pompa her koşuda aşar.
   */
  private[kojo] var koşuSayısı = 0
  private[kojo] var aşanKoşuSayısı = 0
  private[kojo] var enUzunKoşuMs = 0.0

  private def kalpAtışıİste(): Unit =
    if (!kalpAtışıBekleniyor) {
      kalpAtışıBekleniyor = true
      window.requestAnimationFrame { _ => kalpAtışıBekleniyor = false; harcananMs = 0 }
    }

  /**
   * BİR İŞ PATLARSA ötekiler mahsur kalmıyor (#145 incelemesi §1). Kuyruk
   * artık paylaşılan DURUM: A'nın geri çağrımındaki bir hata (çoğu zaman
   * öğrencinin betiği) B'nin bekleyen işini de askıya alırdı -- ölçüldü, üç
   * iş, ortadaki fırlatınca üçüncüsü bir sonraki `scheduleLater`a kadar
   * kuyrukta bekliyordu, ve betik son komutunu vermişse o "sonraki" hiç
   * gelmiyor. Eski pompada paylaşılan kuyruk yoktu, bu kip de yoktu.
   *
   * Her iş kendi try'ında: fırlatan işin ardından döngü sürüyor. Hata
   * YUTULMUYOR -- ilki koşu bitince (ya da kare teslimiyle çıkarken)
   * yeniden fırlatılıyor, yani çağırana eskisi gibi ulaşıyor (üst düzeyde
   * öğrencinin betiğine, kare içindeyse window.onerror'a); sonrakiler
   * konsola. Fırlatan komutun kendi kaplumbağası eskisi gibi donuyor
   * (pompasını yeniden zamanlayamadı); ötekiler değil.
   */
  private def pompayıSürdür(): Unit = {
    if (pompaDönüyor || kapandı) return
    val koşuBaşı = window.performance.now()
    if (koşuBaşı - sonKoşuBitişi > 2.0) harcananMs = 0 // görev sınırından geçildi
    if (harcananMs >= DilimMs) { kareyeTeslim(); return } // bütçe zaten dolu
    pompaDönüyor = true
    var sayaç = 0
    var ilkHata: Throwable = null
    try {
      var sürüyor = true
      while (sürüyor && bekleyenİşler.nonEmpty) {
        sayaç += 1
        // Saat her komutta değil sekizde bir okunuyor; ucuz ama bedava değil.
        if ((sayaç & 7) == 0 && harcananMs + (window.performance.now() - koşuBaşı) >= DilimMs) {
          kareyeTeslim(); sürüyor = false // kuyruk duruyor, kare sürdürür
        }
        else {
          val iş = bekleyenİşler.dequeue()
          try iş()
          catch {
            case t: Throwable =>
              if (ilkHata == null) ilkHata = t
              else js.Dynamic.global.console.error("komut pompası: bir iş daha fırlattı", t.toString)
          }
        }
      }
    }
    finally {
      pompaDönüyor = false
      sonKoşuBitişi = window.performance.now()
      val koşu = sonKoşuBitişi - koşuBaşı
      koşuSayısı += 1
      if (koşu > DilimMs + 8) aşanKoşuSayısı += 1
      if (koşu > enUzunKoşuMs) enUzunKoşuMs = koşu
      harcananMs += koşu
      if (harcananMs > 0) kalpAtışıİste()
    }
    if (ilkHata != null) throw ilkHata
  }

  def runLater(ms: Double)(fn: => Unit): Unit = {
    window.setTimeout(() => if (!kapandı) fn, ms)
  }

  // PERFORMANS: render() her resim değişiminde çağrılıyor (taşı/döndür/boya/çiz
  // hepsi transformDone -> render zincirinden geçer). Eskiden her çağrı ANINDA
  // tam bir sahne çizimiydi; canlandır döngüsündeki tipik bir kare 5-10 tam
  // çizim yapıyordu (üç-cisim benzetiminde ölçüldü: kare başına 6). Artık
  // render() yalnızca işaret koyar; gerçek renderer.render(stage) kare başına
  // EN FAZLA BİR kez koşar. Ölçülen etki: kare başına çizim 6 -> 1, üç-cisim
  // fiddle'ında ~2 kat FPS. Görsel fark yok: tarayıcı zaten kareler arasında
  // boyamaz.
  //
  // Bekleyen çizim iki yoldan boşalır:
  //  - canlandır DIŞINDA (tek seferlik çizim/etkileşim): kaydedilen rAF'te.
  //  - canlandır İÇİNDE: animateHelper her kare fn'den sonra flushRender()
  //    çağırır. Bu olmadan, rAF geri çağrısı içinde kaydedilen istek tarayıcı
  //    tarafından BİR SONRAKİ kareye kuyruklanır ve ekran hep bir adım geride
  //    kalırdı (~16 ms gecikme). flushRender bekleyeni aynı karede boşaltır.
  private var renderPending = false
  private var renderHandle = 0
  def render(): Unit = {
    if (!renderPending && !kapandı) {
      renderPending = true
      renderHandle = window.requestAnimationFrame(_ => flushRender())
    }
  }

  private def flushRender(): Unit = {
    if (renderPending) {
      renderPending = false
      window.cancelAnimationFrame(renderHandle)
      // Bekleyen dolgular tam BURADA yayınlanıyor: kareye kadar biriken n
      // kirlenme, çizer başına tek üçgenlemeye iniyor.
      boyalarıBoşalt()
      renderer.render(stage)
    }
  }

  // öneAl / arkayaAt. Düğümü -- masaüstündeki Piccolo `PNode.moveToFront`/
  // `moveToBack` gibi -- KENDİ EBEVEYNİ içinde taşır.
  //
  // Eskiden doğrudan `stage.removeChild(obj)` çağrılıyordu. Düğüm sahnenin
  // DOĞRUDAN çocuğu değilse -- yani resim bir bileşiğin (Resim.dizi, küme,
  // satır, sütun...) içindeyse -- PIXI'nin removeChild'ı null döndürüyor,
  // ardından gelen addChild(null) da "Cannot read properties of null (reading
  // 'parent')" ile PATLIYOR ve betiği öldürüyordu. Çizilmemiş bir resimde
  // (parent == null) masaüstü sessizce hiçbir şey yapıyor; burada da öyle.
  private def ebeveyniniAl(obj: PIXI.DisplayObject): PIXI.Container = {
    if (bakedNodes.contains(obj)) unbakeAll() // pişmiş düğüm sahnede yok; önce geri al
    obj.parent
  }

  // Sahnedeki ilk süs-olmayan çocuğun sırası. Karar BakePolicy'de (saf, Node'da
  // test edilebilir); burada yalnız sahnenin adları toplanıyor.
  private def süsSonrasıDip: Int =
    BakePolicy.dipSırası((0 until stage.children.length).map(i => stage.getChildAt(i).name))

  // Gerçek kaplumbağanın katmanı mı? Ölçüt BakePolicy.gerçekKaplumbağaMı'da
  // (saf, Node'da sınanabilir); burada yalnız çocukların adları toplanıyor.
  // Ad kontrolü kısa devre yaptığı için dizi yalnız "Turtle Layer" adlı
  // çocuklar için kuruluyor.
  private def kaplumbağaKatmanıMı(c: PIXI.DisplayObject): Boolean =
    c.name == BakePolicy.turtleLayerName && {
      val kap = c.asInstanceOf[PIXI.Container]
      BakePolicy.gerçekKaplumbağaMı(
        c.name,
        Iterator.range(0, kap.children.length).map(i => kap.getChildAt(i).name)
      )
    }

  // Sahnedeki son kaplumbağa katmanı öbeğinin hemen altındaki sıra.
  private def kaplumbağaÖncesiTepe: Int =
    BakePolicy.tepeSırası((0 until stage.children.length).map(i => kaplumbağaKatmanıMı(stage.getChildAt(i))))

  def moveToFront(obj: PIXI.DisplayObject): Unit = {
    val ebeveyn = ebeveyniniAl(obj)
    if (ebeveyn != null) {
      ebeveyn.removeChild(obj)
      // Tepe sıra: kaplumbağa katmanlarının hemen altı; gerekçe
      // BakePolicy.tepeSırası'nda. (Koşulsuz sona eklemek simgeyi örtüyordu.)
      // Sıra removeChild'dan SONRA hesaplanmalı -- addChildAt'ın argümanı
      // orada değerlendiği için öyle oluyor, arkayaAt'taki gibi.
      // Bileşik içindeki davranış DEĞİŞMİYOR: orada sona eklemek doğru.
      if (ebeveyn eq stage) ebeveyn.addChildAt(obj, kaplumbağaÖncesiTepe) else ebeveyn.addChild(obj)
      render()
    }
  }

  def moveToBack(obj: PIXI.DisplayObject): Unit = {
    val ebeveyn = ebeveyniniAl(obj)
    if (ebeveyn != null) {
      ebeveyn.removeChild(obj)
      // Dip sıra: süs katmanlarının hemen üstü; gerekçe BakePolicy.dipSırası'nda.
      // (Bir ara burada dip koşulsuz 1'den başlıyordu; o zaman eksenler kapalıyken
      // arkayaAt düğümü pişirme katmanının ÜSTÜNE -- yani pişmiş resimlerin
      // ÖNÜNE -- taşıyordu, tam tersi.)
      ebeveyn.addChildAt(obj, if (ebeveyn eq stage) süsSonrasıDip else 0)
      render()
    }
  }

  def rendererOptions(
    width:             Double,
    height:            Double,
    antialias:         Boolean = true,
    resolution:        Double  = 1,
    // SAYDAM çizicide temizleme rengi SİYAH olmalı. PIXI transparent=true iken
    // bağlamı premultipliedAlpha ile açıyor; 0xFFFFFF ile temizleyince tampona
    // (255,255,255,0) yazılıyor, ki ön-çarpımlı alfada geçersiz bir piksel --
    // Chrome onu OPAK BEYAZ gösteriyor ve tuvalin CSS artalanı hiç görünmüyor.
    // (Ölçüldü: 0xFFFFFF -> köşe pikseli "255,255,255,0", ekran beyaz;
    //  0x000000 -> "0,0,0,0", CSS gradyanı görünüyor.)
    backgroundColor:   Int     = 0x000000,
    clearBeforeRender: Boolean = true
  ): js.Dynamic = {
    js.Dynamic
      .literal(
        width = width,
        height = height,
        antialias = antialias,
        resolution = resolution,
        backgroundColor = backgroundColor,
        clearBeforeRender = clearBeforeRender,
        // Artalan artık tuvalin CSS'inde (bkz. artalanaKoy). Çizici saydam
        // olmazsa opak temizleme rengi CSS'in üstünü örter.
        transparent = true
      )
  }

  // Artalan neden CSS -- ve masaüstünden nerede AYRIŞIYOR.
  //
  // Masaüstünde tek bir artalan davranışı yok, İKİ tane var
  // (SpriteCanvas.setCanvasBackground, 793-810):
  //
  //   tam opak DÜZ RENK -> setBackgroundWrapper -> Swing bileşeninin
  //     artalanı. Sahnenin dışında, kamerayla kaymıyor.
  //   BAŞKA HER ŞEY (gradyan dahil, çünkü GradientPaint bir Color değil)
  //     -> `case _` dalı: staging.Impl.API.rectangle(bounds.x, bounds.y, ...)
  //     ile SAHNEYE konan bir dikdörtgen, DÜNYA koordinatlarında ve
  //     çağrıldığı andaki görüş alanı kadar.
  //
  // Yani gradyanlarda masaüstü artalanı bir sahne düğümü: kamerayla birlikte
  // ölçekleniyor, kayıyor, ve görüş alanı dışına çıkınca kenarı görünüyor.
  // Burada CSS kullanmak düz renkte parity, GRADYANDA ise BİLİNÇLİ BİR
  // AYRIŞMA: CSS artalanı her zaman tuvalin tamamını dolduruyor, yaklaş/kaydır
  // onu hiç etkilemiyor.
  //
  // Ayrışma bilerek: bir öğrenciye "artalanı sarıdan turuncuya boya" dediğinde
  // beklediği şey tuvalin tamamı, yakınlaştırınca kenarı çıkan bir dikdörtgen
  // değil. Sahne düğümü yolunu seçseydik stage.scale/position'ı her karede
  // ters çevirmek gerekirdi. Ama parity SANILMASIN diye buraya yazıldı.
  //
  // Bilinen ikinci ayrışma: masaüstünde sil() artalanı varsayılana döndürüyor
  // (clearHelper -> setBackgroundWrapper(backgroundColor), currentBackgroundRect
  // siliniyor). iKojo'da erasePictures artalana dokunmuyor, yani sil()'den
  // sonra gradyan duruyor. Bu ayrışma bu değişiklikten önce de vardı (eski
  // renderer.backgroundColor da sil()'i atlatıyordu); gradyan artık gerçek
  // olduğu için daha görünür. Ayrı bir iş.
  private def cssRenk(c: Color): String = {
    val r = c.toRGBA
    "rgba(%d, %d, %d, %s)".format(r.r.get, r.g.get, r.b.get, r.a.get.toString)
  }

  private def artalanaKoy(css: String): Unit = {
    val biçem = renderer.view.asInstanceOf[html.Canvas].style
    biçem.background = css
  }

  def setBackground(color: Color): Unit = {
    artalanaKoy(cssRenk(color))
  }

  /**
   * Yatay gradyan: soldan sağa. Masaüstü `setBackgroundH`in karşılığı, ama
   * BİREBİR DEĞİL: orada gradyan sahneye konan bir dikdörtgen, burada tuvalin
   * CSS'i. Ayrıntı için yukarıdaki "Artalan neden CSS" notu.
   */
  def setBackgroundH(c1: Color, c2: Color): Unit =
    artalanaKoy("linear-gradient(to right, %s, %s)".format(cssRenk(c1), cssRenk(c2)))

  /**
   * Dikey gradyan: yukarıdan aşağıya. Masaüstü `setBackgroundV`in karşılığı,
   * ama BİREBİR DEĞİL -- bkz. yukarıdaki "Artalan neden CSS" notu.
   */
  def setBackgroundV(c1: Color, c2: Color): Unit =
    artalanaKoy("linear-gradient(to bottom, %s, %s)".format(cssRenk(c1), cssRenk(c2)))

  var animating = false

  private var girdiİşleyicisiVar = false
  private[kojo] def girdiİşleyicisiKaydedildi(): Unit = girdiİşleyicisiVar = true
  private[kojo] def komutGelebilir: Boolean =
    animating || timers.nonEmpty || girdiİşleyicisiVar
  def notAssetLoading = !AssetLoader.loading
  var timers = Vector.empty[Int]
  private var prevFrameTime: Double = _
  // Ekran tazeleme hızı kısıtlaması: 0 => her tarayıcı karesinde çalış (~60/sn).
  // Hedef aralık paylaşılır; her canlandır döngüsü kendi "son çalışma" damgasını
  // tutar (aşağıda animateHelper'a parametreyle geçilir) -- yoksa eşzamanlı iki
  // canlandır döngüsü aynı damgayı paylaşıp birbirini aç bırakır.
  private var refreshIntervalMs: Double = 0
  // Yaklaşık yarım tarayıcı karesi (~60Hz). rAF kareleri ~16.7ms ızgaralı ve
  // System.currentTimeMillis tam-sayı ms olduğundan, katı ">= aralık" eşiği hedef
  // hızı bir alt bölene yuvarlardı (örn. 30/sn pratikte ~20/sn). Bu tolerans, tam
  // bölen hedefleri (60/30/20/15/10...) tam tutturmayı sağlar.
  private val kareToleransıMs = 8.0
  def setRefreshRate(fps: Int): Unit = {
    refreshIntervalMs = if (fps <= 0) 0 else 1000.0 / fps
  }

  def frameDeltaTime: Double = {
    val currFrameTime = System.currentTimeMillis() / 1000.0
    if (prevFrameTime == -1) {
      prevFrameTime = currFrameTime
      0
    }
    else {
      val delta = currFrameTime - prevFrameTime
      prevFrameTime = currFrameTime
      delta
    }
  }

  def animate(fn: => Unit): Unit = {
    animating = true
    prevFrameTime = -1
    animateHelper(fn)
  }

  def animateHelper(fn: => Unit): Unit = animateHelper(fn, -1)

  // lastRunMs: bu döngünün fn'i en son çalıştırdığı duvar-saati anı, ms (-1: hiç).
  // Döngüye özgü (parametreyle geçilir) ki eşzamanlı canlandır döngüleri hedef
  // aralıktan bağımsız beslensin. rAF'ın verdiği zaman damgası yerine
  // System.currentTimeMillis kullanıyoruz (frameDeltaTime de öyle yapıyor).
  private def animateHelper(fn: => Unit, lastRunMs: Double): Unit = {
    window.requestAnimationFrame { _ =>
      var nextLast = lastRunMs
      if (notAssetLoading) {
        // Kısıtlama etkinse (setRefreshRate) hedef aralık dolana kadar bu kareyi atla
        val now = System.currentTimeMillis().toDouble
        val due = refreshIntervalMs <= 0 || lastRunMs < 0 || (now - lastRunMs) >= refreshIntervalMs - kareToleransıMs
        if (due) {
          nextLast = now
          frameCount += 1
          fn
          maybeBake()
          flushRender() // bu karede birikeni hemen boşalt (bir kare gecikme olmasın)
        }
      }
      if (animating && !kapandı) {
        animateHelper(fn, nextLast)
      }
    }
  }

  def animateWithState[S](initState: S)(nextState: S => S): Unit = {
    var state = initState
    animate {
      state = nextState(state)
    }
  }

  def setup(fn: => Unit): Unit = {
    window.requestAnimationFrame { _ =>
      if (notAssetLoading) {
        fn
      }
      else {
        setup(fn)
      }
    }
  }

  def stopAnimation(): Unit = {
    animating = false
    timers foreach { t =>
      window.clearInterval(t)
    }
    timers = Vector.empty[Int]
  }

  def timer(ms: Long)(fn: => Unit): Unit = {
    prevFrameTime = -1
    val handle = window.setInterval({ () =>
      if (notAssetLoading) {
        fn
      }
    }, ms)
    timers = timers :+ handle
  }

  lazy val noPic = TurtlePicture { t =>
  }(this)
  @volatile var stageBorder: Picture = _
  @volatile var stageLeft: Picture = _
  @volatile var stageTop: Picture = _
  @volatile var stageRight: Picture = _
  @volatile var stageBot: Picture = _
  @volatile var stageArea: Picture = _

  def clearStage() {
    stageBorder = noPic
    stageLeft = noPic
    stageTop = noPic
    stageRight = noPic
    stageBot = noPic
  }

  def drawStage(fillc: Color)(implicit kojoWorld: KojoWorld) {
    def left(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.forward(size)
    }
    def top(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.right()
      t.forward(size)
    }
    def right(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.right(180)
      t.forward(size)
    }
    def bottom(size: Double) = TurtlePicture { t =>
      t.setPenThickness(0)
      t.left()
      t.forward(size)
    }

    val cb = canvasBounds

    stageLeft = left(canvasHeight)
    stageLeft.translate(cb.x, cb.y)

    stageTop = top(canvasWidth)
    stageTop.translate(cb.x, cb.y + cb.height)

    stageRight = right(canvasHeight)
    stageRight.translate(cb.x + cb.width, cb.y + cb.height)

    stageBot = bottom(canvasWidth)
    stageBot.translate(cb.x + cb.width, cb.y)

    stageArea = TurtlePicture { t =>
      t.setFillColor(fillc)
      t.setPenColor(Color.darkGray)
      for (_ <- 1 to 2) {
        t.forward(canvasHeight)
        t.right()
        t.forward(canvasWidth)
        t.right()
      }
    }
    stageArea.translate(cb.x, cb.y)

    stageBorder = GPics(
      stageLeft,
      stageTop,
      stageRight,
      stageBot
    )

    stageArea.draw()
    stageBorder.draw()
  }

  // bounceVecOffStage: trait'te somut (KojoWorld) -- kopya buradan kaldırıldı.

  def collidesWithStage(p: Picture): Boolean = {
    val stageparts = List(stageTop, stageBot, stageLeft, stageRight)
    p.collision(stageparts).isDefined
  }

  def bouncePicVectorOffPic(pic: Picture, vel: Vector2D, obstacle: Picture, rg: Random): Vector2D = {
    // returns points on the obstacle that contain the given collision coordinate
    def obstacleCollPoints(c: Coordinate): Option[js.Array[Coordinate]] = {
      obstacle.picGeom.getCoordinates.sliding(2).find { cs =>
        val xcheck = if (cs(0).x > cs(1).x)
          cs(0).x >= c.x && c.x >= cs(1).x
        else
          cs(0).x <= c.x && c.x <= cs(1).x

        val ycheck = if (cs(0).y > cs(1).y)
          cs(0).y >= c.y && c.y >= cs(1).y
        else
          cs(0).y <= c.y && c.y <= cs(1).y
        xcheck && ycheck
      }
    }
    // returns vector for obstacle boundary segment that contains the collision point
    def obstacleCollVector(c: Coordinate) = makeVectorFromCollPoints(obstacleCollPoints(c))

    // creates a vector out of two (collision) points
    def makeVectorFromCollPoints(cps: Option[js.Array[Coordinate]]) = cps match {
      case Some(cs) =>
        Vector2D(cs(0).x - cs(1).x, cs(0).y - cs(1).y)
      case None =>
        println("Warning: unable to determine collision vector; generating random vector")
        Vector2D(rg.nextDouble, rg.nextDouble)
    }

    def collisionVector = {
      val pt = obstacle.intersection(pic)
      val iCoords = pt.getCoordinates

      if (iCoords.length == 0) {
        Vector2D(rg.nextDouble, rg.nextDouble).normalize
      }
      else {
        if (iCoords.length == 1) {
          val cv1 = obstacleCollVector(iCoords(0))
          cv1.normalize
        }
        else {
          val c1 = iCoords(0)
          val c2 = iCoords(iCoords.length - 1)
          makeVectorFromCollPoints(Some(js.Array(c1, c2))).normalize
        }
      }
    }
    def pullbackCollision() = {
      val velNorm = vel.normalize
      val v2 = velNorm.rotate(180)
      val velMag = vel.magnitude
      var pulled = 0
      while (pic.collidesWith(obstacle) && pulled < velMag) {
        pic.offset(v2)
        pulled += 1
      }
      pic.offset(velNorm)
    }

    pullbackCollision()
    val cv = collisionVector
    vel.bounceOff(cv)
  }

  var zoomEnabled = true
  def noZoom(): Unit = {
    zoomEnabled = false
  }

  // Tuvali başlangıç görünümüne döndür: dünya-(0,0) merkezde, yakınlaştırma 1.
  // (Fareyle kaydırma da stage.position'ı değiştirir; zoomXY onu geri kurar.)
  def resetView(): Unit = zoomXY(1, 1, 0, 0)

  // Tuvali dünya birimiyle kaydırır (masaüstü tCanvas.scroll). Ölçek hesaba
  // katılır, yoksa yakınlaştırılmışken kaydırma miktarı tutmaz. Ekran merkezini
  // (x, y) kadar öteleyip zoomXY ile yeniden kuruyoruz: böylece canvasOrigin/
  // canvasBounds defteri ve pişmiş doku, fareyle kaydırmadaki gibi tazeleniyor.
  def scroll(x: Double, y: Double): Unit =
    zoomXY(stage.scale.x, -stage.scale.y, merkezDünyaX + x, merkezDünyaY + y)

  // Tuvali döndürür (masaüstü tCanvas.viewRotate). Masaüstündeki gibi BİRİKİMLİ:
  // her çağrı mevcut dönüşe eklenir (tuvaliDöndür(30) iki kez => 60 derece),
  // mutlak bir açı ataması değil. PIXI dönüşü saat yönünde, dünya ekseni ise
  // y'de ters; kullanıcı açısı saat yönünün TERSİ olsun diye işareti
  // çeviriyoruz -- kaplumbağanın sağ/sol anlayışıyla tutarlı.
  def viewRotate(açı: Double): Unit = {
    stage.rotation -= Utils.deg2radians(açı)
    resetBake()
    render()
  }

  // Eksen ve ızgara KENDİ katmanlarında: masaüstünde de tuvalin süsü onlar,
  // kullanıcının çizimi değil. Kaplumbağayla çizilseydi (eski showAxes böyleydi)
  // "gizle" diye bir şey olamazdı ve `sil()` onları da silerdi.
  private var axesLayer: PIXI.Graphics = _
  private var gridLayer: PIXI.Graphics = _

  // Ekranı kaplayacak kadar geniş bir alan: kaydırma/yakınlaştırma sonrası da
  // dolu görünsün diye görünür tuvalin üç katını çiziyoruz.
  private def süsAlanı: (Double, Double) = (canvasWidth * 1.5, canvasHeight * 1.5)

  private def süsKatmanı(mevcut: PIXI.Graphics)(çiz: PIXI.Graphics => Unit): PIXI.Graphics = {
    val g = if (mevcut == null) new PIXI.Graphics() else { mevcut.clear(); mevcut }
    çiz(g)
    g.name = BakePolicy.decorLayerName
    // Sahneye takılı DEĞİLSE tak. Yalnız "yeni mi?" diye bakmak yetmiyor:
    // resimleriSil() süs katmanını da sahneden çıkarıyor, alan ise duruyor;
    // sonraki eksenleriGöster() sahnede olmayan Graphics'e çizip görünmez
    // kalırdı. parent'e bakınca ikinci çağrı onu geri takıyor.
    if (g.parent == null) stage.addChildAt(g, 0) // en altta
    g.visible = true
    noteMutation(g)
    render()
    g
  }

  def showAxes(): Unit = {
    val (w, h) = süsAlanı
    axesLayer = süsKatmanı(axesLayer) { g =>
      g.lineStyle(1, 0x555555, 1)
      g.moveTo(-w, 0); g.lineTo(w, 0)   // x ekseni
      g.moveTo(0, -h); g.lineTo(0, h)   // y ekseni
      // 50 birimde bir çentik (masaüstündeki gibi)
      var x = -w
      while (x <= w) { if (x != 0) { g.moveTo(x, -3); g.lineTo(x, 3) }; x += 50 }
      var y = -h
      while (y <= h) { if (y != 0) { g.moveTo(-3, y); g.lineTo(3, y) }; y += 50 }
    }
  }

  def hideAxes(): Unit = if (axesLayer != null) { axesLayer.visible = false; noteMutation(axesLayer); render() }

  def showGrid(): Unit = {
    val (w, h) = süsAlanı
    gridLayer = süsKatmanı(gridLayer) { g =>
      g.lineStyle(1, 0xcccccc, 1)
      var x = -w
      while (x <= w) { g.moveTo(x, -h); g.lineTo(x, h); x += 50 }
      var y = -h
      while (y <= h) { g.moveTo(-w, y); g.lineTo(w, y); y += 50 }
    }
  }

  def hideGrid(): Unit = if (gridLayer != null) { gridLayer.visible = false; noteMutation(gridLayer); render() }

  // Şu anda ekran merkezinde duran dünya noktası -- pan/zoom'u o noktadan yapmak
  // için. (screen = pos + scale*world; zoomXY scale.set(xf, -yf) yaptığından
  // scale.x = xf, scale.y = -yf. yaklaşXY eşit olmayan ölçek kurabildiği için y'de
  // x-ölçeğini DEĞİL, y-ölçeğini (-scale.y) kullanmak gerekir.)
  private def merkezDünyaX = (screenWidth / 2 - stage.position.x) / stage.scale.x
  private def merkezDünyaY = (stage.position.y - screenHeight / 2) / -stage.scale.y

  val pressedKeys = new collection.mutable.HashSet[Int]

  def initEvents(): Unit = {
    def keyDown(e: KeyboardEvent): Unit = {
      pressedKeys.add(e.keyCode)
    }
    def keyUp(e: KeyboardEvent): Unit = {
      pressedKeys.remove(e.keyCode)
    }
    // Editör çerçevesindeki "Ev" düğmesi buradan çağırır (iframe aynı-köken).
    // js.Dynamic.global'a değil, somut `window` nesnesine atıyoruz: katı-kipte
    // çıplak atama (global.foo=) "kocoResetView is not defined" atardı.
    window.asInstanceOf[scala.scalajs.js.Dynamic]
      .updateDynamic("kocoResetView")((() => resetView()): scala.scalajs.js.Function0[Unit])

    def mouseWheel(e: WheelEvent): Unit = {
      if (zoomEnabled) {
        // Ölçeği biriken bir sayaçtan değil, GERÇEK stage.scale'den türetiriz;
        // yoksa yaklaşXY (koddan zoomXY) sonrası ilk tekerlek tıkında zıplardı.
        // Her iki ekseni AYNI çarpanla ölçekleriz ki eşit olmayan en-boy oranı
        // (yaklaşXY) korunsun. Ekran merkezindeki dünya noktasını koruyarak
        // yakınlaşırız (yoksa zoomXY dünya-(0,0)'ı merkeze koyup pan'ı iptal ederdi).
        val f = if (e.deltaY > 0) 0.9 else 1.1
        zoomXY(stage.scale.x * f, -stage.scale.y * f, merkezDünyaX, merkezDünyaY)
      }
    }
    window.addEventListener("keydown", keyDown(_), false)
    window.addEventListener("keyup", keyUp(_), false)
    window.addEventListener("wheel", mouseWheel(_), false)

    // Tuvali fareyle tutup çekerek kaydırma (pan).
    // Yalnız BOŞ tuvale (hitArea üzerinden hedef = stage) basınca başlar; bir
    // resme basınca (o resim etkileşimliyse hedef odur) pan başlamaz -- böylece
    // stopPropagation'a bağlı kalmadan onMouseClick/onMouseMove-only resimlerde
    // de pan tetiklenmez.
    stage.hitArea = new Rectangle(-1e6, -1e6, 2e6, 2e6)
    var panning = false
    var panStartGX = 0.0
    var panStartGY = 0.0
    var panStartPX = 0.0
    var panStartPY = 0.0
    def panDown(e: InteractionEvent): Unit = {
      if (zoomEnabled && (e.target.asInstanceOf[AnyRef] eq stage)) {
        panning = true
        panStartGX = e.data.global.x
        panStartGY = e.data.global.y
        panStartPX = stage.position.x
        panStartPY = stage.position.y
      }
    }
    def panMove(e: InteractionEvent): Unit = {
      if (panning) {
        stage.position.set(
          panStartPX + (e.data.global.x - panStartGX),
          panStartPY + (e.data.global.y - panStartGY)
        )
        render()
      }
    }
    def panUp(e: InteractionEvent): Unit = {
      if (panning) {
        panning = false
        // Kaydırma bitti: canvasOrigin/canvasBounds'u güncelle ve pişmiş dokuyu
        // geçersiz kıl. zoomXY mevcut ölçek + ekran-merkezi dünya noktasıyla aynı
        // stage.position'ı yeniden kurar (görsel sıçrama yok) ama bu defter
        // tutmayı ve resetBake'i yapar -- yoksa pan'la açılan bölgeye çizilen
        // durağan resimler pişirilince kaybolur, canvasBounds eskir.
        // Mevcut ölçeği (eşit olmayan olabilir) aynen koru: xf=scale.x, yf=-scale.y.
        zoomXY(stage.scale.x, -stage.scale.y, merkezDünyaX, merkezDünyaY)
      }
    }
    stage.on("pointerdown", panDown(_))
    stage.on("pointermove", panMove(_))
    stage.on("pointerup", panUp(_))
    stage.on("pointerupoutside", panUp(_))
  }

  def isKeyPressed(keyCode: Int) = pressedKeys.contains(keyCode)
  def stagePosition = stage.position
  def positionOnStage(data: InteractionData) = data.getLocalPosition(stage)
  def isAMouseButtonPressed = interaction.mouse.buttons > 0
  def mouseMoveOnlyWhenInside(on: Boolean): Unit = {
    interaction.moveWhenInside = on
  }
  def mouseXY = interaction.mouse.getLocalPosition(stage)

  def onKeyPress(fn: Int => Unit): Unit = {
    girdiİşleyicisiKaydedildi() // artık kuyruk boşalsa da betik bitmiş sayılmaz
    def keyDown(e: KeyboardEvent): Unit = {
      fn(e.keyCode)
    }
    window.addEventListener("keydown", keyDown(_), false)
  }
  def onKeyRelease(fn: Int => Unit): Unit = {
    girdiİşleyicisiKaydedildi()
    def keyUp(e: KeyboardEvent): Unit = {
      fn(e.keyCode)
    }
    window.addEventListener("keyup", keyUp(_), false)
  }
}
