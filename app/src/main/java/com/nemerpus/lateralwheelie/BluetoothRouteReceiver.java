package com.nemerpus.lateralwheelie;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class BluetoothRouteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null || !AppPrefs.autoRouteBtu(c))return;
        String action=intent.getAction();
        if(!BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))return;

        if(Build.VERSION.SDK_INT>=31 && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return;

        BluetoothDevice d;
        if(Build.VERSION.SDK_INT>=33)d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
        else d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(d==null)return;

        String address="",name="";
        try{address=d.getAddress();name=d.getName();}catch(SecurityException ignored){return;}
        String configured=AppPrefs.btuAddress(c);
        String configuredName=AppPrefs.btuName(c);
        boolean match=!configured.isEmpty() && configured.equalsIgnoreCase(address);
        if(!match && configured.isEmpty()){
            String n=name==null?"":name.toUpperCase(java.util.Locale.ROOT);
            match=n.contains("HONDA") || n.contains("BTU");
        }
        if(!match)return;

        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            if(RideState.active(c))return;
            Intent s=new Intent(c,RideService.class).setAction(RideService.ACTION_BTU_CONNECTED);
            try{
                AppPrefs.setBtuLastError(c,"");
                AppPrefs.setAutoRouteStarted(c,true);
                if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
            }catch(RuntimeException ex){
                AppPrefs.setAutoRouteStarted(c,false);
                AppPrefs.setBtuLastError(c,"Android bloqueó el inicio automático en segundo plano: "+ex.getClass().getSimpleName());
            }
        }else if(AppPrefs.autoRouteStarted(c)){
            try{c.startService(new Intent(c,RideService.class).setAction(RideService.ACTION_BTU_DISCONNECTED));}catch(RuntimeException ignored){}
        }
    }
}
