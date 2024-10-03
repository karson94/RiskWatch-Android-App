package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.ComponentActivity;

import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.*;

import java.util.Objects;


public class MainActivity extends ComponentActivity {

    public User user;
    private FirebaseAuth mAuth;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;

    // Register for the activity result
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (isGranted.get(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Permission granted, proceed with your app logic
                } else {
                    // Permission denied, handle accordingly (e.g., show a message)
                }
            });
    private final ActivityResultLauncher<String[]> requestBluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (isGranted.get(Manifest.permission.BLUETOOTH_CONNECT) &&
                        isGranted.get(Manifest.permission.BLUETOOTH_SCAN)) {
                    // Permissions granted, proceed with Bluetooth functionality
                } else {
                    // Permissions denied, handle accordingly
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(MainActivity.this, LoginScreen.class);
        startActivity(intent);
        checkNotificationPermission();
        checkBluetoothPermissions();
    }

    // Request notification permissions
    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Request the permission using the ActivityResultLauncher
            requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        } else {
            // Permission already granted, proceed with your app logic
        }
    }

    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            });
        } else {
            // Permissions already granted
        }
    }
}





