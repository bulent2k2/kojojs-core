#!/bin/bash
# router/'daki Koco <-> Kojo çevirmen kopyasını bulent2k2/kojo'dan alır ya da denetler.
#
#   router/cevirmen-esitle.sh --kojo ../kojo            # kopyala (CEVIRMEN_SURUMU'ndaki commit'te olmalı)
#   router/cevirmen-esitle.sh --kojo ../kojo --denetle  # yalnız karşılaştır; fark varsa çıkış 1 (CI)
#
# Kopya listesi tek yerde, aşağıda. Neden bayt bayt kopya: çevirmen masaüstünde
# geliştiriliyor ve orada sınanıyor (CevirmenTest, CevirmenDerlemeTest,
# CevirmenKitaplikTest); burada elle düzeltilen bir kopya iki çevirmen demek.
# NOT: kabuk değişken adları ASCII olmalı.
set -eu
BURASI="$(cd "$(dirname "$0")" && pwd)"
KOJO=""; DENETLE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --kojo) KOJO="$2"; shift 2 ;;
    --denetle) DENETLE=1; shift ;;
    *) echo "kullanım: $0 --kojo <kojo klonu> [--denetle]" >&2; exit 2 ;;
  esac
done
[ -d "$KOJO/.git" ] || { echo "HATA: --kojo bir git klonu olmalı" >&2; exit 2; }

SHA="$(grep -v '^#' "$BURASI/CEVIRMEN_SURUMU" | grep -m1 -E '^[0-9a-f]{40}$' || true)"
[ -n "$SHA" ] || { echo "HATA: CEVIRMEN_SURUMU'nda 40 haneli SHA yok" >&2; exit 2; }

# kojo'daki yol : router'daki yol
LISTE="
src/main/scala/net/kogics/kojo/lite/i18n/tr/cevirmen.scala:src/main/scala/net/kogics/kojo/lite/i18n/tr/cevirmen.scala
src/main/scala/net/kogics/kojo/lite/i18n/tr/cevirisozlugu.scala:src/main/scala/net/kogics/kojo/lite/i18n/tr/cevirisozlugu.scala
src/main/scala/net/kogics/kojo/lite/i18n/tr/dict.scala:src/main/scala/net/kogics/kojo/lite/i18n/tr/dict.scala
src/main/resources/i18n/tr/ceviri-sozlugu.tsv:src/main/resources/i18n/tr/ceviri-sozlugu.tsv
src/main/resources/i18n/tr/ceviri-kurallar.tsv:src/main/resources/i18n/tr/ceviri-kurallar.tsv
lib/scalariform.jar:lib/scalariform.jar
"

fark=0
for cift in $LISTE; do
  kaynak="${cift%%:*}"; hedef="$BURASI/${cift##*:}"
  # Çalışma ağacı DEĞİL, sabit commit'in içeriği: klon başka bir dalda olsa da aynı sonuç
  if git -C "$KOJO" show "$SHA:$kaynak" > /tmp/cevirmen-esitle.$$ 2>/dev/null; then :; else
    echo "HATA: $SHA içinde $kaynak yok (klon o commit'i içeriyor mu? git fetch)" >&2; rm -f /tmp/cevirmen-esitle.$$; exit 2
  fi
  if [ "$DENETLE" = 1 ]; then
    if ! cmp -s /tmp/cevirmen-esitle.$$ "$hedef"; then echo "FARKLI: ${cift##*:}"; fark=1; fi
  else
    mkdir -p "$(dirname "$hedef")"; cp /tmp/cevirmen-esitle.$$ "$hedef"; echo "kopyalandı: ${cift##*:}"
  fi
done
rm -f /tmp/cevirmen-esitle.$$
if [ "$DENETLE" = 1 ]; then
  if [ "$fark" = 0 ]; then echo "router/ çevirmen kopyası kojo@${SHA:0:12} ile bayt bayt aynı."; else
    echo "HATA: kopya kojo@${SHA:0:12} ile ayrışmış. Elle düzeltmeyin; kojo'da düzeltip SHA'yı yükseltin." >&2; exit 1; fi
fi
