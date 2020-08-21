package com.swillers.quadtaichi;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.swillers.quadtaichi.MainActivity.isChargerConnected;
import static com.swillers.quadtaichi.R.mipmap.ic_launcher;

public class TimerService extends IntentService implements SensorEventListener {
    public TimerService() {
        super("TimerService");
    }

    private class LocalBinder extends Binder {

    }
    private final IBinder mBinder = new LocalBinder();

    // TaiChi vars
    public static int taiChiInterval; // Interval between pressure releases in seconds
    public static int nagInterval;  // Interval between notifications in minutes
    public static int tiltThreshold;  // Degrees of tilt needed to achieve Tai Chi mode

    // Logging
    final String logTag = "TimerService";

    // Sensor vars
    public float pitch;
    private static final int FROM_RADS_TO_DEGS = -57;
    public int SENSOR_DELAY = 500;
    Sensor accelerometer;
    Sensor magnetometer;
    private long fullTiltTime = 0;
    SensorManager mSensorManager;

    // Timing stuff
    public static long taiChiTime;

    // Timer stuff
    public static Timer timer;
    public long mCurrentPageMinute = -1;  // The minute that we last paged

    // Notification stuff
    public static boolean hasNotified = false;
    public NotificationManager notificationManager;
    public PendingIntent pendingNotificationIntent;

    public static boolean pauseWhenCharging;
    public boolean inTaiChiMode = false;


    @Override
    public void onCreate() {
        super.onCreate();

        // Load preferences
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        taiChiInterval = sharedPref.getInt("taiChiInterval",45);
        nagInterval = sharedPref.getInt("nagInterval",5);
        tiltThreshold = sharedPref.getInt("tiltThreshold",30);
        pauseWhenCharging = sharedPref.getBoolean("pauseWhenCharging",true);

        taiChiTime = System.currentTimeMillis() + (1000 * 60 * taiChiInterval);

        // Setup Notifications
        Intent notificationIntent = new Intent(this, TimerService.class);
        pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {  // Oreo needs notification channels
            NotificationChannel mChannel = new NotificationChannel("qtc_channel",
                    "Quad Tai Chi Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            mChannel.setDescription("Notifies users when Tai Chi is due");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
        }

        // Setup Sensors
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
            mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
        }
        // Setup timer run checks every 1/4 of a second
        timer = new Timer();
        timer.scheduleAtFixedRate(new mainTask(), 0, 250);

    }

    private class mainTask extends TimerTask {
        public void run() {
            long mNow = System.currentTimeMillis();
            long mSecDiff = (mNow - taiChiTime) / 1000;  // number of seconds before/after taiChiTime
            long mMinDiff = mSecDiff / 60;  // number of minutes before/after taiChiTime
            String mStatus = "";

            if (isChargerConnected && pauseWhenCharging) {  // "pause" Tai Chi when charger connected and pauseWhenCharging is true
                taiChiTime = mNow + (1000 * 60 * taiChiInterval);
                cancelNotification();
                sendActivityBroadCast("charger_connected", pitch, 0, inTaiChiMode,"");

            } else {
                 // Log.d(logTag, "tiltThreshold=" + tiltThreshold + " and pitch="+pitch);

                if (tiltThreshold < pitch) {  // Is the phone tilted in Tai Chi mode
                    if (fullTiltTime == 0) {  // Is fullTiltTime uninitalized?
                        fullTiltTime = mNow + (1000 * 60);  // Tai Chi will finish in 60 seconds from now
                    }
                    if (fullTiltTime < mNow) {   // Is Tai Chi done?
                        // Log.d(logTag, "Resetting: "+taiChiInterval);
                        taiChiTime = mNow + (1000 * 60 * taiChiInterval) + 1200;   // Set the new taiChiTime (now + taiChiInterval + 1-ish second)
                        mStatus = "Tai Chi Complete!";
                    } else {
                        mStatus = String.format(Locale.US, "Tai Chi in progress\nWill complete in: %02d:%02d", (fullTiltTime - mNow) / 1000 / 60, (fullTiltTime - mNow) / 1000 % 60);
                    }
                    inTaiChiMode = true;
                } else {
                    fullTiltTime = 0;
                    inTaiChiMode = false;
                }

                if (mSecDiff > 0) {  // Are we past due for Tai Chi?
                    // Set notification every nagInterval minutes
                    if (mMinDiff % nagInterval == 0 || mMinDiff == 0) {    // Is this the time to send a message?
                        if (mCurrentPageMinute != mMinDiff) {   // Have we paged on this minute yet?  Needed b/c timer is unreliable when sleeping
                            showNotification(mSecDiff);
                            mCurrentPageMinute = mMinDiff;
                            hasNotified = true;
                        }
                    }
                    sendActivityBroadCast("past_due", pitch, mSecDiff, inTaiChiMode, mStatus);
                } else {
                    cancelNotification();
                    sendActivityBroadCast("normal", pitch, mSecDiff, inTaiChiMode, mStatus);
                }
            }
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    float[] mGravity = null;
    float[] mGeomagnetic = null;
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            mGravity = event.values;
            //Log.d(logTag, "Accel");
        }
        if (event.sensor == magnetometer) {
            mGeomagnetic = event.values;
            //Log.d(logTag, "Mag");
        }

