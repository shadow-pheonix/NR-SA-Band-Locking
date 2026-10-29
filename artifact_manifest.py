from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent
files = ['sa-band-lab-0.13-arm64.apk', 'sa-helper-arm64']
data = {'version': '0.13-sa-test', 'package': 'dev.saband.lab',
        'device_tested': False, 'abi': 'arm64-v8a', 'min_android_api': 30,
        'files': {name: {'sha256': hashlib.sha256((root/'dist'/name).read_bytes()).hexdigest(),
                         'bytes': (root/'dist'/name).stat().st_size} for name in files}}
(root/'dist/artifacts.json').write_text(json.dumps(data, indent=2)+'\n')
print(json.dumps(data, indent=2))
