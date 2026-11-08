#!/bin/bash

DIR=$(dirname $0)
pushd $DIR

DEPVER=17

if which wget
then
  wget -c https://github.com/iiordanov/remote-desktop-clients/releases/download/dependencies/remote-desktop-clients-libs-${DEPVER}.tar.gz
else
  curl -L https://github.com/iiordanov/remote-desktop-clients/releases/download/dependencies/remote-desktop-clients-libs-${DEPVER}.tar.gz -o remote-desktop-clients-libs-${DEPVER}.tar.gz
fi

# A previously rebuilt RDP tree must not be replaced by the older prebuilt
# FreeRDP/OpenSSL libraries. Do not import a build marker from the archive.
freerdp_tree=remoteClientLib/jni/libs/deps/FreeRDP
if [ -d "$freerdp_tree" ]; then
  tar xf "remote-desktop-clients-libs-${DEPVER}.tar.gz" \
    --exclude="$freerdp_tree" --exclude="./$freerdp_tree" \
    --exclude='remoteClientLib/jni/libs/FREERDP_BUILT' \
    --exclude='./remoteClientLib/jni/libs/FREERDP_BUILT'
else
  rm -f remoteClientLib/jni/libs/FREERDP_BUILT
  tar xf "remote-desktop-clients-libs-${DEPVER}.tar.gz" \
    --exclude='remoteClientLib/jni/libs/FREERDP_BUILT' \
    --exclude='./remoteClientLib/jni/libs/FREERDP_BUILT'
fi

if [ ! -d remoteClientLib/src/main/jniLibs -a -d remoteClientLib/libs/ ]
then
  rm -f remoteClientLib/src/main/jniLibs
  cp -a remoteClientLib/libs/ remoteClientLib/src/main/jniLibs/
fi

echo "Done downloading and extracting dependencies."
popd
