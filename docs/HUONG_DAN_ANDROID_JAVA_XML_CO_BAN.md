# Hướng dẫn lập trình di động cơ bản: Android Java/XML với FoodNow

**Đối tượng:** người mới học Android. **Kết quả:** hiểu cách tạo màn hình, chuyển màn hình, dùng Fragment và RecyclerView, kiểm tra dữ liệu nhập, gọi API mà không làm treo giao diện. Ví dụ lấy từ module `app/` của FoodNow. Cấu hình Firebase của FoodNow được mô tả trong [hướng dẫn thiết lập](SETUP_FIREBASE_MAPS.md).

## Bài 1 — Chạy project và nhận biết cấu trúc

1. Mở thư mục gốc của project bằng Android Studio, chờ Gradle Sync hoàn tất.
2. Chọn run configuration **`app`** và một máy ảo Android.
3. Trong PowerShell ở thư mục project, chạy `backend/start.ps1`. FoodNow cần backend để đăng nhập, tải thực đơn và đặt hàng.
4. Chạy `app` trên máy ảo. Bản debug mặc định gọi backend ở `http://10.0.2.2:8080` (`10.0.2.2` là đường tới máy host từ Android Emulator; `localhost` trong máy ảo là chính máy ảo).

Các thư mục cần nhớ:

| Vị trí | Vai trò |
|---|---|
| `app/src/main/java/com/example/project1/` | Mã Java: Activity, Fragment, Adapter, model, API. |
| `app/src/main/res/layout/` | Giao diện XML của màn hình và từng dòng danh sách. |
| `app/src/main/res/drawable/`, `values/` | Hình, màu, chuỗi, style. |
| `app/src/main/AndroidManifest.xml` | Khai báo Activity, quyền Internet/vị trí/thông báo. |
| `backend/` | REST API và web quản trị; Android không truy cập database trực tiếp. |

**Tự kiểm tra:** mở [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml), tìm Activity nào là màn hình khởi động. Đáp án: `SignInActivity` có `MAIN` và `LAUNCHER`.

## Bài 2 — Tạo màn hình với XML và Activity

Android thường tách **giao diện** khỏi **xử lý sự kiện**. Ví dụ màn hình đăng nhập gồm [`activity_signin.xml`](../app/src/main/res/layout/activity_signin.xml) và [`SignInActivity.java`](../app/src/main/java/com/example/project1/SignInActivity.java).

Một thành phần XML cần `id` nếu Java phải tìm đến nó:

```xml
<EditText
    android:id="@+id/txtEmail"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Email"
    android:inputType="textEmailAddress" />
```

Trong Activity:

```java
@Override
public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_signin);

    EditText email = findViewById(R.id.txtEmail);
    findViewById(R.id.btnSignIn).setOnClickListener(view -> {
        String value = email.getText().toString().trim();
        if (value.isEmpty()) email.setError("Nhập email");
    });
}
```

FoodNow dùng `screen(R.layout.activity_signin)` trong `BaseActivity` thay cho `setContentView(...)`; ý nghĩa chọn layout là như nhau. Chỉ dùng `findViewById` **sau** khi đã gắn layout. `R.id.txtEmail` phải khớp `android:id` trong XML.

**Thực hành:** đổi chữ gợi ý của ô email trong XML, chạy lại app, kiểm tra thay đổi. Sau đó thêm một `TextView` thông báo bên dưới nút đăng nhập.

## Bài 3 — Chuyển màn hình và truyền dữ liệu bằng Intent

Từ danh sách nhà hàng, FoodNow mở màn hình chi tiết bằng `Intent`:

```java
Intent intent = new Intent(requireContext(), RestaurantActivity.class);
intent.putExtra("restaurantId", restaurant.id);
startActivity(intent);
```

Màn hình nhận đọc ID, kiểm tra nó rồi tìm dữ liệu tương ứng:

```java
int id = getIntent().getIntExtra("restaurantId", -1);
int index = DemoData.indexOfId(id);
if (index < 0) {
    Toast.makeText(this, "Nhà hàng không còn phục vụ", Toast.LENGTH_LONG).show();
    finish();
    return;
}
```

