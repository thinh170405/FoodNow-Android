package com.example.project1;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import org.json.*;
public class OrderFragment extends Fragment {
    private LinearLayout content;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle state){
        LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),0);
        TextView title=new TextView(requireContext());title.setText("Lịch sử đơn hàng");title.setTextSize(24);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(0,0,0,dp(20));root.addView(title);
        ScrollView scroll=new ScrollView(requireContext());content=new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        return root;
    }
    @Override public void onResume(){super.onResume();if(content!=null)load();}
    private void load(){
        LinearLayout target=content;TextView loading=new TextView(requireContext());loading.setText("Đang tải đơn hàng…");content.removeAllViews();content.addView(loading);
        android.content.Context context=requireContext();
        Api.async(requireActivity(),()->new ShopStore(context).orders(),orders->{if(!isAdded()||content!=target)return;render(orders);},error->{if(!isAdded()||content!=target)return;loading.setText(error.getMessage());Button retry=new Button(context);retry.setText("Thử lại");target.addView(retry);retry.setOnClickListener(v->load());});
    }
    private void render(JSONArray orders){
        content.removeAllViews();
        if(orders.length()==0){TextView empty=new TextView(requireContext());empty.setText("Chưa có đơn hàng. Hãy chọn món ở Trang chủ.");content.addView(empty);}
        for(int i=0;i<orders.length();i++){
            JSONObject order=orders.optJSONObject(i);if(order==null)continue;
            LinearLayout card=new LinearLayout(requireContext());card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(12));card.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(16);content.addView(card,cardParams);
            TextView badge=new TextView(requireContext());badge.setText(order.optString("statusLabel"));badge.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),R.color.green));badge.setTypeface(null,android.graphics.Typeface.BOLD);badge.setTextSize(13);badge.setPadding(dp(12),dp(8),dp(12),dp(8));badge.setBackgroundResource(R.drawable.bg_soft);card.addView(badge,new LinearLayout.LayoutParams(-2,-2));
            TextView details=new TextView(requireContext());details.setTextSize(15);details.setLineSpacing(dp(3),1f);details.setPadding(0,dp(12),0,dp(12));
            details.setText(order.optString("details"));card.addView(details);
            double lat=order.optDouble("delivery_latitude",Double.NaN),lng=order.optDouble("delivery_longitude",Double.NaN);
            if(DeliveryMapActivity.valid(lat,lng)){
                Button map=new Button(requireContext());map.setText("Xem điểm giao hàng");card.addView(map);
                map.setOnClickListener(v->startActivity(new android.content.Intent(requireContext(),DeliveryMapActivity.class).putExtra("latitude",lat).putExtra("longitude",lng).putExtra("readonly",true)));
            }
            String status=order.optString("status"),id=order.optString("id");
            Button chat=new Button(requireContext());chat.setText("Nhắn tin về đơn hàng");card.addView(chat);
            chat.setOnClickListener(v->startActivity(new android.content.Intent(requireContext(),ChatActivity.class).putExtra("orderId",id)));
            if(status.equals("delivering")){
                Button track=new Button(requireContext());track.setText("Theo dõi người giao hàng");card.addView(track);
                track.setOnClickListener(v->startActivity(new android.content.Intent(requireContext(),DeliveryMapActivity.class).putExtra("orderId",id).putExtra("latitude",lat).putExtra("longitude",lng).putExtra("readonly",true)));
            }
            if(status.equals("pending")||status.equals("confirmed"))action(card,id,"Hủy đơn","cancelled");
            if(status.equals("delivering"))action(card,id,"Đã nhận hàng","completed");
        }
    }
    private void action(LinearLayout card,String id,String label,String status){
        Button button=new Button(requireContext());button.setText(label);card.addView(button);
        button.setOnClickListener(v->new AlertDialog.Builder(requireContext()).setTitle(label+"?").setMessage("Xác nhận cập nhật trạng thái đơn hàng.")
            .setNegativeButton("Quay lại",null).setPositiveButton("Xác nhận",(d,w)->{
                button.setEnabled(false);android.content.Context context=requireContext();
                Api.async(requireActivity(),()->{new ShopStore(context).status(id,status);return true;},ok->{if(isAdded()&&content!=null)load();},error->{if(isAdded()&&content!=null){button.setEnabled(true);Toast.makeText(context,error.getMessage(),Toast.LENGTH_LONG).show();}});
            }).show());
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onDestroyView(){content=null;super.onDestroyView();}
}
