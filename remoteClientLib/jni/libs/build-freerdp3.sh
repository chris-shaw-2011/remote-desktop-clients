#!/usr/bin/env bash
# Build the pinned Android RDP stack with upstream's CMake ExternalDeps recipe.
set -eo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source build-deps.conf
set -u

sdk_root=${ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}
: "${sdk_root:?Set ANDROID_SDK to the Android SDK directory}"
ndk_root=${FREERDP_NDK:-$PWD/android-ndk-r25c}
cmake_program=${FREERDP_CMAKE:-$(command -v cmake)}
if [[ ! -f "$ndk_root/source.properties" ]]; then
    source build-deps-functions.sh
    ndk_root=$(install_ndk . "$freerdp_ndk_version")
fi
abis=${ANDROID_ABIS:-arm64-v8a}
jobs=${FREERDP_BUILD_JOBS:-8}
source_dir=$PWD/deps/FreeRDP
cache_dir=$PWD/tar/rdp
mkdir -p "$cache_dir"

fetch() {
    local url=$1 archive=$2 expected=$3
    if [[ ! -f "$archive" ]]; then
        curl -fL --retry 3 --connect-timeout 20 --max-time 600 "$url" -o "$archive.partial"
        mv "$archive.partial" "$archive"
    fi
    printf '%s  %s\n' "$expected" "$archive" | sha256sum -c -
}

fetch "$freerdp_source_url" "$cache_dir/freerdp-$freerdp_ver.tar.gz" "$freerdp_source_hash"
fetch "https://github.com/openssl/openssl/releases/download/openssl-$freerdp_openssl_ver/openssl-$freerdp_openssl_ver.tar.gz" "$cache_dir/openssl-$freerdp_openssl_ver.tar.gz" "$freerdp_openssl_hash"
fetch "$freerdp_ffmpeg_url" "$cache_dir/ffmpeg-$freerdp_ffmpeg_ver.tar.gz" "$freerdp_ffmpeg_hash"

patches=(freerdp3-build.patch ../../freerdp-adapter/java.patch freerdp3-patches/*.patch)
fingerprint=$({
    printf '%s\n' "$abis" "$ndk_root" "$cmake_program" "$("$cmake_program" --version | head -n 1)" "${CFLAGS:-}" "${CXXFLAGS:-}" "${LDFLAGS:-}"
    sha256sum build-deps.conf build-freerdp3.sh "${patches[@]}" "$ndk_root/source.properties"
} | sha256sum | cut -d' ' -f1)
output=$source_dir/client/Android/Studio/freeRDPCore/src/main/jniLibs
artifacts=(libfreerdp-android.so libfreerdp3.so libfreerdp-client3.so libwinpr3.so libssl.so libcrypto.so libavcodec.so libavutil.so libswscale.so libswresample.so)
complete=true
for abi in $abis; do
    for library in "${artifacts[@]}"; do
        [[ -f "$output/$abi/$library" ]] || complete=false
    done
done
if [[ ${FORCE_FREERDP_BUILD:-false} != true && "$complete" == true && -f FREERDP_BUILT && $(cat FREERDP_BUILT) == "$fingerprint" ]]; then
    echo 'FreeRDP 3 build inputs and artifacts match; skipping rebuild.'
    exit 0
fi
rm -f FREERDP_BUILT
# This tree contains only downloaded/generated dependency output.
rm -rf "$source_dir"
mkdir -p "$source_dir"
tar xf "$cache_dir/freerdp-$freerdp_ver.tar.gz" --strip-components=1 -C "$source_dir"
for patch_file in "${patches[@]}"; do
    patch --batch --fuzz=0 -d "$source_dir" -p1 < "$patch_file"
done

build_root=$PWD/deps/rdp-build/$fingerprint
for abi in $abis; do
    build_dir=$build_root/$abi
    # Host pkg-config paths must never satisfy Android dependency discovery.
    export PKG_CONFIG_LIBDIR="$build_root/prefix/$abi/lib/pkgconfig"
    export PKG_CONFIG_PATH="$PKG_CONFIG_LIBDIR"
    "$cmake_program" -S "$source_dir/client/Android/Studio/freeRDPCore/src/main/cpp" -B "$build_dir" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ndk_root/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-23 -DANDROID_STL=c++_shared \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_LIBDIR=lib \
        -DFREERDP_BUILD_JOBS="$jobs" \
        -DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 \
        -DFREERDP_DEPENDENCY_PREFIX="$build_root/prefix" \
        -DFREERDP_OPENSSL_VERSION="openssl-$freerdp_openssl_ver" \
        -DFREERDP_OPENSSL_HASH="SHA256=$freerdp_openssl_hash" \
        -DFREERDP_OPENSSL_URL="file://$cache_dir/openssl-$freerdp_openssl_ver.tar.gz" \
        -DFREERDP_FFMPEG_VERSION="$freerdp_ffmpeg_ver" \
        -DFREERDP_FFMPEG_HASH="SHA256=$freerdp_ffmpeg_hash" \
        -DFREERDP_FFMPEG_URL="file://$cache_dir/ffmpeg-$freerdp_ffmpeg_ver.tar.gz" \
        -DWITH_MEDIACODEC=ON -DWITH_FFMPEG=ON -DWITH_OPENSSL=ON -DWITH_OPENH264=OFF \
        -DWITH_CJSON=OFF -DWITH_OPUS=OFF -DWITH_PNG=OFF -DWITH_WEBP=OFF -DWITH_JPEG=ON
    CMAKE_BUILD_PARALLEL_LEVEL="$jobs" "$cmake_program" --build "$build_dir" --parallel "$jobs"
    mkdir -p "$output/$abi"
    cp -L "$build_root/prefix/$abi/lib/"*.so "$output/$abi/"
    cp "$build_dir/libfreerdp-android.so" "$output/$abi/"
    for library in "${artifacts[@]}"; do test -s "$output/$abi/$library"; done
done
printf '%s\n' "$fingerprint" > FREERDP_BUILT
