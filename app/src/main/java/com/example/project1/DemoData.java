package com.example.project1;
import java.util.*;
import java.text.NumberFormat;
import org.json.*;
import android.content.Context;
/** Catalog fetched from the shared backend. */
public final class DemoData {
    public static final List<Restaurant> RESTAURANTS=new ArrayList<>();
    public static int deliveryFee=15000;
    public static JSONObject fetch(Context context) { return Api.request(context,"GET","/catalog",null); }
    public static void load(JSONObject data) {
        try {
            List<Restaurant> loaded=new ArrayList<>(); JSONArray rows=data.getJSONArray("restaurants");
            for(int i=0;i<rows.length();i++) loaded.add(new Restaurant(rows.getJSONObject(i)));
            RESTAURANTS.clear(); RESTAURANTS.addAll(loaded); deliveryFee=data.getInt("deliveryFee");
        } catch(JSONException e) { throw new IllegalStateException("Thực đơn không hợp lệ",e); }
    }
    public static int indexOfId(int id) { for(int i=0;i<RESTAURANTS.size();i++) if(RESTAURANTS.get(i).id==id)return i; return -1; }
    public static String money(long value) { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value)+" VND"; }
    public static int image(String key) {
        switch(key) {
            case "ic_banh_mi":return R.drawable.ic_banh_mi;
            case "ic_milk_tea":return R.drawable.ic_milk_tea;
            case "ic_black_coffee":return R.drawable.ic_black_coffee;
            case "ic_ministop":return R.drawable.ic_ministop;
            case "ic_seveneleven":return R.drawable.ic_seveneleven;
            case "ic_vinmart":return R.drawable.ic_vinmart;
            case "cover_menu_1":return R.drawable.cover_menu_1;
            default:return R.drawable.ic_circlek;
        }
    }
}
