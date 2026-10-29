package com.example.project1;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayout;
public class MainActivity extends BaseActivity {
    private TabLayout tabs;
    private boolean ready;
    private boolean catalogLoading;
    private final android.os.Handler notices=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean resumed;
    @Override public void onCreate(Bundle state) {
        // Catalog must be loaded before fragments that reference it are restored.
        super.onCreate(null);
        if(!new AuthStore(this).signedIn()) { startActivity(new Intent(this,SignInActivity.class)); finish(); return; }
        screen(R.layout.activity_main);tabs=findViewById(R.id.tabLayout);
        tabs.addTab(tabs.newTab().setText("Trang chủ").setIcon(R.drawable.icon_home));
        tabs.addTab(tabs.newTab().setText("Đơn hàng").setIcon(R.drawable.icon_order));
        tabs.addTab(tabs.newTab().setText("Hồ sơ").setIcon(R.drawable.icon_profile));
        int index=state==null?(getIntent().getBooleanExtra("show_order",false)?1:0):state.getInt("tab",0);
        tabs.selectTab(tabs.getTabAt(index));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){
            public void onTabSelected(TabLayout.Tab tab){if(ready){if(tab.getPosition()==0)reloadCatalog();else display(tab.getPosition());}}
            public void onTabUnselected(TabLayout.Tab tab){}
            public void onTabReselected(TabLayout.Tab tab){if(ready){if(tab.getPosition()==0)reloadCatalog();else display(tab.getPosition());}}
        });
        reloadCatalog();
    }
    public void reloadCatalog() { runWithNetwork(this::loadCatalog); }
    private void loadCatalog() {
        if(catalogLoading)return;
        catalogLoading=true;
        boolean refreshing=ready;
        getSupportFragmentManager().executePendingTransactions();
        Fragment current=getSupportFragmentManager().findFragmentById(R.id.layoutContainer);
        FrameLayout frame=findViewById(R.id.layoutContainer);
        if(!refreshing){
            if(current!=null)getSupportFragmentManager().beginTransaction().remove(current).commitNow();
            frame.removeAllViews();
            TextView message=new TextView(this);message.setPadding(32,32,32,32);message.setText("Đang tải thực đơn…");frame.addView(message);
        } else if(!(current instanceof RestaurantFragment)&&tabs.getSelectedTabPosition()==0)display(0);
        Api.async(this,()->{new AuthStore(this).refreshProfile();return DemoData.fetch(this);},data->{
            catalogLoading=false;DemoData.load(data);ready=true;
            if(!refreshing){frame.removeAllViews();display(tabs.getSelectedTabPosition());}
            else if(tabs.getSelectedTabPosition()==0)display(0);
        },error->{
            catalogLoading=false;
            if(error instanceof Api.Failure&&((Api.Failure)error).status==401){new AuthStore(this).clearLocal();startActivity(new Intent(this,SignInActivity.class));finish();return;}
            if(refreshing){Toast.makeText(this,"Chưa cập nhật được thực đơn. Đang hiển thị dữ liệu đã tải.",Toast.LENGTH_LONG).show();return;}
            frame.removeAllViews();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,32,32,32);
            TextView text=new TextView(this);text.setText(error.getMessage());box.addView(text);
            Button retry=new Button(this);retry.setText("Thử lại");box.addView(retry);retry.setOnClickListener(v->reloadCatalog());
            Button exit=new Button(this);exit.setText("Về đăng nhập");box.addView(exit);exit.setOnClickListener(v->{new AuthStore(this).clearLocal();startActivity(new Intent(this,SignInActivity.class));finish();});frame.addView(box);
        });
    }
    private void display(int index) {
        Fragment fragment=index==1?new OrderFragment():index==2?new ProfileFragment():new RestaurantFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.layoutContainer,fragment).commit();
    }
    @Override protected void onResume(){super.onResume();resumed=true;Notifications.register(this);pollNotices();if(ready&&tabs!=null&&tabs.getSelectedTabPosition()==0)reloadCatalog();}
    private void pollNotices(){
        if(!resumed||!new AuthStore(this).signedIn())return;
        Api.async(this,()->Api.request(this,"GET","/notifications",null),data->{if(!resumed)return;org.json.JSONArray events=data.optJSONArray("events");
            if(events!=null)for(int i=events.length()-1;i>=0;i--){org.json.JSONObject e=events.optJSONObject(i);Notifications.show(this,e.optString("id"),e.optString("title"),e.optString("body"));}
            notices.postDelayed(this::pollNotices,15000);
        },error->{if(resumed)notices.postDelayed(this::pollNotices,30000);});
    }
    @Override protected void onPause(){resumed=false;notices.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("show_order",false)&&tabs!=null){tabs.selectTab(tabs.getTabAt(1));}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(tabs!=null)state.putInt("tab",tabs.getSelectedTabPosition());}
}
