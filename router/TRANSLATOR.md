# The translator in router/ (Koco ↔ Kojo)

`POST /translate` runs the desktop Koco script translator (bulent2k2/kojo,
`lite/i18n/tr/translator.scala`) on iKojo's server (kojojs-dev#183). It does not touch the
compiler: it translates lexically and takes milliseconds per request.

## Endpoint

```
POST /translate?direction=tr2en|en2tr|auto     body: script (UTF-8, at most 64 KB)
→ 200 application/json
  { "direction": "tr2en", "code": "<translated script>",
    "report": { "translated": 12,
                "leftovers": [ { "name": "başlık", "count": 2 } ],
                "ambiguous": [ { "name": "…", "chosen": "…", "others": ["…"], "line": 4 } ] },
    "remainingKeywords": [] }
→ 400 text/plain   invalid direction
→ 413              body larger than 64 KB
```

- If `direction` is missing or `auto`, it is detected from the script: a Turkish keyword (`dez`,
  `den`, `tanım`, `eğer`, …) means `tr2en`, otherwise `en2tr`.
- `leftovers`: what the translator calls "still Turkish" (names with Turkish letters or names the
  dictionary knows: the user's own names, gaps in the dictionary). An ASCII user name is left
  untranslated but not listed. Filled for `tr2en` only. No line numbers: the translator keeps only counts.
- `ambiguous`: names with several possible translations, translated with the most frequent one;
  `line` starts at 1.
- `remainingKeywords`: the output still contains a keyword of the source language (translation incomplete).
- Not cached (`Cache-Control: no-cache`); CORS is the same as `/compile`.
- nginx: not reachable from outside until `koco-deploy/nginx.conf` gets a `location = /translate`
  (a change in this repo does not publish the endpoint by itself).

### `/cevir` (fork-only)

`POST /cevir?yon=…` is the same translation with Turkish JSON field names (`yon`, `kod`, `rapor`,
`kalanlar`, `belirsiz`, `kalanAnahtarSozcukler`). It exists only for the iKojo editor's "Çevir"
button until the editor moves to `/translate`; `Cevir.scala` is a thin adapter over `Translate.scala`
and holds no translation logic.

## Copied files (never edited by hand)

These files under `router/` are BYTE-FOR-BYTE copies from `bulent2k2/kojo`; the commit in
`TRANSLATOR_VERSION` decides the version:

| file |
|---|
| `src/main/scala/net/kogics/kojo/lite/i18n/tr/{translator,translationdictionary,dict}.scala` |
| `src/main/resources/i18n/tr/{translation-dictionary,translation-rules}.tsv` |
| `lib/scalariform.jar` (scalariform patched to know the Turkish keywords) |

The translator is developed and tested on the desktop (`TranslatorTest`, `TranslatorCompileTest`,
`TranslatorLibraryTest`); a copy fixed here would mean two translators. CI runs
`router/translator-sync.sh --check` to verify the copy equals the pinned commit.

### Upgrading

Change the SHA in `TRANSLATOR_VERSION`, copy with `router/translator-sync.sh --kojo ../kojo`,
and commit both together.

## The pin must not drift from kojojs-dev's

The translator is pinned to a kojo commit in two places: this `router/TRANSLATOR_VERSION` (the copy
the editor's button uses) and kojojs-dev's `araclar/kojo-cevirmen-surumu.txt` (the `cevir-derle`
measuring gate). When they drifted nothing turned red: kojojs-dev measured the fixed translator and
said "pass" while the live site produced `Picture.eskizden` with the old copy (#80).

`router/pin-denetle.sh` in CI compares the two pins in kojo's history:

| state | result |
|---|---|
| same | passes |
| router BEHIND | red: upgrade the router (see "Upgrading") |
| router AHEAD | passes + warning: kojojs-dev must be upgraded too |
| diverged (neither is the ancestor of the other) | red |

Two repos cannot change in one PR, so "ahead" is not red; otherwise the upgrade PR would block
itself. The opposite direction (kojojs-dev behind) is closed by `araclar/pin-denetle.py` in
kojojs-dev, so whichever side is left ahead turns the other repo's CI red and forces it to catch up.

`DictionaryGenerator`, `TranslatorMain` and `TranslationValidator` are deliberately outside the
library boundary (they read the desktop sources or use the compiler) and are not copied here.

## Trap: scala-xml

The lexer in `scalariform.jar` needs `scala.xml.parsing.TokenTests` at run time. `scalatest` brings
it onto the Test classpath, so the unit tests pass, but without `scala-xml` on the production
classpath the first request fails with `NoClassDefFoundError` (measured on a real server). It is an
explicit dependency in `build.sbt`. The unit tests do NOT catch this (remove it and `TranslateTest`
stays green), so CI checks the production classpath separately (`derleme.yml`, "üretim sınıf yolunda
scala-xml var mı").

## Testing

- `router/test` → `TranslateTest`: direction detection, invalid direction, JSON shape, Turkish
  characters; `CevirTest`: the fork-only adapter.
- By hand, end to end: start `router` with `SCALAFIDDLE_PORT=18880 SCALAFIDDLE_INTERFACE=127.0.0.1`,
  then `curl -X POST --data-binary @script.kojo 'localhost:18880/translate?direction=auto'`.
