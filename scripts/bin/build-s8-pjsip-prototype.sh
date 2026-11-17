#!/usr/bin/env bash
# Isolated, experimental Android ARM64 PJSUA2/JNI build only.
# PJSIP 2.17 Android uses configure-android + make (not its later CMake port).
# No native code is copied into the product APK until licensing is resolved.
set -euo pipefail
umask 077

if [[ "${ALLOW_S8_ISOLATED_PJSIP_BUILD:-0}" != 1 ]]; then
  echo "S8_PJSIP_BUILD_HOLD=isolated_prototype_opt_in_required" >&2
  exit 2
fi

base="${S8_NATIVE_ROOT:-$HOME/agent-workspace/third-party/ai-calls-s8}"
root="${PJSIP_SRC:-$base/pjproject-2.17}"
work="${PJSIP_BUILD_DIR:-$base/pjproject-autotools-android-arm64}"
openssl_src="${S8_OPENSSL_SRC:-$base/openssl-3.5.9}"
openssl_prefix="${S8_OPENSSL_PREFIX:-$base/openssl-android-arm64-3.5.9}"
ndk="${ANDROID_NDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/28.2.13676358}"
api=28

for tool in git make swig perl; do
  command -v "$tool" >/dev/null || {
    echo "S8_PJSIP_BUILD_HOLD=missing_$tool" >&2
    exit 3
  }
done
[[ -x "$ndk/ndk-build" && -f "$root/configure-android" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=missing_android_ndk_or_pjsip_2_17" >&2
  exit 3
}
[[ "$(git -C "$root" describe --tags --exact-match)" == 2.17 ]] || {
  echo "S8_PJSIP_BUILD_HOLD=unpinned_pjsip_source" >&2
  exit 3
}
[[ -z "$(git -C "$root" status --porcelain=v1)" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=dirty_pjsip_source" >&2
  exit 3
}
# Refuse source/build path overlap or accidental product checkout mutation.
[[ "$work" != "$root" && "$work" != "$root/"* &&
   "$work" != "$openssl_src" && "$work" != "$openssl_src/"* ]] || {
  echo "S8_PJSIP_BUILD_HOLD=source_build_overlap" >&2
  exit 3
}
export ANDROID_NDK_ROOT="$ndk"
toolchain="$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin"
[[ -x "$toolchain/clang" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=missing_ndk_clang" >&2
  exit 3
}
export PATH="$toolchain:$PATH"
mkdir -p "$base"

# OpenSSL 3.5 is the LTS branch; use the pinned 3.5.9 security release.
if [[ ! -d "$openssl_src/.git" ]]; then
  [[ ! -e "$openssl_src" ]] || {
    echo "S8_PJSIP_BUILD_HOLD=untrusted_existing_openssl_source" >&2
    exit 3
  }
  git clone --depth 1 --branch openssl-3.5.9 \
    https://github.com/openssl/openssl.git "$openssl_src"
fi
[[ "$(git -C "$openssl_src" describe --tags --exact-match)" == openssl-3.5.9 ]] || {
  echo "S8_PJSIP_BUILD_HOLD=unexpected_openssl_tag" >&2
  exit 3
}
if [[ ! -f "$openssl_prefix/lib/libssl.a" ||
      ! -f "$openssl_prefix/lib/libcrypto.a" ||
      ! -f "$openssl_prefix/include/openssl/ssl.h" ]]; then
  (
    cd "$openssl_src"
    # This is the isolated third-party checkout, not an application worktree.
    ./Configure android-arm64 "-D__ANDROID_API__=$api" \
      "--prefix=$openssl_prefix" "--openssldir=$openssl_prefix/ssl" \
      --libdir=lib no-tests no-apps no-shared
    make -j 4
    make install_sw
  )
fi
[[ -f "$openssl_prefix/lib/libssl.a" &&
   -f "$openssl_prefix/lib/libcrypto.a" &&
   -f "$openssl_prefix/include/openssl/ssl.h" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=android_openssl_not_compiled" >&2
  exit 4
}
echo "S8_ANDROID_ARM64_OPENSSL_3_5_9=READY"

# PJSIP 2.17's supported Android build modifies its working tree. Use a
# separate detached Git worktree and leave the pinned source checkout clean.
if [[ ! -f "$work/configure-android" ]]; then
  [[ ! -e "$work" ]] || {
    echo "S8_PJSIP_BUILD_HOLD=untrusted_existing_pjsip_workdir" >&2
    exit 3
  }
  git -C "$root" worktree add --detach "$work" 2.17
fi
[[ "$(git -C "$work" rev-parse HEAD)" == "$(git -C "$root" rev-parse HEAD)" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=build_source_revision_mismatch" >&2
  exit 3
}

cd "$work"
cat > pjlib/include/pj/config_site.h <<'EOF'
#define PJ_CONFIG_ANDROID 1
#include <pj/config_site_sample.h>
#define PJMEDIA_HAS_VIDEO 0
EOF

# For Android 15+ 16 KiB pages, use NDK r28 and page-size-safe flags.
export OPENSSL_DIR="$openssl_prefix"
export CFLAGS="-fPIC -D__BIONIC_NO_PAGE_SIZE_MACRO"
export LDFLAGS="-Wl,-z,max-page-size=16384"
config_log="$base/pjsip-2.17-configure-android-arm64.log"
APP_PLATFORM="$api" TARGET_ABI=arm64-v8a ./configure-android \
  --use-ndk-cflags "--with-ssl=$OPENSSL_DIR" > "$config_log" 2>&1 || {
  tail -n 70 "$config_log" >&2
  echo "S8_PJSIP_BUILD_HOLD=configure_android_failed" >&2
  exit 4
}
if ! grep -Fq 'OpenSSL library found, SSL support enabled' "$config_log"; then
  tail -n 55 "$config_log" >&2
  echo "S8_PJSIP_BUILD_HOLD=openssl_tls_not_confirmed" >&2
  exit 4
fi
echo "S8_PJSIP_2_17_TLS_CONFIGURED=PASS"
make dep
make -j 4
(
  cd pjsip-apps/src/swig
  make -j 4
)
jni="pjsip-apps/src/swig/java/android/pjsua2/src/main/jniLibs/arm64-v8a/libpjsua2.so"
java="pjsip-apps/src/swig/java/android/pjsua2/src/main/java/org/pjsip/pjsua2/Endpoint.java"
[[ -s "$jni" && -s "$java" ]] || {
  echo "S8_PJSIP_BUILD_HOLD=java_or_jni_missing" >&2
  exit 5
}
# Verify actual ABI rather than assuming the output path is truthful.
readelf="$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
"$readelf" -h "$jni" | grep -Eq 'Machine:.*AArch64' || {
  echo "S8_PJSIP_BUILD_HOLD=wrong_jni_arch" >&2
  exit 5
}
echo "S8_PJSUA2_ANDROID_ARM64_PROTOTYPE=BUILT"
echo "S8_PJSIP_TLS_SRTP_RUNTIME_VALIDATION=OPEN"
echo "S8_PJSIP_LICENSE_DISTRIBUTION_GATE=OPEN"
echo "S8_APK_PACKAGING_PERFORMED=false"
echo "REAL_CALL_PERFORMED=false"
