# Cập nhật ngày 29/10/2026

Đã cài APK mới và chụp các màn hình khách hàng, tài xế trên thiết bị, cùng trang quản trị web. Báo cáo đầy đủ nằm tại `reports/FoodNow_Bao_Cao_Tat_Ca_Man_Hinh_523H0180_Kieu_Huu_Thinh.docx`. Lệnh `:app:assembleDebug`, `:app:lintDebug` và `:app:testDebugUnitTest` chạy thành công. Dữ liệu thử và cấu hình trên điện thoại đã được hoàn nguyên sau khi chụp. Các ghi chép bên dưới là lịch sử kiểm thử ở thời điểm trước đó.

# Kết quả kiểm tra — 09/10/2026

## Đăng nhập tài xế riêng — 28/10/2026

- Role `driver` được tạo qua API admin/web; tài khoản khách/admin bị từ chối ở trang đăng nhập tài xế, tài xế bị từ chối ở trang đăng nhập khách.
- Test backend `test_mobile_features.py`: 14 tests PASS, gồm tạo tài xế, đăng nhập, phân quyền, chỉ xem đơn đang giao, chat và cập nhật vị trí.
- Trên Firestore thật đã thử tạo tài xế tạm, đăng nhập, kiểm tra quyền và xóa dữ liệu thử.
- Android `assembleDebug`, `lintDebug`, `testDebugUnitTest`: BUILD SUCCESSFUL. Chưa có thiết bị ADB để kiểm tra giao diện thực tế.
- Kiểm tra cú pháp web bằng `node --check` đạt. Playwright web test chưa chạy được vì máy không có module `playwright`.

## Chuyển FoodNow sang Cloud Firestore — 28/10/2026

- Tạo Firestore tại `asia-southeast1`; sao lưu SQLite trước khi sao chép.
- Đối chiếu 12 bảng/collection: mọi bản ghi và trường dữ liệu từ SQLite khớp Firestore sau khi nhập.
- Backend Firestore chạy tại cổng 8080; `/api/health` trả `database: Firestore`.
- Smoke test thực tế trên Firestore: đọc catalog 4 nhà hàng/28 món, đăng nhập admin, đọc summary/đơn, đăng ký khách thử, đăng nhập, tạo và đọc đơn, đăng xuất. Dữ liệu thử đã xóa.
- Tải ảnh thử 1.353.781 byte thành công, lưu thành 3 document chunk trong Firestore và đọc lại qua HTTP; ảnh thử đã xóa.
- Bộ test SQLite độc lập còn đạt: `test_server.py` 8 tests, `test_mobile_features.py` 14 tests. Chưa chạy UI Android thực tế sau chuyển đổi.
- Backend dùng chỉ mục SQLite **trong bộ nhớ** để giữ nguyên API cũ, Firestore là nơi lưu bền vững. Chỉ hỗ trợ một tiến trình backend và không đồng bộ thay đổi trực tiếp từ Firebase Console khi server đang chạy.

## Module di động bổ sung — 15/10/2026

- Bản cuối: `assembleDebug lintDebug testDebugUnitTest` thành công. Không có thiết bị ADB kết nối; chưa chạy thực tế GPS, camera, Google Sign-In, FCM và Google Maps của bản này trên điện thoại.
- `python -m unittest discover -s backend -p test_mobile_features.py`: 13 test đạt, gồm 8 test nền và 5 test mới về phân quyền chat, gửi lại tin không trùng, sự kiện thông báo, tọa độ giao hàng, trạng thái đơn, lưu sau khởi tạo lại, avatar, thiếu cấu hình Firebase và thu hồi phiên thiết bị. Test Firebase dùng mock kết quả xác minh để kiểm tra tạo customer, từ chối email chưa xác minh, không tự liên kết email trùng và từ chối token lỗi; không thay thế thử dịch vụ thật.
- `backend/test_chat.cjs` chạy Playwright/Edge với database tạm cổng 8082: khách gửi tin, web quản trị đọc/trả lời, khách nhận tin và sự kiện thông báo; nội dung HTML hiển thị dạng văn bản, không có lỗi JavaScript. Không chụp thêm ảnh.
- `app/google-services.json` đúng package `com.example.project1`, có Android OAuth và Web OAuth. Chưa có `services.properties` chứa Maps API key hoặc Firebase Admin service account. Các kết quả trên không xác nhận dịch vụ cloud đã hoạt động.
- Chia sẻ vị trí chỉ khi màn hình giao hàng đang mở, dùng vai trò quản trị; chưa có hệ thống phân công tài xế riêng. Facebook và thanh toán thật chưa triển khai.


