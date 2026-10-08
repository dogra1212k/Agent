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
    public void testAccessibilityCanNumberAndScrollSettings() throws Exception {
        // Instrumentation force-stops the target at startup; reconnect afterwards.
        android.app.UiAutomation automation = getInstrumentation().getUiAutomation(
            android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        MainActivity activity=getActivity();
        String[] commands = {
            "appops set com.dogra.agent ACCESS_RESTRICTED_SETTINGS allow",
            "settings put secure enabled_accessibility_services null",
            "settings put secure accessibility_enabled 0",
            "settings put secure enabled_accessibility_services com.dogra.agent/com.dogra.agent.AgentAccessibilityService",
            "settings put secure accessibility_enabled 1"
        };
        for (String command : commands) {
            try (android.os.ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
                 java.io.FileInputStream stream = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
                byte[] buffer = new byte[1024];
                while (stream.read(buffer) != -1) { }
            }
        }
        long end=System.currentTimeMillis()+15000;
        while(AgentAccessibilityService.instance==null && System.currentTimeMillis()<end)Thread.sleep(100);
        assertNotNull("Accessibility must connect",AgentAccessibilityService.instance);
        try (android.os.ParcelFileDescriptor descriptor = automation.executeShellCommand("am start -W -a android.settings.SETTINGS");
             java.io.FileInputStream stream = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { }
        }
        end=System.currentTimeMillis()+15000;
        boolean settingsReady=false;
        while(!settingsReady && System.currentTimeMillis()<end) {
            android.view.accessibility.AccessibilityNodeInfo root=AgentAccessibilityService.instance.getRootInActiveWindow();
            if(root!=null) {
                settingsReady="com.android.settings".contentEquals(root.getPackageName());
                root.recycle();
            }
            if(!settingsReady) Thread.sleep(100);
        }
        assertTrue("Settings must become the active accessibility window",settingsReady);
        CountDownLatch numbered=new CountDownLatch(1);
        boolean[] ok={false};
        String[] detail={"No callback"};
        getInstrumentation().runOnMainSync(() -> AgentAccessibilityService.instance.showNumbers((success,message)->{ok[0]=success;detail[0]=message;numbered.countDown();}));
        assertTrue(numbered.await(5,TimeUnit.SECONDS));
        assertTrue("Settings should expose numbered controls: "+detail[0],ok[0]);
        CountDownLatch scrolled=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> AgentAccessibilityService.instance.scroll("down",(success,message)->{ok[0]=success;scrolled.countDown();}));
        assertTrue(scrolled.await(5,TimeUnit.SECONDS));
        assertTrue("Settings should scroll",ok[0]);
        getInstrumentation().runOnMainSync(() -> AgentAccessibilityService.instance.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
    }
}
