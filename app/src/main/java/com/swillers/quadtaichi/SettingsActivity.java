package com.swillers.quadtaichi;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {
EditText editInterval, editNag, editTiltThreshold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editInterval = (EditText) this.findViewById(R.id.editInterval);
        editNag = (EditText) this.findViewById(R.id.editNag);
        editTiltThreshold = (EditText) this.findViewById(R.id.editTiltThreshold);
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        // Log.d("Settings","Interval: "+sharedPref.getInt("taiChiInterval", 0));

        // Populate inputs
        if (sharedPref.getInt("taiChiInterval", 0) == 0) {
            editInterval.setText("45");
            // Log.d("Settings","Going default");
        } else {
            editInterval.setText(String.valueOf(sharedPref.getInt("taiChiInterval",0)));
        }
        if (sharedPref.getInt("nagInterval", 0) == 0) {
            editNag.setText("5");
        } else {
            editNag.setText(String.valueOf(sharedPref.getInt("nagInterval",0)));
        }
        if (sharedPref.getInt("tiltThreshold", 0) == 0) {
            editTiltThreshold.setText("30");
        } else {
            editTiltThreshold.setText(String.valueOf(sharedPref.getInt("tiltThreshold",0)));
        }
    }

    public void updateSettings(View view) {
        SharedPreferences sharedPref = getSharedPreferences("taiChiPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("taiChiInterval", Integer.parseInt(editInterval.getText().toString()));
        editor.putInt("nagInterval", Integer.parseInt(editNag.getText().toString()));
        editor.putInt("tiltThreshold", Integer.parseInt(editTiltThreshold.getText().toString()));
        editor.apply();
        // Log.d("Settings","Saving "+editInterval.getText());
        MainActivity.taiChiInterval = Integer.parseInt(editInterval.getText().toString());
        MainActivity.taiChiTime = System.currentTimeMillis() + (1000 * 60 * MainActivity.taiChiInterval);

        MainActivity.nagInterval = Integer.parseInt(editNag.getText().toString());
        MainActivity.tiltThreshold = Integer.parseInt(editTiltThreshold.getText().toString());
        this.finish();
    }

    public void cancel(View view) {
        this.finish();
    }
}
