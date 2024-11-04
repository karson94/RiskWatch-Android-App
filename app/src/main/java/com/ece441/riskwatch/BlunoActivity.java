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

import java.util.ArrayList;

public class BlunoActivity extends BlunoLibrary {
    private Button buttonScan;
    private Button buttonReset;
    private Button buttonSerialSend;
    private EditText serialSendText;
    private TextView serialReceivedText;
    private ListView listViewDevices;
    private ArrayAdapter<String> listAdapter;
    private boolean mScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluno);

        // Initialize UI components
        serialReceivedText = findViewById(R.id.serialReceivedText);
        serialSendText = findViewById(R.id.serialSendText);
        buttonSerialSend = findViewById(R.id.buttonSerialSend);
        listViewDevices = findViewById(R.id.listViewDevices);
        buttonScan = findViewById(R.id.buttonScan);
        buttonReset = findViewById(R.id.buttonReset);

        // Setup ListView and Adapter
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listViewDevices.setAdapter(listAdapter);

        // Setup click listeners
        buttonSerialSend.setOnClickListener(v -> {
            String message = serialSendText.getText().toString();
            if (!message.isEmpty()) {
                serialSend(message);
                serialSendText.setText("");
            }
        });

        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = (String) parent.getItemAtPosition(position);
            String[] parts = deviceInfo.split("\n");
            if (parts.length > 1) {
                String deviceAddress = parts[1];
                connect(deviceAddress);
            }
        });

        buttonScan.setOnClickListener(v -> {
            if (mScanning) {
                scanLeDevice(false);
                mScanning = false;
                buttonScan.setText(R.string.scan);
            } else {
                listAdapter.clear();
                scanLeDevice(true);
                mScanning = true;
                buttonScan.setText(R.string.scanning);
            }
        });

        buttonReset.setOnClickListener(v -> resetBluetooth());
    }

    private void resetBluetooth() {
        if (mScanning) {
            scanLeDevice(false);
            mScanning = false;
        }

        if (mConnectionState == connectionStateEnum.isConnected) {
            disconnect();
        }

        listAdapter.clear();
        serialReceivedText.setText("");
        buttonScan.setText(R.string.scan);
        mConnectionState = connectionStateEnum.isToScan;
        Toast.makeText(this, "Bluetooth reset", Toast.LENGTH_SHORT).show();
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
            serialReceivedText.append(theString + "\n");
            ((ScrollView) serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        runOnUiThread(() -> {
            String deviceInfo = device.getName() + "\n" + device.getAddress();
            if (listAdapter.getPosition(deviceInfo) == -1) {
                listAdapter.add(deviceInfo);
                listAdapter.notifyDataSetChanged();
            }
        });
    }
}