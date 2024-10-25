package com.ece441.riskwatch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import android.util.Log;
import android.os.Handler;
import java.util.List;
import java.util.ArrayList;
import android.bluetooth.BluetoothGattService;
import java.util.UUID;
import android.os.Build;

public class BlunoActivity extends BlunoLibrary {
    private static final String TAG = BlunoActivity.class.getSimpleName();
    private Button buttonScan;
    private Button buttonReload;
    private ListView listViewDevices;
    private TextView textViewData;
    private TextView textViewReceivedData;
    private ArrayAdapter<String> listAdapter;
    private ArrayList<String> deviceList = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothLeService mBluetoothLeService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnectionState = connectionStateEnum.isConnected;
                runOnUiThread(() -> { 
                    onConectionStateChange(mConnectionState);
                    Log.i(TAG, "Connected to GATT server.");
                    Toast.makeText(BlunoActivity.this, "Connected to GATT server.", Toast.LENGTH_SHORT).show();
                });
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnectionState = connectionStateEnum.isToScan;
                runOnUiThread(() -> { 
                    onConectionStateChange(mConnectionState);
                    Log.i(TAG, "Disconnected from GATT server.");
                    Toast.makeText(BlunoActivity.this, "Disconnected from GATT server.", Toast.LENGTH_SHORT).show();
                });
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Service discovery is complete!
                // You can now get the discovered services:
                List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
                displayGattServices(gattServices); 

                // Update UI to reflect successful connection (e.g., change button text, enable other UI elements)
                runOnUiThread(() -> {
                    buttonScan.setText("Connected"); 
                    // ... other UI updates
                });
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static final int PERMISSION_REQUEST_BLUETOOTH = 2; // Unique integer value

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluno);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        setMainContext(this);
        onCreateProcess();

        buttonScan = findViewById(R.id.buttonScan);
        buttonReload = findViewById(R.id.buttonReload);
        listViewDevices = findViewById(R.id.listViewDevices);
        textViewData = findViewById(R.id.textViewData);
        textViewReceivedData = findViewById(R.id.textViewReceivedData);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listViewDevices.setAdapter(listAdapter);

        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonScanOnClickProcess();
            }
        });

        buttonReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetConnection();
            }
        });

        listViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mConnectionState == connectionStateEnum.isScanning) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }

                if (devices.size() > position) {
                    BluetoothDevice device = devices.get(position);
                    if (device != null && mBluetoothLeService != null) {
                        mBluetoothLeService.connect(device.getAddress());
                        Toast.makeText(BlunoActivity.this, "Attempting to connect...", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mConnectionState != connectionStateEnum.isConnected) {
                                    onConectionStateChange(connectionStateEnum.isToScan);
                                    Toast.makeText(BlunoActivity.this, "Connection failed.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }, 5000);
                    } else {
                        Toast.makeText(BlunoActivity.this, "Bluetooth service not initialized", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Request Bluetooth permissions at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                        PERMISSION_REQUEST_BLUETOOTH);
            } 
        } else {
            // For older Android versions, ensure BLUETOOTH and BLUETOOTH_ADMIN are declared in the manifest
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
        onResumeProcess();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        onPauseProcess();
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopProcess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        onDestroyProcess();
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {
        switch (theConnectionState) {
            case isConnected:
                buttonScan.setText("Connected");
                buttonReload.setVisibility(View.VISIBLE);
                break;
            case isToScan:
                buttonScan.setText("Disconnected");
                buttonReload.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    @Override
    public void onSerialReceived(String theString) {
        textViewReceivedData.append(theString + "\n");
    }

    private void buttonScanOnClickProcess() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_ENABLE_BT);
            return;
        }

        switch (mConnectionState) {
            case isNull:
            case isToScan:
                mConnectionState = connectionStateEnum.isScanning;
                buttonScan.setText("Scanning");
                scanLeDevice();
                break;
            case isScanning:
                mConnectionState = connectionStateEnum.isToScan;
                buttonScan.setText("Scan");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                break;
            case isConnected:
                mBluetoothLeService.disconnect();
                mConnectionState = connectionStateEnum.isToScan;
                buttonScan.setText("Disconnected");
                break;
            default:
                break;
        }
    }

    private void resetConnection() {
        mBluetoothLeService.disconnect();
        mConnectionState = connectionStateEnum.isToScan;
        buttonScan.setText("Scan");
        listViewDevices.setVisibility(View.VISIBLE);
        deviceList.clear();
        devices.clear();
        listAdapter.notifyDataSetChanged();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!devices.contains(device)) {
                        devices.add(device);
                        deviceList.add(device.getName() + "\n" + device.getAddress());
                        listAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    @Override
    protected void onDeviceDiscovered(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            deviceList.add(device.getName() + "\n" + device.getAddress());
            listAdapter.notifyDataSetChanged();
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            Log.w(TAG, "onServicesDiscovered received: null");
            return;
        }
        for (BluetoothGattService gattService : gattServices) {
            UUID serviceUUID = gattService.getUuid();
            Log.d(TAG, "Service discovered: " + serviceUUID);
            // Implement your logic to handle each service
        }
    }

    private void attemptConnection(final BluetoothDevice device) {
        if (device != null && mBluetoothLeService != null) {
            mBluetoothLeService.connect(device.getAddress());
            Toast.makeText(BlunoActivity.this, "Attempting to connect...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(BlunoActivity.this, "Bluetooth service not initialized", Toast.LENGTH_SHORT).show();
        }
    }
}
