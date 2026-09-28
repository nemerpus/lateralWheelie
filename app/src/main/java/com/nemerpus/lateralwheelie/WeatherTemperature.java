package com.nemerpus.lateralwheelie;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class WeatherTemperature {
    interface Callback { void onTemperature(float celsius); }

    private static final String PREFS="weather_temperature";
    private static final String K_TEMP="temp_c";
    private static final String K_TS="ts";
    private static final long REFRESH_MS=15L*60L*1000L;
    private static volatile boolean requestRunning;

    private WeatherTemperature(){}

    static float cached(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return p.contains(K_TEMP)?p.getFloat(K_TEMP,Float.NaN):Float.NaN;
    }

    static void request(Context c,double lat,double lon,Callback cb){
        final Context app=c.getApplicationContext();
        final SharedPreferences p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        float cache=cached(app);
        long ts=p.getLong(K_TS,0);
        if(!Float.isNaN(cache) && now-ts<REFRESH_MS){
            if(cb!=null)cb.onTemperature(cache);
            return;
        }
        if(requestRunning)return;
        requestRunning=true;
        new Thread(() -> {
            HttpURLConnection con=null;
            try{
                String endpoint=String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m&temperature_unit=celsius",
                        lat,lon);
                con=(HttpURLConnection)new URL(endpoint).openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestMethod("GET");
                con.setRequestProperty("Accept","application/json");
                con.setRequestProperty("User-Agent","LateralWheelie/1.0.8");
                if(con.getResponseCode()!=200)return;
                StringBuilder body=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(con.getInputStream()))){
                    String line;while((line=r.readLine())!=null)body.append(line);
                }
                JSONObject current=new JSONObject(body.toString()).optJSONObject("current");
                if(current==null || !current.has("temperature_2m"))return;
                double value=current.optDouble("temperature_2m",Double.NaN);
                if(Double.isNaN(value) || value<-80 || value>65)return;
                float temp=(float)value;
                p.edit().putFloat(K_TEMP,temp).putLong(K_TS,System.currentTimeMillis()).apply();
                if(cb!=null)cb.onTemperature(temp);
            }catch(Exception ignored){
                // Offline is valid: hardware sensor/cached value remain available.
            }finally{
                if(con!=null)con.disconnect();
                requestRunning=false;
            }
        },"lw-weather").start();
    }
}
