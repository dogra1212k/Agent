package com.dogra.agent;

import android.accessibilityservice.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

/** User-initiated control of the current foreground application; no event text is recorded. */
public class AgentAccessibilityService extends AccessibilityService {
    static AgentAccessibilityService instance;
    private WindowManager manager;
    private LinearLayout bubble;
    private TextView bubbleMic;
    private WindowManager.LayoutParams bubbleParams;
    private NumberOverlay numbersView;
    private final List<Target> targets=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AlertDialog confirmation;
    private int actionEpoch;
    private long numberedAt;
    private final Runnable expireNumbers=this::hideNumbers;
    private static class Target {
        final AccessibilityNodeInfo node;
        final Rect bounds=new Rect();
        final String label,pkg;
        final int window;
        Target(AccessibilityNodeInfo n){node=AccessibilityNodeInfo.obtain(n);n.getBoundsInScreen(bounds);label=label(n);pkg=String.valueOf(n.getPackageName());window=n.getWindowId();}
        void release(){node.recycle();}
    }
    @Override protected void onServiceConnected(){instance=this;manager=(WindowManager)getSystemService(WINDOW_SERVICE);SessionState.update("Screen control connected. Agent mein session start karein.");}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        // Content is used only on explicit commands. Window transitions invalidate numbered targets.
        if(!targets.isEmpty() && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName()!=null && !getPackageName().contentEquals(event.getPackageName())) hideNumbers();
    }
    @Override public void onInterrupt(){hideNumbers();cancelConfirmation();if(VoiceSessionService.instance!=null)VoiceSessionService.instance.pause("Screen control interrupt hua. Mic dobara tap karein.");}
    @Override public void onDestroy(){hideNumbers();removeBubble();cancelConfirmation();if(instance==this)instance=null;stopService(new Intent(this,VoiceSessionService.class));SessionState.update("Screen control disconnected.");super.onDestroy();}
    private boolean unlocked(){KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);return k==null||!k.isKeyguardLocked();}
    private AccessibilityNodeInfo root(){
        if(!unlocked())return null;
        AccessibilityNodeInfo r=getRootInActiveWindow();
        if(r!=null && getPackageName().contentEquals(String.valueOf(r.getPackageName()))){r.recycle();return null;}
        return r;
    }
    private static List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root){
        List<AccessibilityNodeInfo> all=new ArrayList<>();if(root==null)return all;all.add(root);
        for(int i=0;i<all.size() && all.size()<450;i++) {AccessibilityNodeInfo n=all.get(i);if(n.isPassword())continue;for(int j=0;j<n.getChildCount() && all.size()<450;j++){AccessibilityNodeInfo child=n.getChild(j);if(child!=null)all.add(child);}}
        return all;
    }
    private static void release(List<AccessibilityNodeInfo> list){for(AccessibilityNodeInfo n:list)n.recycle();}
    private static String ownLabel(AccessibilityNodeInfo n){if(n.isPassword())return "";CharSequence t=n.getText();if(t==null||t.length()==0)t=n.getContentDescription();return t==null?"":t.toString().trim();}
    private static String label(AccessibilityNodeInfo n){
        String own=ownLabel(n);if(!own.isEmpty())return own;
        StringBuilder b=new StringBuilder();for(int i=0;i<n.getChildCount()&&i<8;i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null){String value=ownLabel(child);if(!value.isEmpty()){if(b.length()>0)b.append(' ');b.append(value);}child.recycle();}}
        return b.toString();
    }
    private boolean valid(Target t){
        AccessibilityNodeInfo r=root();if(r==null)return false;
        boolean same=r.getWindowId()==t.window && t.pkg.equals(String.valueOf(r.getPackageName()));r.recycle();
        if(!same || !t.node.refresh() || !t.node.isVisibleToUser() || !t.node.isEnabled() || t.node.isPassword())return false;
        Rect now=new Rect();t.node.getBoundsInScreen(now);return now.equals(t.bounds)&&label(t.node).equals(t.label);
    }
    void tap(String query,ActionExecutor.Result result){
        if(confirmation!=null){result.finish(false,"Pehle screen par Confirm ya Cancel dabayein.");return;}
        Target target=null;
        if(query.matches("[0-9]+")){
            int index;try{index=Integer.parseInt(query)-1;}catch(NumberFormatException e){index=-1;}
            if(index<0||index>=targets.size()||SystemClock.elapsedRealtime()-numberedAt>45000){result.finish(false,"Pehle numbers dikhao boliye; phir screen par dikh raha number boliye.");return;}
            if(!valid(targets.get(index))){hideNumbers();result.finish(false,"Screen badal gayi. Dobara numbers dikhao boliye.");return;}
            target=new Target(targets.get(index).node);
        } else {
            List<AccessibilityNodeInfo> all=nodes(root());List<AccessibilityNodeInfo> exact=new ArrayList<>(),partial=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(AccessibilityNodeInfo n:all){if(!n.isVisibleToUser()||!n.isEnabled()||n.isPassword()||(!n.isClickable()&&!n.isEditable()))continue;
                String text=CommandParser.normalized(label(n));Rect b=new Rect();n.getBoundsInScreen(b);String key=b.toShortString()+text;if(!seen.add(key))continue;
                if(text.equals(query))exact.add(n);else if(!query.isEmpty()&&text.contains(query))partial.add(n);
            }
            List<AccessibilityNodeInfo> matches=exact.isEmpty()?partial:exact;
            if(matches.size()==1)target=new Target(matches.get(0));
            int count=matches.size();release(all);
            if(target==null){result.finish(false,count==0?"Button nahi mila. Numbers dikhao boliye.":"Ek se zyada buttons mile. Numbers dikhao, phir tap number boliye.");return;}
        }
        hideNumbers();String sensitivity=CommandParser.normalized(target.label);
        if(sensitivity.matches(".*(?:\\bpay\\b|\\bbuy\\b|purchase|checkout|transfer|password|passcode|unlock|allow|permission|install|भुगतान|खरीद|अनुमति|पासवर्ड|इंस्टॉल).*")){
            target.release();result.finish(false,"Payment, password aur permission buttons phone par khud tap karein.");return;
        }
        if(sensitivity.matches(".*(?:\\bsend\\b|delete|remove|submit|confirm|post|publish|भेज|मिटा|हटाओ|डिलीट|सबमिट|पुष्टि).*")) {
            confirmTap(target,result);return;
        }
        performTap(target,result);
    }
    private void performTap(Target target,ActionExecutor.Result result){
        if(!valid(target)){target.release();result.finish(false,"Button badal gaya. Dobara command dein.");return;}
        boolean ok=target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);target.release();
        result.finish(ok,ok?"Tap kiya.":"Is button par tap available nahi hai. Phone par manually tap karein.");
    }
    private void confirmTap(Target target,ActionExecutor.Result result){
        if(VoiceSessionService.instance!=null)VoiceSessionService.instance.pauseForConfirmation();
        confirmation=new AlertDialog.Builder(this).setTitle("Confirm: "+target.label)
            .setMessage("Is button ka action current app mein hoga. Sirf tab confirm karein jab yahi aapka irada ho.")
            .setNegativeButton("Cancel",(d,w)->{target.release();result.finish(false,"Cancel kiya.");})
            .setPositiveButton("Confirm tap",(d,w)->{confirmation=null;int epoch=actionEpoch;handler.postDelayed(()->{if(epoch!=actionEpoch){target.release();return;}performTap(target,result);},250);})
            .setCancelable(false).create();
        confirmation.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        confirmation.setOnDismissListener(d->confirmation=null);confirmation.show();
    }
    void cancelConfirmation(){actionEpoch++;if(confirmation!=null){confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();confirmation=null;}}
    boolean hasConfirmation(){return confirmation!=null;}
    void type(String text,ActionExecutor.Result result){
        hideNumbers();AccessibilityNodeInfo r=root();if(r==null){result.finish(false,"Target app kholiye aur uske text box par tap karein.");return;}
        AccessibilityNodeInfo focus=r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);r.recycle();
        if(focus==null||!focus.isVisibleToUser()||!focus.isEditable()||focus.isPassword()) {if(focus!=null)focus.recycle();result.finish(false,"Pehle non-password text box par tap karein. Phir type command boliye.");return;}
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        boolean ok=focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);focus.recycle();
        result.finish(ok,ok?"Text box mein likh diya. Purana text replace hua; send nahi hua.":"Yeh app voice typing allow nahi kar raha.");
    }
    void scroll(String direction,ActionExecutor.Result result){
        hideNumbers();AccessibilityNodeInfo r=root();if(r==null){result.finish(false,"Pehle target app saamne kholiye.");return;}
        Rect bounds=new Rect();r.getBoundsInScreen(bounds);List<AccessibilityNodeInfo> all=nodes(r);
        boolean vertical=direction.equals("down")||direction.equals("up");boolean ok=false;
        if(vertical)for(AccessibilityNodeInfo n:all)if(n.isVisibleToUser()&&n.isScrollable()){ok=n.performAction(direction.equals("down")?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);if(ok)break;}
        release(all);if(ok){result.finish(true,"Scroll kiya.");return;}
        if(bounds.width()<40||bounds.height()<100){result.finish(false,"Scrollable area nahi mila.");return;}
        float x=bounds.exactCenterX(),y=bounds.exactCenterY(),left=bounds.left+bounds.width()*.22f,right=bounds.left+bounds.width()*.78f,top=bounds.top+bounds.height()*.3f,bottom=bounds.top+bounds.height()*.72f;
        Path path=new Path();switch(direction){case "down"->{path.moveTo(x,bottom);path.lineTo(x,top);}case "up"->{path.moveTo(x,top);path.lineTo(x,bottom);}case "left"->{path.moveTo(right,y);path.lineTo(left,y);}default->{path.moveTo(left,y);path.lineTo(right,y);}}
        boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,350)).build(),new GestureResultCallback(){@Override public void onCompleted(GestureDescription g){result.finish(true,"Swipe kiya.");}@Override public void onCancelled(GestureDescription g){result.finish(false,"Swipe cancel hua. Dobara boliye.");}},handler);
        if(!accepted)result.finish(false,"Android ne gesture allow nahi kiya.");
    }
    void readScreen(ActionExecutor.Result result){
        List<AccessibilityNodeInfo> all=nodes(root());LinkedHashSet<String> text=new LinkedHashSet<>();int size=0;
        for(AccessibilityNodeInfo n:all){String value=ownLabel(n);if(n.isVisibleToUser()&&!n.isPassword()&&!value.isEmpty()){if(text.add(value))size+=value.length();if(size>=1200)break;}}
        release(all);String content=String.join(". ",text);if(content.length()>1200)content=content.substring(0,1200);
        result.finish(!content.isEmpty(),content.isEmpty()?"Is app ka readable text nahi mila.":content);
    }
    void showNumbers(ActionExecutor.Result result){
        hideNumbers();List<AccessibilityNodeInfo> all=nodes(root());Set<String> seen=new HashSet<>();
        for(AccessibilityNodeInfo n:all){if(!n.isVisibleToUser()||!n.isEnabled()||n.isPassword()||(!n.isClickable()&&!n.isEditable()))continue;Rect b=new Rect();n.getBoundsInScreen(b);if(b.isEmpty()||!seen.add(b.toShortString()))continue;targets.add(new Target(n));if(targets.size()>=35)break;}
        release(all);if(targets.isEmpty()){result.finish(false,"Is screen par accessible buttons nahi mile.");return;}
        numberedAt=SystemClock.elapsedRealtime();numbersView=new NumberOverlay();
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;manager.addView(numbersView,p);handler.postDelayed(expireNumbers,45000);result.finish(true,targets.size()+" numbers dikh rahe hain. Tap number boliye.");
    }
    void hideNumbers(){handler.removeCallbacks(expireNumbers);if(numbersView!=null&&manager!=null){try{manager.removeView(numbersView);}catch(IllegalArgumentException ignored){}numbersView=null;}for(Target t:targets)t.release();targets.clear();}
    private class NumberOverlay extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        NumberOverlay(){super(AgentAccessibilityService.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int[] origin=new int[2];getLocationOnScreen(origin);for(int i=0;i<targets.size();i++){Rect r=targets.get(i).bounds;float x=Math.max(Ui.dp(getContext(),15),Math.min(getWidth()-Ui.dp(getContext(),15),r.left-origin[0]+Ui.dp(getContext(),13)));float y=Math.max(Ui.dp(getContext(),15),Math.min(getHeight()-Ui.dp(getContext(),15),r.top-origin[1]+Ui.dp(getContext(),13)));paint.setColor(Ui.MINT);canvas.drawCircle(x,y,Ui.dp(getContext(),13),paint);paint.setColor(Ui.BG);paint.setTextSize(Ui.dp(getContext(),13));paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(""+(i+1),x,y-(paint.ascent()+paint.descent())/2,paint);}}
    }
    void refreshBubble(){
        if(manager==null)return;
        if(!SessionState.active){removeBubble();hideNumbers();cancelConfirmation();return;}
        if(bubble==null){
            bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.HORIZONTAL);bubble.setGravity(Gravity.CENTER);bubble.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);bubble.setBackground(Ui.bg(Ui.CARD,Ui.dp(this,30)));
            bubbleMic=Ui.text(this,"●",24,Ui.MINT);bubbleMic.setGravity(Gravity.CENTER);bubbleMic.setContentDescription("Agent microphone. Tap to speak; drag to move.");bubble.addView(bubbleMic,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,56)));
            TextView close=Ui.text(this,"×",26,Ui.MUTED);close.setGravity(Gravity.CENTER);close.setContentDescription("Stop Agent session");bubble.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,56)));close.setOnClickListener(v->stopService(new Intent(this,VoiceSessionService.class)));
            bubbleParams=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;bubbleParams.x=Ui.dp(this,12);bubbleParams.y=Ui.dp(this,180);
            bubbleMic.setOnClickListener(v->{if(VoiceSessionService.instance!=null)VoiceSessionService.instance.listen();});
            bubbleMic.setOnTouchListener(new View.OnTouchListener(){float x,y;int bx,by;boolean moved;public boolean onTouch(View v,android.view.MotionEvent event){switch(event.getActionMasked()){case android.view.MotionEvent.ACTION_DOWN:x=event.getRawX();y=event.getRawY();bx=bubbleParams.x;by=bubbleParams.y;moved=false;return true;case android.view.MotionEvent.ACTION_MOVE:float dx=event.getRawX()-x,dy=event.getRawY()-y;if(Math.abs(dx)+Math.abs(dy)>Ui.dp(AgentAccessibilityService.this,8))moved=true;if(moved){int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;bubbleParams.x=Math.max(0,Math.min(width-bubble.getWidth(),bx+(int)dx));bubbleParams.y=Math.max(0,Math.min(height-bubble.getHeight()-Ui.dp(AgentAccessibilityService.this,24),by+(int)dy));manager.updateViewLayout(bubble,bubbleParams);}return true;case android.view.MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;default:return false;}}});
            manager.addView(bubble,bubbleParams);
        }
        bubbleMic.setText(SessionState.listening?"•••":"●");bubbleMic.setTextColor(SessionState.listening?Ui.PURPLE:Ui.MINT);
    }
    private void removeBubble(){if(bubble!=null&&manager!=null){try{manager.removeView(bubble);}catch(IllegalArgumentException ignored){}bubble=null;bubbleMic=null;}}
}
