package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

final class BottomNavBar extends LinearLayout {
    interface Listener { void onDestination(String destination); }

    private final boolean expanded;
    private final boolean compactHeight;
    private final String current;
    private final Listener listener;

    BottomNavBar(Context c, boolean expanded, boolean compactHeight, String current, Listener listener) {
        super(c);
        this.expanded = expanded;
        this.compactHeight = compactHeight;
        this.current = current;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(Ui.dp(c, 4), Ui.dp(c, compactHeight?2:5), Ui.dp(c, 4), Ui.dp(c, compactHeight?2:5));
        setBackground(Ui.panel(c));
        build();
    }

    private void build() {
        removeAllViews();
        String[][] items = (expanded && !compactHeight)
                ? new String[][]{{"incline","INCLINÓMETRO"},{"telemetry","TELEMETRÍA"},{"route","RUTA"},{"history","HISTORIAL"},{"garage","GARAJE"},{"stats","ESTADÍSTICAS"},{"settings","AJUSTES"}}
                : new String[][]{{"incline","CONDUCCIÓN"},{"route","RUTA"},{"stats","ESTADÍSTICAS"},{"telemetry","SENSORES"},{"settings","AJUSTES"}};
        for (String[] item : items) {
            boolean sel = item[0].equals(current) || (item[0].equals("more") && (current.equals("garage") || current.equals("stats") || current.equals("settings") || current.equals("more")));
            NavItemView v = new NavItemView(getContext(), item[0], item[1], sel);
            LayoutParams lp = new LayoutParams(0, Ui.dp(getContext(), compactHeight?50:66), 1f);
            lp.setMargins(Ui.dp(getContext(), 2), 0, Ui.dp(getContext(), 2), 0);
            addView(v, lp);
            v.setOnClickListener(x -> listener.onDestination(item[0]));
        }
    }

    final class NavItemView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final String key, label;
        private final boolean selected;

        NavItemView(Context c, String key, String label, boolean selected) {
            super(c); this.key = key; this.label = label; this.selected = selected;
            setClickable(true); setFocusable(true); setMinimumWidth(Ui.dp(c, 48)); setMinimumHeight(Ui.dp(c, 48));
            setContentDescription(label);
        }

        @Override protected void onDraw(Canvas c) {
            float w=getWidth(), h=getHeight();
            if(selected){p.setStyle(Paint.Style.FILL);p.setColor(AppPrefs.lightTheme(getContext())?android.graphics.Color.rgb(224,242,252):android.graphics.Color.rgb(5,35,52));c.drawRoundRect(new RectF(2,2,w-2,h-2),Ui.dp(getContext(),14),Ui.dp(getContext(),14),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),2));p.setColor(Ui.RED);c.drawRoundRect(new RectF(2,2,w-2,h-2),Ui.dp(getContext(),14),Ui.dp(getContext(),14),p);}
            float cx=w/2, iconY=h*(compactHeight?.46f:.33f), s=Math.min(w,h)*(compactHeight?.24f:.20f);
            p.setStrokeWidth(Math.max(2,Ui.dp(getContext(),2)));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);p.setColor(selected?Ui.RED:(AppPrefs.lightTheme(getContext())?android.graphics.Color.rgb(78,103,124):android.graphics.Color.rgb(157,181,209)));
            drawIcon(c,cx,iconY,s);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));p.setTextSize(Ui.dp(getContext(), expandedTextSize()));p.setColor(selected?Ui.RED:(AppPrefs.lightTheme(getContext())?android.graphics.Color.rgb(70,88,103):android.graphics.Color.rgb(172,190,211)));if(!compactHeight)c.drawText(label,cx,h*.82f,p);
        }

        private float expandedTextSize(){return getResources().getConfiguration().screenWidthDp>=600?9.5f:8.5f;}

        private void drawIcon(Canvas c,float cx,float cy,float s){
            switch(key){
                case "incline":
                    c.drawArc(new RectF(cx-s,cy-s*.55f,cx+s,cy+s*1.1f),205,130,false,p);c.drawLine(cx,cy+s*.12f,cx+s*.55f,cy-s*.38f,p);c.drawCircle(cx,cy+s*.12f,s*.08f,p);break;
                case "telemetry":
                case "stats":
                    c.drawLine(cx-s*.65f,cy+s*.55f,cx-s*.65f,cy,p);c.drawLine(cx-s*.2f,cy+s*.55f,cx-s*.2f,cy-s*.45f,p);c.drawLine(cx+s*.25f,cy+s*.55f,cx+s*.25f,cy-s*.1f,p);c.drawLine(cx+s*.7f,cy+s*.55f,cx+s*.7f,cy-s*.7f,p);break;
                case "route":
                    path.reset();path.moveTo(cx-s*.65f,cy+s*.65f);path.lineTo(cx-s*.25f,cy-s*.55f);path.lineTo(cx+s*.05f,cy+s*.25f);path.lineTo(cx+s*.65f,cy-s*.65f);c.drawPath(path,p);c.drawCircle(cx-s*.25f,cy-s*.55f,s*.12f,p);break;
                case "history":
                    c.drawCircle(cx,cy,s*.72f,p);c.drawLine(cx,cy,cx,cy-s*.42f,p);c.drawLine(cx,cy,cx+s*.36f,cy+s*.18f,p);break;
                case "garage":
                    c.drawCircle(cx-s*.46f,cy+s*.42f,s*.24f,p);c.drawCircle(cx+s*.46f,cy+s*.42f,s*.24f,p);c.drawLine(cx-s*.38f,cy+s*.18f,cx,cy-s*.15f,p);c.drawLine(cx,cy-s*.15f,cx+s*.38f,cy+s*.18f,p);c.drawLine(cx-s*.1f,cy-s*.15f,cx+s*.42f,cy-s*.35f,p);break;
                case "settings":
                    c.drawCircle(cx,cy,s*.25f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(cx+(float)Math.cos(a)*s*.48f,cy+(float)Math.sin(a)*s*.48f,cx+(float)Math.cos(a)*s*.75f,cy+(float)Math.sin(a)*s*.75f,p);}break;
                case "more":
                    p.setStyle(Paint.Style.FILL);for(int i=-1;i<=1;i++)c.drawCircle(cx+i*s*.55f,cy,s*.11f,p);p.setStyle(Paint.Style.STROKE);break;
            }
        }
    }
}
