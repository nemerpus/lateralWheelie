package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;

final class AppPrefs {
    private static final String P="app_prefs";
    private AppPrefs(){}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(P,Context.MODE_PRIVATE);}

    static String theme(Context c){return p(c).getString("theme","system");}
    static void setTheme(Context c,String v){p(c).edit().putString("theme",v).apply();}
    static boolean lightTheme(Context c){
        String t=theme(c);
        if("light".equals(t)) return true;
        if("dark".equals(t)) return false;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    static String language(Context c){return p(c).getString("language","es");}
    static void setLanguage(Context c,String v){p(c).edit().putString("language",v).apply();}

    static String wallpaperMode(Context c){return p(c).getString("wallpaper_mode","builtin");}
    static void setWallpaperMode(Context c,String v){p(c).edit().putString("wallpaper_mode",v).apply();}
    static int wallpaperIndex(Context c){return Math.max(0,Math.min(9,p(c).getInt("wallpaper_index",0)));}
    static void setWallpaperIndex(Context c,int v){p(c).edit().putInt("wallpaper_index",Math.max(0,Math.min(9,v))).putString("wallpaper_mode","builtin").apply();}
    static String wallpaperUri(Context c){return p(c).getString("wallpaper_uri","");}
    static void setWallpaperUri(Context c,Uri uri){p(c).edit().putString("wallpaper_uri",uri==null?"":uri.toString()).putString("wallpaper_mode","custom").apply();}

    static int wallpaperOpacity(Context c){return p(c).getInt("wallpaper_opacity",62);}
    static void setWallpaperOpacity(Context c,int v){p(c).edit().putInt("wallpaper_opacity",clamp(v)).apply();}
    static int wallpaperDarken(Context c){return p(c).getInt("wallpaper_darken",32);}
    static void setWallpaperDarken(Context c,int v){p(c).edit().putInt("wallpaper_darken",clamp(v)).apply();}
    static int arcBrightness(Context c){return p(c).getInt("arc_brightness",100);}
    static void setArcBrightness(Context c,int v){p(c).edit().putInt("arc_brightness",clamp(v)).apply();}
    static boolean showOverlay(Context c){return p(c).getBoolean("show_overlay",true);}
    static void setShowOverlay(Context c,boolean v){p(c).edit().putBoolean("show_overlay",v).apply();}
    static String fit(Context c){return p(c).getString("wallpaper_fit","fit");}
    static void setFit(Context c,String v){p(c).edit().putString("wallpaper_fit",v).apply();}


    static String arcStyle(Context c){return p(c).getString("arc_style","modern");}
    static void setArcStyle(Context c,String v){p(c).edit().putString("arc_style",v).apply();}
    static int wallpaperBlur(Context c){return p(c).getInt("wallpaper_blur",0);}
    static void setWallpaperBlur(Context c,int v){p(c).edit().putInt("wallpaper_blur",clamp(v)).apply();}
    static int wallpaperSaturation(Context c){return p(c).getInt("wallpaper_saturation",100);}
    static void setWallpaperSaturation(Context c,int v){p(c).edit().putInt("wallpaper_saturation",Math.max(0,Math.min(160,v))).apply();}
    static int wallpaperRotation(Context c){return p(c).getInt("wallpaper_rotation",0);}
    static void setWallpaperRotation(Context c,int v){p(c).edit().putInt("wallpaper_rotation",Math.max(-180,Math.min(180,v))).apply();}
    static boolean wallpaperLean(Context c){return p(c).getBoolean("wallpaper_lean",false);}
    static void setWallpaperLean(Context c,boolean v){p(c).edit().putBoolean("wallpaper_lean",v).apply();}
    static int wallpaperLeanStrength(Context c){return p(c).getInt("wallpaper_lean_strength",35);}
    static void setWallpaperLeanStrength(Context c,int v){p(c).edit().putInt("wallpaper_lean_strength",clamp(v)).apply();}
    static int wallpaperOffsetY(Context c){return p(c).getInt("wallpaper_offset_y",50);}
    static void setWallpaperOffsetY(Context c,int v){p(c).edit().putInt("wallpaper_offset_y",clamp(v)).apply();}

    static boolean autoRouteBtu(Context c){return p(c).getBoolean("auto_route_btu",false);}
    static void setAutoRouteBtu(Context c,boolean v){p(c).edit().putBoolean("auto_route_btu",v).apply();}
    static String btuAddress(Context c){return p(c).getString("btu_address","");}
    static String btuName(Context c){return p(c).getString("btu_name","Honda BTU");}
    static void setBtuDevice(Context c,String address,String name){
        p(c).edit().putString("btu_address",address==null?"":address)
                .putString("btu_name",name==null?"Honda BTU":name).apply();
    }
    static boolean autoRouteStarted(Context c){return p(c).getBoolean("auto_route_started",false);}
    static void setAutoRouteStarted(Context c,boolean v){p(c).edit().putBoolean("auto_route_started",v).apply();}
    static String btuLastError(Context c){return p(c).getString("btu_last_error","");}
    static void setBtuLastError(Context c,String v){p(c).edit().putString("btu_last_error",v==null?"":v).apply();}

    private static int clamp(int v){return Math.max(0,Math.min(100,v));}
}
