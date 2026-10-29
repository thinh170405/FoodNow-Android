package com.example.project1;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import org.json.JSONObject;

/** Backend authentication with a local session/profile cache. */
public final class AuthStore {
    private final Context context;
    private final SharedPreferences session;
    public AuthStore(Context context) { this.context=context.getApplicationContext(); session=this.context.getSharedPreferences("remote_session",0); }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public String email() { return session.getString("email",""); }
    public boolean signedIn() { return !session.getString("token","").isEmpty(); }
    public boolean register(String name,String email,String password) {
        try { Api.request(context,"POST","/auth/register",Api.json("name",name,"email",normalize(email),"password",password)); return true; }
        catch(Api.Failure error) { if(error.status==409)return false; throw error; }
    }
    public boolean login(String email,String password) {
        return loginAt(email,password,false);
    }
    public boolean loginDelivery(String email,String password) {
        return loginAt(email,password,true);
    }
    private boolean loginAt(String email,String password,boolean delivery) {
        try {
            JSONObject result=Api.request(context,"POST",delivery?"/auth/delivery-login":"/auth/login",Api.json("email",normalize(email),"password",password));
            JSONObject user=result.optJSONObject("user");
            if(user==null||result.optString("token").isEmpty()) throw new IllegalStateException("Phản hồi đăng nhập không hợp lệ");
            session.edit().putString("token",result.optString("token")).putString("email",user.optString("email")).putString("provider","password").apply();
            session.edit().putBoolean("deliveryEntry",delivery).apply();
            cacheProfile(user); return true;
        } catch(Api.Failure error) { if(error.status==401)return false; throw error; }
    }
    public boolean changePassword(String current,String replacement) {
        Api.request(context,"POST","/auth/password",Api.json("current",current,"replacement",replacement));
        clearLocal(); return true;
    }
    public void firebase(String idToken){
        JSONObject result=Api.request(context,"POST","/auth/firebase",Api.json("idToken",idToken));JSONObject user=result.optJSONObject("user");
        if(user==null||result.optString("token").isEmpty())throw new IllegalStateException("Phiên đăng nhập không hợp lệ");
        session.edit().putString("token",result.optString("token")).putString("email",user.optString("email")).putString("provider","firebase").apply();cacheProfile(user);
    }
    public JSONObject refreshProfile() {
        JSONObject user=Api.request(context,"GET","/me",null).optJSONObject("user");
        if(user==null) throw new IllegalStateException("Không đọc được hồ sơ");
        cacheProfile(user); return user;
    }
    public void saveProfile(String name,String phone,String address) {
        JSONObject user=Api.request(context,"PATCH","/me",Api.json("name",name,"phone",phone,"address",address)).optJSONObject("user");
        if(user!=null) cacheProfile(user);
    }
    private void cacheProfile(JSONObject user) {
        SharedPreferences.Editor editor=userPrefs(context).edit();
        for(String key:new String[]{"id","name","email","phone","address","avatar","role"}) editor.putString(key,user.optString(key));
        editor.apply();
    }
    public void logout() {
        // Revocation completes off the UI thread; then the UI clears its session.
        try { Api.request(context,"POST","/auth/logout",Api.json()); } finally { clearLocal(); }
    }
    public void clearLocal() { session.edit().clear().apply();context.getSystemService(android.app.NotificationManager.class).cancelAll();
        if(!com.google.firebase.FirebaseApp.getApps(context).isEmpty())com.google.firebase.auth.FirebaseAuth.getInstance().signOut();

    }
    public static SharedPreferences userPrefs(Context context) {
        AuthStore auth=new AuthStore(context);
        if(!auth.signedIn()) throw new IllegalStateException("Cần đăng nhập");
        return context.getSharedPreferences("remote_user_"+Integer.toHexString(Api.base(context).hashCode())+"_"+auth.email(),0);
    }
}
