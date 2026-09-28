package com.nemerpus.lateralwheelie;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;

public class TelemetryDb extends SQLiteOpenHelper {
    static final int VERSION = 6;

    public static class Driver { public long id; public String name; }
    public static class Bike { public long id; public String name, model; }
    public static class Session {
        public long id,start,end,leftTs,rightTs;
        public String driver,bike;
        public double left,right,rr,latG,longG,accelG,brakeG,avgLean,distanceM,leftLat,leftLon,rightLat,rightLon;
    }
    public static class Sample {
        public long ts; public double lat,lon,speed,alt,roll,rr,latG,longG;
    }
    public static class Stats {
        public int sessions;
        public double maxLeft,maxRight,avgPeak,avgLeft,avgRight,absolute;
        public double todayLeft,todayRight;
        public long todayLeftTs,todayRightTs,historicalLeftTs,historicalRightTs;
    }

    public static class RouteComparison {
        public long currentId,previousId,previousStart;
        public long currentElapsedMs,previousElapsedMs,timeDeltaMs;
        public double currentAvgLean,previousAvgLean,avgLeanDelta;
        public double currentLeft,currentRight,previousLeft,previousRight;
        public double currentAccel,currentBrake,previousAccel,previousBrake;
        public double matchMeters;
        public boolean live;
    }

