package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

public class LoginScreen extends AppCompatActivity {

    public User user;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_screen);
        mAuth = FirebaseAuth.getInstance();
    }

    public void login(View view){

        EditText editUsername = findViewById(R.id.userNameInput);
        EditText editPassword = findViewById(R.id.passwordInput);
        String email = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        FirebaseUser fireUser = mAuth.getCurrentUser();
                        if (fireUser != null) {
                            Intent intent = new Intent(LoginScreen.this, HomeActivity.class);
                            intent.putExtra("user", fireUser.getDisplayName());
                            Log.d(TAG, "MAIN USERNAME " + fireUser.getDisplayName());
                            startActivity(intent);
                        }
                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(LoginScreen.this, "Authentication failed. " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

//        user = new User(edit.getText().toString());
//
//        Intent intent = new Intent(this, HomeActivity.class);
//        intent.putExtra("user", user.getUserName());
//
//        Log.d(TAG, "MAIN USERNAME" + user.getUserName());
//
//        startActivity(intent);
    }

    public void goToRegistration(View view) {
        Intent intent = new Intent(this, Register.class);
        startActivity(intent);
    }

}




