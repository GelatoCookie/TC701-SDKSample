#!/usr/bin/env bash
# Build lifecycle.pdf from lifecycle.md using the RFID ECRT Application Note template
# (matches AppNoteSN-Date.pdf: green title banner, teal headings, gray code boxes).
#
# Requires: pandoc + a XeLaTeX engine (TeX Live) with Latin Modern + Menlo fonts.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="lifecycle.md"
OUT="lifecycle.pdf"
BODY="$(mktemp -t lifecycle-body).md"

# Render Mermaid diagrams fresh each build.
rm -rf docs/_mermaid

# The template supplies the title from metadata, so drop the leading H1 heading.
awk 'NR==1 && /^# /{next} {print}' "$SRC" > "$BODY"

pandoc "$BODY" \
  --metadata-file=docs/lifecycle-meta.yaml \
  --from=markdown \
  --columns=40 \
  --pdf-engine=xelatex \
  --template=docs/appnote-template.latex \
  --lua-filter=docs/mermaid-filter.lua \
  --syntax-highlighting=docs/zebra-appnote.theme \
  --toc --toc-depth=3 \
  -o "$OUT"

rm -f "$BODY"
echo "Built $OUT"
