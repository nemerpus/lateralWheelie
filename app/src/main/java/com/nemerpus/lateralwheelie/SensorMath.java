package com.nemerpus.lateralwheelie;

/** Pure-Java sensor-axis helpers. Android Surface rotations are defined as 0,1,2,3. */
final class SensorMath {
    static final int ROTATION_0 = 0;
    static final int ROTATION_90 = 1;
    static final int ROTATION_180 = 2;
    static final int ROTATION_270 = 3;

    private SensorMath() {}

    static float[] screenXY(float x, float y, int rotation) {
        switch (rotation) {
            case ROTATION_90: return new float[]{y, -x};
            case ROTATION_180: return new float[]{-x, -y};
            case ROTATION_270: return new float[]{-y, x};
            default: return new float[]{x, y};
        }
    }


    static double longitudinalG(float lx,float ly,float lz,float gx,float gy,float gz) {
        final double G=9.80665;
        double gm=Math.sqrt(gx*gx+gy*gy+gz*gz);
        if(gm<1.0)return Math.max(-2.0,Math.min(2.0,-lz/G));

        double ux=gx/gm,uy=gy/gm,uz=gz/gm;
        // Phone screen faces the rider, so forward is approximately into the screen (-Z).
        // Remove the gravity component so a mount tilted up/down still measures along
        // the road plane rather than along the raw device Z axis.
        double dot=-uz;
        double fx=dot==0?0:(-dot*ux);
        double fy=dot==0?0:(-dot*uy);
        double fz=-1.0-dot*uz;
        // Equivalent to f0 - dot(f0,u)*u, with f0=(0,0,-1).
        fx=uz*ux;
        fy=uz*uy;
        fz=-1.0+uz*uz;
        double fm=Math.sqrt(fx*fx+fy*fy+fz*fz);
        double value;
        if(fm<0.15){
            value=-lz/G;
        }else{
            fx/=fm;fy/=fm;fz/=fm;
            value=(lx*fx+ly*fy+lz*fz)/G;
        }
        return Math.max(-2.0,Math.min(2.0,value));
    }

    static boolean isScreenFlat(float gx, float gy, float gz) {
        double planar = Math.hypot(gx, gy);
        return Math.abs(gz) >= 7.5 && planar <= 4.0;
    }

    static double applyDeadZone(double a) {
        return Math.abs(a) < 0.7 ? 0.0 : a;
    }

    static double rawLeanFromGravity(float gx, float gy, int rotation) {
        float[] p = screenXY(gx, gy, rotation);
        double a = Math.toDegrees(Math.atan2(p[0], p[1]));
        // Portrait needed the opposite sign on the physical device. Landscape
        // rotations already remap the axes in screenXY(), so do not invert them again.
        if (rotation == ROTATION_0 || rotation == ROTATION_180) a = -a;
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        if (a > 90) a = 180 - a;
        if (a < -90) a = -180 - a;
        return a;
    }

    static double normalizeLean(double a) {
        while (a > 90) a -= 180;
        while (a < -90) a += 180;
        return applyDeadZone(a);
    }
}
