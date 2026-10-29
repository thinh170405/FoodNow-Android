package com.example.project1;
import java.util.ArrayList;
import java.util.List;
import org.json.*;
public class Restaurant {
    public final int id,logo,cover;
    public final String name,address,openHours;
    public final List<Food> menu=new ArrayList<>();
    public Restaurant(JSONObject data) throws JSONException {
        id=data.getInt("id");name=data.getString("name");address=data.getString("address");openHours=data.getString("hours");
        logo=DemoData.image(data.optString("image"));cover=R.drawable.cover_menu_1;
        JSONArray foods=data.getJSONArray("menu");
        for(int i=0;i<foods.length();i++) { JSONObject f=foods.getJSONObject(i);menu.add(new Food(f.getInt("id"),f.getString("name"),f.optString("image"),f.getInt("price"))); }
    }
    public String getOpenHours() { return "Giờ mở cửa: "+openHours; }
}
