package com.swillers.quadtaichi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static com.swillers.quadtaichi.MainActivity.isChargerConnected;

public class TimerService extends JobIntentService implements SensorEventListener {

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private static class LocalBinder extends Binder {

    }

    private final IBinder mBinder = new LocalBinder();

    // TaiChi vars
    public static int taiChiInterval; // Interval between pressure releases in seconds
    public static int nagInterval;  // Interval between notifications in minutes
    public static int tiltThreshold;  // Degrees of tilt needed to achieve Tai Chi mode
    public static int taiChiDuration;  // Duration of tai chi before timer resets

    // Logging
    final String TAG = "TimerService";

    // Sensor vars
    public float pitch;
    private static final int FROM_RADS_TO_DEGS = -57;
    public int SENSOR_DELAY = 3;
    Sensor accelerometer;
    Sensor magnetometer;
    private long fullTiltTime = 0;
    SensorManager mSensorManager;

    // Timing stuff
    public static long taiChiTime;
    public static Timer timer;

    // Notification stuff
    private static final int STATUS_NOTIFICATION_ID = 2;
    public final String CHANNEL_NAME = "qtc_channel";
    public static boolean pauseWhenCharging;
    public boolean inTaiChiMode = false;
    public PendingIntent pendingNotificationIntent;
    public long mNotificationMinute = -1;  // The minute that we updated notification
    public final String notificationTitle = "Quad Tai Chi";
    public boolean isTaiChiDue = false;



    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");

        // Load preferences
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        taiChiInterval = sharedPref.getInt("taiChiInterval", 45);
        nagInterval = sharedPref.getInt("nagInterval", 5);
        tiltThreshold = sharedPref.getInt("tiltThreshold", 30);
        taiChiDuration = sharedPref.getInt("taiChiDuration", 2);
        pauseWhenCharging = sharedPref.getBoolean("pauseWhenCharging", true);

        Intent notificationIntent = new Intent(this, TimerService.class);
        pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        taiChiTime = System.currentTimeMillis() + (1000L * 60 * taiChiInterval);
        createNotificationChannel();

