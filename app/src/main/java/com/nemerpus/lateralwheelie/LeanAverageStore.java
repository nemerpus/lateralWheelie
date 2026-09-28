package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.content.SharedPreferences;

final class LeanAverageStore {
    private static final String P="lean_average";
    private static final String SUM="sum";
    private static final String COUNT="count";
    private static final String RESET_TS="reset_ts";
    private LeanAverageStore(){}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(P,Context.MODE_PRIVATE);}

    static synchronized void add(Context c,double absoluteLean){
        double v=Math.max(0,Math.min(70,Math.abs(absoluteLean)));
        SharedPreferences s=p(c);
        double sum=Double.longBitsToDouble(s.getLong(SUM,Double.doubleToRawLongBits(0)));
        long count=s.getLong(COUNT,0);
        s.edit().putLong(SUM,Double.doubleToRawLongBits(sum+v)).putLong(COUNT,count+1).apply();
    }

    static double average(Context c){
        SharedPreferences s=p(c);
        long count=s.getLong(COUNT,0);
        if(count<=0)return 0;
        double sum=Double.longBitsToDouble(s.getLong(SUM,Double.doubleToRawLongBits(0)));
        return sum/count;
    }

    static long count(Context c){return p(c).getLong(COUNT,0);}
    static long resetTs(Context c){return p(c).getLong(RESET_TS,0);}

    static synchronized void reset(Context c){
        p(c).edit().putLong(SUM,Double.doubleToRawLongBits(0)).putLong(COUNT,0)
                .putLong(RESET_TS,System.currentTimeMillis()).apply();
    }
}
