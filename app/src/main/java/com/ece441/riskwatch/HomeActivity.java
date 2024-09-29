package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Set;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

// Firebase imports
import com.google.firebase.database.*;
import com.google.firebase.ktx.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.util.Random;

public class HomeActivity extends AppCompatActivity {

    private static final ArrayList<Fall> fallArrayList = new ArrayList<>();
    private RecyclerView recyclerView;
    private static FallItemAdapter fallItemAdapter;

    User currentUser;

    boolean startup = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Intent intent = getIntent();

        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();

        if (intent != null) {
            String username = intent.getStringExtra("user");
            currentUser = new User(username);
            Log.d(TAG, "HOME USERNAME " + currentUser.getUserName());
            assert fireUser != null;
            Log.d(TAG, "FireAuth UID: " + fireUser.getUid());
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
//                addFallEntry(fireUser.getUid(),  "06:48 PM", "01/05/24", 15, 92, "Front", 2.6);

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
    }

    // Android "Toast" notif. Only appears in app
    public void notifyFallToast(Context context/*, String fallTime String fallData, String fallDirection*/) {
        String notif = "Fall detected";
        Toast.makeText(context, notif, Toast.LENGTH_LONG).show();
    }

    // Banner notif here

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
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();
        addFallEntry(fireUser.getUid(), "03:02 AM", "03/20/24", 20, 100, "Left", 0.4);
    }

    // Gets passed fall metrics, creates new fall in database for current user
    public void addFallEntry(String userId, String time, String date,
                             int deltaHeartRate, int heartRate, String fallDirection, double impactSeverity) {
        // Get a reference to the "users" directory in the Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        // Get a reference to the "falls" directory under the specific user
        DatabaseReference fallsRef = usersRef.child(userId).child("falls");

        // Generate a unique key for the new fall entry
        String fallId = fallsRef.push().getKey();
        assert fallId != null;

        // Write the fall data to the database under the generated fall ID
        fallsRef.child(fallId).child("time").setValue(time);
        fallsRef.child(fallId).child("date").setValue(date);
        fallsRef.child(fallId).child("deltaHeartRate").setValue(deltaHeartRate);
        fallsRef.child(fallId).child("heartRate").setValue(heartRate);
        fallsRef.child(fallId).child("fallDirection").setValue(fallDirection);
        fallsRef.child(fallId).child("impactSeverity").setValue(impactSeverity);
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

                    boolean fallExists = false;
                    for (Fall fall : fallArrayList) {
                        if (fall.getfallID().equals(fallID)) {
                            fallExists = true;
                            break;
                        }
                    }

                    if (!fallExists) {
                        fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
                        fallItemAdapter.notifyItemInserted(0);
                        recyclerView.scrollToPosition(0);

                        notifyFallBanner(HomeActivity.this, "Now", "Today", "Wherever");

                        if (!startup) {
//                            notifyFallToast(HomeActivity.this);

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

    // ANYTHING AFTER THIS IS BLUETOOTH BUT UNTESTED/INCORRECT

    public class BluetoothDataReceiverActivity extends AppCompatActivity {

        private static final String TAG = "BluetoothDataReceiver";

        private BluetoothAdapter bluetoothAdapter;
        private BluetoothGatt bluetoothGatt;
        String deviceAddress = "00:11:22:33:44:55";

//        private BluetoothGattCharacteristic = "0000dfb1-0000-1000-8000-00805f9b34fb";

        private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "BluetoothGattCallback: Device connected");
                    // Discover services when connected
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "BluetoothGattCallback: Device disconnected");
                    // Handle device disconnection
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                // Received data from Bluetooth device
                byte[] data = characteristic.getValue();
                if (data != null) {
                    String receivedData = new String(data); // Convert byte array to String
                    Log.d(TAG, "Received data from Bluetooth device: " + receivedData);
                    // Process received data as needed
                    processData(receivedData);
                }
            }
        };

        @SuppressLint("MissingPermission")
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Initialize BluetoothAdapter
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : pairedDevices) {
                if ("RiskWatch".equals(device.getName())) {
                    // Found the device named "RiskWatch", retrieve its address
                    String deviceName = device.getName();
                    deviceAddress = device.getAddress();
                    Log.d(TAG, "Found device: " + deviceName + ", Address: " + deviceAddress);
                    break; // Exit loop after finding the device
                }
            }


            // Example: Connect to a specific Bluetooth device (already paired)
            deviceAddress = "";
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bluetoothGatt = bluetoothDevice.connectGatt(this, false, bluetoothGattCallback);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            // Clean up resources
            if (bluetoothGatt != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }

        private void processData(String data) {
            notifyFallToast(HomeActivity.this);
            // Process the received data, update UI, or trigger further actions
            // Example: Update UI with received data
            // textView.setText(data);
        }
    }

}