        // Setup Sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
            mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
        }

        // Setup timer run checks every 1/4 of a second
        timer = new Timer();
        timer.scheduleAtFixedRate(new mainTask(), 0, 250);
        startForeground(STATUS_NOTIFICATION_ID, buildStatusNotification("Quad Tai Chi Running"));
    }

    private class mainTask extends TimerTask {
        public void run() {
            long mNow = System.currentTimeMillis();
            long mSecDiff = (mNow - taiChiTime) / 1000;  // number of seconds before/after taiChiTime
            long mMinDiff = mSecDiff / 60;  // number of minutes before/after taiChiTime
            String mStatus = "";

            if (isChargerConnected && pauseWhenCharging) {  // "pause" Tai Chi when charger connected and pauseWhenCharging is true
                taiChiTime = mNow + (1000L * 60 * taiChiInterval);
                sendActivityBroadCast("charger_connected", pitch, 0, inTaiChiMode, "");
            } else {
                // Log.d(TAG, "tiltThreshold=" + tiltThreshold + " and pitch="+pitch);
                if (tiltThreshold < pitch) {  // Is the phone tilted in Tai Chi mode
                    if (fullTiltTime == 0) {  // Is fullTiltTime uninitialized?
                        fullTiltTime = mNow + (1000L * taiChiDuration * 60);  // Tai Chi will finish in {taiChiDuration} minutes from now
                    }
                    if (fullTiltTime < mNow) {   // Is Tai Chi done?
                        // Log.d(TAG, "Resetting: "+taiChiInterval);
                        taiChiTime = mNow + (1000L * 60 * taiChiInterval) + 1200;   // Set the new taiChiTime (now + taiChiInterval + 1-ish second)
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
                    //Log.d(TAG, "Tai Chi Due. mNotificationMinute=" + mNotificationMinute + " and mMinDiff="+ mMinDiff);
                    if (!isTaiChiDue) {  // Send a single popup notification when Tai Chi is due
                        createPopupNotification("Tai Chi is due!");
                    }
                    isTaiChiDue = true;
                    if (mNotificationMinute != mMinDiff) {   // Have we notified on this minute yet?
                        if (mMinDiff % nagInterval == 0 || mMinDiff == 0) {    // Is this the time to send a popup message?
                            createPopupNotification(String.format(Locale.US, "Tai Chi is overdue by %d minutes", mMinDiff));
                            mNotificationMinute = mMinDiff;
                        } else {
                            updateStatusNotification(String.format(Locale.US, "Tai Chi is overdue by %d minutes", mMinDiff));
                            mNotificationMinute = mMinDiff;
                        }
                    }
                    sendActivityBroadCast("past_due", pitch, mSecDiff, inTaiChiMode, mStatus);
                } else {
                    //Log.d(TAG, "Tai Chi NOT Due. mNotificationMinute=" + mNotificationMinute + " and mMinDiff="+ mMinDiff);
                    isTaiChiDue = false;
                    if (mNotificationMinute != mMinDiff) {   // Have we updated the notification on this minute yet?
                        updateStatusNotification(String.format(Locale.US, "Tai Chi due in %d minutes", mMinDiff * -1));
                        mNotificationMinute = mMinDiff;
                    }
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
            //Log.d(TAG, "Accel");
        }
        if (event.sensor == magnetometer) {
            mGeomagnetic = event.values;
            //Log.d(TAG, "Mag");
        }

        if (mGravity != null && mGeomagnetic != null) {
            // Log.d(TAG, "Calculating orientation");
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                pitch = orientation[1] * FROM_RADS_TO_DEGS; // orientation contains: azimut, pitch and roll
            }
            // Log.d(TAG, "Changing");
        }

    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_NAME, notificationTitle, NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildStatusNotification(String notificationText) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Log.d(TAG, "Building Status Notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_NAME);
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setContentTitle(notificationTitle);
        builder.setContentText(notificationText);
        builder.setOngoing(true);
        builder.setAutoCancel(true);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        builder.setContentIntent(pendingIntent);
        //builder.setColorized(true);
        if (isTaiChiDue) {
            builder.setColor(Color.RED);
        } else {
            builder.setColor(Color.GREEN);
        }
        //builder.setVibrate(vibrate);
        builder.setOnlyAlertOnce(true);
        builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        return builder.build();
    }

    private void createPopupNotification(String notificationText) {
        Log.d(TAG, "Building Popup Notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_NAME);
        builder.setOngoing(false);
        //builder.setColorized(true);
        builder.setColor(Color.RED);
        builder.setAutoCancel(true);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setContentTitle(notificationTitle);
        builder.setContentText(notificationText);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(STATUS_NOTIFICATION_ID, builder.build());
    }


    private void updateStatusNotification(String notificationText) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (notificationManager.areNotificationsEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Notifications permissions denied. We handle this on MainActivity
                Log.d(TAG, "Missing notification Permissions");
            }
            notificationManager.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(notificationText));
            Log.d(TAG, "Updating Status Notification");
        } else {
            Log.d(TAG, "Notifications Disabled");
        }
    }

    private void sendActivityBroadCast(String action, float pitch, long secDiff, boolean inTaiChiMode, String status) {
        try {
            Intent broadCastIntent = new Intent();
            broadCastIntent.setAction(MainActivity.BROADCAST_ACTION);
            broadCastIntent.setPackage("com.swillers.quadtaichi");

            Bundle extras = new Bundle();
            extras.putString("action",action);
            extras.putFloat("pitch",pitch);
            extras.putLong("secDiff",secDiff);
            extras.putBoolean("inTaiChiMode",inTaiChiMode);
            extras.putString("status",status);
            broadCastIntent.putExtras(extras);

            sendBroadcast(broadCastIntent);
            // Log.d(TAG, "Sending broadcast");
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

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "onHandleWork:");

    }
}
