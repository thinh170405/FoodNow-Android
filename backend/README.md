# Backend FoodNow

## Tài khoản tài xế

Admin tạo tài khoản bằng `POST /api/admin/drivers` với `name`, `email`,
`password`; đăng ký khách không thể tự chọn role `driver`. Tài xế đăng nhập
qua `POST /api/auth/delivery-login`, dùng `GET /api/driver/orders` để xem đơn
đang giao và `/api/driver/orders/{id}/messages` hoặc `/location` để chat/chia
sẻ vị trí. Các route này chỉ nhận role `driver`. Chưa có bảng phân công tài xế;
mọi tài xế được cấp quyền đều thấy tất cả đơn đang giao.

## Cloud Firestore hiện tại

Chạy `start.ps1` để dùng Cloud Firestore. Khóa Firebase Admin được trỏ bằng
`GOOGLE_APPLICATION_CREDENTIALS` hoặc file `firestore-credentials.path` chứa
đường dẫn tới tệp JSON nằm ngoài project. Server tải các collection vào bộ nhớ
lúc khởi động và ghi thay đổi vào Firestore trước khi trả lời API. Chỉ chạy một
tiến trình backend; nếu thay đổi Firestore trực tiếp, hãy khởi động lại server.

`data/foodnow.db` là bản SQLite trước chuyển đổi, không còn được server mặc
định sử dụng. `migrate_to_firestore.py` chỉ chạy một lần khi Firestore trống.
Tham số `--db` của `server.py` dành cho test SQLite độc lập. Bản sao SQLite
cũ trong `backups/` không tự cập nhật theo Firestore; cần xuất dữ liệu cloud
riêng khi muốn sao lưu phiên bản mới.

Tại project, tạo môi trường `python -m venv backend/.venv`, cài bằng `backend/.venv/Scripts/python.exe -m pip install -r backend/requirements.txt`, rồi chạy `backend/start.ps1`. Python 3.11+, Pillow xử lý ảnh, Firebase Admin xác minh đăng nhập Google và gửi FCM khi có cấu hình. Xem `docs/SETUP_FIREBASE_MAPS.md`. Website/API cùng cổng, Firestore là nơi lưu dữ liệu chính. Admin ngẫu nhiên khởi tạo lần đầu lưu trong `data/admin-credentials.txt`; tệp không được phục vụ qua web.

## API module di động bổ sung

| Method / path | Chức năng |
|---|---|
| POST /api/auth/firebase | idToken; xác minh bằng Firebase Admin; không tự liên kết email tài khoản cũ |
| POST /api/me/avatar | data: base64 ảnh; chuẩn hóa và lưu ảnh đại diện |
| POST /api/devices | token FCM, gắn tài khoản và phiên hiện tại |
| GET /api/notifications | 50 sự kiện gần nhất của khách hiện tại |
| GET/POST /api/orders/{id}/messages | Khách đọc/gửi body và clientKey theo đơn của mình |
| GET/POST /api/admin/orders/{id}/messages | Quản trị đọc/trả lời chat |
| GET /api/orders/{id}/location | Vị trí giao hàng, accuracy, updated_at và trạng thái đơn |
| GET/POST /api/admin/orders/{id}/location | Quản trị đọc/cập nhật latitude, longitude, accuracy khi đang giao |

Database tự bổ sung users.avatar và các bảng order_messages, delivery_positions, device_tokens, firebase_accounts, notification_events. Tin nhắn chống trùng theo sender/clientKey. FCM chỉ gửi tới token gắn phiên còn hiệu lực; đăng xuất/thu hồi phiên ngừng gửi tới phiên đó. Vị trí là GPS thật gửi khi màn hình nhân viên đang mở; chưa có hệ thống tài xế độc lập.

## Database

| Bảng | Dữ liệu / quan hệ |
|---|---|
| users | Email duy nhất, tên, hash mật khẩu có salt, role, điện thoại, địa chỉ |
| sessions | Hash bearer token, user_id → users, thời hạn |
| restaurants | Tên, địa chỉ, giờ mở cửa, ảnh, active |
| foods | restaurant_id → restaurants, tên, giá VND, ảnh, active |
| orders | user_id → users, restaurant_id → restaurants, giao hàng, thanh toán, tổng tiền, trạng thái, thời gian, client_key duy nhất theo người dùng |
| order_items | order_id → orders, food_id → foods, tên/giá thời điểm đặt, số lượng |

