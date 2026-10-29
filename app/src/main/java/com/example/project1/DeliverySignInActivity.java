package com.example.project1;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** Dedicated entry point for the existing server-authorized delivery operator. */
public class DeliverySignInActivity extends BaseActivity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);screen(R.layout.activity_delivery_signin);
        EditText email=findViewById(R.id.deliveryEmail),password=findViewById(R.id.deliveryPassword);
        Button submit=findViewById(R.id.deliveryLogin),back=findViewById(R.id.customerLogin);
        back.setOnClickListener(v->finish());
        submit.setOnClickListener(v->runWithNetwork(()->{
            String address=email.getText().toString().trim(),secret=password.getText().toString();
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("Nhập email hợp lệ");return;}
            if(secret.isEmpty()){password.setError("Nhập mật khẩu");return;}
            submit.setEnabled(false);back.setEnabled(false);email.setEnabled(false);password.setEnabled(false);
            Api.async(this,()->new AuthStore(this).loginDelivery(address,secret),ok->{
                if(ok){startActivity(new Intent(this,DeliveryActivity.class).putExtra("deliveryEntry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
                else{enable(submit,back,email,password);password.setError("Email hoặc mật khẩu không đúng");}
            },error->{enable(submit,back,email,password);Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();});
        }));
    }
    private void enable(Button submit,Button back,EditText email,EditText password){if(isFinishing())return;submit.setEnabled(true);back.setEnabled(true);email.setEnabled(true);password.setEnabled(true);}
}
