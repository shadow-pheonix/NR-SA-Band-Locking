#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
jdk="${SA_JAVA_HOME:-${JAVA_HOME:-}}"
mkdir -p "$here/build/observation-tests"
"${jdk:+$jdk/bin/}javac" -source 8 -target 8 -Xlint:-options -d "$here/build/observation-tests" "$here/android/src/dev/saband/lab/FiveGBands.java" "$here/android/src/dev/saband/lab/NrFrequencyPlan.java" "$here/android/src/dev/saband/lab/RadioModeSelection.java" "$here/tests/FiveGBandsTest.java" "$here/tests/RadioModeSelectionTest.java"
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.FiveGBandsTest
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.RadioModeSelectionTest
