package com.swillers.quadtaichi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // Activity vars
    TextView txtMessage, txtTilt, txtTaiChi;

    // Logging
    final String TAG = "MainActivity";

    //Broadcast Receiver
    MyBroadCastReceiver myBroadCastReceiver;
    public static final String BROADCAST_ACTION = "com.swillers.quadtaichi";

    // Charger check stuff
    public static boolean isChargerConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtMessage = this.findViewById(R.id.txtMessage);
        txtTilt = this.findViewById(R.id.txtTilt);
        txtTaiChi = this.findViewById(R.id.txtTaiChi);
        BroadcastReceiver chargerReceiver;


        // Register Receiver for Service
        myBroadCastReceiver = new MyBroadCastReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BROADCAST_ACTION);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(myBroadCastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            }else {
                registerReceiver(myBroadCastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        chargerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent batteryReceiverIntent) {
                Log.d(TAG, "Received Change");
                if (batteryReceiverIntent != null) {
                    int plugged = batteryReceiverIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    isChargerConnected = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
                    isChargerConnected = isChargerConnected || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                } else {
                    isChargerConnected = true;
                }
            }
        };
        registerReceiver(
                chargerReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );

        startForegroundService(new Intent(getBaseContext(), TimerService.class));

        Log.d(TAG, "onCreate");

    }


    //Not sure if i need this
    /* @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
        // this.finish();  // close app
    } */

    // Display settings activity
    public void showSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void exit(View view) {
        stopService(new Intent(getBaseContext(), TimerService.class));
        System.exit(0);
        // android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void adjustChairImage(float pitch, String color) {

        // Tilt the wheelchair image
        ImageView imageView = findViewById(R.id.imgWheelChair);
        imageView.setRotation(pitch);
        switch (color) {
            case "red":
                imageView.setImageResource(R.drawable.img_wheelchair_red);
                break;
            case "green":
                imageView.setImageResource(R.drawable.img_wheelchair_green);
                break;
            default:
                imageView.setImageResource(R.drawable.img_wheelchair_blue);
                break;
        }
    }

    protected void onResume() {
        super.onResume();

    }

    protected void onPause() {
        super.onPause();
        // mSensorManager.unregisterListener(this);
    }

    class MyBroadCastReceiver extends BroadcastReceiver {
        String  action, status;
        float pitch;
        long secDiff;
        boolean inTaiChiMode;

        @Override
        public void onReceive(Context context, Intent receiverIntent) {
            try {
                // Log.d(TAG, "Receiving broadcast");
                Bundle extras = receiverIntent.getExtras();
                if (extras != null) {
                    action = extras.getString("action");
                    pitch = extras.getFloat("pitch");
                    secDiff = extras.getLong("secDiff");
                    inTaiChiMode = extras.getBoolean("inTaiChiMode");
                    status = extras.getString("status");
                }

                switch (action) {
                    case "charger_connected":
                        txtMessage.setTextColor(Color.BLACK);
                        txtMessage.setText("Charger connected\nTai Chi on pause");
                        txtTaiChi.setVisibility(View.INVISIBLE);
                        adjustChairImage(pitch, "green");
                        // Log.d(TAG, "charger on: " + secDiff);
                        break;
                    case "past_due":
                        txtMessage.setTextColor(Color.parseColor("#740411"));
                        txtMessage.setText(String.format(Locale.US, "Tai Chi overdue by %02d:%02d", secDiff / 60, secDiff % 60));
                        adjustChairImage(pitch, "red");
                        // Log.d(TAG, "past due: " + secDiff);
                        break;
                    case "normal":
                        txtMessage.setText(String.format(Locale.US, "Tai Chi due in: %02d:%02d", -secDiff / 60, -secDiff % 60));
                        txtMessage.setTextColor(Color.BLACK);
                        adjustChairImage(pitch, "blue");
                        // Log.d(TAGTAG, "normal: " + secDiff);
                        break;
                }
                if (inTaiChiMode) {
                    txtTaiChi.setVisibility(View.VISIBLE);
                    txtTaiChi.setText(status);
                    adjustChairImage(pitch, "green");
                } else {
                    txtTaiChi.setVisibility(View.INVISIBLE);
                }
                txtTilt.setText(String.format(Locale.US,"Tilt: %.2f°",pitch));
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    protected void onDestroy() {
        super.onDestroy();

        // make sure to unregister your receiver after finishing of this activity
        unregisterReceiver(myBroadCastReceiver);
    }

}
