set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$DEST"

for t in curl ar tar awk find od perl xz jq; do
  command -v "$t" >/dev/null || { echo "Butuh '$t'"; exit 1; }
done

TERMUX="https://packages.termux.dev/apt/termux-main"
echo ">> Mengambil indeks paket Termux (aarch64)"
curl -fsSL "$TERMUX/dists/stable/main/binary-aarch64/Packages" -o "$WORK/Packages"

termux_deb() {
  local pkg="$1" file
  file=$(awk -v p="$pkg" 'BEGIN{RS="";FS="\n"}
    { name=""; fn="";
      for(i=1;i<=NF;i++){
        if($i ~ /^Package: /)  name=substr($i,10);
        if($i ~ /^Filename: /) fn=substr($i,11);
      }
      if(name==p){print fn; exit} }' "$WORK/Packages")
  [ -n "$file" ] || { echo "Paket Termux '$pkg' tidak ditemukan"; exit 1; }
  echo ">> $pkg  ($file)"
  curl -fsSL "$TERMUX/$file" -o "$WORK/$pkg.deb"
  mkdir -p "$WORK/$pkg"
  ( cd "$WORK/$pkg" && ar x "../$pkg.deb" && tar xf data.tar.* )
}

termux_deb proot
P="$WORK/proot/data/data/com.termux/files/usr"
cp -L "$P/bin/proot" "$DEST/libproot.so"
cp -L "$P/libexec/proot/loader" "$DEST/libproot-loader.so"

termux_deb libtalloc
T="$WORK/libtalloc/data/data/com.termux/files/usr/lib"
cp -L "$(ls "$T"/libtalloc.so* | head -n1)" "$DEST/libtalloc.so"

SZ_BEFORE=$(wc -c < "$DEST/libproot.so")
perl -0777 -pi -e 's/libtalloc\.so\.2\0/libtalloc.so\0\0\0/g' "$DEST/libproot.so"
[ "$(wc -c < "$DEST/libproot.so")" = "$SZ_BEFORE" ] || { echo "Patch mengubah ukuran libproot.so, dibatalkan"; exit 1; }

termux_deb libandroid-shmem
S="$WORK/libandroid-shmem/data/data/com.termux/files/usr/lib"
cp -L "$(ls "$S"/libandroid-shmem.so* | head -n1)" "$DEST/libandroid-shmem.so"

echo ">> qemu-user-static (Debian arm64)"

QEMU_SOURCE="${QEMU_SOURCE:-ubuntu}"
case "$QEMU_SOURCE" in
  ubuntu) BASE="https://ports.ubuntu.com/ubuntu-ports/pool/universe/q/qemu"
          command -v zstd >/dev/null || { echo "Butuh 'zstd' untuk paket Ubuntu"; exit 1; } ;;
  debian) BASE="https://deb.debian.org/debian/pool/main/q/qemu" ;;
  *) echo "QEMU_SOURCE harus debian atau ubuntu"; exit 1 ;;
esac
echo "   sumber: $QEMU_SOURCE ($BASE)"
ALL=$(curl -fsSL "$BASE/" | grep -o 'qemu-user-static_[^"]*_arm64\.deb' | sort -uV | tac)
echo "   versi tersedia:"; echo "$ALL" | sed 's/^/     /'

if [ -n "${QEMU_VERSION:-}" ]; then
  LIST=$(echo "$ALL" | grep -F "qemu-user-static_${QEMU_VERSION}" || true)
  [ -n "$LIST" ] || { echo "Tidak ada qemu-user-static dengan awalan '$QEMU_VERSION'"; exit 1; }
else
  LIST="$ALL"
fi
[ -n "$LIST" ] || { echo "qemu-user-static arm64 tidak ditemukan di $BASE"; exit 1; }

is_arm64_elf() {
  [ "$(head -c4 "$1" 2>/dev/null | od -An -c | tr -d ' ')" = '177ELF' ] || return 1
  [ "$(od -An -tx1 -j18 -N1 "$1" | tr -d ' ')" = "b7" ]
}

