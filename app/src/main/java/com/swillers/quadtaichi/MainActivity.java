package com.swillers.quadtaichi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import static android.Manifest.permission.ACTIVITY_RECOGNITION;
import static android.Manifest.permission.POST_NOTIFICATIONS;

public class MainActivity extends AppCompatActivity {
    // Combined permissions request code
    private static final int RC_PERMISSIONS = 101;

    // Activity vars
    private TextView txtMessage, txtTilt, txtTaiChi;

    // Logging
    private static final String TAG = "MainActivity";

    // Broadcast Receivers
    private MyBroadCastReceiver myBroadCastReceiver;
    private BroadcastReceiver chargerReceiver;
    public static final String BROADCAST_ACTION = "com.swillers.quadtaichi";

    // Charger check stuff
    public static boolean isChargerConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ensure status bar icons (clock, battery, wifi, notifications) are dark and visible on white background
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        // Apply system bar insets so content does not overlap top notification/status bar
        View mainView = findViewById(R.id.constraintLayout);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        txtMessage = findViewById(R.id.txtMessage);
        txtTilt = findViewById(R.id.txtTilt);
        txtTaiChi = findViewById(R.id.txtTaiChi);

        // Register Broadcast Receiver for Service updates safely across Android versions
        myBroadCastReceiver = new MyBroadCastReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(BROADCAST_ACTION);
            ContextCompat.registerReceiver(this, myBroadCastReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception ex) {
            Log.e(TAG, "Error registering app broadcast receiver", ex);
        }

        // Register Charger Receiver safely across Android versions
        chargerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent batteryReceiverIntent) {
                Log.d(TAG, "Received Battery Status Change");
                if (batteryReceiverIntent != null) {
                    int plugged = batteryReceiverIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    isChargerConnected = plugged == BatteryManager.BATTERY_PLUGGED_AC
                            || plugged == BatteryManager.BATTERY_PLUGGED_USB
                            || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                } else {
                    isChargerConnected = true;
                }
            }
        };
        try {
            ContextCompat.registerReceiver(
                    this,
                    chargerReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED
            );
        } catch (Exception ex) {
            Log.e(TAG, "Error registering charger broadcast receiver", ex);
        }

        checkAndRequestPermissions();

        Log.d(TAG, "onCreate finished");
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(ACTIVITY_RECOGNITION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    RC_PERMISSIONS
            );
        } else {
            startTimerService();
        }
    }

    private void startTimerService() {
        try {
            Intent serviceIntent = new Intent(this, TimerService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start TimerService foreground service", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RC_PERMISSIONS) {
            Log.d(TAG, "Permissions result received");
            startTimerService();
        }
    }

    // Display settings activity
    public void showSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void exit(View view) {
        stopService(new Intent(this, TimerService.class));
        finish();
    }

    private void adjustChairImage(float pitch, String color) {
        ImageView imageView = findViewById(R.id.imgWheelChair);
        if (imageView != null) {
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
    }

    class MyBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent receiverIntent) {
            try {
                Bundle extras = receiverIntent.getExtras();
                if (extras == null) return;

                String action = extras.getString("action", "");
                float pitch = extras.getFloat("pitch", 0f);
                long secDiff = extras.getLong("secDiff", 0L);
                boolean inTaiChiMode = extras.getBoolean("inTaiChiMode", false);
                String status = extras.getString("status", "");

                switch (action) {
                    case "charger_connected":
                        txtMessage.setTextColor(Color.BLACK);
                        txtMessage.setText("Charger connected\nTai Chi on pause");
                        txtTaiChi.setVisibility(View.INVISIBLE);
                        adjustChairImage(pitch, "green");
                        break;
                    case "past_due":
                        txtMessage.setTextColor(Color.parseColor("#740411"));
                        txtMessage.setText(String.format(Locale.US, "Tai Chi overdue by %02d:%02d", secDiff / 60, secDiff % 60));
                        adjustChairImage(pitch, "red");
                        break;
                    case "normal":
                        txtMessage.setText(String.format(Locale.US, "Tai Chi due in: %02d:%02d", -secDiff / 60, -secDiff % 60));
                        txtMessage.setTextColor(Color.BLACK);
                        txtMessage.setVisibility(View.VISIBLE);
                        adjustChairImage(pitch, "blue");
                        break;
                }
                if (inTaiChiMode) {
                    txtTaiChi.setVisibility(View.VISIBLE);
                    txtTaiChi.setText(status);
                    adjustChairImage(pitch, "green");
                } else {
                    txtTaiChi.setVisibility(View.INVISIBLE);
                }
                txtTilt.setText(String.format(Locale.US, "Tilt: %.2f°", pitch));
            } catch (Exception ex) {
                Log.e(TAG, "Error in broadcast receiver", ex);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (myBroadCastReceiver != null) {
                unregisterReceiver(myBroadCastReceiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering myBroadCastReceiver", e);
        }
        try {
            if (chargerReceiver != null) {
                unregisterReceiver(chargerReceiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering chargerReceiver", e);
        }
    }
}
