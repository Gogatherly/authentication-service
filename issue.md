# Issue: Implementasi Fitur Logout dan Refresh Token Endpoint

## Ringkasan
Tambahkan dua fitur pada `authentication-service`:

1. endpoint logout untuk mencabut refresh token,
2. endpoint refresh token untuk menerbitkan access token baru.

Logout pada service ini **tidak berarti access token JWT langsung mati**. Access token saat ini bersifat stateless dan belum ada mekanisme blacklist. Jadi definisi logout untuk issue ini adalah:

1. Backend menghapus refresh token dari database.
2. Client wajib menghapus access token dan refresh token yang disimpan di sisi client.

## Kondisi Saat Ini
Hasil pembacaan codebase:

1. Login email dan login Google mengembalikan `TokenResponse(token, refreshToken)`.
2. Refresh token disimpan ke tabel `refresh_token` di `AuthServiceImpl.issueTokens(...)`.
3. Belum ada endpoint logout.
4. Belum ada endpoint refresh token.
5. Belum ada mekanisme revoke token.
6. Route `/auth/**` saat ini sudah di-`permitAll()` oleh `SecurityConfig`, jadi endpoint logout dan refresh bisa ditambahkan di area yang sama.

## Tujuan
Menyediakan dua endpoint:

### A. Logout
Endpoint yang:

1. menerima `refreshToken`,
2. menghapus refresh token tersebut dari database,
3. mengembalikan response sukses dengan format wrapper API yang sudah dipakai project ini.

### B. Refresh token
Endpoint yang:

1. menerima `refreshToken`,
2. memvalidasi token tersebut masih ada dan belum expired,
3. mengambil user pemilik token,
4. membuat access token JWT baru,
5. mengembalikan access token baru ke client.

## Scope
Masuk scope:

1. Logout untuk **1 refresh token**.
2. Refresh access token menggunakan **1 refresh token**.
3. Validasi request body.
4. Penghapusan refresh token dari database.
5. Validasi refresh token dari database dan expiry date.
6. Pembuatan access token JWT baru.
7. Unit/integration test untuk controller dan service/repository flow minimal.

Di luar scope:

1. Blacklist access token JWT.
2. Logout semua device / semua session user.
3. Refresh token rotation, jika belum ingin diterapkan.
4. Penerbitan refresh token baru saat refresh, kecuali tim memang mengubah requirement.
5. Perubahan arsitektur security yang besar.

## Definisi Perilaku
### Perilaku utama
Jika client mengirim `refreshToken` yang valid, backend menghapus token tersebut dari tabel `refresh_token` lalu mengembalikan sukses.

### Idempotency
Logout sebaiknya **idempotent**:

1. Jika token ada, hapus token lalu return sukses.
2. Jika token sudah tidak ada / sudah pernah di-logout, tetap return sukses yang sama.

Alasan:

1. Implementasi lebih sederhana.
2. Lebih aman karena tidak membocorkan apakah sebuah refresh token masih aktif atau tidak.
3. Cocok untuk retry dari frontend/mobile.

### Catatan penting
Karena access token belum di-blacklist, token akses yang sudah terbit mungkin masih valid sampai masa berlakunya habis. Issue ini harus menyebutkan hal itu dengan jelas agar tidak terjadi salah ekspektasi.

## Feature A: Logout
## Kontrak API yang Diusulkan
### Endpoint
`POST /auth/logout`

### Request body
```json
{
  "refreshToken": "uuid-or-random-token-string"
}
```

### Validasi request
Field `refreshToken` wajib:

1. ada,
2. bertipe string,
3. tidak boleh blank.

### Response sukses
Gunakan pola response yang sudah ada di project:

```json
{
  "status": "success",
  "message": "Logout successful",
  "data": {
    "message": "Logout successful"
  }
}
```

### Response gagal validasi
Jika `refreshToken` kosong atau tidak dikirim, gunakan format error global yang sudah ada:

```json
{
  "status": "error",
  "message": "Validation failed",
  "errors": {
    "refreshToken": "must not be blank"
  }
}
```

## Rencana Implementasi Detail
### 1. Tambah DTO request logout
Buat DTO baru, misalnya:

`src/main/java/com/fizu/authentication/controller/dto/LogoutRequest.java`

Isi minimal:

1. record/class dengan field `refreshToken`
2. anotasi validasi `@NotBlank`

Contoh bentuk:

```java
public record LogoutRequest(@NotBlank String refreshToken) {}
```

### 2. Tambah method di `AuthService`
Tambahkan method baru pada interface:

