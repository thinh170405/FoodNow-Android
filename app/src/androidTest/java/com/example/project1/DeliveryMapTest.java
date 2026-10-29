package com.example.project1;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.action.ViewActions.click;
@RunWith(AndroidJUnit4.class)
public class DeliveryMapTest {
    @Test public void existingPinSurvivesRecreationAndConfirms() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(context,DeliveryMapActivity.class).putExtra("latitude",10.75).putExtra("longitude",106.7);
        try(ActivityScenario<DeliveryMapActivity> scenario=ActivityScenario.launchActivityForResult(intent)){
            onView(withId(R.id.mapConfirm)).check(matches(isEnabled()));
            scenario.recreate();
            onView(withId(R.id.mapHint)).check(matches(withText("Điểm giao: 10.750000, 106.700000")));
            onView(withId(R.id.mapConfirm)).perform(click());
            assertEquals(Activity.RESULT_OK,scenario.getResult().getResultCode());
            assertEquals(10.75,scenario.getResult().getResultData().getDoubleExtra("latitude",0),0);
        }
    }
    @Test public void cancelDoesNotSelectDefaultCenter(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<DeliveryMapActivity> scenario=ActivityScenario.launchActivityForResult(new Intent(context,DeliveryMapActivity.class))){
            onView(withId(R.id.mapConfirm)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            pressBack();assertEquals(Activity.RESULT_CANCELED,scenario.getResult().getResultCode());
        }
    }
}
