package com.dogra.agent;

import android.app.Activity;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class AppSmokeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public AppSmokeTest() { super(MainActivity.class); }
    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) { View match=find(group.getChildAt(i),text); if(match!=null)return match; }
        }
        return null;
    }
    public void testHomeRendersAndHasSetup() {
        Activity activity=getActivity();
        getInstrumentation().waitForIdleSync();
        View root=activity.getWindow().getDecorView();
        assertNotNull(find(root,"A / AGENT"));
        assertNotNull(find(root,"Enable screen control"));
        assertNotNull(find(root,"Run command"));
        assertTrue(root.getWidth()>0);
    }
    public void testLocalTypedCommandCompletes() throws Exception {
        MainActivity activity=getActivity();
        CountDownLatch done=new CountDownLatch(1);
        String[] result={null};
        getInstrumentation().runOnMainSync(() -> new ActionExecutor(activity).execute("time batao", message->{result[0]=message;done.countDown();}));
        assertTrue("Command callback",done.await(5,TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertTrue(result[0].length()>0);
    }
    public void testForegroundSessionStartsAndStops() throws Exception {
        MainActivity activity=getActivity();
        getInstrumentation().runOnMainSync(() -> activity.startForegroundService(new Intent(activity,VoiceSessionService.class).setAction("start")));
        long end=System.currentTimeMillis()+5000;
        while(!SessionState.active && System.currentTimeMillis()<end) Thread.sleep(100);
        assertTrue("Mic foreground service should run",SessionState.active);
        assertFalse("A start-only session must not record",SessionState.listening);
        getInstrumentation().runOnMainSync(() -> activity.stopService(new Intent(activity,VoiceSessionService.class)));
        end=System.currentTimeMillis()+5000;
        while(SessionState.active && System.currentTimeMillis()<end) Thread.sleep(100);
        assertFalse("Stop must end the session",SessionState.active);
        assertFalse(SessionState.listening);
    }
}
