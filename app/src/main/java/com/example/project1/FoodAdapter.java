package com.example.project1;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.ViewHolder> {
    public interface OnFoodItemClickListener { void onFoodItemClick(int position); }
    private final List<Food> foods; private final int[] quantities; private final OnFoodItemClickListener listener;
    public FoodAdapter(List<Food> foods,int[] quantities,OnFoodItemClickListener listener) { this.foods=foods; this.quantities=quantities; this.listener=listener; }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name,price,quantity; ImageView image;
        ViewHolder(View v) { super(v); name=v.findViewById(R.id.tvName); price=v.findViewById(R.id.tvPrice); quantity=v.findViewById(R.id.tvQuantity); image=v.findViewById(R.id.ivImage); }
    }
    @Override public ViewHolder onCreateViewHolder(ViewGroup parent,int type) { return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_food,parent,false)); }
    @Override public void onBindViewHolder(ViewHolder holder,int position) {
        Food f=foods.get(position); holder.name.setText(f.name); holder.price.setText(DemoData.money(f.price)); FoodImages.show(holder.image,f);
        holder.quantity.setText(quantities[position]==0 ? "+" : "×"+quantities[position]);
        holder.itemView.setOnClickListener(v -> {int index=holder.getBindingAdapterPosition();if(index!=RecyclerView.NO_POSITION)listener.onFoodItemClick(index);});
    }
    @Override public int getItemCount() { return foods.size(); }
}
