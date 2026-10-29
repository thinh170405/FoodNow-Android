package com.example.project1;
import android.os.Bundle;
import android.content.Intent;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
public class SignInActivity extends BaseActivity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.activity_signin);
        EditText email=findViewById(R.id.txtEmail), password=findViewById(R.id.txtPassword);
        AuthStore auth = new AuthStore(this);
        if (auth.signedIn()) { startActivity(new Intent(this, "driver".equals(AuthStore.userPrefs(this).getString("role",""))?DeliveryActivity.class:MainActivity.class)); finish(); return; }
        ((android.widget.TextView)findViewById(R.id.btnSignIn)).setText("Đăng nhập");
        ((android.widget.TextView)findViewById(R.id.btnSignUp)).setText("Đăng ký");
        findViewById(R.id.openDeliveryLogin).setOnClickListener(v->startActivity(new Intent(this,DeliverySignInActivity.class)));
        findViewById(R.id.btnSignIn).setOnClickListener(v -> runWithNetwork(() -> {
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()) { email.setError("Nhập email hợp lệ"); return; }
            if(password.length()==0) { password.setError("Nhập mật khẩu"); return; }
            String inputEmail=email.getText().toString(), inputPassword=password.getText().toString();
            v.setEnabled(false);
            worker.execute(() -> {
                try {
                    boolean success=auth.login(inputEmail,inputPassword);
                    runOnUiThread(() -> {
                        if(isFinishing() || isDestroyed()) return;
                        v.setEnabled(true);
                        if(success) { startActivity(new Intent(this,MainActivity.class)); finish(); }
                        else { password.setError("Email hoặc mật khẩu không đúng"); password.requestFocus(); }
                    });
                } catch(RuntimeException error) {
                    runOnUiThread(() -> { if(!isFinishing() && !isDestroyed()) { v.setEnabled(true); info("Không thể đăng nhập",error.getMessage()); } });
                }
            });
        }));
        findViewById(R.id.btnSignUp).setOnClickListener(v -> runWithNetwork(() -> startActivity(new Intent(this,AccountActivity.class))));
        findViewById(R.id.btnForgot).setOnClickListener(v -> info("Quên mật khẩu", "Liên hệ người quản trị khi cần hỗ trợ tài khoản. Hệ thống chưa tích hợp gửi email khôi phục mật khẩu."));
        findViewById(R.id.btnSignInFacebook).setOnClickListener(v->info("Đăng nhập Facebook",getString(R.string.social_login_demo)));
        findViewById(R.id.btnSignInGoogle).setOnClickListener(v->info("Đăng nhập Google",getString(R.string.social_login_demo)));
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)findViewById(R.id.dishTop).setOnLongClickListener(v->{ServerSettings.show(this);return true;});
    }
    private void info(String title,String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
