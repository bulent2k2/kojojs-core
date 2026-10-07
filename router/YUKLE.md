# router/ içindeki `#yükle` genişletmesi

Düzenleyiciye yazılan `// #yükle <yol>` (İngilizce Kojo'da `// #include`) satırı derlemeden
hemen önce router'da genişletilir. Düzenleyicideki metin değişmez; masaüstü Kojo'daki gibi
içe alınanlar betiğin BAŞINA eklenir, `#yükle` satırı yerinde `// #Yükle … -- içeriği
yukarıya alındı` olarak kalır. Kod: `OrnekYukleyici.scala` (sınaması
`OrnekYukleyiciTest`). Editörün `/ornek/<yol>` rotasındaki (kojojs-editor
`koco.OrnekYukleyici`) kuralların aynısı; orada dosya sunucuda açılıp editöre HAZIR
genişletilmiş metin olarak yüklenir, burada kullanıcı satırı kendi yazar.

## Kurallar

- **Yalnız `// $FiddleStart` ile `// $FiddleEnd` arası** taranır (editörün gönderdiği
  sarmalayıcı). İşaretsiz tam bir program olduğu gibi derlenir.
- **Ne içe alınabilir:** `KOCO_ORNEKLER` dizini altındaki `.kojo` / `.kojo.installed`
  betikleri (yansıtılmış masaüstü örnekleri). Tarayıcıda yerel dosya yok: `~/…` içe alınmaz.
  `KOCO_ORNEKLER` boşsa özellik KAPALI (yerel sbt koşusu, upstream davranışı).
- **Yol çözme:** `/samples/tr/x` mutlak yolları `masaustu/src/main/resources` altında
  (yedek: kökün kendisi), göreli yollar önce kökte aranır; uzantısız ada `.kojo` eklenir;
  aynı dosya iki kez alınmaz (döngü korunması); iç içe içe alınanlar o dosyanın başına gelir.
- **Güvenlik** (hedef artık kullanıcıdan geliyor): yalnız `.kojo`/`.kojo.installed`;
  `..`, mutlak yol ve sembolik bağlarla köke çıkılamaz (adayın GERÇEK yolu kök içinde olmalı);
  toplam içe alma 1 MB ile sınırlı.

## Kayıtlı yazılımcıklar (`/sf/<kimlik>/<sürüm>`)

`#yükle` hedefi kaydedilmiş bir yazılımcık bağlantısı da olabilir; tam adres gerekmez:

```
// #yükle /sf/rNLmJw9/2
// #yükle sf/rNLmJw9/2
// #yükle https://ikojo.fly.dev/sf/rNLmJw9/2
// #yükle /sf/rNLmJw9          (sürüm yoksa 0, editörün /sf/:id rotası gibi)
```

- Router kaydı editörün `/raw/<kimlik>/<sürüm>` ucundan (`SCALAFIDDLE_SOURCE_URL`) getirir ve yalnız
  `$FiddleStart`/`$FiddleEnd` arasını (kullanıcının kodunu) içe alır; sarmalayıcı, `$FiddleDependency` ve
  `$ScalaVersion` satırları girmez. İşaretsiz kaynak içe alınmaz (uyarı). Kayıttaki `#yükle` satırları da genişler.
- **Başka sunucunun adresi içe alınmaz** (uyarı): getirme hep bu sunucudan yapılır, yoksa aynı kimlik bizde
  varsa yanlış betik yüklenirdi. Kabul edilen adlar: bu sitenin genel adresi (`SCALAFIDDLE_URL`) ve `localhost`
  (kapı numarası yok sayılır). Yalnız yol her zaman geçerli.
- **Dışarıya istek açmaz:** URL = yapılandırılmış editör tabanı + doğrulanmış kimlik (7 harf/rakam) + sayı;
  yönlendirme izlenmez, 3 sn zaman aşımı, 256 KB sınırı, bir istekte en çok 8 kayıt.
- Getirme bloke edilir; `#yükle` içeren istekler ayrı bir havuzda (`OrnekYukleyici.ec`) çalışır, öbürleri
  hiç bekletilmez. Getirme editörde bir "gömülü erişim" kaydı bırakır (`/raw` ucu böyle).
- Hata satırları `[sf/<kimlik>/<sürüm>]` önekiyle `#yükle` satırına bağlanır.

## Satır eşlemesi

Derleyici genişletilmiş kaynağı derler; hata satırları yanıt ÖNBELLEĞE yazılmadan
düzenleyicinin satırlarına döndürülür (önbellek anahtarı genişletilmiş kaynak + eşleme,
yani içe alınan dosya değişirse eski yanıt gelmez):

- eklenen bloğun önündeki satırlar aynı, arkasındakiler eklenen satır kadar yukarı;
- içe alınan dosyadaki hata, ilk `#yükle` satırına `[dosya]` önekiyle bağlanır;
- içe alınamayan bir hedef için derleme BAŞARISIZSA aynı satıra `warning` konur
  (başarılı derlemede istemci açıklamaları göstermediği için eklenmez; neden yalnız `log`'da).

Bilinen sınır: derleyicinin ham `log` metnindeki `ScalaFiddle.scala:N` numaraları
genişletilmiş kaynağa göredir (istemci log'u satır eşlemesinde kullanmıyor).
