package com.example.project1;
public class Food {
    public final String name,imageKey;
    public final int id,image,price;
    public Food(int id,String name,int image,int price) { this.id=id;this.name=name;this.image=image;this.price=price;this.imageKey=""; }
    public Food(int id,String name,String imageKey,int price) { this.id=id;this.name=name;this.imageKey=imageKey;this.image=DemoData.image(imageKey.startsWith("upload_")?"ic_banh_mi":imageKey);this.price=price; }
}