Truyền **ID** thuận tiện hơn truyền cả object lớn. Luôn dùng giá trị mặc định và kiểm tra trường hợp ID không còn tồn tại. Activity đích phải được khai báo trong manifest; [`RestaurantActivity`](../app/src/main/java/com/example/project1/RestaurantActivity.java) đã được khai báo.

**Thực hành:** mở [`RestaurantFragment.java`](../app/src/main/java/com/example/project1/RestaurantFragment.java), tìm đoạn `putExtra("restaurantId", ...)` và đối chiếu với đoạn `getIntExtra(...)` ở Activity đích.

## Bài 4 — Fragment, tab và RecyclerView

[`MainActivity.java`](../app/src/main/java/com/example/project1/MainActivity.java) có ba tab: **Trang chủ**, **Đơn hàng**, **Hồ sơ**. Khi chọn tab, Activity thay Fragment trong `FrameLayout` có ID `layoutContainer`:

```java
Fragment fragment = index == 1 ? new OrderFragment()
        : index == 2 ? new ProfileFragment()
        : new RestaurantFragment();
getSupportFragmentManager().beginTransaction()
        .replace(R.id.layoutContainer, fragment)
        .commit();
```

Fragment là phần giao diện có thể đặt bên trong Activity. `RestaurantFragment` nhận layout `fragment_restaurant.xml`, còn `OrderFragment` và `ProfileFragment` hiển thị hai tab còn lại.

Danh sách nhà hàng dùng **RecyclerView**. Ba phần làm việc với nhau:

1. `fragment_restaurant.xml` chứa `RecyclerView` có ID `rvRestaurants`.
2. [`RestaurantAdapter.java`](../app/src/main/java/com/example/project1/RestaurantAdapter.java) tạo một dòng từ `row_restaurant.xml`, gán tên/địa chỉ/ảnh vào ViewHolder.
3. `RestaurantFragment` cấp danh sách và xử lý khi người dùng bấm vào một dòng.

```java
RecyclerView list = view.findViewById(R.id.rvRestaurants);
list.setLayoutManager(new LinearLayoutManager(requireContext()));
list.setAdapter(new RestaurantAdapter(restaurants, restaurant -> {
    Intent intent = new Intent(requireContext(), RestaurantActivity.class);
    intent.putExtra("restaurantId", restaurant.id);
    startActivity(intent);
}));
```

`RecyclerView` tái sử dụng ViewHolder khi cuộn, phù hợp danh sách dài. Với dữ liệu thay đổi thường xuyên, nên cập nhật danh sách có kiểm soát thay vì tạo lại Adapter ở mọi lần gõ; FoodNow hiện tạo lại Adapter khi lọc tìm kiếm, đủ đơn giản cho bài học nhỏ.

**Thực hành:** thêm một nhà hàng trong web quản trị, tải lại tab Trang chủ và tìm nó bằng ô tìm kiếm. Đối chiếu dòng hiển thị với `row_restaurant.xml`.

## Bài 5 — Kiểm tra nhập liệu và phản hồi lỗi

Đừng gửi dữ liệu sai lên server rồi mới báo lỗi. Ở màn hình đăng nhập, FoodNow kiểm tra định dạng email và mật khẩu rỗng; ở màn hình thanh toán, kiểm tra người nhận, số điện thoại, địa chỉ và giỏ hàng.

Mẫu cơ bản:

```java
String name = inputName.getText().toString().trim();
if (name.isEmpty()) {
    inputName.setError("Nhập họ tên");
    inputName.requestFocus();
    return;
}
saveButton.setEnabled(false); // Tránh gửi hai lần khi đang chờ
```

Khi tác vụ thành công **hoặc thất bại**, bật lại nút nếu màn hình vẫn còn mở. Phía backend vẫn phải kiểm tra dữ liệu lần nữa: dữ liệu từ client không được xem là luôn đúng. Xem [`CheckoutActivity.java`](../app/src/main/java/com/example/project1/CheckoutActivity.java) và hàm xử lý đơn trong [`server.py`](../backend/server.py).

