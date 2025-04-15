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

public class HomeActivity extends AppCompatActivity {

    private static final ArrayList<Fall> fallArrayList = new ArrayList<>();
    private RecyclerView recyclerView;
    private static FallItemAdapter fallItemAdapter;

    User currentUser;

    boolean startup = true;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Load Alexa credentials from assets
        loadAlexaCredentials();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                },
                1);
        }

        // Get the intent that started this activity
        Intent receivedIntent = getIntent();
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();

        if (receivedIntent != null) {
            String username = receivedIntent.getStringExtra("user");
            boolean isGuest = receivedIntent.getBooleanExtra("isGuest", false);
            // Retrieve Amazon User ID if provided by LoginScreen
            amazonUserId = receivedIntent.getStringExtra("amazon_user_id"); 
            
            // Only update username if we received a new one
            if (username != null) {
                savedUsername = username;
            }
            
            currentUser = new User(savedUsername != null ? savedUsername : "Guest");
            Log.d(TAG, "HOME USERNAME " + currentUser.getUserName());
            
            if (amazonUserId != null) {
                Log.d(TAG, "Amazon User ID: " + amazonUserId);
                // Attempt to get token only if credentials loaded successfully
                if (alexaSkillClientId != null && alexaSkillClientSecret != null) {
                    getProactiveEventsAccessToken();
                } else {
                    Log.e(TAG, "Alexa credentials not loaded, cannot get proactive events token.");
                    Toast.makeText(this, "Error: Alexa credentials missing.", Toast.LENGTH_LONG).show();
                }
            }
            
            if (!isGuest) {
                // Only assert fireUser for non-guest users
                assert fireUser != null;
                Log.d(TAG, "FireAuth UID: " + fireUser.getUid());
            }
        }

        initRead();
        startup = false;

        TextView userNameDisplay = findViewById(R.id.userNameView);
        userNameDisplay.setText("Hi " + currentUser.getUserName() + "!");

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        // Instantiate user if they do not exist by getting account's user name
        usersRef.orderByChild("name").equalTo(currentUser.getUserName()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    // User "Bob" does not exist, so create a new entry
                    createUserDB(currentUser.getUserName());
                }

                // Add a fall entry for user as a test to ensure we can store a fall for them
                // addFallEntry(fireUser.getUid(),  "06:48 PM", "01/05/24", 15, 92, "Front", 2.6);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        // Create recycler view
        recyclerView = findViewById(R.id.fallRecycler);
        // Pass Fall list to the adapter
        fallItemAdapter = new FallItemAdapter(fallArrayList, this);
        // Pass adapter to recycler view
        recyclerView.setAdapter(fallItemAdapter);
        // Make recycler have vertical layout
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        // Thread to read database after every second
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    // Actual database read function
                    initRead();
                    // Delay (up for debate, should be maybe 30 seconds?)
                    Thread.sleep(1000); // Add a delay of 1 second between each read
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // Start thread
        thread.start();

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
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings")
               .setItems(new CharSequence[]{"Account Linking", "Logout"}, (dialog, which) -> {
                   if (which == 0) {
                       accountLink(null);
                   } else if (which == 1) {
                       logOut(null);
                   }
               })
               .show();
    }

    // Logs current user out of the application, take user back to login screen
    public void logOut(View view) {
        Intent intent = new Intent(this, LoginScreen.class);
        int faSize = fallArrayList.size();
        fallArrayList.clear();
        fallItemAdapter.notifyItemRangeRemoved(0, (faSize - 1));
        startActivity(intent);
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

                    FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (fireUser != null) {
                        addFallEntry(fireUser.getUid(), time, date, deltaHeartRate, heartRate, 
                                   fallDirection, impactSeverity);
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
    public void addFallEntry(String userId, String time, String date,
                             int deltaHeartRate, int heartRate, String fallDirection, double impactSeverity) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");
        DatabaseReference fallsRef = usersRef.child(userId).child("falls");

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
        addLocationToFall(fallId);

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
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, handle this case
            return;
        }
        String ownerId = currentUser.getUid();

        // Get reference to the falls node in the database for the current user
        DatabaseReference currentUserFallsRef = FirebaseDatabase.getInstance().getReference("users")
                .child(ownerId).child("falls");

        // Check if the current user has any falls
        currentUserFallsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // User has falls, read falls for the current user
                    readFallsForUser(ownerId);
                } else {
                    // User does not have falls, check if linked to another account
                    DatabaseReference permissionsRef = FirebaseDatabase.getInstance().getReference("permissions");

                    permissionsRef.orderByChild("grantedUsers/" + ownerId).equalTo(true).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                // User is linked to another account, retrieve the owner ID
                                for (DataSnapshot permissionSnapshot : dataSnapshot.getChildren()) {
                                    String linkedAccountId = permissionSnapshot.getKey();
                                    if (linkedAccountId != null) {
                                        // Read falls for the linked account
                                        readFallsForUser(linkedAccountId);
                                        return;
                                    }
                                }
                            } else {
                                // User is neither linked nor has falls, handle this case
                                // For example, display a message indicating no falls available
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed to read value.", error.toException());
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    // This will actually read the database for the passed userID's falls
    public void readFallsForUser(String userID) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userRef = database.getReference("users");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return;
        }

        DatabaseReference currentUserFallsRef = userRef.child(userID).child("falls");

        currentUserFallsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot fallSnapshot : dataSnapshot.getChildren()) {
                    // Process each fall as needed

                    String fallID = fallSnapshot.getKey();
                    String time = fallSnapshot.child("time").getValue(String.class);
                    String date = fallSnapshot.child("date").getValue(String.class);
                    int heartRate = fallSnapshot.child("heartRate").getValue(Integer.class);
                    int deltaHeartRate = fallSnapshot.child("deltaHeartRate").getValue(Integer.class);
                    double impactSeverity = fallSnapshot.child("impactSeverity").getValue(Double.class);
                    String fallDirection = fallSnapshot.child("fallDirection").getValue(String.class);

                    double latitude = fallSnapshot.child("latitude").getValue(Double.class);
                    double longitude = fallSnapshot.child("longitude").getValue(Double.class);
                    String address = fallSnapshot.child("address").getValue(String.class);

                    boolean fallExists = false;
                    for (Fall fall : fallArrayList) {
                        if (fall.getfallID().equals(fallID)) {
                            fallExists = true;
                            break;
                        }
                    }

                    if (!fallExists) {
                        fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, 
                            impactSeverity, fallDirection, latitude, longitude, address));
                        fallItemAdapter.notifyItemInserted(0);
                        recyclerView.scrollToPosition(0);

                        if (!startup) {
                        // notifyFallToast(HomeActivity.this);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    // Takes user to linking page
    public void accountLink(View view) {
        Intent intent = new Intent(this, AccountLink.class);
        startActivity(intent);
    }


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

    private void addLocationToFall(String fallId) {
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fireUser != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
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
                    DatabaseReference fallRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(fireUser.getUid())
                        .child("falls")
                        .child(fallId);
                        
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
             Log.w(TAG, "Location permission not granted or Firebase user is null.");
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
        String staticMapUrl = String.format(
            "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=600x300&markers=color:red%%7C%f,%f&key=%s",
            latitude, longitude, latitude, longitude, BuildConfig.MAPS_API_KEY
        );

        // Create intent to open Google Maps
        Intent mapIntent = new Intent(Intent.ACTION_VIEW);
        mapIntent.setData(Uri.parse(String.format("geo:%f,%f?q=%f,%f", latitude, longitude, latitude, longitude)));
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
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String time = intent.getStringExtra("time");
                String date = intent.getStringExtra("date");
                int heartRate = intent.getIntExtra("heartRate", 0);
                int deltaHeartRate = intent.getIntExtra("deltaHeartRate", 0);
                double impactSeverity = intent.getDoubleExtra("impactSeverity", 0.0);
                String fallDirection = intent.getStringExtra("fallDirection");
                
                // Use existing addFallEntry method
                addFallEntry(currentUser.getUid(), time, date, deltaHeartRate, heartRate, 
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
            Log.i(TAG, "Alexa Skill credentials loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Error reading alexa_skill.json from assets", e);
            alexaSkillClientId = null;
            alexaSkillClientSecret = null;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON from alexa_skill.json", e);
            alexaSkillClientId = null;
            alexaSkillClientSecret = null;
        }
    }

    private void getProactiveEventsAccessToken() {
        // Prevent multiple simultaneous token requests or if credentials failed to load
        if (alexaSkillClientId == null || alexaSkillClientSecret == null) {
             Log.e(TAG, "Cannot get token: Alexa credentials not loaded.");
             // Optionally show a persistent error message if this happens after initial load attempt
             return;
        }
        if (System.currentTimeMillis() < tokenExpiryTime - 60000) {
             Log.d(TAG, "Using existing Proactive Events access token.");
             return;
        }
        // Removed check for placeholder strings

        Log.d(TAG, "Requesting new Proactive Events access token...");
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.amazon.com/auth/o2/token");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);

                // Use loaded credentials here
                String postData = "grant_type=client_credentials" +
                                  "&client_id=" + alexaSkillClientId +
                                  "&client_secret=" + alexaSkillClientSecret +
                                  "&scope=alexa::proactive_events";

                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(postData);
                wr.flush();
                wr.close();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Token request response code: " + responseCode);

                BufferedReader in;
                if (responseCode >= 200 && responseCode < 300) {
                    in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                } else {
                    in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                }
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Log.d(TAG, "Token response body: " + response.toString());

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    proactiveEventsAccessToken = jsonResponse.getString("access_token");
                    long expiresIn = jsonResponse.getLong("expires_in"); // Duration in seconds
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000);
                    Log.i(TAG, "Successfully obtained Proactive Events access token.");
                } else {
                    Log.e(TAG, "Error getting Proactive Events access token: " + response.toString());
                    proactiveEventsAccessToken = null;
                    tokenExpiryTime = 0;
                    mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Failed to get Alexa token: " + responseCode, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during token request: " + e.getMessage(), e);
                proactiveEventsAccessToken = null;
                tokenExpiryTime = 0;
                 mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Network error getting Alexa token.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void sendProactiveEventNotification(Fall fallDetails) {
        // Check if credentials were loaded before proceeding
        if (alexaSkillClientId == null || alexaSkillClientSecret == null) {
             Log.e(TAG, "Cannot send notification: Alexa credentials not loaded.");
              mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Error: Missing Alexa credentials.", Toast.LENGTH_LONG).show());
             return;
        }

        // Ensure we have a user ID and a valid token
        if (amazonUserId == null || amazonUserId.isEmpty()) {
            Log.w(TAG, "Cannot send proactive event, Amazon User ID is missing.");
            return;
        }
        if (proactiveEventsAccessToken == null || System.currentTimeMillis() >= tokenExpiryTime) {
            Log.w(TAG, "Proactive Events access token is missing or expired. Requesting new one.");
            getProactiveEventsAccessToken(); // Attempt to get token (will use loaded credentials)
            mainHandler.postDelayed(() -> {
                 if (proactiveEventsAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                     sendProactiveEventInternal(fallDetails);
                 } else {
                     Log.e(TAG, "Failed to get token, cannot send proactive event for fall: " + fallDetails.getfallID());
                 }
            }, 3000);
            return;
        }

        // If token is valid, send immediately
        sendProactiveEventInternal(fallDetails);
    }


    private void sendProactiveEventInternal(Fall fallDetails) {
        final String currentToken = proactiveEventsAccessToken;
        final String userIdToSend = amazonUserId; // Use the specific user ID again

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // 1. Construct the Event Payload JSON for AMAZON.Occasion.Updated
                JSONObject eventPayload = new JSONObject();
                eventPayload.put("name", "AMAZON.Occasion.Updated"); // Changed event name

                JSONObject occasionPayload = new JSONObject();
                occasionPayload.put("state", "CONFIRMED"); // State of the occasion (e.g., CONFIRMED, CANCELED)
                occasionPayload.put("bookingTime", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));

                JSONObject provider = new JSONObject();
                provider.put("name", "localizedattribute:providerName");
                occasionPayload.put("provider", provider);

                occasionPayload.put("subject", "localizedattribute:occasionSubject");

                eventPayload.put("payload", occasionPayload);

                // 2. Construct the Full Request Body JSON
                JSONObject requestBody = new JSONObject();
                requestBody.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
                requestBody.put("referenceId", UUID.randomUUID().toString()); // Use UUID
                requestBody.put("expiryTime", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date(System.currentTimeMillis() + 3600 * 1000 * 24)));
                requestBody.put("event", eventPayload);

                // --- Add Localized Attributes ---
                JSONArray localizedAttributes = new JSONArray();
                JSONObject enUSAttributes = new JSONObject();
                enUSAttributes.put("locale", "en-US");
                enUSAttributes.put("providerName", "RiskWatch");
                enUSAttributes.put("occasionSubject", "Fall Detected Alert");
                localizedAttributes.put(enUSAttributes);
                requestBody.put("localizedAttributes", localizedAttributes);
                // --- End Localized Attributes ---

                // --- Switch back to Unicast ---
                JSONObject relevantAudience = new JSONObject();
                relevantAudience.put("type", "Unicast");
                relevantAudience.put("payload", new JSONObject().put("user", userIdToSend));
                requestBody.put("relevantAudience", relevantAudience);
                // --- End Unicast ---

                String jsonInputString = requestBody.toString();
                Log.d(TAG, "Proactive Event Request Body (Occasion.Updated - Unicast): " + jsonInputString);

                // 3. Make the HTTP POST Request
                URL url = new URL(PROACTIVE_EVENTS_API_ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + currentToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(jsonInputString);
                wr.flush();
                wr.close();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Proactive Event POST response code: " + responseCode);

                // ... (rest of response handling) ...
                 if (responseCode >= 200 && responseCode < 300) {
                    Log.i(TAG, "Successfully sent proactive event (Occasion.Updated) for fall ID: " + fallDetails.getfallID());
                     if (connection.getInputStream() != null) {
                         BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                         String inputLine;
                         StringBuilder response = new StringBuilder();
                         while ((inputLine = in.readLine()) != null) {
                             response.append(inputLine);
                         }
                         in.close();
                         Log.d(TAG, "Proactive Event Success Response: " + response.toString());
                     }
                    mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Alexa notification sent.", Toast.LENGTH_SHORT).show()); // Add success toast
                } else {
                     if (connection.getErrorStream() != null) {
                         BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                         String inputLine;
                         StringBuilder response = new StringBuilder();
                         while ((inputLine = in.readLine()) != null) {
                             response.append(inputLine);
                         }
                         in.close();
                         Log.e(TAG, "Error sending proactive event: " + responseCode + " - " + response.toString());
                         mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Failed to send Alexa notification: " + responseCode, Toast.LENGTH_LONG).show());
                     } else {
                          Log.e(TAG, "Error sending proactive event: " + responseCode + " - No error stream");
                          mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Failed to send Alexa notification: " + responseCode, Toast.LENGTH_LONG).show());
                     }
                }


            } catch (Exception e) {
                 Log.e(TAG, "Exception during proactive event sending: " + e.getMessage(), e);
                 mainHandler.post(() -> Toast.makeText(HomeActivity.this, "Network error sending Alexa notification.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }


    // --- End Methods for Alexa Proactive Events ---
}
