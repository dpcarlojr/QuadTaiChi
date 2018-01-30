package com.swillers.quadtaichi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.swillers.quadtaichi.R.mipmap.ic_launcher;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // Activity vars
    TextView txtMessage, txtPitch, txtTaiChi;
    public static int taiChiInterval; // Interval between pressure releases in seconds
    public static int nagInterval;  // Interval between notifications in minutes
    public static long tiltThreshold;
    public static boolean pauseWhenCharging;

    // Logging
    private final String logTag = "MainActivity";

    // Sensor vars
    public float pitch;
    private static final int FROM_RADS_TO_DEGS = -57;
    public int SENSOR_DELAY = 500;
    Sensor accelerometer;
    Sensor magnetometer;
    private long fullTiltTime = 0;

    // Timing stuff
    public static long taiChiTime;
    public long mCurrentPageMinute = -1;  // The minute that

    // Charger check stuff
    public static boolean isChargerConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtMessage = this.findViewById(R.id.txtMessage);
        txtPitch = this.findViewById(R.id.txtPitch);
        txtTaiChi = this.findViewById(R.id.txtTaiChi);
        TimerTask timerTask;
        Timer timer;

        SensorManager mSensorManager;


        // Load preferences
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        if (sharedPref.getInt("taiChiInterval", 0) == 0) {
            taiChiInterval = 45;
        } else {
            taiChiInterval = sharedPref.getInt("taiChiInterval",0);
        }
        if (sharedPref.getInt("nagInterval", 0) == 0) {
            nagInterval = 5;
        } else {
            nagInterval = sharedPref.getInt("nagInterval",0);
        }
        if (sharedPref.getInt("tiltThreshold", 0) == 0) {
            tiltThreshold = 5;
        } else {
            tiltThreshold = sharedPref.getInt("tiltThreshold",0);
        }
        pauseWhenCharging = sharedPref.getBoolean("pauseWhenCharging",true);

        taiChiTime = System.currentTimeMillis() + (1000 * 60 * taiChiInterval);

        // Setup Sensors
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            mSensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
            mSensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
        }

        // Setup timer run checks every second
        timerTask = new MyTimerTask();
        timer = new Timer();
        timer.schedule(timerTask, 0,1000);

        // Check initial charger status. BatteryReceiver takes over after this
        Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        isChargerConnected = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
        {
            isChargerConnected = isChargerConnected || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }


        Log.d(logTag, "onCreate");

    }

    // Display a notification that Tai Chi is overdue by 'overdue' seconds
    public void showNotification(long overdue){
        long[] vibrate = {100,200,100,200};
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        String notiTitle = "Tai Chi Time!";
        String notiText = String.format(Locale.US,"Tai Chi is overdue by %d minutes",overdue / 60);

        NotificationManager notiMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {  // Oreo needs notification channels
            NotificationChannel mChannel = new NotificationChannel("qtc_channel","Quad Tai Chi Channel", NotificationManager.IMPORTANCE_DEFAULT);
            mChannel.setDescription("Notifies users when Tai Chi is due");
            if (notiMgr != null) {
                notiMgr.createNotificationChannel(mChannel);
            }
            Notification.Builder builder = new Notification.Builder(this, "qtc_channel")
                    .setSmallIcon(ic_launcher)
                    .setContentTitle(notiTitle)
                    .setContentText(notiText)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            Notification noti = builder.build();
            if (notiMgr != null) {
                notiMgr.notify(999, noti);
            }
        } else {
            Notification noti = new Notification.Builder(this)
                    .setSmallIcon(ic_launcher)
                    .setContentTitle(notiTitle)
                    .setContentText(notiText)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setVibrate(vibrate)
                    .build();

            if (notiMgr != null) {
                notiMgr.notify(999, noti);
            }
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        // this.finish();  // close app
    }

    // Display settings activy
    public void showSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void exit(View view) {
        System.exit(0);
        // android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void TimerMethod() {
        this.runOnUiThread(Timer_Tick);
    }
    private Runnable Timer_Tick = new Runnable() {
        @Override
        public void run() {
            long mNow = System.currentTimeMillis();
            long mSecDiff = (mNow - taiChiTime) / 1000;  // number of seconds before/after taiChiTime
            long mMinDiff = mSecDiff / 60;  // number of minutes before/after taiChiTime
            String mStatus;

            if (isChargerConnected && pauseWhenCharging) {  // "pause" Tai Chi when charger connected and pauseWhenCharging is true
                txtMessage.setTextColor(Color.BLACK);
                txtMessage.setText("Charger connected\nTai Chi on pause");
                taiChiTime = mNow + (1000 * 60 * taiChiInterval);
                txtTaiChi.setVisibility(View.INVISIBLE);
            } else {
                if (mSecDiff > 0) {
                    txtMessage.setTextColor(Color.RED);
                    txtMessage.setText(String.format(Locale.US, "Tai Chi overdue by %02d:%02d", mSecDiff / 60, mSecDiff % 60));

                    // Set notification every nagInterval minutes
                    if (mMinDiff % nagInterval == 0) {    // Is this the time to send a message?
                        if (mCurrentPageMinute != mMinDiff) {   // Have we paged on this minute yet?  Needed b/c timer is unreliable whnen sleeping
                            showNotification(mSecDiff);
                            mCurrentPageMinute = mMinDiff;
                        }
                    }
                } else {
                    txtMessage.setText(String.format(Locale.US, "Tai Chi due in: %02d:%02d", -mSecDiff / 60, -mSecDiff % 60));
                    txtMessage.setTextColor(Color.BLACK);

                    // Cancel notification
                    NotificationManager notiMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (notiMgr != null) {
                        notiMgr.cancel(999);
                    }
                }

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
                    txtTaiChi.setVisibility(View.VISIBLE);
                    txtTaiChi.setText(mStatus);
                } else {
                    txtTaiChi.setVisibility(View.INVISIBLE);
                    fullTiltTime = 0;
                }
            }
            txtPitch.setText(String.format(Locale.US,"Tilt: %.2f\u00B0",pitch));
        }
    };


    public class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            TimerMethod();
        }
    }

    protected void onResume() {
        super.onResume();

    }

    protected void onPause() {
        super.onPause();
        // mSensorManager.unregisterListener(this);
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
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                pitch = orientation[1] * FROM_RADS_TO_DEGS; // orientation contains: azimut, pitch and roll
            }
        }

    }

}
