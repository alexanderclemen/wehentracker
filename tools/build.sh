#!/bin/bash
# Baut wehentracker.apk ohne Gradle (aapt + javac + dx + apksigner).
set -euo pipefail

SRC="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$SRC/build"
SDK=/usr/lib/android-sdk
JAR=$SDK/platforms/android-23/android.jar
BT=$SDK/build-tools/debian
KS="${KEYSTORE:-$SRC/release.keystore}"

rm -rf "$OUT" && mkdir -p "$OUT/classes"

# 1. Ressourcen + Manifest -> unsigniertes APK
aapt package -f -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$JAR" \
  --min-sdk-version 23 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1 -F "$OUT/unsigned.apk"

# 2. Java -> Klassen -> dex (keine Lambdas im Code, dx kann kein Desugaring)
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" -d "$OUT/classes" \
  $(find "$SRC/src" -name '*.java')
"$BT/dx" --dex --min-sdk-version=23 --output="$OUT/classes.dex" "$OUT/classes"

# 3. dex ins APK
( cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null )

# 4. ausrichten + signieren
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass wehentracker -keypass wehentracker \
    -alias wehen -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Wehentracker" 2>/dev/null
fi
apksigner sign --ks "$KS" --ks-pass pass:wehentracker --key-pass pass:wehentracker \
  --out "$OUT/wehentracker.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/wehentracker.apk"
echo "OK: $OUT/wehentracker.apk"
