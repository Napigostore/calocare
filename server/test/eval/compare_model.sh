#!/usr/bin/env bash
# Measures another model on the test photos, then puts the server back on its normal model.
#   bash test/eval/compare_model.sh claude-sonnet-5-5 images-768      (from the server folder)
#
# While it runs, the live server answers every photo with that model: use it before launch or at a quiet hour.
MODEL="$1"; FOLDER="${2:-images}"
[ -z "$MODEL" ] && { echo "usage: bash test/eval/compare_model.sh <model id> [image folder]"; exit 1; }
URL=$(grep '^url=' ../backend.properties | cut -d= -f2-)
KEY=$(grep '^appKey=' ../backend.properties | cut -d= -f2-)

# Whatever happens to the run, the server must end up back on the model in wrangler.toml.
restore() { npx wrangler deploy 2>&1 | grep -iE "Deployed|rror" | head -2; echo "server restored to the model in wrangler.toml"; }
trap restore EXIT

npx wrangler deploy --var "MODEL:$MODEL" 2>&1 | grep -iE "Deployed|rror" | head -2
sleep 5
node test/eval/eval.mjs "$URL" "$KEY" "$FOLDER"
cp "test/eval/results-$FOLDER.json" "test/eval/results-$FOLDER-$MODEL.json"
