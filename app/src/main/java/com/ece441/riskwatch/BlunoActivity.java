package com.ece441.riskwatch;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class BlunoActivity extends BlunoLibrary {
    private Button buttonScan;
    private Button buttonReset;
    private Button buttonSerialSend;
    private EditText serialSendText;
    private TextView serialReceivedText;
    private ListView listViewDevices;
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluno);

        request(1000, new OnPermissionsResult() {
            @Override
            public void OnSuccess() {
                Toast.makeText(BlunoActivity.this, R.string.permissions_granted, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void OnFail(List<String> noPermissions) {
                Toast.makeText(BlunoActivity.this, R.string.permissions_denied, Toast.LENGTH_SHORT).show();
            }
        });

        serialBegin(115200);

        serialReceivedText = findViewById(R.id.serialReceivedText);
        serialSendText = findViewById(R.id.serialSendText);

        buttonSerialSend = findViewById(R.id.buttonSerialSend);
        buttonSerialSend.setOnClickListener(v -> serialSend(serialSendText.getText().toString()));

        listViewDevices = findViewById(R.id.listViewDevices);
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listViewDevices.setAdapter(listAdapter);
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = (String) parent.getItemAtPosition(position);
            String[] parts = deviceInfo.split("\n");
            if (parts.length > 1) {
                String deviceAddress = parts[1];
                connect(deviceAddress);
            }
        });

        buttonScan = findViewById(R.id.buttonScan);
        buttonScan.setOnClickListener(v -> {
            if (isScanning()) {
                scanLeDevice(false);
            } else {
                listAdapter.clear();
                scanLeDevice(true);
            }
        });

        buttonReset = findViewById(R.id.buttonReset);
        buttonReset.setOnClickListener(v -> resetBluetooth());
    }

    private void resetBluetooth() {
        // Stop scanning if scanning
        if (isScanning()) {
            scanLeDevice(false);
        }

        // Disconnect if connected
        if (mConnectionState == connectionStateEnum.isConnected) {
            serialSend("AT+DISC");  // Send disconnect command to Bluno
            mBluetoothLeService.disconnect();
        }

        // Clear the device list
        listAdapter.clear();
        listAdapter.notifyDataSetChanged();

        // Clear the received text
        serialReceivedText.setText("");

        // Reset the scan button text
        buttonScan.setText(R.string.scan);

        // Reset the connection state
        mConnectionState = connectionStateEnum.isToScan;

        // Optionally, you can add any other reset logic here

        Toast.makeText(this, "Bluetooth reset", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("BlunoActivity onResume");
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {
        runOnUiThread(() -> {
            switch (theConnectionState) {
                case isConnected:
                    buttonScan.setText(R.string.connected);
                    break;
                case isConnecting:
                    buttonScan.setText(R.string.connecting);
                    break;
                case isToScan:
                    buttonScan.setText(R.string.scan);
                    break;
                case isScanning:
                    buttonScan.setText(R.string.scanning);
                    break;
                case isDisconnecting:
                    buttonScan.setText(R.string.disconnecting);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onSerialReceived(String theString) {
        runOnUiThread(() -> {
            serialReceivedText.append(theString);
            ((ScrollView) serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        runOnUiThread(() -> {
            if (checkBluetoothPermissions()) {
                String deviceInfo = device.getName() + "\n" + device.getAddress();
                if (listAdapter.getPosition(deviceInfo) == -1) {
                    listAdapter.add(deviceInfo);
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private boolean isScanning() {
        return mScanning;
    }
}
