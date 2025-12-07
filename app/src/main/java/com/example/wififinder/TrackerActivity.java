package com.example.wififinder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import java.util.List;

public class TrackerActivity extends AppCompatActivity {

    private static final String TAG = "TrackerActivity";
    private static final long RSSI_UPDATE_INTERVAL = 500; // Update every 0.5 seconds

    private WifiManager wifiManager;
    private Vibrator vibrator;

    private String targetBssid;
    private String targetSsid;

    private TextView txtTrackerTitle;
    private TextView txtTrackerBssid;
    private TextView txtRssiValue;
    private TextView txtDistance;
    private TextView txtSignalQuality;
    private View circleInner;
    private SwitchCompat switchVibration;
    private Button btnBack;
    private Button btnRefresh;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isTracking = true;
    private boolean vibrationEnabled = true;

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTracking) {
                updateSignalInfo();
                handler.postDelayed(this, RSSI_UPDATE_INTERVAL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracker);

        Log.d(TAG, "onCreate: TrackerActivity started");

        // Get intent data
        Intent intent = getIntent();
        targetBssid = intent.getStringExtra("BSSID");
        targetSsid = intent.getStringExtra("SSID");

        if (targetBssid == null) {
            Log.e(TAG, "onCreate: No BSSID provided");
            finish();
            return;
        }

        // Initialize services
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Initialize views
        txtTrackerTitle = findViewById(R.id.txtTrackerTitle);
        txtTrackerBssid = findViewById(R.id.txtTrackerBssid);
        txtRssiValue = findViewById(R.id.txtRssiValue);
        txtDistance = findViewById(R.id.txtDistance);
        txtSignalQuality = findViewById(R.id.txtSignalQuality);
        circleInner = findViewById(R.id.circleInner);
        switchVibration = findViewById(R.id.switchVibration);
        btnBack = findViewById(R.id.btnBack);

        // Set header info
        txtTrackerTitle.setText(targetSsid);
        txtTrackerBssid.setText(targetBssid);

        // Vibration switch
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vibrationEnabled = isChecked;
            Log.d(TAG, "Vibration " + (isChecked ? "enabled" : "disabled"));
            if (!isChecked && vibrator != null) {
                vibrator.cancel();
            }
        });

        // Back button
        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back button clicked");
            finish();
        });

        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);

        // Vibration switch
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vibrationEnabled = isChecked;
            Log.d(TAG, "Vibration " + (isChecked ? "enabled" : "disabled"));
            if (!isChecked && vibrator != null) {
                vibrator.cancel();
            }
        });

        // Refresh button
        btnRefresh.setOnClickListener(v -> {
            Log.d(TAG, "Refresh button clicked");
            Toast.makeText(this, "Refreshing signal data...", Toast.LENGTH_SHORT).show();

            // Trigger immediate update
            handler.removeCallbacks(updateRunnable);
            updateSignalInfo();
            handler.postDelayed(updateRunnable, RSSI_UPDATE_INTERVAL);

            // Also trigger a new WiFi scan if possible
            try {
                if (wifiManager != null) {
                    boolean scanStarted = wifiManager.startScan();
                    if (scanStarted) {
                        Log.i(TAG, "Manual scan triggered successfully");
                        Toast.makeText(this, "Scanning for latest signal data...", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w(TAG, "Scan blocked by system, using cached data");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error triggering scan: " + e.getMessage(), e);
            }
        });

        // Back button
        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back button clicked");
            finish();
        });



        // Start tracking
        handler.post(updateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTracking = false;
        handler.removeCallbacks(updateRunnable);
        if (vibrator != null) {
            vibrator.cancel();
        }
        Log.d(TAG, "onDestroy: TrackerActivity destroyed");
    }

    private void updateSignalInfo() {
        try {
            int rssi = -100;
            boolean found = false;

            // Try to get RSSI from connection info first
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getBSSID() != null
                    && wifiInfo.getBSSID().equalsIgnoreCase(targetBssid)) {
                rssi = wifiInfo.getRssi();
                found = true;
                Log.v(TAG, "updateSignalInfo: Using connection RSSI: " + rssi);
            } else {
                // Not connected, get from scan results
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                List<ScanResult> results = wifiManager.getScanResults();
                if (results != null) {
                    for (ScanResult sr : results) {
                        if (sr.BSSID.equalsIgnoreCase(targetBssid)) {
                            rssi = sr.level;
                            found = true;
                            Log.v(TAG, "updateSignalInfo: Using scan RSSI: " + rssi);
                            break;
                        }
                    }
                }
            }

            if (!found) {
                Log.w(TAG, "updateSignalInfo: Target network not found");
                txtRssiValue.setText("--");
                txtDistance.setText("Out of range");
                txtSignalQuality.setText("No Signal");
                updateCircleColor(-100);
                if (vibrator != null) {
                    vibrator.cancel();
                }
                return;
            }

            // Update UI
            txtRssiValue.setText(String.valueOf(rssi));

            // Calculate approximate distance (rough estimation)
            double distance = calculateDistance(rssi);
            String distanceText;
            if (distance < 1) {
                distanceText = "< 1 meter";
            } else if (distance < 10) {
                distanceText = String.format("~%.1f meters", distance);
            } else {
                distanceText = String.format("~%.0f meters", distance);
            }
            txtDistance.setText(distanceText);

            // Signal quality
            String quality;
            String qualityColor;
            if (rssi > -50) {
                quality = "Excellent - Very Close!";
                qualityColor = "#4CAF50"; // Green
            } else if (rssi > -60) {
                quality = "Good - Close";
                qualityColor = "#8BC34A"; // Light Green
            } else if (rssi > -70) {
                quality = "Fair - Medium Distance";
                qualityColor = "#FF9800"; // Orange
            } else if (rssi > -80) {
                quality = "Weak - Far";
                qualityColor = "#FF5722"; // Deep Orange
            } else {
                quality = "Very Weak - Very Far";
                qualityColor = "#F44336"; // Red
            }
            txtSignalQuality.setText(quality);
            txtSignalQuality.setTextColor(Color.parseColor(qualityColor));

            // Update circle visualization
            updateCircleColor(rssi);

            // Apply vibration
            if (vibrationEnabled) {
                applyVibration(rssi);
            }

        } catch (Exception e) {
            Log.e(TAG, "updateSignalInfo: Error: " + e.getMessage(), e);
        }
    }

    private double calculateDistance(int rssi) {
        // Rough approximation using free-space path loss formula
        // Distance (meters) ≈ 10 ^ ((RSSI_at_1m - RSSI) / (10 * n))
        // Where n = path loss exponent (typically 2-4, using 2.5 for indoor)
        // RSSI_at_1m ≈ -40 dBm (typical for WiFi)

        int rssiAt1m = -40;
        double pathLossExponent = 2.5;

        double distance = Math.pow(10, (rssiAt1m - rssi) / (10.0 * pathLossExponent));
        return distance;
    }

    private void updateCircleColor(int rssi) {
        GradientDrawable drawable = (GradientDrawable) circleInner.getBackground();

        String fillColor;
        String strokeColor;

        if (rssi > -50) {
            fillColor = "#4CAF50"; // Green
            strokeColor = "#2E7D32";
        } else if (rssi > -60) {
            fillColor = "#8BC34A"; // Light Green
            strokeColor = "#558B2F";
        } else if (rssi > -70) {
            fillColor = "#FF9800"; // Orange
            strokeColor = "#E65100";
        } else if (rssi > -80) {
            fillColor = "#FF5722"; // Deep Orange
            strokeColor = "#BF360C";
        } else {
            fillColor = "#F44336"; // Red
            strokeColor = "#B71C1C";
        }

        drawable.setColor(Color.parseColor(fillColor));
        drawable.setStroke(6, Color.parseColor(strokeColor));
    }

    private void applyVibration(int rssi) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        int minRssi = -90;
        int maxRssi = -40;
        int clamped = Math.max(minRssi, Math.min(maxRssi, rssi));

        float normalized = (float) (clamped - minRssi) / (float) (maxRssi - minRssi);
        int amplitude = (int) (normalized * 255);

        Log.v(TAG, "applyVibration: RSSI=" + rssi + ", amplitude=" + amplitude);

        if (amplitude <= 10) {
            vibrator.cancel();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 10000},
                        new int[]{0, amplitude},
                        1
                );
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(10000);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyVibration: Error: " + e.getMessage(), e);
        }
    }
}
