package com.example.wififinder;

import android.content.Context;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class WifiAdapter extends ArrayAdapter<ScanResult> {

    private Context context;
    private List<ScanResult> networks;
    private List<ScanResult> suspiciousNetworks;

    public WifiAdapter(@NonNull Context context, List<ScanResult> networks, List<ScanResult> suspiciousNetworks) {
        super(context, 0, networks);
        this.context = context;
        this.networks = networks;
        this.suspiciousNetworks = suspiciousNetworks;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.wifi_list_item, parent, false);
        }

        ScanResult sr = networks.get(position);

        TextView txtWarningIcon = convertView.findViewById(R.id.txtWarningIcon);
        TextView txtWifiIcon = convertView.findViewById(R.id.txtWifiIcon);
        TextView txtSsid = convertView.findViewById(R.id.txtSsid);
        TextView txtBssid = convertView.findViewById(R.id.txtBssid);
        TextView txtSuspiciousLabel = convertView.findViewById(R.id.txtSuspiciousLabel);
        TextView txtSignalStrength = convertView.findViewById(R.id.txtSignalStrength);

        // SSID
        String ssid = (sr.SSID == null || sr.SSID.isEmpty()) ? "<Hidden Network>" : sr.SSID;
        txtSsid.setText(ssid);

        // BSSID
        txtBssid.setText(sr.BSSID);

        // Signal Strength
        txtSignalStrength.setText(String.valueOf(sr.level));

        // Color code signal strength
        if (sr.level > -50) {
            txtSignalStrength.setTextColor(Color.parseColor("#4CAF50")); // Green - Excellent
        } else if (sr.level > -60) {
            txtSignalStrength.setTextColor(Color.parseColor("#8BC34A")); // Light Green - Good
        } else if (sr.level > -70) {
            txtSignalStrength.setTextColor(Color.parseColor("#FF9800")); // Orange - Fair
        } else {
            txtSignalStrength.setTextColor(Color.parseColor("#F44336")); // Red - Weak
        }

        // Check if suspicious
        boolean isSuspicious = false;
        for (ScanResult susp : suspiciousNetworks) {
            if (susp.BSSID.equals(sr.BSSID)) {
                isSuspicious = true;
                break;
            }
        }

        if (isSuspicious) {
            txtWarningIcon.setVisibility(View.VISIBLE);
            txtWifiIcon.setVisibility(View.GONE);
            txtSuspiciousLabel.setVisibility(View.VISIBLE);
        } else {
            txtWarningIcon.setVisibility(View.GONE);
            txtWifiIcon.setVisibility(View.VISIBLE);
            txtSuspiciousLabel.setVisibility(View.GONE);
        }

        return convertView;
    }
}
