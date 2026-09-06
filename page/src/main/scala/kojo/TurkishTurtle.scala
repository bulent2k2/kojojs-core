package kojo

import com.vividsolutions.jts.geom.Geometry
import org.scalajs.dom.document

// Turkish (Koco) turtle wrapper for KojoJS.
//
// ÖNEMLİ: englishTurtle'ın türü `TurtleAPI` ve Builtins buraya `turtle`
// (GlobalTurtleForPicture) geçiyor, `turtle0` DEĞİL. Sebep: TurtlePicture.apply
// resim çizerken turtle.globalTurtle'ı resmin kendi kaplumbağasıyla takas
// ediyor. turtle0'a doğrudan bağlansaydık `Picture { yinele(4){ileri(50);sağ()} }`
// kareyi tuvale çizip BOŞ bir resim döndürürdü.
// (SwedishTurtle hâlâ turtle0'a bağlı ve bu hatadan muzdarip.)
//
// Names follow the desktop Koco layer (bulent2k2/kojo,
// src/main/scala/net/kogics/kojo/lite/i18n/trInit.scala) so that scripts read the
// same in both. Commands the browser runtime does not implement yet are left
// commented out, each with the reason -- same convention as SwedishTurtle.

class TurkishTurtle(val englishTurtle: TurtleAPI, builtins: syntax.Builtins)(implicit kojoWorld: KojoWorld)
    extends kojo.tr.SayıYöntemleri
    with kojo.tr.MatematikYöntemleri
    with kojo.tr.BelkiYöntemleri
    with kojo.tr.BölümselİşlevYöntemleri
    with kojo.tr.YazıYöntemleri
    with kojo.tr.HarfYöntemleri
    with kojo.tr.NoktaYöntemleri
    with kojo.tr.Yöney2BYöntemleri
    with kojo.tr.AralıkYöntemleri
    with kojo.tr.KümeYöntemleri
    with kojo.tr.DiziYöntemleri
    with kojo.tr.EşlemYöntemleri
    with kojo.tr.DizinYöntemleri
    with kojo.tr.YöneyYöntemleri
    with kojo.tr.KökTürYöntemleri
    with kojo.tr.DizimYöntemleri
    with kojo.tr.MiskinDizinYöntemleri
    with kojo.tr.KuyrukYöntemleri
    with kojo.tr.RenkYöntemleri
    with kojo.tr.KlavyeYöntemleri
    with kojo.tr.ResimYöntemleri
    with kojo.tr.KumandaYöntemleri
    with kojo.tr.GelecekYöntemleri
    with kojo.tr.DizikYöntemleri
    with kojo.tr.SesYöntemleri
    with kojo.tr.GörünüşYöntemleri {
  import kojo.doodle.Color
  import kojo.tr._

  // ResimYöntemleri'nin ihtiyaç duyduğu builtins erişimi
  protected def kb: syntax.Builtins = builtins
  protected implicit def kd: KojoWorld = kojoWorld

  // Tür takma adları kojo.tr trait'lerinde; Renk de RenkYöntemleri'nde.
  type Hız = Speed.Speed

  // ---- görünürlük ----
  def sil(): Birim = englishTurtle.clear()
  // cleari: tuvali temizler VE kaplumbağayı gizler (clear + invisible).
  def temizle(): Birim = englishTurtle.cleari()
  def silVeSakla(): Birim = englishTurtle.cleari() // sözlük adı (temizle ile aynı)
  def göster(): Birim = görünür()
  def gizle(): Birim = görünmez()
  def görünür(): Birim = englishTurtle.visible()
  def görünmez(): Birim = englishTurtle.invisible()

  // ---- hareket ----
  def ileri(adım: Kesir): Birim = englishTurtle.forward(adım)
  def ileri(): Birim = englishTurtle.forward(25)
  def geri(adım: Kesir): Birim = englishTurtle.back(adım)
  def geri(): Birim = englishTurtle.back(25)
  def sağ(açı: Kesir, yarıçap: Kesir): Birim = englishTurtle.right(açı, yarıçap)
  def sağ(açı: Kesir): Birim = englishTurtle.right(açı)
  def sağ(): Birim = englishTurtle.right(90)
  def sol(açı: Kesir, yarıçap: Kesir): Birim = englishTurtle.left(açı, yarıçap)
  def sol(açı: Kesir): Birim = englishTurtle.left(açı)
  def sol(): Birim = englishTurtle.left(90)
  def dön(açı: Kesir, yarıçap: Kesir): Birim = englishTurtle.turn(açı, yarıçap)
  def dön(açı: Kesir): Birim = englishTurtle.turn(açı)
  // desktop calls this jumpTo; KojoJS spells it setPosition
  def atla(x: Kesir, y: Kesir): Birim = englishTurtle.setPosition(x, y)
  def ilerle(x: Kesir, y: Kesir): Birim = englishTurtle.moveTo(x, y)
  def noktayaGit(x: Kesir, y: Kesir): Birim = englishTurtle.lineTo(x, y)
  def git(x: Kesir, y: Kesir): Birim = englishTurtle.moveTo(x, y) // goTo/moveTo
  // Masaüstünde hop kalem durumunu bozduğu için saveStyle/restoreStyle ile
  // sarılıyor. KojoJS'te Hop -> realForward(n, hop=true) ve penIsUp'a HİÇ
  // dokunmuyor, yani sarmalama gereksiz -- üstelik zararlı: restoreStyle
  // fillColor'ı geri koyarken (başlangıçta null) realSetFillColor beginFill
  // çağırıyor ve PIXI Graphics kalıcı olarak doldurma kipine geçiyor.
  def zıpla(n: Kesir): Birim = englishTurtle.hop(n)
  def zıpla(): Birim = zıpla(25)
  def ev(): Birim = englishTurtle.home()
  def konumuKur(x: Kesir, y: Kesir): Birim = englishTurtle.setPosition(x, y)

  // ---- yön ----
  def açıyaDön(açı: Kesir): Birim = englishTurtle.setHeading(açı)
  def noktayaDön(x: Kesir, y: Kesir): Birim = englishTurtle.towards(x, y)
  def doğu(): Birim = englishTurtle.setHeading(0)
  def batı(): Birim = englishTurtle.setHeading(180)
  def kuzey(): Birim = englishTurtle.setHeading(90)
  def güney(): Birim = englishTurtle.setHeading(-90)
  /**
   * Kaplumbağanın o andaki konumu ve yönü -- GERİ ÇAĞIRMAYLA.
   *
   * Neden düz bir `konum` değeri yok: KojoJS'te her kaplumbağa komutu bir
   * kuyruğa giriyor, çağrıldığı anda çalışmıyor. `ileri(100)` yazdığında
   * kaplumbağa daha kıpırdamamıştır. Anlık bir okuma bu yüzden kuyruktaki
   * komutlardan ÖNCEKİ değeri verirdi -- sessizce yanlış cevap.
   *
   * `konumuOku` bunun yerine okumayı KUYRUĞA sokuyor: işlevin, kendisinden
   * önce yazdığın bütün komutlar bittikten sonra çalışıyor.
   *
   * {{{
   * yinele(4) { ileri(100); sağ() }
   * konumuOku { n => yaz(s"kare bitti, buradayım: ${n.x}, ${n.y}") }
   * }}}
   *
   * DİKKAT: işlevin İÇİNDE verdiğin kaplumbağa komutları kuyruğun SONUNA
   * eklenir, okumanın yapıldığı yere değil. Yani `konumuOku`dan sonra
   * yazdığın komutlar, işlevin içindekilerden önce çalışır.
   */
  def konumuOku(işlev: Nokta => Birim): Birim =
    kuyruktanOku(t => işlev(Nokta(t.position.x, t.position.y)))

  /**
   * Kaplumbağa bu resme değiyor mu? -- `konumuOku` gibi geri çağrımalı.
   *
   * Kaplumbağayı NOKTA sayıyor: gövdesinin resmi değil, burnunun bulunduğu
   * konumun resmin içinde olup olmadığına bakıyor. Öngörülebilir olsun diye
   * böyle; kaplumbağa simgesinin köşesi resme değdiğinde "değdi" demiyor.
   *
   * {{{
   * val duvar = Resim.kare(80).konumlu(100, 0)
   * çiz(duvar)
   * canlandır {
   *   ileri(2)
   *   dokunuyorMu(duvar) { değdi => if (değdi) sağ(90) }
   * }
   * }}}
   *
   * İKİ RESİM arasındaki çarpışma için buna gerek yok: `resim.çarpışıyorMu(öbürü)`
   * doğrudan, geri çağrısız çalışıyor. Oyun yazarken genelde istediğin odur --
   * `dokunuyorMu` kaplumbağayla çizim yaparken işe yarıyor.
   */
  def dokunuyorMu(resim: Resim)(işlev: İkil => Birim): Birim =
    kuyruktanOku { t =>
      val çizgi = resim.picGeom
      if (çizgi == null)
        throw new ÇalışmaSırasıKuralDışı(
          "dokunuyorMu: resim henüz çizilmemiş. Önce çiz(resim) demelisin -- " +
            "çizilmemiş bir resmin sınırları hesaplanmamış oluyor."
        )
      val nokta = kojo.Utils.Gf.createPoint(kojo.Utils.newCoordinate(t.position.x, t.position.y))
      işlev(çizgi.intersects(nokta) || alanıİçeriyorMu(çizgi, nokta))
    }

  /**
   * Resmin İÇİ noktayı kapsıyor mu?
   *
   * Gerekli, çünkü `picGeom` bir LineString -- yani yalnızca DIŞ ÇİZGİ.
   * Karenin tam ortasındaki bir nokta o çizgiye değmiyor, dolayısıyla düz
   * `intersects` "hayır" diyor. (İki resim çarpıştığında dış çizgileri
   * kesiştiği için `çarpışıyorMu` bu sorunu yaşamıyor.)
   *
   * Çözüm: dış çizginin köşelerinden bir çokgen kurup `contains` sormak.
   * Çokgen kurulamıyorsa (açık bir çizgi, çok az nokta) `false` -- öyle bir
   * şeklin zaten içi yok.
   */
  private def alanıİçeriyorMu(çizgi: Geometry, nokta: Geometry): İkil = {
    import scala.scalajs.js.JSConverters._
    val k = çizgi.getCoordinates()
    if (k.length < 3) return false
    val ilk = k(0)
    val son = k(k.length - 1)
    // createLinearRing kapalı halka istiyor; kapalı değilse ilk noktayı ekle.
    val halka =
      if (ilk.x == son.x && ilk.y == son.y) k.toSeq
      else k.toSeq :+ ilk
    if (halka.length < 4) return false
    try kojo.Utils.Gf.createPolygon(halka.toJSArray).contains(nokta)
    catch { case _: Throwable => false }
  }

  /**
   * `konumuOku` gibi, ama yönü verir -- derece cinsinden, 0 ile 360 arasında.
   *
   * KojoJS'in ham `heading`'i dönüşleri biriktiriyor: dört kez sağa dönen bir
   * kaplumbağa için -270 diyor. Aynı yön, ama çocuğa 90 demek gerekiyor.
   */
  def yönüOku(işlev: Kesir => Birim): Birim =
    kuyruktanOku { t =>
      val ham = t.heading % 360
      işlev(if (ham < 0) ham + 360 else ham)
    }

  /**
   * Etkin kaplumbağa. `englishTurtle` bir GlobalTurtleForPicture; hangi
   * kaplumbağaya bağlı olduğu `Resim { ... }` bloğu içinde mi dışında mı
   * olduğumuza göre değişiyor (TurtlePicture takas ediyor), bu yüzden her
   * çağrıda yeniden soruyoruz.
   */
  private def etkinKaplumbağa: Turtle = englishTurtle match {
    case g: GlobalTurtleForPicture => g.globalTurtle
    case t: Turtle                 => t
    case başka =>
      throw new ÇalışmaSırasıKuralDışı(s"konum okunamıyor: beklenmeyen kaplumbağa türü ${başka.getClass.getName}")
  }

  private def kuyruktanOku(işlev: Turtle => Birim): Birim = {
    val t = etkinKaplumbağa
    t.sync(() => işlev(t))
  }

  // ---- kalem ----
  def kalemiİndir(): Birim = englishTurtle.penDown()
  def kalemiKaldır(): Birim = englishTurtle.penUp()
  def kalemRenginiKur(renk: Renk): Birim = englishTurtle.setPenColor(renk)
  def boyamaRenginiKur(renk: Renk): Birim = englishTurtle.setFillColor(renk)
  def kalemKalınlığınıKur(n: Kesir): Birim = englishTurtle.setPenThickness(n)
  // def kalemİnikMi: İkil = englishTurtle.style.down  // KojoJS'te `style` yok

  // ---- biçim ve konum belleği ----
  def biçimleriBelleğeYaz(): Birim = englishTurtle.saveStyle()
  def biçimleriGeriYükle(): Birim = englishTurtle.restoreStyle()
  def konumVeYönüBelleğeYaz(): Birim = englishTurtle.savePosHe()
  def konumVeYönüGeriYükle(): Birim = englishTurtle.restorePosHe()

  // ---- yazı ----
  def yazı(t: Yazı): Birim = englishTurtle.write(t)
  def tuvaleYaz(t: Yazı): Birim = yazı(t)
  def yazıBoyunuKur(boy: Sayı): Birim = englishTurtle.setPenFontSize(boy)

  // ---- şekiller ----
  def yay(yarıçap: Kesir, açı: Kesir): Birim = englishTurtle.arc(yarıçap, açı)
  def daire(yarıçap: Kesir = 25): Birim = englishTurtle.circle(yarıçap)
  def üçgen(en: Kesir = 25): Birim = yinele(3) { ileri(en); sağ(120) }
  def kare(en: Kesir = 25): Birim = yinele(4) { ileri(en); sağ(90) }
  // Devre 6: nokta artık gerçek (Turtle.realDot, dolu daire).
  def nokta(çap: Sayı): Birim = englishTurtle.dot(çap)
  def nokta(): Birim = englishTurtle.dot(25)
  // ışınlar KojoJS'te henüz UYGULANMADI (beamsOn/Off = {}); dosyanın geleneği
  // gereği sessizce çalışmış gibi görünmesinler:
  // def ışınlarıAç() = englishTurtle.beamsOn()
  // def ışınlarıKapat() = englishTurtle.beamsOff()
  // (çıktıyıSil: masaüstü çıktı paneline özgü; Devre 1 yer tutucuları arasında)

  // ---- hız ----
  def hızıKur(hız: Hız): Birim = englishTurtle.setSpeed(hız)
  def canlandırmaHızınıKur(n: Uzun): Birim = englishTurtle.setAnimationDelay(n)
  lazy val yavaş = Speed.slow
  lazy val orta = Speed.medium
  lazy val hızlı = Speed.fast
  lazy val çokHızlı = Speed.superFast

  // Giysi (costume): kaplumbağa KURULURKEN verilebiliyor -- yeniKaplumbağa(x, y, giysi).
  // Sonradan değiştiren komutlar hâlâ yok: giysiKur, giysileriKur, birsonrakiGiysi,
  // giysiyiBüyült (Turtle'ın simgesi tek seferde yükleniyor).

  // ---- döngüler ----
  def yinele(n: Sayı)(diziKomut: => Birim): Birim =
    RepeatCommands.repeat(n) { diziKomut }

  def yineleDizinli(n: Sayı)(diziKomut: Sayı => Birim): Birim =
    RepeatCommands.repeati(n) { i => diziKomut(i) }

  def yineleDoğruysa(koşul: => İkil)(diziKomut: => Birim): Birim =
    RepeatCommands.repeatWhile(koşul) { diziKomut }

  def yineleOlanaKadar(koşul: => İkil)(diziKomut: => Birim): Birim =
    RepeatCommands.repeatUntil(koşul) { diziKomut }

  def yineleİçin[T](dizi: Iterable[T])(diziKomut: T => Birim): Birim =
    RepeatCommands.repeatFor(dizi) { diziKomut }

  def yineleKere[T](dizi: Iterable[T])(diziKomut: T => Birim): Birim =
    yineleİçin(dizi)(diziKomut)

  def yineleİlktenSona(ilki: Sayı, sonu: Sayı)(diziKomut: Sayı => Birim): Birim =
    RepeatCommands.repeatFor(ilki to sonu) { diziKomut }

  // ---- renkler: kojo.tr.RenkYöntemleri ----

  def artalanıKur(renk: Renk): Birim = builtins.setBackground(renk)
  def artalanıKurDik(r1: Renk, r2: Renk): Birim = builtins.setBackgroundV(r1, r2)
  def artalanıKurYatay(r1: Renk, r2: Renk): Birim = builtins.setBackgroundH(r1, r2)
  def rastgeleRenk: Renk = builtins.randomColor
  def rastgeleŞeffafRenk: Renk = builtins.randomTransparentColor

  // ---- giriş / çıkış ----
  def satıroku(istem: Yazı = ""): Yazı = builtins.readln(istem)
  def sayıOku(istem: Yazı = ""): Sayı = builtins.readInt(istem)
  def belirt(koşul: İkil, mesaj: Yazı = ""): Birim = if (!koşul) throw new ÇalışmaSırasıKuralDışı(s"belirt başarısız: $mesaj") // assert
  def kesirOku(istem: Yazı = ""): Kesir = builtins.readDouble(istem)

  /**
   * `Predef.println`/`print` DEĞİL: Scala.js'te onlar `console.log`'a gidiyor,
   * sayfadaki çıktı paneline değil -- `satıryaz("merhaba")` yazan kullanıcı
   * ekranda hiçbir şey görmüyordu (2026-09-03'te canlıda bulundu).
   *
   * Panele yazan `fiddle.Fiddle` kojojs-core/page'de duruyor, bu repoda YOK;
   * o yüzden onun kullandığı DOM sözleşmesine (id="output") doğrudan yazıyoruz
   * -- `KojoWorldImpl`in fiddle-container/canvas-holder'a doğrudan bağlanması
   * gibi. Panel bulunamazsa (tarayıcı dışı koşum, testler) konsola düşüyoruz.
   */
  def satıryaz(): Birim = satıryaz("")
  def satıryaz(veri: Any): Birim = paneleYaz(veri, satırSonu = true)
  def yaz(veri: Any): Birim = paneleYaz(veri, satırSonu = false)

  /**
   * Açık (henüz satır sonu verilmemiş) çıktı satırı. `yaz` buna ekliyor,
   * `satıryaz` ekleyip KAPATIYOR -- uçbirimdeki print/println gibi.
   */
  private var açıkSatır: org.scalajs.dom.Element = null

  private def paneleYaz(veri: Any, satırSonu: İkil): Birim = {
    val panel = document.getElementById("output")
    val metin = String.valueOf(veri)
    if (panel == null) {
      if (satırSonu) println(metin) else print(metin)
    }
    else {
      // Açık satır hâlâ panelin SON çocuğu mu? Değilse araya başkası yazmış
      // (fiddle.Fiddle.println kendi div'ini ekliyor, sil() paneli boşaltıyor)
      // ve eskisine eklemek metni yanlış yere koyardı.
      if (açıkSatır == null || !(panel.lastChild eq açıkSatır)) {
        açıkSatır = document.createElement("div")
        panel.appendChild(açıkSatır)
      }
      açıkSatır.appendChild(document.createTextNode(metin))
      if (satırSonu) {
        // Bomboş div'in yüksekliği sıfır: satıryaz() boş satır göstermiyordu.
        if (açıkSatır.textContent.isEmpty) açıkSatır.appendChild(document.createTextNode("\u00a0"))
        açıkSatır = null
      }
      panel.scrollTop = panel.scrollHeight - panel.clientHeight
    }
  }

  // ---- sayılar ----
  def rastgele(üstSınır: Sayı): Sayı = builtins.random(üstSınır)
  def rastgele(altSınır: Sayı, üstSınır: Sayı): Sayı = builtins.random(altSınır, üstSınır)
  def rastgeleKesir(üstSınır: Kesir): Kesir = builtins.randomDouble(üstSınır)
  def rastgeleKesir(altSınır: Kesir, üstSınır: Kesir): Kesir = builtins.randomDouble(altSınır, üstSınır)
  def rastgeleSayı: Sayı = builtins.randomInt
  def rastgeleUzun: Uzun = builtins.randomLong
  def rastgeleSeçim: İkil = builtins.randomBoolean
  def rastgeleDoğalKesir: Kesir = builtins.randomNormalDouble

  // yuvarla: kojo.tr.MatematikYöntemleri

  def bekle(saniye: Kesir): Birim = englishTurtle.pause(saniye)

  // ---- oyun / etkileşim ----
  def tuşBasılıMı(tuşKodu: Sayı): İkil = builtins.isKeyPressed(tuşKodu)
  def canlandır(işlev: => Birim): Birim = builtins.animate(işlev)
  def canlandırmayıDurdur(): Birim = builtins.stopAnimation()
  def tuvalSınırları: Dikdörtgen = builtins.canvasBounds
  def tuvaliEtkinleştir(): Birim = builtins.activateCanvas()
  def yakınlaştırmayıKapat(): Birim = builtins.disablePanAndZoom()
  def kareSüresi: Kesir = builtins.frameDeltaTime

  // ---- sekme (oyun fiziği) ----
  /**
   * Sahne kenarları `çizSahne(...)` çağrılana kadar null.
   *
   * Bu olmadan `sahnedenSek` `collidesWith(null)` ile TypeError atıyor;
   * `canlandır` döngüsü ilk karede sessizce ölüyor ve resim hiç kıpırdamıyor --
   * ekranda hata yok, hiçbir ipucu yok. `artalanıKur` yetmiyor: o yalnızca
   * tuvalin rengini değiştiriyor, kenarları kurmuyor.
   */
  private def sahneKurulduMu(nereden: Yazı): Birim =
    if (builtins.stageTop == null)
      throw new ÇalışmaSırasıKuralDışı(
        s"$nereden kullanabilmek için önce sahneyi çizmelisin: çizSahne(siyah). " +
          "artalanıKur yalnızca rengi değiştirir, sahne kenarlarını kurmaz."
      )

  def sahnedenSek(resim: Resim, hız: Yöney2B): Yöney2B = {
    sahneKurulduMu("sahnedenSek'i")
    builtins.bouncePicOffStage(resim, hız)
  }
  def resimdenSek(resim: Resim, hız: Yöney2B, engel: Resim): Yöney2B =
    builtins.bouncePicOffPic(resim, hız, engel)
  def sahneKenarı: Resim = { sahneKurulduMu("sahneKenarı'nı"); builtins.stageBorder }
  def sahneÜstü: Resim = { sahneKurulduMu("sahneÜstü'nü"); builtins.stageTop }
  def sahneAltı: Resim = { sahneKurulduMu("sahneAltı'nı"); builtins.stageBot }
  def sahneSolu: Resim = { sahneKurulduMu("sahneSolu'nu"); builtins.stageLeft }
  def sahneSağı: Resim = { sahneKurulduMu("sahneSağı'nı"); builtins.stageRight }

  // ---- masaüstü Koco takma adları ve yer tutucular (Devre 1) ----
  // Masaüstü betiklerinin (ornekler/masaustu) OLDUĞU GİBİ derlenmesi için
  // trInit.scala'daki adlar. Her satırın sağında ikojo'daki asıl karşılık;
  // davranışı farklı olanlar yorumda söyleniyor. Yeni bir ad eklerken önce
  // araclar/ucurum.py çıktısındaki sıklığa bak.

  // tuval
  def durdur(): Birim = canlandırmayıDurdur() // stopAnimation
  def tuşaBasılıMı(tuş: Sayı): İkil = tuşBasılıMı(tuş)
  def tuşaBasınca(iş: Sayı => Birim): Birim = builtins.onKeyPress(iş)
  def tuşuBırakınca(iş: Sayı => Birim): Birim = builtins.onKeyRelease(iş)
  def yaklaş(oran: Kesir): Birim = builtins.zoom(oran)
  def yaklaş(oran: Kesir, xMerkez: Kesir, yMerkez: Kesir): Birim = builtins.zoom(oran, xMerkez, yMerkez)
  def eksenleriGöster(): Birim = builtins.showAxes()
  def gridiGöster(): Birim = builtins.showGrid() // ikojo'da henüz çizmiyor (boş)
  def ızgarayıGöster(): Birim = gridiGöster()
  def tümEkranTuval(): Birim = builtins.toggleFullScreenCanvas()
  def başlangıçNoktasıAltSolKöşeOlsun(): Birim = builtins.originBottomLeft()
  def ikiÇizimArasıSüre: Kesir = kareSüresi // frameDeltaTime
  def fareKonumu: Nokta = kojoWorld.mouseXY
  def silipSakla(): Birim = silVeSakla()
  def çizVeSakla(resimler: Resim*): Birim = builtins.drawAndHide(resimler: _*)
  // masaüstünde Future döndürür; burada Birim (tarayıcıda iptal edilebilir sayaç yok)
  def yineleSayaçla(miliSaniye: Uzun)(işlev: => Birim): Birim = builtins.timer(miliSaniye)(işlev)

  /** Masaüstü `tuvalAlanı`: tuval sınırlarına kısa erişim (`dez ta = tuvalAlanı`). */
  object tuvalAlanı {
    def ta: Dikdörtgen = tuvalSınırları
    def eni: Kesir = en
    def boyu: Kesir = boy
    def en: Kesir = ta.width
    def boy: Kesir = ta.height
    def x: Kesir = ta.x
    def y: Kesir = ta.y
    def X: Kesir = ta.x + ta.width
    def Y: Kesir = ta.y + ta.height
  }
  def yatayMerkezKonumu(uzunluk: Kesir): Kesir = tuvalAlanı.x + (tuvalAlanı.en - uzunluk) / 2
  def dikeyMerkezKonumu(uzunluk: Kesir): Kesir = tuvalAlanı.y + (tuvalAlanı.boy - uzunluk) / 2

  // canlandırma: her karede önceki resmi silip yenisini çizer (animateWithRedraw)
  def canlandırYenidenÇizerek[Evre](ilkEvre: Evre, sonrakiEvre: Evre => Evre, işlev: Evre => Resim): Birim = {
    var önceki: Resim = null
    builtins.animateWithState(ilkEvre) { evre =>
      if (önceki != null) önceki.erase()
      önceki = işlev(evre)
      önceki.draw()
      sonrakiEvre(evre)
    }
  }

  // kaplumbağalar
  type Kaplumbağa = TurkishTurtle
  def kaplumbağa: Kaplumbağa = this
  def yeniKaplumbağa(x: Kesir, y: Kesir): Kaplumbağa = new TurkishTurtle(new Turtle(x, y)(kojoWorld), builtins)(kojoWorld)
  /** Giysili kaplumbağa: `yeniKaplumbağa(0, 0, Görünüş.araba)` (masaüstü trInit.scala:328). */
  def yeniKaplumbağa(x: Kesir, y: Kesir, giysiDosyası: Yazı): Kaplumbağa =
    new TurkishTurtle(new Turtle(x, y, false, giysiDosyası)(kojoWorld), builtins)(kojoWorld)

  // sayılar / yardımcılar
  def rastgeleNormalKesir: Kesir = rastgeleDoğalKesir
  def rastgeleİkil: İkil = rastgeleSeçim
  // masaüstü gibi aynı koleksiyon türünü döndürür (Yöney -> Yöney, Dizin -> Dizin)
  def rastgeleKarıştır[T, C](xLer: IterableOnce[T])(implicit bf: scala.collection.BuildFrom[xLer.type, T, C]): C =
    new scala.util.Random(builtins.Random).shuffle(xLer)
  def gerekli(gerekçe: İkil, mesaj: => Any = ""): Birim = require(gerekçe, mesaj)
  def zamanTut[T](başlık: Yazı = "Zaman ölçümü:")(işlev: => T)(bitiş: Yazı = "sürdü."): T = {
    val t0 = buSaniye
    val çıktı = işlev
    val delta = buSaniye - t0
    val sözcükler = List(başlık, f"$delta%.3f saniye", bitiş).filter(_.nonEmpty)
    satıryaz(sözcükler.mkString(" "))
    çıktı
  }
  object Matematik extends kojo.tr.MatematikYöntemleri

  // mp3 (howler üzerinden; masaüstüyle aynı adlar)
  class Mp3Çalar(p: Mp3Player) {
    def çalıyorMu: İkil = p.isMp3Playing
    def sesMp3üÇal(mp3dosyası: Yazı): Birim = p.playMp3Sound(mp3dosyası)
    def çal(mp3dosyası: Yazı): Birim = p.playMp3(mp3dosyası)
    def durdur(): Birim = p.stopMp3()
    def önyükle(mp3dosyası: Yazı): Birim = p.preloadMp3(mp3dosyası)
    def döngülüÇal(mp3dosyası: Yazı): Birim = p.playMp3Loop(mp3dosyası)
    def döngüyüDurdur(): Birim = p.stopMp3Loop()
  }
  def yeniMp3Çalar: Mp3Çalar = new Mp3Çalar(builtins.newMp3Player)
  def müzikİndir(httpAdresi: Yazı): Birim = builtins.preloadMp3(httpAdresi)
  def müzikMp3üÇal(mp3dosyası: Yazı): Birim = builtins.playMp3(mp3dosyası)
  def sesMp3üÇal(mp3dosyası: Yazı): Birim = builtins.playMp3Sound(mp3dosyası)
  def müzikMp3üÇalDöngülü(mp3dosyası: Yazı): Birim = builtins.playMp3Loop(mp3dosyası)
  def müzikMp3üÇalıyorMu: İkil = builtins.isMp3Playing
  def müzikMp3üKapat(): Birim = builtins.stopMp3()
  def müzikMp3DöngüsünüKapat(): Birim = builtins.stopMp3Loop()
  def Mp3ÇalıyorMu: İkil = müzikMp3üÇalıyorMu
  def Mp3üDurdur(): Birim = müzikMp3üKapat()
  def Mp3DöngüsünüDurdur(): Birim = müzikMp3DöngüsünüKapat()

  // yer tutucular: masaüstü arayüzüne (Swing pencereleri, çıktı paneli) özgü;
  // tarayıcıda karşılığı yok. Betik derlensin ve akış bozulmasın diye boş.
  def kojoVarsayılanİkinciBakışaçısınıKur(): Birim = builtins.switchToDefault2Perspective()
  def kojoÇalışmaSayfalıBakışaçısınıKur(): Birim = {}
  def tümEkranÇıktı(): Birim = {}
  def yazılımcıkDüzenleyicisiniEtkinleştir(): Birim = {}
  def çıktıArtalanınıKur(renk: Renk): Birim = {}
  def çıktıYazıRenginiKur(renk: Renk): Birim = {}
  def çıktıyıSil(): Birim = builtins.clearOutput() // clearOutput = {}; 6 betikte ilk hata buydu
  // masaüstünde canlandırma başlarken çağrılır; burada hemen çalışır
  def canlandırmaBaşlayınca(işlev: => Birim): Birim = işlev
  // masaüstünde ayrı iş parçacığı; tarayıcıda tek iş parçacığı var, hemen çalışır
  def artalandaOynat(kod: => Birim): Birim = kod

  // Kaplumbağanın konumunu/yönünü SENKRON okuyan `konum` ve `doğrultu`
  // (masaüstü trInit.scala) BİLEREK eklenmedi: ikojo'da kaplumbağa komutları
  // kuyruğa giriyor, o yüzden senkron bir okuma kuyruktaki komutlardan ÖNCEKİ
  // konumu verirdi -- sessizce yanlış çizim. Kuyruk sırasını koruyan geri
  // çağrımlı biçimleri kullanın: konumuOku(k => ...), yönüOku(y => ...).

  // ---- Devre 4: durakla ----
  // Masaüstünde iş parçacığını uyutur (Thread.sleep); tarayıcıda bloklamak yok.
  // Kaplumbağa komut kuyruğuna bir bekleme eklenir: sonraki KAPLUMBAĞA komutları
  // o kadar gecikir; resim/çıktı kodu hemen sürer (bekle ile aynı).
  def durakla(saniye: Kesir): Birim = englishTurtle.pause(saniye)
  def duraklaMiliSaniye(miliSaniye: Uzun): Birim = englishTurtle.pause(miliSaniye / 1000.0)

  // ---- Devre 2: küçük özellikler ----
  def buAn: Uzun = System.currentTimeMillis()
  def tuvalBoyutlarınıKur(en: Sayı, boy: Sayı): Birim = builtins.size(en, boy)
  def sahneKenarındanYansıtma(r: Resim, yöney: Yöney2B): Yöney2B = builtins.bouncePicVectorOffStage(r, yöney)
  def engeldenYansıtma(r: Resim, yöney: Yöney2B, engel: Resim): Yöney2B =
    builtins.bouncePicVectorOffPic(r, yöney, engel)
  // yazıyüzü (Yazıyüzü ailesi: kojo.tr.YazıyüzüYöntemleri)
  def yazıYüzünüKur(yy: Yazıyüzü): Birim = {
    englishTurtle.setPenFontFamily(yy.ad)
    englishTurtle.setPenFontSize(yy.boy)
  }
  // yazıyüzü adı PIXI ölçümünde yok sayılır; boy belirleyici
  def yazıÇerçevesi(yazı: Yazı, yazıBoyu: Sayı, yazıyüzüAdı: Yazı = null): Dikdörtgen =
    builtins.textExtent(yazı, yazıBoyu)
  // react: kaplumbağa her karede işlevi çalıştırır
  def davran(işlev: Kaplumbağa => Birim): Birim = builtins.animate(işlev(this))
  def tepkiVer(işlev: Kaplumbağa => Birim): Birim = davran(işlev)
  def canlan(işlev: Kaplumbağa => Birim): Birim = davran(işlev)
  /** Masaüstü showGameTimeCountdown: sol üstte geri sayan sayaç; sıfırda ileti + durdur. */
  def oyunSüresiniGeriyeSayarakGöster(
      süreSaniyeOlarak: Sayı,
      mesaj: Yazı,
      renk: Renk = Renkler.siyah,
      yazıBoyu: Sayı = 15,
      kx: Kesir = 10,
      ky: Kesir = 50
  ): Birim = {
    val ta = tuvalSınırları
    var kalan = süreSaniyeOlarak
    val etiket = builtins.Picture.textu(kalan, yazıBoyu, renk)(kojoWorld)
    etiket.draw()
    etiket.setPosition(ta.x + kx, ta.y + ky)
    builtins.timer(1000) {
      kalan -= 1
      etiket.update(kalan)
      if (kalan <= 0) {
        builtins.drawCenteredMessage(mesaj, renk, yazıBoyu * 2)
        builtins.stopAnimation()
      }
    }
  }
}
