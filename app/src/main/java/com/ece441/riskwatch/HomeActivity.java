package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

// Firebase imports
import com.google.firebase.database.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.location.Location;
import android.location.LocationManager;
import android.location.Geocoder;
import android.location.Address;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import android.view.LayoutInflater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.bumptech.glide.Glide;
import android.graphics.Bitmap;

import android.os.AsyncTask;
import android.net.Uri;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;

import android.content.ActivityNotFoundException;
import com.google.firebase.database.ChildEventListener;
import androidx.annotation.Nullable;

public class HomeActivity extends AppCompatActivity {

    private static final ArrayList<Fall> fallArrayList = new ArrayList<>();
    private RecyclerView recyclerView;
    private static FallItemAdapter fallItemAdapter;

    User currentUser;

    private LocationManager locationManager;
    private Geocoder geocoder;

    private static String savedUsername = null;
    private String amazonUserId = null; // Added to store Amazon User ID

    // --- Proactive Events Configuration ---
    // Removed static final constants for credentials
    private String alexaSkillClientId = null; // Field to hold loaded Client ID
    private String alexaSkillClientSecret = null; // Field to hold loaded Client Secret
    private String proactiveEventsAccessToken = null;
    private long tokenExpiryTime = 0;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String PROACTIVE_EVENTS_API_ENDPOINT = "https://api.amazonalexa.com/v1/proactiveEvents/stages/development";
    // --- End Proactive Events Configuration ---

    private static final String ALEXA_LOG_TAG = TAG; // Use the existing TAG for consistency or define a new one if preferred

    // --- Alexa Configuration ---
    // !!! REPLACE THIS WITH YOUR ACTUAL ALEXA SKILL ID !!!
    // Updated with the provided Skill ID
    private static final String ALEXA_SKILL_ID = "amzn1.ask.skill.fc8c0677-659c-4f9e-b29f-bfa34c7eeefd";
    // --- End Alexa Configuration ---

    // Store the primary user ID for this session (could be Firebase UID or Amazon ID)
    private String primaryUserId = null; 
    private String userDisplayName = "Guest";
    private boolean isGuestUser = true; // Assume guest unless logged in

    private DatabaseReference fallsRefListener = null; // Reference for the listener
    private ChildEventListener fallChildEventListener = null; // The listener itself

    // --- Background Thread Control ---
    private volatile boolean isRunning = true; // Flag to control the background thread
    private Thread backgroundReaderThread = null;
    // --- End Background Thread Control ---

    // --- Startup Delay --- 
    private long activityStartTimeMillis = 0;
    private static final long STARTUP_DELAY_MS = 5000; // 5 seconds
    // --- End Startup Delay --- 

    private String googleMapsApiKey = null; // To store the loaded Maps API key

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        activityStartTimeMillis = System.currentTimeMillis(); // Record start time

        loadMapsApiKey(); // Load the Maps API key
        loadAlexaCredentials(); // Load Alexa credentials

