#!/usr/bin/env bash
# Compila y firma la APK usando las herramientas Android disponibles en el sistema.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK:-/tmp/opencode/sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$SDK/android-14}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/android-12/android.jar}"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"
KEYSTORE="$ROOT/debug.keystore"

if [ ! -f "$ANDROID_JAR" ]; then
  echo "Falta android.jar: $ANDROID_JAR" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> Compilando recursos (aapt)"
aapt package -f -m \
  -J "$OUT/gen" \
  -M "$APP/AndroidManifest.xml" \
  -S "$APP/res" \
  -I "$ANDROID_JAR"

echo "==> Compilando Java"
javac -source 1.8 -target 1.8 -nowarn \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$APP/java" "$OUT/gen" -name '*.java')

echo "==> Generando DEX (d8)"
( cd "$OUT/classes" && jar cf "$OUT/classes.jar" . )
java -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 23 --lib "$ANDROID_JAR" \
  --output "$OUT/dex" "$OUT/classes.jar"

echo "==> Empaquetando APK"
aapt package -f \
  -M "$APP/AndroidManifest.xml" \
  -S "$APP/res" \
  -I "$ANDROID_JAR" \
  -F "$OUT/base.apk"
( cd "$OUT/dex" && zip -q -j "$OUT/base.apk" classes.dex )
zipalign -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "==> Creando keystore de depuración"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "==> Firmando"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$ROOT/EstadoDelTelefono.apk" "$OUT/aligned.apk"

echo "==> Verificando"
apksigner verify --print-certs "$ROOT/EstadoDelTelefono.apk" | head -4
aapt dump badging "$ROOT/EstadoDelTelefono.apk" | grep -E "^(package|application-label|launchable-activity|sdkVersion|targetSdkVersion)" | head -8
ls -lh "$ROOT/EstadoDelTelefono.apk"