## Tải ảnh món ăn — 13/10/2026

- Build APK debug và lint đạt. Chưa chạy phần tải ảnh Android trên thiết bị trong lượt này; không có thiết bị ADB kết nối.
- 8 nhóm test backend đạt, có phân quyền upload, tệp không hợp lệ, chuẩn hóa PNG thành JPEG, giới hạn kích thước đầu ra, ảnh trùng, lưu khóa ảnh vào món, đọc ảnh qua HTTP và giữ ảnh khi khởi tạo lại database.
- `backend/test_upload.cjs` chạy trên Edge đạt: chọn tệp/xem trước, tạo món, sửa ảnh, giữ ảnh khi lưu lại không đổi, lấy ảnh JPEG và kiểm tra API catalog cho Android. Test dùng database tạm riêng; ảnh kiểm thử không được đưa vào database demo.
- Database demo đã sao lưu trước khi chạy phiên bản server mới. Thư viện Pillow được khai báo trong backend/requirements.txt.

## Bổ sung bản đồ — 12/10/2026

- APK debug và lint build thành công sau khi thêm DeliveryMapActivity/checkout/lịch sử đơn.
- Backend: 7 nhóm test đạt, gồm kiểm tra tọa độ ngoài phạm vi, NaN/Infinity/chuỗi/bool, thiếu một tọa độ, tọa độ (0,0), không ghim, lưu/đọc tọa độ qua API khách và admin, chống trùng đơn, migrate database cũ hai lần vẫn giữ dữ liệu.
- Web Playwright/Edge: chi tiết đơn chứa link OpenStreetMap đúng tọa độ; các luồng admin cũ vẫn đạt. Ảnh tại backend/data/order-map-link.png.
- Map HTML đã tải tile thật, chạm/kéo ghim và chụp ảnh tại backend/data/delivery-map.png. Những lần kiểm thử lại gặp ERR_CONNECTION_REFUSED từ tile.openstreetmap.org; toàn bộ test_map.cjs chưa đạt ổn định. Không coi kết quả này là xác nhận dịch vụ tile luôn truy cập được.
- Đã bổ sung và biên dịch test Android cho chọn ghim/đơn hàng, khôi phục màn hình và hủy chọn. Chưa chạy xong trên máy ảo trong lượt này vì giới hạn bộ nhớ và khởi động máy ảo; chưa kiểm thử tính năng bản đồ trên máy thật. Kết quả Android bên dưới thuộc bản trước tính năng bản đồ.
- Database demo đã được sao lưu trước khi khởi động lại server và migrate. Kiểm tra integrity_check trả ok.

## Kết quả bản trước tính năng bản đồ

- Android: `assembleDebug`, `lintDebug`, `testDebugUnitTest` hoàn thành thành công. Lint không có lỗi; còn các warning, xem `app/build/reports/lint-results-debug.html`.
- Backend: 5 test tích hợp HTTP/SQLite đạt. Kiểm tra đăng ký trùng, đăng nhập sai, role admin/customer, cách ly đơn, thu hồi phiên, giá và món ngừng bán, idempotency, snapshot hóa đơn, chuyển trạng thái, khởi tạo lại database không mất dữ liệu, ngăn truy cập tệp nội bộ.
- Android emulator SDK 37 (`emulator-5554`): `BackendIntegrationTest` đạt qua ADB instrumentation (41,137 giây). Đăng nhập qua giao diện, mở Circle K, thêm cà phê, đặt đơn 64.000 VND, đọc database qua API, kiểm tra giỏ đã xóa, hủy đơn, đăng nhập lại và kiểm tra lịch sử/hồ sơ.
- Web qua Playwright/Edge: đăng nhập admin, nhìn thấy đơn do Android tạo/hủy, thêm món và kiểm tra API catalog, tạo đơn khách rồi chuyển confirmed/delivering/completed qua web, kiểm tra API của khách nhận trạng thái mới, đăng xuất. Không có lỗi JavaScript; bố cục kiểm tra tại 1440×1000 và 390×844.
- Sao lưu SQLite bằng `backup.py` đã tạo bản sao thành công.

Các test dữ liệu dùng database tạm riêng trên cổng 8081, không làm thay đổi dữ liệu demo cổng 8080. APK cuối đã build lại sau các sửa nhỏ về màn hình thử lại quyền mạng và xử lý lỗi tải nhà hàng.

Đã bổ sung quyền mạng nội bộ cho target SDK 37. Android kết nối server kiểm thử trực tiếp qua 10.0.2.2:8081. Máy chủ dùng HTTP/1.1 để tránh lỗi đóng luồng phản hồi gặp khi kiểm thử máy ảo.

