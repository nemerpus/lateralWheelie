package com.nemerpus.lateralwheelie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Surface;
import android.view.WindowManager;

public class RideService extends Service implements SensorEventListener, LocationListener {
    public static final String ACTION_STOP = "com.nemerpus.lateralwheelie.STOP_RIDE";
    public static final String ACTION_RESET_MAX = "com.nemerpus.lateralwheelie.RESET_ROUTE_MAX";
    public static final String ACTION_TELEMETRY = "com.nemerpus.lateralwheelie.TELEMETRY";
    public static final String ACTION_BTU_CONNECTED = "com.nemerpus.lateralwheelie.BTU_CONNECTED";
    public static final String ACTION_BTU_DISCONNECTED = "com.nemerpus.lateralwheelie.BTU_DISCONNECTED";
    private static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL = "ride";

    private SensorManager sm;
    private Sensor gravity, accel, linear, ambientTemperature;
    private LocationManager lm;
    private TelemetryDb db;
    private long sid=-1,lastSampleMs=0,lastLeanNs=0,startMs=0;
    private int samples=0,calibrationRotation=-1;
    private double calibration,rawLean,lean,lastLean,maxL,maxR,maxRR,maxLatG,maxLongG,maxAccelG,maxBrakeG,rr,latG,longG;
    private boolean longitudinalReady;
    private double smoothLongitudinalG;
    private long maxLTs,maxRTs;
    private double maxLLat,maxLLon,maxRLat,maxRLon,lat,lon,alt;
    private float speed,temperatureC=Float.NaN,distanceM;
    private Location lastDistanceLocation;
    private boolean gpsFix,recording,sensorsRegistered;
    private boolean mountArmed;
    private long neutralStableSinceMs,handlingUntilMs;
    private double routeLeanSum;
    private long routeLeanCount,lastLeanAverageMs;
    private final Handler serviceHandler=new Handler(Looper.getMainLooper());
    private final Runnable btuStopRunnable=()->{if(recording&&AppPrefs.autoRouteStarted(this))stopSelf();};
    private boolean curveEventActive;
    private double curveEventPeak;
    private long curveEventPeakTs;
    private double curveEventLat,curveEventLon,curveEventSpeed,curveEventAlt;
    private final float[] gravityEstimate=new float[3],lastLinear=new float[3];

    @Override public void onCreate(){
        super.onCreate();
        db=new TelemetryDb(this);
        sm=(SensorManager)getSystemService(SENSOR_SERVICE);
        gravity=sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        accel=sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        linear=sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        ambientTemperature=sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if(ambientTemperature==null)temperatureC=WeatherTemperature.cached(this);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,getString(R.string.ride_channel_name),NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("lateralWheelie registra GPS e inclinación mientras otra app puede estar en primer plano.");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(ACTION_BTU_CONNECTED.equals(action))serviceHandler.removeCallbacks(btuStopRunnable);
        if(ACTION_BTU_DISCONNECTED.equals(action)){
            if(recording&&AppPrefs.autoRouteStarted(this)){serviceHandler.removeCallbacks(btuStopRunnable);serviceHandler.postDelayed(btuStopRunnable,15000);}
            return recording?START_STICKY:START_NOT_STICKY;
        }
        if(intent!=null && ACTION_STOP.equals(action)){ AppPrefs.setAutoRouteStarted(this,false);stopSelf(); return START_NOT_STICKY; }
        if(intent!=null && ACTION_RESET_MAX.equals(action)){
            if(recording) resetRouteMax();
            return recording?START_STICKY:START_NOT_STICKY;
        }
        if(recording) return START_STICKY;

        if(!hasLocationPermission() || !isLocationEnabled()){
            sendStateError(!hasLocationPermission()?"Falta permiso de ubicación":"Activa la ubicación del sistema");
            stopSelf();
            return START_NOT_STICKY;
        }

        try{
            Notification n=buildNotification();
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIFICATION_ID,n);
        }catch(RuntimeException ex){
            sendStateError("No se pudo iniciar la grabación: "+ex.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }

        long existing=RideState.sessionId(this);
        if(RideState.active(this) && existing>0){
            sid=existing;startMs=RideState.startMs(this);maxL=RideState.maxLeft(this);maxR=RideState.maxRight(this);maxLTs=RideState.maxLeftTs(this);maxRTs=RideState.maxRightTs(this);samples=RideState.samples(this);distanceM=RideState.distanceM(this);temperatureC=RideState.temperatureC(this);alt=RideState.altitude(this);maxAccelG=RideState.maxAccelG(this);maxBrakeG=RideState.maxBrakeG(this);routeLeanSum=RideState.leanSum(this);routeLeanCount=RideState.leanCount(this);
        }else{
            sid=db.startSession();
            if(sid<=0){sendStateError("No se pudo crear la sesión");stopSelf();return START_NOT_STICKY;}
            RideState.begin(this,sid);startMs=RideState.startMs(this);
        }
        recording=true;
        registerSensors();
        requestLocations();
        broadcast();
        return START_STICKY;
    }

