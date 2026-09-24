/*
 * Copyright (C) 2026 Bülent Başaran <bulent2k2@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */
package kojo

import org.scalajs.dom.{document, window}

import scala.scalajs.js

/**
 * Dolgu hesabı bir karelik bütçeyi aşarsa çıktı panelinden not düşer (#68).
 *
 * KUSUR DEĞİL, MALİYET: kendini kesen bir yolun NON_ZERO dolgusunu hesaplamak
 * (libtess) nokta sayısıyla süperdoğrusal büyüyor. #68'de ölçüldü, yerel
 * büyüme üssü 1.6-2.1:
 *
 *   250 nokta x 7 kat  ->    ~8 ms
 *   1000 nokta x 7 kat ->   ~95 ms
 *   2000 nokta x 7 kat ->  ~440 ms
 *   4000 nokta x 7 kat -> ~1840 ms
 *
 * Kesişmeyen yol bedavaya yakın: 4000 noktada bile <= 6 ms. Yani pahalı olan
 * NOKTA SAYISI DEĞİL, kesişmeyle birlikte nokta sayısı.
 *
 * NEDEN SÜREYİ ÖLÇÜYORUZ, NOKTA SAYMIYORUZ: #68 "nokta sayısına bakan basit
 * bir eşik" öneriyordu, çünkü "kesişiyor mu" detektörü yazmak pahalı ve zor.
 * Ama kaydın kendi ölçümü nokta sayısının KÖTÜ bir gösterge olduğunu
 * söylüyor -- 4000 nokta kesişmezse 6 ms, 1000 nokta kesişirse 95 ms. Nokta
 * eşiği ya büyük kesişmeyen şekillere yanlış alarm verir ya da kesişen
 * şekilleri kaçırır. Geçen süreyi ölçmek ikisini birden çözüyor ve kesişme
 * tespiti sorununu tümüyle ortadan kaldırıyor: tahmin etmiyoruz, OLANI
 * bildiriyoruz. Bedeli iki `performance.now()` çağrısı.
 *
 * KARŞILIĞINDA: uyarı maliyet ÖDENDİKTEN sonra geliyor, yani ilk yavaş kareyi
 * engellemiyor. Amaç da o değil -- amaç kullanıcının betiğinin neden yavaş
 * olduğunu ÖĞRENMESİ. Sessizliği düzeltiyoruz, davranışı değil
 * (DuraklamaUyarısı ile aynı gerekçe).
 *
 * EŞİK bir karelik bütçe: 60 kare/saniye -> 16.7 ms. Bunun altında kalan bir
 * dolgu canlandırmayı tek başına düşüremez; üstüne çıkan düşürebilir.
 * Ölçümle yerleşimi: 250x7 (~8 ms) susuyor, 1000x7 (~95 ms) konuşuyor.
 */
/**
 * Bir ÇİZERİN o anki şeklinin birikimi.
 *
 * KÜRESEL OLAMAZ, ölçüldü (#130 incelemesi): `KojoWorld.boyalarıBoşalt` tek
 * turda BİRDEN ÇOK çizerin boyasını yayınlıyor, yani iki ayrı `Resim{}`in iki
 * ayrı şeklinin süresi aynı kovaya akardı. İlk sürümde tam bu oldu: iki resim,
 * her biri 30 ms, panele "şu ana dek 60 ms" düştü -- oysa hiçbir şekil 60 ms
 * harcamamıştı. Daha kötüsü, araya giren UCUZ ve BİTMİŞ bir şekil başkasının
 * birikimini üstlenebiliyordu: 4 noktalı bir kareye 50 ms fatura edilip
 * kullanıcıya o karenin nokta sayısını yarıya indirmesi öğütleniyordu.
 *
 * Yani düzeltilen kusurun (yanlış nokta sayısı) daha kötü bir biçimi: orada
 * sayı yanlıştı ama ŞEKİL doğruydu; burada ikisi de yanlış olabiliyordu.
 */
private[kojo] final class ŞekilBirikimi {
  private[kojo] var toplamMs = 0.0
  private[kojo] var bildirildi = false

  /** En son yayında görülen nokta sayısı -- `şekilDurdu` bunu bildiriyor. */
  private[kojo] var sonNoktaSayısı = 0

  private[kojo] def unut(): Unit = {
    toplamMs = 0.0
    bildirildi = false
    sonNoktaSayısı = 0
  }
}

