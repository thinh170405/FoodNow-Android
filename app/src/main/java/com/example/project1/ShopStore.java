package com.example.project1;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
/** Stable food IDs keep local baskets correct when admins change menu ordering. Orders live on the server. */
public final class ShopStore {
    private final Context context;
    private final SharedPreferences prefs;
    public ShopStore(Context context) { this.context=context.getApplicationContext();prefs=AuthStore.userPrefs(context); }
    public int[] cart(int index,int size) {
        Restaurant restaurant=DemoData.RESTAURANTS.get(index);int[] result=new int[size];
        try { JSONObject data=new JSONObject(prefs.getString("cart_"+restaurant.id,"{}"));
            for(int i=0;i<size;i++)result[i]=Math.max(0,Math.min(99,data.optInt(String.valueOf(restaurant.menu.get(i).id),0)));
        } catch(JSONException ignored) {} return result;
    }
    public void saveCart(int index,int[] quantities) {
        Restaurant restaurant=DemoData.RESTAURANTS.get(index);JSONObject data=new JSONObject();
        try {for(int i=0;i<quantities.length;i++) data.put(String.valueOf(restaurant.menu.get(i).id),quantities[i]);}
        catch(JSONException e) {throw new IllegalStateException(e);}
        prefs.edit().putString("cart_"+restaurant.id,data.toString()).apply();
    }
    public JSONArray orders() {
        JSONArray result=Api.request(context,"GET","/orders",null).optJSONArray("orders");
        return result==null?new JSONArray():result;
    }
    public void status(String id,String status) { Api.request(context,"PATCH","/orders/"+id,Api.json("status",status)); }
    public JSONObject place(JSONObject payload,int restaurantId) {
        JSONObject result=Api.request(context,"POST","/orders",payload).optJSONObject("order");
        if(result==null)throw new IllegalStateException("Không nhận được xác nhận đơn");
        prefs.edit().remove("cart_"+restaurantId).apply(); return result;
    }
}
