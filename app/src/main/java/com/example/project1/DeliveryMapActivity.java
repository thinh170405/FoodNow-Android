package com.example.project1;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.Locale;

/** Local trusted map page: no JS bridge or session token. */
public class DeliveryMapActivity extends BaseActivity {
    private WebView map;
    private double latitude=Double.NaN, longitude=Double.NaN;
    private boolean readonly,ready;
    private GpsLocator gps;
    private com.google.android.gms.maps.GoogleMap googleMap;
    private com.google.android.gms.maps.model.Marker pin,driver;
    private boolean googleEnabled;
    private final android.os.Handler poll=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean tracking;
    public static boolean valid(double lat,double lng){return Double.isFinite(lat)&&Double.isFinite(lng)&&Math.abs(lat)<=90&&Math.abs(lng)<=180;}
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state){
        super.onCreate(state);screen(R.layout.activity_delivery_map);
        readonly=getIntent().getBooleanExtra("readonly",false);
        gps=new GpsLocator(this);
        findViewById(R.id.useGps).setVisibility(readonly?View.GONE:View.VISIBLE);
        findViewById(R.id.useGps).setOnClickListener(v->gps.request(location->selectPoint(location.getLatitude(),location.getLongitude(),true)));
        latitude=state==null?getIntent().getDoubleExtra("latitude",Double.NaN):state.getDouble("latitude",Double.NaN);
        longitude=state==null?getIntent().getDoubleExtra("longitude",Double.NaN):state.getDouble("longitude",Double.NaN);
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v->finish());
        findViewById(R.id.mapSearchRow).setVisibility(readonly?View.GONE:View.VISIBLE);
        findViewById(R.id.mapConfirm).setVisibility(readonly?View.GONE:View.VISIBLE);
        EditText search=findViewById(R.id.mapSearch);
        if(state==null)search.setText(getIntent().getStringExtra("address"));
        findViewById(R.id.mapSearchButton).setOnClickListener(v->searchAddress());
        search.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){searchAddress();return true;}return false;});
        map=findViewById(R.id.deliveryMap);
        googleEnabled=!getString(R.string.google_maps_key).trim().isEmpty();
        if(googleEnabled){
            map.setVisibility(View.GONE);
            com.google.android.gms.maps.SupportMapFragment fragment=(com.google.android.gms.maps.SupportMapFragment)getSupportFragmentManager().findFragmentByTag("google-map");
            if(fragment==null){fragment=com.google.android.gms.maps.SupportMapFragment.newInstance();getSupportFragmentManager().beginTransaction().add(R.id.mapContainer,fragment,"google-map").commit();}
            fragment.getMapAsync(value->{googleMap=value;ready=true;
                value.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(new com.google.android.gms.maps.model.LatLng(valid(latitude,longitude)?latitude:10.7769,valid(latitude,longitude)?longitude:106.7009),valid(latitude,longitude)?16:13));
                if(valid(latitude,longitude))selectPoint(latitude,longitude,false);
                value.setOnMapClickListener(point->{if(!readonly)selectPoint(point.latitude,point.longitude,false);});
                value.setOnMarkerDragListener(new com.google.android.gms.maps.GoogleMap.OnMarkerDragListener(){public void onMarkerDragStart(com.google.android.gms.maps.model.Marker m){}public void onMarkerDrag(com.google.android.gms.maps.model.Marker m){}public void onMarkerDragEnd(com.google.android.gms.maps.model.Marker m){selectPoint(m.getPosition().latitude,m.getPosition().longitude,false);}});
            });
        }
        WebSettings settings=map.getSettings();settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setUserAgentString(settings.getUserAgentString()+" FoodNow/1.0 (Android student project)");
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        map.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){ready=true;if(valid(latitude,longitude))selectPoint(latitude,longitude,false);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if(request.isForMainFrame()&&"foodnow-pin".equals(uri.getScheme())&&"selected".equals(uri.getHost())&&!readonly){
                    try{double lat=Double.parseDouble(uri.getQueryParameter("lat")),lng=Double.parseDouble(uri.getQueryParameter("lng"));if(valid(lat,lng)){latitude=lat;longitude=lng;showSelection();}}catch(Exception ignored){}
                }else if("https".equals(uri.getScheme())&&("www.openstreetmap.org".equals(uri.getHost())||"leafletjs.com".equals(uri.getHost()))){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(android.content.ActivityNotFoundException ignored){}
                }
                return true;
            }
        });
        String hash=valid(latitude,longitude)?"#"+latitude+","+longitude+(readonly?",read":""):"";
        if(!googleEnabled)map.loadUrl("file:///android_asset/map/index.html"+hash);
        showSelection();
        findViewById(R.id.mapConfirm).setOnClickListener(v->{if(valid(latitude,longitude)){setResult(RESULT_OK,new Intent().putExtra("latitude",latitude).putExtra("longitude",longitude));finish();}});
    }
    private void selectPoint(double lat,double lng,boolean center){
        if(!valid(lat,lng))return;latitude=lat;longitude=lng;showSelection();
        if(googleMap!=null){com.google.android.gms.maps.model.LatLng point=new com.google.android.gms.maps.model.LatLng(lat,lng);
            if(pin==null)pin=googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions().position(point).title("Điểm giao hàng").draggable(!readonly));else pin.setPosition(point);
            if(center)googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(point,17));
        }else if(!googleEnabled&&ready)map.evaluateJavascript("window.setPoint("+lat+","+lng+")",null);
    }
    @Override protected void onResume(){super.onResume();tracking=true;loadDriver();}
    private void loadDriver(){
        String id=getIntent().getStringExtra("orderId");if(!tracking||id==null)return;
        Api.async(this,()->Api.request(this,"GET","/orders/"+id+"/location",null),data->{if(!tracking)return;
            org.json.JSONObject point=data.optJSONObject("location");
            if(point!=null){double lat=point.optDouble("latitude"),lng=point.optDouble("longitude");long age=System.currentTimeMillis()/1000-point.optLong("updated_at");
                ((TextView)findViewById(R.id.mapHint)).setText("Vị trí người giao cập nhật "+Math.max(0,age)+" giây trước"+(age>60?" · Có thể đã cũ":""));
                if(valid(lat,lng)){if(googleMap!=null){com.google.android.gms.maps.model.LatLng p=new com.google.android.gms.maps.model.LatLng(lat,lng);if(driver==null)driver=googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions().position(p).title("Người giao hàng"));else driver.setPosition(p);}else if(ready)map.evaluateJavascript("window.driverAt("+lat+","+lng+")",null);}
            }else ((TextView)findViewById(R.id.mapHint)).setText("Người giao hàng chưa chia sẻ vị trí.");
            if("delivering".equals(data.optString("status")))poll.postDelayed(this::loadDriver,5000);
        },error->{if(tracking){((TextView)findViewById(R.id.mapHint)).setText("Không cập nhật được vị trí. Đang thử lại…");poll.postDelayed(this::loadDriver,10000);}});
    }
    private void showSelection(){
        boolean selected=valid(latitude,longitude);findViewById(R.id.mapConfirm).setEnabled(selected);
        ((TextView)findViewById(R.id.mapHint)).setText(selected?String.format(Locale.US,"Điểm giao: %.6f, %.6f",latitude,longitude):"Chạm bản đồ để đặt ghim. Cần Internet để tải bản đồ.");
    }
    @SuppressWarnings("deprecation")
    private void searchAddress(){
        EditText field=findViewById(R.id.mapSearch);String query=field.getText().toString().trim();
        if(query.isEmpty()){field.setError("Nhập địa chỉ, quận và thành phố");return;}
        if(!ready||!Geocoder.isPresent()){Toast.makeText(this,"Tìm kiếm chưa sẵn sàng. Bạn có thể kéo và chạm bản đồ.",Toast.LENGTH_LONG).show();return;}
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);
        findViewById(R.id.mapSearchButton).setEnabled(false);
        Api.async(this,()->new Geocoder(this,Locale.forLanguageTag("vi-VN")).getFromLocationName(query,1),places->{
            findViewById(R.id.mapSearchButton).setEnabled(true);
            if(places==null||places.isEmpty()){Toast.makeText(this,"Không tìm thấy. Thêm quận/thành phố hoặc chọn trên bản đồ.",Toast.LENGTH_LONG).show();return;}
            Address place=places.get(0);if(valid(place.getLatitude(),place.getLongitude())){
                if(googleMap!=null)googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(new com.google.android.gms.maps.model.LatLng(place.getLatitude(),place.getLongitude()),17));
                else map.evaluateJavascript("window.centerAt("+place.getLatitude()+","+place.getLongitude()+")",null);
            }
        },error->{findViewById(R.id.mapSearchButton).setEnabled(true);Toast.makeText(this,"Không tìm được địa chỉ lúc này. Hãy chọn trực tiếp trên bản đồ.",Toast.LENGTH_LONG).show();});
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putDouble("latitude",latitude);state.putDouble("longitude",longitude);}
    @Override protected void onStop(){tracking=false;poll.removeCallbacksAndMessages(null);if(gps!=null)gps.stop();super.onStop();}
    @Override protected void onDestroy(){if(map!=null){map.stopLoading();((android.view.ViewGroup)map.getParent()).removeView(map);map.destroy();}super.onDestroy();}
}
