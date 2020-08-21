package com.swillers.quadtaichi;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
EditText editInterval, editNag, editTiltThreshold;
Switch swPauseWhenCharging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editInterval = this.findViewById(R.id.editInterval);
        editNag = this.findViewById(R.id.editNag);
        editTiltThreshold = this.findViewById(R.id.editTiltThreshold);
        swPauseWhenCharging = this.findViewById(R.id.swPauseOnCharge);
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        // Log.d("Settings","Interval: "+sharedPref.getInt("taiChiInterval", 0));

        // Populate inputs
        editInterval.setText(String.format(Locale.US, "%d",TimerService.taiChiInterval));
        editNag.setText(String.format(Locale.US, "%d",TimerService.nagInterval));
        editTiltThreshold.setText(String.format(Locale.US, "%d",TimerService.tiltThreshold));
        swPauseWhenCharging.setChecked(sharedPref.getBoolean("pauseWhenCharging", true));
    }

    public void updateSettings(View view) {
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("taiChiInterval", Integer.parseInt(editInterval.getText().toString()));
        editor.putInt("nagInterval", Integer.parseInt(editNag.getText().toString()));
        editor.putInt("tiltThreshold", Integer.parseInt(editTiltThreshold.getText().toString()));
        editor.putBoolean("pauseWhenCharging", swPauseWhenCharging.isChecked());
        editor.apply();

        Log.d("Settings","Saving Settings");
        TimerService.taiChiInterval = Integer.parseInt(editInterval.getText().toString());
        TimerService.taiChiTime = System.currentTimeMillis() + (1000 * 60 * TimerService.taiChiInterval);
        TimerService.nagInterval = Integer.parseInt(editNag.getText().toString());
        TimerService.tiltThreshold = Integer.parseInt(editTiltThreshold.getText().toString());
        TimerService.pauseWhenCharging = swPauseWhenCharging.isChecked();
        this.finish();
    }

    public void cancel(View view) {
        this.finish();
    }
}
