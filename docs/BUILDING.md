# Build from source

The supported build host is **Linux**, with Bash, GCC, Python 3, **JDK 17**,
Android SDK command-line tools, and **Zig 0.13.0**. No Gradle project or proprietary
Cellular Pro component is needed. Tools must be installed by the builder.

## Install dependencies

On Debian/Ubuntu, the host tools can be installed with:

```sh
sudo apt install build-essential python3 openjdk-17-jdk
```

Install the [Android SDK command-line tools](https://developer.android.com/studio#command-tools)
and [Zig 0.13.0](https://ziglang.org/download/0.13.0/). Set the SDK path and make
`sdkmanager` and `zig` available on PATH:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platforms;android-34" "build-tools;35.0.0"
sdkmanager --licenses
```

Use JDK 17 for this build. If multiple JDKs are installed, set `JAVA_HOME` or
`SA_JAVA_HOME` to the JDK 17 directory. Accept SDK licenses yourself as applicable.

## Run tests

From the repository root:

```sh
bash tests/run_tests.sh
bash tests/test_five_g_bands.sh
```

The native tests require GCC, Python and permission to open local Unix sockets.
The Java host tests require the JDK but do not require an Android phone or SDK.
They exercise the production protocol code with test-only transports; those hooks
are compiled out of the shipped ARM64 helper.

## Build the APK

```sh
bash build.sh
```

Outputs:

```text
dist/sa-band-lab-0.12-arm64.apk
dist/sa-helper-arm64
dist/artifacts.json
```

The script compiles the C helper for `aarch64-linux-musl` as a static PIE with
16 KiB load alignment, compiles Java, runs D8 and aapt2, packages licenses, aligns
the APK, signs it and verifies the signature. It compiles `android/res` with
AAPT2 and links those resources, including the adaptive/themed launcher icons,
into the base package. Minimum Android API is 30.

## Signing and upgrades

Without an explicit keystore, the first build creates a **local development key**
at `.signing/debug.p12`. Its default password is `android`. The key is ignored
by Git and must never be committed. Different builders get different keys.

To use an existing signing key:

```sh
export SA_KEYSTORE=/path/to/your/signing-key.p12
export SA_KEY_ALIAS=your-alias
read -r -s -p 'Keystore password: ' SA_KEYSTORE_PASS
echo
export SA_KEYSTORE_PASS
bash build.sh
```

An explicitly selected missing keystore causes a failure; the script will not
silently generate a replacement identity. This script expects the key password
to match the keystore password, as with its generated PKCS12 development key.

The official download uses the project's existing private development key; that
key is not in this repository. A newly generated self-build key **cannot update**
an official installation. Restore settings and export backups before switching
installations, or keep using the official release path. Backup import remains
bound to the device, firmware, baseband and signing-key-scoped Android ID.

## Overrides

| Variable | Purpose |
| --- | --- |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Android SDK root; default `$HOME/Android/Sdk` |
| `SA_BUILD_TOOLS_VERSION` | SDK Build Tools version; default `35.0.0` |
| `SA_JAVA_HOME` / `JAVA_HOME` | JDK root; otherwise use PATH |
| `SA_ZIG` | Zig executable; default `zig` |
| `SA_ZIG_CACHE` | Zig global cache; default `.cache/zig` |
| `SA_ANDROID_JAR` | Override the Android framework jar |
| `SA_AAPT2` / `SA_ZIPALIGN` | Override individual SDK executable paths |
| `SA_ANDROID_TOOLS` | Optional extracted-tool directory containing `android.jar`, `d8.jar`, `apksigner.jar`, `zipalign` and its runtime libraries |
| `SA_KEYSTORE`, `SA_KEY_ALIAS`, `SA_KEYSTORE_PASS` | Signing configuration |

The SDK layout is the default. The extracted-tool override exists for isolated
build environments; it does not download dependencies or publish credentials.

## Speed-test source checks

`tests/test_five_g_bands.sh` also runs the pure Java HTTP transfer/band-sampling
policy tests. It uses fake connections and consumes no network data.

Report/history formatting checks use a JVM `org.json` jar as a **test-only**
dependency (for example the upstream `org.json:json:20240303` artifact):

```sh
SA_ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar" \
SA_JSON_JAR=/path/to/json-20240303.jar \
bash tests/test_speed_reports.sh
```

The APK continues using Android's built-in `org.json`; no external JSON jar or
Cloudflare SDK is bundled. These checks do not build an APK or execute Android
views, connectivity services or AtomicFile storage.
