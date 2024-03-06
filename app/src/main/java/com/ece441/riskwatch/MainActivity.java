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

import androidx.core.content.ContextCompat;


public class MainActivity extends ComponentActivity {

    public User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void login(View view){

        EditText edit = findViewById(R.id.userNameInput);
        user = new User(edit.getText().toString());

        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("user", user.getUserName());

        Log.d(TAG, "MAIN USERNAME" + user.getUserName());

        startActivity(intent);
    }
}




