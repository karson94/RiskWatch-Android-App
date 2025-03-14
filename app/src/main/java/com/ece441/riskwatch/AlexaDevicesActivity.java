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

import java.util.ArrayList;
import java.util.List;

public class AlexaDevicesActivity extends AppCompatActivity {
    private static final String TAG = "AlexaDevicesActivity";
    
    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private List<AmazonAlexaHelper.AlexaDevice> deviceList;
    private AmazonAlexaHelper alexaHelper;
    private String amazonAccessToken;
    
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
        
        // Initialize Alexa helper
        alexaHelper = new AmazonAlexaHelper(this);
        
        // Fetch Alexa devices
        fetchAlexaDevices();
    }
    
    private void fetchAlexaDevices() {
        if (amazonAccessToken == null || amazonAccessToken.isEmpty()) {
            Toast.makeText(this, "Amazon access token is missing", Toast.LENGTH_SHORT).show();
            return;
        }
        
        alexaHelper.getAlexaDevices(amazonAccessToken, new AmazonAlexaHelper.AlexaDevicesCallback() {
            @Override
            public void onDevicesRetrieved(List<AmazonAlexaHelper.AlexaDevice> devices) {
                runOnUiThread(() -> {
                    deviceList.clear();
                    deviceList.addAll(devices);
                    adapter.notifyDataSetChanged();
                    
                    if (devices.isEmpty()) {
                        Toast.makeText(AlexaDevicesActivity.this, "No Alexa devices found", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Error retrieving Alexa devices: " + errorMessage);
                    Toast.makeText(AlexaDevicesActivity.this, "Error retrieving Alexa devices: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    public void goBack(View view) {
        finish();
    }
    
    /**
     * Adapter for displaying Alexa devices in a RecyclerView
     */
    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private final Context context;
        private final List<AmazonAlexaHelper.AlexaDevice> devices;
        
        public DeviceAdapter(Context context, List<AmazonAlexaHelper.AlexaDevice> devices) {
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
            AmazonAlexaHelper.AlexaDevice device = devices.get(position);
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