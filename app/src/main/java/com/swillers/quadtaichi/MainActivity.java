package com.swillers.quadtaichi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // Activity vars
    public TextView txtMessage, txtPitch, txtTaiChi;
    public static int taiChiInterval; // Interval between pressure releases in seconds
    public static int nagInterval;  // Interval between notifications in minutes
    public static long tiltThreshold;

    // Logging
    private String logTag = "MainActivity";

    // Sensor vars
    public float pitch;
    private SensorManager mSensorManager;
    private static final int FROM_RADS_TO_DEGS = -57;
    Sensor accelerometer;
    Sensor magnetometer;
    private long fullTiltTime = 0;
    private boolean isTilted;

    // Timing stuff
    public static long taiChiTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtMessage = (TextView) this.findViewById(R.id.txtMessage);
        txtPitch = (TextView) this.findViewById(R.id.txtPitch);
        txtTaiChi = (TextView) this.findViewById(R.id.txtTaiChi);
        TimerTask timerTask;
        Timer timer;

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
        taiChiTime = System.currentTimeMillis() + (1000 * 60 * taiChiInterval);

        // Setup Sensors
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Setup timer run checks every second
        timerTask = new MyTimerTask();
        timer = new Timer();
        timer.schedule(timerTask, 0,1000);

        // Log.d(logTag, "onCreate");

    }



    public void showNotification(long overdue){
        NotificationManager notiMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification noti = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Tai Chi Time!")
                .setContentText("Tai Chi is overdue by " + (overdue / 60) + " minutes")
                .setAutoCancel(true)
                .build();

        notiMgr.notify(999,noti);

    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        // this.finish();  // close app
    }

    public void showSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);

    }

    private void TimerMethod() {
        this.runOnUiThread(Timer_Tick);
    }
    private Runnable Timer_Tick = new Runnable() {
        @Override
        public void run() {
            long mNow = System.currentTimeMillis();
            long mTimeDiff = (mNow - taiChiTime) / 1000;  // number of seconds before/after taiChiTime
            String mStatus;


            Log.d(logTag,"Past Due: " + mTimeDiff + " - Tai Chi Time: " + taiChiTime);
            if (mTimeDiff > 0) {
                txtMessage.setTextColor(Color.RED);
                txtMessage.setText("Tai Chi overdue by " + String.format("%02d", (mTimeDiff/60)) + ":" + String.format("%02d", (mTimeDiff % 60)));

                // Set notification every minute
                if ((mTimeDiff % 60 / nagInterval) == 0 || mTimeDiff == 1) {
                    showNotification(mTimeDiff);
                }
            } else {
                txtMessage.setText("Tai Chi due in: " + String.format("%02d", -mTimeDiff/60) + ":" + String.format("%02d", -mTimeDiff % 60));
                txtMessage.setTextColor(Color.BLACK);

                // Cancel notification
                NotificationManager notiMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                notiMgr.cancel(999);
            }

            if (tiltThreshold < pitch) {
                if (fullTiltTime == 0) {
                    fullTiltTime = mNow + (1000 * 60);  // Tai Chi will finish in 60 seconds
                }
                if (fullTiltTime < mNow) {
                    //Log.d(logTag, "Resetting: "+taiChiInterval);
                    taiChiTime = mNow + (1000 * 60 * taiChiInterval);
                    mStatus = "Complete!";
                } else {
                    mStatus = "Will complete in: " + String.format("%02d", (fullTiltTime - mNow) / 1000 / 60) + ":" + String.format("%02d", (fullTiltTime - mNow) / 1000 % 60);
                }
                txtTaiChi.setVisibility(View.VISIBLE);
                txtTaiChi.setText("Tai Chi in progress\n" + mStatus);
            } else {
                txtTaiChi.setVisibility(View.INVISIBLE);
                fullTiltTime = 0;
            }

            txtPitch.setText(String.format("Pitch: %f",pitch));
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
        //mSensorManager.registerListener(this, mRotationSensor, SensorManager.SENSOR_DELAY_UI);
        //mSensorManager.registerListener(this, mRotationSensor, SENSOR_DELAY);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    protected void onPause() {
        super.onPause();
       //mSensorManager.unregisterListener(this);

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
