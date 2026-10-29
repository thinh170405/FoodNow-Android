package com.example.project1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** REST API client and background executor. */
public final class Api {
    private static final ExecutorService WORKERS=Executors.newFixedThreadPool(3);
    private Api() {}
    public static class Failure extends RuntimeException {
        public final int status;
        Failure(int status,String message) { super(message); this.status=status; }
    }
    public static String base(Context context) { return context.getSharedPreferences("server_config",0).getString("url","http://10.0.2.2:8080"); }
    public static JSONObject json(Object... pairs) {
        JSONObject object=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) object.put((String)pairs[i],pairs[i+1]); return object; }
        catch(Exception e) { throw new IllegalArgumentException(e); }
    }
    public static JSONObject request(Context context,String method,String path,JSONObject body) {
        java.net.HttpURLConnection connection=null;
        try {
            connection=(java.net.HttpURLConnection)new java.net.URL(base(context)+"/api"+path).openConnection();
            connection.setConnectTimeout(7000);connection.setReadTimeout(20000);connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type","application/json");
            String token=context.getSharedPreferences("remote_session",0).getString("token","");
            if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){connection.setDoOutput(true);try(java.io.OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
            int status=connection.getResponseCode();java.io.InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            StringBuilder text=new StringBuilder();
            if(stream!=null)try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)text.append(line);}
            JSONObject result;try{result=new JSONObject(text.toString());}catch(Exception e){throw new Failure(status,"Server trả dữ liệu không hợp lệ");}
            if(status>=400)throw new Failure(status,result.optString("error","Không thể xử lý yêu cầu"));return result;
        }catch(Failure e){throw e;}catch(Exception e){Failure failure=new Failure(0,"Không kết nối được server. Kiểm tra địa chỉ server và mạng.");failure.initCause(e);throw failure;}
        finally{if(connection!=null)connection.disconnect();}
    }
    public static <T> void async(Activity activity,Callable<T> task,Consumer<T> success) {
        async(activity,task,success,error->{
            if(error instanceof Failure && ((Failure)error).status==401) {
                new AuthStore(activity).clearLocal();
                activity.startActivity(new Intent(activity,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            }
            Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();
        });
    }
    public static <T> void async(Activity activity,Callable<T> task,Consumer<T> success,Consumer<Exception> failure) {
        WORKERS.execute(()->{try { T value=task.call(); activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())success.accept(value);}); }
            catch(Exception error) { activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())failure.accept(error);}); }});
    }
    public static void background(Runnable work) { WORKERS.execute(work); }
}
