package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.activity.ComponentActivity;

import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.*;

import java.util.Objects;


public class MainActivity extends ComponentActivity {

    public User user;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(MainActivity.this, LoginScreen.class);
        startActivity(intent);
    }
}




