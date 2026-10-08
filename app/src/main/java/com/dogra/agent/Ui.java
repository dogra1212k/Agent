package com.dogra.agent;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(11,18,28), CARD=Color.rgb(21,32,45), MINT=Color.rgb(102,227,196),
        TEXT=Color.rgb(240,246,255), MUTED=Color.rgb(165,181,201), PURPLE=Color.rgb(181,162,255);
    static int dp(Context c, float n) { return (int)(c.getResources().getDisplayMetrics().density*n+.5f); }
    static GradientDrawable bg(int color, float radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    static TextView text(Context c, String s, int size, int color) {
        TextView v=new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(0,1.15f); return v;
    }
    static Button button(Context c, String label, boolean accent, View.OnClickListener listener) {
        Button b=new Button(c); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(accent?BG:TEXT); b.setTypeface(null,Typeface.BOLD);
        b.setBackground(bg(accent?MINT:CARD,dp(c,16))); b.setMinHeight(dp(c,52));
        b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8)); b.setOnClickListener(listener);
        return b;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(parent.getContext(),top); parent.addView(view,lp);
    }
}
