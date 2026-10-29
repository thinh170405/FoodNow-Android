# Kết nối Firebase và Google Maps cho FoodNow

Package Android: `com.example.project1`. Project Firebase đã nhận: `foodnow-44d00`.

## 1 Firebase Authentication và đăng nhập Google

1. Firebase Console → Project settings → Your apps → chọn Android đúng package.
2. Add fingerprint, nhập SHA-1 bản debug:
   `D5:E6:C1:6C:DC:50:19:09:47:12:B0:8B:CC:3F:E1:3D:E4:DE:22:3C`
3. SHA-256 nếu cần:
   `47:AE:81:3B:45:20:67:8F:87:AB:E5:8C:9C:20:2B:F5:19:61:08:0E:45:F0:CB:54:BA:BC:AD:99:DA:E8:9C:49`
4. Authentication → Sign-in method → Google → Enable → chọn email hỗ trợ → Save.
5. Tải lại `google-services.json`, thay tệp ở `app/google-services.json` và build lại APK.

Gradle đọc cấu hình đúng package và tạo resource cho Firebase. Cần có OAuth client loại 3 (web client). Nút Google dùng Credential Manager → Firebase Authentication → gửi Firebase ID token cho máy chủ xác minh bằng Admin SDK. Không chỉ gửi email để đăng nhập.

Email trùng tài khoản FoodNow đã có sẽ bị từ chối liên kết tự động; dùng mật khẩu FoodNow cho tài khoản đó. Tài khoản Google mới có vai trò customer. Mật khẩu Google được quản lý tại Google.

## 2 Firebase Admin trên máy chủ

1. Firebase Console → Project settings → Service accounts → Firebase Admin SDK → Generate new private key.
2. Lưu trên máy chạy backend, ví dụ `backend/service-account.json`. Đây là khóa riêng: không đưa vào APK, báo cáo, ảnh chụp, chat hoặc Git.
3. Tạo môi trường riêng và cài thư viện để không ảnh hưởng các project Python khác:

```powershell
python -m venv backend/.venv
backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt
```
4. Trong PowerShell, trỏ đến tệp vừa tải rồi khởi động máy chủ:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = (Resolve-Path 'backend/service-account.json').Path
backend/.venv/Scripts/python.exe backend/server.py --host 0.0.0.0 --port 8080
```

Service account phải thuộc cùng project với cấu hình Android. Máy chủ dùng để kiểm tra ID token và gửi FCM. Nếu Google Cloud yêu cầu, bật Firebase Cloud Messaging API (HTTP v1). Không dùng khóa server legacy.

## 3 Google Maps

1. Google Cloud Console → chọn project FoodNow → bật Maps SDK for Android.
2. Liên kết billing nếu quyết định dùng Google Maps; tự kiểm tra điều khoản và hạn mức của tài khoản trước khi bật.
3. Tạo API key. Application restrictions → Android apps → package trên và SHA-1 debug trên. API restrictions → chỉ Maps SDK for Android.
4. Sao chép `services.properties.example` thành `services.properties` ở gốc project, điền `MAPS_API_KEY=...`.
5. Build lại APK. Khi có khóa, app dùng Google Maps SDK; khi chưa có, app giữ bản đồ OpenStreetMap hiện tại. Có key nhưng chưa bật API/billing hoặc sai restriction có thể khiến nền Google Maps trống.

## 4 Kiểm tra chức năng sau cấu hình

- Google: đăng nhập một tài khoản Google chưa trùng email FoodNow; kiểm tra hồ sơ và đặt đơn. Không coi build thành công là kiểm thử đăng nhập cloud thành công.
- Thông báo: Hồ sơ → Bật thông báo đơn hàng → cho phép Android 13+. Đổi trạng thái đơn hoặc trả lời chat từ web. Với Firebase Admin và FCM hợp lệ, thông báo có thể đến khi app ở nền; khi app đang mở, cũng có kiểm tra sự kiện định kỳ. Force-stop app có thể ngăn nhận FCM đến khi mở lại.
- GPS: Thanh toán → Chọn điểm giao → Dùng vị trí hiện tại → cho phép vị trí. Có thể chọn gần đúng; kiểm tra lại ghim và địa chỉ trước khi xác nhận.
- Camera: Hồ sơ → Chụp ảnh đại diện → chụp và xác nhận → ảnh được nén, gửi máy chủ và lưu hồ sơ. Hủy chụp giữ ảnh cũ.
- Chat: Lịch sử đơn → Nhắn tin về đơn hàng. Trên web → Chi tiết đơn → Nhắn tin với khách. Tin lưu SQLite; màn hình đang mở cập nhật khoảng 4 giây.
- Giao hàng: quản trị đổi đơn sang Đang giao. Quản trị tạo tài khoản tài xế trên web; trên điện thoại chọn Đăng nhập tài xế → chọn đơn đang giao. Hiện mọi tài xế được cấp quyền đều thấy các đơn đang giao, chưa có phân công riêng. Người giao phải giữ màn hình chia sẻ mở; thoát màn hình sẽ dừng GPS. Khách mở Theo dõi người giao hàng; vị trí cập nhật khoảng 5 giây, có ghi thời gian dữ liệu cũ. Không dùng tọa độ giả.

## Nguồn hướng dẫn

- https://firebase.google.com/docs/auth/android/google-signin
- https://firebase.google.com/docs/admin/setup
- https://firebase.google.com/docs/cloud-messaging/android/get-started
- https://developers.google.com/maps/documentation/android-sdk/get-api-key

Google Sign-In đáp ứng phần tích hợp tài khoản mạng xã hội. Facebook Login chưa được triển khai và cần Meta App riêng nếu giảng viên yêu cầu cụ thể Facebook.