resolve_in_root() {
  local p="$1" root="$2" i=0 t
  while [ -L "$p" ] && [ "$i" -lt 10 ]; do
    t=$(readlink "$p")
    case "$t" in
      /*) p="$root$t" ;;
      *)  p="$(dirname "$p")/$t" ;;
    esac
    i=$((i + 1))
  done
  echo "$p"
}

FOUND=""
TRIES=0
for DEB in $LIST; do
  TRIES=$((TRIES + 1))
  [ "$TRIES" -le 8 ] || break
  echo "   coba $DEB"
  rm -rf "$WORK/qemu"
  mkdir -p "$WORK/qemu"
  curl -fsSL "$BASE/$DEB" -o "$WORK/qemu.deb" || continue
  ( cd "$WORK/qemu" && ar x ../qemu.deb && tar xf data.tar.* ) || continue

  while IFS= read -r Q; do
    R=$(resolve_in_root "$Q" "$WORK/qemu")
    if [ -f "$R" ] && is_arm64_elf "$R"; then
      FOUND="$R"
      break
    fi
  done < <(find "$WORK/qemu" \( -type f -o -type l \) \
             \( -name '*i386-binfmt-P' -o -name 'qemu-i386*' \))

  if [ -n "$FOUND" ]; then
    echo "   ditemukan: ${FOUND#$WORK/qemu}"
    break
  fi
  echo "   tidak ada binary qemu-i386 asli di $DEB; isi paket terkait i386:"
  find "$WORK/qemu" -name '*i386*' -exec ls -l {} \; | sed 's/^/     /'
  echo "   coba versi sebelumnya"
done

[ -n "$FOUND" ] || { echo "Tidak ada paket qemu-user-static arm64 yang berisi binary qemu-i386 yang valid"; exit 1; }
cp -L "$FOUND" "$DEST/libqemu-i386.so"

ASSETS="$ROOT/app/src/main/assets"
mkdir -p "$ASSETS"

echo ">> Rootfs Debian i386 (bundel APK, dari Docker Hub)"
REPO="i386/debian"; TAG="bookworm-slim"
REG="https://registry-1.docker.io/v2/$REPO"
ACCEPT="application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"
TOKEN=$(curl -fsSL "https://auth.docker.io/token?service=registry.docker.io&scope=repository:$REPO:pull" | jq -r .token)
dh() { curl -fsSL -H "Authorization: Bearer $TOKEN" -H "Accept: $ACCEPT" "$@"; }

MANIFEST=$(dh "$REG/manifests/$TAG")
if echo "$MANIFEST" | jq -e 'has("manifests")' >/dev/null; then
  DIGEST=$(echo "$MANIFEST" | jq -r '[.manifests[] | select(.platform.os=="linux" and .platform.architecture=="386")][0].digest')
  [ -n "$DIGEST" ] && [ "$DIGEST" != "null" ] || { echo "linux/386 tidak ada di $REPO:$TAG"; exit 1; }
  MANIFEST=$(dh "$REG/manifests/$DIGEST")
fi
LAYER=$(echo "$MANIFEST" | jq -r '.layers[0].digest')
dh "$REG/blobs/$LAYER" -o "$ASSETS/rootfs-game.tar.gz"
tar -tzf "$ASSETS/rootfs-game.tar.gz" >/dev/null || { echo "rootfs-game.tar.gz rusak"; exit 1; }

echo ">> libatomic1 i386 (bundel APK, Ubuntu noble)"
GCC_POOL="https://archive.ubuntu.com/ubuntu/pool/main/g/gcc-14"
ATOMIC_DEB=$(curl -fsSL "$GCC_POOL/" | grep -o 'libatomic1_14[^"]*24\.04[^"]*_i386\.deb' | sort -uV | tail -n1)
[ -n "$ATOMIC_DEB" ] || { echo "libatomic1 i386 tidak ditemukan di $GCC_POOL"; exit 1; }
echo "   $ATOMIC_DEB"
curl -fsSL "$GCC_POOL/$ATOMIC_DEB" -o "$WORK/libatomic1.deb"
mkdir -p "$WORK/atomic"
( cd "$WORK/atomic" && ar x ../libatomic1.deb )
DATA=$(ls "$WORK"/atomic/data.tar.* | head -n1)
case "$DATA" in
  *.xz) cp "$DATA" "$ASSETS/libatomic1-i386.tar.xz" ;;
  *)    mkdir -p "$WORK/atomic/content"
        tar -xf "$DATA" -C "$WORK/atomic/content"
        tar -C "$WORK/atomic/content" -cJf "$ASSETS/libatomic1-i386.tar.xz" . ;;
esac
tar -tJf "$ASSETS/libatomic1-i386.tar.xz" > "$WORK/atomic.list"
grep -q 'libatomic\.so\.1' "$WORK/atomic.list" || { echo "libatomic.so.1 tidak ada di arsip"; cat "$WORK/atomic.list"; exit 1; }

ls -la "$ASSETS"

chmod 755 "$DEST"/*.so
echo
echo ">> Selesai:"
ls -la "$DEST"

if command -v file >/dev/null; then
  echo; echo ">> Cek arsitektur (harus ARM aarch64):"
  file "$DEST"/*.so
fi

if command -v readelf >/dev/null; then
  echo; echo ">> Cek dependency (harus ada di APK atau berupa library sistem Android):"
  SYS=" libc.so libdl.so libm.so liblog.so libz.so "
  MISSING=0
  for so in "$DEST"/*.so; do
    case "$(basename "$so")" in libqemu-i386.so|libproot-loader.so) continue ;; esac
    for need in $(readelf -d "$so" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'); do
      if [ -f "$DEST/$need" ] || [[ "$SYS" == *" $need "* ]]; then continue; fi
      echo "   KURANG: $(basename "$so") butuh $need"
      MISSING=1
    done
  done
  [ "$MISSING" = 0 ] || { echo "Ada dependency yang belum disertakan di APK"; exit 1; }
  echo "   semua dependency terpenuhi"
fi