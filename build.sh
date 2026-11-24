#!/bin/bash
export PATH="$HOME/jdk/bin:$PATH"
export JAVA_HOME="$HOME/jdk"
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

# SDK is pre-installed manually in $ANDROID_HOME (platforms/android-34 + build-tools/34.0.0)
test -f "$ANDROID_HOME/platforms/android-34/android.jar" || { echo "missing android.jar"; exit 1; }

BT="$ANDROID_HOME/build-tools/34.0.0"
PLAT="$ANDROID_HOME/platforms/android-34/android.jar"
mkdir -p build/gen build/obj build/apk build/dex
rm -rf build/gen/* build/obj/* build/apk/*

# 2. resources / manifest
"$BT/aapt2" compile --dir app/src/main/res -o build/res.zip 2>/dev/null || echo "no res, skip compile"
if [ -f build/res.zip ]; then RESARG="build/res.zip"; else RESARG=""; fi
"$BT/aapt2" link -o build/apk/app-unsigned.apk -I "$PLAT" --manifest app/src/main/AndroidManifest.xml --java build/gen -A app/src/main/assets $RESARG

# 3. compile java
find app/src/main/java build/gen -name "*.java" > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -classpath "$PLAT" -d build/obj @build/sources.txt

# 4. dex
"$BT/d8" --lib "$PLAT" --output build/dex $(find build/obj -name "*.class")
cp build/dex/classes.dex build/apk/
cd build/apk && zip -q app-unsigned.apk classes.dex && cd ../..

# 5. align + sign (debug keystore generated locally)
"$BT/zipalign" -f 4 build/apk/app-unsigned.apk build/apk/app-aligned.apk
if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -v -keystore build/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=BabyWeather,O=Kalo,C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out BabyWeather.apk build/apk/app-aligned.apk
"$BT/apksigner" verify --verbose BabyWeather.apk
ls -lh BabyWeather.apk
echo BUILD_OK