/**
 * Sabitler, metin ve panel -- durumsuz (#149). Durum (saat, sayaç, zaman
 * kapısı, erken çarpan) `ÜçgenlemeRaporu`nda, dünya başına.
 */
object ÜçgenlemeUyarısı {

  /** Bir karelik bütçe (60 kare/saniye). */
  private[kojo] val bütçeMs = 16.7

  /**
   * İki not arasındaki en az süre -- DuraklamaUyarısı'ndaki zaman kapısının
   * aynısı, aynı gerekçeyle (#98 incelemesi): `canlandır` içinde her karede
   * yeniden çizilen ağır bir şekil saniyede 20-50 özdeş satır basardı ve
   * tekrar eden uyarı, yanlış uyarı kadar hızlı öğretir ki uyarılar
   * okunmasın. Koşum başına sayaç yerine zaman kapısı, çünkü buradaki iki
   * kalıp (bir karede birkaç yayın / yeni bir koşum) zamanda ayrışıyor.
   */
  private[kojo] val enAzAralıkMs = 2000.0

  /** `ÜçgenlemeRaporu.erkenÇarpan`ın varsayılanı (gerekçesi orada). */
  private[kojo] val varsayılanErkenÇarpan = 3.0

  /**
   * Tarayıcı saati. `paneleYaz` ile AYNI özeni gösteriyor (tarayıcı yoksa
   * çökmüyor) -- üstelik burası SICAK yol: `Turtle.üçgenleriÇiz` her dolguda
   * iki kez çağırıyor. Tarayıcısız ortamda 0 dönmek doğru bozulma: süre hep 0
   * çıkar, eşik hiç aşılmaz, not da düşmez -- zaten yazacak panel yok.
   * Çözüm `lazy val`de bir kez yapılıyor, çağrı başına değil.
   */
  private[kojo] lazy val tarayıcıSaati: () => Double =
    if (js.typeOf(js.Dynamic.global.window) == "undefined" ||
        js.isUndefined(js.Dynamic.global.window.performance)) () => 0.0
    else () => window.performance.now()

  /**
   * Okunabilir olsun diye üç parça: NE oldu (sayılarla), NEDEN, NE YAPILABİLİR.
   *
   * Süre tam sayıya yuvarlanıyor -- ondalık burada bilgi taşımıyor ve ölçüm
   * zaten koşudan koşuya oynuyor. BÜTÇE yuvarlanMIYOR: `bütçeMs.round` 17
   * basıyordu, yani süresi (16.7, 17.5) arasına düşen bir dolgu için not
   * "17 ms sürdü -- bir karelik bütçe 17 ms" diye okunuyordu; eşik aşıldığı
   * için düşen bir not kendi gerekçesini yalanlıyordu. Dar bir bant ama tam
   * da yavaş makinelerin bandı: orada süreler eşiğin hemen üstünde kümelenir.
   */
  private[kojo] def metin(süreMs: Double, noktaSayısı: Int, bitti: Boolean): String =
    (if (bitti)
       s"Not: bu şeklin dolgusunu hesaplamak ${süreMs.round} ms sürdü ($noktaSayısı nokta)"
     else
       s"Not: bu şeklin dolgusu şu ana dek ${süreMs.round} ms aldı " +
         s"(şimdilik $noktaSayısı nokta; şekil büyüdükçe artacak)") +
      s" -- bir karelik bütçe $bütçeMs ms. " +
      "Kendini kesen şekillerde dolgu hesabı nokta sayısıyla karesele yakın " +
      "büyüyor, yani nokta sayısını yarıya indirmek süreyi dörtte bire yakın " +
      "düşürür. Canlandırma içindeyse daha az noktayla çizmeyi ya da " +
      "boyamaRenginiKur çağırmayıp yalnız kalemle çizmeyi deneyebilirsin."

  /**
   * Çıktı paneline yazar; panel yoksa (tarayıcı dışı koşum, sınamalar) konsola
   * düşer. DOM sözleşmesi DuraklamaUyarısı ile aynı -- id="output", kendi
   * div'imizi ekliyoruz.
   */
  /** private[kojo]: KojoWorldImpl elle geri dönüş satırını aynı panele yazıyor. */
  private[kojo] def paneleYaz(metin: String): Unit = {
    val panel = if (js.typeOf(js.Dynamic.global.document) == "undefined") null else document.getElementById("output")
    if (panel == null) {
      js.Dynamic.global.console.warn(metin)
    }
    else {
      val satır = document.createElement("div")
      satır.appendChild(document.createTextNode(metin))
      panel.appendChild(satır)
      panel.scrollTop = panel.scrollHeight - panel.clientHeight
    }
  }
}

