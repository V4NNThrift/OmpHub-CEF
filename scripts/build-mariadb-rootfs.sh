set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
mkdir -p "$ASSETS"
NAME="omphub-mariadb-$$"
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

echo ">> apk add mariadb di alpine:3.20 (arm64)"
docker run --platform linux/arm64 --name "$NAME" alpine:3.20 \
  sh -c "apk add --no-cache mariadb mariadb-client && rm -rf /var/cache/apk/*"

echo ">> export + kompres"
docker export "$NAME" | xz -T0 -6 > "$ASSETS/mariadb-rootfs.tar.xz"

LIST="$(mktemp)"
tar -tJf "$ASSETS/mariadb-rootfs.tar.xz" > "$LIST"
grep -q 'usr/bin/mariadbd$' "$LIST" || { echo "mariadbd tidak ada di rootfs hasil build"; exit 1; }
rm -f "$LIST"
ls -la "$ASSETS/mariadb-rootfs.tar.xz"
