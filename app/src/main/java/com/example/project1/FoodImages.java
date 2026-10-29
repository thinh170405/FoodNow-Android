package com.example.project1;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
/** Bounded image downloads; recycled rows are protected by tags. */
public final class FoodImages {
    private static final java.util.concurrent.ExecutorService WORKERS=java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final android.os.Handler MAIN=new android.os.Handler(android.os.Looper.getMainLooper());
    private static final android.util.LruCache<String,Bitmap> CACHE=new android.util.LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    public static void show(ImageView view,Food food){show(view,food.imageKey,food.image);}
    public static void show(ImageView view,String key,int fallback){
        view.setImageResource(fallback);view.setTag(null);
        if(!key.matches("upload_[a-f0-9]{64}"))return;
        String url=Api.base(view.getContext())+"/uploads/"+key+".jpg";view.setTag(url);
        Bitmap cached=CACHE.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        WORKERS.execute(()->{java.net.HttpURLConnection connection=null;
            try{connection=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();connection.setConnectTimeout(7000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);if(connection.getResponseCode()!=200)return;
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>5*1024*1024)return;bytes.write(buffer,0,n);}}
                byte[] data=bytes.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
                if(options.outWidth<=0||options.outHeight<=0||options.outWidth>1600||options.outHeight>1600)return;
                int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>400)sample*=2;options.inSampleSize=sample;options.inJustDecodeBounds=false;
                Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(image!=null){CACHE.put(url,image);MAIN.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(image);});}
            }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        });
    }
}
