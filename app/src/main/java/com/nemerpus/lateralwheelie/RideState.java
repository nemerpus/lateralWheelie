package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.content.SharedPreferences;

final class RideState {
    private static final String P = "ride_state";
    private RideState() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    static boolean active(Context c) { return p(c).getBoolean("active", false); }
    static long sessionId(Context c) { return p(c).getLong("sid", -1); }
    static long startMs(Context c) { return p(c).getLong("start", 0); }
    static double maxLeft(Context c) { return Double.longBitsToDouble(p(c).getLong("maxL", Double.doubleToRawLongBits(0))); }
    static double maxRight(Context c) { return Double.longBitsToDouble(p(c).getLong("maxR", Double.doubleToRawLongBits(0))); }
    static long maxLeftTs(Context c) { return p(c).getLong("maxLTs", 0); }
    static long maxRightTs(Context c) { return p(c).getLong("maxRTs", 0); }
    static int samples(Context c) { return p(c).getInt("samples", 0); }
    static float speed(Context c) { return p(c).getFloat("speed", 0); }
    static float altitude(Context c) { return p(c).getFloat("alt", 0); }
    static float distanceM(Context c) { return p(c).getFloat("distanceM", 0); }
    static float temperatureC(Context c) { return p(c).getFloat("tempC", Float.NaN); }
    static boolean gpsFix(Context c) { return p(c).getBoolean("gpsFix", false); }
    static double leanSum(Context c){return Double.longBitsToDouble(p(c).getLong("leanSum",Double.doubleToRawLongBits(0)));}
    static long leanCount(Context c){return p(c).getLong("leanCount",0);}
    static double maxAccelG(Context c) { return Double.longBitsToDouble(p(c).getLong("maxAccelG",Double.doubleToRawLongBits(0))); }
    static double maxBrakeG(Context c) { return Double.longBitsToDouble(p(c).getLong("maxBrakeG",Double.doubleToRawLongBits(0))); }

    static void begin(Context c, long sid) {
        p(c).edit().putBoolean("active", true).putLong("sid", sid)
                .putLong("start", System.currentTimeMillis())
                .putLong("maxL", Double.doubleToRawLongBits(0))
                .putLong("maxR", Double.doubleToRawLongBits(0))
                .putLong("maxLTs", 0).putLong("maxRTs", 0)
                .putInt("samples", 0).putFloat("speed", 0).putFloat("alt", 0).putFloat("distanceM", 0).putFloat("tempC", Float.NaN).putBoolean("gpsFix", false)
                .putLong("maxAccelG",Double.doubleToRawLongBits(0)).putLong("maxBrakeG",Double.doubleToRawLongBits(0))
                .putLong("leanSum",Double.doubleToRawLongBits(0)).putLong("leanCount",0).apply();
    }

    static void restoreActive(Context c, long sid, long start, double maxL, double maxR) {
        p(c).edit().putBoolean("active", true).putLong("sid", sid).putLong("start", start)
                .putLong("maxL", Double.doubleToRawLongBits(maxL))
                .putLong("maxR", Double.doubleToRawLongBits(maxR)).apply();
    }

    static synchronized void update(Context c, double maxL, double maxR, long maxLTs, long maxRTs,
                       int samples, float speed, float altitude, float distanceM, float temperatureC, boolean gpsFix) {
        SharedPreferences sp=p(c);
        double storedL=Double.longBitsToDouble(sp.getLong("maxL",Double.doubleToRawLongBits(0)));
        double storedR=Double.longBitsToDouble(sp.getLong("maxR",Double.doubleToRawLongBits(0)));
        long storedLTs=sp.getLong("maxLTs",0),storedRTs=sp.getLong("maxRTs",0);
        boolean takeL=maxL<storedL, takeR=maxR>storedR;
        double mergedL=Math.min(storedL,maxL), mergedR=Math.max(storedR,maxR);
        sp.edit().putLong("maxL", Double.doubleToRawLongBits(mergedL))
                .putLong("maxR", Double.doubleToRawLongBits(mergedR))
                .putLong("maxLTs", takeL?maxLTs:storedLTs).putLong("maxRTs", takeR?maxRTs:storedRTs)
                .putInt("samples", samples).putFloat("speed", speed).putFloat("alt", altitude).putFloat("distanceM", distanceM).putFloat("tempC", temperatureC).putBoolean("gpsFix", gpsFix).apply();
    }

    static synchronized void mergeMax(Context c,double lean,long ts){
        if(!active(c))return;
        SharedPreferences sp=p(c);
        double l=maxLeft(c),r=maxRight(c); long lts=maxLeftTs(c),rts=maxRightTs(c);
        if(lean<l){l=lean;lts=ts;}
        if(lean>r){r=lean;rts=ts;}
        sp.edit().putLong("maxL",Double.doubleToRawLongBits(l)).putLong("maxR",Double.doubleToRawLongBits(r))
                .putLong("maxLTs",lts).putLong("maxRTs",rts).apply();
    }

    static synchronized void updateLeanAverage(Context c,double sum,long count){
        p(c).edit().putLong("leanSum",Double.doubleToRawLongBits(sum)).putLong("leanCount",count).apply();
    }

    static synchronized void updateGPeaks(Context c,double accel,double brake){
        SharedPreferences sp=p(c);
        double a=Math.max(maxAccelG(c),Math.max(0,accel));
        double b=Math.min(maxBrakeG(c),Math.min(0,brake));
        sp.edit().putLong("maxAccelG",Double.doubleToRawLongBits(a))
                .putLong("maxBrakeG",Double.doubleToRawLongBits(b)).apply();
    }

    static synchronized void resetGPeaks(Context c){
        p(c).edit().putLong("maxAccelG",Double.doubleToRawLongBits(0))
                .putLong("maxBrakeG",Double.doubleToRawLongBits(0)).apply();
    }

    static synchronized void resetMax(Context c){
        p(c).edit().putLong("maxL",Double.doubleToRawLongBits(0)).putLong("maxR",Double.doubleToRawLongBits(0))
                .putLong("maxLTs",0).putLong("maxRTs",0).apply();
    }

    static void finish(Context c) {
        p(c).edit().putBoolean("active", false).putLong("sid", -1).putFloat("speed",0).putBoolean("gpsFix",false).apply();
    }
}
