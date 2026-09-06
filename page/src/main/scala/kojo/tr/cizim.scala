package kojo.tr

/**
 * Masaüstü Koco'nun giysi/artalan görüntü adları (kojo: lite/i18n/tr/cizim.scala).
 * Değerler masaüstüyle aynı `/media/...` yolları; dosyalar kojojs-dev/medya altında,
 * koco-deploy nginx'i `/media/`yi oraya bağlar. Kullanım: `Resim.imge(Görünüş.araba)`.
 * Giysiler `yeniKaplumbağa(x, y, Görünüş.araba)` ile kullanılabilir; kaplumbağayı
 * sonradan giydiren komutlar (giysiKur, giysileriKur...) henüz yok. `/images/...` ile başlayanlar
 * masaüstü kavanozunun içindeki simgeler; burada sunulmaz (404).
 */
trait GörünüşYöntemleri extends TemelTürler {
  object Artalan {
    val demiryolu: Yazı = "/media/backgrounds/train-tracks3.gif"
  }

  object Görünüş {
    val demiryolu: Yazı = "/media/backgrounds/train-tracks3.gif"
    val scala: Yazı = "/images/scala16x16.png"
    val kare: Yazı = "/images/kindvar.png"
    val baklava: Yazı = "/images/field.png"
    val bayrak1: Yazı = "/images/generic-flag.png"
    val daire: Yazı = "/images/kindmethod.png"
    val yıldız: Yazı = "/images/star.png"
    val yarasa1a: Yazı = "/media/costumes/bat1-a.png"
    val yarasa1b: Yazı = "/media/costumes/bat1-b.png"
    val kalem: Yazı = "/media/costumes/pencil.png"
    val kadınElSallarken: Yazı = "/media/costumes/womanwaving.png"
    val araba: Yazı = "/media/costumes/car.png"
    val araba1: Yazı = "/media/car-ride/car1.png"
    val araba2: Yazı = "/media/car-ride/car2.png"
    val yarasa1: Yazı = "/media/costumes/bat1-a.png"
    val yarasa2: Yazı = "/media/costumes/bat1-b.png"
    val elSallayanKadın: Yazı = "/media/costumes/womanwaving.png"

    val tuğla: Yazı = "/media/collidium/bwall.png"
    val top1: Yazı = "/media/collidium/ball1.png"
    val top2: Yazı = "/media/collidium/ball2.png"
    val top3: Yazı = "/media/collidium/ball3.png"
    val top4: Yazı = "/media/collidium/ball4.png"

    val topKanatlı1: Yazı = "/media/flappy-ball/ballwing1.png"
    val topKanatlı2: Yazı = "/media/flappy-ball/ballwing2.png"
  }
  val Çizim = Görünüş // masaüstü takma adı
}