        // --- Location Permission Check (moved up) ---
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                },
                1);
        }
        // --- End Location Permission Check ---

        // --- Process Intent and Determine User ID ---
        Intent receivedIntent = getIntent();
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser(); // Still useful for Guest/Email logins

        if (receivedIntent != null) {
            isGuestUser = receivedIntent.getBooleanExtra("isGuest", false);
            amazonUserId = receivedIntent.getStringExtra("amazon_user_id"); // Keep this for Alexa logic
            userDisplayName = receivedIntent.getStringExtra("user");
            
            if (amazonUserId != null && !isGuestUser) {
                 // Amazon Login flow (manual linking)
                 // Sanitize the Amazon ID to make it a valid Firebase key
                 String sanitizedAmazonId = amazonUserId.replace('.', '_').replace('#', '_').replace('$', '_').replace('[', '_').replace(']', '_');
                 primaryUserId = sanitizedAmazonId; // Use SANITIZED Amazon ID as the primary key
                 Log.d(TAG, "Using SANITIZED Amazon User ID as primary key: " + primaryUserId);
            } else if (!isGuestUser && fireUser != null) {
                 // Email/Password Login flow
                 primaryUserId = fireUser.getUid(); // Use Firebase UID as primary key
                 if (userDisplayName == null) userDisplayName = fireUser.getDisplayName(); // Use Firebase display name if not passed
                 Log.d(TAG, "Using Firebase UID as primary key: " + primaryUserId);
            } else if (isGuestUser && fireUser != null) {
                 // Guest Login flow (Firebase Anonymous)
                 primaryUserId = fireUser.getUid(); // Use Firebase Anonymous UID as primary key
                 userDisplayName = "Guest"; // Ensure display name is Guest
                 Log.d(TAG, "Using Anonymous Firebase UID as primary key: " + primaryUserId);
            } else {
                 // Error case or unexpected state
                 Log.e(TAG, "Could not determine valid user ID. isGuest: " + isGuestUser + ", amazonUserId: " + amazonUserId + ", fireUser: " + (fireUser != null));
                 Toast.makeText(this, "Error identifying user.", Toast.LENGTH_LONG).show();
                 // Consider finishing activity or redirecting to login
                 finish(); 
                 return; // Prevent rest of onCreate
            }
            
            // Update currentUser object (optional, if still used elsewhere)
            currentUser = new User(userDisplayName); // Use the determined display name
            Log.d(TAG, "HOME USERNAME set to: " + currentUser.getUserName());
            Log.d(TAG, "isGuest flag: " + isGuestUser);
            
            // Existing Alexa token logic (uses amazonUserId if present)
            if (amazonUserId != null) {
                Log.d(TAG, "Amazon User ID provided: " + amazonUserId);
                if (alexaSkillClientId != null && alexaSkillClientSecret != null) {
                    getProactiveEventsAccessToken();
                } else {
                    Log.e(TAG, "Alexa credentials not loaded, cannot get proactive events token.");
                    // Toast.makeText(this, "Error: Alexa credentials missing.", Toast.LENGTH_LONG).show(); // Less intrusive logging
                }
            }
            
            // Removed the assertion/check for fireUser here as primaryUserId handles identification

        } else {
            Log.e(TAG, "Received Intent was null in HomeActivity onCreate.");
            Toast.makeText(this, "Error starting home screen.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // --- End Intent Processing ---

        initRead(); // Use primaryUserId internally now
        attachFallListener(); // Attach listener for NEW falls

        TextView userNameDisplay = findViewById(R.id.userNameView);
        userNameDisplay.setText("Hi " + userDisplayName + "!"); // Use the determined display name

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // DatabaseReference usersRef = database.getReference("users"); // No longer needed for this part

        // Create recycler view
        recyclerView = findViewById(R.id.fallRecycler);
        // Pass Fall list to the adapter, including the API key
        fallItemAdapter = new FallItemAdapter(fallArrayList, this, googleMapsApiKey);
        // Pass adapter to recycler view
        recyclerView.setAdapter(fallItemAdapter);
        // Make recycler have vertical layout
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        // --- Background Thread for initRead (if still needed for periodic refresh?) ---
        // Consider if this polling thread is still necessary now that ChildEventListener is used for *new* items.
        // If only initial load is needed, remove this thread entirely.
        // If periodic refresh of *all* data is desired (e.g., for potential missed events), keep it but manage it.
        isRunning = true; // Reset flag in case activity is recreated
        backgroundReaderThread = new Thread(() -> {
            try {
                while (isRunning) {
                    // Perform the initial/periodic read on a background thread
                    // Use post to update UI if needed from initRead results, though initRead currently doesn't update UI directly
                    // initRead(); // This fetches ALL falls repeatedly 
                    
                    // Log that the thread is running (for debugging)
                    Log.d(TAG, "Background reader thread loop running...");

                    // Delay - Adjust interval as needed, or remove if thread is removed
                    Thread.sleep(30000); // Example: Refresh every 30 seconds
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Background reader thread interrupted.");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            Log.d(TAG, "Background reader thread finished.");
        });
        backgroundReaderThread.start(); // Start the managed thread
        // --- End Background Thread ---

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.navigation_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_bluetooth) {
                Intent intent = new Intent(this, BluetoothActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_settings) {
                showSettingsDialog();
                return true;
            } else if (itemId == R.id.navigation_home) {
                return true;
            } else if (itemId == R.id.navigation_analysis) {
                Intent intent = new Intent(this, FallAnalysisActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
            return false;
        });

        createNotificationChannel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        // --- Graceful Shutdown --- 
        isRunning = false; // Signal thread to stop
        if (backgroundReaderThread != null) {
            backgroundReaderThread.interrupt(); // Interrupt the sleep/wait
        }
        detachFallListener(); // Clean up the listener
        // --- End Graceful Shutdown --- 
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final CharSequence[] items = {"Account Linking", "Link Amazon Skill", "Logout"};

        builder.setTitle("Settings")
               .setItems(items, (dialog, which) -> {
                   if (which == 0) { // Account Linking
                       accountLink(null);
                   } else if (which == 1) { // Link Amazon Skill (using new method)
                       enableAlexaSkill(); // Changed call
                   } else if (which == 2) { // Logout
                       logOut(null);
                   }
               })
               .show();
    }

    // Logs current user out of the application, take user back to login screen
    public void logOut(View view) {
        Log.d(TAG, "logOut called");
        // --- Graceful Shutdown --- 
        isRunning = false; // Signal thread to stop
        if (backgroundReaderThread != null) {
            backgroundReaderThread.interrupt(); // Interrupt the sleep/wait
        }
        detachFallListener(); // Detach listener FIRST
        // --- End Graceful Shutdown ---
        
        Intent intent = new Intent(this, LoginScreen.class);
        int faSize = fallArrayList.size();
        fallArrayList.clear();
        if (fallItemAdapter != null && faSize > 0) {
             fallItemAdapter.notifyItemRangeRemoved(0, faSize);
        }
        startActivity(intent);
        finish(); // Finish HomeActivity after logging out
    }

    // Adds sample fall to current user for testing purposes (Let's make this dynamic instead of the static)
    // Does this via addFallEntry
    public void addRandFall(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            geocoder = new Geocoder(this, Locale.getDefault());
            
            try {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    // Get current time
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String time = timeFormat.format(new Date());
                    
                    // Current date
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy", Locale.getDefault());
                    String date = dateFormat.format(new Date());
                    
                    // Heart rate: Base rate 60-80 for elderly
                    int baseHeartRate = 60 + (int)(Math.random() * 20);
                    int heartRate = baseHeartRate + (int)(Math.random() * 40); // Increase during fall
                    int deltaHeartRate = heartRate - baseHeartRate;
                    
                    // Impact severity: Usually moderate, occasionally severe
                    double impactSeverity = Math.random();
                    if (impactSeverity > 0.8) { // 20% chance of severe fall
                        impactSeverity = 2.0 + Math.random() * 1.0; // Severe: 2.0-3.0
                    } else {
                        impactSeverity = 0.5 + Math.random() * 1.5; // Moderate: 0.5-2.0
                    }
                    
                    // Fall direction: More common to fall forward or sideways
                    String[] directions = {"Forward", "Backward", "Left", "Right"};
                    int dirIndex = (int)(Math.random() * 100);
                    String fallDirection;
                    if (dirIndex < 40) fallDirection = "Forward";      // 40% forward
                    else if (dirIndex < 60) fallDirection = "Backward"; // 20% backward
                    else if (dirIndex < 80) fallDirection = "Left";     // 20% left
                    else fallDirection = "Right";                       // 20% right

                    // Use the primaryUserId determined in onCreate
                    if (primaryUserId != null) {
                        addFallEntry(primaryUserId, time, date, deltaHeartRate, heartRate, 
                                   fallDirection, impactSeverity);
                    } else {
                        Log.e(TAG, "primaryUserId is null in addRandFall. Cannot add fall.");
                        Toast.makeText(this, "Error identifying user.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error accessing location: " + e.getMessage());
            }
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
        }
    }

    // Gets passed fall metrics, creates new fall in database for current user
    // The 'userId' parameter will now be the primaryUserId (Firebase UID or Amazon ID)
    public void addFallEntry(String userId, String time, String date,
                             int deltaHeartRate, int heartRate, String fallDirection, double impactSeverity) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // Change path to top-level /falls/{userId}
        DatabaseReference fallsRef = database.getReference("falls").child(userId);

        String fallId = fallsRef.push().getKey();
        assert fallId != null;

        // Create initial fall data
        Map<String, Object> fallData = new HashMap<>();
        fallData.put("time", time);
        fallData.put("date", date);
        fallData.put("heartRate", heartRate);
        fallData.put("deltaHeartRate", deltaHeartRate);
        fallData.put("fallDirection", fallDirection);
        fallData.put("impactSeverity", impactSeverity);

        // Save initial data to Firebase
        fallsRef.child(fallId).setValue(fallData);

        // Add to UI with initial "Unknown location"
        final Fall newFall = new Fall(fallId, time, date, heartRate, deltaHeartRate,
                               impactSeverity, fallDirection, 0, 0, "Unknown location");
        fallArrayList.add(0, newFall);
        if (fallItemAdapter != null) {
            fallItemAdapter.notifyItemInserted(0);
            recyclerView.scrollToPosition(0);
        }

        // Add location data using existing method
        addLocationToFall(userId, fallId);

        // --- Send Proactive Event if Amazon user is logged in ---
        if (amazonUserId != null && !amazonUserId.isEmpty()) {
             // We pass the fall object which will eventually have location data
             // The event sending itself needs the location details eventually
             // For now, we trigger it - location is added async by addLocationToFall
             Log.d(TAG, "Attempting to send proactive event for fall ID: " + fallId);
             sendProactiveEventNotification(newFall); // Pass the newly created fall object
        } else {
             Log.d(TAG, "Not an Amazon user or Amazon User ID is null, skipping proactive event.");
        }
        // --- End Proactive Event ---

        // Show initial notification (location will update when available in UI notification)
        // Note: This is the Android notification, not the Alexa one.
        showFallNotification(time, "Unknown location", impactSeverity, 0, 0);
    }

    // Needs to get re-organized. Is the main function that checks if the current user is just a "listener" or if they are the senior
    // If fall listener, read the user's permission list and display the list of falls
    // If fall creator, display own falls
    public void initRead() {
        // Use the primaryUserId determined in onCreate
        if (primaryUserId == null) {
            Log.e(TAG, "primaryUserId is null in initRead. Cannot read data.");
            return;
        }
        String ownerId = primaryUserId;

        // Get reference to the falls node in the database for the current user ID
        // Change path to top-level /falls/{ownerId}
        DatabaseReference currentUserFallsRef = FirebaseDatabase.getInstance().getReference("falls")
                .child(ownerId);

        // Simplified: Always read falls directly for the ownerId.
        // Remove the logic checking for permissions/linked accounts for now.
        readFallsForUser(ownerId);
    }

    // This will actually read the database for the passed userID's falls
    // The userID passed here will be the primaryUserId
    public void readFallsForUser(String userID) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // DatabaseReference userRef = database.getReference("users"); // No longer needed here

        // Removed FirebaseUser check as it's not the primary identifier anymore
        if (userID == null || userID.isEmpty()) {
             Log.e(TAG, "UserID passed to readFallsForUser is null or empty.");
            return;
        }

        // Change path to top-level /falls/{userID}
        DatabaseReference currentUserFallsRef = database.getReference("falls").child(userID);

        currentUserFallsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot fallSnapshot : dataSnapshot.getChildren()) {
                    // Process each fall as needed

                    String fallID = fallSnapshot.getKey();
                    String time = fallSnapshot.child("time").getValue(String.class);
                    String date = fallSnapshot.child("date").getValue(String.class);
                    
                    // Safely get integer values, providing default (0) if null
                    Integer heartRateObj = fallSnapshot.child("heartRate").getValue(Integer.class);
                    int heartRate = (heartRateObj != null) ? heartRateObj : 0;
                    
                    Integer deltaHeartRateObj = fallSnapshot.child("deltaHeartRate").getValue(Integer.class);
                    int deltaHeartRate = (deltaHeartRateObj != null) ? deltaHeartRateObj : 0;
                    
                    // Safely get double value, providing default (0.0) if null
                    Double impactSeverityObj = fallSnapshot.child("impactSeverity").getValue(Double.class);
                    double impactSeverity = (impactSeverityObj != null) ? impactSeverityObj : 0.0;
                    
                    String fallDirection = fallSnapshot.child("fallDirection").getValue(String.class);

                    // Safely get location values
                    Double latitudeObj = fallSnapshot.child("latitude").getValue(Double.class);
                    double latitude = (latitudeObj != null) ? latitudeObj : 0.0;
                    Double longitudeObj = fallSnapshot.child("longitude").getValue(Double.class);
                    double longitude = (longitudeObj != null) ? longitudeObj : 0.0;
                    String address = fallSnapshot.child("address").getValue(String.class);
                    if (address == null) address = "Unknown location"; // Default for address too

                    boolean fallExists = false;
                    for (Fall fall : fallArrayList) {
                        if (fall.getfallID().equals(fallID)) {
                            fallExists = true;
                            break;
                        }
                    }

                    if (!fallExists) {
                        Fall newlyReadFall = new Fall(fallID, time, date, heartRate, deltaHeartRate, 
                            impactSeverity, fallDirection, latitude, longitude, address);
                        fallArrayList.add(0, newlyReadFall);
                        
                        // Notify adapter after adding
                        if (fallItemAdapter != null) {
                            final int insertIndex = 0; // Assuming we always add at the top
                            mainHandler.post(() -> {
                                fallItemAdapter.notifyItemInserted(insertIndex);
                                // Optional: Scroll to top if desired
                                // recyclerView.scrollToPosition(insertIndex);
                            });
                        }
                        
                        // Removed !startup check here as notifications are handled by listener
                    }
                } // End of loop processing initial falls
                
                // --- Initial load complete --- 
                Log.d(TAG, "Initial fall data read complete.");
                // --- End Initial load complete --- 
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    // Takes user to linking page (LWA)
    public void accountLink(View view) {
        Intent intent = new Intent(this, AccountLink.class);
        startActivity(intent);
    }

    /**
     * Attempts to open the Alexa app to the skill page using various URIs.
     * Falls back to a web link.
     */
    private void enableAlexaSkill() {
        String skillId = ALEXA_SKILL_ID; // Use the constant
        boolean opened = false;
        String alexaPackage = "com.amazon.dee.app";

        // --- Attempt 1: Standard Published Skill Deep Link (alexa://) ---
        // Even for dev, this is the most likely *supported* deep link if any works
        if (!opened) {
            try {
                Uri skillUri = Uri.parse("alexa://skills/dp/" + skillId + "/?ref=skill_dp_redirect_app");
                Intent alexaIntent = new Intent(Intent.ACTION_VIEW, skillUri);
                // alexaIntent.setPackage(alexaPackage); // Explicit package targeting can sometimes help/hurt, try with and without if needed
                alexaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.d(ALEXA_LOG_TAG, "Alexa: Attempting V1: alexa://skills/dp/ URI: " + skillUri.toString());
                startActivity(alexaIntent);
                opened = true;
                Log.i(ALEXA_LOG_TAG, "Alexa: Successfully launched intent for V1 URI.");
            } catch (ActivityNotFoundException e) {
                Log.w(ALEXA_LOG_TAG, "Alexa: Failed V1 (alexa://skills/dp/): " + e.getMessage());
            } catch (Exception e) { // Catch other potential exceptions
                 Log.e(ALEXA_LOG_TAG, "Alexa: Error launching V1 (alexa://skills/dp/): " + e.getMessage());
            }
        }

        // --- Attempt 2: Undocumented Dev Skills Section Link (amazon://) ---
        // Less likely to work, seems based on user speculation
        if (!opened) {
            try {
                Uri devSkillsUri = Uri.parse("amazon://skills/your-skills/dev");
                Intent alexaIntent = new Intent(Intent.ACTION_VIEW, devSkillsUri);
                alexaIntent.setPackage(alexaPackage); // Explicitly target Alexa app
                alexaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.d(ALEXA_LOG_TAG, "Alexa: Attempting V2: amazon://skills/your-skills/dev URI: " + devSkillsUri.toString());
                startActivity(alexaIntent);
                opened = true;
                 Log.i(ALEXA_LOG_TAG, "Alexa: Successfully launched intent for V2 URI (Dev Skills).");
            } catch (ActivityNotFoundException e) {
                Log.w(ALEXA_LOG_TAG, "Alexa: Failed V2 (amazon://skills/your-skills/dev): " + e.getMessage());
            } catch (Exception e) {
                 Log.e(ALEXA_LOG_TAG, "Alexa: Error launching V2 (amazon://skills/your-skills/dev): " + e.getMessage());
            }
        }

        // --- Attempt 3: General Skills Page Link (amazon://) ---
        if (!opened) {
            try {
                Uri skillsUri = Uri.parse("amazon://skills");
                Intent alexaIntent = new Intent(Intent.ACTION_VIEW, skillsUri);
                alexaIntent.setPackage(alexaPackage); // Explicitly target Alexa app
                alexaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 Log.d(ALEXA_LOG_TAG, "Alexa: Attempting V3: amazon://skills URI: " + skillsUri.toString());
                startActivity(alexaIntent);
                opened = true;
                 Log.i(ALEXA_LOG_TAG, "Alexa: Successfully launched intent for V3 URI (Skills Home).");
            } catch (ActivityNotFoundException e) {
                 Log.w(ALEXA_LOG_TAG, "Alexa: Failed V3 (amazon://skills): " + e.getMessage());
            } catch (Exception e) {
                 Log.e(ALEXA_LOG_TAG, "Alexa: Error launching V3 (amazon://skills): " + e.getMessage());
            }
        }

        // --- Fallback 4: Web URL (alexa.amazon.com) ---
        if (!opened) {
             Log.w(ALEXA_LOG_TAG, "Alexa: All app deep link attempts failed. Falling back to web URL.");
            try {
                // Use the alexa.amazon.com SPA link
                Uri webUri = Uri.parse("https://alexa.amazon.com/spa/index.html#skills/dp/" + skillId + "/");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, webUri);
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.d(ALEXA_LOG_TAG, "Alexa: Attempting Fallback V4: Web URL: " + webUri.toString());
                startActivity(browserIntent);
                // We don't set opened=true here, as it's just opening a browser
            } catch (ActivityNotFoundException e2) {
                 // This means no browser available - very unlikely
                 Log.e(ALEXA_LOG_TAG, "Alexa: Failed Fallback V4 (Web URL): No browser found?", e2);
                Toast.makeText(this,
                    "Please open the Alexa app or website manually to find the RiskWatch skill",
                    Toast.LENGTH_LONG).show();
            } catch (Exception e2) { // Catch other potential exceptions
                 Log.e(ALEXA_LOG_TAG, "Alexa: Error launching Fallback V4 (Web URL): " + e2.getMessage());
                  Toast.makeText(this,
                    "Could not open skill link. Please try manually.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }
    // --- End enableAlexaSkill Method ---

    // Creates user in the database
    public static void createUserDB(String userName) {
        // Get a reference to the "users" directory in the Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        // Generate a unique key for the new user entry
        String userId = usersRef.push().getKey();

        // Write the user name to the database under the specified user ID
        assert userId != null;
        usersRef.child(userId).child("name").setValue(userName);
    }

    // Bluetooth
    public void openBluetoothActivity(View view) {
        Intent bluetoothIntent = new Intent(this, BluetoothActivity.class);
        bluetoothIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(bluetoothIntent);
    }

    private void addLocationToFall(String userId, String fallId) {
        // Use the passed userId (which should be primaryUserId)
        if (userId != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            geocoder = new Geocoder(this, Locale.getDefault());
            
            try {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    final double latitude = lastLocation.getLatitude();
                    final double longitude = lastLocation.getLongitude();
                    final String address = getAddressFromLocation(lastLocation);

                    // Update Firebase
                    // Change path to top-level /falls/{userId}/{fallId}
                    DatabaseReference fallRef = FirebaseDatabase.getInstance()
                        .getReference("falls") // Top-level "falls"
                        .child(userId)      // User ID
                        .child(fallId);     // Specific Fall ID
                        
                    Map<String, Object> locationUpdates = new HashMap<>();
                    locationUpdates.put("latitude", latitude);
                    locationUpdates.put("longitude", longitude);
                    locationUpdates.put("address", address);
                    fallRef.updateChildren(locationUpdates).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Update UI
                            Fall updatedFall = updateFallInUI(fallId, latitude, longitude, address);

                            // Show Android notification with location and impact severity
                            fallRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String time = snapshot.child("time").getValue(String.class);
                                    Double severity = snapshot.child("impactSeverity").getValue(Double.class);
                                    // Update the existing Android notification (or show if not shown)
                                    showFallNotification(time, address, severity != null ? severity : 0, latitude, longitude);

                                    // If this was an Amazon user, potentially update or resend the Alexa event?
                                    // For simplicity now, we send the Alexa event initially in addFallEntry.
                                    // If location is critical *before* sending, logic needs restructuring.
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Error getting fall data for notification: " + error.getMessage());
                                }
                            });
                        } else {
                             Log.e(TAG, "Error updating fall location in Firebase: " + task.getException().getMessage());
                        }
                    });
                } else {
                    Log.w(TAG, "Last known location is null, cannot add location to fall ID: " + fallId);
                    // If location is critical for the Alexa notification, handle this case
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error accessing location: " + e.getMessage());
            }
        } else {
             Log.w(TAG, "Location permission not granted or userId is null in addLocationToFall.");
        }
    }

    private String getAddressFromLocation(Location location) {
        String address = "Unknown location";
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (!addresses.isEmpty()) {
                Address addr = addresses.get(0);
                address = addr.getAddressLine(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting address: " + e.getMessage());
        }
        return address;
    }

    private Fall updateFallInUI(String fallId, double latitude, double longitude, String address) {
        Fall updatedFall = null;
        for (int i = 0; i < fallArrayList.size(); i++) {
            Fall fall = fallArrayList.get(i);
            if (fall.getfallID().equals(fallId)) {
                fall.latitude = latitude;
                fall.longitude = longitude;
                fall.address = address;
                if (fallItemAdapter != null) {
                    final int index = i;
                    mainHandler.post(() -> fallItemAdapter.notifyItemChanged(index));
                }
                updatedFall = fall;
                break;
            }
        }
        return updatedFall;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static ArrayList<Fall> getFallArrayList() {
        return fallArrayList;
    }

    private void showFallNotification(String time, String location, double severity, double latitude, double longitude) {
        // Ensure the API key is loaded before constructing the URL
        if (googleMapsApiKey == null || googleMapsApiKey.isEmpty()) {
            Log.e(TAG, "Google Maps API key is missing. Cannot show map in notification.");
            // Optionally show notification without map, or just log error
            // For now, let's proceed without the map if key is missing
             // Create the notification builder (without map initially)
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fall_detection_channel")
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("Fall Detected!")
                .setContentText("Time: " + time + " | Location: " + location)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

            // Add action button to open maps
            Intent mapIntent = new Intent(Intent.ACTION_VIEW);
            mapIntent.setData(Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", latitude, longitude, latitude, longitude)));
            PendingIntent mapPendingIntent = PendingIntent.getActivity(this, 1, mapIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_dialog_map, "View Location", mapPendingIntent);
            
            // Show the notification without the map image
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(HomeActivity.this);
            if (ActivityCompat.checkSelfPermission(HomeActivity.this, 
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1, builder.build()); // Use a unique ID, e.g., 1
            }
            return; // Exit the method as we can't load the map
        }

        String staticMapUrl = String.format(
            Locale.US, // Use Locale.US to ensure decimal points are periods
            "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=600x300&markers=color:red%%7C%f,%f&key=%s",
            latitude, longitude, latitude, longitude, googleMapsApiKey // Use the loaded key
        );
        Log.d(TAG, "Static Map URL: " + staticMapUrl); // Log the URL for debugging

        // Create intent to open Google Maps
        Intent mapIntent = new Intent(Intent.ACTION_VIEW);
        mapIntent.setData(Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", latitude, longitude, latitude, longitude)));
        PendingIntent mapPendingIntent = PendingIntent.getActivity(this, 1, mapIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create the notification builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "fall_detection_channel")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Fall Detected!")
            .setContentText("Time: " + time + " | Location: " + location)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        // Add action button to open maps
        builder.addAction(android.R.drawable.ic_dialog_map, "View Location", mapPendingIntent);

        // Load map image asynchronously
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    return Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(staticMapUrl)
                        .submit()
                        .get();
                } catch (Exception e) {
                    Log.e(TAG, "Error loading map image: " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    NotificationCompat.BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle("Fall Detected!")
                        .setSummaryText(location);
                    builder.setStyle(bigPictureStyle);
                } else {
                     Log.w(TAG, "Map bitmap was null, showing notification without map image.");
                }

                // Show the notification
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(HomeActivity.this);
                if (ActivityCompat.checkSelfPermission(HomeActivity.this, 
                        android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(1, builder.build());
                }
            }
        }.execute();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "fall_detection_channel",
                "Fall Detection",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for detected falls");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("time")) {
            // Use the primaryUserId established in onCreate
            if (primaryUserId != null) {
                String time = intent.getStringExtra("time");
                String date = intent.getStringExtra("date");
                int heartRate = intent.getIntExtra("heartRate", 0);
                int deltaHeartRate = intent.getIntExtra("deltaHeartRate", 0);
                double impactSeverity = intent.getDoubleExtra("impactSeverity", 0.0);
                String fallDirection = intent.getStringExtra("fallDirection");
                
                // Use existing addFallEntry method
                addFallEntry(primaryUserId, time, date, deltaHeartRate, heartRate, 
                            fallDirection, impactSeverity);
            }
        }
    }

    // --- Methods for Alexa Proactive Events ---

    // Method to load Alexa credentials from assets/alexa_skill.json
    private void loadAlexaCredentials() {
        try {
            InputStream is = getAssets().open("alexa_skill.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject credentials = new JSONObject(json);
            alexaSkillClientId = credentials.getString("clientId");
            alexaSkillClientSecret = credentials.getString("clientSecret");
            Log.i(ALEXA_LOG_TAG, "Alexa: Skill credentials loaded successfully. Client ID: " + alexaSkillClientId);
        } catch (IOException e) {
            Log.e(ALEXA_LOG_TAG, "Alexa: Error reading alexa_skill.json from assets", e);
            alexaSkillClientId = null;
            alexaSkillClientSecret = null;
            mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error reading Alexa credentials.", Toast.LENGTH_LONG).show());
        } catch (JSONException e) {
            Log.e(ALEXA_LOG_TAG, "Alexa: Error parsing JSON from alexa_skill.json", e);
            alexaSkillClientId = null;
            alexaSkillClientSecret = null;
             mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error parsing Alexa credentials.", Toast.LENGTH_LONG).show());
        }
    }

    private void getProactiveEventsAccessToken() {
        // Prevent multiple simultaneous token requests or if credentials failed to load
        if (alexaSkillClientId == null || alexaSkillClientSecret == null) {
             Log.e(ALEXA_LOG_TAG, "Alexa: Cannot get token: Credentials not loaded or invalid.");
             return;
        }
        // Check if token is still valid (give 1 min buffer)
        if (proactiveEventsAccessToken != null && System.currentTimeMillis() < tokenExpiryTime - 60000) {
             Log.d(ALEXA_LOG_TAG, "Alexa: Using existing valid Proactive Events access token.");
             return;
        }
        if (proactiveEventsAccessToken != null) {
             Log.d(ALEXA_LOG_TAG, "Alexa: Existing token expired or nearing expiry. Requesting new one.");
        } else {
             Log.d(ALEXA_LOG_TAG, "Alexa: No existing token. Requesting new Proactive Events access token...");
        }

        // Ensure we are not already requesting a token (optional, simple check here)
        // More robust solution would use AtomicBoolean or similar if high concurrency expected
        // For simplicity, relying on single executor thread.

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            Log.d(ALEXA_LOG_TAG, "Alexa: Starting background task to request token.");
            try {
                URL url = new URL("https://api.amazon.com/auth/o2/token");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000); // 10 seconds
                connection.setReadTimeout(10000);    // 10 seconds


                String postData = "grant_type=client_credentials" +
                                  "&client_id=" + alexaSkillClientId +
                                  "&client_secret=" + alexaSkillClientSecret +
                                  "&scope=alexa::proactive_events";
                Log.d(ALEXA_LOG_TAG, "Alexa: Token request data (credentials omitted): grant_type=client_credentials&scope=alexa::proactive_events");

                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(postData);
                wr.flush();
                wr.close();

                int responseCode = connection.getResponseCode();
                Log.d(ALEXA_LOG_TAG, "Alexa: Token request response code: " + responseCode);

                BufferedReader in;
                StringBuilder response = new StringBuilder();
                InputStream stream;

                if (responseCode >= 200 && responseCode < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                     Log.w(ALEXA_LOG_TAG, "Alexa: Token request failed with code: " + responseCode);
                }

                if (stream != null) {
                    in = new BufferedReader(new InputStreamReader(stream));
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    Log.d(ALEXA_LOG_TAG, "Alexa: Token response body: " + response.toString());
                } else {
                     Log.e(ALEXA_LOG_TAG, "Alexa: Token request - response stream was null for code: " + responseCode);
                }


                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    proactiveEventsAccessToken = jsonResponse.getString("access_token");
                    long expiresIn = jsonResponse.getLong("expires_in"); // Duration in seconds
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000);
                    Log.i(ALEXA_LOG_TAG, "Alexa: Successfully obtained Proactive Events access token. Expires in: " + expiresIn + " seconds.");
                    // Log only part of the token for verification if needed, never the whole token
                    // Log.d(ALEXA_LOG_TAG, "Alexa: Token starts with: " + (proactiveEventsAccessToken != null && proactiveEventsAccessToken.length() > 10 ? proactiveEventsAccessToken.substring(0, 10) : "N/A"));
                } else {
                    Log.e(ALEXA_LOG_TAG, "Alexa: Error getting Proactive Events access token. Code: " + responseCode + ", Body: " + response.toString());
                    proactiveEventsAccessToken = null;
                    tokenExpiryTime = 0;
                    // Post error message to UI thread
                    mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Failed to get Alexa token: " + responseCode, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) { // Catch broader exceptions like SocketTimeoutException
                Log.e(ALEXA_LOG_TAG, "Alexa: Exception during token request: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                proactiveEventsAccessToken = null;
                tokenExpiryTime = 0;
                 mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Network error getting Alexa token.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                     Log.d(ALEXA_LOG_TAG, "Alexa: Token request connection disconnected.");
                }
            }
        });
    }

    private void sendProactiveEventNotification(Fall fallDetails) {
        Log.d(ALEXA_LOG_TAG, "Alexa: Preparing to send proactive event for fall ID: " + fallDetails.getfallID());
        // Check if credentials were loaded before proceeding
        if (alexaSkillClientId == null || alexaSkillClientSecret == null) {
             Log.e(ALEXA_LOG_TAG, "Alexa: Cannot send notification: Credentials not loaded.");
              mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error: Missing Alexa credentials.", Toast.LENGTH_LONG).show());
             return;
        }

        // Ensure we have a user ID
        if (amazonUserId == null || amazonUserId.isEmpty()) {
            // This is only an error if we intend to use Unicast later, but log warning for now
            Log.w(ALEXA_LOG_TAG, "Alexa: Amazon User ID is missing. Cannot send Unicast events.");
            // Decide if you want to prevent *all* sends or just Unicast
             // For now, let's allow Multicast attempts if needed.
             // return; // Uncomment this line if a user ID is absolutely required for any send attempt
        }

         // Check token validity
        if (proactiveEventsAccessToken == null || System.currentTimeMillis() >= tokenExpiryTime - 10000) { // Check with 10 sec buffer
            Log.w(ALEXA_LOG_TAG, "Alexa: Proactive Events access token is missing or expired/expiring soon. Requesting new one first.");
            getProactiveEventsAccessToken(); // Attempt to get/refresh token
            // Delay the actual sending attempt to allow token retrieval
            mainHandler.postDelayed(() -> {
                 Log.d(ALEXA_LOG_TAG, "Alexa: Retrying sendProactiveEventInternal after token request delay.");
                 // Re-check token after delay before sending
                 if (proactiveEventsAccessToken != null && System.currentTimeMillis() < tokenExpiryTime - 10000) {
                     Log.d(ALEXA_LOG_TAG, "Alexa: Token seems valid after refresh attempt, proceeding with send.");
                     sendProactiveEventInternal(fallDetails);
                 } else {
                     Log.e(ALEXA_LOG_TAG, "Alexa: Failed to get a valid token after refresh attempt, cannot send proactive event for fall: " + fallDetails.getfallID());
                     mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Failed to refresh Alexa token.", Toast.LENGTH_LONG).show());
                 }
            }, 5000); // Increased delay to 5 seconds for token request
            return;
        }

        // If token is valid, send immediately
        Log.d(ALEXA_LOG_TAG, "Alexa: Token is valid. Proceeding with sendProactiveEventInternal.");
        sendProactiveEventInternal(fallDetails);
    }


    private void sendProactiveEventInternal(Fall fallDetails) {
        final String currentToken = proactiveEventsAccessToken;
        final String userIdToSend = amazonUserId; // Keep this variable, but it won't be used in Multicast payload

        // --- DIAGNOSTIC LOG ---
        Log.d(ALEXA_LOG_TAG, "Alexa: Entering sendProactiveEventInternal. User ID (for potential Unicast): " + userIdToSend + ", Token present: " + (currentToken != null && !currentToken.isEmpty()));
        // --- END DIAGNOSTIC LOG ---

        // Determine delivery type (Modify this logic based on your desired test)
        final String deliveryType = "Multicast"; // Set to "Unicast" or "Multicast" for testing
        final String eventName = "AMAZON.MessageAlert.Activated"; // Set desired event schema


        // Add a null/empty check specifically for Unicast (won't trigger for Multicast)
        if ("Unicast".equals(deliveryType) && (userIdToSend == null || userIdToSend.isEmpty())) {
            Log.e(ALEXA_LOG_TAG, "Alexa: Cannot send Unicast event: User ID is null or empty!");
            mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Cannot send Alexa alert: User ID missing.", Toast.LENGTH_SHORT).show());
            return;
        }
         if (currentToken == null || currentToken.isEmpty()) {
             Log.e(ALEXA_LOG_TAG, "Alexa: Cannot send event: Access token is missing!");
             mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Cannot send Alexa alert: Token missing.", Toast.LENGTH_SHORT).show());
             return;
         }


        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            Log.d(ALEXA_LOG_TAG, "Alexa: Starting background task to send event (" + deliveryType + ", " + eventName + ").");
            try {
                // 1. Construct Event Payload
                JSONObject eventPayload = new JSONObject();
                eventPayload.put("name", eventName);

                JSONObject payloadDetails = new JSONObject();
                 // --- Payload specific to eventName ---
                if ("AMAZON.MessageAlert.Activated".equals(eventName)) {
                    JSONObject stateObject = new JSONObject();
                    stateObject.put("status", "UNREAD");
                    payloadDetails.put("state", stateObject);
                    payloadDetails.put("messageGroup", new JSONObject()
                        .put("creator", new JSONObject().put("name", "RiskWatch Alert"))
                        .put("count", 1));
                } else if ("AMAZON.OrderStatus.Updated".equals(eventName)) {
                     // Example for OrderStatus (adjust as needed)
                     JSONObject stateObject = new JSONObject();
                     stateObject.put("status", "ORDER_SHIPPED"); // Example status
                     payloadDetails.put("state", stateObject);
                     JSONObject orderDetails = new JSONObject();
                     JSONObject seller = new JSONObject();
                     seller.put("name", "localizedattribute:sellerName"); // Requires localizedAttributes
                     orderDetails.put("seller", seller);
                     payloadDetails.put("order", orderDetails);
                 } // Add other event types if needed
                // --- End Payload specific ---
                eventPayload.put("payload", payloadDetails);

                // 2. Construct Full Request Body
                JSONObject requestBody = new JSONObject();
                requestBody.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
                requestBody.put("referenceId", UUID.randomUUID().toString());
                requestBody.put("expiryTime", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date(System.currentTimeMillis() + 3600 * 1000 * 24))); // 24 hr expiry
                requestBody.put("event", eventPayload);

                // 3. Add Relevant Audience
                JSONObject relevantAudience = new JSONObject();
                relevantAudience.put("type", deliveryType);
                if ("Unicast".equals(deliveryType)) {
                    relevantAudience.put("payload", new JSONObject().put("user", userIdToSend));
                } else { // Multicast
                    relevantAudience.put("payload", new JSONObject()); // Empty payload for Multicast
                }
                requestBody.put("relevantAudience", relevantAudience);

                // 4. Add Localized Attributes (Example for OrderStatus)
                 if ("AMAZON.OrderStatus.Updated".equals(eventName)) {
                     JSONArray localizedAttributes = new JSONArray();
                     JSONObject enUSAttributes = new JSONObject();
                     enUSAttributes.put("locale", "en-US");
                     enUSAttributes.put("sellerName", "RiskWatch Fall Alert"); // Customize as needed
                     localizedAttributes.put(enUSAttributes);
                     requestBody.put("localizedAttributes", localizedAttributes);
                 }

                // 5. Log and Send
                String jsonInputString = requestBody.toString();
                Log.d(ALEXA_LOG_TAG, "Alexa: Proactive Event Request Body (" + deliveryType + ", " + eventName + "): " + jsonInputString);

                URL url = new URL(PROACTIVE_EVENTS_API_ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + currentToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000); // 15 seconds
                connection.setReadTimeout(15000);    // 15 seconds


                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(jsonInputString);
                wr.flush();
                wr.close();

                int responseCode = connection.getResponseCode();
                Log.d(ALEXA_LOG_TAG, "Alexa: Proactive Event POST response code: " + responseCode);

                // 6. Handle Response
                StringBuilder responseBody = new StringBuilder();
                InputStream responseStream = null;
                try {
                    if (responseCode >= 200 && responseCode < 300) {
                        responseStream = connection.getInputStream();
                        Log.i(ALEXA_LOG_TAG, "Alexa: Successfully sent proactive event ("+ deliveryType + ", " + eventName + ") for fall ID: " + fallDetails.getfallID() + ". Code: " + responseCode);
                        mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Alexa notification sent (" + deliveryType + ").", Toast.LENGTH_SHORT).show());
                    } else {
                        responseStream = connection.getErrorStream();
                         Log.w(ALEXA_LOG_TAG, "Alexa: Received error response code for proactive event: " + responseCode);
                    }

                    if (responseStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBody.append(line);
                        }
                        reader.close();
                        Log.d(ALEXA_LOG_TAG, "Alexa: Proactive Event Response Body: " + responseBody.toString());
                    } else {
                        Log.d(ALEXA_LOG_TAG, "Alexa: Proactive Event Response Body stream was null.");
                    }

                    // Handle specific error codes after logging body
                    if (responseCode >= 300) {
                         Log.e(ALEXA_LOG_TAG, "Alexa: Error sending proactive event ("+ deliveryType + ", " + eventName + "). Code: " + responseCode + ", Body: " + responseBody.toString());
                         final String errorMsg = "Failed Alexa send ("+ deliveryType +"): " + responseCode;
                         mainHandler.post(() -> Toast.makeText(HomeActivity.this, errorMsg, Toast.LENGTH_LONG).show());
                    }

                } catch (IOException readEx) {
                     Log.e(ALEXA_LOG_TAG, "Alexa: IOException reading response stream for code " + responseCode, readEx);
                     final String errorMsg = "Error reading Alexa response: " + responseCode;
                     mainHandler.post(() -> Toast.makeText(HomeActivity.this, errorMsg, Toast.LENGTH_LONG).show());
                } finally {
                     if (responseStream != null) {
                         try { responseStream.close(); } catch (IOException ignored) {}
                     }
                }


            } catch (Exception e) { // Catch broader exceptions like JSONException, MalformedURLException
                 Log.e(ALEXA_LOG_TAG, "Alexa: Exception during proactive event sending ("+ deliveryType + ", " + eventName + "): " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
                 mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Network error sending Alexa notification.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                    Log.d(ALEXA_LOG_TAG, "Alexa: Event send connection disconnected.");
                }
            }
        });
    }

    private void attachFallListener() {
        if (primaryUserId == null) {
            Log.e(TAG, "Cannot attach fall listener, primaryUserId is null.");
            return;
        }
    
        // Reference to the user's falls path
        fallsRefListener = FirebaseDatabase.getInstance().getReference("falls").child(primaryUserId);
    
        // Detach any existing listener first (safety measure)
        detachFallListener(); 
    
        Log.d(TAG, "Attaching ChildEventListener to: " + fallsRefListener.toString());
        fallChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // A new fall has been added AFTER the listener was attached
                Log.d(TAG, "onChildAdded triggered for fall ID: " + dataSnapshot.getKey());
                
                 // --- Time-based Startup Delay Check --- 
                long currentTimeMillis = System.currentTimeMillis();
                if (currentTimeMillis - activityStartTimeMillis < STARTUP_DELAY_MS) {
                    Log.d(TAG, "onChildAdded skipped within " + STARTUP_DELAY_MS + "ms startup delay.");
                    return; 
                }
                // --- End Time-based Startup Delay Check --- 
    
                try {
                    // Parse the newly added fall data (with null checks)
                    String fallID = dataSnapshot.getKey();
                    if (fallID == null) return; // Should not happen, but safety check
    
                    String time = dataSnapshot.child("time").getValue(String.class);
                    String date = dataSnapshot.child("date").getValue(String.class);
                    Integer heartRateObj = dataSnapshot.child("heartRate").getValue(Integer.class);
                    int heartRate = (heartRateObj != null) ? heartRateObj : 0;
                    Integer deltaHeartRateObj = dataSnapshot.child("deltaHeartRate").getValue(Integer.class);
                    int deltaHeartRate = (deltaHeartRateObj != null) ? deltaHeartRateObj : 0;
                    Double impactSeverityObj = dataSnapshot.child("impactSeverity").getValue(Double.class);
                    double impactSeverity = (impactSeverityObj != null) ? impactSeverityObj : 0.0;
                    String fallDirection = dataSnapshot.child("fallDirection").getValue(String.class);
                    Double latitudeObj = dataSnapshot.child("latitude").getValue(Double.class);
                    double latitude = (latitudeObj != null) ? latitudeObj : 0.0;
                    Double longitudeObj = dataSnapshot.child("longitude").getValue(Double.class);
                    double longitude = (longitudeObj != null) ? longitudeObj : 0.0;
                    String address = dataSnapshot.child("address").getValue(String.class);
                    if (address == null) address = "Unknown location";
    
                    Fall newFall = new Fall(fallID, time, date, heartRate, deltaHeartRate,
                                           impactSeverity, fallDirection, latitude, longitude, address);
    
                    // --- CRUCIAL CHECK: Avoid duplicates ---
                    // Check if this fall ID is already in our list (loaded by initRead)
                    boolean alreadyExists = false;
                    for (Fall existingFall : fallArrayList) {
                        if (existingFall.getfallID().equals(fallID)) {
                            alreadyExists = true;
                            break;
                        }
                    }
    
                    if (!alreadyExists) {
                        Log.d(TAG, "New fall detected (not in list): " + fallID + ". Adding and Notifying.");
                        // Add to the beginning of the list and update UI
                        fallArrayList.add(0, newFall);
                        if (fallItemAdapter != null) {
                             mainHandler.post(() -> fallItemAdapter.notifyItemInserted(0));
                             // Optional: Scroll to top if desired
                             // recyclerView.scrollToPosition(0);
                        }
    
                        // --- Trigger Notifications ---
                        showFallNotification(time, address, impactSeverity, latitude, longitude);
                        if (amazonUserId != null && !amazonUserId.isEmpty()) {
                            Log.d(TAG, "Attempting to send proactive event for new fall ID: " + fallID);
                            sendProactiveEventNotification(newFall);
                        }
                        // --- End Trigger Notifications ---
    
                    } else {
                         Log.d(TAG, "Fall " + fallID + " already exists in the list, skipping add/notification from onChildAdded.");
                    }
    
                } catch (Exception e) {
                     Log.e(TAG, "Error processing fall in onChildAdded", e);
                }
            }
    
            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // Handle fall data updates if needed (e.g., location added later)
                 Log.d(TAG, "onChildChanged for fall ID: " + dataSnapshot.getKey());
                 // You might want to update the item in fallArrayList and notifyItemChanged here
            }
    
            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                // Handle fall deletion if needed
                 Log.d(TAG, "onChildRemoved for fall ID: " + dataSnapshot.getKey());
                 // You might want to remove the item from fallArrayList and notifyItemRemoved here
            }
    
            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // Usually not relevant for this structure
            }
    
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "Fall ChildEventListener failed:", databaseError.toException());
                // Maybe try re-attaching after a delay?
            }
        };
        fallsRefListener.addChildEventListener(fallChildEventListener);
    }
    
    private void detachFallListener() {
        if (fallsRefListener != null && fallChildEventListener != null) {
            Log.d(TAG, "Detaching ChildEventListener from: " + fallsRefListener.toString());
            fallsRefListener.removeEventListener(fallChildEventListener);
            fallChildEventListener = null;
            fallsRefListener = null;
        }
    }

    // --- End Methods for Alexa Proactive Events ---

    // --- Add this new method --- 
    private void loadMapsApiKey() {
        try {
            InputStream is = getAssets().open("google_maps_config.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject config = new JSONObject(json);
            googleMapsApiKey = config.getString("mapsApiKey");
            if (googleMapsApiKey == null || googleMapsApiKey.isEmpty()) {
                 Log.e(TAG, "Maps API Key loaded from JSON is null or empty.");
                 googleMapsApiKey = null; // Ensure it's null if empty
            } else {
                 Log.i(TAG, "Google Maps API Key loaded successfully.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading google_maps_config.json from assets", e);
            googleMapsApiKey = null;
            mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error reading Maps config.", Toast.LENGTH_LONG).show());
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON from google_maps_config.json", e);
            googleMapsApiKey = null;
             mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error parsing Maps config.", Toast.LENGTH_LONG).show());
        }
    }
}
