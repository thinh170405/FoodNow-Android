package com.example.project1;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
public class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.ViewHolder> {
    public interface OnRestaurantItemClickListener { void onRestaurantItemClick(Restaurant restaurant); }
    private final List<Restaurant> restaurants;
    private final OnRestaurantItemClickListener listener;
    public RestaurantAdapter(List<Restaurant> restaurants,OnRestaurantItemClickListener listener) { this.restaurants=restaurants; this.listener=listener; }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name,address; ImageView image;
        ViewHolder(View v) { super(v); name=v.findViewById(R.id.tvName); address=v.findViewById(R.id.tvAddress); image=v.findViewById(R.id.ivImage); }
    }
    @Override public ViewHolder onCreateViewHolder(ViewGroup parent,int type) { return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_restaurant,parent,false)); }
    @Override public void onBindViewHolder(ViewHolder holder,int position) {
        Restaurant r=restaurants.get(position); holder.name.setText(r.name); holder.address.setText(r.address); holder.image.setImageResource(r.logo);
        holder.itemView.setOnClickListener(v -> listener.onRestaurantItemClick(r));
    }
    @Override public int getItemCount() { return restaurants.size(); }
}
