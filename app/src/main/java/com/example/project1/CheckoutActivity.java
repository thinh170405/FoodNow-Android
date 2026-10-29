package com.example.project1;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.*;

public class CheckoutActivity extends BaseActivity {
    private Restaurant restaurant;
    private int subtotal;
    private String orderId;
    private JSONArray items;
    private boolean submitted;
    private double latitude=Double.NaN,longitude=Double.NaN;
    private final androidx.activity.result.ActivityResultLauncher<Intent> mapPicker=registerForActivityResult(
        new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{
            Intent data=result.getData();if(result.getResultCode()==RESULT_OK&&data!=null){
                double lat=data.getDoubleExtra("latitude",Double.NaN),lng=data.getDoubleExtra("longitude",Double.NaN);
                if(DeliveryMapActivity.valid(lat,lng)){latitude=lat;longitude=lng;showPoint();}
            }
        });
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!new AuthStore(this).signedIn()){finish();return;}
        screen(R.layout.activity_checkout);
        if(state!=null){latitude=state.getDouble("latitude",Double.NaN);longitude=state.getDouble("longitude",Double.NaN);}
        showPoint();
        findViewById(R.id.chooseDeliveryPoint).setOnClickListener(v->mapPicker.launch(new Intent(this,DeliveryMapActivity.class)
            .putExtra("latitude",latitude).putExtra("longitude",longitude).putExtra("address",value(R.id.txtAddress))));
        findViewById(R.id.clearDeliveryPoint).setOnClickListener(v->{latitude=Double.NaN;longitude=Double.NaN;showPoint();});
        orderId=state==null?java.util.UUID.randomUUID().toString():state.getString("orderId",java.util.UUID.randomUUID().toString());
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v->finish());
        findViewById(R.id.btnConfirmPayment).setEnabled(false);
        if(DemoData.RESTAURANTS.isEmpty()) Api.async(this,()->DemoData.fetch(this),data->{DemoData.load(data);initialize(state);},error->{Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();finish();});
        else initialize(state);
    }
    private void initialize(Bundle state){
        int index=DemoData.indexOfId(getIntent().getIntExtra("restaurantId",-1));
        int[] quantities=getIntent().getIntArrayExtra("quantities"), ids=getIntent().getIntArrayExtra("foodIds");
        if(index<0||quantities==null||ids==null||quantities.length!=ids.length){finish();return;}
        restaurant=DemoData.RESTAURANTS.get(index);items=new JSONArray();subtotal=0;
        StringBuilder summary=new StringBuilder(restaurant.name).append("\n\n");
        for(int i=0;i<quantities.length;i++){
            if(quantities[i]==0)continue;
            Food selected=null;for(Food food:restaurant.menu)if(food.id==ids[i])selected=food;
            if(selected==null||quantities[i]<1||quantities[i]>99){Toast.makeText(this,"Thực đơn đã thay đổi, vui lòng chọn lại món",Toast.LENGTH_LONG).show();finish();return;}
            long amount=(long)selected.price*quantities[i];
            if((long)subtotal+amount>1000000000){Toast.makeText(this,"Giỏ hàng vượt giới hạn",Toast.LENGTH_LONG).show();finish();return;}
            subtotal+=(int)amount;
            items.put(Api.json("foodId",selected.id,"quantity",quantities[i]));
            summary.append(quantities[i]).append(" × ").append(selected.name).append(" — ").append(DemoData.money((int)amount)).append("\n");
        }
        if(items.length()==0){finish();return;}
        ((TextView)findViewById(R.id.tvSummary)).setText(summary.toString());
        ((TextView)findViewById(R.id.tvPaymentTotal)).setText("Tiền món: "+DemoData.money(subtotal)+"\nPhí giao hàng: "+DemoData.money(DemoData.deliveryFee)+"\nTổng: "+DemoData.money(subtotal+DemoData.deliveryFee));
        if(state==null){SharedPreferences prefs=AuthStore.userPrefs(this);((EditText)findViewById(R.id.txtRecipient)).setText(prefs.getString("name",""));((EditText)findViewById(R.id.txtPhone)).setText(prefs.getString("phone",""));((EditText)findViewById(R.id.txtAddress)).setText(prefs.getString("address",""));}
        findViewById(R.id.btnConfirmPayment).setEnabled(true);findViewById(R.id.btnConfirmPayment).setOnClickListener(v->confirm());
    }
    private String value(int id){return ((EditText)findViewById(id)).getText().toString().trim();}
    private void showPoint(){
        boolean selected=DeliveryMapActivity.valid(latitude,longitude);
        ((TextView)findViewById(R.id.deliveryPointLabel)).setText(selected?String.format(java.util.Locale.US,"Điểm đã chọn: %.6f, %.6f\nKiểm tra ghim khớp địa chỉ giao hàng.",latitude,longitude):"Chưa ghim vị trí (không bắt buộc). Vẫn cần nhập số nhà/địa chỉ ở trên.");
        findViewById(R.id.clearDeliveryPoint).setVisibility(selected?android.view.View.VISIBLE:android.view.View.GONE);
    }
    private void confirm(){
        if(submitted)return;
        for(int id:new int[]{R.id.txtRecipient,R.id.txtPhone,R.id.txtAddress})if(value(id).isEmpty()){((EditText)findViewById(id)).setError("Vui lòng nhập thông tin");return;}
        String phone=value(R.id.txtPhone);if(!phone.replaceAll("[\\s()-]","").matches("(?:0[0-9]{9}|\\+84[0-9]{9})")){((EditText)findViewById(R.id.txtPhone)).setError("Số điện thoại không hợp lệ");return;}
        int choice=((RadioGroup)findViewById(R.id.paymentMethod)).getCheckedRadioButtonId();
        String payment=choice==R.id.payCash?"cod":choice==R.id.payWallet?"wallet_demo":"bank_demo";
        JSONObject payload=Api.json("clientKey",orderId,"restaurantId",restaurant.id,"items",items,"expectedTotal",subtotal+DemoData.deliveryFee,
            "recipient",value(R.id.txtRecipient),"phone",phone,"address",value(R.id.txtAddress),"note",value(R.id.txtNote),"paymentMethod",payment,
            "deliveryLatitude",DeliveryMapActivity.valid(latitude,longitude)?latitude:JSONObject.NULL,
            "deliveryLongitude",DeliveryMapActivity.valid(latitude,longitude)?longitude:JSONObject.NULL);
        new AlertDialog.Builder(this).setTitle("Xác nhận đặt hàng").setMessage("Tổng: "+DemoData.money(subtotal+DemoData.deliveryFee)+"\nĐơn sẽ được gửi tới nhà hàng. Ví/chuyển khoản chỉ mô phỏng.")
            .setNegativeButton("Quay lại",null).setPositiveButton("Xác nhận",(d,w)->{
                if(submitted)return;submitted=true;findViewById(R.id.btnConfirmPayment).setEnabled(false);
                Api.async(this,()->new ShopStore(this).place(payload,restaurant.id),order->{
                    startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("show_order",true));finish();
                },error->{submitted=false;findViewById(R.id.btnConfirmPayment).setEnabled(true);Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();});
            }).show();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("orderId",orderId);state.putDouble("latitude",latitude);state.putDouble("longitude",longitude);}
}
