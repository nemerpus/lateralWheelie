package com.nemerpus.lateralwheelie;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stable route viewer.
 *
 * The WebView and MapLibre map are created once. GPS updates modify two existing
 * GeoJSON sources through evaluateJavascript instead of rebuilding/reloading the
 * complete HTML document. This preserves zoom/pitch/bearing and avoids flashing.
 *
 * Satellite remains the default base layer. Expensive raster DEM terrain was
 * deliberately removed because it was the least stable part of the previous
 * WebView renderer. The map keeps a pitched perspective and provides a street
 * fallback that can also activate automatically when satellite tiles fail.
 */
public class RouteMapView extends WebView {
    private boolean pageReady;
    private String latestPayload="[]";
    private String lastPushed="";

    public RouteMapView(Context c){super(c);init();}

    @SuppressLint("SetJavaScriptEnabled")
    private void init(){
        setBackgroundColor(Color.rgb(4,8,12));
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        WebSettings s=getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        if(Build.VERSION.SDK_INT>=26)s.setSafeBrowsingEnabled(true);

        setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                pageReady=true;
                pushLatest(true);
            }
        });
        setContentDescription("Mapa de ruta GPS en satélite con perspectiva y respaldo callejero");
        loadUrl("file:///android_asset/route_map.html");
    }

    public void setSamples(List<TelemetryDb.Sample> samples){
        List<TelemetryDb.Sample> valid=new ArrayList<>();
        if(samples!=null){
            for(TelemetryDb.Sample x:samples){
                if(Math.abs(x.lat)>0.000001 || Math.abs(x.lon)>0.000001)valid.add(x);
            }
        }

        StringBuilder pts=new StringBuilder("[");
        for(int i=0;i<valid.size();i++){
            TelemetryDb.Sample x=valid.get(i);
            if(i>0)pts.append(',');
            pts.append('[')
                    .append(String.format(Locale.US,"%.7f",x.lon)).append(',')
                    .append(String.format(Locale.US,"%.7f",x.lat)).append(',')
                    .append(String.format(Locale.US,"%.2f",x.roll))
                    .append(']');
        }
        pts.append(']');
        latestPayload=pts.toString();
        pushLatest(false);
    }

    private void pushLatest(boolean force){
        if(!pageReady)return;
        if(!force && latestPayload.equals(lastPushed))return;
        final String payload=latestPayload;
        // Keep a JS-side copy as well: if MapLibre has not emitted "load" yet,
        // route_map.html consumes lwNativeData as soon as the map becomes ready.
        evaluateJavascript(
                "window.lwNativeData="+payload+";"+
                "if(window.lwSetPoints){window.lwSetPoints(window.lwNativeData);}",
                null);
        lastPushed=payload;
    }
}
