package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeRequest;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.workflow.RequestContext;
import com.amazon.identity.auth.device.api.authorization.AuthorizeListener;
import com.amazon.identity.auth.device.api.authorization.ProfileScope;
import com.amazon.identity.auth.device.api.authorization.AuthCancellation;

public class LoginScreen extends AppCompatActivity {

    public User user;
    private FirebaseAuth mAuth;
    private RequestContext requestContext; // Amazon Login context

    // Register for the activity result launchers
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (isGranted.get(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Permission granted
                }
            });

    private final ActivityResultLauncher<String[]> requestBluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (isGranted.get(Manifest.permission.BLUETOOTH_CONNECT) &&
                        isGranted.get(Manifest.permission.BLUETOOTH_SCAN)) {
                    // Permissions granted
                }
            });

    private final ActivityResultLauncher<String[]> requestLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (isGranted.get(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        isGranted.get(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    // Location permissions granted
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_screen);
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize Amazon Login
        requestContext = RequestContext.create(this);
        requestContext.registerListener(new AuthorizeListener() {
            @Override
            public void onSuccess(AuthorizeResult result) {
                // Handle successful Amazon login
                runOnUiThread(() -> {
                    Log.d(TAG, "Amazon Login successful");
                    user = new User(result.getUser().getUserId());
                    Intent intent = new Intent(LoginScreen.this, HomeActivity.class);
                    intent.putExtra("user", result.getUser().getUserName());
                    intent.putExtra("amazon_user_id", result.getUser().getUserId());
                    startActivity(intent);
                });
            }

            @Override
            public void onError(AuthError ae) {
                // Handle Amazon login error
                runOnUiThread(() -> {
                    Log.e(TAG, "Amazon Login error: " + ae.getMessage());
                    Toast.makeText(LoginScreen.this, 
                        "Amazon Login Failed: " + ae.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onCancel(AuthCancellation cancellation) {
                // Handle when user cancels the login process
                runOnUiThread(() -> {
                    Log.d(TAG, "Amazon Login cancelled by user");
                    Toast.makeText(LoginScreen.this, 
                        "Login cancelled", 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        // Check all required permissions
        checkNotificationPermission();
        checkBluetoothPermissions();
        checkLocationPermissions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        requestContext.onResume();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
    }

    // Permission check methods
    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            });
        }
    }

    public void login(View view) {
        EditText editUsername = findViewById(R.id.userNameInput);
        EditText editPassword = findViewById(R.id.passwordInput);
        String input = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter a username or email", Toast.LENGTH_SHORT).show();
            return;
        }

        // If password is empty, treat it as a guest login
        if (password.isEmpty()) {
            mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Intent intent = new Intent(LoginScreen.this, HomeActivity.class);
                    intent.putExtra("user", "Guest");  // Always use "Guest" for anonymous users
                    intent.putExtra("isGuest", true);
                    startActivity(intent);
                } else {
                    Toast.makeText(LoginScreen.this, "Guest login failed.",
                            Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        // Regular email/password login
        mAuth.signInWithEmailAndPassword(input, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser fireUser = mAuth.getCurrentUser();
                        if (fireUser != null) {
                            Intent intent = new Intent(LoginScreen.this, HomeActivity.class);
                            intent.putExtra("user", fireUser.getDisplayName());
                            Log.d(TAG, "MAIN USERNAME " + fireUser.getDisplayName());
                            startActivity(intent);
                        }
                    } else {
                        Toast.makeText(LoginScreen.this, "Authentication failed. " + 
                            task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void goToRegistration(View view) {
        Intent intent = new Intent(this, Register.class);
        startActivity(intent);
    }

    public void guestLogin(View view) {
        Log.d(TAG, "guestLogin() started");
        mAuth.signInAnonymously()
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInAnonymously:success");
                    Intent intent = new Intent(LoginScreen.this, HomeActivity.class);
                    intent.putExtra("user", "Guest");
                    intent.putExtra("isGuest", true);
                    startActivity(intent);
                } else {
                    Log.w(TAG, "signInAnonymously:failure", task.getException());
                    Toast.makeText(LoginScreen.this, "Anonymous authentication failed.",
                            Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    // Amazon Login methods - simplified for testing
    public void loginWithAmazon(View view) {
        Log.d(TAG, "Amazon Login initiated");
        AuthorizationManager.authorize(
            new AuthorizeRequest.Builder(requestContext)
                .addScopes(ProfileScope.profile())
                .build()
        );
    }
}
