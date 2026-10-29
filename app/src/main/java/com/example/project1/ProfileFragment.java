package com.example.project1;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.content.*;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
public class ProfileFragment extends Fragment {
    private String photoPath;
    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> camera=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),ok->{if(ok)uploadPhoto();else deletePhoto();});
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),ok->{if(isAdded()){if(ok)Notifications.register(requireContext());Toast.makeText(requireContext(),ok?"Đã bật thông báo":"Bạn có thể bật thông báo trong cài đặt ứng dụng",Toast.LENGTH_LONG).show();}});
    public ProfileFragment(){super(R.layout.fragment_profile);}
    @Override public void onViewCreated(View view,Bundle state){
        android.app.Activity activity=requireActivity();AuthStore auth=new AuthStore(activity);
        EditText name=view.findViewById(R.id.txtName),email=view.findViewById(R.id.txtEmail),phone=view.findViewById(R.id.txtPhone),address=view.findViewById(R.id.txtAddress);
        SharedPreferences prefs=AuthStore.userPrefs(activity);name.setText(prefs.getString("name",""));email.setText(auth.email());phone.setText(prefs.getString("phone",""));address.setText(prefs.getString("address",""));email.setEnabled(false);
        if(state!=null)photoPath=state.getString("photoPath");
        FoodImages.show(view.findViewById(R.id.avatar),prefs.getString("avatar",""),R.drawable.ic_camera);
        view.findViewById(R.id.takeAvatar).setOnClickListener(v->{try{
            java.io.File folder=new java.io.File(activity.getCacheDir(),"camera");folder.mkdirs();
            java.io.File file=java.io.File.createTempFile("avatar-",".jpg",folder);photoPath=file.getAbsolutePath();
            camera.launch(androidx.core.content.FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file));
        }catch(Exception e){deletePhoto();Toast.makeText(activity,"Không mở được camera trên thiết bị này",Toast.LENGTH_LONG).show();}});
        view.findViewById(R.id.enableNotifications).setOnClickListener(v->{if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);else{Notifications.register(activity);Toast.makeText(activity,"Thông báo đã sẵn sàng",Toast.LENGTH_SHORT).show();}});
        Button update=view.findViewById(R.id.btnUpdate);
        update.setOnClickListener(v->{String n=name.getText().toString().trim(),p=phone.getText().toString().trim(),a=address.getText().toString().trim();if(n.isEmpty()){name.setError("Nhập họ tên");return;}update.setEnabled(false);
            Api.async(activity,()->{auth.saveProfile(n,p,a);return true;},ok->{if(isAdded()){update.setEnabled(true);Toast.makeText(activity,"Đã lưu hồ sơ",Toast.LENGTH_SHORT).show();}},error->{if(isAdded()){update.setEnabled(true);Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();}});
        });
        view.findViewById(R.id.btnChangePassword).setOnClickListener(v->startActivity(new Intent(activity,AccountActivity.class).putExtra("change",true)));
        if("firebase".equals(activity.getSharedPreferences("remote_session",0).getString("provider",""))){
            ((Button)view.findViewById(R.id.btnChangePassword)).setText("Tài khoản đăng nhập Google");
            view.findViewById(R.id.btnChangePassword).setOnClickListener(v->Toast.makeText(activity,"Mật khẩu được quản lý trong tài khoản Google của bạn.",Toast.LENGTH_LONG).show());
        }
        LinearLayout root=(LinearLayout)update.getParent();Button logout=new Button(activity);logout.setText("Đăng xuất");root.addView(logout);
        logout.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("Đăng xuất?").setNegativeButton("Ở lại",null).setPositiveButton("Đăng xuất",(d,w)->{
            logout.setEnabled(false);
            Runnable exit=()->{auth.clearLocal();startActivity(new Intent(activity,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();};
            Api.async(activity,()->{auth.logout();return true;},ok->exit.run(),error->exit.run());
        }).show());
    }
    private void uploadPhoto(){
        if(!isAdded()||photoPath==null)return;android.app.Activity activity=requireActivity();String path=photoPath;
        Api.async(activity,()->{
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(path,options);
            if(options.outWidth<=0||options.outHeight<=0)throw new IllegalArgumentException("Không đọc được ảnh");
            int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>1600)sample*=2;options.inJustDecodeBounds=false;options.inSampleSize=sample;
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(path,options);if(bitmap==null)throw new IllegalArgumentException("Không đọc được ảnh");
            android.media.ExifInterface exif=new android.media.ExifInterface(path);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);android.graphics.Matrix matrix=new android.graphics.Matrix();
            if(orientation==6)matrix.postRotate(90);else if(orientation==3)matrix.postRotate(180);else if(orientation==8)matrix.postRotate(270);
            android.graphics.Bitmap corrected=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();corrected.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);if(corrected!=bitmap)corrected.recycle();bitmap.recycle();
            org.json.JSONObject result=Api.request(activity,"POST","/me/avatar",Api.json("data",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)));
            new AuthStore(activity).refreshProfile();return result.optString("image");
        },key->{deletePhoto();if(isAdded()&&getView()!=null){FoodImages.show(getView().findViewById(R.id.avatar),key,R.drawable.ic_camera);Toast.makeText(activity,"Đã lưu ảnh đại diện",Toast.LENGTH_SHORT).show();}},error->{deletePhoto();if(isAdded())Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();});
    }
    private void deletePhoto(){if(photoPath!=null)new java.io.File(photoPath).delete();photoPath=null;}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("photoPath",photoPath);}
}
