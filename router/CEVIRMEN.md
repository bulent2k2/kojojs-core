# router/ içindeki çevirmen (Koco ↔ Kojo)

`POST /cevir`, masaüstü Koco'nun betik çevirmenini (bulent2k2/kojo,
`lite/i18n/tr/cevirmen.scala`) iKojo'nun sunucusunda çalıştırır
(kojojs-dev#183). Derleyiciye dokunmaz: sözcük düzeyinde çevirir, istek başına
milisaniyeler sürer.

## Uç

```
POST /cevir?yon=tr2en|en2tr|oto     gövde: betik (UTF-8, en çok 64 KB)
→ 200 application/json
  { "yon": "tr2en", "kod": "<çevrilmiş betik>",
    "rapor": { "cevrilen": 12,
               "kalanlar": [ { "ad": "başlık", "sayi": 2 } ],
               "belirsiz": [ { "ad": "…", "secilen": "…", "digerleri": ["…"], "satir": 4 } ] },
    "kalanAnahtarSozcukler": [] }
→ 400 text/plain   geçersiz yön
→ 413              gövde 64 KB'tan büyük
```

- `yon` verilmezse ya da `oto` ise betikten bulunur: Türkçe anahtar sözcük
  (`dez`, `den`, `tanım`, `eğer`, …) varsa `tr2en`, yoksa `en2tr`.
- `kalanlar`: çevirmenin "Türkçe kaldı" dedikleri (Türkçe harf taşıyan ya da
  sözlükte bilinen adlar: kullanıcının kendi adları, sözlüğün eksikleri). ASCII
  bir kullanıcı adı çevrilmeden kalır ama listelenmez. Yalnız `tr2en`'de dolu.
  Satır numarası yok: çevirmen yalnız sayı tutuyor.
- `belirsiz`: birden çok karşılığı olan ve en sık görüleniyle çevrilen adlar;
  `satir` 1'den başlar.
- `kalanAnahtarSozcukler`: çıktıda hâlâ kaynak dilin anahtar sözcüğü varsa
  (çeviri eksik).
- Önbelleklenmez (`Cache-Control: no-cache`); CORS `/compile` ile aynı ayar.
- nginx: `koco-deploy/nginx.conf`'a `location = /cevir` eklenmedikçe dışarıdan
  erişilmez (bu depodaki değişiklik tek başına ucu yayımlamaz).

## Kopya dosyalar (elle düzenlenmez)

`router/` altındaki şu dosyalar `bulent2k2/kojo`'dan BAYT BAYT kopyadır;
sürümü `CEVIRMEN_SURUMU`'ndaki commit belirler:

| dosya |
|---|
| `src/main/scala/net/kogics/kojo/lite/i18n/tr/{cevirmen,cevirisozlugu,dict}.scala` |
| `src/main/resources/i18n/tr/{ceviri-sozlugu,ceviri-kurallar}.tsv` |
| `lib/scalariform.jar` (Türkçe anahtar sözcükleri bilen yamalı scalariform) |

Çevirmen masaüstünde geliştiriliyor ve orada sınanıyor (`CevirmenTest`,
`CevirmenDerlemeTest`, `CevirmenKitaplikTest`); burada düzeltilen bir kopya iki
çevirmen demek. CI `router/cevirmen-esitle.sh --denetle` ile kopyanın sabit
commit'le aynı olduğunu sınar.

Yükseltmek: `CEVIRMEN_SURUMU`'ndaki SHA'yı değiştir,
`router/cevirmen-esitle.sh --kojo ../kojo` ile kopyala, ikisini aynı commit'te
gönder.

`dict.scala` ve `cevirmen.scala` çevirmenin kitaplık sınırına dahil;
`SözlükÜreteci`, `CevirmenMain` ve `ÇeviriDoğrulama` bilerek dışarıda (masaüstü
kaynaklarını okuyorlar ya da derleyici kullanıyorlar), buraya kopyalanmaz.

## Tuzak: scala-xml

`scalariform.jar`'ın sözcükleyicisi çalışma zamanında `scala.xml.parsing.TokenTests`
ister. `scalatest` bunu Test sınıf yoluna getirdiği için birim sınaması geçiyor,
ama üretim sınıf yolunda `scala-xml` yoksa ilk istek `NoClassDefFoundError` verir
(gerçek sunucuda ölçüldü). `build.sbt`'de açıkça bağımlılık olarak duruyor. Birim
sınaması bunu YAKALAMAZ (kaldırılsa `CevirTest` yine yeşil), bu yüzden CI üretim
sınıf yolunu ayrıca denetler (`derleme.yml`, "üretim sınıf yolunda scala-xml var mı").

## Sınama

- `router/test` → `CevirTest`: yön bulma, geçersiz yön, JSON şekli, Türkçe karakterler.
- Elle uçtan uca: `router`'ı `SCALAFIDDLE_PORT=18880 SCALAFIDDLE_INTERFACE=127.0.0.1` ile
  başlatıp `curl -X POST --data-binary @betik.kojo localhost:18880/cevir`.
