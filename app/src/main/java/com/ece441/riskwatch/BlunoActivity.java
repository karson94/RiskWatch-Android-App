package com.ece441.riskwatch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
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

import java.util.ArrayList;

public class BlunoActivity extends BlunoLibrary {
    private Button buttonScan;
    private ListView listViewDevices;
    private TextView textViewData;
    private ArrayAdapter<String> listAdapter;
    private ArrayList<String> deviceList = new ArrayList<>();
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluno);

        setMainContext(this);
        onCreateProcess();

        buttonScan = findViewById(R.id.buttonScan);
        listViewDevices = findViewById(R.id.listViewDevices);
        textViewData = findViewById(R.id.textViewData);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listViewDevices.setAdapter(listAdapter);

        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonScanOnClickProcess();
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
                    if (device != null) {
                        connect(device.getAddress());
                    }
                }
            }
        });

        // Initialize Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumeProcess();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        onDestroyProcess();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Handle activity result if needed
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {
        switch (theConnectionState) {
            case isConnected:
                buttonScan.setText("Connected");
                break;
            case isConnecting:
                buttonScan.setText("Connecting");
                break;
            case isToScan:
                buttonScan.setText("Scan");
                break;
            case isScanning:
                buttonScan.setText("Scanning");
                break;
            case isDisconnecting:
                buttonScan.setText("isDisconnecting");
                break;
            default:
                break;
        }
    }

    @Override
    public void onSerialReceived(String theString) {
        textViewData.append(theString);
    }

    private void buttonScanOnClickProcess() {
        switch (mConnectionState) {
            case isNull:
                mConnectionState = connectionStateEnum.isScanning;
                buttonScan.setText("Scanning");
                scanLeDevice();
                break;
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
            case isConnecting:
                break;
            case isConnected:
                mBluetoothLeService.disconnect();
                mConnectionState = connectionStateEnum.isDisconnecting;
                buttonScan.setText("isDisconnecting");
                break;
            case isDisconnecting:
                break;
        }
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
}
