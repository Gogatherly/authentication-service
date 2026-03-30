# Authentication Service

Service ini menangani autentikasi untuk ekosistem Gogatherly. Implementasinya berbasis Spring Boot dan menyediakan alur login Google, registrasi email, verifikasi email, login email, refresh access token, dan logout berbasis refresh token.

## Fitur Utama

- Login menggunakan Google ID token
- Registrasi akun dengan email dan password
- Verifikasi email menggunakan kode 6 digit
- Resend kode verifikasi
- Ganti email untuk akun yang belum terverifikasi
- Login email/password setelah email terverifikasi
- Penerbitan JWT access token
- Penyimpanan refresh token di database
- Refresh access token
- Logout dengan invalidasi refresh token

## Stack

- Java 25
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Security
- Spring Data JPA
- MySQL
- JWT (`jjwt`)
- Google API Client
- Lombok
- Maven

## Struktur Singkat

- `src/main/java/com/fizu/authentication/controller` untuk endpoint HTTP
- `src/main/java/com/fizu/authentication/service` untuk business logic autentikasi, Google verification, dan email verification
- `src/main/java/com/fizu/authentication/model` untuk entity dan repository
- `src/main/java/com/fizu/authentication/security` untuk konfigurasi security
- `src/main/java/com/fizu/authentication/exception` untuk exception domain dan global handler
- `src/main/resources/application.properties` untuk konfigurasi aplikasi
- `databases.sql` untuk contoh schema database awal

## Alur Autentikasi

### 1. Login Google

Client mengirim Google ID token ke `/auth/google`. Service memverifikasi token ke Google, membuat user baru jika email belum ada, lalu mengembalikan access token dan refresh token.

### 2. Registrasi Email

Client mengirim email, password, dan username ke `/auth/email/register`. Service membuat akun dengan `provider=email`, menyimpan password yang sudah di-hash, menghasilkan kode verifikasi 6 digit, lalu mengirimkan kode ke email.

### 3. Verifikasi Email

Client mengirim email dan kode ke `/auth/email/verify`. Jika kode valid dan belum expired, akun ditandai sebagai terverifikasi.

### 4. Login Email

Client login melalui `/auth/email/login`. Hanya akun email yang sudah terverifikasi yang bisa login dan menerima token.

### 5. Refresh Token

Client mengirim refresh token ke `/auth/refresh`. Jika token masih valid dan belum expired, service mengeluarkan access token baru.

### 6. Logout

Client mengirim refresh token ke `/auth/logout`. Service menghapus refresh token dari database sehingga token tersebut tidak bisa dipakai lagi.

## Environment Variables

Service membaca konfigurasi dari environment variable berikut:

| Variable | Wajib | Keterangan |
| --- | --- | --- |
| `DATABASE_HOST` | Ya | Host MySQL |
| `DATABASE_PORT` | Ya | Port MySQL |
| `DATABASE_NAME` | Ya | Nama database |
| `DATABASE_USERNAME` | Ya | Username database |
| `DATABASE_PASSWORD` | Ya | Password database |
| `JWT_SECRET_KEY` | Ya | Secret untuk signing JWT |
| `JWT_EXPIRATION` | Ya | Durasi access token sesuai implementasi `JwtUtil` |
| `REFRESH_TOKEN_EXPIRATION` | Ya | Jumlah hari masa berlaku refresh token |
| `GOOGLE_CLIENT_ID` | Ya | Google OAuth client ID untuk verifikasi ID token |
| `GOOGLE_CLIENT_SECRET` | Ya | Tersedia di konfigurasi Spring OAuth client |
| `EMAIL_VERIFICATION_EXPIRATION_MINUTES` | Tidak | Default `10` menit |

Contoh `.env`:

```env
DATABASE_HOST=localhost
DATABASE_PORT=3306
DATABASE_NAME=authentication_service
DATABASE_USERNAME=root
DATABASE_PASSWORD=secret

JWT_SECRET_KEY=replace-with-strong-secret
JWT_EXPIRATION=3600000
REFRESH_TOKEN_EXPIRATION=7

GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

EMAIL_VERIFICATION_EXPIRATION_MINUTES=10
```

## Menjalankan Service

### Prasyarat

- Java 25
- Maven wrapper (`./mvnw`) atau Maven terpasang
- MySQL aktif

### 1. Siapkan database

Buat database MySQL lalu sesuaikan environment variable. File `databases.sql` bisa dipakai sebagai referensi schema awal.

### 2. Jalankan aplikasi

```bash
./mvnw spring-boot:run
```

Default port aplikasi adalah `8000`.

## Endpoint API

Semua endpoint berikut terbuka di bawah prefix `/auth/**`.

| Method | Endpoint | Deskripsi |
| --- | --- | --- |
| `POST` | `/auth/google` | Login/register menggunakan Google ID token |
| `POST` | `/auth/email/register` | Registrasi akun email |
| `POST` | `/auth/email/login` | Login email dan password |
| `POST` | `/auth/email/verify` | Verifikasi email dengan kode |
| `POST` | `/auth/email/resend` | Kirim ulang kode verifikasi |
| `POST` | `/auth/email/change` | Ganti email akun yang belum verified |
| `POST` | `/auth/refresh` | Tukar refresh token menjadi access token baru |
| `POST` | `/auth/logout` | Hapus refresh token |

## Contoh Request

### Google Login

```http
POST /auth/google
Content-Type: application/json

{
  "idToken": "google-id-token"
}
```

### Register Email

```http
POST /auth/email/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "secret123",
  "username": "user123"
}
```

### Verify Email

```http
POST /auth/email/verify
Content-Type: application/json

{
  "email": "user@example.com",
  "code": "123456"
}
```

### Email Login

```http
POST /auth/email/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "secret123"
}
```

### Refresh Access Token

```http
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "stored-refresh-token"
}
```

### Logout

```http
POST /auth/logout
Content-Type: application/json

{
  "refreshToken": "stored-refresh-token"
}
```

## Bentuk Response

Response sukses dibungkus dengan `ApiSuccessResponse`.

Contoh respons login:

```json
{
  "message": "Login successful",
  "data": {
    "token": "jwt-access-token",
    "refreshToken": "refresh-token"
  }
}
```

Response error dibungkus dengan `ApiErrorResponse`. Beberapa status yang sudah ditangani:

- `400 Bad Request` untuk validation error, verification code invalid/expired, dan business validation lain
- `401 Unauthorized` untuk kredensial salah, Google token tidak valid, atau refresh token tidak valid
- `403 Forbidden` untuk email yang belum diverifikasi
- `404 Not Found` untuk resource seperti email yang tidak ditemukan
- `409 Conflict` untuk email sudah terdaftar, email sudah verified, atau mismatch provider
- `500 Internal Server Error` untuk error yang tidak tertangani

## Catatan Implementasi

- Security saat ini mengizinkan semua endpoint `/auth/**` tanpa autentikasi.
- Password disimpan menggunakan `BCryptPasswordEncoder`.
- Refresh token disimpan di tabel `refresh_token`.
- Verifikasi email saat ini masih berupa logging melalui `EmailServiceImpl`, belum terhubung ke provider email eksternal.
- Google login memakai verifikasi audience berdasarkan `GOOGLE_CLIENT_ID`.

## Testing

Untuk menjalankan test:

```bash
./mvnw test
```

Test yang sudah ada mencakup alur logout dan refresh token pada `AuthServiceImplTest`.
