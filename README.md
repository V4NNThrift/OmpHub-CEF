# OmpHub

Panel untuk menjalankan server **open.mp** atau **SA-MP** (0.3.7 / 0.3.DL) beserta **MariaDB** langsung dari HP Android (arm64), tanpa root.

Kelola server game, database, file, console, dan performa perangkat melalui satu aplikasi.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(arm64)-3DDC84)
![Version](https://img.shields.io/badge/version-1.1.4-blue)

Rootfs game dan MariaDB dibundel di dalam APK dan dipasang otomatis saat aplikasi pertama kali dibuka. Server game open.mp atau SA-MP dipasang sendiri oleh pengguna melalui tab Setup atau dengan mengunggah arsip server.

---

## Daftar Isi

- [Fitur](#fitur)
- [Persyaratan](#persyaratan)
- [Instalasi](#instalasi)
- [Pemakaian](#pemakaian)
- [Menghubungkan Gamemode ke Database](#menghubungkan-gamemode-ke-database)
- [Konfigurasi](#konfigurasi)
- [Backup dan Restore](#backup-dan-restore)
- [Lokasi Data](#lokasi-data)
- [Arsitektur](#arsitektur)
- [Troubleshooting](#troubleshooting)
- [Batasan dan Catatan Penting](#batasan-dan-catatan-penting)
- [Atribusi dan Lisensi Komponen](#atribusi-dan-lisensi-komponen)

---

## Fitur

### Kontrol Server
- Pilih mesin server **open.mp** atau **SA-MP** versi 0.3.7 dan 0.3.DL.
- Start, Stop, dan Restart server melalui aplikasi.
- Urutan startup server mengutamakan MariaDB sebelum server game jika opsi menjalankan MariaDB bersama server diaktifkan.
- Server berjalan sebagai foreground service agar tetap aktif ketika aplikasi berada di background.
- Stop server secara halus melalui perintah `exit` atau RCON.

### Dashboard
- Informasi server secara real-time melalui query UDP.
- Menampilkan nama server, jumlah pemain, mode, bahasa, dan status password.
- Daftar pemain online beserta skor dan ping.
- Informasi alamat LAN dan alamat lokal server.
- Kartu Performa untuk memantau CPU, RAM, thread, dan uptime server game, MariaDB, serta panel.
- Grafik performa sekitar 2 menit terakhir.
- Informasi perangkat seperti RAM, suhu, status termal, trafik jaringan, ruang penyimpanan, dan waktu respons query server.

### Console
- Mode input stdin untuk mengirim perintah langsung ke proses server open.mp.
- Mode RCON untuk mengirim perintah melalui UDP.
- Menampilkan log server game dan MariaDB.

### File Manager
- Menjelajahi folder server seperti `gamemodes/`, `filterscripts/`, `plugins/`, dan `scriptfiles/`.
- Upload file dari penyimpanan perangkat.
- Membuat dan menghapus file maupun folder.
- Mengekstrak arsip ZIP.
- Editor teks bawaan untuk mengubah file konfigurasi dan file teks lainnya.

### Database
- Integrasi **phpMyAdmin** melalui WebView di dalam aplikasi OmpHub.
- Mengelola database MariaDB melalui antarmuka phpMyAdmin.
- Tombol **Nyalakan MariaDB** dan **Matikan MariaDB** untuk mengontrol layanan database.
- Import file SQL dari penyimpanan perangkat.
- Export database menjadi file SQL untuk backup.
- Mempertahankan sesi dan halaman terakhir phpMyAdmin ketika pengguna kembali membuka halaman Database, selama sesi masih berlaku.
- MariaDB berjalan native arm64 dan hanya listen pada `127.0.0.1`.

### Setup dan Keamanan
- Instalasi otomatis rootfs game dan MariaDB saat aplikasi pertama kali dibuka.
- Pemilihan dan pemasangan server open.mp atau SA-MP.
- Pengaturan URL unduhan open.mp dan SA-MP tanpa perlu build ulang aplikasi.
- Pemilihan versi SA-MP 0.3.7 atau 0.3.DL.
- Tes lingkungan PRoot untuk game dan database.
- Pengaturan dasar keamanan RCON.
- Update server tidak menimpa file konfigurasi, gamemode, plugin, dan data penting lainnya.

---

## Persyaratan

| Item | Keterangan |
|---|---|
| Android | 8.0 (API 26) atau lebih baru |
| Arsitektur | arm64-v8a |
| Root | Tidak perlu |
| Ruang kosong | Sekitar 1 GB atau lebih |
| Internet | Diperlukan untuk mengunduh server game |
| RAM | Disarankan 4 GB atau lebih |

---

## Instalasi

1. Unduh APK terbaru dari halaman Releases.
2. Izinkan pemasangan dari sumber tidak dikenal.
3. Install dan buka OmpHub.
4. Tunggu proses instalasi otomatis rootfs dan MariaDB selesai.
5. Izinkan notifikasi ketika diminta untuk kebutuhan foreground service.

Instalasi komponen dilakukan ketika aplikasi pertama kali dibuka, bukan ketika APK dipasang. Jika proses terhenti, aplikasi dapat melanjutkan proses instalasi ketika dibuka kembali.

---

## Pemakaian

### 1. Persiapan Awal

Tunggu proses instalasi otomatis selesai. Rootfs game dan MariaDB disiapkan dari aset APK.

### 2. Memasang Server Game

Server game tidak dibundel di dalam APK. Pengguna perlu memasangnya sendiri.

Pilih mesin server pada bagian Setup:

- **open.mp**
- **SA-MP 0.3.7**
- **SA-MP 0.3.DL**

Server dapat dipasang melalui dua cara:

- Unduh langsung dari tab Setup.
- Upload arsip server melalui tab Files, kemudian ekstrak ke folder server.

Pastikan executable `omp-server` atau `samp03svr` berada langsung di folder server.

### 3. Mengatur Gamemode

Upload file gamemode `.amx` ke folder `gamemodes/`.

Untuk open.mp, daftarkan gamemode melalui `pawn.main_scripts` pada `config.json`.

Untuk SA-MP, daftarkan gamemode melalui `server.cfg`:

```ini
gamemode0 namagamemode 1
```

Jangan sertakan ekstensi `.amx`.

### 4. Menjalankan Server

Buka Dashboard dan tekan Start untuk menjalankan server game.

### 5. Mengelola Database

Buka tab Database.

- Tekan **Nyalakan MariaDB** untuk menjalankan layanan database.
- Setelah MariaDB siap, phpMyAdmin dapat digunakan melalui WebView.
- Gunakan phpMyAdmin untuk mengelola database dan tabel.
- Gunakan fitur Import SQL untuk mengimpor file database.
- Gunakan Export SQL untuk membuat cadangan database.
- Tekan **Matikan MariaDB** untuk menghentikan layanan database.

### 6. Menghubungkan Server Game ke Database

Pastikan konfigurasi koneksi MySQL pada gamemode menggunakan host, port, database, username, dan password yang sesuai dengan informasi MariaDB di OmpHub.

---

## Menghubungkan Gamemode ke Database

Gunakan `127.0.0.1`, bukan `localhost`.

Contoh koneksi menggunakan plugin MySQL untuk Pawn:

```pawn
new MySQL:g_SQL;

public OnGameModeInit()
{
    g_SQL = mysql_connect("127.0.0.1", "samp", "PASSWORD_DARI_OMPHUB", "samp");
    return 1;
}
```

**Catatan:**
- Pastikan MariaDB sedang berjalan.
- Gunakan kredensial yang ditampilkan oleh OmpHub.
- Pastikan port database sesuai dengan konfigurasi OmpHub.
- Plugin MySQL harus menggunakan binary Linux 32-bit karena proses server game berjalan melalui lingkungan i386.
- Sesuaikan sintaks `mysql_connect` dengan versi plugin MySQL yang digunakan.

---

## Konfigurasi

Pengaturan server tersedia melalui tab Setup.

| Opsi | Default | Keterangan |
|---|---|---|
| Nama server | `OmpHub Server` | Nama server game |
| Max pemain | `50` | Batas jumlah pemain |
| Port UDP | `7777` | Port server game |
| Port DB | `3306` | Port MariaDB |
| Jalankan MariaDB bersama server | Aktif | Menjalankan MariaDB ketika server game dimulai |

Pengaturan URL unduhan open.mp dan SA-MP dapat diubah melalui bagian URL unduhan.

Perubahan konfigurasi server tertentu membutuhkan restart agar diterapkan.

---

## Backup dan Restore

OmpHub menyediakan fitur backup dan restore untuk membantu menjaga data server.

Backup dapat mencakup:
- Folder server.
- Gamemode dan filterscripts.
- Plugin dan konfigurasi.
- Scriptfiles.
- Dump database SQL apabila MariaDB sedang berjalan.

Selain backup melalui Setup, pengguna juga dapat menggunakan fitur Export SQL di halaman Database untuk membuat cadangan database secara terpisah.

Sebelum melakukan import SQL, disarankan membuat backup terlebih dahulu karena proses import dapat mengubah atau menimpa data yang sudah ada.

---

## Lokasi Data

Data aplikasi disimpan dalam penyimpanan privat OmpHub.

| Path | Isi |
|---|---|
| `server/` | Folder server open.mp atau SA-MP |
| `rootfs/game/` | Rootfs Debian i386 |
| `rootfs/db/` | Rootfs Alpine arm64 dan MariaDB |
| `db/data/` | Datadir MariaDB |
| `db/credentials.json` | Kredensial database |
| `backups/` | File backup dan dump SQL |

Menghapus aplikasi atau melakukan Clear Data akan menghapus data yang tersimpan di penyimpanan privat aplikasi. Selalu buat dan salin backup ke lokasi lain sebelum melakukan tindakan tersebut.

---

## Arsitektur

```text
Android App (Kotlin + Jetpack Compose)
 |
 +-- ServerService
 |    +-- Server Controller
 |    +-- Foreground Service
 |
 +-- PRoot + QEMU
 |    +-- Debian i386
 |         +-- open.mp / SA-MP
 |
 +-- PRoot Native
      +-- Alpine arm64
           +-- MariaDB
           +-- phpMyAdmin (WebView)
```

Poin penting:

- Server game berjalan dalam lingkungan Linux i386 melalui QEMU user-mode.
- MariaDB berjalan native arm64.
- PRoot tidak menggunakan network namespace, sehingga `127.0.0.1` dapat digunakan untuk komunikasi lokal antara server game dan MariaDB.
- Gamemode mengakses MariaDB melalui TCP.
- MariaDB hanya listen pada alamat lokal `127.0.0.1`.

---

## Troubleshooting

| Gejala | Kemungkinan penyebab / solusi |
|---|---|
| Instalasi awal belum selesai | Tunggu proses ekstraksi dan periksa log pada tab Setup. |
| Server game tidak dapat dijalankan | Periksa file executable dan konfigurasi server. |
| MariaDB tidak berjalan | Buka tab Database, nyalakan MariaDB, lalu periksa log database. |
| phpMyAdmin tidak dapat dibuka | Pastikan MariaDB dan layanan web phpMyAdmin sudah berjalan. |
| Import SQL gagal | Periksa format file SQL, isi file, dan target database. |
| Export SQL gagal | Pastikan MariaDB berjalan dan database yang dituju tersedia. |
| Gamemode tidak dapat terhubung ke database | Periksa host `127.0.0.1`, port, username, password, dan nama database. |
| Dashboard tidak menerima query UDP | Pastikan server game berjalan dan query diaktifkan. |
| Server berhenti saat layar mati | Periksa pengaturan optimasi baterai dan izin background perangkat. |

---

## Batasan dan Catatan Penting

1. OmpHub ditujukan untuk perangkat Android arm64.
2. Server game berjalan melalui emulasi QEMU sehingga performanya bergantung pada perangkat.
3. Performa dapat berbeda berdasarkan merek HP, versi Android, dan kebijakan sistem.
4. MariaDB hanya listen pada `127.0.0.1`.
5. Data aplikasi berada di penyimpanan privat. Menghapus aplikasi atau Clear Data dapat menghapus seluruh data server dan database.
6. Buat backup secara berkala untuk menghindari kehilangan data.
7. Pastikan konfigurasi koneksi gamemode menggunakan instance MariaDB OmpHub yang sama agar data game dapat dikelola melalui phpMyAdmin.

---

## Atribusi dan Lisensi Komponen

OmpHub menggunakan dan/atau mendistribusikan berbagai komponen pihak ketiga, termasuk PRoot, QEMU, MariaDB, Debian, Alpine, open.mp, dan SA-MP.

Setiap komponen memiliki lisensi masing-masing. Informasi lebih lanjut dapat dilihat pada [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Proyek ini tidak berafiliasi dengan open.mp, Rockstar Games, atau Take-Two Interactive.
