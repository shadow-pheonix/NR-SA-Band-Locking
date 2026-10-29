#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
sdk_tools="$sdk/build-tools/${SA_BUILD_TOOLS_VERSION:-35.0.0}"
jdk="${SA_JAVA_HOME:-${JAVA_HOME:-}}"
java_cmd="${jdk:+$jdk/bin/}java"
javac_cmd="${jdk:+$jdk/bin/}javac"
keytool_cmd="${jdk:+$jdk/bin/}keytool"
zig="${SA_ZIG:-zig}"

# SA_ANDROID_TOOLS retains support for an explicit directory of extracted jars.
if [[ -n "${SA_ANDROID_TOOLS:-}" ]]; then
    sdk_tools="$SA_ANDROID_TOOLS"
    android_jar="${SA_ANDROID_JAR:-$sdk_tools/android.jar}"
    d8_jar="$sdk_tools/d8.jar"
    signer_jar="$sdk_tools/apksigner.jar"
else
    android_jar="${SA_ANDROID_JAR:-$sdk/platforms/android-34/android.jar}"
    d8_jar="$sdk_tools/lib/d8.jar"
    signer_jar="$sdk_tools/lib/apksigner.jar"
fi
aapt="${SA_AAPT2:-$sdk_tools/aapt2}"
zipalign="${SA_ZIPALIGN:-$sdk_tools/zipalign}"
export ZIG_GLOBAL_CACHE_DIR="${SA_ZIG_CACHE:-$here/.cache/zig}"

for executable in gcc python3 "$java_cmd" "$javac_cmd" "$keytool_cmd" "$zig" "$aapt" "$zipalign"; do
    command -v "$executable" >/dev/null || { echo "Missing build tool: $executable. See docs/BUILDING.md." >&2; exit 1; }
done
for file in "$android_jar" "$d8_jar" "$signer_jar"; do
    [[ -f "$file" ]] || { echo "Missing SDK file: $file. See docs/BUILDING.md." >&2; exit 1; }
done
mkdir -p "$here/build" "$here/.signing" "$here/dist"
python3 - "$here" <<'PY'
from pathlib import Path
import shutil, sys
root = Path(sys.argv[1])
for name in ['classes', 'dex']:
    target = root/'build'/name
    if target.exists(): shutil.rmtree(target)
    target.mkdir()
PY
native=("$here/native/sa_helper.c" "$here/native/qmi_sa.c" "$here/native/vendor/qcom-efs/diag.c" "$here/native/vendor/qcom-efs/util.c")
gcc -std=gnu11 -O2 -Wall -Wextra -Werror -DSA_TESTING -o "$here/build/sa-helper-host" "${native[@]}"
"$zig" cc -target aarch64-linux-musl -static -O2 -s -fPIE -Wl,-z,max-page-size=16384 -Wall -Wextra -Werror -o "$here/build/sa-helper-arm64" "${native[@]}"
"$javac_cmd" -source 8 -target 8 -Xlint:-options -classpath "$android_jar" -d "$here/build/classes" "$here/android/src/dev/saband/lab/"*.java
python3 - "$here" <<'PY'
from pathlib import Path
import sys, zipfile
root = Path(sys.argv[1])
with zipfile.ZipFile(root/'build/classes.jar', 'w') as z:
    for p in sorted((root/'build/classes').rglob('*.class')):
        z.write(p, str(p.relative_to(root/'build/classes')))
PY
"$java_cmd" -cp "$d8_jar" com.android.tools.r8.D8 --min-api 30 --lib "$android_jar" --output "$here/build/dex" "$here/build/classes.jar"
"$aapt" compile --dir "$here/android/res" -o "$here/build/resources.zip"
"$aapt" link --manifest "$here/android/AndroidManifest.xml" -I "$android_jar" --min-sdk-version 30 --target-sdk-version 34 -o "$here/build/base.apk" "$here/build/resources.zip"
python3 "$here/package_apk.py"
LD_LIBRARY_PATH="$sdk_tools:$sdk_tools/lib64${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" "$zipalign" -f 4 "$here/build/unsigned.apk" "$here/build/aligned.apk"

keystore="${SA_KEYSTORE:-$here/.signing/debug.p12}"
export SA_KEYSTORE_PASS="${SA_KEYSTORE_PASS:-android}"
alias="${SA_KEY_ALIAS:-sa-lab}"
if [[ ! -f "$keystore" ]]; then
    [[ -z "${SA_KEYSTORE:-}" ]] || { echo "Explicit SA_KEYSTORE does not exist; refusing to replace your signing identity." >&2; exit 1; }
    "$keytool_cmd" -genkeypair -keystore "$keystore" -storetype PKCS12 -storepass:env SA_KEYSTORE_PASS -keypass:env SA_KEYSTORE_PASS -alias "$alias" -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=SA Band Lab Development"
fi
"$java_cmd" -jar "$signer_jar" sign --ks "$keystore" --ks-pass env:SA_KEYSTORE_PASS --ks-key-alias "$alias" --out "$here/dist/sa-band-lab-0.11-arm64.apk" "$here/build/aligned.apk"
"$java_cmd" -jar "$signer_jar" verify --verbose "$here/dist/sa-band-lab-0.11-arm64.apk"
cp "$here/build/sa-helper-arm64" "$here/dist/sa-helper-arm64"
python3 "$here/artifact_manifest.py"
