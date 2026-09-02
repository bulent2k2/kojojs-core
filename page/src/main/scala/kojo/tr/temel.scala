package kojo.tr

/**
 * Türkçe tür takma adları ve çekirdek değerler.
 *
 * Neden paket nesnesi değil de trait: prelude `import trTurtle._` yapıyor, yani
 * kullanıcının göreceği her şey TurkishTurtle'ın ÜYESİ olmalı. Paket nesnesindeki
 * takma adlar bu import'la gelmiyordu.
 *
 * Diğer tr trait'leri de bunu extend ediyor, böylece kendi içlerinde Sayı/İkil
 * gibi adları kullanabiliyorlar.
 *
 * ÖNEMLİ: `doğru`/`yanlış` masaüstünde kütüphane değeri DEĞİL; yamalı Scala
 * derleyicisinin (scala-tr) Türkçe anahtar kelimeleri. KojoJS standart Scala ile
 * derlendiği için burada `val` olarak sağlıyoruz.
 */
trait TemelTürler {
  type Nesne = Object
  type Birim = Unit
  type Her = Any
  type HerDeğer = AnyVal
  type HerGönder = AnyRef
  type Yok = Null
  type Hiç = Nothing

  type İkil = Boolean
  type Seçim = Boolean
  val doğru = true
  val yanlış = false

  type Lokma = Byte
  type Kısa = Short
  type Sayı = Int
  type Uzun = Long
  type İriSayı = BigInt
  type UfakKesir = Float
  type Kesir = Double
  type İriKesir = BigDecimal

  type Yazı = String

  type Diz[B] = collection.Seq[B]
  type Dizi[B] = Seq[B]
  type Dizin[A] = List[A]
  type SıralıDizi[A] = IndexedSeq[A]
  type Yineleyici[C] = Iterator[C]
  type Yinelenebilir[C] = Iterable[C]
  // 2.13'te IterableOnce; 2.12 karşılığı TraversableOnce
  type YinelenebilirBirKere[C] = TraversableOnce[C]

  type KuralDışı = Exception
  type ÇalışmaSırasıKuralDışı = RuntimeException
}
