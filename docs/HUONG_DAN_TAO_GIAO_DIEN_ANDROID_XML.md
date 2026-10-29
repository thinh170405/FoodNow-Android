# Hướng dẫn tạo giao diện Android bằng XML

**Dành cho người mới học Java/XML.** Sau bài này, bạn có thể tạo một màn hình, phối màu và khoảng cách, thiết kế ô nhập và nút, làm một dòng danh sách, rồi nối thao tác chạm với Java. Ví dụ lấy từ module `app/` của FoodNow.

## 1. Giao diện Android nằm ở đâu?

Trong Android Studio, mở **Project → app → src → main → res → layout**. Mỗi file XML mô tả một cây `View`/`ViewGroup`:

```text
activity_signin.xml
└── NestedScrollView
    └── RelativeLayout
        ├── ImageView
        └── LinearLayout
            ├── TextView
            ├── EditText (email)
            ├── EditText (mật khẩu)
            └── Button (đăng nhập)
```

XML quyết định **nhìn thấy gì và bố trí ở đâu**; `SignInActivity.java` quyết định **điều gì xảy ra khi người dùng thao tác**. Mỗi file layout chỉ có **một phần tử gốc**. Android Studio có các chế độ **Code**, **Split**, **Design** để vừa sửa XML vừa xem bản xem trước. [Tài liệu Android về layout XML](https://developer.android.com/develop/ui/views/layout/declaring-layout).

Trong project, xem trực tiếp [`activity_signin.xml`](../app/src/main/res/layout/activity_signin.xml) và [`SignInActivity.java`](../app/src/main/java/com/example/project1/SignInActivity.java).

## 2. Tạo một bố cục cơ bản

Trong `res/layout`, chọn **New → Layout Resource File**, đặt tên `activity_ui_demo`. Chọn `LinearLayout` làm root, sau đó nhập XML sau:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp"
    android:background="@color/surface">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Tạo tài khoản"
        android:textColor="@color/ink"
        android:textSize="28sp"
        android:textStyle="bold" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Nhập thông tin để bắt đầu"
        android:textColor="@color/muted"
        android:textSize="14sp" />

    <EditText
        android:id="@+id/demoName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:minHeight="56dp"
        android:hint="Họ và tên"
        android:inputType="textPersonName"
        android:padding="12dp" />

    <Button
        android:id="@+id/demoContinue"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:minHeight="48dp"
        android:text="Tiếp tục"
        android:textAllCaps="false"
        android:textColor="@android:color/white"
        android:backgroundTint="@color/green" />
</LinearLayout>
```

`@color/surface`, `@color/ink`, `@color/muted`, `@color/green` đã có trong [`colors.xml`](../app/src/main/res/values/colors.xml). Bản xem trước của XML không cần kết nối backend.

### Các thuộc tính cần nhớ

| Thuộc tính | Ý nghĩa |
|---|---|
| `match_parent` | Chiếm hết kích thước mà cha cho phép. |
| `wrap_content` | Chỉ lớn vừa đủ để chứa nội dung. |
| `orientation="vertical"` | Xếp các View con từ trên xuống. |
| `padding` | Khoảng trống **bên trong** View. |
| `layout_marginTop` | Khoảng trống **bên ngoài**, phía trên View. |
| `dp` | Đơn vị cho kích thước/khoảng cách. |
| `sp` | Đơn vị cho cỡ chữ, tôn trọng cài đặt kích thước chữ của người dùng. |
| `@+id/demoName` | Tạo ID mới để Java gọi `R.id.demoName`. |

**Thử ngay:** đổi `android:padding` từ `24dp` sang `8dp` trong Preview; quan sát nội dung sát mép hơn. Trả lại `24dp` trước khi tiếp tục.

## 3. Nối XML với Java

Tạo `UiDemoActivity.java` trong package `com.example.project1`:

```java
package com.example.project1;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class UiDemoActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_ui_demo);

        EditText name = findViewById(R.id.demoName);
        Button next = findViewById(R.id.demoContinue);
        next.setOnClickListener(view -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("Hãy nhập họ tên");
                name.requestFocus();
                return;
            }
            Toast.makeText(this, "Xin chào " + value, Toast.LENGTH_SHORT).show();
        });
    }
}
```

Thêm `<activity android:name=".UiDemoActivity" android:exported="false" />` **bên trong** thẻ `<application>` của [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml). Để mở màn hình demo từ một Activity khác trong lúc học, gọi `startActivity(new Intent(this, UiDemoActivity.class));` (nhớ import `android.content.Intent`). Không cần đặt Activity demo thành launcher.

**Thử ngay:** nhập tên rồi bấm Tiếp tục; sau đó xóa tên và bấm lại để thấy `setError`. `findViewById` chỉ dùng sau `setContentView`/`screen`.

## 4. Làm màn hình thích ứng với chiều cao và bàn phím

Nếu form có nhiều ô nhập, màn hình nhỏ hoặc bàn phím mở có thể che nút cuối. [`activity_signin.xml`](../app/src/main/res/layout/activity_signin.xml) đặt nội dung trong `NestedScrollView` để người dùng cuộn tới các nút. Với form dài, dùng mẫu:

```xml
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">
        <!-- Các ô nhập và nút -->
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

