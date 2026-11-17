#!/usr/bin/env bash
# S8 experimental SIP/SDP/SRTP SDK build: NEVER packages native GPL code into app.
# Usage: ALLOW_S8_ISOLATED_PJSIP_BUILD=1 bash scripts/bin/build-s8-pjsip-prototype.sh
set -euo pipefail

if [[ "${ALLOW_S8_ISOLATED_PJSIP_BUILD:-0}" != 1 ]]; then
  echo "S8_PJSIP_BUILD_HOLD=isolated_prototype_opt_in_required" >&2
  exit 2
fi

root="${PJSIP_SRC:-$HOME/agent-workspace/third-party/ai-calls-s8/pjproject-2.17}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ndk="${ANDROID_NDK_ROOT:-$sdk/ndk/28.2.13676358}"
out="${PJSIP_BUILD_DIR:-$HOME/agent-workspace/third-party/ai-calls-s8/pjproject-build-android-arm64}"
[[ "$out" != "$root" && "$out" != "$root/"* ]] || {
  echo "S8_PJSIP_BUILD_HOLD=out_must_be_outside_source" >&2
  exit 2
}
for tool in cmake ninja swig git; do
  command -v "$tool" >/dev/null || {
    echo "S8_PJSIP_BUILD_HOLD=missing_$tool" >&2
    exit 3
  }
done
[[ -f "$root/CMakeLists.txt" && -x "$ndk/ndk-build" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=missing_pinned_source_or_android_ndk" >&2
  exit 3
}
[[ "$(git -C "$root" describe --tags --exact-match)" == 2.17 ]] || {
  echo "S8_PJSIP_BUILD_HOLD=source_not_exactly_tag_2.17" >&2
  exit 3
}
[[ -z "$(git -C "$root" status --porcelain=v1)" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=source_has_uncommitted_changes" >&2
  exit 3
}
mkdir -p "$out"
cmake -S "$root" -B "$out" -G Ninja \
  "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF \
  -DPJ_BUILD_APPS=OFF -DPJMEDIA_WITH_VIDEO=OFF \
  -DPJMEDIA_WITH_SRTP=ON -DPJSIP_WITH_TLS=ON \
  -DPJLIB_WITH_SSL=openssl -DPJ_BUILD_SWIG_JAVA=ON \
  "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"

# CMake options are necessary, not sufficient: verify the generated cache.
for required in 'PJMEDIA_WITH_SRTP:BOOL=ON' 'PJSIP_WITH_TLS:BOOL=ON' 'PJ_BUILD_SWIG_JAVA:BOOL=ON'; do
  if ! grep -Fqx "$required" "$out/CMakeCache.txt"; then
    echo "S8_PJSIP_BUILD_HOLD=required_security_option_not_enabled" >&2
    exit 4
  fi
done
if grep -Eq '^PJLIB_WITH_SSL:[A-Z]+=($|none|OFF)$' "$out/CMakeCache.txt"; then
  echo "S8_PJSIP_BUILD_HOLD=openssl_tls_unavailable" >&2
  exit 4
fi
cmake --build "$out" --parallel 4
java_src="$(find "$out" "$root/pjsip-apps/src/swig/java" -type f -name 'Endpoint.java' -print -quit 2>/dev/null || true)"
native_lib="$(find "$out" -type f -name 'libpjsua2.so' -print -quit 2>/dev/null || true)"
[[ -n "$java_src" && -n "$native_lib" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=pjsua2_jni_or_java_missing" >&2
  exit 5
}
echo "S8_PJSUA2_ANDROID_ARM64_PROTOTYPE=BUILT"
echo "S8_PJSIP_SECURITY_CONFIG_TLS_SRTP_MANDATORY_AT_RUNTIME=STILL_REQUIRED"
echo "S8_PJSIP_LICENSE_DISTRIBUTION_GATE=UNRESOLVED"
echo "S8_APK_PACKAGING_PERFORMED=false"
echo "REAL_CALL_PERFORMED=false"
