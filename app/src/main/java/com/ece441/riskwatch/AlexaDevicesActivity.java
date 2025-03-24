package com.ece441.riskwatch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AlexaDevicesActivity extends AppCompatActivity {
    private static final String TAG = "AlexaDevicesActivity";
    private static final String ALEXA_DEVICES_API_URL = "https://api.amazonalexa.com/v1/devices";
    
    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private List<AlexaDevice> deviceList;
    private String amazonAccessToken;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alexa_devices);
        
        // Initialize views
        recyclerView = findViewById(R.id.deviceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Initialize device list and adapter
        deviceList = new ArrayList<>();
        adapter = new DeviceAdapter(this, deviceList);
        recyclerView.setAdapter(adapter);
        
        // Get Amazon access token from intent
        amazonAccessToken = getIntent().getStringExtra("amazon_access_token");
        
        // Fetch Alexa devices
        fetchAlexaDevices();
    }
    
    private void fetchAlexaDevices() {
        if (amazonAccessToken == null || amazonAccessToken.isEmpty()) {
            Toast.makeText(this, "Amazon access token is missing", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executor.execute(() -> {
            try {
                URL url = new URL(ALEXA_DEVICES_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + amazonAccessToken);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    List<AlexaDevice> devices = parseDevicesResponse(response.toString());
                    runOnUiThread(() -> {
                        deviceList.clear();
                        deviceList.addAll(devices);
                        adapter.notifyDataSetChanged();
                        
                        if (devices.isEmpty()) {
                            Toast.makeText(AlexaDevicesActivity.this, "No Alexa devices found", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Error retrieving Alexa devices: " + responseCode);
                        Toast.makeText(AlexaDevicesActivity.this, "Error retrieving Alexa devices: " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error retrieving Alexa devices", e);
                    Toast.makeText(AlexaDevicesActivity.this, "Error retrieving Alexa devices: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private List<AlexaDevice> parseDevicesResponse(String response) {
        List<AlexaDevice> devices = new ArrayList<>();
        
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray devicesArray = jsonResponse.getJSONArray("devices");
            
            for (int i = 0; i < devicesArray.length(); i++) {
                JSONObject deviceObj = devicesArray.getJSONObject(i);
                String deviceId = deviceObj.getString("deviceId");
                String deviceName = deviceObj.getString("deviceName");
                String deviceType = deviceObj.getString("deviceType");
                
                devices.add(new AlexaDevice(deviceId, deviceName, deviceType));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Alexa devices response", e);
        }
        
        return devices;
    }
    
    public void goBack(View view) {
        finish();
    }
    
    /**
     * Class representing an Alexa device
     */
    public static class AlexaDevice {
        private final String deviceId;
        private final String deviceName;
        private final String deviceType;
        
        public AlexaDevice(String deviceId, String deviceName, String deviceType) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public String getDeviceName() {
            return deviceName;
        }
        
        public String getDeviceType() {
            return deviceType;
        }
    }
    
    /**
     * Adapter for displaying Alexa devices in a RecyclerView
     */
    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private final Context context;
        private final List<AlexaDevice> devices;
        
        public DeviceAdapter(Context context, List<AlexaDevice> devices) {
            this.context = context;
            this.devices = devices;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AlexaDevice device = devices.get(position);
            holder.text1.setText(device.getDeviceName());
            holder.text2.setText(device.getDeviceType());
        }
        
        @Override
        public int getItemCount() {
            return devices.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;
            
            public ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
} 