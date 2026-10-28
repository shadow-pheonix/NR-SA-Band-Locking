# Third-party code and protocol references

The original application, narrow QMI client, tests and build scripts are MIT
licensed; see [LICENSE](LICENSE).

## Bundled code

`native/vendor/qcom-efs/{diag.c,diag.h,util.c,util.h}` originate from
[catxeon/qcom-efs-browser](https://github.com/catxeon/qcom-efs-browser), commit
`9fde4f92a0cc49fc645cb70d19419f553108cb53`, under BSD-3-Clause.
The upstream [LICENSE](native/vendor/qcom-efs/LICENSE),
[NOTICE](native/vendor/qcom-efs/NOTICE), original file hashes in
[ORIGIN.json](native/vendor/qcom-efs/ORIGIN.json), and our modification ledger in
[PATCHES.md](native/vendor/qcom-efs/PATCHES.md) are retained.
The APK includes these notices in its assets.

## Protocol references

The QMI implementation uses protocol facts researched from the following primary
sources. Their implementation code is not included or linked:

- [libqmi NAS message definitions](https://github.com/linux-mobile-broadband/libqmi/blob/main/data/qmi-service-nas.json)
- [libqmi DMS message definitions](https://github.com/linux-mobile-broadband/libqmi/blob/main/data/qmi-service-dms.json)
- [libqmi RAT mode flags](https://github.com/linux-mobile-broadband/libqmi/blob/main/src/libqmi-glib/qmi-enums-nas.h)
- [qcom-band-menu](https://github.com/Fronsipswu/qcom-band-menu), including its
  subscription binding convention and experimental 0x00d2 frequency-list decoder
- [Linux QRTR transport](https://github.com/torvalds/linux/blob/master/net/qrtr/af_qrtr.c)
- [AOSP NR frequency mapping](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/telephony/java/android/telephony/AccessNetworkUtils.java)

Cellular Pro analysis informed an earlier EFS experiment. No Cellular Pro APK,
daemon, library, decompiled source or other proprietary component is distributed
in this repository or application.

## Build tools

Android SDK tools, a Java JDK and Zig are external build dependencies and are not
redistributed in this repository. The helper is statically linked using Zig's
musl target; musl's license is available in [LICENSES/musl.txt](LICENSES/musl.txt).
Zig/compiler-runtime notices are also retained in [LICENSES/zig.txt](LICENSES/zig.txt).
The Android framework is supplied by the device, not bundled with the app.