        if (mGravity != null && mGeomagnetic != null) {
            // Log.d(logTag, "Calculating orientation");
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                pitch = orientation[1] * FROM_RADS_TO_DEGS; // orientation contains: azimut, pitch and roll
            }
            // Log.d(logTag, "Changing");
        }

    }

    // Display a notification that Tai Chi is overdue by 'overdue' seconds
    public void showNotification(long overdue){
        long[] vibrate = {100,200,100,200};
        String notiText;
        // Intent intent = new Intent(this, MainActivity.class);
        // PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        cancelNotification();
        String notiTitle = "Tai Chi Time!";
        if (overdue / 60 == 0 ) {
            notiText = "Tai Chi is overdue";
        } else {
            notiText = String.format(Locale.US, "Tai Chi is overdue by %d minutes", overdue / 60);
        }
        Notification mNotification;
        Notification.Builder mBuilder;

        // NotificationManager notiMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {  // Oreo needs notification channels
            mBuilder = new Notification.Builder(this, "qtc_channel")
                    .setSmallIcon(ic_launcher)
                    .setContentTitle(notiTitle)
                    .setContentText(notiText)
                    .setContentIntent(pendingNotificationIntent)
                    .setAutoCancel(true);
        } else {
            mBuilder = new Notification.Builder(this)
                    .setSmallIcon(ic_launcher)
                    .setContentTitle(notiTitle)
                    .setContentText(notiText)
                    .setContentIntent(pendingNotificationIntent)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setVibrate(vibrate);
        }
        mNotification = mBuilder.build();

        if (notificationManager != null) {
            notificationManager.notify(999, mNotification);
        }
    }

    private void cancelNotification () {
        // Cancel notification
        if (hasNotified) {
            if (notificationManager != null) {
                notificationManager.cancel(999);
                hasNotified = false;
                Log.d(logTag, "Canceling notification");
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(logTag, "Service Start");
    }

    private void sendActivityBroadCast(String action, float pitch, long secDiff, boolean inTaiChiMode, String status) {
        try {
            Intent broadCastIntent = new Intent();
            broadCastIntent.setAction(MainActivity.BROADCAST_ACTION);

            Bundle extras = new Bundle();
            extras.putString("action",action);
            extras.putFloat("pitch",pitch);
            extras.putLong("secDiff",secDiff);
            extras.putBoolean("inTaiChiMode",inTaiChiMode);
            extras.putString("status",status);
            broadCastIntent.putExtras(extras);

            sendBroadcast(broadCastIntent);
            // Log.d(logTag, "Sending broadcast");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
