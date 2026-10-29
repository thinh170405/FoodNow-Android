package com.example.project1;
import android.os.*;
import android.widget.*;
import org.json.*;
public class ChatActivity extends BaseActivity {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private boolean active,busy;
    private String id,key,body;
    private EditText input;
    private String path(){String role=AuthStore.userPrefs(this).getString("role","");return ("admin".equals(role)?"/admin/orders/":"driver".equals(role)?"/driver/orders/":"/orders/")+id+"/messages";}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(!new AuthStore(this).signedIn()){finish();return;}screen(R.layout.activity_chat);
        id=getIntent().getStringExtra("orderId");if(id==null){finish();return;}
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v->finish());
        input=findViewById(R.id.chatInput);findViewById(R.id.chatSend).setOnClickListener(v->send());
    }
    @Override protected void onResume(){super.onResume();active=true;load();}
    private void load(){if(!active)return;Api.async(this,()->Api.request(this,"GET",path(),null),result->{if(!active)return;
        LinearLayout list=findViewById(R.id.chatMessages);list.removeAllViews();JSONArray messages=result.optJSONArray("messages");
        if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);TextView text=new TextView(this);text.setText(m.optString("name")+"\n"+m.optString("body"));text.setTextSize(16);text.setPadding(16,12,16,12);list.addView(text);}
        ((TextView)findViewById(R.id.chatStatus)).setText("Trao đổi với người phụ trách đơn hàng");timer.postDelayed(this::load,4000);
    },error->{if(active){((TextView)findViewById(R.id.chatStatus)).setText(error.getMessage());timer.postDelayed(this::load,8000);}});}
    private void send(){if(busy)return;String value=input.getText().toString().trim();if(value.isEmpty())return;
        if(!value.equals(body)){key=java.util.UUID.randomUUID().toString();body=value;}busy=true;input.setEnabled(false);findViewById(R.id.chatSend).setEnabled(false);
        Api.async(this,()->Api.request(this,"POST",path(),Api.json("body",body,"clientKey",key)),result->{busy=false;key=null;body=null;input.setText("");input.setEnabled(true);findViewById(R.id.chatSend).setEnabled(true);timer.removeCallbacksAndMessages(null);load();},error->{busy=false;input.setEnabled(true);findViewById(R.id.chatSend).setEnabled(true);Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();});
    }
    @Override protected void onPause(){active=false;timer.removeCallbacksAndMessages(null);super.onPause();}
}