Không đặt một `RecyclerView` dài bên trong ScrollView chỉ để cuộn danh sách; RecyclerView đã tự cuộn và tái sử dụng dòng. Đối với `EditText`, chọn `inputType` phù hợp: `textEmailAddress`, `textPassword`, `phone`, `number`. Bàn phím sẽ phù hợp loại dữ liệu hơn. [Hướng dẫn inputType của Android](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/style).

## 5. Dùng màu và style thay vì lặp thuộc tính

FoodNow lưu bảng màu tại [`colors.xml`](../app/src/main/res/values/colors.xml) và style tại [`styles.xml`](../app/src/main/res/values/styles.xml). Ví dụ `FoodNow.Input` gom nền, padding, chiều cao tối thiểu và màu chữ của ô nhập. Khi áp dụng:

```xml
<EditText
    style="@style/FoodNow.Input"
    android:id="@+id/exampleEmail"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Email"
    android:inputType="textEmailAddress" />
```

Sửa style một lần giúp các ô cùng kiểu thay đổi đồng bộ. Hình nền bo góc của thẻ nhà hàng nằm ở [`bg_card.xml`](../app/src/main/res/drawable/bg_card.xml): một `<shape>` gồm màu nền, góc bo và viền.

**Thử ngay:** tạo `@color/soft_green` làm nền cho một `TextView` giải thích; so sánh độ dễ đọc với chữ `@color/ink`.

## 6. Tạo hai nút cùng hàng

Đăng nhập và Đăng ký trong FoodNow nằm ngang. Với `LinearLayout` ngang, đặt chiều rộng hai phần tử là `0dp` và cùng `layout_weight="1"` để chia đều chỗ trống:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">
    <Button android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:text="Đăng nhập" />
    <Button android:layout_width="0dp" android:layout_height="wrap_content"
        android:layout_weight="1" android:text="Đăng ký" />
</LinearLayout>
```

Có thể thêm `Space` rộng `8dp` giữa hai nút. Với `LinearLayout` **dọc**, dùng `layout_height="0dp"` khi chia đều chiều cao bằng weight. [Tài liệu Android về LinearLayout](https://developer.android.com/develop/ui/views/layout/linear).

## 7. Thiết kế một dòng danh sách bằng RecyclerView

Mỗi nhà hàng trong FoodNow có một thẻ riêng ở [`row_restaurant.xml`](../app/src/main/res/layout/row_restaurant.xml): ảnh 72dp, tên, địa chỉ và nền bo góc. Danh sách chứa các thẻ nằm trong [`fragment_restaurant.xml`](../app/src/main/res/layout/fragment_restaurant.xml). [`RestaurantAdapter.java`](../app/src/main/java/com/example/project1/RestaurantAdapter.java) gán dữ liệu cho từng dòng.

Quy trình làm danh sách:

1. Tạo layout **một dòng** (`row_restaurant.xml`).
2. Đặt `RecyclerView` trong layout **màn hình**.
3. Viết `ViewHolder` tìm các View trong một dòng.
4. Trong `onBindViewHolder`, gán tên/địa chỉ/ảnh và sự kiện bấm.
5. Trong Fragment/Activity, đặt `LinearLayoutManager` rồi đặt Adapter.

**Thử ngay:** đổi `layout_marginBottom` của thẻ từ `12dp` sang `20dp`; xem khoảng cách giữa các nhà hàng trong Preview hoặc trên máy ảo. [Hướng dẫn RecyclerView của Android](https://developer.android.com/develop/ui/views/layout/recyclerview).

## 8. Kiểm tra giao diện trước khi hoàn thành

- Xem Preview ở màn hình nhỏ và lớn; chạy trên máy ảo để kiểm tra hành vi thật.
- Phóng to chữ trong cài đặt hệ thống, kiểm tra chữ/nút có bị cắt hay không.
- Mở bàn phím khi nhập, kiểm tra nút cuối còn cuộn tới được.
- Bấm từng nút và thử ô trống; nút có phản hồi rõ ràng không?
- Dùng `contentDescription` cho ảnh/nút mang ý nghĩa; ảnh chỉ để trang trí có thể dùng `@null`.
- Kiểm tra tương phản chữ và nền, vùng chạm đủ lớn, trạng thái lỗi và trạng thái đang tải.

Trong project, build nhanh sau khi sửa XML:

```powershell
$env:JAVA_HOME = 'E:/App/Androi studio/jbr' # Đổi theo JDK máy bạn
.\gradlew.bat :app:assembleDebug
```

Build đạt mới xác nhận XML và Java biên dịch. Preview và thử trên thiết bị giúp phát hiện lỗi bố cục, bàn phím và thao tác chạm.

## Bài tập cuối

Tạo một màn hình **Hồ sơ đơn giản** gồm tiêu đề, ảnh đại diện, hai ô nhập tên/số điện thoại và nút Lưu. Dùng màu FoodNow, bọc form bằng `NestedScrollView`, kiểm tra trường tên không rỗng, và hiện `Toast` khi bấm Lưu. Sau đó chuyển một ô nhập sang `FoodNow.Input` để thấy tác dụng của style dùng chung.