**Thực hành:** thử để trống người nhận, nhập số điện thoại sai và nhập địa chỉ hợp lệ; ghi lại thông báo của từng trường hợp. Không dùng đơn thật để kiểm thử thanh toán vì ví/chuyển khoản trong project chỉ mô phỏng.

## Bài 6 — Gọi REST API ngoài luồng giao diện

Android không nên thực hiện mạng trực tiếp trên **UI thread** vì màn hình có thể đứng. FoodNow gom HTTP trong [`Api.java`](../app/src/main/java/com/example/project1/Api.java), chạy công việc bằng Executor và trả kết quả về UI thread:

```java
Api.async(this,
    () -> Api.request(this, "GET", "/catalog", null),
    result -> android.util.Log.d("FoodNow", result.toString()),
    error -> Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show());
```

`Api.request` thêm tiền tố `/api`, mã hóa/đọc JSON và gửi Bearer token nếu đã đăng nhập. Vì vậy path `"/catalog"` tương ứng `GET http://10.0.2.2:8080/api/catalog` trên máy ảo. Backend trả lỗi HTTP với nội dung JSON; app hiển thị lỗi và cho người dùng thử lại.

Luồng dữ liệu của FoodNow:

```text
Android Activity/Fragment
    → Api.java (HTTP + JSON)
    → backend/server.py (xác thực, kiểm tra, xử lý)
    → Cloud Firestore
```

**Thực hành:** dừng backend rồi mở app để xem trạng thái lỗi; khởi động lại backend và bấm **Thử lại**. Kiểm tra app không bị treo khi mạng lỗi.

## Bài 7 — Vòng đời màn hình và tự kiểm tra

- `onCreate`: tạo màn hình và gắn listener.
- `onResume`: tải lại dữ liệu cần cập nhật khi quay về màn hình. FoodNow tải lại thực đơn khi trở lại Trang chủ.
- `onPause`/`onStop`: dừng các công việc chỉ có ý nghĩa khi màn hình đang hiển thị, như polling hoặc GPS.
- `onSaveInstanceState`: lưu trạng thái giao diện ngắn hạn khi Activity có thể được tạo lại; dữ liệu quan trọng vẫn phải lưu bền vững ở store/backend.

Sau mỗi bài, kiểm tra tối thiểu: màn hình mở được, dữ liệu đúng, thao tác sai có thông báo, nút không bị khóa vĩnh viễn, quay lại màn hình trước không mất trạng thái cần giữ. Với project này có thể chạy:

```powershell
$env:JAVA_HOME = 'E:/App/Androi studio/jbr' # Đổi theo JDK trên máy của bạn
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Build thành công chỉ xác nhận mã biên dịch và các bài test tự động đã chạy; cần thử trực tiếp trên máy ảo/điện thoại để đánh giá bố cục, thao tác chạm và mạng.

## Bài tập cuối: đi từ đăng nhập đến đặt món

1. Đăng ký tài khoản khách và đăng nhập.
2. Tìm một nhà hàng; mở chi tiết và chọn hai món.
3. Tăng/giảm số lượng, kiểm tra tổng tiền giỏ hàng.
4. Điền thông tin giao hàng, xác nhận đơn.
5. Mở tab Đơn hàng và xem trạng thái; mở web quản trị để đối chiếu cùng một đơn.

**Câu hỏi ôn tập:** XML khác Java ở vai trò nào? Vì sao cần `Intent`? Adapter làm gì? Vì sao gọi API ngoài UI thread? `10.0.2.2` và `localhost` khác nhau thế nào trên máy ảo?

## Tài liệu chính thức để đọc tiếp

- [Activity và AndroidManifest](https://developer.android.com/guide/components/activities/intro-activities)
- [Tạo Fragment](https://developer.android.com/guide/fragments/create)
- [Danh sách với RecyclerView](https://developer.android.com/develop/ui/views/layout/recyclerview)
- [Xử lý công việc bất đồng bộ bằng Java](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads)