/**
 * DÜNYA BAŞINA rapor durumu (#149): saat, sayaç, zaman kapısı, erken çarpan.
 *
 * NEDEN DÜNYA BAŞINA. Bu durum önce `object ÜçgenlemeUyarısı`nda TEKİLDİ ve
 * sınamalar onu paylaşıyordu; aynı kökten üç ayrı olay çıktı (#124: sahte
 * saatin takımlar arası sızması uyarıyı susturdu; #130: birikim küreseldi;
 * #148: fikstür + erken eşik + takımlar arası artık -- ve #162'de dördüncüsü:
 * sbt takımları aynı olay döngüsünde iç içe koşturunca bir takımın sahte
 * saatiyle ötekinin pompadan geçen gülü not düşürdü). Her seferinde noktasal
 * hafifletme (afterAll geri verme, hepsiniUnut, 300 ms bekleyiş, erken yolu
 * kapatma, sıralı takımlar). Canlıda tek dünya var, davranış aynı; sınamada
 * her dünya kendi saatini, sayacını ve kapısını taşıyor -- bir dünyanın
 * kirletmesi ötekine ulaşamıyor.
 *
 * `susturuldu`: `KojoWorld.kapat` bunu koyuyor. Kapanmış bir dünyanın geç
 * kalan işi (giysisi geç yüklenen kaplumbağa, bekleyen boya) paylaşılan
 * panele yazmasın -- sayacı ve kapısı zaten ayrı, ama panel DOM'da tek.
 */
