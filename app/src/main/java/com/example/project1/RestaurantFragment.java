package com.example.project1;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
public class RestaurantFragment extends Fragment {
    public RestaurantFragment() { super(R.layout.fragment_restaurant); }
    @Override public void onViewCreated(View view, Bundle state) {
        RecyclerView list=view.findViewById(R.id.rvRestaurants);
        list.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1,0,1));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        android.widget.LinearLayout root=(android.widget.LinearLayout)view;
        android.widget.EditText search=new android.widget.EditText(requireContext()); search.setHint("Tìm nhà hàng hoặc món ăn"); search.setSingleLine(true);
        int spacing=Math.round(16*getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams searchParams=new android.widget.LinearLayout.LayoutParams(-1,-2);searchParams.setMargins(spacing,0,spacing,spacing);search.setLayoutParams(searchParams);root.addView(search,1);
        android.widget.TextView empty=new android.widget.TextView(requireContext()); empty.setText("Không tìm thấy kết quả. Thử tên món hoặc nhà hàng khác nhé.");empty.setPadding(spacing,spacing,spacing,spacing); root.addView(empty,2);
        java.util.function.Consumer<String> filter=query->{
            java.util.List<Restaurant> matches=new java.util.ArrayList<>();
            String key=normalize(query);
            for(Restaurant r:DemoData.RESTAURANTS) {
                boolean found=normalize(r.name+" "+r.address).contains(key);
                for(Food food:r.menu) if(normalize(food.name).contains(key)) found=true;
                if(found) matches.add(r);
            }
            empty.setVisibility(matches.isEmpty()?View.VISIBLE:View.GONE);
            list.setAdapter(new RestaurantAdapter(matches, restaurant -> startActivity(new Intent(requireContext(),RestaurantActivity.class).putExtra("restaurantId",restaurant.id))));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { filter.accept(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        }); filter.accept("");
    }
    private static String normalize(String value) { return java.text.Normalizer.normalize(value.toLowerCase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d'); }
}
