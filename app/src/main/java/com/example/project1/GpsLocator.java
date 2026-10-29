package com.example.project1;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.util.function.Consumer;

/** Foreground, user-triggered location request with timeout and cleanup. */
public final class GpsLocator {
    private final AppCompatActivity activity;
    private final LocationManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Consumer<Location> result;
    private final ActivityResultLauncher<String[]> permission;
    private final LocationListener listener=new LocationListener(){
        @Override public void onLocationChanged(Location location){
            Consumer<Location> callback=result;stop();if(callback!=null)callback.accept(location);
        }
        @Override public void onProviderEnabled(String provider){}
        @Override public void onProviderDisabled(String provider){}
        @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    };
    public GpsLocator(AppCompatActivity activity){
        this.activity=activity;manager=(LocationManager)activity.getSystemService(android.content.Context.LOCATION_SERVICE);
        permission=activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),grants->{
            if(allowed())locate();else{stop();message("Bạn chưa cho phép vị trí. Có thể chọn ghim bằng tay.");}
        });
    }
    private boolean allowed(){return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public void request(Consumer<Location> callback){stop();result=callback;if(allowed())locate();else permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});}
    @android.annotation.SuppressLint("MissingPermission") private void locate(){
        boolean started=false;
        try{
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                if(!manager.isProviderEnabled(provider))continue;
                if(provider.equals(LocationManager.GPS_PROVIDER)&&activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)continue;
                manager.requestLocationUpdates(provider,1000,0,listener,Looper.getMainLooper());started=true;
            }
        }catch(SecurityException ignored){}
        if(!started){stop();message("Hãy bật Vị trí trong cài đặt điện thoại hoặc chọn ghim bằng tay.");return;}
        message("Đang lấy vị trí hiện tại…");
        handler.postDelayed(()->{stop();message("Chưa lấy được vị trí. Hãy thử ở nơi thoáng hoặc chọn bằng tay.");},20000);
    }
    public void stop(){handler.removeCallbacksAndMessages(null);if(manager!=null)manager.removeUpdates(listener);result=null;}
    private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
}
