#!/usr/bin/env bash
# Downloads the OFL-licensed fonts Alal bundles into app/src/main/res/font/.
# Run from the repo root. Safe to re-run. Every download is optional: the app
# resolves fonts by name at runtime and falls back to system fonts if missing.
#
#   Literata (serif titles)        -> literata.ttf            (variable: opsz,wght)
#   Inter (UI / Latin body)        -> inter.ttf               (variable: opsz,wght)
#   Noto Sans Myanmar (Burmese)    -> notosansmyanmar_regular.ttf / _bold.ttf
#
# Pyidaungsu is not on Google Fonts. If you have the TTFs, copy them in as:
#   pyidaungsu_regular.ttf  and  pyidaungsu_bold.ttf
# and the app will pick them up automatically (it becomes the default Myanmar font).
set -uo pipefail

DEST="app/src/main/res/font"
mkdir -p "$DEST"
BASE="https://github.com/google/fonts/raw/main/ofl"

fetch() { # fetch <dest-file> <url>...
  local dest="$DEST/$1"; shift
  if [ -s "$dest" ]; then echo "  [skip] $dest exists"; return 0; fi
  for url in "$@"; do
    if curl -fsSL --retry 3 --max-time 60 -o "$dest.tmp" "$url"; then
      mv "$dest.tmp" "$dest"; echo "  [ok]   $dest"; return 0
    fi
  done
  rm -f "$dest.tmp"; echo "  [miss] $dest (will use system fallback)"; return 1
}

echo "Fetching fonts into $DEST ..."
fetch literata.ttf \
  "$BASE/literata/Literata%5Bopsz%2Cwght%5D.ttf"
fetch inter.ttf \
  "$BASE/inter/Inter%5Bopsz%2Cwght%5D.ttf"
fetch notosansmyanmar_regular.ttf \
  "https://github.com/googlefonts/noto-fonts/raw/main/unhinted/ttf/NotoSansMyanmar/NotoSansMyanmar-Regular.ttf" \
  "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansMyanmar/unhinted/ttf/NotoSansMyanmar-Regular.ttf" \
  "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansMyanmar/full/ttf/NotoSansMyanmar-Regular.ttf" \
  "https://github.com/notofonts/myanmar/raw/main/fonts/NotoSansMyanmar/unhinted/ttf/NotoSansMyanmar-Regular.ttf" \
  "$BASE/notosansmyanmar/NotoSansMyanmar-Regular.ttf" \
  "$BASE/notosansmyanmar/NotoSansMyanmar%5Bwdth%2Cwght%5D.ttf"
fetch notosansmyanmar_bold.ttf \
  "https://github.com/googlefonts/noto-fonts/raw/main/unhinted/ttf/NotoSansMyanmar/NotoSansMyanmar-Bold.ttf" \
  "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansMyanmar/unhinted/ttf/NotoSansMyanmar-Bold.ttf" \
  "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansMyanmar/full/ttf/NotoSansMyanmar-Bold.ttf" \
  "https://github.com/notofonts/myanmar/raw/main/fonts/NotoSansMyanmar/unhinted/ttf/NotoSansMyanmar-Bold.ttf" \
  "$BASE/notosansmyanmar/NotoSansMyanmar-Bold.ttf"

# Licenses (kept next to the fonts for the About screen / attribution).
curl -fsSL --max-time 30 -o "app/FONT_LICENSES_OFL.txt" "$BASE/inter/OFL.txt" 2>/dev/null || true
echo "Done."
exit 0