```java
MessageResponse logout(String refreshToken);
```

Tujuannya agar controller tidak berisi logic penghapusan token.

### 3. Implementasi logout di `AuthServiceImpl`
Tambahkan method `logout(String refreshToken)` dengan aturan:

1. cari token berdasarkan nilai `refreshToken`,
2. jika ditemukan, hapus dari database,
3. jika tidak ditemukan, jangan lempar error,
4. tetap return `new MessageResponse("Logout successful")`.

Saran implementasi:

1. tandai method dengan `@Transactional`,
2. pakai repository method yang sederhana dan jelas,
3. jangan ubah flow login yang sudah ada.

Contoh pseudo-flow:

```java
Optional<RefreshToken> existingToken = refreshTokenRepository.findByToken(refreshToken);
existingToken.ifPresent(refreshTokenRepository::delete);
return new MessageResponse("Logout successful");
```

## 4. Rapikan repository refresh token
Repository saat ini punya method:

```java
RefreshToken findByToken(String token);
```

Sebaiknya ubah/tambah menjadi method yang lebih aman:

```java
Optional<RefreshToken> findByToken(String token);
void deleteByToken(String token);
boolean existsByToken(String token);
```

Minimal yang dibutuhkan untuk issue ini:

1. `Optional<RefreshToken> findByToken(String token)` atau
2. `void deleteByToken(String token)`

Jika memilih `deleteByToken`, pastikan tetap idempotent dan tidak error saat token tidak ditemukan.

### 5. Tambah endpoint di `AuthController`
Tambahkan endpoint:

```java
@PostMapping("/auth/logout")
public ResponseEntity<ApiSuccessResponse<MessageResponse>> logout(@Valid @RequestBody LogoutRequest request) {
    MessageResponse response = authService.logout(request.refreshToken());
    return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
}
```

Catatan:

1. pakai `@Valid`,
2. gunakan wrapper `ApiSuccessResponse`,
3. response message konsisten: `"Logout successful"`.

### 6. Pertahankan error handling yang sudah ada
Tidak perlu exception baru khusus untuk token yang tidak ditemukan, karena behavior yang diinginkan adalah idempotent success.

Yang perlu tetap bekerja:

1. validasi request body ditangani `GlobalExceptionHandler`,
2. response error tetap mengikuti format project.

### 7. Tambah test controller
Tambahkan test minimal di `AuthControllerTest`:

1. `logoutReturnsWrappedSuccessResponse`
2. `logoutReturnsValidationErrorsWhenRefreshTokenBlank`

Detail assert yang disarankan:

1. status HTTP `200` untuk sukses,
2. `$.status == "success"`,
3. `$.message == "Logout successful"`,
4. `$.data.message == "Logout successful"`.

Untuk validasi:

1. status HTTP `400`,
2. `$.status == "error"`,
3. `$.message == "Validation failed"`,
4. `$.errors.refreshToken == "must not be blank"`.

### 8. Tambah test service atau repository flow
Kalau memungkinkan, tambahkan test yang memverifikasi:

1. saat token ada, token benar-benar dihapus,
2. saat token tidak ada, method tetap return sukses,
3. method tidak melempar exception untuk unknown token.

Kalau waktu sempit, prioritaskan:

1. controller test,
2. satu service test untuk case token ditemukan,
3. satu service test untuk case token tidak ditemukan.

## Feature B: Refresh Token Endpoint
## Definisi Perilaku
Jika client mengirim `refreshToken` yang valid dan belum expired, backend mengembalikan access token JWT baru.

Jika `refreshToken` tidak ditemukan atau expired:

1. backend return error,
2. backend tidak membuat access token baru,
3. frontend harus menganggap session perlu login ulang.

### Keputusan desain untuk issue ini
Agar implementasi tetap sederhana dan jelas, endpoint refresh pada issue ini:

1. **tidak merotasi refresh token**,
2. **tidak membuat refresh token baru**,
3. hanya membuat access token baru.

## Kontrak API yang Diusulkan
### Endpoint
`POST /auth/refresh`

### Request body
```json
{
  "refreshToken": "uuid-or-random-token-string"
}
```

### Validasi request
Field `refreshToken` wajib:

1. ada,
2. bertipe string,
3. tidak boleh blank.

### Response sukses
Saran paling jelas: buat DTO baru yang hanya mengembalikan access token, misalnya:

`AccessTokenResponse(String token)`

Contoh response:

```json
{
  "status": "success",
  "message": "Token refreshed successfully",
  "data": {
    "token": "new-jwt-access-token"
  }
}
```

### Response gagal
Jika refresh token tidak ditemukan atau sudah expired:

```json
{
  "status": "error",
  "message": "Invalid or expired refresh token",
  "errors": null
}
```

Saran HTTP status:

1. `401 Unauthorized` untuk refresh token invalid/expired.
2. `400 Bad Request` untuk body request invalid.

## Rencana Implementasi Detail
### 1. Tambah DTO request refresh
Buat DTO baru:

`src/main/java/com/fizu/authentication/controller/dto/RefreshTokenRequest.java`

Isi minimal:

```java
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
```

### 2. Tambah DTO response access token
Buat DTO baru agar kontrak response refresh lebih jelas:

`src/main/java/com/fizu/authentication/controller/dto/AccessTokenResponse.java`

Isi minimal:

```java
public record AccessTokenResponse(String token) {}
```

Jangan pakai `TokenResponse` bila endpoint refresh tidak mengembalikan refresh token baru, karena itu bisa membingungkan implementor frontend.

### 3. Tambah exception baru untuk refresh token invalid
Saran buat exception baru:

`InvalidRefreshTokenException`

Gunakan exception ini untuk dua kondisi:

1. refresh token tidak ditemukan,
2. refresh token sudah expired.

Tujuannya agar mapping error di `GlobalExceptionHandler` jelas dan tidak tercampur dengan login credential error.

### 4. Tambah method di `AuthService`
Tambahkan method baru:

```java
AccessTokenResponse refreshAccessToken(String refreshToken);
```

### 5. Implementasi refresh di `AuthServiceImpl`
Flow yang diinginkan:

1. cari row `refresh_token` berdasarkan token,
2. jika tidak ada, lempar `InvalidRefreshTokenException`,
3. cek `expiryDate`,
4. jika expired, hapus token dari database lalu lempar `InvalidRefreshTokenException`,
5. ambil `User` dari relasi refresh token,
6. generate JWT baru dengan `jwtUtil.generateToken(user.getEmail())`,
7. return access token baru.

Pseudo-flow:

```java
RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow(() -> new InvalidRefreshTokenException("Invalid or expired refresh token"));

if (storedToken.getExpiryDate().isBefore(LocalDate.now())) {
    refreshTokenRepository.delete(storedToken);
    throw new InvalidRefreshTokenException("Invalid or expired refresh token");
}

String accessToken = jwtUtil.generateToken(storedToken.getUser().getEmail());
return new AccessTokenResponse(accessToken);
```

### 6. Catatan validasi expiry
Di entity saat ini `expiryDate` bertipe `LocalDate`, bukan `Instant` atau `LocalDateTime`.

Artinya implementasi minimal yang aman:

1. anggap token expired jika `expiryDate` sebelum `LocalDate.now()`,
2. jangan mengubah tipe kolom pada issue ini kecuali memang diperlukan oleh tim.

Jika tim ingin presisi jam/menit, itu sebaiknya dijadikan issue terpisah.

### 7. Tambah endpoint di `AuthController`
Tambahkan endpoint:

```java
@PostMapping("/auth/refresh")
public ResponseEntity<ApiSuccessResponse<AccessTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    AccessTokenResponse response = authService.refreshAccessToken(request.refreshToken());
    return ResponseEntity.ok(ApiSuccessResponse.of("Token refreshed successfully", response));
}
```

### 8. Update `GlobalExceptionHandler`
Tambahkan mapping exception baru:

1. `InvalidRefreshTokenException` -> `401 Unauthorized`

Message yang dipakai:

`"Invalid or expired refresh token"`

### 9. Rapikan repository refresh token
Method repository yang disarankan setelah dua fitur ini:

```java
Optional<RefreshToken> findByToken(String token);
void deleteByToken(String token);
```

Itu sudah cukup untuk logout dan refresh.

### 10. Tambah test controller untuk refresh
Tambahkan test minimal:

1. `refreshReturnsWrappedSuccessResponse`
2. `refreshReturnsValidationErrorsWhenRefreshTokenBlank`
3. `refreshReturnsUnauthorizedWhenRefreshTokenInvalid`

Assert yang disarankan:

1. success -> status `200`, message `"Token refreshed successfully"`, `data.token` berisi token baru
2. validation -> status `400`, message `"Validation failed"`
3. invalid token -> status `401`, message `"Invalid or expired refresh token"`

### 11. Tambah test service untuk refresh
Tambahkan test minimal:

