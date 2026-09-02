package kojo

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
    with kojo.tr.ResimYöntemleri {
  import kojo.doodle.Color
  import kojo.tr._

  // ResimYöntemleri'nin ihtiyaç duyduğu builtins erişimi
  protected def kb: syntax.Builtins = builtins
  protected implicit def kd: KojoWorld = kojoWorld

  // Tür takma adları kojo.tr trait'lerinde; Renk de RenkYöntemleri'nde.
  type Hız = Speed.Speed

  // ---- görünürlük ----
  def sil(): Birim = englishTurtle.clear()
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
  // `doğrultu` ve `konum` yok: KojoJS'te her komut bir kuyruğa giriyor, hemen
  // çalışmıyor. Anlık bir okuma kuyruktaki komutlardan ÖNCEKİ değeri verirdi.
  // def doğrultu: Kesir = englishTurtle.heading
  // def konum: Nokta = englishTurtle.position

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
  // nokta/ışınlar/çıktıyıSil KojoJS'te henüz UYGULANMADI (gövdeleri boş).
  // Dosyanın geleneği gereği sessizce çalışmış gibi görünmesinler:
  // def nokta(çap: Sayı) = englishTurtle.dot(çap)      // TurtleAPI.dot gövdesi yorumda
  // def ışınlarıAç() = englishTurtle.beamsOn()          // beamsOn/Off = {}
  // def ışınlarıKapat() = englishTurtle.beamsOff()
  // def çıktıyıSil() = builtins.clearOutput()           // clearOutput = {}

  // ---- hız ----
  def hızıKur(hız: Hız): Birim = englishTurtle.setSpeed(hız)
  def canlandırmaHızınıKur(n: Uzun): Birim = englishTurtle.setAnimationDelay(n)
  lazy val yavaş = Speed.slow
  lazy val orta = Speed.medium
  lazy val hızlı = Speed.fast
  lazy val çokHızlı = Speed.superFast

  // giysi (costume) komutları KojoJS'te henüz yok:
  // giysiKur, giysileriKur, birsonrakiGiysi, giysiyiBüyült

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
  def satıryaz(): Birim = println()
  def satıryaz(veri: Any): Birim = println(veri)
  def yaz(veri: Any): Birim = print(veri)

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
  def tuvalSınırları = builtins.canvasBounds
  def tuvaliEtkinleştir(): Birim = builtins.activateCanvas()
  def yakınlaştırmayıKapat(): Birim = builtins.disablePanAndZoom()
  def kareSüresi: Kesir = builtins.frameDeltaTime
}
