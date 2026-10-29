package com.example.project1;
public class FoodMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {
    @Override public void onNewToken(String token){if(new AuthStore(this).signedIn())Api.background(()->{try{Api.request(this,"POST","/devices",Api.json("token",token));}catch(Exception ignored){}});}
    @Override public void onMessageReceived(com.google.firebase.messaging.RemoteMessage message){
        if(!new AuthStore(this).signedIn())return;
        java.util.Map<String,String> data=message.getData();
        if(!AuthStore.userPrefs(this).getString("id","").equals(data.get("userId")))return;
        Notifications.show(this,data.getOrDefault("eventId",String.valueOf(message.getSentTime())),data.getOrDefault("title","FoodNow"),data.getOrDefault("body","Đơn hàng có cập nhật mới"));
    }
}
