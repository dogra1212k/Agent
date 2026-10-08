package com.dogra.agent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status, permissions, heard;
    private EditText input;
    private Button mic, session;
    private ActionExecutor typedExecutor;
    private final Runnable renderListener=this::refresh;
    private boolean pendingListen;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root=Ui.column(this); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout body=Ui.column(this); body.setPadding(Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,30)); scroll.addView(body);
        TextView brand=Ui.text(this,"A / AGENT",15,Ui.MINT); brand.setLetterSpacing(.15f); brand.setTypeface(null,Typeface.BOLD); Ui.add(body,brand,0);
        TextView title=Ui.text(this,"Your voice.\nYour phone.",36,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); Ui.add(body,title,16);
        Ui.add(body,Ui.text(this,"Hindi · Hinglish · English",14,Ui.MUTED),8);
        LinearLayout card=Ui.column(this); card.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18)); card.setBackground(Ui.bg(Ui.CARD,Ui.dp(this,22)));
        status=Ui.text(this,"",18,Ui.TEXT); heard=Ui.text(this,"",13,Ui.MUTED);
        Ui.add(card,status,0); Ui.add(card,heard,8); Ui.add(body,card,22);
        mic=Ui.button(this,"●  Tap & speak",true,v->startSession(true)); Ui.add(body,mic,16);
        session=Ui.button(this,"Start floating control",false,v->{if(SessionState.active) stopSession(); else if(AgentAccessibilityService.instance==null)explainAccessibility();else startSession(false);}); Ui.add(body,session,10);
        LinearLayout options=Ui.column(this);
        Switch handsFree=new Switch(this); handsFree.setText("Hands-free session (up to 5 min)"); handsFree.setTextColor(Ui.TEXT); handsFree.setTextSize(14); handsFree.setMinHeight(Ui.dp(this,48));
        handsFree.setChecked(prefs().getBoolean("hands_free",false));
        handsFree.setOnCheckedChangeListener((v,checked)->{prefs().edit().putBoolean("hands_free",checked).apply(); if(VoiceSessionService.instance!=null) VoiceSessionService.instance.setHandsFree(checked);});
        Ui.add(options,handsFree,0);
        Switch offline=new Switch(this); offline.setText("On-device speech only"); offline.setTextColor(Ui.TEXT); offline.setTextSize(14); offline.setMinHeight(Ui.dp(this,48));
        offline.setChecked(prefs().getBoolean("offline",false)); offline.setOnCheckedChangeListener((v,c)->{prefs().edit().putBoolean("offline",c).apply(); if(VoiceSessionService.instance!=null) VoiceSessionService.instance.resetRecognizer();}); Ui.add(options,offline,2);
        Spinner language=new Spinner(this); ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Hindi / Hinglish","English (India)"}); language.setAdapter(adapter); language.setSelection(prefs().getString("language","hi-IN").equals("hi-IN")?0:1);
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){prefs().edit().putString("language",pos==0?"hi-IN":"en-IN").apply();}}); Ui.add(options,language,6); Ui.add(body,options,10);
        Ui.add(body,Ui.text(this,"ONE-TIME SETUP",12,Ui.MINT),24);
        permissions=Ui.text(this,"",13,Ui.MUTED); Ui.add(body,permissions,8);
        Ui.add(body,Ui.button(this,"Enable screen control",false,v->explainAccessibility()),10);
        Ui.add(body,Ui.button(this,"App permissions / restricted settings",false,v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){SessionState.update("Android Settings mein Agent Voice ki App info kholiye.");}}),10);
        Ui.add(body,Ui.text(this,"Screen control lets Agent tap buttons, type and scroll across apps. Turn it on in Android Accessibility, then return here and start a session.",13,Ui.MUTED),10);
        Ui.add(body,Ui.text(this,"OR TYPE A COMMAND",12,Ui.MINT),24);
        input=new EditText(this); input.setTextColor(Ui.TEXT); input.setHintTextColor(Ui.MUTED); input.setHint("Chrome kholo aur AI news search karo"); input.setTextSize(15); input.setMaxLines(4); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); Ui.add(body,input,8);
        Ui.add(body,Ui.button(this,"Run command",false,v->runTyped()),10);
        Ui.add(body,Ui.text(this,"TRY SAYING",12,Ui.MINT),24);
        String[] samples={"Chrome kholo aur AI news search karo","YouTube par Hindi songs search karo","numbers dikhao → tap 3","type Namaste Arun","neeche jao · back · home","5 minute ka timer lagao"};
        for(String example:samples) {TextView sample=Ui.text(this,example,14,Ui.TEXT); sample.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8)); Ui.add(body,sample,2);}
        Ui.add(body,Ui.button(this,"Commands & privacy",false,v->showHelp()),14);
        Ui.add(body,Ui.text(this,"No server URL. No API key. Commands run locally; the phone’s speech provider may use the internet. This version understands listed commands, not every possible task.",12,Ui.MUTED),18);
        setContentView(root); typedExecutor=new ActionExecutor(this); SessionState.listeners.add(renderListener); refresh();
    }
    private SharedPreferences prefs(){return getSharedPreferences("agent",MODE_PRIVATE);}
    private void startSession(boolean listen) {
        pendingListen=listen;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},20); return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !prefs().getBoolean("asked_notifications",false)) {
            prefs().edit().putBoolean("asked_notifications",true).apply(); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21); return;
        }
        try {startForegroundService(new Intent(this,VoiceSessionService.class).setAction(listen?"listen":"start"));}
        catch(RuntimeException e) {SessionState.update("Session start nahi hua. App saamne rakhkar dobara try karein.");}
    }
    private void stopSession(){stopService(new Intent(this,VoiceSessionService.class));}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g); if(r==20 && (g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED)) SessionState.update("Mic permission nahi mili. Aap command type kar sakte hain."); else if(r==20||r==21) startSession(pendingListen); refresh();}
    private void explainAccessibility(){new AlertDialog.Builder(this).setTitle("Agent ko screen control dein?")
        .setMessage("Is permission se Agent screen ke visible buttons/text padh sakta hai aur aapke commands par tap, type aur scroll kar sakta hai. Screen text phone se upload nahi hota. Password fields use nahi hote.\n\nAndroid Settings → Accessibility → Agent Voice → On.\n\nAgar Restricted setting dikhe: App info → top-right menu → Allow restricted settings. Phir Accessibility par wapas aayein.\n\nFloating × ya notification ke Stop se session band kar sakte hain.")
        .setNegativeButton("Abhi nahi",null).setPositiveButton("Open Accessibility",(d,w)->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(ActivityNotFoundException e){SessionState.update("Settings app mein Accessibility kholiye.");}}).show();}
    private void runTyped(){String text=input.getText().toString().trim(); if(text.isEmpty())return; if(VoiceSessionService.instance!=null)VoiceSessionService.instance.runCommand(text);else {SessionState.record(text); typedExecutor.execute(text,result->SessionState.update(result));}}
    private void refresh(){if(status==null)return;status.setText(SessionState.status);heard.setText(SessionState.heard.isEmpty()?"Say: Chrome kholo": "You: "+SessionState.heard);
        mic.setText(SessionState.listening?"Listening… tap to restart":"●  Tap & speak");session.setText(SessionState.active?"Stop session":"Start floating control");
        permissions.setText("Microphone: "+(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"ready":"not allowed")+"\nScreen control: "+(AgentAccessibilityService.instance!=null?"connected":"off"));}
    private void showHelp(){new AlertDialog.Builder(this).setTitle("Agent commands")
        .setMessage("OPEN: Chrome kholo, WhatsApp kholo, open Settings\nSEARCH: search AI news; YouTube par music search karo; maps Delhi\nSCREEN: tap Search; numbers dikhao; tap 3; type Namaste; neeche jao; scroll up; swipe left; back; home; recent apps\nMORE: read screen; volume up/down; mute/unmute; 5 minute ka timer lagao; dial 12345; time batao\nSTOP: stop agent, रुको, floating ×, or notification Stop\n\nTap the floating mic in another app to speak there. Drag it to move. Hands-free is an explicit, limited session; it pauses after repeated silence/errors. It is not an always-on wake word.\n\nSome apps hide buttons from Accessibility. Password/lock screens are not controlled. Sensitive buttons need a tap on Confirm; payments/security actions stay manual.\n\nAgent stores only language/settings. Command history is in memory. No screen uploads, audio files, analytics, server or API key. Your chosen system speech provider may process audio online. On-device mode never falls back online; it needs Android 12+ and a supported downloaded language model.")
        .setPositiveButton("Theek hai",null).show();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){SessionState.listeners.remove(renderListener); if(typedExecutor!=null)typedExecutor.cancel(); super.onDestroy();}
}
