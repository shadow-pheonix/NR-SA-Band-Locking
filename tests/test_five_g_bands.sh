#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
jdk="${SA_JAVA_HOME:-${JAVA_HOME:-}}"
mkdir -p "$here/build/observation-tests"
sources=(
    FiveGBands NrFrequencyPlan RadioModeSelection PreferenceJournal BootPolicy BootReapply
)
tests=(FiveGBandsTest RadioModeSelectionTest PreferenceJournalTest BootReapplyTest)
inputs=()
for source in "${sources[@]}"; do inputs+=("$here/android/src/dev/saband/lab/$source.java"); done
for test in "${tests[@]}"; do inputs+=("$here/tests/$test.java"); done
"${jdk:+$jdk/bin/}javac" -source 8 -target 8 -Xlint:-options -d "$here/build/observation-tests" "${inputs[@]}"
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.FiveGBandsTest
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.RadioModeSelectionTest
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.PreferenceJournalTest
"${jdk:+$jdk/bin/}java" -cp "$here/build/observation-tests" dev.saband.lab.BootReapplyTest
