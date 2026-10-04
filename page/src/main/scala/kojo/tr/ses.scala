package kojo.tr

/**
 * Masaüstü Koco'nun ses adları (kojo: lite/i18n/tr/ses.scala, tr/muzik.scala,
 * trInit.scala notaÇal). İki bölüm:
 *
 *  - `Ses` sabitleri: mp3 yolları. Masaüstüyle aynı `/media/...` yolları; dosyalar
 *    kojojs-dev/medya altında, koco-deploy nginx'i `/media/`yi oraya bağlar.
 *    Çalma `sesMp3üÇal` / `müzikMp3üÇalDöngülü` (TurkishTurtle, howler).
 *  - `notaÇal(nota, süreMs, ses)`: masaüstünde MIDI (RealtimeNotePlayer); burada
 *    Web Audio osilatörü (kojo.NotaÇalar; İngilizcesi playNote). `nota` MIDI
 *    perdesi (0-127; 69 = la 440 Hz), `ses` 0-127. Beklemez; arka arkaya
 *    çağrılar masaüstündeki gibi sıraya girip ezgi olur. `Çalgı` kodları MIDI
 *    program numaraları (İngilizcesi Instrument); burada dalga biçimine kabaca
 *    eşlenir (piyano ailesi üçgen, bas sinüs, gürültülü çalgılar kare/testere).
 */
trait SesYöntemleri extends TemelTürler {
  object Ses {
    val arabaHızlanıyor = "/media/car-ride/car-accel.mp3"
    val arabaFrenYapıyor = "/media/car-ride/car-brake.mp3"
    val arabaKazaYaptı = "/media/car-ride/car-crash.mp3"
    val arabaGidiyor = "/media/car-ride/car-move.mp3"
    val vuruş = "/media/collidium/hit.mp3"
    val yaşasın = "/media/collidium/win.mp3"

    val mağarada = "/media/music-loops/Cave.mp3"
    val basDavulVuruşları = "/media/music-loops/DrumBeats.mp3"
    val bateri = "/media/music-loops/Drum.mp3"
    val bateriElektronik = "/media/music-loops/DrumMachine.mp3"
    val bahçe = "/media/music-loops/Garden.mp3"
    val gitar1 = "/media/music-loops/GuitarChords1.mp3"
    val gitar2 = "/media/music-loops/GuitarChords2.mp3"
    val ortaçağ1 = "/media/music-loops/Medieval1.mp3"
    val ksilofon1 = "/media/music-loops/Xylo1.mp3"
    val ksilofon2 = "/media/music-loops/xylo4.mp3"
  }

  object Çalgı {
    val Piyano = 0
    val AkustikKuyruklu = 0
    val ParlakAkustik = 1
    val ElektroKuyruklu = 2
    val HonkyTonkPiyano = 3
    val ElektroPiyano = 4
    val AkustikBas = 32
    val Kuş = 123
    val Telefon = 124
    val Helikopter = 125
    val Alkış = 126
    val Tabanca = 127
  }

  // Çalıcı (zaman imleci, AudioContext) Builtins'te: notaÇal ile playNote aynı
  // imleci paylaşsın, karışık betikte de notalar sıraya girsin. TurkishTurtle
  // bunu builtins.notaÇalar ile ezer; tek başına karıştıranlar (testler) kendi
  // çalıcısını alır.
  protected lazy val notaÇalar: kojo.NotaÇalar = new kojo.NotaÇalar

  /** MIDI perdesini frekansa çevirir (69 → 440 Hz). */
  def notaFrekansı(nota: Sayı): Kesir = kojo.NotaÇalar.frekans(nota)

  /** MIDI çalgı kodunu Web Audio dalga biçimine eşler (kaba yaklaşım). */
  def çalgıDalgası(kod: Sayı): Yazı = kojo.NotaÇalar.dalga(kod)

  def notaÇalgısınıKur(çalgı: Sayı): Birim = {
    require(çalgı >= 0 && çalgı <= 127, "çalgı 0 ile 127 arasında olmalı")
    notaÇalar.çalgıyıKur(çalgı)
  }

  /** Beklemez: nota bir önceki notanın bittiği anda (ya da hemen) çalar. */
  def notaÇal(nota: Sayı, süreMiliSaniye: Sayı, ses: Sayı = 80): Birim = {
    require(nota >= 0 && nota <= 127, "nota 0 ile 127 arasında olmalı")
    require(ses >= 0 && ses <= 127, "ses 0 ile 127 arasında olmalı")
    notaÇalar.çal(nota, süreMiliSaniye, ses)
  }

  /**
   * Notaların HEPSİ aynı anda başlar, `süreMiliSaniye` kadar sürer; sonraki
   * nota o kadar sonra başlar. Masaüstünde karşılığı yok (iKojo'ya özgü).
   * Ses düzeyi nota sayısının KAREKÖKÜ kadar bölünür: akor tek nota kadar
   * yüksek çalar (çok kalabalık akorda cızırdamasın diye daha da kısılır).
   */
  def akorÇal(notalar: Diz[Sayı], süreMiliSaniye: Sayı, ses: Sayı = 80): Birim = {
    notalar.foreach(nota => require(nota >= 0 && nota <= 127, "nota 0 ile 127 arasında olmalı"))
    require(ses >= 0 && ses <= 127, "ses 0 ile 127 arasında olmalı")
    notaÇalar.akorÇal(notalar, süreMiliSaniye, ses)
  }

  /**
   * `(nota, süreMiliSaniye)` çiftlerinin hepsi aynı anda başlar, her nota kendi
   * süresince çalar; sonraki nota EN UZUN notanın bitişinde başlar. Melodi ile
   * bası beraber çalmak için: `beraberÇal(Dizi((67, 500), (43, 1000)))`.
   */
  def beraberÇal(süreliNotalar: Diz[(Sayı, Sayı)], ses: Sayı = 80): Birim = {
    süreliNotalar.foreach { case (nota, _) => require(nota >= 0 && nota <= 127, "nota 0 ile 127 arasında olmalı") }
    require(ses >= 0 && ses <= 127, "ses 0 ile 127 arasında olmalı")
    notaÇalar.beraberÇal(süreliNotalar, ses)
  }

  /** Sessiz bekleyiş (es): sıradaki nota `süreMiliSaniye` sonra başlar. */
  def notaSus(süreMiliSaniye: Sayı): Birim = notaÇalar.sus(süreMiliSaniye)

  /**
   * Sıradaki ve çalmakta olan bütün notaları susturur, zaman imlecini sıfırlar:
   * bundan sonraki `notaÇal` hemen çalar. Masaüstündeki `stopNotePlayer`; orada
   * Çalıştır/Durdur bunu kendiliğinden yapar, iKojo'da betik kendisi çağırır
   * (yoksa melodi çalarken Çalıştır'a basınca ikinci melodi birincinin ardına eklenir).
   */
  def notaÇalıcıyıDurdur(): Birim = notaÇalar.durdur()

  /** `notaÇalıcıyıDurdur` ile aynı (müziğiKapat/müziğiDurdur çiftleri gibi). */
  def notaÇalıcıyıKapat(): Birim = notaÇalıcıyıDurdur()

  /**
   * Sıradaki notaların bitmesine kalan süre (milisaniye), çalan yoksa 0. Nota
   * döngüsünden hemen sonra çağrılırsa melodinin toplam süresi. (`buAn - t0`
   * bunu vermez: notaÇal beklemez, yalnız sıraya koyar.)
   */
  def kalanNotaSüresi: Sayı = notaÇalar.kalanMiliSaniye
}
