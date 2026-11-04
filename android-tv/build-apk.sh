#!/usr/bin/env bash
# Builds the Android TV apk with the newest stable Media3 (ExoPlayer); falls back to a known-good version.
set -euo pipefail
cd "$(dirname "$0")"
npm install
npm install --no-save hls.js@latest
node ../scripts/copy-hls.js node_modules/hls.js/dist/hls.min.js ../renderer/hls.min.js

LATEST=$(curl -fsSL https://dl.google.com/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml | python3 -c "
import re,sys
v=re.findall(r'<version>([^<]+)</version>',sys.stdin.read())
v=[x for x in v if re.fullmatch(r'\d+\.\d+\.\d+',x)]
print(max(v,key=lambda s:tuple(map(int,s.split('.')))))") || LATEST=""
echo "Latest stable Media3: ${LATEST:-unknown}"

build() { # $1 = media3 version, $2 = compileSdk
  rm -rf android www && mkdir www && cp -r ../renderer/* www/ \
  && npx cap add android \
  && MEDIA3="$1" COMPILE_SDK="$2" node patch-android.js \
  && npx cap sync android \
  && (cd android && chmod +x gradlew && ./gradlew assembleDebug --no-daemon)
}

USED=""
if [ -n "$LATEST" ] && build "$LATEST" 35; then USED="$LATEST"
else
  echo "::warning::Build with Media3 ${LATEST:-latest} failed, falling back to 1.4.1"
  build 1.4.1 34
  USED="1.4.1"
fi
cp android/app/build/outputs/apk/debug/app-debug.apk MovieBox-TV.apk
echo "$USED" > media3-version.txt
echo "APK built with Media3 ExoPlayer $USED"