    public TelemetryDb(Context c) { super(c, "lateralwheelie.db", null, VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE drivers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,created_ms INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE bikes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,model TEXT DEFAULT '',calibration_0 REAL DEFAULT 0,calibration_90 REAL DEFAULT 0,calibration_180 REAL DEFAULT 0,calibration_270 REAL DEFAULT 0,created_ms INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        d.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,start_ms INTEGER NOT NULL,end_ms INTEGER,driver_id INTEGER NOT NULL,bike_id INTEGER NOT NULL,max_left REAL DEFAULT 0,max_right REAL DEFAULT 0,max_left_ts INTEGER DEFAULT 0,max_right_ts INTEGER DEFAULT 0,max_left_lat REAL DEFAULT 0,max_left_lon REAL DEFAULT 0,max_right_lat REAL DEFAULT 0,max_right_lon REAL DEFAULT 0,max_roll_rate REAL DEFAULT 0,max_lat_g REAL DEFAULT 0,max_long_g REAL DEFAULT 0,max_accel_g REAL DEFAULT 0,max_brake_g REAL DEFAULT 0,avg_lean REAL DEFAULT 0,distance_m REAL DEFAULT 0,FOREIGN KEY(driver_id) REFERENCES drivers(id),FOREIGN KEY(bike_id) REFERENCES bikes(id))");
        d.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL,ts INTEGER NOT NULL,lat REAL,lon REAL,speed REAL,alt REAL,roll REAL,roll_rate REAL,lat_g REAL,long_g REAL,FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        d.execSQL("CREATE INDEX idx_samples_session_ts ON samples(session_id,ts)");
        d.execSQL("CREATE TABLE lean_events(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL,ts INTEGER NOT NULL,side INTEGER NOT NULL,peak REAL NOT NULL,lat REAL,lon REAL,speed REAL,alt REAL,FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        d.execSQL("CREATE INDEX idx_lean_events_session_ts ON lean_events(session_id,ts)");
        long now=System.currentTimeMillis();
        d.execSQL("INSERT INTO drivers(name,created_ms) VALUES('Conductor principal',"+now+")");
        d.execSQL("INSERT INTO bikes(name,model,created_ms) VALUES('Mi moto','',"+now+")");
        d.execSQL("INSERT INTO settings(k,v) VALUES('driver','1'),('bike','1'),('invert_sides','0'),('orientation_override','-1')");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            trySql(d,"CREATE TABLE IF NOT EXISTS settings(k TEXT PRIMARY KEY,v TEXT)");
            trySql(d,"INSERT OR IGNORE INTO settings(k,v) VALUES('driver','1'),('bike','1')");
            trySql(d,"CREATE INDEX IF NOT EXISTS idx_samples_session_ts ON samples(session_id,ts)");
        }
        if (oldVersion < 3) {
            trySql(d,"ALTER TABLE bikes ADD COLUMN calibration_0 REAL DEFAULT 0");
            trySql(d,"ALTER TABLE bikes ADD COLUMN calibration_90 REAL DEFAULT 0");
            trySql(d,"ALTER TABLE bikes ADD COLUMN calibration_180 REAL DEFAULT 0");
            trySql(d,"ALTER TABLE bikes ADD COLUMN calibration_270 REAL DEFAULT 0");
            trySql(d,"UPDATE bikes SET calibration_0=calibration WHERE calibration IS NOT NULL");
            trySql(d,"ALTER TABLE drivers ADD COLUMN created_ms INTEGER DEFAULT 0");
            trySql(d,"ALTER TABLE bikes ADD COLUMN created_ms INTEGER DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_left_ts INTEGER DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_right_ts INTEGER DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_left_lat REAL DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_left_lon REAL DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_right_lat REAL DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_right_lon REAL DEFAULT 0");
            trySql(d,"INSERT OR IGNORE INTO settings(k,v) VALUES('invert_sides','0')");
            trySql(d,"INSERT OR IGNORE INTO settings(k,v) VALUES('orientation_override','-1')");
        }
        if (oldVersion < 4) {
            trySql(d,"CREATE TABLE IF NOT EXISTS lean_events(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL,ts INTEGER NOT NULL,side INTEGER NOT NULL,peak REAL NOT NULL,lat REAL,lon REAL,speed REAL,alt REAL,FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
            trySql(d,"CREATE INDEX IF NOT EXISTS idx_lean_events_session_ts ON lean_events(session_id,ts)");
        }
        if (oldVersion < 5) {
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_accel_g REAL DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN max_brake_g REAL DEFAULT 0");
        }
        if (oldVersion < 6) {
            trySql(d,"ALTER TABLE sessions ADD COLUMN avg_lean REAL DEFAULT 0");
            trySql(d,"ALTER TABLE sessions ADD COLUMN distance_m REAL DEFAULT 0");
        }
    }

    public void leanEvent(long sessionId,long ts,double peak,double lat,double lon,double speed,double alt){
        if(sessionId<=0 || Math.abs(peak)<=35.0)return;
        ContentValues x=new ContentValues();x.put("session_id",sessionId);x.put("ts",ts);x.put("side",peak<0?-1:1);x.put("peak",peak);
        x.put("lat",lat);x.put("lon",lon);x.put("speed",speed);x.put("alt",alt);
        getWritableDatabase().insert("lean_events",null,x);
    }

    private void trySql(SQLiteDatabase d,String sql){ try { d.execSQL(sql); } catch (Exception ignored) {} }

    private String setting(String k,String def){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{k})){return c.moveToFirst()?c.getString(0):def;}catch(Exception e){return def;}
    }
    private long settingLong(String k,long def){try{return Long.parseLong(setting(k,String.valueOf(def)));}catch(Exception e){return def;}}
    private void setSetting(String k,String v){ContentValues x=new ContentValues();x.put("k",k);x.put("v",v);getWritableDatabase().insertWithOnConflict("settings",null,x,SQLiteDatabase.CONFLICT_REPLACE);}

    public long activeDriver(){return settingLong("driver",1);} public long activeBike(){return settingLong("bike",1);}
    public void setActive(long d,long b){setSetting("driver",String.valueOf(d));setSetting("bike",String.valueOf(b));}
    public boolean invertSides(){return "1".equals(setting("invert_sides","0"));}
    public void setInvertSides(boolean v){setSetting("invert_sides",v?"1":"0");}
    public int orientationOverride(){return (int)settingLong("orientation_override",-1);}
    public void setOrientationOverride(int v){if(v < -1 || v > 3)v=-1;setSetting("orientation_override",String.valueOf(v));}
    public int effectiveRotation(int displayRotation){int v=orientationOverride();return v>=0?v:displayRotation;}

    public String activeLabel(){
        String dn="Conductor",bn="Moto";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT d.name,b.name FROM drivers d,bikes b WHERE d.id=? AND b.id=?",new String[]{String.valueOf(activeDriver()),String.valueOf(activeBike())})){
            if(c.moveToFirst()){dn=c.getString(0);bn=c.getString(1);}
        }
        return dn+" · "+bn;
    }

    private String calColumn(int rotation){
        if(rotation==SensorMath.ROTATION_90)return "calibration_90";
        if(rotation==SensorMath.ROTATION_180)return "calibration_180";
        if(rotation==SensorMath.ROTATION_270)return "calibration_270";
        return "calibration_0";
    }
    public double calibration(int rotation){
        String col=calColumn(rotation);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+col+" FROM bikes WHERE id=?",new String[]{String.valueOf(activeBike())})){return c.moveToFirst()?c.getDouble(0):0;}catch(Exception e){return 0;}
    }
    public void setCalibration(int rotation,double rawLean){ContentValues x=new ContentValues();x.put(calColumn(rotation),rawLean);getWritableDatabase().update("bikes",x,"id=?",new String[]{String.valueOf(activeBike())});}

    public long addDriver(String n){ContentValues v=new ContentValues();v.put("name",n.trim());v.put("created_ms",System.currentTimeMillis());return getWritableDatabase().insert("drivers",null,v);}
    public long addBike(String n,String m){ContentValues v=new ContentValues();v.put("name",n.trim());v.put("model",m.trim());v.put("created_ms",System.currentTimeMillis());return getWritableDatabase().insert("bikes",null,v);}
    public boolean updateDriver(long id,String n){
        String value=n==null?"":n.trim();if(value.isEmpty())return false;
        ContentValues v=new ContentValues();v.put("name",value);
        try{return getWritableDatabase().update("drivers",v,"id=?",new String[]{String.valueOf(id)})>0;}catch(Exception e){return false;}
    }
    public boolean updateBike(long id,String n,String m){
        String name=n==null?"":n.trim();if(name.isEmpty())return false;
        ContentValues v=new ContentValues();v.put("name",name);v.put("model",m==null?"":m.trim());
        try{return getWritableDatabase().update("bikes",v,"id=?",new String[]{String.valueOf(id)})>0;}catch(Exception e){return false;}
    }

    public ArrayList<Driver> drivers(){ArrayList<Driver>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM drivers ORDER BY name",null)){while(c.moveToNext()){Driver x=new Driver();x.id=c.getLong(0);x.name=c.getString(1);a.add(x);}}return a;}
    public ArrayList<Bike> bikes(){ArrayList<Bike>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,model FROM bikes ORDER BY name",null)){while(c.moveToNext()){Bike x=new Bike();x.id=c.getLong(0);x.name=c.getString(1);x.model=c.getString(2);a.add(x);}}return a;}

    public boolean deleteDriver(long id){
        if(drivers().size()<=1 || id==activeDriver()) return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sessions WHERE driver_id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst()&&c.getInt(0)>0)return false;}
        return getWritableDatabase().delete("drivers","id=?",new String[]{String.valueOf(id)})>0;
    }
    public boolean deleteBike(long id){
        if(bikes().size()<=1 || id==activeBike()) return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sessions WHERE bike_id=?",new String[]{String.valueOf(id)})){if(c.moveToFirst()&&c.getInt(0)>0)return false;}
        return getWritableDatabase().delete("bikes","id=?",new String[]{String.valueOf(id)})>0;
    }

    public long startSession(){ContentValues v=new ContentValues();v.put("start_ms",System.currentTimeMillis());v.put("driver_id",activeDriver());v.put("bike_id",activeBike());return getWritableDatabase().insert("sessions",null,v);}

    public void sample(long sid,long ts,double lat,double lon,double speed,double alt,double roll,double rr,double lg,double log){
        ContentValues v=new ContentValues();v.put("session_id",sid);v.put("ts",ts);v.put("lat",lat);v.put("lon",lon);v.put("speed",speed);v.put("alt",alt);v.put("roll",roll);v.put("roll_rate",rr);v.put("lat_g",lg);v.put("long_g",log);getWritableDatabase().insert("samples",null,v);
    }

    public void end(long sid,double ml,double mr,long mlTs,long mrTs,double mlLat,double mlLon,double mrLat,double mrLon,double rr,double lg,double log,double accelG,double brakeG,double avgLean,double distanceM){
        ContentValues v=new ContentValues();v.put("end_ms",System.currentTimeMillis());v.put("max_left",ml);v.put("max_right",mr);v.put("max_left_ts",mlTs);v.put("max_right_ts",mrTs);v.put("max_left_lat",mlLat);v.put("max_left_lon",mlLon);v.put("max_right_lat",mrLat);v.put("max_right_lon",mrLon);v.put("max_roll_rate",rr);v.put("max_lat_g",lg);v.put("max_long_g",log);v.put("max_accel_g",accelG);v.put("max_brake_g",brakeG);v.put("avg_lean",avgLean);v.put("distance_m",distanceM);getWritableDatabase().update("sessions",v,"id=?",new String[]{String.valueOf(sid)});
    }

    public ArrayList<Session> sessions(){
        ArrayList<Session>a=new ArrayList<>();
        String q="SELECT s.id,s.start_ms,COALESCE(s.end_ms,0),d.name,b.name,s.max_left,s.max_right,s.max_roll_rate,s.max_lat_g,s.max_long_g,COALESCE(s.max_left_ts,0),COALESCE(s.max_right_ts,0),COALESCE(s.max_left_lat,0),COALESCE(s.max_left_lon,0),COALESCE(s.max_right_lat,0),COALESCE(s.max_right_lon,0),COALESCE(s.max_accel_g,0),COALESCE(s.max_brake_g,0),COALESCE(s.avg_lean,0),COALESCE(s.distance_m,0) FROM sessions s JOIN drivers d ON d.id=s.driver_id JOIN bikes b ON b.id=s.bike_id ORDER BY s.start_ms DESC";
        try(Cursor c=getReadableDatabase().rawQuery(q,null)){while(c.moveToNext()){Session s=new Session();s.id=c.getLong(0);s.start=c.getLong(1);s.end=c.getLong(2);s.driver=c.getString(3);s.bike=c.getString(4);s.left=c.getDouble(5);s.right=c.getDouble(6);s.rr=c.getDouble(7);s.latG=c.getDouble(8);s.longG=c.getDouble(9);s.leftTs=c.getLong(10);s.rightTs=c.getLong(11);s.leftLat=c.getDouble(12);s.leftLon=c.getDouble(13);s.rightLat=c.getDouble(14);s.rightLon=c.getDouble(15);s.accelG=c.getDouble(16);s.brakeG=c.getDouble(17);s.avgLean=c.getDouble(18);s.distanceM=c.getDouble(19);a.add(s);}}
        return a;
    }

    public Session session(long id){for(Session s:sessions())if(s.id==id)return s;return null;}
    public long latestSessionId(){ArrayList<Session>s=sessions();return s.isEmpty()?-1:s.get(0).id;}

    public ArrayList<Sample> samples(long sid,int maxPoints){
        ArrayList<Sample>a=new ArrayList<>();int count=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM samples WHERE session_id=?",new String[]{String.valueOf(sid)})){if(c.moveToFirst())count=c.getInt(0);}
        int step=Math.max(1,(int)Math.ceil(count/(double)Math.max(1,maxPoints)));int i=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT ts,lat,lon,speed,alt,roll,roll_rate,lat_g,long_g FROM samples WHERE session_id=? ORDER BY ts",new String[]{String.valueOf(sid)})){
            while(c.moveToNext()){boolean take=i%step==0 || i==count-1;if(take){Sample s=new Sample();s.ts=c.getLong(0);s.lat=c.getDouble(1);s.lon=c.getDouble(2);s.speed=c.getDouble(3);s.alt=c.getDouble(4);s.roll=c.getDouble(5);s.rr=c.getDouble(6);s.latG=c.getDouble(7);s.longG=c.getDouble(8);a.add(s);}i++;}
        }
        return a;
    }

    public RouteComparison findRouteComparison(long currentId,boolean live){
        Session cur=session(currentId);
        if(cur==null)return null;
        ArrayList<Sample> current=validGps(samples(currentId,90));
        if(current.size()<6)return null;
        double currentLength=pathMeters(current);
        if(currentLength<250)return null;

        RouteComparison best=null;
        double bestScore=Double.MAX_VALUE;
        for(Session prev:sessions()){
            if(prev.id==currentId || prev.end<=0 || prev.start>=cur.start)continue;
            if(!safeEq(cur.driver,prev.driver) || !safeEq(cur.bike,prev.bike))continue;
            ArrayList<Sample> old=validGps(samples(prev.id,110));
            if(old.size()<6)continue;

            double startGap=meters(current.get(0).lat,current.get(0).lon,old.get(0).lat,old.get(0).lon);
            if(startGap>300)continue;

            double oldLength=pathMeters(old);
            if(!live && currentLength>500 && oldLength>500){
                double ratio=currentLength/oldLength;
                if(ratio<0.78 || ratio>1.28)continue;
                Sample ce=current.get(current.size()-1),oe=old.get(old.size()-1);
                if(meters(ce.lat,ce.lon,oe.lat,oe.lon)>350)continue;
            }

            double score=meanNearestMeters(current,old);
            if(!live)score=(score+meanNearestMeters(old,current))*.5;
            if(score>(live?110:125) || score>=bestScore)continue;

            Sample currentLast=current.get(current.size()-1);
            int oldProgress=nearestIndex(old,currentLast.lat,currentLast.lon);
            if(live && oldProgress<Math.max(2,old.size()/20))continue;

            RouteComparison r=new RouteComparison();
            r.currentId=currentId;r.previousId=prev.id;r.previousStart=prev.start;r.live=live;r.matchMeters=score;
            long currentEnd=live?currentLast.ts:(cur.end>0?cur.end:currentLast.ts);
            long previousEnd=live?old.get(oldProgress).ts:prev.end;
            r.currentElapsedMs=Math.max(0,currentEnd-cur.start);
            r.previousElapsedMs=Math.max(0,previousEnd-prev.start);
            r.timeDeltaMs=r.currentElapsedMs-r.previousElapsedMs;

            r.currentAvgLean=cur.avgLean>0?cur.avgLean:averageAbsLean(current);
            r.previousAvgLean=prev.avgLean>0?prev.avgLean:averageAbsLean(old);
            r.avgLeanDelta=r.currentAvgLean-r.previousAvgLean;
            r.currentLeft=cur.left;r.currentRight=cur.right;r.previousLeft=prev.left;r.previousRight=prev.right;
            r.currentAccel=cur.accelG;r.currentBrake=cur.brakeG;r.previousAccel=prev.accelG;r.previousBrake=prev.brakeG;
            best=r;bestScore=score;
        }
        return best;
    }

    private ArrayList<Sample> validGps(ArrayList<Sample> in){
        ArrayList<Sample> out=new ArrayList<>();for(Sample s:in)if(Math.abs(s.lat)>0.000001||Math.abs(s.lon)>0.000001)out.add(s);return out;
    }
    private boolean safeEq(String a,String b){return a==null?b==null:a.equals(b);}
    private double averageAbsLean(ArrayList<Sample> x){if(x.isEmpty())return 0;double sum=0;for(Sample s:x)sum+=Math.abs(s.roll);return sum/x.size();}
    private double pathMeters(ArrayList<Sample> x){double d=0;for(int i=1;i<x.size();i++)d+=meters(x.get(i-1).lat,x.get(i-1).lon,x.get(i).lat,x.get(i).lon);return d;}
    private int nearestIndex(ArrayList<Sample> x,double lat,double lon){int best=0;double d=Double.MAX_VALUE;for(int i=0;i<x.size();i++){double q=meters(lat,lon,x.get(i).lat,x.get(i).lon);if(q<d){d=q;best=i;}}return best;}
    private double meanNearestMeters(ArrayList<Sample> a,ArrayList<Sample> b){
        if(a.isEmpty()||b.isEmpty())return Double.MAX_VALUE;double total=0;
        for(Sample x:a){double best=Double.MAX_VALUE;for(Sample y:b){double d=meters(x.lat,x.lon,y.lat,y.lon);if(d<best)best=d;}total+=best;}
        return total/a.size();
    }
    private double meters(double lat1,double lon1,double lat2,double lon2){
        double R=6371000.0,p1=Math.toRadians(lat1),p2=Math.toRadians(lat2),dp=Math.toRadians(lat2-lat1),dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    public Stats stats(){
        Stats s=new Stats();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),MIN(max_left),MAX(max_right),AVG(MAX(ABS(max_left),ABS(max_right))),AVG(ABS(max_left)),AVG(ABS(max_right)),MAX(MAX(ABS(max_left),ABS(max_right))) FROM sessions WHERE end_ms IS NOT NULL",null)){
            if(c.moveToFirst()){s.sessions=c.getInt(0);s.maxLeft=n(c,1);s.maxRight=n(c,2);s.avgPeak=n(c,3);s.avgLeft=n(c,4);s.avgRight=n(c,5);s.absolute=n(c,6);}
        }
        Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long day=cal.getTimeInMillis();
        long dayEnd=day+24L*60L*60L*1000L;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT max_left,max_left_ts FROM sessions WHERE end_ms IS NOT NULL AND max_left_ts>=? AND max_left_ts<? ORDER BY max_left ASC LIMIT 1",new String[]{String.valueOf(day),String.valueOf(dayEnd)})){if(c.moveToFirst()){s.todayLeft=n(c,0);s.todayLeftTs=c.getLong(1);}}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT max_right,max_right_ts FROM sessions WHERE end_ms IS NOT NULL AND max_right_ts>=? AND max_right_ts<? ORDER BY max_right DESC LIMIT 1",new String[]{String.valueOf(day),String.valueOf(dayEnd)})){if(c.moveToFirst()){s.todayRight=n(c,0);s.todayRightTs=c.getLong(1);}}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT max_left_ts FROM sessions WHERE end_ms IS NOT NULL ORDER BY max_left ASC LIMIT 1",null)){if(c.moveToFirst())s.historicalLeftTs=c.getLong(0);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT max_right_ts FROM sessions WHERE end_ms IS NOT NULL ORDER BY max_right DESC LIMIT 1",null)){if(c.moveToFirst())s.historicalRightTs=c.getLong(0);}
        return s;
    }
    private double n(Cursor c,int i){return c.isNull(i)?0:c.getDouble(i);}

    public void deleteSession(long id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("samples","session_id=?",new String[]{String.valueOf(id)});d.delete("sessions","id=?",new String[]{String.valueOf(id)});d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public void deleteAllTelemetry(){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("samples",null,null);d.delete("sessions",null,null);d.setTransactionSuccessful();}finally{d.endTransaction();}}
}
