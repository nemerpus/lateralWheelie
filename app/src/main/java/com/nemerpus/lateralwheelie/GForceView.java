package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Compact, non-interactive G-meter for the telemetry screen. */
public final class GForceView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double latG, longG;

    public GForceView(Context c) { super(c); init(); }
    public GForceView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init(){setMinimumHeight(Ui.dp(getContext(),220));setContentDescription("Medidor de fuerzas G lateral y longitudinal");}
    public void setG(double lateral,double longitudinal){latG=Math.max(-1.5,Math.min(1.5,lateral));longG=Math.max(-1.5,Math.min(1.5,longitudinal));invalidate();}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();if(w<2||h<2)return;
        float pad=Ui.dp(getContext(),14);RectF card=new RectF(0,0,w,h);
        p.setStyle(Paint.Style.FILL);p.setShader(new android.graphics.LinearGradient(0,0,w,h,new int[]{Color.rgb(10,29,42),Color.rgb(5,15,23)},null,Shader.TileMode.CLAMP));c.drawRoundRect(card,Ui.dp(getContext(),18),Ui.dp(getContext(),18),p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Ui.BORDER);c.drawRoundRect(new RectF(1,1,w-1,h-1),Ui.dp(getContext(),18),Ui.dp(getContext(),18),p);
        float cx=w*.5f,cy=h*.52f,r=Math.min(w,h)*.33f;
        p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Color.rgb(36,67,87));for(int i=1;i<=3;i++)c.drawCircle(cx,cy,r*i/3f,p);c.drawLine(cx-r,cy,cx+r,cy,p);c.drawLine(cx,cy-r,cx,cy+r,p);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(Ui.MUTED);p.setTextSize(Ui.dp(getContext(),10));c.drawText("FRENADA",cx,cy-r-Ui.dp(getContext(),6),p);c.drawText("ACEL.",cx,cy+r+Ui.dp(getContext(),16),p);c.drawText("IZQ",cx-r-Ui.dp(getContext(),18),cy+Ui.dp(getContext(),4),p);c.drawText("DER",cx+r+Ui.dp(getContext(),18),cy+Ui.dp(getContext(),4),p);
        float dx=(float)(latG/1.5*r),dy=(float)(longG/1.5*r);float x=cx+dx,y=cy+dy;double mag=Math.sqrt(latG*latG+longG*longG);int heat=Ui.heat(Math.min(70,mag/1.5*70));int transparent=Color.argb(0,Color.red(heat),Color.green(heat),Color.blue(heat));p.setShader(new RadialGradient(x,y,Ui.dp(getContext(),30),Color.argb(120,Color.red(heat),Color.green(heat),Color.blue(heat)),transparent,Shader.TileMode.CLAMP));c.drawCircle(x,y,Ui.dp(getContext(),30),p);p.setShader(null);p.setColor(heat);c.drawCircle(x,y,Ui.dp(getContext(),8),p);p.setColor(Color.WHITE);c.drawCircle(x,y,Ui.dp(getContext(),3),p);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(Ui.dp(getContext(),11));p.setColor(Ui.TEXT);c.drawText(String.format(Locale.getDefault(),"LAT %+.2f g",latG),pad,h-Ui.dp(getContext(),12),p);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.getDefault(),"LONG %+.2f g",longG),w-pad,h-Ui.dp(getContext(),12),p);
    }
}
