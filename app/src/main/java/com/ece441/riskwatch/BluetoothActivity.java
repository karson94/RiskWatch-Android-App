package com.ece441.riskwatch;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

import com.google.firebase.auth.FirebaseAuth;

public class BluetoothActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothActivity";
    private static final long SCAN_PERIOD = 5000; // 5 seconds
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    private BluetoothAdapter bluetoothAdapter;
    private boolean isScanning = false;
    private Handler handler = new Handler();
    private DeviceListAdapter deviceListAdapter;
    private BluetoothGatt bluetoothGatt;

    // UUIDs matching the ESP32
    private static final UUID SERVICE_UUID = UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");

    private ProgressDialog progressDialog;

    private EditText editData;
    private Button btnSend;
    private Button btnScan;

    private TextView logTextView;
    private ScrollView logScrollView;
    private TextView connectedDeviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        ListView listView = findViewById(R.id.device_list);
        deviceListAdapter = new DeviceListAdapter(this);
        listView.setAdapter(deviceListAdapter);

        editData = findViewById(R.id.edit_data);
        btnSend = findViewById(R.id.btn_send);
        btnSend.setEnabled(false);

        btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> {
            if (checkPermissionsAndBluetooth()) {
                deviceListAdapter.clear(); // Clear previous devices
                scanLeDevice(true);
            }
        });

        btnSend.setOnClickListener(v -> sendData());

        logTextView = findViewById(R.id.log_text_view);
        logScrollView = findViewById(R.id.log_scroll_view);
        connectedDeviceInfo = findViewById(R.id.connected_device_info);

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private boolean checkPermissionsAndBluetooth() {
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION);
            return false;
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    // Handle permission results
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, but let user initiate scan
                btnScan.setEnabled(true);
            } else {
                Toast.makeText(this, "Location permission required for Bluetooth scanning", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // Handle Bluetooth enable result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth enabled, but let user initiate scan
                btnScan.setEnabled(true);
            } else {
                Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Start scanning
            handler.postDelayed(() -> {
                isScanning = false;
                bluetoothAdapter.stopLeScan(leScanCallback);
                btnScan.setEnabled(true);
                Toast.makeText(BluetoothActivity.this, "Scan complete", Toast.LENGTH_SHORT).show();
            }, SCAN_PERIOD);

            isScanning = true;
            btnScan.setEnabled(false);
            Toast.makeText(this, "Starting scan...", Toast.LENGTH_SHORT).show();
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            // Stop scanning
            isScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
            btnScan.setEnabled(true);
        }
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(() -> {
                deviceListAdapter.addDevice(device);
            });
        }
    };

    // Handle device selection from the list
    public void onDeviceSelected(BluetoothDevice device) {
        scanLeDevice(false);
        connectToDevice(device);
    }

    private void connectToDevice(BluetoothDevice device) {
        if (bluetoothGatt == null) {
            progressDialog = ProgressDialog.show(this, "Connecting", "Connecting to " + device.getName() + "...");
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            runOnUiThread(() -> {
                if (newState == BluetoothAdapter.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.");
                    // Notify user
                    Toast.makeText(BluetoothActivity.this, "Connected to " + gatt.getDevice().getName(), Toast.LENGTH_SHORT).show();
                    gatt.discoverServices();
                } else if (newState == BluetoothAdapter.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.");
                    runOnUiThread(() -> {
                        Toast.makeText(BluetoothActivity.this, "Disconnected from " + gatt.getDevice().getName(), Toast.LENGTH_SHORT).show();
                        btnSend.setEnabled(false);
                        connectedDeviceInfo.setVisibility(View.GONE);
                        logScrollView.setVisibility(View.GONE);
                        deviceListAdapter.clear();
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        // Clean up gatt
                        if (bluetoothGatt != null) {
                            bluetoothGatt.close();
                            bluetoothGatt = null;
                        }
                    });
                }
            });
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        // Enable notifications
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                        runOnUiThread(() -> {
                            btnSend.setEnabled(true);
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            connectedDeviceInfo.setText("Connected to: " + gatt.getDevice().getName());
                            connectedDeviceInfo.setVisibility(View.VISIBLE);
                            logScrollView.setVisibility(View.VISIBLE);
                            deviceListAdapter.clear();
                            Toast.makeText(BluetoothActivity.this, "Connected and ready", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        Log.e(TAG, "Characteristic not found");
                        runOnUiThread(() -> {
                            Toast.makeText(BluetoothActivity.this, "Characteristic not found", Toast.LENGTH_SHORT).show();
                            if (progressDialog != null && progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "Service not found");
                    runOnUiThread(() -> {
                        Toast.makeText(BluetoothActivity.this, "Service not found", Toast.LENGTH_SHORT).show();
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    });
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                runOnUiThread(() -> {
                    Toast.makeText(BluetoothActivity.this, "Service discovery failed", Toast.LENGTH_SHORT).show();
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for(byte byteChar : data) {
                        stringBuilder.append((char)byteChar);
                    }
                    final String receivedData = stringBuilder.toString();
                    
                    // Log the raw received data
                    Log.d(TAG, "Raw data received from device: " + receivedData);
                    
                    try {
                        // Parse the comma-separated data
                        String[] parts = receivedData.split(",");
                        if (parts.length == 6) {
                            String time = parts[0].trim();
                            String date = parts[1].trim();
                            int heartRate = Integer.parseInt(parts[2].trim());
                            int deltaHeartRate = Integer.parseInt(parts[3].trim());
                            double impactSeverity = Double.parseDouble(parts[4].trim());
                            String fallDirection = parts[5].trim();

                            // Log the parsed data
                            Log.d(TAG, String.format("Parsed Data - Time: %s, Date: %s, HR: %d, ΔHR: %d, Impact: %.1f, Direction: %s",
                                    time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));

                            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            
                            runOnUiThread(() -> {
                                ((HomeActivity) getApplicationContext()).addFallEntry(
                                    userId, time, date, deltaHeartRate, heartRate, fallDirection, impactSeverity);
                                appendLog("Fall detected and logged to database");
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing data: " + e.getMessage());
                        Log.d(TAG, "Failed to parse data: " + receivedData);
                    }
                    
                    runOnUiThread(() -> {
                        appendLog("Received: " + receivedData);
                    });
                }
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            runOnUiThread(() -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    appendLog("Sent: " + characteristic.getStringValue(0));
                } else {
                    appendLog("Failed to send data");
                }
            });
        }
    };

    private void sendData() {
        String dataToSend = editData.getText().toString().trim();
        if (dataToSend.isEmpty()) {
            Toast.makeText(this, "Enter data to send", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothGatt == null) {
            Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service == null) {
            Toast.makeText(this, "Service not found", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic == null) {
            Toast.makeText(this, "Characteristic not found", Toast.LENGTH_SHORT).show();
            return;
        }

        characteristic.setValue(dataToSend);
        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
        if (success) {
            appendLog("Sending: " + dataToSend);
        } else {
            appendLog("Failed to send data");
        }
    }

    private void appendLog(String message) {
        String currentLog = logTextView.getText().toString();
        currentLog += message + "\n";
        logTextView.setText(currentLog);
        // Scroll to the bottom
        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
    }
}