Gradle `connectedDebugAndroidTest --offline` không chạy được do thiếu dependency UTP trong cache; test APK đã build, cài và thực thi bằng `adb shell am instrument`. Đây là kiểm thử thực tế trên máy ảo, không phải chỉ kiểm tra biên dịch. Chưa kiểm tra trên điện thoại vật lý.

Ví điện tử/chuyển khoản còn mô phỏng; chưa triển khai server Internet, khôi phục mật khẩu qua email hoặc tích hợp cổng thanh toán thật.

## Cập nhật cấu hình Maps

Đã nhận và lưu Maps API key trong services.properties (được gitignore), rồi yêu cầu build lại APK. Chưa xác minh key được Google chấp nhận, API/billing/restriction trên Cloud hoặc hiển thị bản đồ thực tế; không có thiết bị ADB kết nối. Thông tin thiếu key trong các kết quả trước là trạng thái trước lần cập nhật này.

## Tinh chỉnh UI FoodNow

Đồng bộ màu, nút Material bo góc, ô nhập có trạng thái focus, thẻ nhà hàng/món/đơn và nhãn trạng thái. Rút ngắn hiệu ứng mở màn hình, giữ danh sách cũ khi cập nhật nền và chặn yêu cầu tải catalog trùng. assembleDebug, lintDebug và testDebugUnitTest đạt; lint còn warning. Không có thiết bị ADB để kiểm tra trực quan/độ mượt thực tế. Không chụp thêm ảnh ứng dụng.

## Báo cáo cập nhật 16/10/2026

Đã chụp lại Android từ đăng nhập đến đặt đơn 89.000 VND và lịch sử trên RMX3701 với APK giao diện mới; database minh họa riêng cổng 8083. GPS trả tọa độ, nhưng nền Google Maps trống; chưa có log xác định nguyên nhân. Không đưa tọa độ GPS hiện tại vào báo cáo. Web quản trị và chat đã chụp lại. Báo cáo 23 trang đã render và kiểm tra từng trang; ba ảnh xem điểm giao/hồ sơ/đổi mật khẩu giữ từ bản trước, có chú thích. Camera, chat Android và giao hàng chưa chụp lại. Điện thoại ngắt USB; người dùng chọn tiếp tục báo cáo trước. Cấu hình điện thoại vẫn là server kiểm thử 127.0.0.1:8083 và tài khoản demo; bản sao trước chụp ở reports/work/before-new-report-prefs.tar chưa được khôi phục.

## Kiểm tra khôi phục server — 22/10/2026

- assembleDebug, lintDebug, testDebugUnitTest: BUILD SUCCESSFUL. 3 unit tests qua; lint không có lỗi chặn build.
- backend/.venv/Scripts/python.exe -m unittest discover -s backend -p test_mobile_features.py: 13 tests PASS trên database tạm.
- Khôi phục API/remote_session, ảnh server, Google/Firebase, FCM, chat và vị trí giao hàng; bỏ LocalDatabase khỏi app.
- Database/backend/web hiện hữu không bị sửa/xóa bởi tác vụ khôi phục.
- ADB không có thiết bị: chưa cài hoặc kiểm thử UI bản khôi phục trên điện thoại. Chưa xác nhận Google Maps/Firebase với cấu hình cloud thật.

## Đăng nhập giao hàng — 22/10/2026

- assembleDebug + lintDebug + testDebugUnitTest: PASS (3 unit tests).
- test_mobile_features.py: 14 tests PASS, gồm kiểm tra đăng nhập giao hàng từ chối customer, sai mật khẩu không tạo phiên, admin được phép và đăng xuất thu hồi phiên.
- Chưa chạy UI trên thiết bị: adb devices không có máy kết nối.
- Form giao hàng dùng quyền admin hiện hữu, chưa có role driver/phân công đơn độc lập. Cần restart backend để nạp /api/auth/delivery-login.

## Giao diện giao hàng mới — 22/10/2026

DeliveryActivity dùng activity_delivery.xml và item_delivery_order.xml: tiêu đề, lời chào, số đơn đang giao, trạng thái trống/loading/lỗi, danh sách thẻ đơn. Nút Cập nhật lấy dữ liệu thật; thẻ có địa chỉ, người nhận, tổng tiền, chia sẻ GPS, bản đồ, chat và chi tiết. Không đổi quyền admin hay tự tạo đơn mẫu. Build assembleDebug và lintDebug PASS. Điện thoại ngắt ADB trước bước cài, chưa kiểm tra trực quan bản UI mới trên thiết bị.

