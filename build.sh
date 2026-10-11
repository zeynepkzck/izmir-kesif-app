#!/bin/bash
set -e
cd "$(dirname "$0")"
A=${TOOLS:-/home/claude/dl}; AAPT=$A/aapt2pkg/aapt2/bin/Linux/aapt2
VC=${VERSION_CODE:-1}; VN=${VERSION_NAME:-1.0}
rm -rf build; mkdir -p build/gen build/classes build/dex
$AAPT compile --dir res -o build/res.zip
$AAPT link -I $A/android-all.jar --manifest AndroidManifest.xml --java build/gen -o build/base.apk \
  --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN build/res.zip
javac -nowarn --release 8 -encoding UTF-8 -cp $A/android-all.jar -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true
test -f build/classes/app/izmirkesif/MainActivity.class
java -cp $A/dx.jar com.android.dx.command.Main --dex --min-sdk-version=26 --output=build/dex/classes.dex build/classes
python3 tools/addzip.py build/base.apk build/unsigned.apk classes.dex=build/dex/classes.dex
mkdir -p build/tools && javac -nowarn -cp $A/apksig.jar -d build/tools tools/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED -cp $A/apksig.jar:build/tools Sign keystore.p12 izmirkesif kesif build/unsigned.apk build/izmir-kesif.apk
javac -nowarn -cp $A/apksig.jar -d build/tools tools/Verify.java
java -cp $A/apksig.jar:build/tools Verify build/izmir-kesif.apk
