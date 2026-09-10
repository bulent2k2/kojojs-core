package kojo.tr

import scala.collection.mutable.{Stack, Queue, PriorityQueue}

/**
 * Yığın (Stack), Kuyruk (Queue) ve Öncelik Sırası (PriorityQueue).
 *
 */
trait KuyrukYöntemleri extends TemelTürler with EşlemYöntemleri with DizimYöntemleri {
  type Yığın[T] = Stack[T]
  type Kuyruk[T] = Queue[T]
  type ÖncelikSırası[T] = PriorityQueue[T]

  object Yığın {
    // Yığın(1, 2, 3) = "1'i it, 2'yi it, 3'ü it", yani TEPEDE 3 olur.
    // Scala'nın kendi Stack(1, 2, 3)'ü tepeye 1'i koyar (gösterimi tepeden
    // başlıyor); masaüstü Koco baştan beri itme sırasını kullanıyordu ve
    // öğrenci sezgisine yakın olan o. İki depo bu davranışta eşitlendi.
    def apply[T](elems: T*): Yığın[T] = { val y = Stack.empty[T]; y.pushAll(elems); y }
    def boş[T]: Yığın[T] = Stack.empty[T]
    // Başka bir yığının kopyası, aynı sırada (tepe yine tepede)
    def doldur[T](y2: Yığın[T]): Yığın[T] = Stack.from(y2)
  }

  object Kuyruk {
    def apply[T](elems: T*): Kuyruk[T] = Queue(elems: _*)
    def boş[T]: Kuyruk[T] = Queue.empty[T]
  }

  object ÖncelikSırası {
    def apply[T](elems: T*)(implicit sıralama: Ordering[T]): ÖncelikSırası[T] =
      PriorityQueue(elems: _*)(sıralama)
    def boş[T](implicit sıralama: Ordering[T]): ÖncelikSırası[T] = PriorityQueue.empty[T](sıralama)
  }

