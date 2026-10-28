#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$here/build"
native=("$here/native/sa_helper.c" "$here/native/qmi_sa.c" "$here/native/vendor/qcom-efs/diag.c" "$here/native/vendor/qcom-efs/util.c")
gcc -std=gnu11 -O2 -Wall -Wextra -Werror -DSA_TESTING -o "$here/build/sa-helper-host" "${native[@]}"
gcc -std=gnu11 -O2 -Wall -Wextra -Werror -DSA_TESTING -Wl,--wrap=send -o "$here/build/sa-helper-qrtr-contract" "${native[@]}" "$here/tests/qrtr_send_contract.c"
gcc -std=gnu11 -O2 -Wall -Wextra -Werror -o "$here/build/test-transport" "$here/tests/test_transport.c" "$here/native/vendor/qcom-efs/util.c"
"$here/build/test-transport"
python3 "$here/tests/test_helper.py"
SA_QMI_HELPER_TEST_BINARY="$here/build/sa-helper-qrtr-contract" SA_QMI_CONTRACT_TEST=1 python3 "$here/tests/test_qmi_helper.py"
