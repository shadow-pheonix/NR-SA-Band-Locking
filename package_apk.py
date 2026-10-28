"""Package only our code and the licensed transport; no proprietary APK files."""
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parent
output = root / 'build/unsigned.apk'
shutil.copyfile(root / 'build/base.apk', output)
with zipfile.ZipFile(output, 'a', compression=zipfile.ZIP_DEFLATED) as z:
    z.write(root / 'build/dex/classes.dex', 'classes.dex')
    z.write(root / 'build/sa-helper-arm64', 'lib/arm64-v8a/libsahelper.so')
    for name in ['LICENSE', 'NOTICE', 'ORIGIN.json', 'PATCHES.md']:
        z.write(root / 'native/vendor/qcom-efs' / name, 'assets/transport-' + name)
    for name in ['README.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md']:
        z.write(root / name, 'assets/' + name)
    for p in sorted((root / 'LICENSES').glob('*.txt')):
        z.write(p, 'assets/licenses/' + p.name)