private[kojo] final class ÜçgenlemeRaporu {
  import ÜçgenlemeUyarısı._

  /** Sınama saati değiştirebilsin diye `var`; varsayılanı tarayıcı saati. */
  private[kojo] var saat: () => Double = tarayıcıSaati

  /**
   * Şekil BİTMEDEN konuşma eşiği. Bitmeyi beklemek yetmiyor, çünkü bir şekil
   * hiç bitmeyebilir: şekli tamamlayan tek şey kalem kalkık taşınma
   * (`turtlePathMoveTo`) ya da boya değişimi (`realSetFillPaint`), ve betiğin
   * SON şekli çoğu zaman ikisini de görmeden bitiyor.
   * `ornekler/14-agir-dolgu.kojo`'nun 1000 noktalık gülü tam böyle -- yalnız
   * tamamlanmış şekle bakan bir uyarı, uyarılması gereken şekli susturuyordu.
   * `var`: sınama kendi dünyasında erken yolu kapatabilsin (UcgenlemeDilimTest).
   */
  private[kojo] var erkenÇarpan = varsayılanErkenÇarpan

  private var sonNotZamanı = Double.NegativeInfinity
  private var notSayısı = 0
  private var susturuldu = false

  /** Panele GERÇEKTEN kaç not düştü. Zaman kapısına takılanlar sayılmıyor. */
  private[kojo] def düşenNotSayısı: Int = notSayısı

  /** Zaman kapısını ve sayacı sıfırla (sınama içinde iki evreyi ayırmak için). */
  private[kojo] def hepsiniUnut(): Unit = {
    sonNotZamanı = Double.NegativeInfinity
    notSayısı = 0
  }

  /** Kapanmış dünya: bundan sonra not yok (bkz. sınıf belgesi). */
  private[kojo] def sustur(): Unit = susturuldu = true

  /**
   * Bir ŞEKLİN dolgusu, o şekil bitmeden birkaç kez yayınlanabiliyor: pompa bir
   * karede en çok bir dilim iş yapıp kareye teslim ediyor (KojoWorld.DilimMs,
   * #131; eskiden 100 komutta bir setTimeout), her karede `requestAnimationFrame`
   * BÜYÜYEN çokgeni yeniden üçgenliyor. Ölçüldü (gerçek tarayıcı, #125): 250 noktalık bir gül için not
   * "146 nokta" diyordu -- kullanıcının betiğinde olmayan bir sayı.
   *
   * O yüzden ölçüm ŞEKİL BAŞINA birikiyor ve not şekil başına EN ÇOK BİR KEZ
   * düşüyor. Kullanıcının ödediği bedel zaten toplam: yarım yayınlar da
   * gerçekten harcanmış süre.
   *
   * Bütçeyi aşmadıysa hiçbir şey yapmıyor -- sıcak yolda tek bir
   * karşılaştırma.
   *
   * @param bitti çokgen tamamlandı (kalem kalkık taşınma / boya değişimi):
   *              birikim bu yayınla kapanıyor.
   * @param durdu çokgen tamamlanmadı ama şekle bir daha nokta gelmeyecek
   *              (`Turtle.şekilDurmuş`, kare sınırında): sayı nihai, kesin
   *              biçim. `bitti`den farkı birikimin kapanmaması -- şekil
   *              teknik olarak açık, yeniden yayınlanabilir (`çiz`/`sil`).
   *              Sıra önemli: `bittiSayılır` erkenÇarpan'dan ÖNCE bakılıyor,
   *              yoksa toplam erken eşiği aşmışsa "şimdilik" biçimi basılır,
   *              oysa şekil durmuş durumda (#134 canlı ölçüm).
   */
  private[kojo] def üçgenlemeBitti(
      birikim: ŞekilBirikimi, süreMs: Double, noktaSayısı: Int, bitti: Boolean, durdu: Boolean = false): Unit = {
    birikim.toplamMs += süreMs
    birikim.sonNoktaSayısı = noktaSayısı
    val bittiSayılır = bitti || durdu
    val toplam = birikim.toplamMs
    val konuşulabilir = toplam > bütçeMs && (bittiSayılır || toplam > erkenÇarpan * bütçeMs)
    if (!birikim.bildirildi && konuşulabilir) konuş(birikim, metin(toplam, noktaSayısı, bittiSayılır))
    if (bitti) birikim.unut()
  }

  /**
   * Şekil BÜYÜMEYİ BIRAKTI ve son yayını çoktan yapılmış: biriken süreyi
   * son yayının nokta sayısıyla, kesin biçimde bildir.
   *
   * NEDEN AYRI BİR GİRİŞ. Bir şekli "bitmiş" sayan iki yol var
   * (`turtlePathMoveTo` ve `realSetFillPaint`), ve betiğin SON şekli çoğu
   * zaman ikisini de görmüyor. `erkenÇarpan` o boşluğun kaba vekiliydi:
   * bütçeyi aşan ama 50.1 ms'yi aşmayan her son şekil sessiz kalıyordu --
   * `ornekler/14-agir-dolgu.kojo`'nun 35 ms'lik gülü tam oraya düşüyordu
   * (#133). Durmuş şekil için iki giriş var: yayını bekleyen şekil
   * `üçgenlemeBitti(durdu = true)` ile o yayında konuşuyor; yayını olmayan
   * (son komutları nokta eklemedi) burada. Her ikisi de kare sınırında,
   * `KojoWorld.boyalarıBoşalt`tan (bkz. Turtle.şekilDurmuş -- niye boşalma
   * anında değil, ve #134'ün "(193 nokta)" kusurunun yeni pompadaki yolu).
   *
   * SAHNEYE DOKUNMUYOR, bilerek: `boyamayıİşle` gibi kalıcı düğüm YAZMIYOR,
   * yalnız biriken süreyi bildiriyor. `bitti` bugün iki ayrı soruyu birden
   * cevaplıyor -- "çokgen tamamlandı mı" (pahalı, sahne durumu) ve "daha
   * nokta gelecek mi" (yalnız rapor); burada yalnız ikincisi soruluyor.
   * Ölçüldü (#134): kuyruk boşalması canlandırma döngüsünde kare başına
   * 1.63 kez oluyor; oraya kalıcı düğüm yazan bir kanca #91/#109'da
   * kapatılan sızıntıyı geri getirirdi.
   *
   * `erkenÇarpan` KALDIRILMADI: o hâlâ BÜYÜMEKTE olan çok ağır bir şekli
   * (örneğin 1000 noktalı gülü, daha bitmeden) haber veriyor. Buradaki
   * giriş yalnız durmuş şekli kapsıyor; ikisi ayrı durum.
   */
  private[kojo] def şekilDurdu(birikim: ŞekilBirikimi): Unit =
    if (!birikim.bildirildi && birikim.toplamMs > bütçeMs)
      konuş(birikim, metin(birikim.toplamMs, birikim.sonNoktaSayısı, bitti = true))

  /** Zaman kapısı + sayaç + panel; iki girişin ortak ucu. */
  private def konuş(birikim: ŞekilBirikimi, satır: String): Unit =
    if (!susturuldu) {
      val şimdi = saat()
      if (şimdi - sonNotZamanı >= enAzAralıkMs) {
        sonNotZamanı = şimdi
        notSayısı += 1
        birikim.bildirildi = true
        paneleYaz(satır)
      }
    }
}
