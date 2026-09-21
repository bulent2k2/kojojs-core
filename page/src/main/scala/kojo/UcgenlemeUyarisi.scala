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
  private[kojo] def unut(): Unit = {
    toplamMs = 0.0
    bildirildi = false
  }
}

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

  /**
   * Sınama saati değiştirebilsin diye ayrı.
   *
   * Varsayılanı `paneleYaz` ile AYNI özeni gösteriyor (tarayıcı yoksa
   * çökmüyor) -- üstelik burası SICAK yol: `Turtle.üçgenleriÇiz` her dolguda
   * iki kez çağırıyor. Tarayıcısız ortamda 0 dönmek doğru bozulma: süre hep 0
   * çıkar, eşik hiç aşılmaz, not da düşmez -- zaten yazacak panel yok.
   * Çözüm `lazy val`de bir kez yapılıyor, çağrı başına değil.
   */
  private[kojo] var saat: () => Double = () => tarayıcıSaati()

  private lazy val tarayıcıSaati: () => Double =
    if (js.typeOf(js.Dynamic.global.window) == "undefined" ||
        js.isUndefined(js.Dynamic.global.window.performance)) () => 0.0
    else () => window.performance.now()

  /**
   * Bir ŞEKLİN dolgusu, o şekil bitmeden birkaç kez yayınlanıyor: `scheduleLater`
   * ilk 100 komutu eşzamanlı koşturup sonrasını erteliyor (KojoWorld.MaxBurst),
   * arada `requestAnimationFrame` devreye girip BÜYÜYEN çokgeni yeniden
   * üçgenliyor. Ölçüldü (gerçek tarayıcı, #125): 250 noktalık bir gül için not
   * "146 nokta" diyordu -- kullanıcının betiğinde olmayan bir sayı.
   *
   * O yüzden ölçüm ŞEKİL BAŞINA birikiyor ve not şekil başına EN ÇOK BİR KEZ
   * düşüyor. Kullanıcının ödediği bedel zaten toplam: yarım yayınlar da
   * gerçekten harcanmış süre.
   */
  /**
   * Şekil BİTMEDEN konuşma eşiği. Bitmeyi beklemek yetmiyor, çünkü bir şekil
   * hiç bitmeyebilir: şekli tamamlayan tek şey kalem kalkık taşınma
   * (`turtlePathMoveTo`) ya da boya değişimi (`realSetFillPaint`), ve betiğin
   * SON şekli çoğu zaman ikisini de görmeden bitiyor.
   * `ornekler/14-agir-dolgu.kojo`'nun 1000 noktalık gülü tam böyle -- yalnız
   * tamamlanmış şekle bakan bir uyarı, uyarılması gereken şekli susturuyordu.
   */
  private[kojo] val erkenÇarpan = 3.0

  private var sonNotZamanı = Double.NegativeInfinity
  private var notSayısı = 0

  /** Panele GERÇEKTEN kaç not düştü. Zaman kapısına takılanlar sayılmıyor. */
  private[kojo] def düşenNotSayısı: Int = notSayısı

  /** Yalnız sınamalar için: KÜRESEL durum (zaman kapısı ve sayaç). */
  private[kojo] def hepsiniUnut(): Unit = {
    sonNotZamanı = Double.NegativeInfinity
    notSayısı = 0
  }

  /**
   * Bir üçgenleme bitti: süresi ve nokta sayısı.
   *
   * Bütçeyi aşmadıysa hiçbir şey yapmıyor -- sıcak yolda tek bir
   * karşılaştırma.
   */
  private[kojo] def üçgenlemeBitti(
      birikim: ŞekilBirikimi, süreMs: Double, noktaSayısı: Int, bitti: Boolean): Unit = {
    birikim.toplamMs += süreMs
    val toplam = birikim.toplamMs
    val konuşulabilir = toplam > bütçeMs && (bitti || toplam > erkenÇarpan * bütçeMs)
    if (!birikim.bildirildi && konuşulabilir) {
      val şimdi = saat()
      if (şimdi - sonNotZamanı >= enAzAralıkMs) {
        sonNotZamanı = şimdi
        notSayısı += 1
        birikim.bildirildi = true
        paneleYaz(metin(toplam, noktaSayısı, bitti))
      }
    }
    if (bitti) birikim.unut()
  }

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
  private def paneleYaz(metin: String): Unit = {
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
