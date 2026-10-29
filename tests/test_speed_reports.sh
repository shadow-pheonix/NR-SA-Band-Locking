#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
jdk="${SA_JAVA_HOME:-${JAVA_HOME:-}}"
: "${SA_ANDROID_JAR:?Set SA_ANDROID_JAR to SDK platform android.jar}"
: "${SA_JSON_JAR:?Set SA_JSON_JAR to a JVM org.json jar (test dependency only)}"
mkdir -p "$here/build/speed-report-tests"
"${jdk:+$jdk/bin/}javac" -source 8 -target 8 -Xlint:-options -classpath "$SA_JSON_JAR:$SA_ANDROID_JAR" -d "$here/build/speed-report-tests" \
    "$here/android/src/dev/saband/lab/SpeedTestEngine.java" "$here/android/src/dev/saband/lab/SpeedTestStore.java" "$here/tests/SpeedTestReportsTest.java"
"${jdk:+$jdk/bin/}java" -cp "$SA_JSON_JAR:$here/build/speed-report-tests:$SA_ANDROID_JAR" dev.saband.lab.SpeedTestReportsTest
