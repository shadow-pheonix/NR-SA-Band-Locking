#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
jdk="${SA_JAVA_HOME:-${JAVA_HOME:-}}"
mkdir -p "$here/build/speed-http-tests"
"${jdk:+$jdk/bin/}javac" -source 8 -target 8 -Xlint:-options -d "$here/build/speed-http-tests" \
    "$here/android/src/dev/saband/lab/SpeedTestEngine.java" "$here/tests/SpeedTestHttpTest.java"
"${jdk:+$jdk/bin/}java" --add-modules jdk.httpserver -cp "$here/build/speed-http-tests" dev.saband.lab.SpeedTestHttpTest
