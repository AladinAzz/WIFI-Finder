package com.example.wififinder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WiFiVibrationRadar";
    private static final int REQ_PERMISSIONS = 1001;
    private static final int REQ_BACKGROUND_LOCATION = 1002;
    private static final long AUTO_REFRESH_INTERVAL_MS = 5000;

    private WifiManager wifiManager;

    private Button btnScan;
    private TextView txtNetworkCount;
    private TextView txtStatus;
    private ListView listViewWifi;
    private TextView txtEmptyState;

    private WifiAdapter adapter;
    private List<ScanResult> lastScanResults = new ArrayList<>();
    private List<ScanResult> suspiciousNetworks = new ArrayList<>();

    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());

    private long lastScanTime = 0;
    private int scanCount = 0;
    private static final long SCAN_WINDOW_MS = 120000;

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver: Received SCAN_RESULTS_AVAILABLE_ACTION");

            boolean success = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false);

            if (success) {
                Log.i(TAG, "Scan completed successfully");
                showScanResults();
            } else {
                Log.e(TAG, "Scan failed or throttled by system");
                Log.i(TAG, "Using cached scan results instead");
                showScanResults();
            }
        }
    };

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (wifiManager != null) {
                Log.d(TAG, "Auto-refresh: Reading cached scan results");
                showScanResults();
            }
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity starting");

        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: Layout inflated");

        try {
            wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            Log.d(TAG, "onCreate: WifiManager initialized");
        } catch (Exception e) {
            String errorMsg = "Failed to get WifiManager: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }

        btnScan = findViewById(R.id.btnScan);
        txtNetworkCount = findViewById(R.id.txtNetworkCount);
        txtStatus = findViewById(R.id.txtStatus);
        listViewWifi = findViewById(R.id.listViewWifi);
        txtEmptyState = findViewById(R.id.txtEmptyState);
        Log.d(TAG, "onCreate: UI components initialized");

        adapter = new WifiAdapter(this, lastScanResults, suspiciousNetworks);
        listViewWifi.setAdapter(adapter);
        Log.d(TAG, "onCreate: ListView adapter set");

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Scan button clicked");
                startWifiScan();
            }
        });

        listViewWifi.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "ListView item clicked at position: " + position);

                if (position >= 0 && position < lastScanResults.size()) {
                    ScanResult sr = lastScanResults.get(position);
                    String ssid = (sr.SSID == null || sr.SSID.isEmpty())
                            ? "<Hidden Network>" : sr.SSID;

                    Log.i(TAG, "Opening tracker for: " + ssid + " (" + sr.BSSID + ")");

                    Intent intent = new Intent(MainActivity.this, TrackerActivity.class);
                    intent.putExtra("BSSID", sr.BSSID);
                    intent.putExtra("SSID", ssid);
                    startActivity(intent);
                }
            }
        });

        // Register scan receiver
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(wifiScanReceiver, filter);
            Log.d(TAG, "onCreate: BroadcastReceiver registered");
        } catch (Exception e) {
            String errorMsg = "Failed to register BroadcastReceiver: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        }

        checkAndRequestPermissions();

        Log.d(TAG, "onCreate: Completed");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed");

        checkLocationEnabled();

        Log.d(TAG, "onResume: Starting auto-refresh loop");
        autoRefreshHandler.post(autoRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity paused");
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Cleaning up");

        try {
            unregisterReceiver(wifiScanReceiver);
            Log.d(TAG, "onDestroy: BroadcastReceiver unregistered");
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: Error unregistering receiver: " + e.getMessage(), e);
        }

        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        Log.d(TAG, "onDestroy: Completed");
    }

    private void checkAndRequestPermissions() {
        Log.d(TAG, "checkAndRequestPermissions: Starting permission check");
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "checkAndRequestPermissions: Android 13+, checking NEARBY_WIFI_DEVICES");
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                Log.i(TAG, "checkAndRequestPermissions: NEARBY_WIFI_DEVICES permission needed");
            } else {
                Log.d(TAG, "checkAndRequestPermissions: NEARBY_WIFI_DEVICES already granted");
            }
        } else {
            Log.d(TAG, "checkAndRequestPermissions: Android < 13, checking ACCESS_FINE_LOCATION");
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
                Log.i(TAG, "checkAndRequestPermissions: ACCESS_FINE_LOCATION permission needed");
            } else {
                Log.d(TAG, "checkAndRequestPermissions: ACCESS_FINE_LOCATION already granted");
            }
        }

        if (!needed.isEmpty()) {
            Log.i(TAG, "checkAndRequestPermissions: Requesting " + needed.size() + " permissions");
            showPermissionExplanationDialog(needed.toArray(new String[0]));
        } else {
            Log.d(TAG, "checkAndRequestPermissions: All initial permissions granted");
            requestBackgroundLocationIfNeeded();
        }
    }

    private void showPermissionExplanationDialog(final String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location permission to scan for Wi-Fi networks.\n\n" +
                        "Why? Android requires location access to see Wi-Fi networks (SSID and signal strength).\n\n" +
                        "Your location data is NOT collected or shared.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            permissions,
                            REQ_PERMISSIONS);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(MainActivity.this,
                            "App cannot scan without location permission",
                            Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "requestBackgroundLocationIfNeeded: Checking ACCESS_BACKGROUND_LOCATION");

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                Log.i(TAG, "requestBackgroundLocationIfNeeded: Showing explanation dialog");

                new AlertDialog.Builder(this)
                        .setTitle("Background Location Needed")
                        .setMessage("For better Wi-Fi scanning, please allow location access 'All the time'.\n\n" +
                                "This enables continuous Wi-Fi network detection even when the app is minimized.\n\n" +
                                "In the next screen, select 'Allow all the time'.")
                        .setPositiveButton("Continue", (dialog, which) -> {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    REQ_BACKGROUND_LOCATION);
                        })
                        .setNegativeButton("Skip", (dialog, which) -> {
                            Toast.makeText(MainActivity.this,
                                    "Limited scanning mode. You can change this later in Settings.",
                                    Toast.LENGTH_LONG).show();
                        })
                        .show();
            } else {
                Log.d(TAG, "requestBackgroundLocationIfNeeded: Background location already granted");
            }
        }
    }

    private void checkLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager != null &&
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

        if (!isLocationEnabled) {
            Log.w(TAG, "checkLocationEnabled: Location services are disabled");
            showLocationDisabledDialog();
        } else {
            Log.d(TAG, "checkLocationEnabled: Location services are enabled");
        }
    }

    private void showLocationDisabledDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location Services Disabled")
                .setMessage("Wi-Fi scanning requires location services to be enabled.\n\n" +
                        "Please turn on Location in the next screen.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Wi-Fi scanning requires location permission set to 'Allow all the time'.\n\n" +
                        "Please:\n" +
                        "1. Tap 'Open Settings'\n" +
                        "2. Select 'Permissions'\n" +
                        "3. Select 'Location'\n" +
                        "4. Choose 'Allow all the time'\n" +
                        "5. Enable 'Use precise location'")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startWifiScan() {
        Log.d(TAG, "startWifiScan: Method called");

        if (wifiManager == null) {
            String errorMsg = "WifiManager is null";
            Log.e(TAG, "startWifiScan: " + errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager != null &&
                (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

        if (!isLocationEnabled) {
            Log.e(TAG, "startWifiScan: Location services are disabled");
            showLocationDisabledDialog();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.w(TAG, "startWifiScan: Wi-Fi is disabled, attempting to enable");
            txtStatus.setText("Enabling Wi-Fi...");

            try {
                boolean enabled = wifiManager.setWifiEnabled(true);
                Log.i(TAG, "startWifiScan: setWifiEnabled result: " + enabled);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performScan();
                    }
                }, 1000);
                return;
            } catch (Exception e) {
                String errorMsg = "Failed to enable Wi-Fi: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
        }

        performScan();
    }

    private void performScan() {
        long now = System.currentTimeMillis();

        if (now - lastScanTime > SCAN_WINDOW_MS) {
            scanCount = 0;
            Log.d(TAG, "performScan: Scan window expired, reset counter");
        }

        if (scanCount >= 4) {
            long waitTime = (SCAN_WINDOW_MS - (now - lastScanTime)) / 1000;
            String msg = "Scan throttled. Wait " + waitTime + "s. Auto-refresh continues.";
            Log.w(TAG, "performScan: " + msg);
            txtStatus.setText(msg);
            showScanResults();
            return;
        }

        try {
            boolean started = wifiManager.startScan();
            Log.i(TAG, "performScan: startScan() returned: " + started);

            if (started) {
                if (scanCount == 0) {
                    lastScanTime = now;
                }
                scanCount++;
                String message = "Scanning... (" + scanCount + "/4)";
                Log.i(TAG, "performScan: " + message);
                txtStatus.setText(message);
            } else {
                Log.w(TAG, "performScan: startScan() blocked. Using cached results.");
                txtStatus.setText("Using cached results");
            }

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "Reading scan results");
                    showScanResults();
                }
            }, 500);

        } catch (Exception e) {
            String errorMsg = "Scan error: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            txtStatus.setText(errorMsg);
            showScanResults();
        }
    }

    private void showScanResults() {
        Log.d(TAG, "showScanResults: Retrieving scan results");

        try {
            if (!wifiManager.isWifiEnabled()) {
                String errorMsg = "Wi-Fi is OFF";
                Log.e(TAG, "showScanResults: " + errorMsg);
                txtStatus.setText(errorMsg);
                txtEmptyState.setVisibility(View.VISIBLE);
                listViewWifi.setVisibility(View.GONE);
                return;
            }

            boolean hasLocationPermission = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasLocationPermission = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            } else {
                hasLocationPermission = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            }

            if (!hasLocationPermission) {
                String errorMsg = "Location permission denied";
                Log.e(TAG, "showScanResults: " + errorMsg);
                txtStatus.setText(errorMsg);
                txtEmptyState.setVisibility(View.VISIBLE);
                listViewWifi.setVisibility(View.GONE);
                showPermissionSettingsDialog();
                return;
            }

            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean isLocationEnabled = locationManager != null &&
                    (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

            if (!isLocationEnabled) {
                String errorMsg = "Location services OFF";
                Log.e(TAG, "showScanResults: " + errorMsg);
                txtStatus.setText(errorMsg);
                txtEmptyState.setVisibility(View.VISIBLE);
                listViewWifi.setVisibility(View.GONE);
                showLocationDisabledDialog();
                return;
            }

            lastScanResults.clear();
            suspiciousNetworks.clear();

            List<ScanResult> results = wifiManager.getScanResults();

            if (results == null || results.isEmpty()) {
                Log.w(TAG, "showScanResults: No networks found");
                txtStatus.setText("No networks detected");
                txtNetworkCount.setText("0 networks");
                txtEmptyState.setVisibility(View.VISIBLE);
                listViewWifi.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                return;
            }

            lastScanResults.addAll(results);
            Log.i(TAG, "showScanResults: Found " + lastScanResults.size() + " networks");

            // Identify suspicious networks
            for (ScanResult sr : lastScanResults) {
                boolean isSuspicious = false;
                String ssid = (sr.SSID == null || sr.SSID.isEmpty()) ? "<hidden>" : sr.SSID;

                if (sr.level > -50) {
                    isSuspicious = true;
                    Log.w(TAG, "SUSPICIOUS (very close): " + ssid + " | " + sr.BSSID + " | " + sr.level + " dBm");
                }

                String ssidLower = ssid.toLowerCase();
                if (ssidLower.contains("android") ||
                        ssidLower.contains("iphone") ||
                        ssidLower.contains("hotspot") ||
                        ssidLower.contains("mobile") ||
                        ssidLower.contains("sm-") ||
                        ssidLower.contains("pixel") ||
                        ssidLower.contains("xiaomi") ||
                        ssidLower.contains("huawei") ||
                        ssidLower.contains("oneplus") ||
                        ssidLower.contains("redmi") ||
                        ssidLower.contains("oppo") ||
                        ssidLower.contains("vivo") ||
                        ssidLower.contains("realme") ||
                        (ssid.equals("<hidden>") && sr.level > -60)) {
                    isSuspicious = true;
                    Log.w(TAG, "SUSPICIOUS (hotspot pattern): " + ssid + " | " + sr.BSSID + " | " + sr.level + " dBm");
                }

                if (isSuspicious) {
                    suspiciousNetworks.add(sr);
                }
            }

            // Sort by signal strength
            Collections.sort(lastScanResults, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    return Integer.compare(b.level, a.level);
                }
            });

            // Update UI
            txtNetworkCount.setText(lastScanResults.size() + " networks" +
                    (suspiciousNetworks.size() > 0 ? " (" + suspiciousNetworks.size() + " suspicious)" : ""));
            txtStatus.setText("Tap a network to track it");
            txtEmptyState.setVisibility(View.GONE);
            listViewWifi.setVisibility(View.VISIBLE);

            adapter.notifyDataSetChanged();

            if (suspiciousNetworks.size() > 0) {
                Log.w(TAG, "Found " + suspiciousNetworks.size() + " suspicious networks");
            }

        } catch (SecurityException e) {
            String errorMsg = "Security error: Permission denied";
            Log.e(TAG, errorMsg, e);
            txtStatus.setText(errorMsg);
            showPermissionSettingsDialog();
        } catch (Exception e) {
            String errorMsg = "Error: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            txtStatus.setText(errorMsg);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);

        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;

            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.i(TAG, "onRequestPermissionsResult: " + permissions[i]
                        + " = " + (granted ? "GRANTED" : "DENIED"));
                if (!granted) {
                    allGranted = false;
                }
            }

            if (!allGranted) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Wi-Fi scanning cannot work without location permission.")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            showPermissionSettingsDialog();
                        })
                        .setNegativeButton("Exit", (dialog, which) -> {
                            finish();
                        })
                        .show();
            } else {
                Log.i(TAG, "onRequestPermissionsResult: All permissions granted");
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
                requestBackgroundLocationIfNeeded();
            }
        } else if (requestCode == REQ_BACKGROUND_LOCATION) {
            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                Log.i(TAG, "onRequestPermissionsResult: Background location granted");
                Toast.makeText(this, "Full permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "onRequestPermissionsResult: Background location denied");
            }
        }
    }
}
