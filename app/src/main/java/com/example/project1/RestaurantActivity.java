package com.example.project1;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
public class RestaurantActivity extends BaseActivity implements FoodAdapter.OnFoodItemClickListener {
    private Restaurant restaurant; private int[] quantities; private FoodAdapter adapter;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.activity_restaurant);
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        if(!new AuthStore(this).signedIn()) { startActivity(new Intent(this,SignInActivity.class)); finish(); return; }
        if(DemoData.RESTAURANTS.isEmpty()) Api.async(this,()->DemoData.fetch(this),data->{DemoData.load(data);initializeMenu();},error->{Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();finish();});
        else initializeMenu();
    }
    private void initializeMenu() {
        int index=DemoData.indexOfId(getIntent().getIntExtra("restaurantId",-1));
        if(index<0) { Toast.makeText(this,"Nhà hàng không còn phục vụ",Toast.LENGTH_LONG).show(); finish(); return; }
        restaurant=DemoData.RESTAURANTS.get(index);
        quantities=new ShopStore(this).cart(index,restaurant.menu.size());
        if (quantities == null || quantities.length != restaurant.menu.size()) {
            quantities = new int[restaurant.menu.size()];
        }
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        ((TextView)findViewById(R.id.tvName)).setText(restaurant.name);
        ((TextView)findViewById(R.id.tvAddress)).setText(restaurant.address);
        ((TextView)findViewById(R.id.tvOpenHours)).setText(restaurant.getOpenHours());
        ((ImageView)findViewById(R.id.ivCover)).setImageResource(restaurant.cover);
        RecyclerView list=findViewById(R.id.rvFoods); list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new FoodAdapter(restaurant.menu,quantities,this); list.setAdapter(adapter);
        findViewById(R.id.layoutViewBasket).setOnClickListener(v -> basket()); updateBasket();
    }
    @Override public void onFoodItemClick(int position) { if(position<0)return; if(quantities[position]<99) quantities[position]++; adapter.notifyItemChanged(position); updateBasket(); }
    private int count() { int result=0; for(int q:quantities)result+=q; return result; }
    private long total() { long result=0; for(int i=0;i<quantities.length;i++)result+=(long)quantities[i]*restaurant.menu.get(i).price; return result; }
    private void updateBasket() { new ShopStore(this).saveCart(DemoData.RESTAURANTS.indexOf(restaurant),quantities); ((TextView)findViewById(R.id.tvTotalItems)).setText(count()+" món"); ((TextView)findViewById(R.id.tvTotalPrices)).setText(DemoData.money(total())); }
    private void basket() {
        if(count()==0) { Toast.makeText(this,"Chạm món ăn để thêm vào giỏ",Toast.LENGTH_SHORT).show(); return; }
        LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(24,16,24,16);
        TextView totalView=new TextView(this);
        for(int i=0;i<quantities.length;i++) if(quantities[i]>0) {
            final int item=i;
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            TextView label=new TextView(this); label.setText(restaurant.menu.get(i).name); row.addView(label);
            LinearLayout controls=new LinearLayout(this);
            Button minus=new Button(this), plus=new Button(this), remove=new Button(this);
            TextView quantity=new TextView(this); quantity.setGravity(android.view.Gravity.CENTER); quantity.setText(String.valueOf(quantities[i]));
            minus.setText("−"); plus.setText("+"); remove.setText("Xóa");
            controls.addView(minus,new LinearLayout.LayoutParams(0,-2,1)); controls.addView(quantity,new LinearLayout.LayoutParams(0,-1,1));
            controls.addView(plus,new LinearLayout.LayoutParams(0,-2,1)); controls.addView(remove,new LinearLayout.LayoutParams(0,-2,1)); row.addView(controls); rows.addView(row);
            Runnable refresh=()->{ quantity.setText(String.valueOf(quantities[item])); adapter.notifyItemChanged(item); updateBasket(); totalView.setText("Tổng tiền món: "+DemoData.money(total())); if(quantities[item]==0) row.setVisibility(android.view.View.GONE); };
            minus.setOnClickListener(v->{ if(quantities[item]>0) quantities[item]--; refresh.run(); });
            plus.setOnClickListener(v->{ if(quantities[item]<99) quantities[item]++; refresh.run(); });
            remove.setOnClickListener(v->{ quantities[item]=0; refresh.run(); });
        }
        totalView.setText("Tổng tiền món: "+DemoData.money(total())); rows.addView(totalView);
        ScrollView scroll=new ScrollView(this); scroll.addView(rows);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Giỏ hàng · "+restaurant.name).setView(scroll)
            .setNegativeButton("Chọn tiếp",null).setNeutralButton("Xóa tất cả",(d,w)->{ java.util.Arrays.fill(quantities,0); adapter.notifyDataSetChanged(); updateBasket(); })
            .setPositiveButton("Thanh toán",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(count()==0) { Toast.makeText(this,"Giỏ hàng trống",Toast.LENGTH_SHORT).show(); return; }
                dialog.dismiss();
                startActivity(new Intent(this,CheckoutActivity.class)
                    .putExtra("restaurantId",restaurant.id)
                    .putExtra("foodIds",foodIds())
                    .putExtra("quantities",quantities));
            }));
        dialog.show();
    }
    private int[] foodIds() { int[] ids=new int[restaurant.menu.size()]; for(int i=0;i<ids.length;i++)ids[i]=restaurant.menu.get(i).id; return ids; }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putIntArray("quantities",quantities); }
}
