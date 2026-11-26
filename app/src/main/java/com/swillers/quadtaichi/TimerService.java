package com.swillers.quadtaichi;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import static com.swillers.quadtaichi.MainActivity.isChargerConnected;

public class TimerService extends Service implements SensorEventListener {

    // Logging
    private static final String TAG = "TimerService";

    // TaiChi Preferences
    public static volatile int taiChiInterval; // Interval between pressure releases in seconds
    public static volatile int nagInterval;   // Interval between notifications in minutes
    public static volatile int tiltThreshold; // Degrees of tilt needed to achieve Tai Chi mode
    public static volatile int taiChiDuration; // Duration of tai chi before timer resets
    public static volatile boolean pauseWhenCharging;
    public static volatile long taiChiTime;

    // Sensor vars
    private static final int FROM_RADS_TO_DEGS = -57;
    public static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
    private SensorManager mSensorManager;
    public volatile float pitch;
    private long fullTiltTime = 0;

    // Preallocated arrays for sensor calculation to avoid GC allocations
    private final float[] mGravity = new float[3];
    private final float[] mGeomagnetic = new float[3];
    private final float[] rotationMatrixR = new float[9];
    private final float[] rotationMatrixI = new float[9];
    private final float[] orientationValues = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;

    // Timing stuff
    public static Timer timer;

    // Notification stuff
    private static final int STATUS_NOTIFICATION_ID = 2;
    public static final String CHANNEL_NAME = "qtc_channel";
    public volatile boolean inTaiChiMode = false;
    public long mNotificationMinute = -1;  // The minute that we updated notification
    public final String notificationTitle = "Quad Tai Chi";
    public volatile boolean isTaiChiDue = false;

    public static class LocalBinder extends Binder {

    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        startForegroundServiceNotification();
        return START_STICKY;
    }

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

        taiChiTime = System.currentTimeMillis() + (1000L * 60 * taiChiInterval);
        taiChiTime = System.currentTimeMillis() + (1000L * 60 * taiChiInterval);
        createNotificationChannel();

        // Start Foreground Service
        startForegroundServiceNotification();

