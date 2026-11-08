# Komponen pihak ketiga

APK ini membawa (dibundel) dan/atau mengunduh komponen berikut. Masing-masing punya lisensi sendiri.

## Dibundel di dalam APK

| Komponen | Sumber | Lisensi |
|---|---|---|
| PRoot + loader | paket Termux | GPL-2.0-or-later |
| talloc | paket Termux | LGPL-3.0-or-later |
| libandroid-shmem | paket Termux | BSD-3-Clause <!-- verifikasi di repo Termux sebelum rilis --> |
| QEMU user-mode (qemu-i386-static) | paket Ubuntu `qemu-user-static` | GPL-2.0 (dan lainnya) |
| libatomic1 (i386) | paket Ubuntu (bagian dari GCC) | GPL-3.0 dengan GCC Runtime Library Exception |
| Debian i386 rootfs (`i386/debian:bookworm-slim`) | debuerreotype / Debian | berbagai (DFSG) |
| Alpine minirootfs (aarch64) | alpinelinux.org | berbagai |
| MariaDB | Alpine Linux | GPL-2.0 |
| Apache Commons Compress | Apache | Apache-2.0 |
| XZ for Java | tukaani.org | Domain publik |
| AndroidX / Jetpack Compose | Google | Apache-2.0 |

## Diunduh atau diunggah oleh pengguna (tidak dibundel di APK)

| Komponen | Sumber | Lisensi |
|---|---|---|
| open.mp server | github.com/openmultiplayer/open.mp | MPL-2.0 |
| SA-MP server 0.3.7 / 0.3.DL (`samp03svr`, opsional) | URL yang bisa diatur di Setup | Freeware, hak cipta SA-MP Team; ikuti ketentuan dari sa-mp.com |

## Kewajiban lisensi

Karena APK mendistribusikan binary berlisensi GPL/LGPL (PRoot, talloc, QEMU, libatomic1, MariaDB, dan paket di dalam rootfs), siapa pun yang mendistribusikan APK ini wajib menyediakan source-nya atau penawaran tertulis untuk itu, serta memberi tahu pengguna lisensinya.

Source komponen dapat diperoleh dari:

- Termux: https://github.com/termux/termux-packages
- Ubuntu (qemu, gcc): https://packages.ubuntu.com
- Debian: https://sources.debian.org
- Alpine (mariadb): https://pkgs.alpinelinux.org
