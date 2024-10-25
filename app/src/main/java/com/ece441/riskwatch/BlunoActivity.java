package com.ece441.riskwatch;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class BlunoActivity extends BlunoLibrary {
    private Button buttonScan;
    private Button buttonSerialSend;
    private EditText serialSendText;
    private TextView serialReceivedText;

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

        buttonScan = findViewById(R.id.buttonScan);
        buttonScan.setOnClickListener(v -> buttonScanOnClickProcess());
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("BlunoActivity onResume");
        // Any additional BlunoActivity-specific onResume code can go here
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {
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
    }

    @Override
    public void onSerialReceived(String theString) {
        serialReceivedText.append(theString);
        ((ScrollView) serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
    }
}
