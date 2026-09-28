package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

final class LiveSensorController implements SensorEventListener {
    interface RotationProvider { int rotation(); }
    interface Listener { void onSnapshot(Snapshot s); }

    static final class Snapshot {
        double rawLean;
        double lean;
        double rollRate;
        double latG;
        double longG;
        int rotation;
        boolean gravitySensor;
        boolean linearSensor;
        float ambientC=Float.NaN;
        long timestampNs;
        boolean flatSurface;
        boolean maxEligible;
        boolean mountArmed;
    }

    private final SensorManager sm;
    private final TelemetryDb db;
    private final RotationProvider rotationProvider;
    private final Listener listener;
    private final Sensor gravity;
    private final Sensor accel;
    private final Sensor linear;
    private final Sensor ambientTemperature;
    private boolean running;
    private boolean firstLean = true;
    private double smoothLean;
    private long lastLeanNs;
    private final float[] gravityEstimate = new float[3];
    private final float[] lastLinear = new float[3];
    private double calibration;
    private int calibrationRotation = -1;
    private float ambientC=Float.NaN;
    private boolean longitudinalReady;
    private double smoothLongitudinalG;
    private boolean mountArmed;
    private long neutralStableSinceMs;
    private long handlingUntilMs;

    LiveSensorController(Context c, TelemetryDb db, RotationProvider rp, Listener listener) {
        this.db = db;
        this.rotationProvider = rp;
        this.listener = listener;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        ambientTemperature = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
    }

    void start() {
        if (running) return;
        running = true;
        if (gravity != null) sm.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
        if (linear != null) sm.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME);
        if (accel != null && (gravity == null || linear == null)) {
            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
        if (ambientTemperature != null) sm.registerListener(this, ambientTemperature, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() {
        if (!running) return;
        running = false;
        sm.unregisterListener(this);
    }

    void refreshCalibration() {
        calibrationRotation = -1;
        firstLean = true;
    }

    private void ensureCalibration(int rotation) {
        if (rotation != calibrationRotation) {
            calibration = db.calibration(rotation);
            calibrationRotation = rotation;
            firstLean = true;
        }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        int rot = db.effectiveRotation(rotationProvider.rotation());
        ensureCalibration(rot);
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            final float alpha = 0.88f;
            for (int i = 0; i < 3; i++) {
                gravityEstimate[i] = alpha * gravityEstimate[i] + (1f - alpha) * e.values[i];
                if (linear == null) lastLinear[i] = e.values[i] - gravityEstimate[i];
            }
            if (gravity == null) emitFromGravity(gravityEstimate[0], gravityEstimate[1], gravityEstimate[2], e.timestamp, rot);
        } else if (e.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravityEstimate[0] = e.values[0]; gravityEstimate[1] = e.values[1]; gravityEstimate[2] = e.values[2];
            emitFromGravity(e.values[0], e.values[1], e.values[2], e.timestamp, rot);
        } else if (e.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            lastLinear[0] = e.values[0]; lastLinear[1] = e.values[1]; lastLinear[2] = e.values[2];
        } else if (e.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            ambientC=e.values[0];
        }
    }

    private void emitFromGravity(float gx, float gy, float gz, long ts, int rot) {
        boolean flat = SensorMath.isScreenFlat(gx, gy, gz);
        double raw = flat ? 0.0 : SensorMath.rawLeanFromGravity(gx, gy, rot);
        if (db.invertSides()) raw = -raw;
        double corrected = flat ? 0.0 : SensorMath.normalizeLean(raw - calibration);
        if (firstLean) {
            smoothLean = corrected;
            firstLean = false;
        } else {
            smoothLean = smoothLean * 0.80 + corrected * 0.20;
        }

        double rr = 0;
        if (lastLeanNs > 0 && ts > lastLeanNs) {
            double dt = (ts - lastLeanNs) / 1_000_000_000.0;
            rr = (smoothLean - lastEmittedLean) / Math.max(dt, 0.001);
            rr = Math.max(-720, Math.min(720, rr));
        }
        lastLeanNs = ts;
        lastEmittedLean = smoothLean;

        float[] xy = SensorMath.screenXY(lastLinear[0], lastLinear[1], rot);
        Snapshot s = new Snapshot();
        s.rawLean = raw;
        s.lean = smoothLean;
        s.rollRate = rr;
        s.latG = xy[0] / SensorManager.GRAVITY_EARTH;
        double measuredLong=SensorMath.longitudinalG(lastLinear[0],lastLinear[1],lastLinear[2],
                gravityEstimate[0],gravityEstimate[1],gravityEstimate[2]);
        if(!longitudinalReady){smoothLongitudinalG=measuredLong;longitudinalReady=true;}
        else smoothLongitudinalG=smoothLongitudinalG*.74+measuredLong*.26;
        if(Math.abs(smoothLongitudinalG)<0.025)smoothLongitudinalG=0;
        s.longG = Math.max(-1.5,Math.min(1.5,smoothLongitudinalG));
        s.rotation = rot;
        s.gravitySensor = gravity != null;
        s.linearSensor = linear != null;
        s.ambientC = ambientC;
        s.timestampNs = ts;
        s.flatSurface = flat;

        long now=android.os.SystemClock.elapsedRealtime();
        double linearMag=Math.sqrt(lastLinear[0]*lastLinear[0]+lastLinear[1]*lastLinear[1]+lastLinear[2]*lastLinear[2])
                / SensorManager.GRAVITY_EARTH;
        boolean handling=flat || Math.abs(rr)>150.0 || linearMag>0.90;
        if(handling){
            handlingUntilMs=now+1800;
            mountArmed=false;
            neutralStableSinceMs=0;
        }

        // Once disturbed, maxima remain locked until the phone is back close to the
        // calibrated neutral position and stable for almost one second.
        if(!mountArmed && now>=handlingUntilMs && !flat && Math.abs(smoothLean)<=12.0 && Math.abs(rr)<35.0 && linearMag<0.25){
            if(neutralStableSinceMs==0)neutralStableSinceMs=now;
            if(now-neutralStableSinceMs>=900)mountArmed=true;
        }else if(!mountArmed && (Math.abs(smoothLean)>12.0 || Math.abs(rr)>=35.0 || linearMag>=0.25)){
            neutralStableSinceMs=0;
        }

        s.mountArmed=mountArmed;
        s.maxEligible=mountArmed && now>=handlingUntilMs && !flat;
        listener.onSnapshot(s);
    }

    private double lastEmittedLean;

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
