package com.example.project1;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
public abstract class BaseActivity extends AppCompatActivity {
    private Runnable pendingNetwork;
    protected void runWithNetwork(Runnable action){
        if(android.os.Build.VERSION.SDK_INT>=37&&checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK")!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingNetwork=action;requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"},370);}else action.run();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==370){Runnable action=pendingNetwork;pendingNetwork=null;
            if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){if(action!=null)action.run();}
            else new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Cần quyền kết nối máy chủ").setMessage("Hãy cho phép truy cập mạng nội bộ để kết nối server trên máy tính.").setPositiveButton("OK",null).show();
        }
    }
    protected void screen(int layout) {
        setContentView(layout);
        FrameLayout windowContent=findViewById(android.R.id.content);
        View content=windowContent.getChildAt(0);
        windowContent.removeView(content);
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        TextView signature=new TextView(this);
        signature.setText(R.string.student_signature);
        signature.setTextSize(11);
        signature.setTextColor(getColor(R.color.muted));
        signature.setBackgroundColor(getColor(R.color.surface));
        signature.setGravity(android.view.Gravity.CENTER);
        signature.setMinHeight(Math.round(28*getResources().getDisplayMetrics().density));
        shell.addView(signature,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        windowContent.addView(shell,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        if(android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled()){
            content.setAlpha(0f);content.setTranslationY(8f*getResources().getDisplayMetrics().density);
            content.animate().alpha(1f).translationY(0f).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(180).start();
        }
        ViewCompat.setOnApplyWindowInsetsListener(shell, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }
}
