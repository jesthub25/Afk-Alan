#!/usr/bin/env bash
# Gerçek bir Paper sunucusu açar, AfkAlan'ı (WorldEdit/FAWE ve PlaceholderAPI ile birlikte) yükler,
# eklentinin kendi testini (-Dafkalan.selftest) çalıştırır ve günlükte hata arar.
# Kullanım: smoke-test.sh <mc-sürümü | 1.21-latest> <AfkAlan.jar>
set -euo pipefail

MC="$1"
JAR="$(readlink -f "$2")"
UA="AfkAlan-CI/1.0 (https://github.com/jesthub25/Afk-Alan)"
PAPI_VERSION="2.11.6"

if [[ "$MC" == *-latest ]]; then
  FAMILY="${MC%-latest}"
  MC=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper" \
        | jq -r --arg f "$FAMILY" '.versions[$f] | map(select(test("^[0-9.]+$"))) | sort_by(split(".") | map(tonumber)) | last')
  echo "En güncel $FAMILY sürümü: $MC"
fi

DIR="${RUNNER_TEMP:-/tmp}/smoke-$MC"
rm -rf "$DIR"
mkdir -p "$DIR/plugins"
cd "$DIR"
echo "mc=$MC" >> "${GITHUB_OUTPUT:-/dev/null}"

# ------------------------------------------------------------------ Paper
paper_url() {
  local v="$1" url=""
  url=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$v/builds" 2>/dev/null \
        | jq -r 'max_by(.id) | .downloads["server:default"].url // empty' 2>/dev/null || true)
  if [[ -z "$url" ]]; then
    local b name
    b=$(curl -fsSL -A "$UA" "https://api.papermc.io/v2/projects/paper/versions/$v/builds" | jq -r '.builds | last | .build')
    name=$(curl -fsSL -A "$UA" "https://api.papermc.io/v2/projects/paper/versions/$v/builds/$b" | jq -r '.downloads.application.name')
    url="https://api.papermc.io/v2/projects/paper/versions/$v/builds/$b/downloads/$name"
  fi
  echo "$url"
}
URL=$(paper_url "$MC")
echo "Paper: $URL"
curl -fsSL -A "$UA" -o paper.jar "$URL"

# ------------------------------------------------------------------ WorldEdit / FAWE (Modrinth)
modrinth() {
  local project="$1" loader="$2"
  curl -fsSL -A "$UA" -G "https://api.modrinth.com/v2/project/$project/version" \
       --data-urlencode "loaders=[\"$loader\"]" \
       --data-urlencode "game_versions=[\"$MC\"]" 2>/dev/null \
    | jq -r '[.[] | select(.version_type=="release")] + . | .[0].files | (map(select(.primary)) + .)[0].url // empty' 2>/dev/null || true
}
WE_URL=""
for spec in "worldedit bukkit" "worldedit paper" "fastasyncworldedit paper" "fastasyncworldedit bukkit"; do
  set -- $spec
  WE_URL=$(modrinth "$1" "$2")
  if [[ -n "$WE_URL" ]]; then echo "WorldEdit: $1 ($2) $WE_URL"; break; fi
done
if [[ -n "$WE_URL" ]]; then
  curl -fsSL -A "$UA" -o plugins/worldedit.jar "$WE_URL" || { echo "::warning::WorldEdit indirilemedi"; rm -f plugins/worldedit.jar; }
else
  echo "::warning::$MC için Modrinth'te WorldEdit/FAWE yok, WorldEdit testi atlandı"
fi

# ------------------------------------------------------------------ PlaceholderAPI
if gh release download "$PAPI_VERSION" -R PlaceholderAPI/PlaceholderAPI -p "*.jar" -D plugins 2>/dev/null; then
  echo "PlaceholderAPI $PAPI_VERSION indirildi"
elif curl -fsSL -A "$UA" -o plugins/PlaceholderAPI.jar \
      "https://hangar.papermc.io/api/v1/projects/HelpChat/PlaceholderAPI/versions/$PAPI_VERSION/PAPER/download"; then
  echo "PlaceholderAPI (Hangar) indirildi"
else
  rm -f plugins/PlaceholderAPI.jar
  echo "::warning::PlaceholderAPI indirilemedi, PAPI testi atlandı"
fi

cp "$JAR" plugins/AfkAlan.jar
ls -la plugins

# ------------------------------------------------------------------ sunucu
echo "eula=true" > eula.txt
LEVEL="flat"
case "$MC" in 1.16*|1.17*|1.18*) LEVEL="flat" ;; *) LEVEL="minecraft\\:flat" ;; esac
cat > server.properties <<EOF
online-mode=false
level-type=$LEVEL
generate-structures=false
spawn-protection=0
view-distance=3
simulation-distance=3
max-players=2
server-port=25565
motd=AfkAlan CI
EOF

set +e
timeout 900 java -Xms1G -Xmx2G \
  -Dafkalan.selftest=true -Dafkalan.selftest.shutdown=true \
  -Dcom.mojang.eula.agree=true -DPaper.IgnoreJavaVersion=true -Dpaper.disablePluginRemapping=false \
  -jar paper.jar --nogui > server.log 2>&1 < <(sleep 1200)
CODE=$?
set -e
echo "Sunucu çıkış kodu: $CODE"

echo "----------------------------- AfkAlan satırları"
grep -nE "AfkAlan|afkalan" server.log || true
echo "-----------------------------"

FAIL=0
grep -q "AFKALAN-SELFTEST: OK" server.log || { echo "::error::$MC: AfkAlan selftest başarılı olmadı"; FAIL=1; }
if grep -nE "at com\.jestron\.afkalan" server.log; then echo "::error::$MC: AfkAlan kaynaklı istisna"; FAIL=1; fi
if grep -nE "(ERROR|SEVERE).*AfkAlan|AfkAlan.*(ERROR|SEVERE)|Could not load 'plugins/AfkAlan|Error occurred while (enabling|disabling) AfkAlan" server.log; then
  echo "::error::$MC: AfkAlan hata günlüğü"; FAIL=1
fi
if [[ -f plugins/worldedit.jar ]] && ! grep -q "bağlantısı hazır" server.log; then
  echo "::error::$MC: WorldEdit bağlantısı kurulamadı"; FAIL=1
fi
if [[ $FAIL -ne 0 ]]; then
  echo "----------------------------- server.log (son 300 satır)"
  tail -n 300 server.log
  exit 1
fi
echo "$MC: TAMAM"
