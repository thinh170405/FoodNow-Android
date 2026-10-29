package com.example.project1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Requires python backend/run_test_server.py and an Android emulator. */
@RunWith(AndroidJUnit4.class)
public class BackendIntegrationTest {
    private void waitText(String text) {
        AssertionError last=null;
        for(int i=0;i<100;i++) {
            try {onView(withText(text)).check(matches(isDisplayed()));return;}
            catch(androidx.test.espresso.NoMatchingViewException|AssertionError error){last=new AssertionError(error);SystemClock.sleep(100);}
        }
        throw last;
    }
    @Test public void loginCheckoutDatabaseAndStatus() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences config=context.getSharedPreferences("server_config",0), session=context.getSharedPreferences("remote_session",0);
        String oldUrl=config.getString("url",null), oldToken=session.getString("token",""), oldEmail=session.getString("email","");
        AuthStore auth=new AuthStore(context);
        if(android.os.Build.VERSION.SDK_INT>=37)InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.ACCESS_LOCAL_NETWORK");
        try {
            config.edit().putString("url","http://10.0.2.2:8081").commit();auth.clearLocal();
            String email="android"+System.currentTimeMillis()+"@test.local";
            assertTrue(auth.register("Android Test",email,"Customer123"));
            assertFalse(auth.login(email,"Wrong123"));
            assertTrue(auth.login(email,"Customer123"));
            auth.saveProfile("Android Test","0912345678","123 Test Street");auth.logout();
            try(ActivityScenario<SignInActivity> login=ActivityScenario.launch(SignInActivity.class)) {
                onView(withId(R.id.txtEmail)).perform(replaceText(email));
                onView(withId(R.id.txtPassword)).perform(replaceText("Customer123"),closeSoftKeyboard());
                onView(withId(R.id.btnSignIn)).perform(scrollTo(),click());
                waitText("Circle K");
                onView(withText("Circle K")).perform(click());
                waitText("Cà phê đen");
                onView(withText("Cà phê đen")).perform(click());
                onView(withId(R.id.layoutViewBasket)).perform(click());
                onView(withText("Thanh toán")).perform(click());
                onView(withId(R.id.chooseDeliveryPoint)).perform(scrollTo(),click());
                // The pin is deliberately selected by a map tap, never by the default center.
                onView(withId(R.id.mapConfirm)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
                for(int i=0;i<60;i++){
                    onView(withId(R.id.deliveryMap)).perform(click());
                    try{onView(withId(R.id.mapConfirm)).check(matches(isEnabled()));break;}
                    catch(AssertionError error){if(i==59)throw error;SystemClock.sleep(500);}
                }
                onView(withId(R.id.mapConfirm)).perform(click());
                onView(withId(R.id.deliveryPointLabel)).check(matches(withText(org.hamcrest.Matchers.containsString("Điểm đã chọn:"))));
                onView(withId(R.id.btnConfirmPayment)).perform(click());
                onView(withText("Xác nhận")).perform(click());
                waitText("Lịch sử đơn hàng");
                JSONObject order=null;
                for(int i=0;i<50;i++){org.json.JSONArray all=new ShopStore(context).orders();if(all.length()>0){order=all.getJSONObject(0);break;}SystemClock.sleep(100);}
                assertNotNull(order);assertEquals(64000,order.getInt("total"));assertEquals("pending",order.getString("status"));
                assertFalse(order.isNull("delivery_latitude"));assertFalse(order.isNull("delivery_longitude"));
                assertEquals(10.7769,order.getDouble("delivery_latitude"),0.02);
                assertEquals(106.7009,order.getDouble("delivery_longitude"),0.02);
                assertEquals(1,new ShopStore(context).orders().length());
                assertEquals(0,new ShopStore(context).cart(0,DemoData.RESTAURANTS.get(0).menu.size())[0]);
                new ShopStore(context).status(order.getString("id"),"cancelled");
                assertEquals("cancelled",new ShopStore(context).orders().getJSONObject(0).getString("status"));
                auth.logout();assertTrue(auth.login(email,"Customer123"));
                assertEquals("123 Test Street",auth.refreshProfile().getString("address"));
                assertEquals(1,new ShopStore(context).orders().length());
            }
        } finally {
            try{if(auth.signedIn())auth.logout();}catch(Exception ignored){}
            SharedPreferences.Editor editor=config.edit();if(oldUrl==null)editor.remove("url");else editor.putString("url",oldUrl);editor.commit();
            session.edit().clear().putString("token",oldToken).putString("email",oldEmail).commit();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->DemoData.RESTAURANTS.clear());
        }
    }
}
