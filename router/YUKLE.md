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
