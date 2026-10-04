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
 * BERABER çalan notalar (`beraberÇal`, `akorÇal`): hepsi aynı `başla` anında
 * başlar, her biri kendi süresince çalar; imleç EN UZUN notanın sonuna gider,
 * yani sonraki nota hepsi bittikten sonra başlar. Masaüstünde karşılığı yok
 * (tek kanal, tek iz); bu iKojo'ya özgü. Ses düzeyi nota sayısının karekökü
 * kadar bölünür: güç kabaca sabit kalır, akor tek nota kadar yüksek çalar. n
 * eş fazlı nota en kötü durumda n * düzey toplar; 0.3/√n yalnız n <= 10'a
 * kadar tepeyi 1'in altında tutar (0.3·√10 ≈ 0.95), o yüzden kalabalık gruplarda
 * düzey 0.95/n ile sınırlanıyor: tepe hiçbir grupta 0.95'i aşmaz, cızırdamaz.
 * Tek nota için bölen 1, yani `çal` eskisi gibi. Süresi 0 olan nota da grubun
 * büyüklüğüne sayılır (sessiz kalır ama ötekileri kısar).
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

  /** Bağlamı kurar ve askıdaysa sürdürür (kullanıcı etkileşiminden önce kurulan bağlam askıda başlar). */
  private def hazırBağlam(): js.Dynamic = {
    val ctx = bağlamıAl()
    if (ctx != null && (ctx.state: Any) == "suspended" && js.typeOf(ctx.resume) == "function") ctx.resume()
    ctx
  }

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

  def çal(nota: Int, süreMiliSaniye: Int, ses: Int): Unit =
    beraberÇal(Seq((nota, süreMiliSaniye)), ses)

  /** Hepsi aynı anda başlar, aynı süre sürer; sonraki nota `süre` sonra. */
  def akorÇal(notalar: collection.Seq[Int], süreMiliSaniye: Int, ses: Int): Unit =
    beraberÇal(notalar.map(n => (n, süreMiliSaniye)), ses)

  /**
   * Hepsi aynı anda başlar, her biri kendi süresince çalar; sonraki nota en
   * uzununun bitişinde başlar. Önce HEPSİ doğrulanıyor: biri sınır dışıysa
   * hiçbir nota sıraya girmiyor (yarım akor çalmıyor). Boş dizi bir şey yapmaz.
   */
  def beraberÇal(süreliNotalar: collection.Seq[(Int, Int)], ses: Int): Unit = {
    süreliNotalar.foreach { case (nota, _) =>
      require(nota >= 0 && nota <= 127, "Note pitch should be between 0 and 127")
    }
    require(ses >= 0 && ses <= 127, "Note volume should be between 0 and 127")
    if (süreliNotalar.nonEmpty) {
      val ctx = hazırBağlam()
      if (ctx != null) {
        val şimdi = ctx.currentTime.asInstanceOf[Double]
        val başla = math.max(şimdi, imleç)
        val n = süreliNotalar.size
        // 0.3/√n n <= 10'a kadar; ötesinde 0.95/n: n eş fazlı notanın toplamı 0.95'i aşmasın
        val taban = math.min(0.3 / math.sqrt(n), 0.95 / n)
        // exponentialRamp sıfırdan başlayamaz (tanımsız); ses = 0 için ufak taban
        val düzey = math.max(ses / 127.0 * taban, 1e-4) // hoparlörü patlatmadan
        var sonBitiş = başla
        süreliNotalar.foreach { case (nota, süreMiliSaniye) =>
          val bitiş = başla + math.max(süreMiliSaniye, 0) / 1000.0
          sonBitiş = math.max(sonBitiş, bitiş)

          val osilatör = ctx.createOscillator()
          val kazanç = ctx.createGain()
          osilatör.`type` = NotaÇalar.dalga(çalgı)
          osilatör.frequency.value = NotaÇalar.frekans(nota)
          kazanç.gain.setValueAtTime(düzey, başla)
          kazanç.gain.exponentialRampToValueAtTime(0.001, bitiş)
          osilatör.connect(kazanç)
          kazanç.connect(ctx.destination)
          osilatör.start(başla)
          osilatör.stop(bitiş)

          çalanlar += ((kazanç, bitiş))
        }
        imleç = sonBitiş
        çalanlar.filterInPlace(_._2 > şimdi)
      }
    }
  }

  /** Sessiz bekleyiş: imleci ilerletir, ses çıkarmaz (müzikte es). */
  def sus(süreMiliSaniye: Int): Unit = {
    val ctx = hazırBağlam()
    if (ctx != null) {
      val şimdi = ctx.currentTime.asInstanceOf[Double]
      imleç = math.max(şimdi, imleç) + math.max(süreMiliSaniye, 0) / 1000.0
    }
  }

  /**
   * Sıradaki bütün notaların bitmesine kalan süre (ms), boştaysa 0. Döngüyle
   * nota dizdikten hemen sonra çağrılırsa melodinin toplam süresi. (Betikteki
   * `buAn - t0` bunu vermez: notaÇal beklemez, yalnız sıraya koyar.)
   * Bağlam askıdaysa (kullanıcı etkileşiminden önce) saat ilerlemez, yani
   * sıradaki bütün notaların süresi döner.
   */
  def kalanMiliSaniye: Int =
    if (bağlam == null) 0
    else math.max(0.0, math.round((imleç - bağlam.currentTime.asInstanceOf[Double]) * 1000.0).toDouble).toInt

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