    private Notification buildNotification(){
        Intent open=new Intent(this,MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,RideService.class).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_lean).setContentTitle("lateralWheelie · ruta activa")
                .setContentText("Inclinación y GPS se están registrando")
                .setOngoing(true).setContentIntent(content).setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause,"Detener",stop);
        return b.build();
    }

    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean isLocationEnabled(){
        try{
            if(lm==null)return false;
            if(Build.VERSION.SDK_INT>=28)return lm.isLocationEnabled();
            return Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE)!=Settings.Secure.LOCATION_MODE_OFF;
        }catch(Exception e){return false;}
    }

    private void registerSensors(){
        if(sensorsRegistered)return;
        sensorsRegistered=true;
        if(gravity!=null)sm.registerListener(this,gravity,SensorManager.SENSOR_DELAY_GAME);
        if(linear!=null)sm.registerListener(this,linear,SensorManager.SENSOR_DELAY_GAME);
        if(accel!=null && (gravity==null || linear==null))sm.registerListener(this,accel,SensorManager.SENSOR_DELAY_GAME);
        if(ambientTemperature!=null)sm.registerListener(this,ambientTemperature,SensorManager.SENSOR_DELAY_NORMAL);
    }

    @SuppressLint("MissingPermission")
    private void requestLocations(){
        if(!hasLocationPermission())return;
        try{
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2000,0,this);
        }catch(RuntimeException ignored){}
    }

    private int rotation(){int display=((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();return db.effectiveRotation(display);}
    private void ensureCalibration(int rotation){if(rotation!=calibrationRotation){calibration=db.calibration(rotation);calibrationRotation=rotation;}}

    @Override public void onSensorChanged(SensorEvent e){
        int rot=rotation();ensureCalibration(rot);
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            final float alpha=.88f;
            for(int i=0;i<3;i++){gravityEstimate[i]=alpha*gravityEstimate[i]+(1-alpha)*e.values[i];if(linear==null)lastLinear[i]=e.values[i]-gravityEstimate[i];}
            if(gravity==null)updateLean(gravityEstimate[0],gravityEstimate[1],gravityEstimate[2],e.timestamp,rot);
        }else if(e.sensor.getType()==Sensor.TYPE_GRAVITY){
            gravityEstimate[0]=e.values[0];gravityEstimate[1]=e.values[1];gravityEstimate[2]=e.values[2];updateLean(e.values[0],e.values[1],e.values[2],e.timestamp,rot);
        }else if(e.sensor.getType()==Sensor.TYPE_LINEAR_ACCELERATION){
            lastLinear[0]=e.values[0];lastLinear[1]=e.values[1];lastLinear[2]=e.values[2];
        }else if(e.sensor.getType()==Sensor.TYPE_AMBIENT_TEMPERATURE){
            temperatureC=e.values[0];
            RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,gpsFix);
            broadcast();
        }
    }

    private void updateLean(float gx,float gy,float gz,long ts,int rot){
        boolean flat=SensorMath.isScreenFlat(gx,gy,gz);
        rawLean=flat?0:SensorMath.rawLeanFromGravity(gx,gy,rot);if(db.invertSides())rawLean=-rawLean;
        double corrected=flat?0:SensorMath.normalizeLean(rawLean-calibration);
        lean=lastLeanNs==0?corrected:(lean*.80+corrected*.20);
        if(lastLeanNs>0&&ts>lastLeanNs){double dt=(ts-lastLeanNs)/1_000_000_000.0;rr=(lean-lastLean)/Math.max(.001,dt);rr=Math.max(-720,Math.min(720,rr));}
        lastLeanNs=ts;lastLean=lean;
        float[] xy=SensorMath.screenXY(lastLinear[0],lastLinear[1],rot);latG=xy[0]/SensorManager.GRAVITY_EARTH;
        double measuredLong=SensorMath.longitudinalG(lastLinear[0],lastLinear[1],lastLinear[2],
                gravityEstimate[0],gravityEstimate[1],gravityEstimate[2]);
        if(!longitudinalReady){smoothLongitudinalG=measuredLong;longitudinalReady=true;}
        else smoothLongitudinalG=smoothLongitudinalG*.74+measuredLong*.26;
        if(Math.abs(smoothLongitudinalG)<0.025)smoothLongitudinalG=0;
        longG=Math.max(-1.5,Math.min(1.5,smoothLongitudinalG));
        long monotonicNow=android.os.SystemClock.elapsedRealtime();
        double linearMag=Math.sqrt(lastLinear[0]*lastLinear[0]+lastLinear[1]*lastLinear[1]+lastLinear[2]*lastLinear[2])
                / SensorManager.GRAVITY_EARTH;
        boolean handling=flat || Math.abs(rr)>150.0 || linearMag>0.90;
        if(handling){
            handlingUntilMs=monotonicNow+1800;
            mountArmed=false;
            neutralStableSinceMs=0;
            curveEventActive=false;curveEventPeak=0;
        }

        if(!mountArmed && monotonicNow>=handlingUntilMs && !flat && Math.abs(lean)<=12.0 && Math.abs(rr)<35.0 && linearMag<0.25){
            if(neutralStableSinceMs==0)neutralStableSinceMs=monotonicNow;
            if(monotonicNow-neutralStableSinceMs>=900)mountArmed=true;
        }else if(!mountArmed && (Math.abs(lean)>12.0 || Math.abs(rr)>=35.0 || linearMag>=0.25)){
            neutralStableSinceMs=0;
        }

        // A stored route max should represent riding, not handling the phone at a stop.
        // 2.22 m/s = ~8 km/h.  The live angle remains visible at all times.
        boolean routeEligible=mountArmed && monotonicNow>=handlingUntilMs && !flat && gpsFix && speed>=2.22f;

        if(routeEligible){
            maxRR=Math.max(maxRR,Math.abs(rr));
            maxLatG=Math.max(maxLatG,Math.abs(latG));
            maxLongG=Math.max(maxLongG,Math.abs(longG));
            if(longG>maxAccelG)maxAccelG=longG;
            if(longG<maxBrakeG)maxBrakeG=longG;
            RideState.updateGPeaks(this,maxAccelG,maxBrakeG);
        }

        long now=System.currentTimeMillis();
        double bounded=Math.max(-70.0,Math.min(70.0,lean));
        if(routeEligible && now-lastLeanAverageMs>=1000){
            routeLeanSum+=Math.abs(bounded);routeLeanCount++;lastLeanAverageMs=now;
            RideState.updateLeanAverage(this,routeLeanSum,routeLeanCount);
            LeanAverageStore.add(this,Math.abs(bounded));
        }
        if(routeEligible){
            if(bounded<maxL){maxL=bounded;maxLTs=now;maxLLat=lat;maxLLon=lon;}
            if(bounded>maxR){maxR=bounded;maxRTs=now;maxRLat=lat;maxRLon=lon;}

            if(!curveEventActive && Math.abs(bounded)>35.0){
                curveEventActive=true;curveEventPeak=bounded;curveEventPeakTs=now;
                curveEventLat=lat;curveEventLon=lon;curveEventSpeed=speed;curveEventAlt=alt;
            }else if(curveEventActive && Math.signum(bounded)==Math.signum(curveEventPeak) && Math.abs(bounded)>Math.abs(curveEventPeak)){
                curveEventPeak=bounded;curveEventPeakTs=now;curveEventLat=lat;curveEventLon=lon;curveEventSpeed=speed;curveEventAlt=alt;
            }
            if(curveEventActive && (Math.abs(bounded)<30.0 || Math.signum(bounded)!=Math.signum(curveEventPeak))){
                if(sid>0 && Math.abs(curveEventPeak)>35.0)db.leanEvent(sid,curveEventPeakTs,curveEventPeak,curveEventLat,curveEventLon,curveEventSpeed,curveEventAlt);
                curveEventActive=false;curveEventPeak=0;
            }
        }else if(curveEventActive){
            curveEventActive=false;curveEventPeak=0;
        }

        // Keep the route timeline, but suppress an obviously handled-phone lean from
        // painting a false red 70° segment on the route map.
        double storedLean=routeEligible?bounded:0.0;
        if(sid>0&&now-lastSampleMs>=200){db.sample(sid,now,lat,lon,speed,alt,storedLean,rr,latG,longG);lastSampleMs=now;samples++;}
        RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,gpsFix);
        broadcast();
    }

    @Override public void onLocationChanged(Location l){
        if(lastDistanceLocation!=null && l.getAccuracy()<=50f){float d=lastDistanceLocation.distanceTo(l);if(d>=0&&d<500)distanceM+=d;}
        lastDistanceLocation=new Location(l);
        lat=l.getLatitude();lon=l.getLongitude();speed=l.hasSpeed()?l.getSpeed():0;alt=l.hasAltitude()?l.getAltitude():0;gpsFix=true;
        if(ambientTemperature==null){
            WeatherTemperature.request(this,lat,lon,value ->
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        temperatureC=value;
                        RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,true);
                        broadcast();
                    }));
        }
        RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,true);broadcast();
    }
    // Required for Android 8-10: these became default methods only on Android 11 (API 30).
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { if(LocationManager.GPS_PROVIDER.equals(provider)) { gpsFix=false; RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,false); broadcast(); } }
    @Override @SuppressWarnings("deprecation") public void onStatusChanged(String provider,int status,Bundle extras) { }


    private void resetRouteMax(){
        maxL=0;maxR=0;maxLTs=0;maxRTs=0;
        maxLLat=0;maxLLon=0;maxRLat=0;maxRLon=0;maxAccelG=0;maxBrakeG=0;maxLongG=0;
        RideState.resetMax(this);RideState.resetGPeaks(this);
        RideState.update(this,maxL,maxR,maxLTs,maxRTs,samples,speed,(float)alt,distanceM,temperatureC,gpsFix);
        broadcast();
    }

    private void broadcast(){
        Intent i=new Intent(ACTION_TELEMETRY).setPackage(getPackageName());
        i.putExtra("roll",lean).putExtra("rr",rr).putExtra("latg",latG).putExtra("longg",longG).putExtra("speed",speed).putExtra("alt",alt)
                .putExtra("maxL",maxL).putExtra("maxR",maxR).putExtra("gpsFix",gpsFix).putExtra("samples",samples).putExtra("sid",sid)
                .putExtra("distanceM",distanceM).putExtra("tempC",temperatureC).putExtra("maxAccelG",maxAccelG).putExtra("maxBrakeG",maxBrakeG).putExtra("mountArmed",mountArmed);
        sendBroadcast(i);
    }

    private void sendStateError(String message){sendBroadcast(new Intent(ACTION_TELEMETRY).setPackage(getPackageName()).putExtra("error",message));}

    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public IBinder onBind(Intent intent){return null;}

    @Override public void onDestroy(){
        serviceHandler.removeCallbacks(btuStopRunnable);
        if(AppPrefs.autoRouteStarted(this))AppPrefs.setAutoRouteStarted(this,false);
        if(sensorsRegistered)sm.unregisterListener(this);
        try{lm.removeUpdates(this);}catch(Exception ignored){}
        if(recording&&sid>0){double finalL=Math.min(maxL,RideState.maxLeft(this));double finalR=Math.max(maxR,RideState.maxRight(this));db.end(sid,finalL,finalR,RideState.maxLeftTs(this),RideState.maxRightTs(this),maxLLat,maxLLon,maxRLat,maxRLon,maxRR,maxLatG,maxLongG,Math.max(maxAccelG,RideState.maxAccelG(this)),Math.min(maxBrakeG,RideState.maxBrakeG(this)),routeLeanCount>0?routeLeanSum/routeLeanCount:0,distanceM);}
        if(recording)RideState.finish(this);
        recording=false;
        try{stopForeground(true);}catch(Exception ignored){}
        super.onDestroy();
    }
}
