package scalafiddle.router

import upickle.default._

/** `/bilgi` ucunun gövdesi: sunucunun hangi ağaçtan kurulduğu ve nerede koştuğu.
  *
  * İki ayrı kaynaktan besleniyor ve ikisi de eksik olabilir:
  *
  *   - DERLEME ZAMANI (`core`/`dev`/`editor`/`tarih`): koco-deploy/build.sh
  *     klonların commit'lerini imaja damgalıyor, start.sh onları KOCO_SURUM_*
  *     ortam değişkenleri olarak geçiriyor. Damgasız kurulumlarda boş kalır.
  *   - KOŞMA ZAMANI (`flyImaj`/`flyMakine`/`flyBolge`): Fly'ın konteynere
  *     verdiği FLY_* değişkenleri. Fly dışında koşarken boş kalır.
  *
  * Boş alanlar `""` olarak dönüyor, alan hiç düşmüyor: tüketen tarafın şema
  * beklentisi yayım ortamına göre değişmesin.
  */
/** `/bilgi`'nin DIŞARIYA açık hâli: yalnız "hangi ağaçtan kuruldu".
  *
  * Alanların hepsi zaten açık depolarda duran commit'ler, artı imaj kimliği.
  * DIŞARIDA OLMAYANLAR ve sebebi: `flyMakine` ve `flyBolge` iç kimlikler,
  * `derleyiciSayisi` ise kapasiteyi söylüyor -- koco-deploy#17'de ölçüldüğü
  * gibi kapasite aşılınca kullanıcıya anında hata çıkıyor, yani o sayı
  * sunucuyu doyurmanın tarifi. Tamamı için /bilgi/tam (anahtarlı).
  */
case class BilgiGenel(core: String,
                      dev: String,
                      editor: String,
                      tarih: String,
                      router: String,
                      flyImaj: String)

object BilgiGenel { implicit val rw: ReadWriter[BilgiGenel] = macroRW }

case class Bilgi(core: String,
                 dev: String,
                 editor: String,
                 tarih: String,
                 router: String,
                 flyImaj: String,
                 flyMakine: String,
                 flyBolge: String,
                 derleyiciSayisi: String)

object Bilgi {
  implicit val rw: ReadWriter[Bilgi] = macroRW

  private def cevre(ad: String): String = sys.env.getOrElse(ad, "")

  /** Her istekte yeniden okunuyor (önbelleklenmiyor): ortam değişkenleri süreç
    * ömrü boyunca sabit olsa da, burada bir değer önbelleklemek "eski bilgi
    * gösteren tanı ucu" sınıfına kapı açar -- bu ucun var olma sebebinin tersi.
    */
  def simdiki: Bilgi = Bilgi(
    core = Config.surum.core,
    dev = Config.surum.dev,
    editor = Config.surum.editor,
    tarih = Config.surum.tarih,
    router = Config.version,
    flyImaj = cevre("FLY_IMAGE_REF"),
    flyMakine = cevre("FLY_MACHINE_ID"),
    flyBolge = cevre("FLY_REGION"),
    derleyiciSayisi = cevre("COMPILER_INSTANCES")
  )

  /** Dışarıya verilen kırpılmış hâl. Tam hâlden TÜRETİLMİYOR, alanları tek tek
    * yazılıyor: `Bilgi`'ye ileride eklenen bir alan buraya kendiliğinden
    * sızmasın. Yeni alanın dışarı çıkması bilinçli bir satır gerektirsin.
    */
  def genel: BilgiGenel = {
    val t = simdiki
    BilgiGenel(t.core, t.dev, t.editor, t.tarih, t.router, t.flyImaj)
  }
}
