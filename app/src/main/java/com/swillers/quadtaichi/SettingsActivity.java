package com.swillers.quadtaichi;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    EditText editInterval, editNag, editTiltThreshold, editTaiChiDuration;
    SwitchCompat swPauseWhenCharging;
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editInterval = this.findViewById(R.id.editInterval);
        editNag = this.findViewById(R.id.editNag);
        editTiltThreshold = this.findViewById(R.id.editTiltThreshold);
        editTaiChiDuration = this.findViewById(R.id.editTaiChiDuration);
        swPauseWhenCharging = this.findViewById(R.id.swPauseOnCharge);
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        // Log.d(TAG,"Interval: "+sharedPref.getInt("taiChiInterval", 0));

        // Populate inputs
        editInterval.setText(String.format(Locale.US, "%d",TimerService.taiChiInterval));
        editNag.setText(String.format(Locale.US, "%d",TimerService.nagInterval));
        editTiltThreshold.setText(String.format(Locale.US, "%d",TimerService.tiltThreshold));
        editTaiChiDuration.setText(String.format(Locale.US, "%d",TimerService.taiChiDuration));
        swPauseWhenCharging.setChecked(sharedPref.getBoolean("pauseWhenCharging", true));
    }

    public void updateSettings(View view) {
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("taiChiInterval", Integer.parseInt(editInterval.getText().toString()));
        editor.putInt("nagInterval", Integer.parseInt(editNag.getText().toString()));
        editor.putInt("tiltThreshold", Integer.parseInt(editTiltThreshold.getText().toString()));
        editor.putInt("taiChiDuration", Integer.parseInt(editTaiChiDuration.getText().toString()));
        editor.putBoolean("pauseWhenCharging", swPauseWhenCharging.isChecked());
        editor.apply();

        Log.d(TAG,"Saving Settings");
        Log.d(TAG,"TimerService.taiChiTime: "+TimerService.taiChiTime);
        TimerService.taiChiInterval = Integer.parseInt(editInterval.getText().toString());
        TimerService.taiChiTime = System.currentTimeMillis() + (1000L * 60 * TimerService.taiChiInterval);
        TimerService.nagInterval = Integer.parseInt(editNag.getText().toString());
        TimerService.tiltThreshold = Integer.parseInt(editTiltThreshold.getText().toString());
        TimerService.taiChiDuration = Integer.parseInt(editTaiChiDuration.getText().toString());
        TimerService.pauseWhenCharging = swPauseWhenCharging.isChecked();
        this.finish();
    }

    public void cancel(View view) {
        this.finish();
    }
}
