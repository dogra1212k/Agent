package com.dogra.agent;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.AlarmClock;
import android.provider.Settings;
import java.text.DateFormat;
import java.util.*;

final class ActionExecutor {
    interface Done {void complete(String message);}
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation;
    private static final Map<String,String> APPS=new HashMap<>();
    static {
        for(String n:new String[]{"chrome","क्रोम","browser","ब्राउज़र"})APPS.put(n,"com.android.chrome");
        for(String n:new String[]{"youtube","यूट्यूब"})APPS.put(n,"com.google.android.youtube");
        for(String n:new String[]{"whatsapp","व्हाट्सएप","व्हाट्सऐप","वाट्सएप"})APPS.put(n,"com.whatsapp");
        for(String n:new String[]{"gmail","जीमेल"})APPS.put(n,"com.google.android.gm");
        for(String n:new String[]{"maps","मैप्स","google maps"})APPS.put(n,"com.google.android.apps.maps");
        for(String n:new String[]{"instagram","इंस्टाग्राम"})APPS.put(n,"com.instagram.android");
        for(String n:new String[]{"telegram","टेलीग्राम"})APPS.put(n,"org.telegram.messenger");
        for(String n:new String[]{"play store","प्ले स्टोर"})APPS.put(n,"com.android.vending");
    }
    ActionExecutor(Context c){context=c;}
    void cancel(){generation++;handler.removeCallbacksAndMessages(null);}
    void execute(String text,Done done){cancel();int token=generation;List<Command> plan=new CommandParser().parse(text);run(plan,0,token,new ArrayList<>(),done);}
    private void run(List<Command> plan,int i,int token,List<String> results,Done done){
        if(token!=generation)return;
        if(i==plan.size()){done.complete(String.join("\n",results));return;}
        Command c=plan.get(i);
        if(c.kind==Command.Kind.UNKNOWN){done.complete(c.value);return;}
        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        if(keyguard!=null && keyguard.isKeyguardLocked() && c.kind!=Command.Kind.STOP){done.complete("Pehle phone khud unlock karein.");return;}
        try {act(c,(ok,message)->{if(token!=generation)return;results.add(message);if(!ok || c.kind==Command.Kind.STOP){done.complete(String.join("\n",results));return;}handler.postDelayed(()->run(plan,i+1,token,results,done),i+1<plan.size()?900:0);});}
        catch(RuntimeException e){done.complete("Android ne action rok diya. App/permission check karke dobara boliye.");}
    }
    interface Result {void finish(boolean ok,String message);}
    private void act(Command c,Result result){
        AgentAccessibilityService a=AgentAccessibilityService.instance;
        switch(c.kind){
            case OPEN -> result.finish(true,openApp(c.value));
            case SEARCH -> {launchWeb("https://www.google.com/search?q="+Uri.encode(c.value),"com.android.chrome");result.finish(true,"Search khol diya: "+c.value);}
            case YOUTUBE -> {launchWeb("https://www.youtube.com/results?search_query="+Uri.encode(c.value),"com.google.android.youtube");result.finish(true,"YouTube search: "+c.value);}
            case MAPS -> {launchWeb("https://www.google.com/maps/search/?api=1&query="+Uri.encode(c.value),"com.google.android.apps.maps");result.finish(true,"Maps search: "+c.value);}
            case VOLUME -> {AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int direction=switch(c.value){case "up"->AudioManager.ADJUST_RAISE;case "down"->AudioManager.ADJUST_LOWER;case "mute"->AudioManager.ADJUST_MUTE;default->AudioManager.ADJUST_UNMUTE;};audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,AudioManager.FLAG_SHOW_UI);result.finish(true,"Media volume: "+c.value);}
            case TIMER -> {launch(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,Integer.parseInt(c.value)*60).putExtra(AlarmClock.EXTRA_MESSAGE,"Agent timer").putExtra(AlarmClock.EXTRA_SKIP_UI,false));result.finish(true,"Timer screen khol diya. Wahan start/confirm karein.");}
            case DIAL -> {launch(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+c.value)));result.finish(true,"Dialer khol diya. Call button aap dabayein.");}
            case TIME -> result.finish(true,DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
            case HELP -> result.finish(true,"Boliye: Chrome kholo, search AI news, YouTube par music search karo, numbers dikhao, tap 3, type Namaste, neeche jao, back, home, ya stop agent.");
            case STOP -> {context.stopService(new Intent(context,VoiceSessionService.class));if(a!=null)a.hideNumbers();result.finish(true,"Session band ho gaya.");}
            default -> {if(a==null){result.finish(false,"Screen control off hai. Agent app mein Enable screen control dabayein.");return;}
                switch(c.kind){
                    case BACK -> global(a,AccessibilityService.GLOBAL_ACTION_BACK,result);
                    case HOME -> global(a,AccessibilityService.GLOBAL_ACTION_HOME,result);
                    case RECENTS -> global(a,AccessibilityService.GLOBAL_ACTION_RECENTS,result);
                    case NOTIFICATIONS -> global(a,AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,result);
                    case QUICK_SETTINGS -> global(a,AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,result);
                    case TAP -> a.tap(c.value,result);
                    case TYPE -> a.type(c.value,result);
                    case SCROLL -> a.scroll(c.value,result);
                    case READ -> a.readScreen(result);
                    case NUMBERS -> a.showNumbers(result);
                    case HIDE_NUMBERS -> {a.hideNumbers();result.finish(true,"Numbers hata diye.");}
                    default -> result.finish(false,"Yeh command abhi supported nahi hai.");
                }
            }
        }
    }
    private void global(AgentAccessibilityService a,int action,Result r){a.hideNumbers();boolean ok=a.performGlobalAction(action);r.finish(ok,ok?"Done.":"Yeh action is screen par available nahi hai.");}
    private String openApp(String name){
        if(name.equals("settings")||name.equals("सेटिंग्स")||name.equals("सेटिंग")){launch(new Intent(Settings.ACTION_SETTINGS));return "Settings khol diya.";}
        if(name.equals("camera")||name.equals("कैमरा")){launch(new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));return "Camera khol diya.";}
        PackageManager pm=context.getPackageManager();String pkg=APPS.get(name);
        Intent launchIntent=pkg==null?null:pm.getLaunchIntentForPackage(pkg);
        if(launchIntent==null){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps=pm.queryIntentActivities(query,0);List<ResolveInfo> exact=new ArrayList<>(),partial=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(ResolveInfo app:apps){if(!seen.add(app.activityInfo.packageName))continue;String label=CommandParser.normalized(app.loadLabel(pm).toString());if(label.equals(name))exact.add(app);else if(label.contains(name))partial.add(app);}
            List<ResolveInfo> matches=exact.isEmpty()?partial:exact;
            if(matches.size()!=1)throw new IllegalArgumentException(matches.isEmpty()?"App nahi mila":"App name ambiguous");
            launchIntent=pm.getLaunchIntentForPackage(matches.get(0).activityInfo.packageName);
        }
        if(launchIntent==null)throw new ActivityNotFoundException();launch(launchIntent);return name+" khol diya.";
    }
    private void launchWeb(String url,String pkg){Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg);try{launch(i);}catch(ActivityNotFoundException e){i.setPackage(null);launch(i);}}
    private void launch(Intent i){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Context launcher=AgentAccessibilityService.instance!=null?AgentAccessibilityService.instance:context;launcher.startActivity(i);}
}
