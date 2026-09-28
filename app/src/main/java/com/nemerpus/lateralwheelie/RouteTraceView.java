package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RouteTraceView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<TelemetryDb.Sample> samples = new ArrayList<>();

    public RouteTraceView(Context c) { super(c); init(); }
    public RouteTraceView(Context c, AttributeSet a) { super(c,a); init(); }

    private void init() {
        setMinimumHeight(Ui.dp(getContext(), 220));
        setContentDescription("Trazado GPS coloreado por inclinación");
    }

    public void setSamples(List<TelemetryDb.Sample> values) {
        samples = values == null ? new ArrayList<>() : values;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w=getWidth(),h=getHeight();
        p.setStyle(Paint.Style.FILL);p.setColor(Ui.CARD);c.drawRoundRect(new RectF(0,0,w,h),Ui.dp(getContext(),18),Ui.dp(getContext(),18),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Ui.BORDER);c.drawRoundRect(new RectF(1,1,w-1,h-1),Ui.dp(getContext(),18),Ui.dp(getContext(),18),p);
        float pad=Ui.dp(getContext(),24);
        p.setStrokeWidth(1);p.setColor(Color.rgb(20,43,58));
        for(int i=1;i<5;i++){float x=pad+(w-2*pad)*i/5f;c.drawLine(x,pad,x,h-pad,p);float y=pad+(h-2*pad)*i/5f;c.drawLine(pad,y,w-pad,y,p);}

        List<TelemetryDb.Sample> valid=new ArrayList<>();
        for(TelemetryDb.Sample s:samples) if(Math.abs(s.lat)>0.000001 || Math.abs(s.lon)>0.000001) valid.add(s);
        if(valid.size()<2){
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(Ui.MUTED);p.setTextSize(Ui.dp(getContext(),14));
            c.drawText(samples.isEmpty()?"Sin puntos GPS todavía":"Esperando una segunda posición GPS…",w/2,h/2,p);return;
        }
        double minLat=Double.POSITIVE_INFINITY,maxLat=Double.NEGATIVE_INFINITY,minLon=Double.POSITIVE_INFINITY,maxLon=Double.NEGATIVE_INFINITY;
        for(TelemetryDb.Sample s:valid){minLat=Math.min(minLat,s.lat);maxLat=Math.max(maxLat,s.lat);minLon=Math.min(minLon,s.lon);maxLon=Math.max(maxLon,s.lon);}
        double dLat=Math.max(1e-7,maxLat-minLat),dLon=Math.max(1e-7,maxLon-minLon);
        float drawW=w-2*pad,drawH=h-2*pad;
        for(int i=1;i<valid.size();i++){
            TelemetryDb.Sample a=valid.get(i-1),b=valid.get(i);
            float x1=(float)(pad+(a.lon-minLon)/dLon*drawW);float y1=(float)(pad+(maxLat-a.lat)/dLat*drawH);
            float x2=(float)(pad+(b.lon-minLon)/dLon*drawW);float y2=(float)(pad+(maxLat-b.lat)/dLat*drawH);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Ui.dp(getContext(),5));p.setColor(Ui.heat(b.roll));c.drawLine(x1,y1,x2,y2,p);
        }
        TelemetryDb.Sample first=valid.get(0),last=valid.get(valid.size()-1);
        float sx=(float)(pad+(first.lon-minLon)/dLon*drawW),sy=(float)(pad+(maxLat-first.lat)/dLat*drawH);
        float ex=(float)(pad+(last.lon-minLon)/dLon*drawW),ey=(float)(pad+(maxLat-last.lat)/dLat*drawH);
        p.setStyle(Paint.Style.FILL);p.setColor(Ui.GREEN);c.drawCircle(sx,sy,Ui.dp(getContext(),6),p);p.setColor(Ui.RED);c.drawCircle(ex,ey,Ui.dp(getContext(),6),p);
    }
}
