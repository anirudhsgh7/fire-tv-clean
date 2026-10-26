#!/usr/bin/env bash
# Build Blackbox without Gradle: aapt2, javac, d8, zipalign, apksigner.
# Requires JDK 17 and Android SDK platform 22 + build-tools.
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z $SDK ]]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    [[ -f $d/platforms/android-22/android.jar ]] && { SDK=$d; break; }
  done
fi
[[ -n $SDK ]] || { echo "error: Android SDK not found; set ANDROID_HOME" >&2; exit 1; }

JAVA=${JAVA_HOME:-}
if [[ -z $JAVA || ! -x $JAVA/bin/javac ]]; then
  for d in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /usr/lib/jvm/java-17-openjdk; do
    [[ -x $d/bin/javac ]] && { JAVA=$d; break; }
  done
fi
[[ -n $JAVA && -x $JAVA/bin/javac ]] || { echo "error: JDK 17 not found; set JAVA_HOME" >&2; exit 1; }

JAR=$SDK/platforms/android-22/android.jar
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)
[[ -f $JAR ]] || { echo "error: $JAR missing" >&2; exit 1; }
[[ -x $BT/aapt2 ]] || { echo "error: no build-tools under $SDK" >&2; exit 1; }

KEYSTORE=${BLACKBOX_KEYSTORE:-$PWD/blackbox.keystore}
KEYPASS=${BLACKBOX_KEYPASS:-blackbox}
KEYALIAS=${BLACKBOX_KEYALIAS:-blackbox}
if [[ ! -f $KEYSTORE ]]; then
  "$JAVA/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias "$KEYALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=Blackbox" >/dev/null
  echo "created signing key $KEYSTORE (keep it: updates must use the same key)"
fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/blackbox.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version 22 --target-sdk-version 22 \
  --version-code 1 --version-name 0.1 \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JAVA/bin/javac" --release 8 -encoding UTF-8 \
  -classpath "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api 22 --lib "$JAR" --output "$WORK/dex" @"$WORK/classes.txt"
(cd "$WORK/dex" && zip -q -X "$WORK/app.apk" classes.dex)

"$BT/zipalign" -f 4 "$WORK/app.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEYALIAS" \
  --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out build/blackbox.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/blackbox.apk
printf 'built %s (%s bytes)\n' "$PWD/build/blackbox.apk" "$(wc -c < build/blackbox.apk | tr -d ' ')"
