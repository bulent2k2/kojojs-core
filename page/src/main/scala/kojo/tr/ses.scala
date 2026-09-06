package kojo.tr

import scala.scalajs.js

/**
 * Masaüstü Koco'nun ses adları (kojo: lite/i18n/tr/ses.scala, tr/muzik.scala,
 * trInit.scala notaÇal). İki bölüm:
 *
 *  - `Ses` sabitleri: mp3 yolları. Masaüstüyle aynı `/media/...` yolları; dosyalar
 *    kojojs-dev/medya altında, koco-deploy nginx'i `/media/`yi oraya bağlar.
 *    Çalma `sesMp3üÇal` / `müzikMp3üÇalDöngülü` (TurkishTurtle, howler).
 *  - `notaÇal(nota, süreMs, ses)`: masaüstünde MIDI (RealtimeNotePlayer); burada
 *    Web Audio osilatörü. `nota` MIDI perdesi (0-127; 69 = la 440 Hz), `ses` 0-127.
 *    `Çalgı` kodları MIDI program numaraları; burada dalga biçimine kabaca eşlenir
 *    (piyano ailesi üçgen, bas sinüs, gürültülü çalgılar kare/testere).
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

  private var çalgıKodu: Sayı = 0
  private var sesBağlamı: js.Dynamic = null

  /** MIDI perdesini frekansa çevirir (69 → 440 Hz). */
  def notaFrekansı(nota: Sayı): Kesir = 440.0 * math.pow(2.0, (nota - 69) / 12.0)

  /** MIDI çalgı kodunu Web Audio dalga biçimine eşler (kaba yaklaşım). */
  def çalgıDalgası(kod: Sayı): Yazı =
    if (kod < 8) "triangle"            // piyanolar
    else if (kod < 16) "sine"          // renkli vurmalılar (çelesta, ksilofon...)
    else if (kod < 24) "square"        // orglar
    else if (kod < 32) "sawtooth"      // gitarlar
    else if (kod < 40) "sine"          // baslar
    else if (kod < 56) "sawtooth"      // yaylılar, koro
    else if (kod < 72) "square"        // nefesliler
    else if (kod < 120) "triangle"     // sentez, etnik
    else "sawtooth"                    // ses efektleri

  def notaÇalgısınıKur(çalgı: Sayı): Birim = { çalgıKodu = çalgı }

  def notaÇal(nota: Sayı, süreMiliSaniye: Sayı, ses: Sayı = 80): Birim = {
    require(nota >= 0 && nota <= 127, "nota 0 ile 127 arasında olmalı")
    require(ses >= 0 && ses <= 127, "ses 0 ile 127 arasında olmalı")
    import js.Dynamic.{global => g}
    val varMı = js.typeOf(g.AudioContext) != "undefined" || js.typeOf(g.webkitAudioContext) != "undefined"
    if (varMı) {
      if (sesBağlamı == null) {
        val Ctx = if (js.typeOf(g.AudioContext) != "undefined") g.AudioContext else g.webkitAudioContext
        sesBağlamı = js.Dynamic.newInstance(Ctx)()
      }
      val ctx = sesBağlamı
      val osilatör = ctx.createOscillator()
      val kazanç = ctx.createGain()
      osilatör.`type` = çalgıDalgası(çalgıKodu)
      osilatör.frequency.value = notaFrekansı(nota)
      val şimdi = ctx.currentTime.asInstanceOf[Double]
      val süre = süreMiliSaniye / 1000.0
      // exponentialRamp sıfırdan başlayamaz (tanımsız); ses = 0 için ufak taban
      val düzey = math.max(ses / 127.0 * 0.3, 1e-4) // hoparlörü patlatmadan
      kazanç.gain.setValueAtTime(düzey, şimdi)
      kazanç.gain.exponentialRampToValueAtTime(0.001, şimdi + süre)
      osilatör.connect(kazanç)
      kazanç.connect(ctx.destination)
      osilatör.start(şimdi)
      osilatör.stop(şimdi + süre)
    }
    // Web Audio yoksa (ör. Node testleri) sessizce geç
  }
}
