package com.example.project1;
import android.app.Activity;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
public final class ServerSettings {
    public static void show(Activity activity){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(Api.base(activity));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Kết nối máy chủ").setMessage("Điện thoại thật: nhập IP Wi-Fi của máy tính, ví dụ http://192.168.1.10:8080. Máy ảo: http://10.0.2.2:8080. Hai thiết bị cần cùng mạng.").setView(input).setNegativeButton("Hủy",null).setNeutralButton("Kiểm tra",null).setPositiveButton("Lưu",null).create();
        dialog.setOnShowListener(d->{
          dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            String value=validAddress(input);
            if(value==null)return;
            v.setEnabled(false);
            new Thread(()->{
              String result;
              java.net.HttpURLConnection connection=null;
              try{
                connection=(java.net.HttpURLConnection)new java.net.URL(value+"/api/catalog").openConnection();
                connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                result=connection.getResponseCode()==200?"Kết nối server thành công":"Server trả mã "+connection.getResponseCode();
              }catch(Exception e){result="Không kết nối được. Kiểm tra IP, Wi-Fi và tường lửa máy tính.";}
              finally{if(connection!=null)connection.disconnect();}
              String message=result;
              activity.runOnUiThread(()->{v.setEnabled(true);Toast.makeText(activity,message,Toast.LENGTH_LONG).show();});
            }).start();
          });
          dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=validAddress(input);
            if(value==null)return;
            if(!value.equals(Api.base(activity))){new AuthStore(activity).clearLocal();activity.getSharedPreferences("server_config",0).edit().putString("url",value).apply();}
            dialog.dismiss();
          });
        });dialog.show();
    }
    private static String validAddress(EditText input){
        String value=input.getText().toString().trim().replaceAll("/+$","");
        try{java.net.URI uri=new java.net.URI(value);if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().isEmpty())throw new IllegalArgumentException();}
        catch(Exception e){input.setError("Nhập http(s)://địa-chỉ:port, không thêm /api");return null;}
        return value;
    }
}
