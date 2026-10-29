package com.example.project1;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;


public final class Notifications {
    public static final String CHANNEL="foodnow_orders";
    public static void register(Context context){
        if(context.getString(R.string.google_app_id).isEmpty()||!new AuthStore(context).signedIn())return;
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->Api.background(()->{try{Api.request(context,"POST","/devices",Api.json("token",token));}catch(Exception ignored){}}));
    }
    public static void show(Context context,String id,String title,String body){
        if(!new AuthStore(context).signedIn())return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        android.content.SharedPreferences prefs=AuthStore.userPrefs(context);
        if(prefs.getBoolean("notice_"+id,false))return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Đơn hàng và tin nhắn",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MainActivity.class).putExtra("show_order",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(id.hashCode(),new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.icon_order).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(action).build());
        prefs.edit().putBoolean("notice_"+id,true).apply();
    }
}