  implicit class YığınMetotları[T](d: Yığın[T]) {
    def it(öge: T): Yığın[T] = d.push(öge)
    def çek(): T = d.pop()
    def koy(öge: T): Yığın[T] = d.push(öge) // it ile aynı (kitapçık adı)
    def al(): T = d.pop()          // çek ile aynı (kitapçık adı)
    def tepesi: T = d.top
    def boyu: Sayı = d.size
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    // masaüstündeki adlarla eşitleme (bkz. kojo/lite/i18n/tr/kuyruk.scala)
    def tepe: T = d.top            // tepesi ile aynı
    def koyHepsini(dizi: YinelenebilirBirKere[T]): Yığın[T] = d.pushAll(dizi)
    def itHepsini(dizi: YinelenebilirBirKere[T]): Yığın[T] = d.pushAll(dizi)
    def dizi: Dizi[T] = d.toSeq
    type Col = Yığın[T]
    type Belki[B] = Option[B]

    // --- yığını toplu boşaltma (Eylül 2026 turu) ---------------------------
    // Adlandırma kuralı: EYLEMLE başla -- çekHepsini, ekleAraya, çıkarSondan.
    def çekHepsini: Diz[T] = d.popAll()                    // tepeden dibe
    def alHepsini: Dizi[T] = d.removeAll()                 // çekHepsini ile aynı
    def alHepsiniTersten: Dizi[T] = d.removeAllReverse()   // dipten tepeye
    def çekDoğruKaldıkça(deneme: T => İkil): Diz[T] = d.popWhile(deneme)
    def çekBelki: Belki[T] = d.removeHeadOption()
    // Yığında "son" = DİP. çıkarSondan tepedekini değil, en alttakini alır.
    def çıkarSondan(): T = d.removeLast()
    def çıkarSondanBelki: Belki[T] = d.removeLastOption()
    def sondanÇıkar(): T = d.removeLast()                  // çıkarSondan takma adı
    def sondanÇıkarBelki: Belki[T] = d.removeLastOption()  // çıkarSondanBelki takma adı
    def çıkarSondanDoğruKaldıkça(deneme: T => İkil): Dizi[T] = d.removeLastWhile(deneme)
    def çıkarİlkUyanı(deneme: T => İkil): Belki[T] = d.removeFirst(deneme)

    // --- yerinde değiştirenler ---------------------------------------------
    def eleYerinde(deneme: T => İkil): Col = { d.filterInPlace(deneme); d }
    def işleYerinde(işlev: T => T): Col = { d.mapInPlace(işlev); d }
    def düzİşleYerinde(işlev: T => YinelenebilirBirKere[T]): Col = { d.flatMapInPlace(işlev); d }
    def sıralıYerinde(implicit sıralama: Ordering[T]): Col = { d.sortInPlace()(sıralama); d }
    def sıralaYerinde[B](iş: T => B)(implicit sıralama: Ordering[B]): Col = { d.sortInPlaceBy(iş)(sıralama); d }
    def sırayaSokYerinde(önce: (T, T) => İkil): Col = { d.sortInPlaceWith(önce); d }
    def alYerinde(kaçTane: Sayı): Col = { d.takeInPlace(kaçTane); d }
    def alSağdanYerinde(kaçTane: Sayı): Col = { d.takeRightInPlace(kaçTane); d }
    def alDoğruKaldıkçaYerinde(deneme: T => İkil): Col = { d.takeWhileInPlace(deneme); d }
    def düşürYerinde(kaçTane: Sayı): Col = { d.dropInPlace(kaçTane); d }
    def düşürSağdanYerinde(kaçTane: Sayı): Col = { d.dropRightInPlace(kaçTane); d }
    def düşürDoğruKaldıkçaYerinde(deneme: T => İkil): Col = { d.dropWhileInPlace(deneme); d }
    def dilimYerinde(nereden: Sayı, nereye: Sayı): Col = { d.sliceInPlace(nereden, nereye); d }
    def uzatYerinde(boy: Sayı, öge: T): Col = { d.padToInPlace(boy, öge); d }
    def yamaYerinde(nereden: Sayı, yenisi: YinelenebilirBirKere[T], kaçTane: Sayı): Col = {
      d.patchInPlace(nereden, yenisi, kaçTane); d
    }

    // --- konumla erişim (0 = TEPE) -----------------------------------------
    def güncelle(yeri: Sayı, öge: T): Birim = d.update(yeri, öge)
    def ekleAraya(yeri: Sayı, öge: T): Birim = d.insert(yeri, öge)
    def ekleArayaHepsini(yeri: Sayı, ögeler: YinelenebilirBirKere[T]): Birim = d.insertAll(yeri, ögeler)
    def çıkar(yeri: Sayı): T = d.remove(yeri)
    def ekleHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.addAll(ögeler); d }
    def çıkarHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.subtractAll(ögeler); d }
    def dizime[S >: T](implicit delil: scala.reflect.ClassTag[S]): Dizim[S] = new Dizim(d.toArray(delil))

  }

  implicit class KuyrukMetotları[T](d: Kuyruk[T]) {
    type Col = Kuyruk[T]
    type Eşlek[K, D] = collection.immutable.Map[K, D]
    type Belki[B] = Option[B]
    def ekle(öge: T): Kuyruk[T] = d += öge
    def çıkar(): T = d.dequeue()
    def baştanAl(): T = d.dequeue() // çıkar ile aynı (kitapçık adı)
    def başı: T = d.head
    def boyu: Sayı = d.size
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
  
    // --- uçlar, arama --------------------------------------------------
    def başıBelki: Belki[T] = d.headOption
    def sonuBelki: Belki[T] = d.lastOption
    def bul(deneme: T => İkil): Belki[T] = d.find(deneme)
    def bulSondan(deneme: T => İkil): Belki[T] = d.findLast(deneme)
    def nerede(deneme: T => İkil): Sayı = d.indexWhere(deneme)
    def nerede(deneme: T => İkil, başlamaNoktası: Sayı): Sayı = d.indexWhere(deneme, başlamaNoktası)
    def neredeSondan(deneme: T => İkil): Sayı = d.lastIndexWhere(deneme)
    def dilimSırası[S >: T](dilim: Diz[S]): Sayı = d.indexOfSlice(dilim)
    def dilimSırasıSondan[S >: T](dilim: Diz[S]): Sayı = d.lastIndexOfSlice(dilim)
    def sıralar: Range = d.indices
    def başındaMı[S >: T](dizi: Yinelenebilir[S]): İkil = d.startsWith(dizi)
    def sonundaMı[S >: T](dizi: Yinelenebilir[S]): İkil = d.endsWith(dizi)
    def karşılıklıMı[S](öbürü: Diz[S])(deneme: (T, S) => İkil): İkil = d.corresponds(öbürü)(deneme)

    // --- bölme, öbekleme -----------------------------------------------
    def böl(deneme: T => İkil): (Kuyruk[T], Kuyruk[T]) = d.partition(deneme)
    def bölİşle[A1, A2](işlev: T => Either[A1, A2]): (Kuyruk[A1], Kuyruk[A2]) = d.partitionMap(işlev)
    def bölDoğruKaldıkça(deneme: T => İkil): (Kuyruk[T], Kuyruk[T]) = d.span(deneme)
    def bölYerinden(yeri: Sayı): (Kuyruk[T], Kuyruk[T]) = d.splitAt(yeri)
    def öbekli(boy: Sayı): Yineleyici[Kuyruk[T]] = d.grouped(boy)
    def kayarÖbekli(boy: Sayı): Yineleyici[Kuyruk[T]] = d.sliding(boy)
    def kayarÖbekli(boy: Sayı, adım: Sayı): Yineleyici[Kuyruk[T]] = d.sliding(boy, adım)
    def öbekleİşle[K, B](anahtar: T => K)(değer: T => B): Eşlek[K, Kuyruk[B]] = d.groupMap(anahtar)(değer)
    def öbekleİşleİndirge[K, B](anahtar: T => K)(değer: T => B)(indirge: (B, B) => B): Eşlek[K, B] =
      d.groupMapReduce(anahtar)(değer)(indirge)
    def kombinasyonlar(ögeSayısı: Sayı): Yineleyici[Kuyruk[T]] = d.combinations(ögeSayısı)
    def permütasyonlar: Yineleyici[Kuyruk[T]] = d.permutations
    def kuyruklar: Yineleyici[Kuyruk[T]] = d.tails
    def önler: Yineleyici[Kuyruk[T]] = d.inits

    // --- katlama, indirgeme, tarama ------------------------------------
    def katla[S >: T](z: S)(işlev: (S, S) => S): S = d.fold(z)(işlev)
    def indirgeSoldan[S >: T](işlem: (S, T) => S): S = d.reduceLeft(işlem)
    def indirgeSağdan[S >: T](işlem: (T, S) => S): S = d.reduceRight(işlem)
    def indirgeBelki[S >: T](işlem: (S, S) => S): Belki[S] = d.reduceOption(işlem)
    def indirgeSoldanBelki[S >: T](işlem: (S, T) => S): Belki[S] = d.reduceLeftOption(işlem)
    def indirgeSağdanBelki[S >: T](işlem: (T, S) => S): Belki[S] = d.reduceRightOption(işlem)
    // tara: katla gibi, ama ara sonuçların HEPSİNİ verir
    def tara[S >: T](z: S)(işlev: (S, S) => S): Kuyruk[S] = d.scan(z)(işlev)
    def taraSoldan[B](z: B)(işlev: (B, T) => B): Kuyruk[B] = d.scanLeft(z)(işlev)
    def taraSağdan[B](z: B)(işlev: (T, B) => B): Kuyruk[B] = d.scanRight(z)(işlev)
    def enUfağıBelki[S >: T](implicit sıralama: math.Ordering[S]): Belki[T] = d.minOption(sıralama)
    def enUfağıBelki[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): Belki[T] = d.minByOption(iş)(karşılaştırma)
    def enİrisiBelki[S >: T](implicit sıralama: math.Ordering[S]): Belki[T] = d.maxOption(sıralama)
    def enİrisiBelki[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): Belki[T] = d.maxByOption(iş)(karşılaştırma)

    // --- ekleme, çıkarma -----------------------------------------------
    def sonunaEkle[S >: T](öge: S): Kuyruk[S] = d.appended(öge)
    def önüneEkle[S >: T](öge: S): Kuyruk[S] = d.prepended(öge)
    def sonunaEkleHepsini[S >: T](öbürü: YinelenebilirBirKere[S]): Kuyruk[S] = d.appendedAll(öbürü)
    def önüneEkleHepsini[S >: T](öbürü: YinelenebilirBirKere[S]): Kuyruk[S] = d.prependedAll(öbürü)
    def uzat[S >: T](boy: Sayı, öge: S): Kuyruk[S] = d.padTo(boy, öge)
    def yama[S >: T](nereden: Sayı, yenisi: YinelenebilirBirKere[S], kaçTane: Sayı): Kuyruk[S] =
      d.patch(nereden, yenisi, kaçTane)
    def fark[S >: T](öbürü: Diz[S]): Kuyruk[T] = d.diff(öbürü)
    def kesişim[S >: T](öbürü: Diz[S]): Kuyruk[T] = d.intersect(öbürü)
    def bileşim[S >: T](öbürü: Diz[S]): Kuyruk[S] = d.concat(öbürü)

    // --- seçme, düzleştirme, ikili işlemler ----------------------------
    def seçİşle[B](işlev: PartialFunction[T, B]): Kuyruk[B] = d.collect(işlev)
    def seçİşleİlk[B](işlev: PartialFunction[T, B]): Belki[B] = d.collectFirst(işlev)
    def düzleştir[B](implicit delil: T => YinelenebilirBirKere[B]): Kuyruk[B] = d.flatten(delil)
    def devrik[B](implicit delil: T => Yinelenebilir[B]): Kuyruk[Kuyruk[B]] = d.transpose(delil)
    def ikiliyiAç[A1, A2](implicit delil: T => (A1, A2)): (Kuyruk[A1], Kuyruk[A2]) = d.unzip(delil)
    def ikileHepsini[B, S >: T](öbürü: Yinelenebilir[B], buDolgu: S, oDolgu: B): Kuyruk[(S, B)] =
      d.zipAll(öbürü, buDolgu, oDolgu)
    def tersİşle[B](işlev: T => B): Kuyruk[B] = d.reverse.map(işlev)

    // --- YERİNDE değiştirenler -------------------------------------------
    // Kuyruğun KENDİSİNİ değiştirirler. Kuyruk mantığı: kuyruğaEkle sona
    // koyar, baştanÇıkar baştan alır (ilk giren ilk çıkar).
    def kuyruğaEkle(öge: T): Col = { d.enqueue(öge); d }
    def kuyruğaEkleHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.enqueueAll(ögeler); d }
    def baştanÇıkar(): T = d.dequeue()
    def baştanÇıkarBelki: Belki[T] = d.removeHeadOption()
    def baştanÇıkarDoğruKaldıkça(deneme: T => İkil): Diz[T] = d.removeHeadWhile(deneme)
    def baştanÇıkarKoşulla(deneme: T => İkil): Belki[T] = d.dequeueFirst(deneme)
    def baştanÇıkarHepsiniKoşulla(deneme: T => İkil): Diz[T] = d.dequeueWhile(deneme)
    // DİKKAT: dequeueWhile baştan başlar, ilk uymayanda durur; dequeueAll ise
    // kuyruğun HER yerinden uyanları toplar. Ad masaüstünden geliyor.
    def baştanAlHepsini(deneme: T => İkil): Dizi[T] = d.dequeueAll(deneme).toList
    def sondanÇıkar(): T = d.removeLast()
    def sondanÇıkarBelki: Belki[T] = d.removeLastOption()
    def ilki: T = d.front
    def eleYerinde(deneme: T => İkil): Col = { d.filterInPlace(deneme); d }
    def işleYerinde(işlev: T => T): Col = { d.mapInPlace(işlev); d }
    def sıralıYerinde(implicit sıralama: Ordering[T]): Col = { d.sortInPlace()(sıralama); d }
    def sıralaYerinde[B](iş: T => B)(implicit sıralama: Ordering[B]): Col = { d.sortInPlaceBy(iş)(sıralama); d }
    @deprecated("eylemle başlayan ada geçildi: ekleHepsini kullanın", "Eylül 2026")
    def hepsiniEkle(ögeler: YinelenebilirBirKere[T]): Col = ekleHepsini(ögeler)
    def ekleHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.addAll(ögeler); d }
    @deprecated("eylemle başlayan ada geçildi: ekleAraya kullanın", "Eylül 2026")
    def araEkle(yeri: Sayı, öge: T): Birim = ekleAraya(yeri, öge)
    def çıkar(yeri: Sayı): T = d.remove(yeri)
    def çıkarHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.subtractAll(ögeler); d }
    def boşalt(): Birim = d.clear()
    def sıralı(implicit sıralama: Ordering[T]): Diz[T] = d.sorted(sıralama)
    def sırala[B](iş: T => B)(implicit sıralama: Ordering[B]): Diz[T] = d.sortBy(iş)(sıralama)
    def sırayaSok(önce: (T, T) => İkil): Diz[T] = d.sortWith(önce)
    def içeriyorMu[S >: T](öge: S): İkil = d.contains(öge)
    def sırası[S >: T](öge: S): Sayı = d.indexOf(öge)
    def sırasıSondan[S >: T](öge: S): Sayı = d.lastIndexOf(öge)
    def yinelemesiz: Diz[T] = d.distinct
    def yinelemesizİşlevle[B](işlev: T => B): Diz[T] = d.distinctBy(işlev)
    // --- ArrayDeque'in yerinde değiştirenleri (Eylül 2026 turu) -----------
    // Adlandırma kuralı: EYLEMLE başla -- ekleAraya, alHepsini, çıkarSondan.
    def alYerinde(kaçTane: Sayı): Col = { d.takeInPlace(kaçTane); d }
    def alSağdanYerinde(kaçTane: Sayı): Col = { d.takeRightInPlace(kaçTane); d }
    def alDoğruKaldıkçaYerinde(deneme: T => İkil): Col = { d.takeWhileInPlace(deneme); d }
    def düşürYerinde(kaçTane: Sayı): Col = { d.dropInPlace(kaçTane); d }
    def düşürSağdanYerinde(kaçTane: Sayı): Col = { d.dropRightInPlace(kaçTane); d }
    def düşürDoğruKaldıkçaYerinde(deneme: T => İkil): Col = { d.dropWhileInPlace(deneme); d }
    def dilimYerinde(nereden: Sayı, nereye: Sayı): Col = { d.sliceInPlace(nereden, nereye); d }
    def uzatYerinde(boy: Sayı, öge: T): Col = { d.padToInPlace(boy, öge); d }
    def yamaYerinde(nereden: Sayı, yenisi: YinelenebilirBirKere[T], kaçTane: Sayı): Col = {
      d.patchInPlace(nereden, yenisi, kaçTane); d
    }
    def düzİşleYerinde(işlev: T => YinelenebilirBirKere[T]): Col = { d.flatMapInPlace(işlev); d }
    def sırayaSokYerinde(önce: (T, T) => İkil): Col = { d.sortInPlaceWith(önce); d }

    // --- konumla erişim, toplu çıkarma ------------------------------------
    def güncelle(yeri: Sayı, öge: T): Birim = d.update(yeri, öge)
    def ekleAraya(yeri: Sayı, öge: T): Birim = d.insert(yeri, öge)
    def ekleArayaHepsini(yeri: Sayı, ögeler: YinelenebilirBirKere[T]): Birim = d.insertAll(yeri, ögeler)
    def alHepsini: Dizi[T] = d.removeAll()
    def alHepsiniTersten: Dizi[T] = d.removeAllReverse()
    def çıkarİlkUyanı(deneme: T => İkil): Belki[T] = d.removeFirst(deneme)
    def çıkarSondanDoğruKaldıkça(deneme: T => İkil): Dizi[T] = d.removeLastWhile(deneme)
    // çıkarSondan/çıkarSondanBelki: sondanÇıkar ailesinin eylemle başlayan biçimi
    def çıkarSondan(): T = d.removeLast()
    def çıkarSondanBelki: Belki[T] = d.removeLastOption()
    def dizime[S >: T](implicit delil: scala.reflect.ClassTag[S]): Dizim[S] = new Dizim(d.toArray(delil))

}

  implicit class ÖncelikSırasıMetotları[T](d: ÖncelikSırası[T]) {
    type Col = ÖncelikSırası[T]
    type C2[B] = ÖncelikSırası[B]
    type Belki[B] = Option[B]
    type Eşlek[K, D] = collection.immutable.Map[K, D]
    type Iter[A] = collection.mutable.Iterable[A]
    def ekle(öge: T): ÖncelikSırası[T] = d.addOne(öge) // `d += öge` ile aynı
    def çıkar(): T = d.dequeue()
    def başı: T = d.head
    def boyu: Sayı = d.length
    def tane: Sayı = d.size // boyu ile aynı (kitapçık adı)
    def boşMu: İkil = d.isEmpty
    def doluMu: İkil = d.nonEmpty
    def sil(): Birim = d.clear()
    def dizine: Dizin[T] = d.toList
  
    // --- ortak çekirdek ---------------------------------------------------
    // NOT: Koleksiyon ÜRETEN yöntemler Dizi veriyor, ÖncelikSırası değil.
    // Sebep: yeni bir öncelik sırası kurmak örtük bir Ordering ister ve
    // imzaları gereksiz karmaşıklaştırırdı. Sıralı bir sonuç gerekiyorsa
    // ÖncelikSırası(...) ile açıkça yeniden kurulur.
    def başıBelki: Belki[T] = d.headOption
    def sonuBelki: Belki[T] = d.lastOption
    def bul(deneme: T => İkil): Belki[T] = d.find(deneme)
    def böl(deneme: T => İkil): (Dizi[T], Dizi[T]) = {
      val (e, h) = d.iterator.toSeq.partition(deneme); (e, h)
    }
    def bölDoğruKaldıkça(deneme: T => İkil): (Dizi[T], Dizi[T]) = d.iterator.toSeq.span(deneme)
    def bölYerinden(yeri: Sayı): (Dizi[T], Dizi[T]) = d.iterator.toSeq.splitAt(yeri)
    def öbekli(boy: Sayı): Yineleyici[Dizi[T]] = d.iterator.toSeq.grouped(boy)
    def kayarÖbekli(boy: Sayı): Yineleyici[Dizi[T]] = d.iterator.toSeq.sliding(boy)
    def öbekleİşle[K, B](anahtar: T => K)(değer: T => B): Eşlek[K, Dizi[B]] =
      d.iterator.toSeq.groupMap(anahtar)(değer)
    def öbekleİşleİndirge[K, B](anahtar: T => K)(değer: T => B)(indirge: (B, B) => B): Eşlek[K, B] =
      d.iterator.toSeq.groupMapReduce(anahtar)(değer)(indirge)
    def kuyruklar: Yineleyici[Dizi[T]] = d.iterator.toSeq.tails
    def önler: Yineleyici[Dizi[T]] = d.iterator.toSeq.inits
    def katla[S >: T](z: S)(işlev: (S, S) => S): S = d.fold(z)(işlev)
    def indirgeSoldan[S >: T](işlem: (S, T) => S): S = d.reduceLeft(işlem)
    def indirgeSağdan[S >: T](işlem: (T, S) => S): S = d.reduceRight(işlem)
    def indirgeBelki[S >: T](işlem: (S, S) => S): Belki[S] = d.reduceOption(işlem)
    def indirgeSoldanBelki[S >: T](işlem: (S, T) => S): Belki[S] = d.reduceLeftOption(işlem)
    def indirgeSağdanBelki[S >: T](işlem: (T, S) => S): Belki[S] = d.reduceRightOption(işlem)
    def tara[S >: T](z: S)(işlev: (S, S) => S): Dizi[S] = d.iterator.toSeq.scan(z)(işlev)
    def taraSoldan[B](z: B)(işlev: (B, T) => B): Dizi[B] = d.iterator.toSeq.scanLeft(z)(işlev)
    def taraSağdan[B](z: B)(işlev: (T, B) => B): Dizi[B] = d.iterator.toSeq.scanRight(z)(işlev)
    def enUfağıBelki[S >: T](implicit sıralama: math.Ordering[S]): Belki[T] = d.minOption(sıralama)
    def enUfağıBelki[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): Belki[T] = d.minByOption(iş)(karşılaştırma)
    def enİrisiBelki[S >: T](implicit sıralama: math.Ordering[S]): Belki[T] = d.maxOption(sıralama)
    def enİrisiBelki[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): Belki[T] = d.maxByOption(iş)(karşılaştırma)
    def seçİşle[B](işlev: PartialFunction[T, B]): Dizi[B] = d.iterator.toSeq.collect(işlev)
    def seçİşleİlk[B](işlev: PartialFunction[T, B]): Belki[B] = d.collectFirst(işlev)
    def düzleştir[B](implicit delil: T => YinelenebilirBirKere[B]): Dizi[B] = d.iterator.toSeq.flatten(delil)
    def devrik[B](implicit delil: T => Yinelenebilir[B]): Dizi[Dizi[B]] = d.iterator.toSeq.transpose(delil)
    def ikiliyiAç[A1, A2](implicit delil: T => (A1, A2)): (Dizi[A1], Dizi[A2]) = d.iterator.toSeq.unzip(delil)
    def ikileHepsini[B, S >: T](öbürü: Yinelenebilir[B], buDolgu: S, oDolgu: B): Dizi[(S, B)] =
      d.iterator.toSeq.zipAll(öbürü, buDolgu, oDolgu)

    // --- YERİNDE değiştirenler -------------------------------------------
    def işleYerinde(işlev: T => T): Col = { d.mapInPlace(işlev); d }
    def ekleHepsini(ögeler: YinelenebilirBirKere[T]): Col = { d.addAll(ögeler); d }
    @deprecated("eylemle başlayan ada geçildi: ekleHepsini kullanın", "Eylül 2026")
    def hepsiniEkle(ögeler: YinelenebilirBirKere[T]): Col = ekleHepsini(ögeler)
    def kuyruğa: Kuyruk[T] = d.toQueue

    // --- masaüstü Koco ile eşitleme (bkz. lite/i18n/tr/kuyruk.scala) ------
    // Masaüstündeki mutPriQueMethods'ta baştan beri olan, ikojo'da eksik olanlar.
    def ekle(ögeler: T*) = d.enqueue(ögeler: _*)
    def baştanAl(): T = d.dequeue()
    def baştanAlHepsini[T2 >: T]: Dizi[T2] = d.dequeueAll.toList // ArraySeq veriyordu: çıktıda DizikDizisi görünüyordu
    def ikizle(): Col = d.clone()
    def kuyruğu: Col = d.tail
    def önü: Col = d.init
    def sonu: T = d.last
    def ele(deneme: T => İkil): Col = d.filter(deneme)
    def eleDeğilse(deneme: T => İkil): Col = d.filterNot(deneme)
    def işle[A](işlev: T => A): Iter[A] = d.map(işlev)
    def düzİşle[A](işlev: T => C2[A]): Iter[A] = d.flatMap(işlev)
    def indirge[B >: T](işlem: (B, B) => B): B = d.reduce(işlem)
    def soldanKatla[T2](z: T2)(işlev: (T2, T) => T2): T2 = d.foldLeft(z)(işlev)
    def sağdanKatla[T2](z: T2)(işlev: (T, T2) => T2): T2 = d.foldRight(z)(işlev)
    def topla[T2 >: T](implicit num: scala.math.Numeric[T2]) = d.sum(num)
    def çarp[T2 >: T](implicit num: scala.math.Numeric[T2]) = d.product(num)
    def yazıYap: Yazı = d.mkString
    def yazıYap(ara: Yazı): Yazı = d.mkString(ara)
    def yazıYap(başı: Yazı, ara: Yazı, sonu: Yazı): Yazı = d.mkString(başı, ara, sonu)
    def tersi = d.reverse
    def herbiriİçin[S](işlev: T => S): Birim = d.foreach(işlev)
    def varMı(deneme: T => İkil): İkil = d.exists(deneme)
    def hepsiDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def hepsiİçinDoğruMu(deneme: T => İkil): İkil = d.forall(deneme)
    def al(n: Sayı): Col = d.take(n)
    def alDoğruKaldıkça(deneme: T => İkil): Col = d.takeWhile(deneme)
    def alSağdan(n: Sayı): Col = d.takeRight(n)
    def düşür(n: Sayı): Col = d.drop(n)
    def düşürDoğruKaldıkça(deneme: T => İkil): Col = d.dropWhile(deneme)
    def düşürSağdan(n: Sayı): Col = d.dropRight(n)
    def diziye = d.toSeq
    def kümeye = d.toSet
    def yöneye = d.toVector
    def dizime[S >: T](implicit delil: scala.reflect.ClassTag[S]): Dizim[S] = new Dizim(d.toArray(delil))
    def eşleğe[A, D](implicit delil: T <:< (A, D)): Eşlek[A, D] = d.toMap
    def eşleme[A, D](implicit delil: T <:< (A, D)): Eşlem[A, D] = Eşlem.değişmezden(d.toMap)
    def say(işlev: T => İkil): Sayı = d.count(işlev)
    def dilim(nereden: Sayı, nereye: Sayı) = d.slice(nereden, nereye)
    def ikile[S](öbürü: YinelenebilirBirKere[S]) = d.zip(öbürü)
    def ikileSırayla = d.zipWithIndex
    def ikileKonumla = d.zipWithIndex
    def öbekle[A](iş: T => A): Eşlek[A, Col] = d.groupBy(iş)
    def enUfağı[B >: T](implicit sıralama: math.Ordering[B]): T = d.min(sıralama)
    def enUfağı[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): T = d.minBy(iş)(karşılaştırma)
    def enİrisi[B >: T](implicit sıralama: math.Ordering[B]): T = d.max(sıralama)
    def enİrisi[B](iş: T => B)(implicit karşılaştırma: math.Ordering[B]): T = d.maxBy(iş)(karşılaştırma)
}
}
