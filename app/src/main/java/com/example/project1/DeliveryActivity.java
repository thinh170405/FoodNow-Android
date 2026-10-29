package com.example.project1;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import androidx.appcompat.app.AlertDialog;
public class DeliveryActivity extends BaseActivity {
    private GpsLocator gps;
    private LinearLayout list;
    private boolean active,loading;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(!new AuthStore(this).signedIn()||!"driver".equals(AuthStore.userPrefs(this).getString("role",""))){finish();return;}
        screen(R.layout.activity_delivery);gps=new GpsLocator(this);list=findViewById(R.id.deliveryOrders);
        ((TextView)findViewById(R.id.deliveryGreeting)).setText("Xin chào, "+AuthStore.userPrefs(this).getString("name","bạn"));
        findViewById(R.id.deliveryChangePassword).setOnClickListener(v->startActivity(new Intent(this,AccountActivity.class).putExtra("change",true)));
        Button exit=findViewById(R.id.deliveryExit);boolean entry=getSharedPreferences("remote_session",0).getBoolean("deliveryEntry",false);exit.setText(entry?"Đăng xuất":"Quay lại");
        exit.setOnClickListener(v->{if(!entry){finish();return;}
            new AlertDialog.Builder(this).setTitle("Đăng xuất tài khoản giao hàng?").setNegativeButton("Ở lại",null).setPositiveButton("Đăng xuất",(d,w)->{exit.setEnabled(false);Api.async(this,()->{new AuthStore(this).logout();return true;},ok->leaveDelivery(),error->leaveDelivery());}).show();
        });
        findViewById(R.id.deliveryRefresh).setOnClickListener(v->load());
    }
    @Override protected void onResume(){super.onResume();active=true;if(list!=null)load();}
    private void load(){if(loading||!active)return;loading=true;findViewById(R.id.deliveryProgress).setVisibility(View.VISIBLE);findViewById(R.id.deliveryRefresh).setEnabled(false);findViewById(R.id.deliveryError).setVisibility(View.GONE);
        Api.async(this,()->Api.request(this,"GET","/driver/orders",null),data->{loading=false;if(!active)return;finishLoad();render(data.optJSONArray("orders"));},error->{loading=false;if(!active)return;finishLoad();
            if(error instanceof Api.Failure&&((Api.Failure)error).status==401){leaveDelivery();return;}
            TextView message=findViewById(R.id.deliveryError);message.setText(error.getMessage()+"\nBấm Cập nhật để thử lại.");message.setVisibility(View.VISIBLE);
        });
    }
    private void finishLoad(){findViewById(R.id.deliveryProgress).setVisibility(View.GONE);findViewById(R.id.deliveryRefresh).setEnabled(true);}
    private void render(JSONArray orders){list.removeAllViews();int count=0;
        if(orders!=null)for(int i=0;i<orders.length();i++){JSONObject order=orders.optJSONObject(i);if(order==null||!"delivering".equals(order.optString("status")))continue;count++;
            String id=order.optString("id");View card=getLayoutInflater().inflate(R.layout.item_delivery_order,list,false);list.addView(card);
            ((TextView)card.findViewById(R.id.deliveryOrderTitle)).setText(order.optString("restaurant_name","Đơn giao hàng"));
            ((TextView)card.findViewById(R.id.deliveryOrderId)).setText("Mã đơn #"+id);
            ((TextView)card.findViewById(R.id.deliveryRecipient)).setText(order.optString("recipient")+" · "+order.optString("phone"));
            ((TextView)card.findViewById(R.id.deliveryAddress)).setText(order.optString("address"));
            ((TextView)card.findViewById(R.id.deliveryTotal)).setText(("cod".equals(order.optString("payment_method"))?"Thu tiền mặt: ":"Tổng đơn (thanh toán mô phỏng): ")+DemoData.money(order.optLong("total")));
            card.findViewById(R.id.shareDeliveryLocation).setOnClickListener(v->gps.request(location->{if(!active)return;
                Api.async(this,()->Api.request(this,"POST","/driver/orders/"+id+"/location",Api.json("latitude",location.getLatitude(),"longitude",location.getLongitude(),"accuracy",(double)location.getAccuracy())),ok->{if(active)Toast.makeText(this,"Đã chia sẻ vị trí với khách hàng",Toast.LENGTH_SHORT).show();});
            }));
            card.findViewById(R.id.deliveryChatButton).setOnClickListener(v->startActivity(new Intent(this,ChatActivity.class).putExtra("orderId",id)));
            double lat=order.optDouble("delivery_latitude",Double.NaN),lng=order.optDouble("delivery_longitude",Double.NaN);
            card.findViewById(R.id.deliveryMapButton).setOnClickListener(v->{if(DeliveryMapActivity.valid(lat,lng))startActivity(new Intent(this,DeliveryMapActivity.class).putExtra("latitude",lat).putExtra("longitude",lng).putExtra("readonly",true));else new AlertDialog.Builder(this).setTitle("Địa chỉ giao hàng").setMessage(order.optString("address")+"\nKhách chưa chọn ghim trên bản đồ.").setPositiveButton("Đóng",null).show();});
            card.findViewById(R.id.deliveryDetailsButton).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Chi tiết đơn hàng").setMessage(order.optString("details")).setPositiveButton("Đóng",null).show());
        }
        ((TextView)findViewById(R.id.deliveryCount)).setText(String.valueOf(count));findViewById(R.id.deliveryEmpty).setVisibility(count==0?View.VISIBLE:View.GONE);
    }
    @Override protected void onStop(){active=false;if(gps!=null)gps.stop();super.onStop();}
    private void leaveDelivery(){new AuthStore(this).clearLocal();startActivity(new Intent(this,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
}