1. refresh token valid -> return access token baru
2. refresh token tidak ditemukan -> throw `InvalidRefreshTokenException`
3. refresh token expired -> token dihapus lalu throw `InvalidRefreshTokenException`

## Checklist Implementasi
- Buat `LogoutRequest`
- Buat `RefreshTokenRequest`
- Buat `AccessTokenResponse`
- Tambah `logout(String refreshToken)` di `AuthService`
- Tambah `refreshAccessToken(String refreshToken)` di `AuthService`
- Implementasikan logout di `AuthServiceImpl`
- Implementasikan refresh access token di `AuthServiceImpl`
- Rapikan method repository untuk pencarian/penghapusan refresh token
- Tambah endpoint `POST /auth/logout` di controller
- Tambah endpoint `POST /auth/refresh` di controller
- Tambah exception `InvalidRefreshTokenException`
- Update `GlobalExceptionHandler` untuk exception refresh token
- Tambah test sukses
- Tambah test validasi request
- Tambah test penghapusan token di service/repository
- Tambah test refresh token valid/invalid/expired
- Pastikan semua test existing tetap hijau

## Acceptance Criteria
Issue dianggap selesai jika:

1. Ada endpoint `POST /auth/logout`.
2. Ada endpoint `POST /auth/refresh`.
3. Kedua endpoint menerima body dengan field `refreshToken`.
4. Jika `refreshToken` valid dan ada di DB, endpoint logout menghapus row token.
5. Jika `refreshToken` sudah tidak ada, endpoint logout tetap return sukses.
6. Jika `refreshToken` valid dan belum expired, endpoint refresh mengembalikan access token baru.
7. Jika `refreshToken` tidak ditemukan atau expired, endpoint refresh return `401` dengan message yang benar.
8. Jika `refreshToken` blank/kosong, kedua endpoint return `400` dengan format error yang benar.
9. Response sukses memakai `ApiSuccessResponse`.
10. Tidak ada perubahan perilaku yang merusak endpoint login/register/verify yang sudah ada.
11. Test baru ditambahkan dan lulus.

## Contoh Skenario QA Manual
### Skenario 1: logout berhasil
1. Login terlebih dahulu.
2. Simpan nilai `refreshToken` dari response login.
3. Pastikan token tersebut ada di tabel `refresh_token`.
4. Panggil `POST /auth/logout` dengan token itu.
5. Pastikan response sukses.
6. Pastikan row token tersebut sudah hilang dari tabel `refresh_token`.

### Skenario 2: logout diulang
1. Panggil `POST /auth/logout` lagi dengan token yang sama.
2. Pastikan tetap mendapat response sukses yang sama.

### Skenario 3: request invalid
1. Kirim body `{ "refreshToken": "" }`.
2. Pastikan status `400`.
3. Pastikan field error `refreshToken` muncul.

### Skenario 4: refresh token berhasil
1. Login terlebih dahulu.
2. Ambil nilai `refreshToken` dari response login.
3. Panggil `POST /auth/refresh` dengan token tersebut.
4. Pastikan response sukses.
5. Pastikan field `data.token` berisi access token JWT baru.

### Skenario 5: refresh token invalid
1. Panggil `POST /auth/refresh` dengan token acak yang tidak ada di DB.
2. Pastikan status `401`.
3. Pastikan message `"Invalid or expired refresh token"`.

### Skenario 6: refresh token expired
1. Siapkan refresh token yang `expiry_date` sudah lewat.
2. Panggil `POST /auth/refresh`.
3. Pastikan status `401`.
4. Pastikan token expired tersebut dihapus dari DB bila implementasi mengikuti cleanup saat refresh.

## Catatan untuk Programmer
Beberapa batasan penting agar implementasi tidak melebar:

1. Jangan menambahkan blacklist access token pada issue ini.
2. Jangan mengubah format response global.
3. Jangan mengubah kontrak endpoint login yang sudah ada.
4. Jangan lakukan refresh token rotation kecuali requirement berubah.
5. Fokuskan perubahan pada DTO, controller, service, repository, exception, dan test.

## Estimasi Breakdown Task
Estimasi kasar untuk junior programmer:

1. Analisis codebase: 15-30 menit
2. DTO + exception + service interface + controller: 30-45 menit
3. Implementasi service + repository: 30-60 menit
4. Menulis test: 45-90 menit
5. Debugging + retest: 20-45 menit

Total estimasi: sekitar 2 sampai 4.5 jam, tergantung familiaritas dengan Spring Boot test dan JPA repository.
