package com.nemerpus.lateralwheelie;



import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_ROUTE_LOCATION=1201, REQ_NOTIFICATIONS=1202, REQ_WALLPAPER=1203, REQ_BLUETOOTH_CONNECT=1204;
    private static final String ACTION_START_RIDE="com.nemerpus.lateralwheelie.START_RIDE";

    private TelemetryDb db;
    private LiveSensorController sensors;
    private LiveSensorController.Snapshot snapshot;
    private FrameLayout windowRoot,body;
    private EdgeSafeFrameLayout safeRoot;
    private LinearLayout shell;
    private RideGaugeView gauge;
    private GForceView gMeter;
    private RouteMapView routeTrace;
    private TextView profileChip,sessionChip;
    private TextView tAngle,tRate,tLatG,tLongG,tSpeed,tAlt,tGps,tTemp,tRouteTime,tDistance,routeState,routeMeta,routeCompare;
    private String current="incline";
    private long selectedRouteSession=-1;
    private boolean pendingExternalStart;
    private long lastTraceRefresh,lastComparisonRefresh;
    private long lastComparedSession=-1;
    private TelemetryDb.RouteComparison cachedComparison;
    private boolean resumed;
    private boolean hudMode;
    private long resetArmedAt;
    // Immediate on-screen route maxima. These mirror RideState but are also fed by the
    // foreground sensor controller so the main gauge never lags behind the service.
    private double uiMaxLeft, uiMaxRight;
    private boolean uiMaxInitialized;
    private long uiMaxSessionId=Long.MIN_VALUE;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable ticker=new Runnable(){@Override public void run(){if(!resumed)return;updateHeader();updateRouteUi(false);if(RideState.active(MainActivity.this))syncUiRouteMaxFromState();if(gauge!=null){gauge.setRiding(RideState.active(MainActivity.this));gauge.setGpsFix(RideState.gpsFix(MainActivity.this));gauge.setRouteMax(uiMaxLeft,uiMaxRight);gauge.setRideTelemetry(RideState.speed(MainActivity.this)*3.6f,RideState.altitude(MainActivity.this),RideState.distanceM(MainActivity.this),RideState.temperatureC(MainActivity.this),RideState.startMs(MainActivity.this));gauge.setLongitudinalPeaks(RideState.maxBrakeG(MainActivity.this),RideState.maxAccelG(MainActivity.this));}handler.postDelayed(this,250);}};

    private final BroadcastReceiver rx=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        String error=i.getStringExtra("error");if(error!=null){Toast.makeText(MainActivity.this,error,Toast.LENGTH_LONG).show();updateHeader();updateRouteUi(true);return;}
        double stateL=i.getDoubleExtra("maxL",RideState.maxLeft(MainActivity.this));double stateR=i.getDoubleExtra("maxR",RideState.maxRight(MainActivity.this));mergeUiRouteMax(stateL,stateR);if(gauge!=null){gauge.setRouteMax(uiMaxLeft,uiMaxRight);gauge.setGpsFix(i.getBooleanExtra("gpsFix",false));gauge.setRideTelemetry(i.getFloatExtra("speed",0)*3.6f,(float)i.getDoubleExtra("alt",0),i.getFloatExtra("distanceM",RideState.distanceM(MainActivity.this)),i.getFloatExtra("tempC",RideState.temperatureC(MainActivity.this)),RideState.startMs(MainActivity.this));gauge.setLongitudinalPeaks(i.getDoubleExtra("maxBrakeG",RideState.maxBrakeG(MainActivity.this)),i.getDoubleExtra("maxAccelG",RideState.maxAccelG(MainActivity.this)));}
        updateHeader();updateRouteUi(false);
    }};

    @Override public void onCreate(Bundle b){
        // Force a content-only window. Some Android/vendor builds may otherwise add a
        // framework title strip using android:label even when the theme is NoActionBar.
        // The app already provides its own identity/header, so that strip is redundant
        // and wastes critical vertical space in landscape/HUD mode.
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Ui.applyTheme(this);setupEdgeToEdge();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        db=new TelemetryDb(this);
        resetUiRouteMaxFromState();
        if(b!=null){uiMaxLeft=b.getDouble("uiMaxLeft",uiMaxLeft);uiMaxRight=b.getDouble("uiMaxRight",uiMaxRight);uiMaxInitialized=true;}
        current=b==null?"incline":b.getString("screen","incline");
        selectedRouteSession=b==null?-1:b.getLong("routeSession",-1);
        hudMode=b!=null&&b.getBoolean("hudMode",false);
        pendingExternalStart=ACTION_START_RIDE.equals(getIntent().getAction());
        if(pendingExternalStart){current="route";selectedRouteSession=-1;}
        sensors=new LiveSensorController(this,db,this::displayRotation,this::onLiveSensor);
        if(hudMode) showHud(); else showScreen(current);
    }

    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("screen",current);out.putLong("routeSession",selectedRouteSession);out.putBoolean("hudMode",hudMode);out.putDouble("uiMaxLeft",uiMaxLeft);out.putDouble("uiMaxRight",uiMaxRight);}

    private void applySystemBarAppearance(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;c.setSystemBarsAppearance(AppPrefs.lightTheme(this)?mask:0,mask);}}
    }

    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge(){
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams lp=getWindow().getAttributes();lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(lp);}
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private int displayRotation(){return getWindowManager().getDefaultDisplay().getRotation();}
    private int widthDp(){if(Build.VERSION.SDK_INT>=30)return Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().width()/getResources().getDisplayMetrics().density);return getResources().getConfiguration().screenWidthDp;}
    private int heightDp(){if(Build.VERSION.SDK_INT>=30)return Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().height()/getResources().getDisplayMetrics().density);return getResources().getConfiguration().screenHeightDp;}
    private boolean wide(){return widthDp()>=600;}
    private boolean compact(){return widthDp()<480;}
    private boolean compactHeight(){return heightDp()<480;}

    private void showScreen(String destination){
        if(routeTrace!=null){try{routeTrace.stopLoading();routeTrace.destroy();}catch(Exception ignored){}routeTrace=null;}
        Ui.applyTheme(this);
        hudMode=false;
        restoreSystemBars();
        current=destination;
        windowRoot=new FrameLayout(this);windowRoot.setBackground(Ui.appBackground(this));setContentView(windowRoot);windowRoot.post(this::applySystemBarAppearance);
        safeRoot=new EdgeSafeFrameLayout(this);windowRoot.addView(safeRoot,new FrameLayout.LayoutParams(-1,-1));
        shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);safeRoot.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        shell.addView(buildHeader(),new LinearLayout.LayoutParams(-1,Ui.dp(this,compactHeight()?46:56)));
        body=new FrameLayout(this);LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);bodyLp.topMargin=Ui.dp(this,6);shell.addView(body,bodyLp);
        switch(destination){
            case "telemetry":buildTelemetry();break;case "route":buildRoute();break;case "history":buildHistory();break;case "garage":buildGarage();break;case "stats":buildStats();break;case "settings":buildSettings();break;case "more":buildMore();break;default:buildIncline();break;
        }
        BottomNavBar nav=new BottomNavBar(this,wide(),compactHeight(),current,this::onNav);LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,compactHeight()?58:76));navLp.topMargin=Ui.dp(this,compactHeight()?2:6);shell.addView(nav,navLp);
        updateHeader();
    }

    private View buildHeader(){
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this,compactHeight()?8:12),Ui.dp(this,4),Ui.dp(this,compactHeight()?8:12),Ui.dp(this,4));
        bar.setBackground(Ui.panel(this));

        profileChip=Ui.title(this,"",compactHeight()?13.5f:(wide()?16.5f:14.5f));
        profileChip.setSingleLine(true);
        profileChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        profileChip.setGravity(Gravity.CENTER_VERTICAL);
        profileChip.setPadding(Ui.dp(this,5),0,Ui.dp(this,8),0);
        profileChip.setContentDescription("LateralWheelie, conductor y moto activos. Toca para editar.");
        profileChip.setOnClickListener(v->showScreen("garage"));
        bar.addView(profileChip,new LinearLayout.LayoutParams(0,-1,1));

        sessionChip=Ui.title(this,"",compactHeight()?9f:(wide()?11f:9.5f));
        sessionChip.setGravity(Gravity.CENTER);
        sessionChip.setMaxLines(1);
        sessionChip.setPadding(Ui.dp(this,7),0,Ui.dp(this,7),0);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(
                compactHeight()?Ui.dp(this,112):(wide()?Ui.dp(this,170):Ui.dp(this,104)),
                Ui.dp(this,compactHeight()?32:36));
        bar.addView(sessionChip,sp);
        setHeaderIdentity();
        return bar;
    }

    private void setHeaderIdentity(){
        if(profileChip==null)return;
        String brand="lateralWheelie";
        String profile=db.activeLabel();
        String text=brand+"  ·  "+profile;
        SpannableString ss=new SpannableString(text);
        ss.setSpan(new ForegroundColorSpan(Ui.TEXT),0,7,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Ui.RED),7,brand.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int ps=brand.length()+3;
        ss.setSpan(new ForegroundColorSpan(Ui.MUTED),ps,ss.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new RelativeSizeSpan(compactHeight()?.78f:.82f),ps,ss.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        profileChip.setText(ss);
        profileChip.setTypeface(android.graphics.Typeface.create("sans",android.graphics.Typeface.BOLD_ITALIC));
    }

    private void updateHeader(){
        setHeaderIdentity();
        if(sessionChip==null)return;
        if(RideState.active(this)){
            long elapsed=Math.max(0,System.currentTimeMillis()-RideState.startMs(this));long sec=elapsed/1000;
            sessionChip.setText(String.format(Locale.getDefault(),"● REC %02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60));
            sessionChip.setTextColor(Ui.RED);
            sessionChip.setBackground(Ui.outlined(Color.rgb(34,8,15),Color.rgb(118,25,39),12,1,this));
        }else{
            sessionChip.setText("LISTO");
            sessionChip.setTextColor(Ui.GREEN);
            sessionChip.setBackground(Ui.outlined(Color.rgb(7,31,28),Color.rgb(23,83,73),12,1,this));
        }
    }

    private void onNav(String d){
        if("more".equals(d)){showScreen("more");return;}selectedRouteSession=-1;showScreen(d);
    }

    private void buildIncline(){
        LinearLayout ride=new LinearLayout(this);
        ride.setOrientation(LinearLayout.VERTICAL);
        body.addView(ride,new FrameLayout.LayoutParams(-1,-1));

        gauge=new RideGaugeView(this);
        gauge.setRiding(RideState.active(this));
        gauge.setGpsFix(RideState.gpsFix(this));
        gauge.setRouteMax(uiMaxLeft,uiMaxRight);
        gauge.setRideTelemetry(RideState.speed(this)*3.6f,RideState.altitude(this),RideState.distanceM(this),RideState.temperatureC(this),RideState.startMs(this));
        gauge.setLongitudinalPeaks(RideState.maxBrakeG(this),RideState.maxAccelG(this));
        if(snapshot!=null){gauge.setRoll(snapshot.lean);gauge.setLongitudinalG(snapshot.longG);}
        ride.addView(gauge,new LinearLayout.LayoutParams(-1,0,1));
        ride.addView(buildRideUtilityBar(false),new LinearLayout.LayoutParams(-1,Ui.dp(this,compactHeight()?42:48)));
    }

    private View buildRideUtilityBar(boolean hud){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this,10),Ui.dp(this,3),Ui.dp(this,10),Ui.dp(this,3));
        row.setBackground(Ui.panel(this));

        TextView reset=Ui.action(this,"RESET MÁX.",Ui.RED,false);
        reset.setAlpha(.86f);reset.setTextSize(10.5f);
        reset.setOnClickListener(v->armOrResetMax(reset));
        row.addView(reset,new LinearLayout.LayoutParams(0,-1,1));

        TextView profile=Ui.action(this,"CONDUCTOR / MOTO",Ui.BLUE,false);
        profile.setTextSize(10f);
        profile.setOnClickListener(v->{if(hud)exitHud();showScreen("garage");});
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-1,1.25f);pp.leftMargin=Ui.dp(this,6);row.addView(profile,pp);

        TextView full=Ui.action(this,hud?"× SALIR":"⛶ PANTALLA",Ui.BLUE,false);
        full.setAlpha(.86f);full.setTextSize(10f);
        full.setContentDescription(hud?"Salir de pantalla completa":"Pantalla completa");
        full.setOnClickListener(v->{if(hud)exitHud();else showHud();});
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-1,1);fp.leftMargin=Ui.dp(this,6);row.addView(full,fp);
        return row;
    }

    private void armOrResetMax(TextView button){
        long now=System.currentTimeMillis();
        if(resetArmedAt>0 && now-resetArmedAt>=250 && now-resetArmedAt<=2500){
            resetArmedAt=0;button.setText("RESET MÁX.");
            uiMaxLeft=0;uiMaxRight=0;uiMaxSessionId=RideState.sessionId(this);uiMaxInitialized=true;if(gauge!=null)gauge.setRouteMax(0,0);
            RideState.resetMax(this);RideState.resetGPeaks(this);if(gauge!=null)gauge.resetGPeaks();
            if(RideState.active(this)){
                Intent i=new Intent(this,RideService.class).setAction(RideService.ACTION_RESET_MAX);
                startService(i);
            }
            return;
        }
        resetArmedAt=now;button.setText("¿RESET?");
        handler.postDelayed(()->{if(resetArmedAt>0 && System.currentTimeMillis()-resetArmedAt>=2400){resetArmedAt=0;button.setText("RESET MÁX.");}},2500);
    }

    private void showHud(){
        hudMode=true;current="incline";
        windowRoot=new FrameLayout(this);windowRoot.setBackground(Ui.appBackground(this));setContentView(windowRoot);windowRoot.post(this::applySystemBarAppearance);

        LinearLayout hudRoot=new LinearLayout(this);hudRoot.setOrientation(LinearLayout.VERTICAL);
        windowRoot.addView(hudRoot,new FrameLayout.LayoutParams(-1,-1));

        gauge=new RideGaugeView(this);
        gauge.setRiding(RideState.active(this));gauge.setGpsFix(RideState.gpsFix(this));gauge.setRouteMax(uiMaxLeft,uiMaxRight);
        gauge.setRideTelemetry(RideState.speed(this)*3.6f,RideState.altitude(this),RideState.distanceM(this),RideState.temperatureC(this),RideState.startMs(this));
        gauge.setLongitudinalPeaks(RideState.maxBrakeG(this),RideState.maxAccelG(this));
        if(snapshot!=null){gauge.setRoll(snapshot.lean);gauge.setLongitudinalG(snapshot.longG);}
        hudRoot.addView(gauge,new LinearLayout.LayoutParams(-1,0,1));
        hudRoot.addView(buildRideUtilityBar(true),new LinearLayout.LayoutParams(-1,Ui.dp(this,compactHeight()?40:46)));
        windowRoot.post(this::enterImmersiveMode);
    }

    private void exitHud(){hudMode=false;restoreSystemBars();showScreen("incline");}

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(android.view.WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @SuppressWarnings("deprecation")
    private void restoreSystemBars(){
        if(Build.VERSION.SDK_INT>=30){View decor=getWindow().getDecorView();decor.post(()->{WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.show(android.view.WindowInsets.Type.systemBars());});}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private ScrollView scrollContainer(){ScrollView s=new ScrollView(this);s.setFillViewport(true);LinearLayout c=new LinearLayout(this);c.setId(android.R.id.content);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,12));s.addView(c,new ScrollView.LayoutParams(-1,-2));body.addView(s,new FrameLayout.LayoutParams(-1,-1));return s;}
    private LinearLayout scrollContent(ScrollView s){return (LinearLayout)s.getChildAt(0);}
    private TextView screenTitle(String title,String subtitle){
        TextView v=Ui.title(this,"",wide()?24:21);v.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,8));
        if(subtitle==null){v.setText(title);return v;}
        SpannableString x=new SpannableString(title+"\n"+subtitle);int start=title.length()+1;x.setSpan(new ForegroundColorSpan(Ui.MUTED),start,x.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);x.setSpan(new RelativeSizeSpan(.60f),start,x.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);v.setText(x);return v;
    }

    private LinearLayout statCard(String label,String value){LinearLayout c=Ui.card(this);TextView l=Ui.title(this,label,11);l.setTextColor(Ui.MUTED);TextView v=Ui.title(this,value,wide()?26:22);v.setPadding(0,Ui.dp(this,4),0,0);c.addView(l);c.addView(v);return c;}

    private void buildTelemetry(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Telemetría","Datos detallados fuera de la vista de conducción"));
        LinearLayout r1=statRow();tAngle=valueInto(r1,"ÁNGULO","0.0°");tRate=valueInto(r1,"ROLL RATE","0°/s");c.addView(r1,Ui.lpMatchWrap(this,4));
        LinearLayout r2=statRow();tLatG=valueInto(r2,"G LATERAL","0.00 g");tLongG=valueInto(r2,"G LONGITUDINAL · GAS+/FRENO−","0.00 g");c.addView(r2,Ui.lpMatchWrap(this,8));
        LinearLayout r3=statRow();tSpeed=valueInto(r3,"VELOCIDAD GPS","0 km/h");tAlt=valueInto(r3,"ALTITUD","0 m");c.addView(r3,Ui.lpMatchWrap(this,8));
        LinearLayout r4=statRow();tGps=valueInto(r4,"GPS","SIN FIJACIÓN");tTemp=valueInto(r4,"TEMPERATURA AMBIENTE","--°C");c.addView(r4,Ui.lpMatchWrap(this,8));
        LinearLayout r5=statRow();tRouteTime=valueInto(r5,"TIEMPO DE RUTA","00:00:00");tDistance=valueInto(r5,"DISTANCIA","0.0 km");c.addView(r5,Ui.lpMatchWrap(this,8));
        gMeter=new GForceView(this);LinearLayout.LayoutParams gm=new LinearLayout.LayoutParams(-1,compact()?Ui.dp(this,240):Ui.dp(this,280));gm.topMargin=Ui.dp(this,10);c.addView(gMeter,gm);
        LinearLayout note=Ui.card(this);TextView nt=Ui.text(this,"La inclinación usa gravedad (con fallback al acelerómetro) y remapea los ejes según la rotación real de pantalla. La protección de soporte bloquea nuevos máximos si detecta manipulación brusca del teléfono y no se rearma hasta volver cerca de 0° de forma estable. En una ruta, los máximos se aceptan con GPS y movimiento ≥ 8 km/h. Las G usan aceleración lineal cuando existe.",13,Ui.MUTED);note.addView(nt);c.addView(note,Ui.lpMatchWrap(this,10));
        refreshTelemetryText();
    }

    private LinearLayout statRow(){LinearLayout row=new LinearLayout(this);row.setOrientation(compact()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);return row;}
    private TextView valueInto(LinearLayout row,String label,String value){LinearLayout card=statCard(label,value);TextView v=(TextView)card.getChildAt(1);LinearLayout.LayoutParams lp=compact()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(Ui.dp(this,4),compact()?Ui.dp(this,4):0,Ui.dp(this,4),0);row.addView(card,lp);return v;}

    private void refreshTelemetryText(){
        if(snapshot!=null){if(tAngle!=null)tAngle.setText(snapshot.flatSurface?"0.0° · REPOSO":String.format(Locale.getDefault(),"%+.1f°",snapshot.lean));if(tRate!=null)tRate.setText(String.format(Locale.getDefault(),"%+.0f°/s",snapshot.rollRate));if(tLatG!=null)tLatG.setText(String.format(Locale.getDefault(),"%+.2f g",snapshot.latG));if(tLongG!=null)tLongG.setText(String.format(Locale.getDefault(),"%+.2f g",snapshot.longG));if(gMeter!=null)gMeter.setG(snapshot.latG,snapshot.longG);}
        boolean fix=RideState.gpsFix(this);
        if(tSpeed!=null)tSpeed.setText(fix?String.format(Locale.getDefault(),"%.0f km/h",RideState.speed(this)*3.6):"-- km/h");
        if(tAlt!=null)tAlt.setText(fix?String.format(Locale.getDefault(),"%.0f m",RideState.altitude(this)):"-- m");
        if(tGps!=null)tGps.setText(fix?"FIJACIÓN OK":"SIN FIJACIÓN");
        if(tTemp!=null){float tc=snapshot!=null&&!Float.isNaN(snapshot.ambientC)?snapshot.ambientC:RideState.temperatureC(this);tTemp.setText(Float.isNaN(tc)?"NO DISP.":String.format(Locale.getDefault(),"%.0f°C",tc));}
        if(tDistance!=null)tDistance.setText(RideState.active(this)?String.format(Locale.getDefault(),"%.1f km",RideState.distanceM(this)/1000f):"—");
        if(tRouteTime!=null){long sec=RideState.active(this)&&RideState.startMs(this)>0?Math.max(0,(System.currentTimeMillis()-RideState.startMs(this))/1000):-1;tRouteTime.setText(sec<0?"—":String.format(Locale.getDefault(),"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60));}
    }

    private void buildRoute(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Ruta","GPS + inclinación + telemetría"));
        routeState=Ui.title(this,"",wide()?24:20);routeMeta=Ui.text(this,"",13,Ui.MUTED);LinearLayout status=Ui.card(this);status.addView(routeState);status.addView(routeMeta);c.addView(status,Ui.lpMatchWrap(this,4));
        TextView action=Ui.action(this,RideState.active(this)?"DETENER RUTA":"INICIAR RUTA",RideState.active(this)?Ui.RED:Ui.BLUE,true);c.addView(action,Ui.lpMatchWrap(this,10));action.setOnClickListener(v->{if(RideState.active(this))stopRoute();else requestRouteStart();});
        routeTrace=new RouteMapView(this);LinearLayout.LayoutParams tr=new LinearLayout.LayoutParams(-1,wide()?Ui.dp(this,310):Ui.dp(this,250));tr.topMargin=Ui.dp(this,10);c.addView(routeTrace,tr);
        TextView legend=Ui.text(this,"Satélite con perspectiva estable · inicio verde · fin rojo · trazado coloreado por inclinación. Puedes cambiar a CALLE; si fallan varias teselas satélite el mapa usa ese respaldo automáticamente. La ruta GPS sigue guardándose localmente.",12,Ui.MUTED);legend.setPadding(Ui.dp(this,4),Ui.dp(this,8),0,0);c.addView(legend);
        LinearLayout compareCard=Ui.card(this);compareCard.addView(Ui.title(this,"COMPARATIVA DE RUTA",12));
        routeCompare=Ui.text(this,"Buscando rutas anteriores coincidentes…",12,Ui.MUTED);compareCard.addView(routeCompare,Ui.lpMatchWrap(this,5));
        c.addView(compareCard,Ui.lpMatchWrap(this,8));
        if(selectedRouteSession>0 && !RideState.active(this)){TelemetryDb.Session x=db.session(selectedRouteSession);if(x!=null){TextView del=Ui.action(this,"ELIMINAR ESTA RUTA",Ui.RED,false);c.addView(del,Ui.lpMatchWrap(this,10));del.setOnClickListener(v->confirmDeleteSession(x.id));}}
        updateRouteUi(true);
    }

    private void updateRouteUi(boolean forceTrace){
        if(routeState==null)return;
        boolean active=RideState.active(this);long sid=active?RideState.sessionId(this):(selectedRouteSession>0?selectedRouteSession:db.latestSessionId());
        if(active){routeState.setText(RideState.gpsFix(this)?"● GRABANDO · GPS OK":"● GRABANDO · buscando GPS");routeState.setTextColor(RideState.gpsFix(this)?Ui.GREEN:Ui.YELLOW);long sec=Math.max(0,(System.currentTimeMillis()-RideState.startMs(this))/1000);routeMeta.setText(String.format(Locale.getDefault(),"%02d:%02d:%02d  ·  %d muestras  ·  máx. ← %.1f° / %.1f° →",sec/3600,(sec/60)%60,sec%60,RideState.samples(this),Math.abs(RideState.maxLeft(this)),RideState.maxRight(this)));}
        else if(sid>0){TelemetryDb.Session x=db.session(sid);routeState.setText("RUTA GUARDADA");routeState.setTextColor(Ui.TEXT);routeMeta.setText(x==null?"":new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(x.start))+"  ·  "+x.driver+" / "+x.bike+"\nMáx. ← "+String.format(Locale.getDefault(),"%.1f°",Math.abs(x.left))+" ("+timeOrDash(x.leftTs)+")   "+String.format(Locale.getDefault(),"%.1f°",x.right)+" → ("+timeOrDash(x.rightTs)+")");}
        else{routeState.setText("SIN RUTA ACTIVA");routeState.setTextColor(Ui.MUTED);routeMeta.setText("Inicia una ruta para registrar el trazado GPS y la inclinación. El inclinómetro funciona aunque no grabes una ruta.");}
        long now=System.currentTimeMillis();
        if(routeTrace!=null&&sid>0&&(forceTrace||(active&&now-lastTraceRefresh>3200))){
            routeTrace.setSamples(db.samples(sid,active?1200:2200));
            lastTraceRefresh=now;
        }
        if(routeCompare!=null){
            if(sid<=0){routeCompare.setText("Aún no hay una ruta para comparar.");}
            else if(forceTrace || sid!=lastComparedSession || now-lastComparisonRefresh>5000){
                cachedComparison=db.findRouteComparison(sid,active);
                lastComparedSession=sid;lastComparisonRefresh=now;
                renderRouteComparison(cachedComparison,active);
            }
        }
    }

    private void renderRouteComparison(TelemetryDb.RouteComparison r,boolean active){
        if(routeCompare==null)return;
        if(r==null){
            routeCompare.setText(active?"Todavía no se reconoce una ruta anterior coincidente. La comparación aparecerá cuando haya suficiente trazado GPS común.":"No se encontró una ruta anterior suficientemente parecida.");
            routeCompare.setTextColor(Ui.MUTED);return;
        }
        SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault());
        double currentAvg=active&&RideState.leanCount(this)>0?RideState.leanSum(this)/RideState.leanCount(this):r.currentAvgLean;
        double avgDelta=currentAvg-r.previousAvgLean;
        String timeDelta=formatSignedDuration(r.timeDeltaMs);
        String match=String.format(Locale.getDefault(),"Coincide con %s · separación media GPS ~%.0f m",df.format(new Date(r.previousStart)),r.matchMeters);
        String time=String.format(Locale.getDefault(),"%s: %s · anterior %s · Δ %s",active?"Hasta este punto":"Tiempo",formatDuration(r.currentElapsedMs),formatDuration(r.previousElapsedMs),timeDelta);
        String lean=String.format(Locale.getDefault(),"Inclinación media: %.1f° · anterior %.1f° · Δ %+.1f°",currentAvg,r.previousAvgLean,avgDelta);
        String peaks=String.format(Locale.getDefault(),"Máx. sesión: ← %.1f° / %.1f° → · anterior ← %.1f° / %.1f° →",Math.abs(r.currentLeft),r.currentRight,Math.abs(r.previousLeft),r.previousRight);
        routeCompare.setText(match+"\n"+time+"\n"+lean+"\n"+peaks);
        routeCompare.setTextColor(Ui.TEXT);
    }

    private String formatDuration(long ms){
        long s=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);
    }
    private String formatSignedDuration(long ms){
        String sign=ms>0?"+":ms<0?"−":"±";long s=Math.abs(ms)/1000;
        return String.format(Locale.getDefault(),"%s%02d:%02d:%02d",sign,s/3600,(s/60)%60,s%60);
    }

    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean locationEnabled(){try{android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);return lm!=null && (Build.VERSION.SDK_INT>=28?lm.isLocationEnabled():true);}catch(RuntimeException e){return false;}}

    private void requestRouteStart(){
        if(RideState.active(this)){showScreen("route");return;}
        if(!hasLocationPermission()){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},REQ_ROUTE_LOCATION);return;
        }
        if(!locationEnabled()){
            new AlertDialog.Builder(this).setTitle("Activa la ubicación").setMessage("Para guardar el trazado de la ruta, Android exige que la ubicación del sistema esté activa. El inclinómetro seguirá funcionando sin GPS.")
                    .setNegativeButton("Cancelar",null).setPositiveButton("Abrir ajustes",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();return;
        }
        try{uiMaxLeft=0;uiMaxRight=0;uiMaxInitialized=true;uiMaxSessionId=-1;if(gauge!=null)gauge.setRouteMax(0,0);startForegroundService(new Intent(this,RideService.class));selectedRouteSession=-1;handler.postDelayed(()->{updateHeader();if(!RideState.active(this))Toast.makeText(this,"La grabación no pudo iniciarse. Revisa permisos y ubicación.",Toast.LENGTH_LONG).show();showScreen("route");},450);}catch(RuntimeException ex){new AlertDialog.Builder(this).setTitle("No se pudo iniciar la ruta").setMessage(ex.getClass().getSimpleName()+"\nLa aplicación seguirá funcionando como inclinómetro.").setPositiveButton("Aceptar",null).show();}
    }

    private void stopRoute(){stopService(new Intent(this,RideService.class));handler.postDelayed(()->{selectedRouteSession=db.latestSessionId();showScreen("route");},350);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_WALLPAPER&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}AppPrefs.setWallpaperUri(this,uri);Toast.makeText(this,"Fondo personalizado guardado",Toast.LENGTH_SHORT).show();showScreen("settings");}}

    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] grants){
        super.onRequestPermissionsResult(req,perms,grants);
        if(req==REQ_ROUTE_LOCATION){if(hasLocationPermission())requestRouteStart();else new AlertDialog.Builder(this).setTitle("Ruta sin GPS").setMessage("No se ha concedido ubicación. No se iniciará el servicio de ruta y no habrá cierre inesperado. El inclinómetro y la telemetría de pantalla siguen disponibles.").setPositiveButton("Aceptar",null).show();}
        else if(req==REQ_BLUETOOTH_CONNECT){if(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)chooseBtuDevice();else Toast.makeText(this,"Sin permiso Bluetooth no se puede usar Honda BTU como disparador.",Toast.LENGTH_LONG).show();}
    }

    private void buildHistory(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Historial","Rutas almacenadas localmente"));ArrayList<TelemetryDb.Session> list=db.sessions();
        if(list.isEmpty()){LinearLayout empty=Ui.card(this);empty.addView(Ui.text(this,"Todavía no hay rutas guardadas.",15,Ui.MUTED));c.addView(empty,Ui.lpMatchWrap(this,6));return;}
        SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault());
        for(TelemetryDb.Session x:list){LinearLayout card=Ui.card(this);TextView h=Ui.title(this,df.format(new Date(x.start))+" · "+x.driver+" / "+x.bike,15);TextView v=Ui.text(this,String.format(Locale.getDefault(),"← %.1f°     %.1f° →     ·     LAT %.2f g",Math.abs(x.left),x.right,x.latG),13,Ui.MUTED);card.addView(h);card.addView(v);LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView open=Ui.action(this,"VER RUTA",Ui.BLUE,false);TextView del=Ui.action(this,"ELIMINAR",Ui.RED,false);actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=Ui.dp(this,8);actions.addView(del,dp);card.addView(actions,Ui.lpMatchWrap(this,10));c.addView(card,Ui.lpMatchWrap(this,8));open.setOnClickListener(vw->{selectedRouteSession=x.id;showScreen("route");});del.setOnClickListener(vw->confirmDeleteSession(x.id));}
    }

    private void confirmDeleteSession(long id){new AlertDialog.Builder(this).setTitle("Eliminar ruta").setMessage("Se eliminarán la sesión y todas sus muestras GPS/telemetría. Esta acción no se puede deshacer.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{db.deleteSession(id);if(selectedRouteSession==id)selectedRouteSession=-1;showScreen("history");}).show();}

    private void buildGarage(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);
        c.addView(screenTitle("Garaje","Edita conductor, moto y calibración del soporte"));

        TelemetryDb.Driver activeDriver=findDriver(db.activeDriver());
        TelemetryDb.Bike activeBike=findBike(db.activeBike());

        LinearLayout active=Ui.card(this);
        active.addView(Ui.title(this,"PERFIL ACTIVO",11));
        active.addView(Ui.title(this,db.activeLabel(),wide()?22:19));
        active.addView(Ui.text(this,"Este perfil aparece en la cabecera. Toca la cabecera desde cualquier pantalla para volver aquí.",11,Ui.MUTED),Ui.lpMatchWrap(this,5));
        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);
        TextView ed=Ui.action(this,"EDITAR CONDUCTOR",Ui.BLUE,false);
        TextView eb=Ui.action(this,"EDITAR MOTO",Ui.BLUE,false);
        quick.addView(ed,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        LinearLayout.LayoutParams ebp=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);ebp.leftMargin=Ui.dp(this,6);quick.addView(eb,ebp);
        active.addView(quick,Ui.lpMatchWrap(this,8));
        ed.setOnClickListener(v->{if(activeDriver!=null)editDriver(activeDriver);});
        eb.setOnClickListener(v->{if(activeBike!=null)editBike(activeBike);});
        c.addView(active,Ui.lpMatchWrap(this,4));

        LinearLayout cal=Ui.card(this);cal.addView(Ui.title(this,"CALIBRACIÓN 0°",13));
        String raw=snapshot==null?"Sin lectura":"Lectura actual: "+String.format(Locale.getDefault(),"%+.1f°",snapshot.rawLean);
        cal.addView(Ui.text(this,raw+"\nSe guarda por moto y por orientación física del teléfono.",13,Ui.MUTED));
        TextView calBtn=Ui.action(this,"CALIBRAR ESTA POSICIÓN COMO 0°",Ui.BLUE,false);
        cal.addView(calBtn,Ui.lpMatchWrap(this,10));
        calBtn.setOnClickListener(v->{if(snapshot==null){Toast.makeText(this,"Aún no hay lectura de sensor",Toast.LENGTH_SHORT).show();return;}db.setCalibration(snapshot.rotation,snapshot.rawLean);sensors.refreshCalibration();Toast.makeText(this,"Calibración guardada para esta moto/orientación",Toast.LENGTH_SHORT).show();showScreen("garage");});
        c.addView(cal,Ui.lpMatchWrap(this,8));

        c.addView(Ui.title(this,"CONDUCTORES",13),Ui.lpMatchWrap(this,14));
        for(TelemetryDb.Driver d:db.drivers()){
            c.addView(profileRow(d.name,d.id==db.activeDriver(),
                    ()->{db.setActive(d.id,db.activeBike());sensors.refreshCalibration();showScreen("garage");},
                    ()->editDriver(d),
                    ()->{if(!db.deleteDriver(d.id))Toast.makeText(this,"No se puede borrar: está activo, es el último o tiene rutas asociadas.",Toast.LENGTH_LONG).show();else showScreen("garage");}),
                    Ui.lpMatchWrap(this,6));
        }
        TextView addD=Ui.action(this,"+ AÑADIR CONDUCTOR",Ui.BLUE,false);c.addView(addD,Ui.lpMatchWrap(this,8));
        addD.setOnClickListener(v->inputText("Nuevo conductor","Nombre",name->{long id=db.addDriver(name);if(id>0){db.setActive(id,db.activeBike());showScreen("garage");}}));

        c.addView(Ui.title(this,"MOTOS",13),Ui.lpMatchWrap(this,16));
        for(TelemetryDb.Bike b:db.bikes()){
            String label=b.name+(b.model==null||b.model.trim().isEmpty()?"":" · "+b.model);
            c.addView(profileRow(label,b.id==db.activeBike(),
                    ()->{db.setActive(db.activeDriver(),b.id);sensors.refreshCalibration();showScreen("garage");},
                    ()->editBike(b),
                    ()->{if(!db.deleteBike(b.id))Toast.makeText(this,"No se puede borrar: está activa, es la última o tiene rutas asociadas.",Toast.LENGTH_LONG).show();else showScreen("garage");}),
                    Ui.lpMatchWrap(this,6));
        }
        TextView addB=Ui.action(this,"+ AÑADIR MOTO",Ui.BLUE,false);c.addView(addB,Ui.lpMatchWrap(this,8));addB.setOnClickListener(v->inputBike());
    }

    private TelemetryDb.Driver findDriver(long id){for(TelemetryDb.Driver d:db.drivers())if(d.id==id)return d;return null;}
    private TelemetryDb.Bike findBike(long id){for(TelemetryDb.Bike b:db.bikes())if(b.id==id)return b;return null;}

    private View profileRow(String name,boolean active,Runnable select,Runnable edit,Runnable delete){
        LinearLayout card=Ui.card(this);card.setOrientation(LinearLayout.VERTICAL);
        TextView nameV=Ui.title(this,(active?"● ":"")+name,14);nameV.setTextColor(active?Ui.GREEN:Ui.TEXT);nameV.setSingleLine(false);
        card.addView(nameV,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView use=Ui.action(this,active?"ACTIVO":"USAR",active?Ui.GREEN:Ui.BLUE,false);use.setEnabled(!active);
        TextView ed=Ui.action(this,"EDITAR",Ui.BLUE,false);
        TextView del=Ui.action(this,"ELIMINAR",Ui.RED,false);
        actions.addView(use,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,Ui.dp(this,42),1);ep.leftMargin=Ui.dp(this,5);actions.addView(ed,ep);
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,Ui.dp(this,42),1);dp.leftMargin=Ui.dp(this,5);actions.addView(del,dp);
        card.addView(actions,Ui.lpMatchWrap(this,8));

        use.setOnClickListener(v->select.run());ed.setOnClickListener(v->edit.run());
        del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Eliminar").setMessage("Solo se pueden eliminar perfiles sin rutas asociadas y que no estén activos.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->delete.run()).show());
        return card;
    }

    interface Got{void ok(String s);}
    private void inputText(String title,String hint,Got got){
        EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad=Ui.dp(this,20);FrameLayout wrap=new FrameLayout(this);wrap.setPadding(pad,0,pad,0);wrap.addView(e,new FrameLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle(title).setView(wrap).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{String value=e.getText().toString().trim();if(!value.isEmpty())got.ok(value);}).show();
    }

    private void editDriver(TelemetryDb.Driver driver){
        EditText e=new EditText(this);e.setText(driver.name);e.setSelection(e.getText().length());e.setHint("Nombre del conductor");e.setInputType(InputType.TYPE_CLASS_TEXT);
        int pad=Ui.dp(this,20);FrameLayout wrap=new FrameLayout(this);wrap.setPadding(pad,0,pad,0);wrap.addView(e,new FrameLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("Editar conductor").setView(wrap).setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w)->{String value=e.getText().toString().trim();if(value.isEmpty()||!db.updateDriver(driver.id,value))Toast.makeText(this,"No se pudo guardar el nombre.",Toast.LENGTH_LONG).show();showScreen("garage");}).show();
    }

    private void inputBike(){showBikeDialog(null);}

    private void editBike(TelemetryDb.Bike bike){showBikeDialog(bike);}

    private void showBikeDialog(TelemetryDb.Bike bike){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
        EditText n=new EditText(this);n.setHint("Nombre, por ejemplo CB650R");
        EditText m=new EditText(this);m.setHint("Modelo / año (opcional)");
        if(bike!=null){n.setText(bike.name);m.setText(bike.model==null?"":bike.model);}
        box.addView(n);box.addView(m);
        new AlertDialog.Builder(this).setTitle(bike==null?"Nueva moto":"Editar moto").setView(box).setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w)->{
                    String name=n.getText().toString().trim();String model=m.getText().toString().trim();
                    if(name.isEmpty()){Toast.makeText(this,"La moto necesita un nombre.",Toast.LENGTH_LONG).show();showScreen("garage");return;}
                    if(bike==null){long id=db.addBike(name,model);if(id>0){db.setActive(db.activeDriver(),id);sensors.refreshCalibration();}}
                    else if(!db.updateBike(bike.id,name,model))Toast.makeText(this,"No se pudo guardar la moto.",Toast.LENGTH_LONG).show();
                    showScreen("garage");
                }).show();
    }

    private void buildStats(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Estadísticas","Máximos y evolución de tus sesiones"));TelemetryDb.Stats st=db.stats();
        LinearLayout today=Ui.card(this);today.addView(Ui.title(this,"MÁXIMOS DE HOY",12));today.addView(Ui.title(this,String.format(Locale.getDefault(),"← %.1f°   ·   %.1f° →",Math.abs(st.todayLeft),st.todayRight),wide()?28:24));today.addView(Ui.text(this,"Izquierda: "+timeOrDash(st.todayLeftTs)+"    ·    Derecha: "+timeOrDash(st.todayRightTs),12,Ui.MUTED));c.addView(today,Ui.lpMatchWrap(this,6));
        LinearLayout hist=Ui.card(this);hist.addView(Ui.title(this,"HISTÓRICO",12));hist.addView(Ui.title(this,String.format(Locale.getDefault(),"← %.1f°   ·   %.1f° →",Math.abs(st.maxLeft),st.maxRight),wide()?28:24));hist.addView(Ui.text(this,"Fechas de pico: "+dateTimeOrDash(st.historicalLeftTs)+" / "+dateTimeOrDash(st.historicalRightTs),12,Ui.MUTED));c.addView(hist,Ui.lpMatchWrap(this,8));
        LinearLayout row=new LinearLayout(this);row.setOrientation(compact()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);LinearLayout a=statCard("PROMEDIO PICO/SESIÓN",String.format(Locale.getDefault(),"%.1f°",st.avgPeak));LinearLayout b=statCard("SESIONES",String.valueOf(st.sessions));if(compact()){row.addView(a,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(-1,-2);rb.topMargin=Ui.dp(this,8);row.addView(b,rb);}else{row.addView(a,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rb=new LinearLayout.LayoutParams(0,-2,1);rb.leftMargin=Ui.dp(this,8);row.addView(b,rb);}c.addView(row,Ui.lpMatchWrap(this,8));
        LinearLayout sides=Ui.card(this);sides.addView(Ui.title(this,"PROMEDIO POR LADO",12));sides.addView(Ui.text(this,String.format(Locale.getDefault(),"Izquierda %.1f°    ·    Derecha %.1f°",st.avgLeft,st.avgRight),16,Ui.TEXT));c.addView(sides,Ui.lpMatchWrap(this,8));
        LinearLayout avg=Ui.card(this);avg.addView(Ui.title(this,"INCLINACIÓN MEDIA DESDE RESET",12));
        avg.addView(Ui.title(this,String.format(Locale.getDefault(),"%.1f°",LeanAverageStore.average(this)),wide()?27:23));
        long rt=LeanAverageStore.resetTs(this);
        avg.addView(Ui.text(this,(rt>0?"Reiniciado: "+new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(rt)):"Desde el primer registro")+" · "+LeanAverageStore.count(this)+" muestras de conducción válidas",11,Ui.MUTED));
        c.addView(avg,Ui.lpMatchWrap(this,8));
    }
    private String timeOrDash(long ts){return ts<=0?"—":new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ts));}private String dateTimeOrDash(long ts){return ts<=0?"—":new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(ts));}

    private void buildSettings(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Ajustes","Apariencia, fondo, sensores y datos"));

        LinearLayout appearance=Ui.card(this);appearance.addView(Ui.title(this,"APARIENCIA",13));
        appearance.addView(Ui.text(this,"Tema",11,Ui.MUTED));
        LinearLayout themes=new LinearLayout(this);themes.setOrientation(compact()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        String[] themeNames={"CLARO","OSCURO","SEGÚN SISTEMA"};String[] themeValues={"light","dark","system"};String currentTheme=AppPrefs.theme(this);
        for(int i=0;i<themeNames.length;i++){final String value=themeValues[i];TextView b=Ui.action(this,(currentTheme.equals(value)?"● ":"")+themeNames[i],currentTheme.equals(value)?Ui.GREEN:Ui.BLUE,false);LinearLayout.LayoutParams lp=compact()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.setMargins(compact()?0:Ui.dp(this,6),compact()?Ui.dp(this,6):0,0,0);themes.addView(b,lp);b.setOnClickListener(v->{AppPrefs.setTheme(this,value);Ui.applyTheme(this);showScreen("settings");});}
        appearance.addView(themes,Ui.lpMatchWrap(this,6));
        appearance.addView(Ui.text(this,"Idioma de interfaz",11,Ui.MUTED),Ui.lpMatchWrap(this,10));
        LinearLayout langs=new LinearLayout(this);langs.setOrientation(LinearLayout.HORIZONTAL);String[] ln={"Español","English","Galego"};String[] lv={"es","en","gl"};String lang=AppPrefs.language(this);
        for(int i=0;i<ln.length;i++){final String value=lv[i];TextView b=Ui.action(this,(lang.equals(value)?"● ":"")+ln[i],lang.equals(value)?Ui.GREEN:Ui.BLUE,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=Ui.dp(this,6);langs.addView(b,lp);b.setOnClickListener(v->{AppPrefs.setLanguage(this,value);Toast.makeText(this,"Idioma guardado. La traducción completa de todas las pantallas se aplicará progresivamente; español es la interfaz completa actual.",Toast.LENGTH_LONG).show();showScreen("settings");});}
        appearance.addView(langs,Ui.lpMatchWrap(this,6));c.addView(appearance,Ui.lpMatchWrap(this,4));

        LinearLayout wall=Ui.card(this);wall.addView(Ui.title(this,"FONDO DE PANTALLA",13));
        wall.addView(Ui.text(this,"El fondo es decorativo y permanece siempre recto: nunca representa la inclinación. El arco y el valor numérico son la referencia física.",12,Ui.MUTED));
        String mode=AppPrefs.wallpaperMode(this);TextView none=Ui.action(this,("none".equals(mode)?"● ":"")+"SIN FONDO",Ui.BLUE,false);wall.addView(none,Ui.lpMatchWrap(this,8));none.setOnClickListener(v->{AppPrefs.setWallpaperMode(this,"none");showScreen("settings");});
        TextView custom=Ui.action(this,("custom".equals(mode)?"● ":"")+"MI FONDO · ELEGIR IMAGEN DEL TELÉFONO",Ui.BLUE,false);wall.addView(custom,Ui.lpMatchWrap(this,6));custom.setOnClickListener(v->chooseWallpaper());
        String[] names={"Costa nocturna","Montaña al atardecer","Bosque nocturno","Valle de luces","Paso alpino","Desierto nocturno","Túnel","Ciudad nocturna","Carretera rural","Montaña nevada"};
        wall.addView(Ui.text(this,"10 fondos cinematográficos integrados",11,Ui.MUTED),Ui.lpMatchWrap(this,10));
        for(int row=0;row<5;row++){LinearLayout rr=new LinearLayout(this);rr.setOrientation(LinearLayout.HORIZONTAL);for(int col=0;col<2;col++){int idx=row*2+col;boolean sel="builtin".equals(mode)&&AppPrefs.wallpaperIndex(this)==idx;View tile=wallpaperTile(idx,names[idx],sel);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(col==1)lp.leftMargin=Ui.dp(this,6);rr.addView(tile,lp);}wall.addView(rr,Ui.lpMatchWrap(this,6));}
        wall.addView(prefSlider("Visibilidad del fondo · más = más visible",AppPrefs.wallpaperOpacity(this),v->AppPrefs.setWallpaperOpacity(this,v)),Ui.lpMatchWrap(this,10));
        wall.addView(prefSlider("Oscurecimiento · más = más oscuro",AppPrefs.wallpaperDarken(this),v->AppPrefs.setWallpaperDarken(this,v)),Ui.lpMatchWrap(this,8));
        wall.addView(prefSlider("Brillo del arco",AppPrefs.arcBrightness(this),v->AppPrefs.setArcBrightness(this,v)),Ui.lpMatchWrap(this,8));
        wall.addView(Ui.text(this,"Estilo del arco",11,Ui.MUTED),Ui.lpMatchWrap(this,10));
        LinearLayout arcRow=new LinearLayout(this);arcRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] an={"MODERNO","MINIMAL","CLÁSICO"};String[] av={"modern","minimal","classic"};String as=AppPrefs.arcStyle(this);
        for(int i=0;i<an.length;i++){final String value=av[i];TextView b=Ui.action(this,(as.equals(value)?"● ":"")+an[i],as.equals(value)?Ui.RED:Ui.BLUE,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=Ui.dp(this,5);arcRow.addView(b,lp);b.setOnClickListener(v->{AppPrefs.setArcStyle(this,value);showScreen("settings");});}wall.addView(arcRow,Ui.lpMatchWrap(this,5));
        LinearLayout arcRow2=new LinearLayout(this);arcRow2.setOrientation(LinearLayout.HORIZONTAL);String[] an2={"DIFUMINADO","ARCADE","DEPORTIVO"};String[] av2={"blur","arcade","sport"};
        for(int i=0;i<an2.length;i++){final String value=av2[i];TextView b=Ui.action(this,(as.equals(value)?"● ":"")+an2[i],as.equals(value)?Ui.RED:Ui.BLUE,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=Ui.dp(this,5);arcRow2.addView(b,lp);b.setOnClickListener(v->{AppPrefs.setArcStyle(this,value);showScreen("settings");});}wall.addView(arcRow2,Ui.lpMatchWrap(this,6));
        if("custom".equals(mode)){
            wall.addView(Ui.text(this,"Ajustes de mi fondo",11,Ui.MUTED),Ui.lpMatchWrap(this,10));
            wall.addView(prefSlider("Desenfoque",AppPrefs.wallpaperBlur(this),v->AppPrefs.setWallpaperBlur(this,v)),Ui.lpMatchWrap(this,5));
            wall.addView(prefSlider160("Saturación",AppPrefs.wallpaperSaturation(this),v->AppPrefs.setWallpaperSaturation(this,v)),Ui.lpMatchWrap(this,5));
            wall.addView(prefSliderRange("Rotación",AppPrefs.wallpaperRotation(this),-180,180,"°",v->AppPrefs.setWallpaperRotation(this,v)),Ui.lpMatchWrap(this,5));
            wall.addView(prefToggle("Inclinar fondo con la moto",AppPrefs.wallpaperLean(this),v->AppPrefs.setWallpaperLean(this,v)),Ui.lpMatchWrap(this,5));
            if(AppPrefs.wallpaperLean(this)) wall.addView(prefSlider("Intensidad de inclinación",AppPrefs.wallpaperLeanStrength(this),v->AppPrefs.setWallpaperLeanStrength(this,v)),Ui.lpMatchWrap(this,5));
        }
        wall.addView(Ui.text(this,"Ajuste de imagen",11,Ui.MUTED),Ui.lpMatchWrap(this,8));LinearLayout fits=new LinearLayout(this);fits.setOrientation(LinearLayout.HORIZONTAL);String[] fn={"RELLENAR","ENCAJAR","CENTRAR"};String[] fv={"fill","fit","center"};String fit=AppPrefs.fit(this);for(int i=0;i<fn.length;i++){final String value=fv[i];TextView b=Ui.action(this,(fit.equals(value)?"● ":"")+fn[i],fit.equals(value)?Ui.GREEN:Ui.BLUE,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=Ui.dp(this,6);fits.addView(b,lp);b.setOnClickListener(v->{AppPrefs.setFit(this,value);showScreen("settings");});}wall.addView(fits,Ui.lpMatchWrap(this,6));
        boolean overlay=AppPrefs.showOverlay(this);TextView ov=Ui.action(this,(overlay?"● ":"○ ")+"DATOS EN SUPERPOSICIÓN",overlay?Ui.GREEN:Ui.BLUE,false);wall.addView(ov,Ui.lpMatchWrap(this,8));ov.setOnClickListener(v->{AppPrefs.setShowOverlay(this,!AppPrefs.showOverlay(this));showScreen("settings");});
        c.addView(wall,Ui.lpMatchWrap(this,8));

        LinearLayout avgReg=Ui.card(this);
        avgReg.addView(Ui.title(this,"INCLINACIÓN MEDIA ACUMULADA",13));
        avgReg.addView(Ui.title(this,String.format(Locale.getDefault(),"%.1f°",LeanAverageStore.average(this)),wide()?26:22));
        avgReg.addView(Ui.text(this,"Media absoluta de inclinación registrada durante conducción válida desde el último reset. No incluye manipulación del móvil ni paradas detectadas.",11,Ui.MUTED));
        TextView resetAvg=Ui.action(this,"RESET INCLINACIÓN MEDIA",Ui.RED,false);avgReg.addView(resetAvg,Ui.lpMatchWrap(this,8));
        resetAvg.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Reiniciar media").setMessage("Se pondrá a cero el registro acumulado de inclinación media. Las rutas guardadas no se borrarán.").setNegativeButton("Cancelar",null).setPositiveButton("Reiniciar",(d,w)->{LeanAverageStore.reset(this);showScreen("settings");}).show());
        c.addView(avgReg,Ui.lpMatchWrap(this,8));

        LinearLayout btu=Ui.card(this);btu.addView(Ui.title(this,"AUTO-RUTA · HONDA BTU",13));
        String btuTarget=AppPrefs.btuAddress(this).isEmpty()?"Sin dispositivo seleccionado":AppPrefs.btuName(this)+" · "+AppPrefs.btuAddress(this);
        btu.addView(Ui.text(this,"Objetivo: "+btuTarget+"\\nLa ruta usa un servicio foreground de ubicación, por lo que sigue registrando aunque LateralWheelie no esté en primer plano. Opcionalmente, una conexión Bluetooth del Honda BTU puede iniciar la ruta y su desconexión la detiene tras 15 s.",11,Ui.MUTED));
        TextView chooseBtu=Ui.action(this,"SELECCIONAR BTU EMPAREJADO",Ui.BLUE,false);btu.addView(chooseBtu,Ui.lpMatchWrap(this,8));chooseBtu.setOnClickListener(v->chooseBtuDevice());
        TextView autoBtu=Ui.action(this,(AppPrefs.autoRouteBtu(this)?"● ":"○ ")+"AUTO-RUTA BTU",AppPrefs.autoRouteBtu(this)?Ui.GREEN:Ui.BLUE,false);btu.addView(autoBtu,Ui.lpMatchWrap(this,6));
        autoBtu.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BLUETOOTH_CONNECT);return;}AppPrefs.setAutoRouteBtu(this,!AppPrefs.autoRouteBtu(this));showScreen("settings");});
        String btuErr=AppPrefs.btuLastError(this);if(!btuErr.isEmpty())btu.addView(Ui.text(this,btuErr,10,Ui.YELLOW),Ui.lpMatchWrap(this,6));
        c.addView(btu,Ui.lpMatchWrap(this,8));

        LinearLayout guard=Ui.card(this);
        guard.addView(Ui.title(this,"PROTECCIÓN DE MÁXIMOS",13));
        guard.addView(Ui.text(this,"Evita que coger o sacar el teléfono del soporte genere un falso máximo. Si detecta giro/movimiento brusco, bloquea máximos hasta que el móvil vuelva cerca de 0° y permanezca estable. En rutas también exige GPS y al menos 8 km/h.",12,Ui.MUTED));
        c.addView(guard,Ui.lpMatchWrap(this,8));

        LinearLayout direction=Ui.card(this);direction.addView(Ui.title(this,"SENTIDO IZQUIERDA / DERECHA",13));TextView inv=Ui.action(this,db.invertSides()?"INVERTIDO · TOCA PARA NORMAL":"NORMAL · TOCA PARA INVERTIR",Ui.BLUE,false);direction.addView(inv,Ui.lpMatchWrap(this,8));inv.setOnClickListener(v->{db.setInvertSides(!db.invertSides());sensors.refreshCalibration();Toast.makeText(this,"Sentido cambiado. Recalibra 0° para esta orientación.",Toast.LENGTH_LONG).show();showScreen("settings");});c.addView(direction,Ui.lpMatchWrap(this,8));
        LinearLayout orient=Ui.card(this);orient.addView(Ui.title(this,"ORIENTACIÓN DEL TELÉFONO",13));orient.addView(Ui.text(this,"Automática distingue vertical y horizontal. Si bloqueas la rotación de Android, puedes fijar la orientación física del soporte.",12,Ui.MUTED));int om=db.orientationOverride();String[] on={"AUTOMÁTICA","VERTICAL 0°","HORIZONTAL 90°","VERTICAL 180°","HORIZONTAL 270°"};int[] ovs={-1,0,1,2,3};for(int i=0;i<on.length;i++){final int val=ovs[i];TextView b=Ui.action(this,(om==val?"● ":"")+on[i],om==val?Ui.GREEN:Ui.BLUE,false);orient.addView(b,Ui.lpMatchWrap(this,6));b.setOnClickListener(v->{db.setOrientationOverride(val);sensors.refreshCalibration();Toast.makeText(this,"Orientación aplicada. Comprueba y calibra 0° en Garaje.",Toast.LENGTH_LONG).show();showScreen("settings");});}c.addView(orient,Ui.lpMatchWrap(this,8));
        if(Build.VERSION.SDK_INT>=33){LinearLayout notif=Ui.card(this);notif.addView(Ui.title(this,"NOTIFICACIONES",13));notif.addView(Ui.text(this,"Permiten mostrar correctamente el servicio de grabación de ruta.",12,Ui.MUTED));TextView ask=Ui.action(this,"PERMITIR NOTIFICACIONES",Ui.BLUE,false);notif.addView(ask,Ui.lpMatchWrap(this,8));ask.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS));c.addView(notif,Ui.lpMatchWrap(this,8));}
        LinearLayout data=Ui.card(this);data.addView(Ui.title(this,"DATOS",13));data.addView(Ui.text(this,"Puedes borrar rutas individualmente desde Historial. El borrado global requiere escribir BORRAR y conserva conductores y motos.",12,Ui.MUTED));TextView wipe=Ui.action(this,"BORRAR TODA LA TELEMETRÍA",Ui.RED,false);data.addView(wipe,Ui.lpMatchWrap(this,8));wipe.setOnClickListener(v->verifyDeleteAll());c.addView(data,Ui.lpMatchWrap(this,8));
        c.addView(Ui.text(this,"Temperatura exterior: sensor ambiente del teléfono o, si no existe, Open-Meteo según la posición GPS.",10,Ui.MUTED),Ui.lpMatchWrap(this,8));
        c.addView(Ui.text(this,"LateralWheelie 1.0.14 · Revision 1",11,Ui.MUTED),Ui.lpMatchWrap(this,8));
    }

    private View wallpaperTile(int index,String name,boolean selected){
        LinearLayout box=Ui.card(this);box.setPadding(Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,8));
        int[] ids={R.drawable.lw_wallpaper_01,R.drawable.lw_wallpaper_02,R.drawable.lw_wallpaper_03,R.drawable.lw_wallpaper_04,R.drawable.lw_wallpaper_05,R.drawable.lw_wallpaper_06,R.drawable.lw_wallpaper_07,R.drawable.lw_wallpaper_08,R.drawable.lw_wallpaper_09,R.drawable.lw_wallpaper_10};
        ImageView im=new ImageView(this);im.setImageResource(ids[index]);im.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(im,new LinearLayout.LayoutParams(-1,Ui.dp(this,110)));
        TextView label=Ui.text(this,(selected?"● ":"")+(index+1)+". "+name,11,selected?Ui.GREEN:Ui.TEXT);label.setGravity(Gravity.CENTER);label.setPadding(0,Ui.dp(this,6),0,0);box.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(this,34)));
        box.setClickable(true);box.setFocusable(true);box.setOnClickListener(v->{AppPrefs.setWallpaperIndex(this,index);showScreen("settings");});return box;
    }

    interface BoolSetter{void set(boolean v);}
    interface IntSetter{void set(int value);}
    private View prefSlider(String label,int value,IntSetter setter){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);TextView txt=Ui.text(this,label+" · "+value+"%",11,Ui.MUTED);SeekBar b=new SeekBar(this);b.setMax(100);b.setProgress(value);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean fromUser){txt.setText(label+" · "+v+"%");if(fromUser)setter.set(v);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});box.addView(txt);box.addView(b,new LinearLayout.LayoutParams(-1,-2));return box;}
    private View prefToggle(String label,boolean value,BoolSetter setter){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView txt=Ui.text(this,label,12,Ui.TEXT);row.addView(txt,new LinearLayout.LayoutParams(0,-2,1));android.widget.Switch sw=new android.widget.Switch(this);sw.setChecked(value);sw.setOnCheckedChangeListener((button,checked)->setter.set(checked));row.addView(sw,new LinearLayout.LayoutParams(-2,-2));return row;}
    private View prefSliderRange(String label,int value,int min,int max,String suffix,IntSetter setter){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);TextView txt=Ui.text(this,label+" · "+value+suffix,11,Ui.MUTED);SeekBar b=new SeekBar(this);b.setMax(max-min);b.setProgress(value-min);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar sb,int raw,boolean fromUser){int v=raw+min;txt.setText(label+" · "+v+suffix);if(fromUser)setter.set(v);}public void onStartTrackingTouch(SeekBar sb){}public void onStopTrackingTouch(SeekBar sb){}});box.addView(txt);box.addView(b,new LinearLayout.LayoutParams(-1,-2));return box;}
    private View prefSlider160(String label,int value,IntSetter setter){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);TextView txt=Ui.text(this,label+" · "+value+"%",11,Ui.MUTED);SeekBar b=new SeekBar(this);b.setMax(160);b.setProgress(value);b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean fromUser){txt.setText(label+" · "+v+"%");if(fromUser)setter.set(v);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});box.addView(txt);box.addView(b,new LinearLayout.LayoutParams(-1,-2));return box;}
    private void chooseWallpaper(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,REQ_WALLPAPER);}

    private void verifyDeleteAll(){EditText e=new EditText(this);e.setHint("Escribe BORRAR");new AlertDialog.Builder(this).setTitle("Borrado protegido").setMessage("Se eliminarán todas las rutas y muestras de telemetría. Los perfiles se conservan.").setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Verificar",(d,w)->{if("BORRAR".equalsIgnoreCase(e.getText().toString().trim())){if(RideState.active(this)){Toast.makeText(this,"Detén primero la ruta activa.",Toast.LENGTH_LONG).show();return;}db.deleteAllTelemetry();showScreen("settings");}else Toast.makeText(this,"Verificación incorrecta",Toast.LENGTH_LONG).show();}).show();}

    private void chooseBtuDevice(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BLUETOOTH_CONNECT);return;
        }
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null){Toast.makeText(this,"Este dispositivo no dispone de Bluetooth.",Toast.LENGTH_LONG).show();return;}
        if(!a.isEnabled()){startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return;}
        Set<BluetoothDevice> bonded;
        try{bonded=a.getBondedDevices();}catch(SecurityException e){Toast.makeText(this,"Falta permiso de dispositivos cercanos.",Toast.LENGTH_LONG).show();return;}
        if(bonded==null||bonded.isEmpty()){Toast.makeText(this,"No hay dispositivos Bluetooth emparejados.",Toast.LENGTH_LONG).show();return;}
        ArrayList<BluetoothDevice> list=new ArrayList<>(bonded);
        list.sort((x,y)->{
            String xn=safeBtName(x).toUpperCase(Locale.ROOT),yn=safeBtName(y).toUpperCase(Locale.ROOT);
            int xp=(xn.contains("HONDA")||xn.contains("BTU"))?0:1,yp=(yn.contains("HONDA")||yn.contains("BTU"))?0:1;
            if(xp!=yp)return Integer.compare(xp,yp);return xn.compareTo(yn);
        });
        String[] labels=new String[list.size()];
        for(int i=0;i<list.size();i++){BluetoothDevice d=list.get(i);labels[i]=safeBtName(d)+"\\n"+safeBtAddress(d);}
        new AlertDialog.Builder(this).setTitle("Selecciona Honda BTU").setItems(labels,(dialog,which)->{
            BluetoothDevice d=list.get(which);AppPrefs.setBtuDevice(this,safeBtAddress(d),safeBtName(d));AppPrefs.setAutoRouteBtu(this,true);AppPrefs.setBtuLastError(this,"");showScreen("settings");
        }).setNegativeButton("Cancelar",null).show();
    }

    private String safeBtName(BluetoothDevice d){try{String n=d.getName();return n==null||n.trim().isEmpty()?"Bluetooth":n;}catch(SecurityException e){return "Bluetooth";}}
    private String safeBtAddress(BluetoothDevice d){try{return d.getAddress();}catch(SecurityException e){return "";}}

    private void buildMore(){
        ScrollView s=scrollContainer();LinearLayout c=scrollContent(s);c.addView(screenTitle("Más","Gestión y análisis"));String[][] items={{"GARAJE","Motos, conductores y calibración","garage"},{"ESTADÍSTICAS","Máximos diarios, históricos y promedios","stats"},{"AJUSTES","Datos, permisos e integraciones opcionales","settings"}};for(String[] x:items){LinearLayout card=Ui.card(this);TextView h=Ui.title(this,x[0],18);TextView sub=Ui.text(this,x[1],13,Ui.MUTED);card.addView(h);card.addView(sub);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->showScreen(x[2]));c.addView(card,Ui.lpMatchWrap(this,8));}
    }

    private void resetUiRouteMaxFromState(){
        if(RideState.active(this)){
            uiMaxLeft=Math.min(0,RideState.maxLeft(this));uiMaxRight=Math.max(0,RideState.maxRight(this));uiMaxSessionId=RideState.sessionId(this);
        }else{
            // MÁX. RUTA is an always-on trip meter for the current app run, even when GPS recording is off.
            uiMaxLeft=0;uiMaxRight=0;uiMaxSessionId=-1;
        }
        uiMaxInitialized=true;
    }
    private void mergeUiRouteMax(double left,double right){if(!uiMaxInitialized)resetUiRouteMaxFromState();uiMaxLeft=Math.min(uiMaxLeft,Math.min(0,left));uiMaxRight=Math.max(uiMaxRight,Math.max(0,right));}
    private void syncUiRouteMaxFromState(){long sid=RideState.sessionId(this);if(!uiMaxInitialized||sid!=uiMaxSessionId){resetUiRouteMaxFromState();return;}mergeUiRouteMax(RideState.maxLeft(this),RideState.maxRight(this));}
    private void onLiveSensor(LiveSensorController.Snapshot s){
        snapshot=s;
        if(s.maxEligible) mergeUiRouteMax(s.lean,s.lean); // Ignore phone handling/removal from the mount.
        if(RideState.active(this)){
            // Persistent route maxima are owned by RideService, where GPS speed and
            // the mount guard are both available. Do not merge raw Activity samples here.
            // Merge, never replace, so recording state cannot erase maxima already seen on screen.
            mergeUiRouteMax(RideState.maxLeft(this),RideState.maxRight(this));
            uiMaxSessionId=RideState.sessionId(this);
        }
        if(gauge!=null){
            gauge.setRouteMax(uiMaxLeft,uiMaxRight);gauge.setRoll(s.lean);gauge.setLongitudinalG(s.longG);
            if(RideState.active(this))gauge.setLongitudinalPeaks(RideState.maxBrakeG(this),RideState.maxAccelG(this));
            float tc=!Float.isNaN(s.ambientC)?s.ambientC:RideState.temperatureC(this);
            gauge.setRideTelemetry(RideState.speed(this)*3.6f,RideState.altitude(this),RideState.distanceM(this),tc,RideState.startMs(this));
        }
        refreshTelemetryText();
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent!=null&&ACTION_START_RIDE.equals(intent.getAction())){selectedRouteSession=-1;pendingExternalStart=true;showScreen("route");if(resumed)handler.post(this::consumeExternalStart);}}
    private void consumeExternalStart(){if(!pendingExternalStart||!resumed)return;pendingExternalStart=false;requestRouteStart();}

    @SuppressWarnings("deprecation")
    private void registerTelemetryReceiver(IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rx, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rx, filter);
        }
    }
    @Override protected void onResume(){super.onResume();resumed=true;sensors.start();IntentFilter f=new IntentFilter(RideService.ACTION_TELEMETRY);registerTelemetryReceiver(f);handler.removeCallbacks(ticker);handler.post(ticker);handler.post(this::consumeExternalStart);if(hudMode&&windowRoot!=null)windowRoot.post(this::enterImmersiveMode);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(ticker);sensors.stop();try{unregisterReceiver(rx);}catch(Exception ignored){}super.onPause();}

    @Override public void onBackPressed(){if(hudMode){exitHud();return;}super.onBackPressed();}
}