Chỉ mục SQLite trong bộ nhớ dùng foreign keys; thay đổi được ghi lên Firestore theo batch trước khi trả lời API. Một request lưu cả đơn và các món. Server tính tổng, trả 409 nếu expectedTotal không khớp. Ẩn thay vì xóa món/nhà hàng để giữ lịch sử. Trạng thái: pending → confirmed → delivering → completed; hủy chỉ từ pending/confirmed.

## API

JSON UTF-8. API riêng tư dùng `Authorization: Bearer <token>`. Lỗi có trường error: 400 dữ liệu sai, 401 hết phiên, 403 thiếu quyền, 404 không tìm thấy, 409 xung đột, 429 quá nhiều lần đăng nhập.

| Method / path | Chức năng / body |
|---|---|
| GET /api/health | Kiểm tra server |
| POST /api/auth/register | name, email, password |
| POST /api/auth/login | email, password → token, user |
| POST /api/auth/logout | Thu hồi phiên |
| POST /api/auth/password | current, replacement → thu hồi mọi phiên |
| GET /api/me | Hồ sơ |
| PATCH /api/me | name, phone, address |
| GET /api/catalog | Nhà hàng/món đang bán, deliveryFee |
| GET /api/orders | Đơn của người đăng nhập |
| POST /api/orders | clientKey, restaurantId, recipient, phone, address, note, paymentMethod, expectedTotal, items[{foodId,quantity}] |
| PATCH /api/orders/{id} | status: hủy/nhận đơn của mình |
| GET /api/admin/summary | Thống kê |
| GET /api/admin/users | Hồ sơ, không trả password_hash |
| GET /api/admin/catalog | Tất cả nhà hàng/món kể cả ẩn |
| POST /api/admin/restaurants | name, address, hours, image, active |
| PATCH /api/admin/restaurants/{id} | Cùng các trường tạo |
| POST /api/admin/foods | name, restaurant_id, price, image, active |
| PATCH /api/admin/foods/{id} | Cùng các trường tạo |
| GET /api/admin/orders | Tất cả đơn |
| PATCH /api/admin/orders/{id} | status: chuyển trạng thái hợp lệ |

Admin routes bắt buộc role admin. Đăng ký luôn tạo customer dù request gửi role khác. paymentMethod: cod, wallet_demo, bank_demo. `POST /api/admin/images` nhận JSON `{data: base64}` của ảnh JPG/PNG/WebP tối đa 5 MB; trả `{image,url}`. Dùng khóa `image` khi tạo/sửa món. Server kiểm tra nội dung, chuẩn hóa JPEG tối đa 1600 px và lưu BLOB vào bảng uploaded_images. `GET /uploads/upload_<sha256>.jpg` phục vụ ảnh công khai cho web/Android, không trả thông tin tài khoản. Ảnh nhà hàng vẫn dùng danh sách có sẵn.

Đơn có thể gửi thêm `deliveryLatitude` và `deliveryLongitude`: cả hai là số trong [-90,90] và [-180,180], hoặc cùng null/bỏ qua. API trả `delivery_latitude` và `delivery_longitude`; không lấy tọa độ mặc định khi người dùng chưa chọn. Server tự migrate bảng orders của database cũ bằng hai cột REAL nullable. Chi tiết đơn trên web cung cấp link OpenStreetMap với đúng tọa độ đã lưu.

## Sao lưu

Lệnh `python backend/backup.py` chỉ sao lưu file SQLite cũ, không sao lưu Firestore. SQLite backup API tạo bản sao nhất quán tại `backend/backups/foodnow-<thời-gian>.db`. Để kiểm tra bản sao, dừng server rồi chạy `python backend/server.py --db <đường-dẫn-bản-sao>`; dữ liệu gốc không bị thay đổi. Không thay/xóa database hoặc WAL/SHM khi server đang mở.

## Demo thuyết trình

1. Android đăng ký/đăng nhập, sửa hồ sơ.
2. Web thêm món; mở lại tab Trang chủ trên Android để thấy món.
3. Android đặt hàng; web thấy đúng người nhận/tổng tiền.
4. Web xác nhận và chuyển đang giao; mở lại tab Đơn hàng trên Android rồi xác nhận nhận hàng.
5. Web xem hoàn thành/thống kê; đóng/mở server để chứng minh dữ liệu còn lưu.

Các test dùng database tạm riêng. Admin admin@test.local/Admin12345 chỉ có trên fixture 8081, không tạo trong database demo chính.


