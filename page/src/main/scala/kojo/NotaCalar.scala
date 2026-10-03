package kojo

import scala.collection.mutable
import scala.scalajs.js

/**
 * `playNote` / `notaÇal`'ın çalıcısı: Web Audio osilatörü, masaüstünün
 * RealtimeNotePlayer'ı (MIDI) gibi bir ZAMAN İMLECİYLE.
 *
 * Masaüstünde playNote beklemez: notayı MIDI izine imlecin olduğu yere ekler ve
 * imleci süre kadar ilerletir; sıralayıcı boştaysa hemen, çalıyorsa öncekiler
 * bitince çalar. Arka arkaya çağrılar böylece bir EZGİ olur. Burada da aynısı:
 * nota `max(şimdi, imleç)` anında başlar, imleç notanın sonuna gider. (İmlecin
 * olmadığı eski hâlde bütün notalar aynı anda, akor gibi çalıyordu: #179.)
 *
 * Çalgı kodları MIDI program numaraları; Web Audio'da çalgı yok, dalga biçimine
 * kabaca eşlenir (`dalga`). Çalgı değişikliği, masaüstündeki gibi, yalnız
 * sonraki notaları etkiler.
 */
class NotaÇalar {
  // Test kancası: sahte bağlam ya da OfflineAudioContext buraya konabilir.
  // null ise ilk notada tarayıcının AudioContext'i kurulur.
  private[kojo] var bağlam: js.Dynamic = null
  // Sıradaki notanın en erken başlayabileceği an (bağlamın saatiyle, saniye)
  private var imleç = 0.0
  private var çalgı = 0
  // (kazanç düğümü, bitiş anı): durdur() için, bitenler ayıklanır
  private val çalanlar = mutable.ArrayBuffer.empty[(js.Dynamic, Double)]

  private def bağlamıAl(): js.Dynamic = {
    if (bağlam == null) {
      import js.Dynamic.{global => g}
      val Ctx =
        if (js.typeOf(g.AudioContext) != "undefined") g.AudioContext
        else if (js.typeOf(g.webkitAudioContext) != "undefined") g.webkitAudioContext
        else null
      // Web Audio yoksa (ör. Node testleri) sessizce geç
      if (Ctx != null) bağlam = js.Dynamic.newInstance(Ctx)()
    }
    bağlam
  }

  def çalgıyıKur(kod: Int): Unit = {
    require(kod >= 0 && kod <= 127, "Instrument Code should be between 0 and 127")
    çalgı = kod
  }

  def çal(nota: Int, süreMiliSaniye: Int, ses: Int): Unit = {
    require(nota >= 0 && nota <= 127, "Note pitch should be between 0 and 127")
    require(ses >= 0 && ses <= 127, "Note volume should be between 0 and 127")
    val ctx = bağlamıAl()
    if (ctx != null) {
      // Kullanıcı etkileşiminden önce kurulan bağlam askıda başlar
      if ((ctx.state: Any) == "suspended" && js.typeOf(ctx.resume) == "function") ctx.resume()
      val şimdi = ctx.currentTime.asInstanceOf[Double]
      val başla = math.max(şimdi, imleç)
      val bitiş = başla + math.max(süreMiliSaniye, 0) / 1000.0
      imleç = bitiş

      val osilatör = ctx.createOscillator()
      val kazanç = ctx.createGain()
      osilatör.`type` = NotaÇalar.dalga(çalgı)
      osilatör.frequency.value = NotaÇalar.frekans(nota)
      // exponentialRamp sıfırdan başlayamaz (tanımsız); ses = 0 için ufak taban
      val düzey = math.max(ses / 127.0 * 0.3, 1e-4) // hoparlörü patlatmadan
      kazanç.gain.setValueAtTime(düzey, başla)
      kazanç.gain.exponentialRampToValueAtTime(0.001, bitiş)
      osilatör.connect(kazanç)
      kazanç.connect(ctx.destination)
      osilatör.start(başla)
      osilatör.stop(bitiş)

      çalanlar.filterInPlace(_._2 > şimdi)
      çalanlar += ((kazanç, bitiş))
    }
  }

  /** Sıradaki bütün notaları susturur, imleci sıfırlar (masaüstü stopNotePlayer). */
  def durdur(): Unit = {
    çalanlar.foreach { case (kazanç, _) => kazanç.disconnect() }
    çalanlar.clear()
    imleç = 0.0
  }
}

object NotaÇalar {
  /** MIDI perdesini frekansa çevirir (69 → 440 Hz). */
  def frekans(nota: Int): Double = 440.0 * math.pow(2.0, (nota - 69) / 12.0)

  /** MIDI çalgı kodunu Web Audio dalga biçimine eşler (kaba yaklaşım). */
  def dalga(kod: Int): String =
    if (kod < 8) "triangle"            // piyanolar
    else if (kod < 16) "sine"          // renkli vurmalılar (çelesta, ksilofon...)
    else if (kod < 24) "square"        // orglar
    else if (kod < 32) "sawtooth"      // gitarlar
    else if (kod < 40) "sine"          // baslar
    else if (kod < 56) "sawtooth"      // yaylılar, koro
    else if (kod < 72) "square"        // nefesliler
    else if (kod < 120) "triangle"     // sentez, etnik
    else "sawtooth"                    // ses efektleri
}