        // Setup Sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (accelerometer != null) {
                mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
            }
            if (magnetometer != null) {
                mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
            }
        }

        // Setup timer run checks every 100th of a second
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        timer.schedule(new mainTask(), 0, 100);
    }

    private void startForegroundServiceNotification() {
        try {
            Notification notification = buildStatusNotification("Quad Tai Chi Running", false);
            if (Build.VERSION.SDK_INT >= 34) {
                boolean hasActivityRec = ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED;

                if (hasActivityRec) {
                    ServiceCompat.startForeground(
                            this,
                            STATUS_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                    );
                } else {
                    Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted yet, deferring startForeground");
                }
            } else {
                startForeground(STATUS_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground notification", e);
        }
    }

    private class mainTask extends TimerTask {
        @Override
        public void run() {
            long mNow = System.currentTimeMillis();
            long mSecDiff = (mNow - taiChiTime) / 1000;  // number of seconds before/after taiChiTime
            long mMinDiff = mSecDiff / 60;  // number of minutes before/after taiChiTime

            if (isChargerConnected && pauseWhenCharging) {  // "pause" Tai Chi when charger connected and pauseWhenCharging is true
                taiChiTime = mNow + (1000L * 60 * taiChiInterval);
                sendActivityBroadCast("charger_connected", pitch, 0, inTaiChiMode, "");
            } else {
                float currentPitch = pitch;
                String mStatus;
                if (tiltThreshold < currentPitch) {  // Is the phone tilted in Tai Chi mode
                    if (fullTiltTime == 0) {  // Is fullTiltTime uninitialized?
                        fullTiltTime = mNow + (1000L * taiChiDuration * 60);  // Tai Chi will finish in {taiChiDuration} minutes from now
                    }
                    if (fullTiltTime < mNow) {   // Is Tai Chi done?
                        taiChiTime = mNow + (1000L * 60 * taiChiInterval) + 1200;   // Set the new taiChiTime (now + taiChiInterval + 1-ish second)
                        mStatus = "Tai Chi Complete!";
                    } else {
                        long remainingSecs = (fullTiltTime - mNow) / 1000;
                        mStatus = String.format(Locale.US, "Tai Chi in progress\nWill complete in: %02d:%02d", remainingSecs / 60, remainingSecs % 60);
                    }
                    inTaiChiMode = true;
                } else {
                    fullTiltTime = 0;
                    inTaiChiMode = false;
                    mStatus = "";
                }

                if (mSecDiff > 0) {  // Are we past due for Tai Chi?
                    boolean shouldAlert = !isTaiChiDue;
                    isTaiChiDue = true;
                    if (mNotificationMinute != mMinDiff) {   // Have we notified on this minute yet?
                        if (mMinDiff % nagInterval == 0 || mMinDiff == 0) {
                            updateStatusNotification(String.format(Locale.US, "Tai Chi is overdue by %d minutes", mMinDiff), shouldAlert);
                        } else {
                            updateStatusNotification(String.format(Locale.US, "Tai Chi is overdue by %d minutes", mMinDiff), false);
                        }
                        mNotificationMinute = mMinDiff;
                    } else if (shouldAlert) {
                        updateStatusNotification("Tai Chi is due!", true);
                    }
                    sendActivityBroadCast("past_due", currentPitch, mSecDiff, inTaiChiMode, mStatus);
                } else {
                    isTaiChiDue = false;
                    if (mNotificationMinute != mMinDiff) {   // Have we updated the notification on this minute yet?
                        updateStatusNotification(String.format(Locale.US, "Tai Chi due in %d minutes", mMinDiff * -1), false);
                        mNotificationMinute = mMinDiff;
                    }
                    sendActivityBroadCast("normal", currentPitch, mSecDiff, inTaiChiMode, mStatus);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, mGravity, 0, 3);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);
            hasGeomagnetic = true;
        }

        if (hasGravity) {
            if (hasGeomagnetic) {
                boolean success = SensorManager.getRotationMatrix(rotationMatrixR, rotationMatrixI, mGravity, mGeomagnetic);
                if (success) {
                    SensorManager.getOrientation(rotationMatrixR, orientationValues);
                    pitch = orientationValues[1] * FROM_RADS_TO_DEGS; // orientation contains: azimuth, pitch and roll
                }
            } else {
                // Fallback: Calculate pitch directly from accelerometer values when magnetometer is unavailable (e.g. emulators)
                double ax = mGravity[0];
                double ay = mGravity[1];
                double az = mGravity[2];
                pitch = (float) Math.toDegrees(Math.atan2(ay, Math.sqrt(ax * ax + az * az)));
            }
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_NAME, notificationTitle, NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildStatusNotification(String notificationText, boolean alert) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_NAME);
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setContentTitle(notificationTitle);
        builder.setContentText(notificationText);
        builder.setOngoing(true);
        builder.setAutoCancel(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setContentIntent(pendingIntent);
        if (isTaiChiDue) {
            builder.setColor(Color.RED);
        } else {
            builder.setColor(Color.GREEN);
        }

        if (alert) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
            builder.setOnlyAlertOnce(false);
        } else {
            builder.setOnlyAlertOnce(true);
        }
        return builder.build();
    }

    private void updateStatusNotification(String notificationText, boolean alert) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (notificationManager.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing notification permissions");
                return;
            }
            notificationManager.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(notificationText, alert));
            Log.d(TAG, "Updating Status Notification: " + notificationText);
        } else {
            Log.d(TAG, "Notifications Disabled");
        }
    }

    private void sendActivityBroadCast(String action, float pitch, long secDiff, boolean inTaiChiMode, String status) {
        try {
            Intent broadCastIntent = new Intent();
            broadCastIntent.setAction(MainActivity.BROADCAST_ACTION);
            broadCastIntent.setPackage(getPackageName());

            Bundle extras = new Bundle();
            extras.putString("action", action);
            extras.putFloat("pitch", pitch);
            extras.putLong("secDiff", secDiff);
            extras.putBoolean("inTaiChiMode", inTaiChiMode);
            extras.putBoolean("inTaiChiMode", inTaiChiMode);
            extras.putString("status", status);
            broadCastIntent.putExtras(extras);

            sendBroadcast(broadCastIntent);
        } catch (Exception ex) {
            Log.e(TAG, "Error sending broadcast", ex);
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }
}
