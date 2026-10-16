#!/usr/bin/env bash
# Downloads InTouch's two typefaces (open source, SIL Open Font License) from the Google Fonts
# GitHub repo into app/src/main/res/font/. Run before building; the GitHub build does it for you.
#   Josefin Sans  - headings, names, labels, numbers
#   Nunito Sans   - body text
set -euo pipefail

dir="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
mkdir -p "$dir"

auth=()
if [[ -n "${GITHUB_TOKEN:-}" ]]; then auth=(-H "Authorization: Bearer $GITHUB_TOKEN"); fi

# Finds the variable-font file in a family folder (file names include the font's axes).
download() {
  local family="$1" pattern="$2" out="$3"
  local url
  url=$(curl -fsSL "${auth[@]}" "https://api.github.com/repos/google/fonts/contents/ofl/$family" |
    python3 -c "import sys, json, re; print(next(f['download_url'] for f in json.load(sys.stdin) if re.fullmatch(r'$pattern', f['name'])))")
  curl -fsSL "$url" -o "$dir/$out"
  echo "Fetched $out from $url"
}

download josefinsans 'JosefinSans\[[^\]]*\]\.ttf' josefin_sans.ttf
download nunitosans 'NunitoSans\[[^\]]*\]\.ttf' nunito_sans.ttf
