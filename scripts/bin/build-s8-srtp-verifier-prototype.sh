#!/usr/bin/env bash
# Isolated Android ARM64 SRTP attestation JNI build for pinned PJSIP 2.17.
# Intentionally excluded from product APK until license/runtime gates are cleared.
set -euo pipefail
umask 077
if [[ "${ALLOW_S8_SRTP_VERIFIER_BUILD:-0}" != 1 ]]; then
  echo "S8_SRTP_VERIFIER_HOLD=isolated_build_opt_in_required" >&2
  exit 2
fi

ndk="${ANDROID_NDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/28.2.13676358}"
base="${S8_NATIVE_ROOT:-$HOME/agent-workspace/third-party/ai-calls-s8}"
pj="${PJSIP_BUILD_DIR:-$base/pjproject-autotools-android-arm64}"
pinned="${PJSIP_SRC:-$base/pjproject-2.17}"
ssl="$base/openssl-android-arm64-3.5.9"
out="${S8_VERIFIER_BUILD_DIR:-$base/srtp-verifier-android-arm64}"
tool="$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin"
source_file="prototypes/s8-pjsua2/native/S8Pjsua2SrtpAudit.cpp"
native="$pj/pjsip-apps/src/swig/java/android/pjsua2/src/main/jniLibs/arm64-v8a/libpjsua2.so"
[[ -x "$tool/clang++" && -x "$tool/llvm-readelf" &&
   -s "$native" && -f "$source_file" && -d "$pj/pjsip/include" ]] || {
  echo "S8_SRTP_VERIFIER_HOLD=missing_native_toolchain_or_pjsua2" >&2
  exit 3
}
[[ "$(git -C "$pinned" describe --tags --exact-match)" == 2.17 ]] || {
  echo "S8_SRTP_VERIFIER_HOLD=unpinned_pjsip" >&2
  exit 3
}
[[ "$out" != "$pj" && "$out" != "$pj/"* ]] || {
  echo "S8_SRTP_VERIFIER_HOLD=build_overlaps_pjsip" >&2
  exit 3
}
mkdir -p "$out"
"$tool/clang++" -target aarch64-none-linux-android28 -std=c++17 \
  -fPIC -shared -O2 -DPJ_AUTOCONF=1 -DANDROID \
  -I"$pj/pjlib/include" -I"$pj/pjlib-util/include" \
  -I"$pj/pjnath/include" -I"$pj/pjmedia/include" \
  -I"$pj/pjsip/include" -I"$pj/third_party/include" \
  -I"$pj/third_party/srtp/include" -I"$ssl/include" \
  "$source_file" -L"$(dirname "$native")" -lpjsua2 \
  -Wl,--no-undefined -Wl,-z,relro,-z,now -Wl,-z,max-page-size=16384 \
  -o "$out/libs8srtpaudit.so"

"$tool/llvm-readelf" -h "$out/libs8srtpaudit.so" | grep -q 'Machine:.*AArch64' || {
  echo "S8_SRTP_VERIFIER_HOLD=wrong_arch" >&2; exit 4;
}
"$tool/llvm-readelf" -lW "$out/libs8srtpaudit.so" |
  awk '$1=="LOAD"{if ($NF!="0x4000") bad=1; count++} END{exit (bad || count==0)}' || {
  echo "S8_SRTP_VERIFIER_HOLD=not_16kb_page_aligned" >&2; exit 4;
}
"$tool/llvm-nm" -D --defined-only "$out/libs8srtpaudit.so" |
  grep -Fq Java_pl_michalmatu_aicalls_sip_S8Pjsua2SrtpAudit_nativeSrtpActive || {
  echo "S8_SRTP_VERIFIER_HOLD=jni_export_missing" >&2; exit 4;
}
"$tool/llvm-readelf" -dW "$out/libs8srtpaudit.so" | grep -Fq '[libpjsua2.so]' || {
  echo "S8_SRTP_VERIFIER_HOLD=native_pjsua2_dependency_missing" >&2; exit 4;
}
echo "S8_SRTP_VERIFIER_JNI_ARM64_LINK=PASS"
echo "S8_SRTP_VERIFIER_SHA256=$(shasum -a 256 "$out/libs8srtpaudit.so" | cut -d' ' -f1)"
echo "S8_SRTP_VERIFIER_ON_DEVICE_QA=OPEN"
echo "S8_PJSIP_LICENSE_GATE=OPEN"
echo "PRODUCT_APK_PACKAGED=false"
echo "REAL_CALL_PERFORMED=false"
