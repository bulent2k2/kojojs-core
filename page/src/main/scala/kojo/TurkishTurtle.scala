package kojo

// Turkish (Koco) turtle wrapper for KojoJS.
//
// Names follow the desktop Koco layer (bulent2k2/kojo,
// src/main/scala/net/kogics/kojo/lite/i18n/trInit.scala) so that scripts read the
// same in both. Commands the browser runtime does not implement yet are left
// commented out, each with the reason -- same convention as SwedishTurtle.

class TurkishTurtle(val englishTurtle: Turtle, builtins: syntax.Builtins)
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
    with kojo.tr.KuyrukYöntemleri {
  import kojo.doodle.Color
  import kojo.tr._

  // Tür takma adları (Sayı, Kesir, Yazı, İkil, Birim ...) artık kojo.tr paket
  // nesnesinde; burada yalnızca KojoJS'e özgü olanlar kalıyor.
  type Renk = Color
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
  def zıpla(n: Kesir): Birim = {
    englishTurtle.saveStyle() // to preserve pen state
    englishTurtle.hop(n) // hop leaves the pen down afterwards
    englishTurtle.restoreStyle()
  }
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
  def nokta(çap: Sayı): Birim = englishTurtle.dot(çap)
  def nokta(): Birim = englishTurtle.dot(25)

  // ---- hız ----
  def hızıKur(hız: Hız): Birim = englishTurtle.setSpeed(hız)
  def canlandırmaHızınıKur(n: Uzun): Birim = englishTurtle.setAnimationDelay(n)
  lazy val yavaş = Speed.slow
  lazy val orta = Speed.medium
  lazy val hızlı = Speed.fast
  lazy val çokHızlı = Speed.superFast

  // ---- ışınlar ----
  def ışınlarıAç(): Birim = englishTurtle.beamsOn()
  def ışınlarıKapat(): Birim = englishTurtle.beamsOff()

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

  // ---- renkler ----
  lazy val kırmızı = builtins.Color.red
  lazy val mavi = builtins.Color.blue
  lazy val yeşil = builtins.Color.green
  lazy val sarı = builtins.Color.yellow
  lazy val mor = builtins.Color.purple
  lazy val morumsu = builtins.Color.magenta
  lazy val pembe = builtins.Color.pink
  lazy val kahverengi = builtins.Color.brown
  lazy val turuncu = builtins.Color.orange
  lazy val gri = builtins.Color.gray
  lazy val siyah = builtins.Color.black
  lazy val beyaz = builtins.Color.white
  lazy val saydam = builtins.noColor

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
  def çıktıyıSil(): Birim = builtins.clearOutput()

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
}
