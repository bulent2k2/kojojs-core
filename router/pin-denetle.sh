#!/bin/bash
# router/CEVIRMEN_SURUMU (bu depo) ile kojojs-dev'in araclar/kojo-cevirmen-surumu.txt'i aynı çevirmen
# commit'ini gösteriyor mu.
#
#   router/pin-denetle.sh --dev <kojojs-dev klonu> --kojo <kojo klonu, geçmişiyle>
#
# NEDEN: çevirmen iki yerde SABİT bir kojo commit'ine bağlı -- editördeki Çevir düğmesi router'ın
# kopyasını, kojojs-dev'in ölçüm kapısı (cevir-derle) kendi pinini kullanıyor. İkisi birbirinden
# kopabiliyor ve kopunca hiçbir şey kırmızı yanmıyor: kojojs-dev düzeltilmiş çevirmeni ölçüp
# "geçti" derken canlı site eski kopyayla `Picture.eskizden` üretiyordu (kojojs-core#80).
#
# KARAR (pin'ler tek PR'da birlikte değişemiyor, iki depo):
#   aynı                    geçer
#   bu depo (router) GERİDE kırmızı: router'ı yükseltin (CEVIRMEN.md "Yükseltmek")
#   bu depo ÖNDE            geçer, UYARI: kojojs-dev de yükseltilmeli. Yükseltme PR'ı kendi kendini
#                           kırmızıya çevirmesin; öbür depodaki denetim karşı yönü kapatıyor
#   ayrışmış                kırmızı (biri master'dan, öbürü dalından)
# NOT: kabuk değişken adları ASCII olmalı.
set -eu
BURASI="$(cd "$(dirname "$0")" && pwd)"
DEV=""; KOJO=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dev) DEV="$2"; shift 2 ;;
    --kojo) KOJO="$2"; shift 2 ;;
    *) echo "kullanım: $0 --dev <kojojs-dev klonu> --kojo <kojo klonu>" >&2; exit 2 ;;
  esac
done
[ -d "$KOJO/.git" ] || { echo "HATA: --kojo bir git klonu olmalı" >&2; exit 2; }
sha_oku() { grep -v '^#' "$1" | grep -m1 -E '^[0-9a-f]{40}$' || true; }
BENIM="$(sha_oku "$BURASI/CEVIRMEN_SURUMU")"
ONUN="$(sha_oku "$DEV/araclar/kojo-cevirmen-surumu.txt")"
[ -n "$BENIM" ] || { echo "HATA: router/CEVIRMEN_SURUMU'nda 40 haneli SHA yok" >&2; exit 2; }
[ -n "$ONUN" ] || { echo "HATA: kojojs-dev/araclar/kojo-cevirmen-surumu.txt'de 40 haneli SHA yok" >&2; exit 2; }

if [ "$BENIM" = "$ONUN" ]; then
  echo "aynı: router ve kojojs-dev çevirmeni kojo@${BENIM:0:12}"; exit 0
fi
# Her iki commit de klonda olmalı (geçmişle birlikte)
for s in "$BENIM" "$ONUN"; do
  git -C "$KOJO" cat-file -e "$s^{commit}" 2>/dev/null || { echo "HATA: $s kojo klonunda yok (git fetch)" >&2; exit 2; }
done
if git -C "$KOJO" merge-base --is-ancestor "$BENIM" "$ONUN"; then
  echo "::error::router çevirmeni (kojo@${BENIM:0:12}) kojojs-dev'dekinden (kojo@${ONUN:0:12}) GERİDE."
  echo "Canlı Çevir düğmesi eski çevirmenle çalışır; kojojs-dev'in ölçtüğü ile sitenin yaptığı ayrışır."
  echo "Düzeltme: router/CEVIRMEN_SURUMU'nu ${ONUN} yapın, router/cevirmen-esitle.sh --kojo ../kojo koşun,"
  echo "ikisini aynı commit'te gönderin (bkz. router/CEVIRMEN.md)."
  exit 1
elif git -C "$KOJO" merge-base --is-ancestor "$ONUN" "$BENIM"; then
  echo "::warning::router çevirmeni (kojo@${BENIM:0:12}) kojojs-dev'dekinden (kojo@${ONUN:0:12}) ÖNDE. kojojs-dev de yükseltilmeli: araclar/kojo-cevirmen-surumu.txt + cevir-derle.py --guncelle."
  exit 0
else
  echo "::error::router (kojo@${BENIM:0:12}) ve kojojs-dev (kojo@${ONUN:0:12}) çevirmen commit'leri AYRIŞMIŞ (biri öbürünün atası değil)."
  exit 1
fi
