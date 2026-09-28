package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;

import java.io.InputStream;
import java.util.Locale;

/** Hero ride instrument. No touch UI lives here: the entire canvas can safely scale. */
public class RideGaugeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private double roll;
    private double visualRoll;
    private long lastVisualFrameNs;
    private double routeMaxLeft;
    private double routeMaxRight;
    private double curvePeak;
    private double heldPeak;
    private boolean wasLeaning;
    private long neutralSince;
    private long peakHoldStart;
    private long peakHoldUntil;
    private long maxPulseUntil;
    private int maxPulseSide;
    private final double[] recentLeft = new double[6];
    private final double[] recentRight = new double[6];
    private int recentLeftCount = 0;
    private int recentRightCount = 0;
    private boolean riding;
    private boolean gpsFix;
    private float speedKmh,altitudeM,distanceM,temperatureC=Float.NaN;
    private double longitudinalG,peakAccelG,peakBrakeG;
    private long routeStartMs;
    private Bitmap masterScene;
    private int masterSceneIndex=-1;
    private Bitmap customScene;
    private String customSceneUri="";

    public RideGaugeView(Context c) { super(c); init(); }
    public RideGaugeView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Inclinómetro en tiempo real");
        // v0.9.2: static decorative wallpaper; only the inclinometer represents physical lean.
    }

    public void setRiding(boolean active) { riding = active; invalidate(); }
    public void setGpsFix(boolean fix) { gpsFix = fix; invalidate(); }
    public void setRideTelemetry(float speedKmh,float altitudeM,float distanceM,float temperatureC,long routeStartMs){this.speedKmh=speedKmh;this.altitudeM=altitudeM;this.distanceM=distanceM;this.temperatureC=temperatureC;this.routeStartMs=routeStartMs;invalidate();}
    public void setLongitudinalG(double value){
        longitudinalG=Math.max(-1.5,Math.min(1.5,value));
        if(longitudinalG>peakAccelG)peakAccelG=longitudinalG;
        if(longitudinalG<peakBrakeG)peakBrakeG=longitudinalG;
        invalidate();
    }
    public void setLongitudinalPeaks(double brake,double accel){
        peakBrakeG=Math.min(peakBrakeG,Math.min(0,brake));
        peakAccelG=Math.max(peakAccelG,Math.max(0,accel));
        invalidate();
    }
    public void resetGPeaks(){peakAccelG=0;peakBrakeG=0;invalidate();}

    public void setRoll(double value) {
        roll = Math.max(-70, Math.min(70, value));
        long now = System.currentTimeMillis();
        double abs = Math.abs(roll);
        if (!wasLeaning && abs >= 8.0) {
            wasLeaning = true;
            neutralSince = 0;
            curvePeak = roll;
        }
        if (wasLeaning) {
            if (Math.abs(roll) > Math.abs(curvePeak)) curvePeak = roll;
            if (abs <= 5.0) {
                if (neutralSince == 0) neutralSince = now;
                if (now - neutralSince >= 260) {
                    heldPeak = curvePeak;
                    pushRecentPeak(curvePeak);
                    peakHoldStart = now;
                    peakHoldUntil = now + 2200;
                    wasLeaning = false;
                    neutralSince = 0;
                }
            } else {
                neutralSince = 0;
            }
        }
        invalidate();
    }

    public void setRouteMax(double left, double right) {
        long now = System.currentTimeMillis();
        if (riding && left < routeMaxLeft - 0.05) {
            maxPulseSide = -1;
            maxPulseUntil = now + 1500;
        }
        if (riding && right > routeMaxRight + 0.05) {
            maxPulseSide = 1;
            maxPulseUntil = now + 1500;
        }
        routeMaxLeft = Math.max(-70.0, Math.min(0.0, left));
        routeMaxRight = Math.min(70.0, Math.max(0.0, right));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;
        boolean wide = w / h > 1.24f;
        long frameNs=System.nanoTime();
        if(lastVisualFrameNs==0)visualRoll=roll;
        double dt=lastVisualFrameNs==0?0.016:Math.min(0.05,(frameNs-lastVisualFrameNs)/1_000_000_000.0);
        lastVisualFrameNs=frameNs;
        double response=1.0-Math.exp(-dt*8.5);
        visualRoll += (roll-visualRoll)*response;
        if(Math.abs(visualRoll-roll)>0.03)postInvalidateOnAnimation();
        drawFrameScene(c, w, h, (float)visualRoll);

        float cx = w * 0.5f;
        float sideSafe = Ui.dp(getContext(), wide ? 34 : 24);
        float labelSafe = Ui.dp(getContext(), wide ? 30 : 22);
        float topEdge = Ui.dp(getContext(), wide ? 22 : 22);
        float cy;
        float r;
        if(wide){
            // Panoramic landscape gauge. The circle centre intentionally lives low:
            // only its crown is visible, giving a flatter arc without clipping it.
            float topCrown=Math.max(4f,h*.055f);
            float desiredR=Math.min(w*.43f,h*.90f);
            float maxRWidth=w*.5f-sideSafe-labelSafe;
            r=Math.max(h*.62f,Math.min(desiredR,maxRWidth));
            cy=topCrown+r;
        }else{
            cy=h*.61f;
            float maxByWidth=w*.5f-sideSafe-labelSafe;
            float maxByTop=cy-topEdge-Ui.dp(getContext(),8);
            float desired=Math.min(w*.43f,h*.42f);
            r=Math.max(Math.min(w,h)*.22f,Math.min(desired,Math.min(maxByWidth,maxByTop)));
        }
        RectF arc = new RectF(cx-r,cy-r,cx+r,cy+r);
        float stroke = Math.max(Ui.dp(getContext(), 18), r * 0.075f);

        // Empty rail.
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(stroke);
        p.setColor(Color.rgb(18, 31, 43));
        c.drawArc(arc, 200, 140, false, p);
        p.setStrokeWidth(Math.max(1, stroke * 0.08f));
        p.setColor(Color.rgb(77, 105, 130));
        c.drawArc(arc, 200, 140, false, p);

        // Selectable public-facing arc styles.
        String arcStyle=AppPrefs.arcStyle(getContext());
        int ab=AppPrefs.arcBrightness(getContext());
        float baseAlpha=Math.max(40,Math.min(255,Math.round(150f*ab/100f)));
        if("minimal".equals(arcStyle)){
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Math.max(Ui.dp(getContext(),2),stroke*.12f));p.setColor(Color.argb((int)baseAlpha,238,244,248));c.drawArc(arc,200,140,false,p);
        }else if("classic".equals(arcStyle)){
            p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(stroke*.24f);p.setColor(Color.argb((int)baseAlpha,225,232,238));c.drawArc(arc,200,140,false,p);
        }else if("blur".equals(arcStyle)){
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(stroke*.48f);p.setColor(Color.argb(Math.min(110,(int)baseAlpha),255,48,68));c.drawArc(arc,200,140,false,p);
            p.setStrokeWidth(stroke*.18f);p.setColor(Color.argb((int)baseAlpha,255,238,242));c.drawArc(arc,200,140,false,p);
        }else if("arcade".equals(arcStyle)){
            p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(stroke*.36f);
            for(int d=-70;d<70;d+=5){int col=d<0?Color.rgb(35,183,255):Color.rgb(255,48,68);p.setColor(Color.argb((int)baseAlpha,Color.red(col),Color.green(col),Color.blue(col)));c.drawArc(arc,270+d,3.6f,false,p);}
        }else if("sport".equals(arcStyle)){
            p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(stroke*.40f);
            for(int d=-70;d<70;d++){int col=Math.abs(d)>48?Color.rgb(255,48,68):Color.rgb(230,235,240);p.setColor(Color.argb((int)baseAlpha,Color.red(col),Color.green(col),Color.blue(col)));c.drawArc(arc,270+d,1.25f,false,p);}
        }else{
            p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(stroke*.34f);
            for (int d=-70;d<70;d++){int col=Ui.heat(d);p.setColor(Color.argb((int)baseAlpha,Color.red(col),Color.green(col),Color.blue(col)));c.drawArc(arc,270+d,1.35f,false,p);}
        }

        // Dynamic rail: 0° -> current angle, brighter than the base scale.
        p.setStrokeCap("minimal".equals(arcStyle)?Paint.Cap.ROUND:Paint.Cap.BUTT);
        p.setStrokeWidth("minimal".equals(arcStyle)?stroke*.25f:stroke * 0.58f);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(roll)));
        int sign = roll >= 0 ? 1 : -1;
        for (int i = 0; i < steps; i++) {
            double from = Math.min(Math.abs(roll), i);
            double to = Math.min(Math.abs(roll), i + 1.15);
            double degree = to * sign;
            float start = (float) (270 + (sign > 0 ? from : -to));
            p.setColor(Ui.heat(degree));
            c.drawArc(arc, start, (float) Math.max(0.7, to - from), false, p);
        }
        p.setStrokeCap(Paint.Cap.ROUND);

        drawTicks(c, cx, cy, r, wide);
        drawNeedleMarker(c, cx, cy, r);


        long now = System.currentTimeMillis();
        boolean holdingPeak = now < peakHoldUntil;
        double shown = holdingPeak ? heldPeak : roll;
        float valueY = wide ? h * 0.43f : cy + r * 0.60f;
        float valueSize = Math.max(Ui.dp(getContext(), 42), Math.min(w, h) * (wide ? 0.105f : 0.10f));
        float scale = 1f;
        if (holdingPeak) {
            long age = now - peakHoldStart;
            if (age < 380) {
                double t = age / 380.0;
                scale = (float) (1.0 + 0.20 * Math.sin(Math.PI * t));
            } else scale = 1.08f;
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        p.setColor(Ui.heat(shown));
        p.setTextSize(valueSize * scale);
        c.drawText(String.format(Locale.getDefault(), "%+.1f°", shown), cx, valueY, p);
        p.setTextSize(Math.max(Ui.dp(getContext(), 13), valueSize * 0.22f));
        p.setColor(holdingPeak ? Ui.heat(shown) : Ui.MUTED);
        String label = holdingPeak ? "PICO DE CURVA" : shown < -1 ? "IZQUIERDA" : shown > 1 ? "DERECHA" : "NIVEL";
        c.drawText(label, cx, valueY + valueSize * 0.32f, p);

        drawRouteMax(c, w, h, cx, cy, r, wide, now);
        drawRecentPeaks(c,w,h,wide);
        drawLongitudinalG(c,w,h,wide);
        drawStatus(c, w, h, wide);

        if (holdingPeak || now < maxPulseUntil) postInvalidateDelayed(24);
    }


    private void drawFrameScene(Canvas c, float w, float h, float sceneRoll) {
        String mode=AppPrefs.wallpaperMode(getContext());
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(Ui.BG);c.drawRect(0,0,w,h,p);
        if(!"none".equals(mode)){
            Bitmap bmp="custom".equals(mode)?getCustomScene():getBuiltinScene(AppPrefs.wallpaperIndex(getContext()));
            if(bmp!=null){
                int alpha=Math.round(255f*AppPrefs.wallpaperOpacity(getContext())/100f);
                int sat=AppPrefs.wallpaperSaturation(getContext());
                ColorMatrix cm=new ColorMatrix();cm.setSaturation(sat/100f);p.setColorFilter(new ColorMatrixColorFilter(cm));
                if(AppPrefs.wallpaperBlur(getContext())>0){float br=Math.max(0.5f,AppPrefs.wallpaperBlur(getContext())*.16f);p.setMaskFilter(new android.graphics.BlurMaskFilter(br,android.graphics.BlurMaskFilter.Blur.NORMAL));}
                c.save();
                float bgRot=AppPrefs.wallpaperRotation(getContext());
                if(AppPrefs.wallpaperLean(getContext())) bgRot += (float)(visualRoll * AppPrefs.wallpaperLeanStrength(getContext()) / 100.0);
                c.rotate(bgRot,w*.5f,h*.5f);
                // slight overscan prevents black corners while rotating/leaning
                float overscan=1f+Math.min(.22f,Math.abs(bgRot)/180f*.22f);
                c.scale(overscan,overscan,w*.5f,h*.5f);
                drawStaticWallpaper(c,bmp,w,h,alpha,AppPrefs.fit(getContext()),"builtin".equals(mode)?AppPrefs.wallpaperIndex(getContext()):-1);
                c.restore();
                p.setColorFilter(null);p.setMaskFilter(null);
            }
        }
        int dark=Math.round(255f*AppPrefs.wallpaperDarken(getContext())/100f);
        if(dark>0){p.setColor(Color.argb(dark,0,0,0));p.setStyle(Paint.Style.FILL);c.drawRect(0,0,w,h,p);}
        // Static wallpaper by design: it never leans with the bike. Only the instrument moves.
        // A very subtle live heat halo can still reflect the current angle without altering geometry.
        int heat=Ui.heat(sceneRoll);float absN=Math.min(1f,Math.abs(sceneRoll)/70f);
        p.setShader(new RadialGradient(w*.5f,h*.79f,w*.32f,new int[]{Color.argb((int)(12+52*absN),Color.red(heat),Color.green(heat),Color.blue(heat)),Color.TRANSPARENT},new float[]{0f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(w*.5f,h*.79f,w*.32f,p);p.setShader(null);
    }

    private void drawStaticWallpaper(Canvas c,Bitmap bmp,float w,float h,int alpha,String fit,int preset){
        float sx=w/bmp.getWidth(),sy=h/bmp.getHeight();float scale;
        if("fit".equals(fit))scale=Math.min(sx,sy);else if("center".equals(fit))scale=Math.min(1f,Math.min(sx,sy));else scale=Math.max(sx,sy);
        // Keep the rider visually farther away than previous revisions.
        if("fill".equals(fit))scale*=.93f;
        float dw=bmp.getWidth()*scale,dh=bmp.getHeight()*scale,cx=w*.5f,cy=h*.51f;
        RectF dst=new RectF(cx-dw*.5f,cy-dh*.5f,cx+dw*.5f,cy+dh*.5f);
        p.setAlpha(alpha);p.setColorFilter(null);
        c.drawBitmap(bmp,null,dst,p);p.setColorFilter(null);p.setAlpha(255);
        // Presets are rendered as authored; no synthetic tint/overlay that degrades the artwork.
    }

    private ColorMatrixColorFilter presetFilter(int i){
        ColorMatrix m=new ColorMatrix();
        switch(i){
            case 1:m.set(new float[]{.62f,0,.18f,0,0, 0,.72f,.20f,0,0, .08f,.15f,1.18f,0,4, 0,0,0,1,0});break; // urbana azul
            case 2:m.setSaturation(.72f);break; // montaña
            case 3:m.set(new float[]{1.15f,.08f,0,0,8, .03f,.88f,0,0,0, 0,.02f,.62f,0,-4, 0,0,0,1,0});break; // desierto
            case 4:m.set(new float[]{.72f,.05f,0,0,0, .04f,1.05f,.03f,0,0, 0,.08f,.68f,0,0, 0,0,0,1,0});break; // bosque
            case 5:m.set(new float[]{.56f,0,.05f,0,-5, 0,.72f,.14f,0,0, .10f,.20f,1.12f,0,5, 0,0,0,1,0});break; // lluvia
            case 6:m.set(new float[]{1.08f,.05f,0,0,8, .04f,1.02f,.02f,0,5, 0,.04f,.88f,0,2, 0,0,0,1,0});break; // amanecer
            case 7:m.setSaturation(1.25f);break; // circuito
            case 8:m.setSaturation(.15f);break; // minimalista
            case 9:m.setSaturation(0f);break; // técnico
            default:m.reset();
        }
        return new ColorMatrixColorFilter(m);
    }

    private void drawPresetOverlay(Canvas c,float w,float h,int i){
        p.setStyle(Paint.Style.FILL);
        if(i==1){p.setColor(Color.argb(35,0,120,255));c.drawRect(0,0,w,h,p);}
        else if(i==5){p.setColor(Color.argb(45,18,75,125));c.drawRect(0,0,w,h,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(50,180,220,255));for(int x=0;x<w;x+=24)c.drawLine(x,0,x-70,h,p);}
        else if(i==7){p.setColor(Color.argb(28,255,35,40));c.drawRect(0,h*.82f,w,h,p);}
        else if(i==8){p.setColor(Color.argb(120,0,0,0));c.drawRect(0,0,w,h,p);}
        else if(i==9){p.setColor(Color.argb(155,0,8,14));c.drawRect(0,0,w,h,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(65,0,180,255));for(int x=0;x<w;x+=32)c.drawLine(x,0,x,h,p);for(int y=0;y<h;y+=32)c.drawLine(0,y,w,y,p);}
    }

    private Bitmap getBuiltinScene(int index){
        index=Math.max(0,Math.min(9,index));
        if(masterScene!=null&&!masterScene.isRecycled()&&masterSceneIndex==index)return masterScene;
        int[] ids={R.drawable.lw_wallpaper_01,R.drawable.lw_wallpaper_02,R.drawable.lw_wallpaper_03,R.drawable.lw_wallpaper_04,R.drawable.lw_wallpaper_05,R.drawable.lw_wallpaper_06,R.drawable.lw_wallpaper_07,R.drawable.lw_wallpaper_08,R.drawable.lw_wallpaper_09,R.drawable.lw_wallpaper_10};
        masterScene=BitmapFactory.decodeResource(getResources(),ids[index]);masterSceneIndex=index;return masterScene;
    }

    private Bitmap getCustomScene(){
        String uri=AppPrefs.wallpaperUri(getContext());
        if(uri==null||uri.isEmpty())return null;
        if(customScene!=null&&!customScene.isRecycled()&&uri.equals(customSceneUri))return customScene;
        try(InputStream in=getContext().getContentResolver().openInputStream(Uri.parse(uri))){customScene=BitmapFactory.decodeStream(in);customSceneUri=uri;return customScene;}catch(Exception e){return null;}
    }

    private void drawMasterCover(Canvas c,Bitmap bmp,float w,float h,int alpha,float degrees,float zoom,float dx,float dy){
        if(bmp==null)return;
        float scale=Math.max(w/bmp.getWidth(),h/bmp.getHeight())*zoom;
        float dw=bmp.getWidth()*scale,dh=bmp.getHeight()*scale;
        float cx=w*.5f+dx,cy=h*.53f+dy;
        c.save();
        c.rotate(degrees,w*.5f,h*.82f);
        RectF dst=new RectF(cx-dw*.5f,cy-dh*.5f,cx+dw*.5f,cy+dh*.5f);
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);
        c.drawBitmap(bmp,null,dst,p);p.setAlpha(255);
        c.restore();
    }

    private void drawCinematicBackground(Canvas c, float w, float h, float sceneRoll) {
        // Australian-high-country inspired dusk: dark enough for HUD legibility, but with
        // a warm horizon and a road surface that receives the live lean heat colour.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.rgb(5, 8, 16), Color.rgb(22, 14, 24), Color.rgb(45, 18, 15), Color.rgb(3, 7, 10)},
                new float[]{0, .34f, .60f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float horizon = h * 0.56f;
        // Visual parallax: the world counters the bike lean, while the road bends into the turn.
        float leanN=(float)Math.max(-1,Math.min(1,sceneRoll/55.0));
        float worldShift=-leanN*w*.075f;
        c.save(); c.translate(worldShift,0);
        // Distant layered ranges.
        p.setColor(Color.rgb(18, 18, 25));
        path.reset(); path.moveTo(0, horizon);
        path.lineTo(w*.12f,horizon*.82f); path.lineTo(w*.25f,horizon*.95f);
        path.lineTo(w*.39f,horizon*.76f); path.lineTo(w*.55f,horizon*.94f);
        path.lineTo(w*.72f,horizon*.80f); path.lineTo(w*.86f,horizon*.91f);
        path.lineTo(w,horizon*.74f); path.lineTo(w,horizon+40); path.lineTo(0,horizon+40); path.close();
        c.drawPath(path,p);
        p.setColor(Color.rgb(9, 13, 17));
        path.reset(); path.moveTo(0,horizon+18); path.lineTo(w*.18f,horizon*.88f); path.lineTo(w*.34f,horizon+25);
        path.lineTo(w*.51f,horizon*.86f); path.lineTo(w*.68f,horizon+32); path.lineTo(w*.84f,horizon*.90f);
        path.lineTo(w,horizon+10); path.lineTo(w,h); path.lineTo(0,h); path.close(); c.drawPath(path,p);

        // Low sunset glow kept behind the instrument.
        p.setShader(new RadialGradient(w*.72f,horizon*.82f,w*.30f,
                new int[]{Color.argb(120,255,105,28),Color.argb(50,190,45,20),Color.TRANSPARENT},
                new float[]{0,.38f,1},Shader.TileMode.CLAMP));
        c.drawCircle(w*.72f,horizon*.82f,w*.30f,p); p.setShader(null);

        // Long perspective road. Its vanishing line and near edge bend with the measured lean.
        float bend=-leanN*w*.18f;
        p.setColor(Color.rgb(14, 16, 19));
        path.reset(); path.moveTo(w*.43f,horizon); path.lineTo(w*.57f,horizon); path.quadTo(w*.72f+bend,h*.76f,w*.93f+bend,h); path.lineTo(w*.07f+bend,h); path.quadTo(w*.28f+bend,h*.76f,w*.43f,horizon); path.close();
        c.drawPath(path,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1,w*.0018f)); p.setColor(Color.rgb(118,111,102));
        path.reset();path.moveTo(w*.43f,horizon);path.quadTo(w*.28f+bend,h*.76f,w*.07f+bend,h);c.drawPath(path,p); path.reset();path.moveTo(w*.57f,horizon);path.quadTo(w*.72f+bend,h*.76f,w*.93f+bend,h);c.drawPath(path,p);

        // Broken centre line with forward motion. The phase is speed-driven while recording,
        // so the road appears to flow under the bike instead of behaving like a static backdrop.
        p.setColor(Color.argb(150,215,207,184)); p.setStrokeWidth(Math.max(2,w*.0022f));
        float motionSpeed=Math.max(8f,speedKmh);
        float phase=(System.currentTimeMillis()%4000L)/4000f*(motionSpeed/80f);
        for(int i=0;i<7;i++){
            float base=((i+.15f)/6f+phase*.22f)%1.08f;
            float t0=Math.max(0,base), t1=Math.min(1,base+.055f+.045f*base);
            float y0=horizon+(h-horizon)*t0*t0, y1=horizon+(h-horizon)*t1*t1;
            float shift0=bend*t0*t0,shift1=bend*t1*t1;c.drawLine(w*.5f+shift0*.72f,y0,w*.5f+shift1,y1,p);
        }
        if(riding)postInvalidateOnAnimation();
        c.restore();

        // Live temperature reflection: projected onto the asphalt, stronger as lean grows.
        int heat=Ui.heat(roll);
        int ha=Color.argb((int)(36+Math.min(70,Math.abs(roll))*2.0),Color.red(heat),Color.green(heat),Color.blue(heat));
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w*.5f,h*.84f,w*.34f,
                new int[]{ha,Color.argb(28,Color.red(heat),Color.green(heat),Color.blue(heat)),Color.TRANSPARENT},
                new float[]{0,.42f,1},Shader.TileMode.CLAMP));
        c.drawOval(new RectF(w*.16f,h*.67f,w*.84f,h*1.08f),p); p.setShader(null);

        // Heat trail follows the lean side and makes the chromatic temperature readable on-road.
        float side=-leanN;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(5,w*.008f));
        p.setColor(Color.argb(145,Color.red(heat),Color.green(heat),Color.blue(heat)));
        path.reset(); path.moveTo(w*.5f,horizon+h*.04f); path.quadTo(w*(.5f+side*.10f),h*.76f,w*(.5f+side*.24f),h*1.02f); c.drawPath(path,p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawTicks(Canvas c, float cx, float cy, float r, boolean wide) {
        float tickOuter = r - Math.max(Ui.dp(getContext(), 12), r * .05f);
        float tickInner = r - Math.max(Ui.dp(getContext(), 28), r * .11f);
        for (int d = -70; d <= 70; d += 5) {
            double a = Math.toRadians(270 + d);
            float outerX = (float) (cx + Math.cos(a) * tickOuter);
            float outerY = (float) (cy + Math.sin(a) * tickOuter);
            float innerR = d % 10 == 0 ? tickInner : (tickInner + (tickOuter - tickInner) * .48f);
            float innerX = (float) (cx + Math.cos(a) * innerR);
            float innerY = (float) (cy + Math.sin(a) * innerR);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d == 0 ? Math.max(3, r * .012f) : Math.max(1, r * .005f));
            p.setColor(d == 0 ? Color.WHITE : Color.rgb(157, 180, 204));
            c.drawLine(innerX, innerY, outerX, outerY, p);
            if (d % 10 == 0) {
                float labelR = r + Math.max(Ui.dp(getContext(), 10), r * .045f);
                float tx = (float) (cx + Math.cos(a) * labelR);
                float ty = (float) (cy + Math.sin(a) * labelR);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                float tickText=wide?Math.min(Ui.dp(getContext(),11),Math.max(8f,getHeight()*.038f)):Math.max(Ui.dp(getContext(),11),r*.047f);
                p.setTextSize(tickText);
                p.setColor(d == 0 ? Color.WHITE : Color.rgb(187, 204, 223));
                c.drawText(String.valueOf(Math.abs(d)), tx, ty + p.getTextSize() * .32f, p);
            }
        }
    }

    private void drawNeedleMarker(Canvas c, float cx, float cy, float r) {
        double a = Math.toRadians(270 + roll);
        float rr = r + Math.max(Ui.dp(getContext(), 1), r * .004f);
        float x = (float) (cx + Math.cos(a) * rr);
        float y = (float) (cy + Math.sin(a) * rr);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawCircle(x, y, Math.max(Ui.dp(getContext(), 5), r * .022f), p);
        p.setColor(Ui.heat(roll));
        c.drawCircle(x, y, Math.max(Ui.dp(getContext(), 3), r * .014f), p);
    }

    private void drawAmbientGlow(Canvas c, float x, float y, float r) {
        int heat = Ui.heat(roll);
        int transparent = Color.argb(0, Color.red(heat), Color.green(heat), Color.blue(heat));
        int soft = Color.argb((int) (48 + Math.min(70, Math.abs(roll)) * 1.5), Color.red(heat), Color.green(heat), Color.blue(heat));
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y + r * .18f, r * .72f,
                new int[]{soft, Color.argb(30, Color.red(heat), Color.green(heat), Color.blue(heat)), transparent},
                new float[]{0, .42f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y + r * .18f, r * .72f, p);
        p.setShader(null);
    }

    private void drawBikeAndRider(Canvas c, float cx, float cy, float deg, float s) {
        c.save();
        c.rotate(deg, cx, cy);
        float wheelY = cy + s * .36f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(5, 7, 10));
        c.drawOval(new RectF(cx - s * .15f, wheelY - s * .02f, cx + s * .15f, wheelY + s * .47f), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, s * .025f));
        p.setColor(Color.rgb(102, 108, 115));
        c.drawOval(new RectF(cx - s * .12f, wheelY, cx + s * .12f, wheelY + s * .43f), p);

        // Naked-bike body, black with bronze CB650R-like details.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(23, 27, 31));
        path.reset(); path.moveTo(cx - s * .26f, cy + s * .12f); path.lineTo(cx - s * .16f, cy - s * .06f);
        path.lineTo(cx + s * .17f, cy - s * .05f); path.lineTo(cx + s * .27f, cy + s * .14f); path.lineTo(cx + s * .13f, cy + s * .28f); path.lineTo(cx - s * .16f, cy + s * .27f); path.close();
        c.drawPath(path, p);
        p.setColor(Color.rgb(155, 105, 58));
        c.drawRoundRect(new RectF(cx - s * .22f, cy + s * .06f, cx - s * .16f, cy + s * .28f), s * .02f, s * .02f, p);
        c.drawRoundRect(new RectF(cx + s * .16f, cy + s * .06f, cx + s * .22f, cy + s * .28f), s * .02f, s * .02f, p);
        p.setColor(Color.rgb(240, 32, 43));
        c.drawRoundRect(new RectF(cx - s * .11f, cy + s * .16f, cx + s * .11f, cy + s * .23f), s * .03f, s * .03f, p);

        // Rider torso and shoulders.
        p.setColor(Color.rgb(11, 13, 17));
        path.reset(); path.moveTo(cx - s * .24f, cy - s * .06f); path.lineTo(cx - s * .15f, cy - s * .41f);
        path.quadTo(cx, cy - s * .56f, cx + s * .15f, cy - s * .41f); path.lineTo(cx + s * .24f, cy - s * .06f);
        path.lineTo(cx + s * .13f, cy + s * .11f); path.lineTo(cx - s * .13f, cy + s * .11f); path.close();
        c.drawPath(path, p);

        // Red angular chest/back branding inspired by the user's black/red jacket.
        p.setColor(Color.rgb(232, 31, 47));
        path.reset(); path.moveTo(cx, cy - s * .28f); path.lineTo(cx - s * .10f, cy - s * .40f); path.lineTo(cx - s * .04f, cy - s * .18f);
        path.lineTo(cx, cy - s * .11f); path.lineTo(cx + s * .04f, cy - s * .18f); path.lineTo(cx + s * .10f, cy - s * .40f); path.close();
        c.drawPath(path, p);

        // Arms/handlebar. The biker greeting uses the arm OPPOSITE the lean direction.
        // The elbow opens to roughly 95° and the hand ends in a two-finger V sign.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(4, s * .055f));
        p.setColor(Color.rgb(14, 16, 20));
        float salute = (float)Math.max(0, Math.min(1, (Math.abs(deg) - 10f) / 24f));
        if (deg < -1f && salute > 0f) {
            // Leaning left -> greet with RIGHT arm.
            c.drawLine(cx - s*.15f, cy - s*.31f, cx - s*.32f, cy - s*.06f, p);
            float shoulderX=cx+s*.15f, shoulderY=cy-s*.31f;
            float elbowX=cx+s*(.28f+.10f*salute), elbowY=cy-s*(.18f-.03f*salute);
            float handX=cx+s*(.47f+.12f*salute), handY=cy-s*(.03f-.02f*salute);
            c.drawLine(shoulderX,shoulderY,elbowX,elbowY,p);c.drawLine(elbowX,elbowY,handX,handY,p);
            p.setStrokeWidth(Math.max(2,s*.020f));c.drawLine(handX,handY,handX+s*.085f,handY-s*.075f,p);c.drawLine(handX,handY,handX+s*.105f,handY-s*.035f,p);
        } else if (deg > 1f && salute > 0f) {
            // Leaning right -> greet with LEFT arm.
            c.drawLine(cx + s*.15f, cy - s*.31f, cx + s*.32f, cy - s*.06f, p);
            float shoulderX=cx-s*.15f, shoulderY=cy-s*.31f;
            float elbowX=cx-s*(.28f+.10f*salute), elbowY=cy-s*(.18f-.03f*salute);
            float handX=cx-s*(.47f+.12f*salute), handY=cy-s*(.03f-.02f*salute);
            c.drawLine(shoulderX,shoulderY,elbowX,elbowY,p);c.drawLine(elbowX,elbowY,handX,handY,p);
            p.setStrokeWidth(Math.max(2,s*.020f));c.drawLine(handX,handY,handX-s*.085f,handY-s*.075f,p);c.drawLine(handX,handY,handX-s*.105f,handY-s*.035f,p);
        } else {
            c.drawLine(cx - s*.15f, cy - s*.31f, cx - s*.32f, cy - s*.06f, p);
            c.drawLine(cx + s*.15f, cy - s*.31f, cx + s*.32f, cy - s*.06f, p);
        }
        p.setStrokeWidth(Math.max(2, s * .025f));
        p.setColor(Color.rgb(142, 150, 159));
        c.drawLine(cx - s * .34f, cy - s * .07f, cx + s * .34f, cy - s * .07f, p);

        // Helmet: matte black, red twin stripes.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(8, 10, 13));
        c.drawCircle(cx, cy - s * .58f, s * .13f, p);
        p.setColor(Color.rgb(215, 31, 44));
        c.drawRoundRect(new RectF(cx - s * .045f, cy - s * .705f, cx - s * .015f, cy - s * .48f), s * .01f, s * .01f, p);
        c.drawRoundRect(new RectF(cx + s * .015f, cy - s * .705f, cx + s * .045f, cy - s * .48f), s * .01f, s * .01f, p);
        p.setColor(Color.rgb(61, 74, 87));
        c.drawRoundRect(new RectF(cx - s * .095f, cy - s * .61f, cx + s * .095f, cy - s * .545f), s * .025f, s * .025f, p);
        c.restore();
    }


    private void pushRecentPeak(double peak){
        if(Math.abs(peak)<8.0)return;
        double v=Math.max(-70.0,Math.min(70.0,peak));
        if(v<0){
            for(int i=Math.min(5,recentLeftCount);i>0;i--)recentLeft[i]=recentLeft[i-1];
            recentLeft[0]=v;if(recentLeftCount<6)recentLeftCount++;
        }else{
            for(int i=Math.min(5,recentRightCount);i>0;i--)recentRight[i]=recentRight[i-1];
            recentRight[0]=v;if(recentRightCount<6)recentRightCount++;
        }
    }

    private void drawRecentPeaks(Canvas c,float w,float h,boolean wide){
        if(recentLeftCount==0 && recentRightCount==0)return;
        float margin=Math.max(8f,Math.min(Ui.dp(getContext(),wide?18:28),w*.035f));
        float leftX=margin,rightX=w-margin;
        float top=wide?h*.245f:h*.19f;
        float bottom=wide?h*.57f:h*.45f;
        float gap=Math.max(6f,(bottom-top)/5f);
        float base=Math.max(h*(wide?.032f:.021f),Math.min(Ui.dp(getContext(),wide?18:20),h*(wide?.052f:.032f)));

        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        p.setStyle(Paint.Style.FILL);p.setShader(null);
        for(int side=0;side<2;side++){
            double[] values=side==0?recentLeft:recentRight;
            int count=side==0?recentLeftCount:recentRightCount;
            p.setTextAlign(side==0?Paint.Align.LEFT:Paint.Align.RIGHT);
            float x=side==0?leftX:rightX;
            for(int i=0;i<count;i++){
                float scale=1f-i*.115f;
                int alpha=Math.max(72,235-i*30);
                int heat=Ui.heat(values[i]);
                p.setColor(Color.argb(alpha,Color.red(heat),Color.green(heat),Color.blue(heat)));
                p.setTextSize(base*scale);
                c.drawText(String.format(Locale.getDefault(),"%.1f°",Math.abs(values[i])),x,top+i*gap,p);
            }
        }
    }

    private void drawRouteMax(Canvas c, float w, float h, float cx, float cy, float r, boolean wide, long now) {
        // Secondary information only: keep the centre visually free for the inclinometer.
        float blockW=wide?Math.min(w*.135f,Ui.dp(getContext(),154)):Math.min(w*.29f,Ui.dp(getContext(),132));
        float blockH=wide?Math.min(h*.105f,Ui.dp(getContext(),43)):Math.min(h*.075f,Ui.dp(getContext(),48));
        blockH=Math.max(blockH,Ui.dp(getContext(),34));
        float y=Math.max(4f,h*(wide?.012f:.010f));
        float margin=Math.max(9f,Math.min(Ui.dp(getContext(),14),w*.025f));

        drawMaxCompact(c,new RectF(margin,y,margin+blockW,y+blockH),routeMaxLeft,true,now<maxPulseUntil&&maxPulseSide<0);
        drawMaxCompact(c,new RectF(w-margin-blockW,y,w-margin,y+blockH),routeMaxRight,false,now<maxPulseUntil&&maxPulseSide>0);
    }

    private void drawMaxCompact(Canvas c,RectF r,double value,boolean left,boolean pulse){
        int accent=Math.abs(value)>.05?Ui.heat(value):Color.rgb(118,145,170);

        // No large opaque card: a subtle edge and underline keep the value discoverable
        // without competing with the central angle/arc.
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(pulse?82:42,5,15,23));
        c.drawRoundRect(r,Ui.dp(getContext(),9),Ui.dp(getContext(),9),p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f,Ui.dp(getContext(),pulse?1.2f:.7f)));
        p.setColor(pulse?accent:Color.argb(115,55,84,105));
        c.drawRoundRect(r,Ui.dp(getContext(),9),Ui.dp(getContext(),9),p);

        float x=left?r.left+Ui.dp(getContext(),8):r.right-Ui.dp(getContext(),8);
        p.setTextAlign(left?Paint.Align.LEFT:Paint.Align.RIGHT);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        p.setTextSize(Math.max(Ui.dp(getContext(),7.5f),r.height()*.20f));
        p.setColor(Color.argb(180,154,177,197));
        c.drawText("MÁX.",x,r.top+r.height()*.31f,p);

        float valueSize=Math.max(Ui.dp(getContext(),13),r.height()*.39f);
        p.setTextSize(valueSize*(pulse?1.10f:1f));
        p.setColor(accent);
        String v=String.format(Locale.getDefault(),"%.1f°",Math.abs(value));
        c.drawText(left?"← "+v:v+" →",x,r.top+r.height()*.76f,p);

        p.setStrokeWidth(Math.max(1f,r.height()*.035f));
        p.setColor(Color.argb(pulse?220:115,Color.red(accent),Color.green(accent),Color.blue(accent)));
        float underline=left?r.left:r.right-r.width()*.48f;
        float underlineEnd=left?r.left+r.width()*.48f:r.right;
        c.drawLine(underline,r.bottom-1f,underlineEnd,r.bottom-1f,p);
    }


    private void drawLongitudinalG(Canvas c,float w,float h,boolean wide){
        float outerGap=Math.max(3f,Math.min(Ui.dp(getContext(),5),h*.012f));
        float statusH=Math.min(Ui.dp(getContext(),wide?58:62),h*(wide?.18f:.15f));
        float statusTop=h-outerGap-statusH;
        float panelH=Math.min(Ui.dp(getContext(),wide?39:43),h*(wide?.125f:.105f));
        float panelBottom=statusTop-outerGap;
        float panelTop=panelBottom-panelH;
        if(panelH<12f || panelTop<h*.54f)return;

        float side=Math.max(w*.10f,Math.min(Ui.dp(getContext(),wide?150:34),w*.19f));
        float cx=w*.5f,barLeft=side,barRight=w-side;
        float barY=panelTop+panelH*.61f;
        float half=(barRight-barLeft)*.5f,maxScale=1.20f;

        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(154,4,12,18));
        c.drawRoundRect(new RectF(barLeft-Math.min(Ui.dp(getContext(),12),w*.012f),panelTop,
                barRight+Math.min(Ui.dp(getContext(),12),w*.012f),panelBottom),
                Math.min(Ui.dp(getContext(),11),panelH*.25f),Math.min(Ui.dp(getContext(),11),panelH*.25f),p);

        p.setTextSize(Math.max(7f,Math.min(Ui.dp(getContext(),wide?9:8.5f),panelH*.27f)));
        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));p.setColor(Ui.MUTED);
        p.setTextAlign(Paint.Align.LEFT);c.drawText(String.format(Locale.getDefault(),"FRENO · %.2fG",Math.abs(peakBrakeG)),barLeft,panelTop+panelH*.30f,p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format(Locale.getDefault(),"%.2fG · GAS",peakAccelG),barRight,panelTop+panelH*.30f,p);

        float trackH=Math.max(2f,Math.min(Ui.dp(getContext(),5),panelH*.13f));
        p.setColor(Color.rgb(29,45,57));c.drawRoundRect(new RectF(barLeft,barY-trackH*.5f,barRight,barY+trackH*.5f),trackH,trackH,p);
        p.setColor(Color.rgb(205,220,230));c.drawRect(cx-1f,barY-panelH*.17f,cx+1f,barY+panelH*.17f,p);

        double g=Math.max(-maxScale,Math.min(maxScale,longitudinalG));float x=cx+(float)(g/maxScale*half);
        int heat=Ui.heat(Math.min(70,Math.abs(g)/maxScale*70));p.setColor(heat);
        if(g<0)c.drawRoundRect(new RectF(x,barY-trackH*.5f,cx,barY+trackH*.5f),trackH,trackH,p);
        else if(g>0)c.drawRoundRect(new RectF(cx,barY-trackH*.5f,x,barY+trackH*.5f),trackH,trackH,p);
        c.drawCircle(x,barY,Math.max(3f,Math.min(Ui.dp(getContext(),5.5f),panelH*.13f)),p);
        p.setColor(Color.WHITE);c.drawCircle(x,barY,Math.max(1.5f,panelH*.04f),p);

        String current=Math.abs(g)<0.03?"0.00 G":String.format(Locale.getDefault(),"%s %.2f G",g<0?"FRENO":"GAS",Math.abs(g));
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(7f,Math.min(Ui.dp(getContext(),wide?10:9.5f),panelH*.27f)));
        p.setColor(Math.abs(g)<0.03?Ui.MUTED:heat);c.drawText(current,cx,panelBottom-panelH*.07f,p);
    }

    private void drawStatus(Canvas c, float w, float h, boolean wide) {
        if(!AppPrefs.showOverlay(getContext()))return;
        long sec=(riding&&routeStartMs>0)?Math.max(0,(System.currentTimeMillis()-routeStartMs)/1000):0;
        String[] values={
                gpsFix?"OK":"--",
                String.format(Locale.getDefault(),"%.0f",speedKmh),
                String.format(Locale.getDefault(),"%.0f m",altitudeM),
                String.format(Locale.getDefault(),"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60),
                String.format(Locale.getDefault(),"%.1f km",distanceM/1000f),
                Float.isNaN(temperatureC)?"--°C":String.format(Locale.getDefault(),"%.0f°C",temperatureC)
        };
        String[] labels={"GPS","KM/H","ALT","TIEMPO","RUTA","TEMP"};

        float gap=Math.max(3f,Math.min(Ui.dp(getContext(),5),h*.012f));
        float panelH=Math.min(Ui.dp(getContext(),wide?58:62),h*(wide?.18f:.15f));
        float panelBottom=h-gap,panelTop=panelBottom-panelH;
        RectF panel=new RectF(Math.max(4f,w*.008f),panelTop,w-Math.max(4f,w*.008f),panelBottom);

        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(Color.argb(184,4,12,18));
        c.drawRoundRect(panel,Math.min(Ui.dp(getContext(),12),panelH*.22f),Math.min(Ui.dp(getContext(),12),panelH*.22f),p);

        float cellW=panel.width()/6f;
        float iconY=panelTop+panelH*.30f,valueY=panelTop+panelH*.65f,labelY=panelTop+panelH*.88f;
        float iconS=Math.max(4f,Math.min(Ui.dp(getContext(),wide?8.5f:8f),panelH*.14f));

        p.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD));
        for(int i=0;i<6;i++){
            float cx=panel.left+cellW*(i+.5f);
            if(i>0){
                p.setColor(Color.argb(62,150,178,198));p.setStrokeWidth(Math.max(1f,panelH*.012f));p.setStyle(Paint.Style.STROKE);
                c.drawLine(panel.left+cellW*i,panelTop+panelH*.15f,panel.left+cellW*i,panelBottom-panelH*.15f,p);
            }
            drawTelemetryIcon(c,i,cx,iconY,iconS,i==0&&gpsFix?Ui.GREEN:Color.rgb(205,220,230));
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(7f,Math.min(Ui.dp(getContext(),wide?10.5f:9.5f),panelH*.19f)));
            p.setColor(i==0&&gpsFix?Ui.GREEN:Color.WHITE);c.drawText(values[i],cx,valueY,p);
            p.setTextSize(Math.max(6f,Math.min(Ui.dp(getContext(),wide?7.5f:7f),panelH*.13f)));p.setColor(Ui.MUTED);
            c.drawText(labels[i],cx,labelY,p);
        }
        if(riding)postInvalidateDelayed(1000);
    }

    private void drawTelemetryIcon(Canvas c,int type,float cx,float cy,float s,int color){
        p.setShader(null);p.setColor(color);p.setStrokeWidth(Math.max(1f,s*.20f));
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        if(type==0){
            // GPS / location pin.
            c.drawCircle(cx,cy-s*.22f,s*.58f,p);
            path.moveTo(cx-s*.43f,cy+s*.18f);path.lineTo(cx,cy+s*.92f);path.lineTo(cx+s*.43f,cy+s*.18f);c.drawPath(path,p);
            c.drawCircle(cx,cy-s*.22f,s*.17f,p);
        }else if(type==1){
            // Speedometer.
            c.drawArc(new RectF(cx-s,cy-s,cx+s,cy+s),200,140,false,p);
            path.moveTo(cx,cy);path.lineTo(cx+s*.55f,cy-s*.42f);c.drawPath(path,p);
            c.drawCircle(cx,cy,s*.12f,p);
        }else if(type==2){
            // Altitude / mountain.
            path.moveTo(cx-s,cy+s*.7f);path.lineTo(cx-s*.25f,cy-s*.75f);path.lineTo(cx+s*.05f,cy-s*.20f);
            path.lineTo(cx+s*.38f,cy-s*.67f);path.lineTo(cx+s,cy+s*.7f);c.drawPath(path,p);
        }else if(type==3){
            // Route stopwatch.
            c.drawCircle(cx,cy+s*.05f,s*.78f,p);
            c.drawLine(cx,cy-s*.98f,cx,cy-s*.70f,p);
            c.drawLine(cx-s*.25f,cy-s*.98f,cx+s*.25f,cy-s*.98f,p);
            c.drawLine(cx,cy+s*.05f,cx+s*.38f,cy-s*.25f,p);
        }else if(type==4){
            // Travelled route.
            path.moveTo(cx-s*.95f,cy+s*.55f);
            path.cubicTo(cx-s*.30f,cy+s*.35f,cx-s*.55f,cy-s*.50f,cx,cy-s*.42f);
            path.cubicTo(cx+s*.52f,cy-s*.35f,cx+s*.30f,cy+s*.42f,cx+s*.92f,cy+s*.55f);
            c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx-s*.95f,cy+s*.55f,s*.13f,p);c.drawCircle(cx+s*.92f,cy+s*.55f,s*.13f,p);
        }else{
            // Ambient temperature thermometer.
            c.drawCircle(cx,cy+s*.58f,s*.34f,p);
            c.drawRoundRect(new RectF(cx-s*.18f,cy-s*.85f,cx+s*.18f,cy+s*.55f),s*.18f,s*.18f,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy+s*.58f,s*.18f,p);
            c.drawRoundRect(new RectF(cx-s*.07f,cy-s*.35f,cx+s*.07f,cy+s*.52f),s*.07f,s*.07f,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
