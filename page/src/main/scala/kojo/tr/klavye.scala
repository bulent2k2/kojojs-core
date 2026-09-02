package kojo.tr

/**
 * Tuş kodlarının Türkçesi -- oyun yazmak için `tuşBasılıMı(tuşlar.sol)` gibi.
 *
 * Değerler KojoJS'in `kojo.KeyCodes` sınıfındaki VK_* sabitleriyle birebir aynı;
 * sabit oldukları için burada doğrudan yazılıyorlar (bağımlılık gerekmiyor).
 */
trait KlavyeYöntemleri extends TemelTürler {
  object tuşlar {
    val gir = '\n'.toInt
    val silGeri = '\b'.toInt
    val sekme = '\t'.toInt
    val iptal = 0x03
    val temizle = 0x0c
    val kaldırma = 0x10 // shift
    val kontrol = 0x11
    val alt = 0x12
    val dur = 0x13
    val büyükHarfKilidi = 0x14
    val çık = 0x1b // escape
    val kaç = çık
    val boşluk = 0x20
    val sayfaYukarı = 0x21
    val sayfaAşağı = 0x22
    val satırSonu = 0x23
    val satırBaşı = 0x24
    val ev = satırBaşı

    val sol = 0x25
    val yukarı = 0x26
    val sağ = 0x27
    val aşağı = 0x28

    val virgül = 0x2c
    val eksi = 0x2d
    val nokta = 0x2e
    val bölü = 0x2f
    val noktalıVirgül = 0x3b
    val eşittir = 0x3d

    val n0 = 0x30; val n1 = 0x31; val n2 = 0x32; val n3 = 0x33; val n4 = 0x34
    val n5 = 0x35; val n6 = 0x36; val n7 = 0x37; val n8 = 0x38; val n9 = 0x39

    val a = 0x41; val b = 0x42; val c = 0x43; val d = 0x44; val e = 0x45
    val f = 0x46; val g = 0x47; val h = 0x48; val i = 0x49; val j = 0x4a
    val k = 0x4b; val l = 0x4c; val m = 0x4d; val n = 0x4e; val o = 0x4f
    val p = 0x50; val q = 0x51; val r = 0x52; val s = 0x53; val t = 0x54
    val u = 0x55; val v = 0x56; val w = 0x57; val x = 0x58; val y = 0x59
    val z = 0x5a
  }
}
