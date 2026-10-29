package com.example.project1;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.appbar.MaterialToolbar;

/** Registration and authenticated password changes share the same validated form. */
public class AccountActivity extends BaseActivity {
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screen(R.layout.activity_account);
        boolean change = getIntent().getBooleanExtra("change", false);
        AuthStore auth = new AuthStore(this);
        if (change && !auth.signedIn()) { finish(); return; }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(change ? "Đổi mật khẩu" : "Tạo tài khoản");
        toolbar.setNavigationOnClickListener(v -> finish());
        EditText name=findViewById(R.id.accountName), email=findViewById(R.id.accountEmail),
            current=findViewById(R.id.currentPassword), password=findViewById(R.id.newPassword), repeat=findViewById(R.id.repeatPassword);
        name.setVisibility(change ? View.GONE : View.VISIBLE);
        email.setVisibility(change ? View.GONE : View.VISIBLE);
        current.setVisibility(change ? View.VISIBLE : View.GONE);
        Button submit=findViewById(R.id.submitAccount);
        submit.setText(change ? "Lưu mật khẩu mới" : "Đăng ký");
        submit.setOnClickListener(v -> {
            String n=name.getText().toString().trim(), e=AuthStore.normalize(email.getText().toString()),
                p=password.getText().toString(), old=current.getText().toString();
            if (!change && n.isEmpty()) { name.setError("Nhập họ tên"); name.requestFocus(); return; }
            if (!change && !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { email.setError("Email không hợp lệ"); email.requestFocus(); return; }
            if (change && old.isEmpty()) { current.setError("Nhập mật khẩu hiện tại"); current.requestFocus(); return; }
            if (p.length()<8 || p.length()>128 || !p.matches("(?s).*\\p{L}.*") || !p.matches("(?s).*\\d.*")) {
                password.setError("8–128 ký tự, gồm chữ và số"); password.requestFocus(); return;
            }
            if (!p.equals(repeat.getText().toString())) { repeat.setError("Mật khẩu nhập lại không khớp"); repeat.requestFocus(); return; }
            if (change && p.equals(old)) { password.setError("Mật khẩu mới phải khác mật khẩu hiện tại"); return; }
            submit.setEnabled(false);
            submit.setText("Đang xử lý…");
            worker.execute(() -> {
                try {
                    boolean ok=change ? auth.changePassword(old,p) : auth.register(n,e,p);
                    runOnUiThread(() -> {
                        if(isFinishing() || isDestroyed()) return;
                        submit.setEnabled(true); submit.setText(change ? "Lưu mật khẩu mới" : "Đăng ký");
                        if (!ok) {
                            EditText field=change ? current : email;
                            field.setError(change ? "Mật khẩu hiện tại không đúng" : "Email này đã được đăng ký"); field.requestFocus(); return;
                        }
                        new AlertDialog.Builder(this).setTitle(change ? "Đã đổi mật khẩu" : "Đăng ký thành công")
                            .setMessage(change ? "Hãy đăng nhập lại bằng mật khẩu mới." : "Bạn có thể đăng nhập bằng tài khoản vừa tạo.")
                            .setCancelable(false).setPositiveButton("Đăng nhập",(dialog,which) -> {
                                if(change) auth.clearLocal();
                                startActivity(new Intent(this,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
                            }).show();
                    });
                } catch(RuntimeException error) {
                    runOnUiThread(() -> { if(!isFinishing() && !isDestroyed()) { submit.setEnabled(true); submit.setText("Thử lại"); Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); } });
                }
            });
        });
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
