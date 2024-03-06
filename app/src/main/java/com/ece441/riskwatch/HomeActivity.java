package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

// Firebase imports
import com.google.firebase.database.*;
import com.google.firebase.ktx.Firebase;


import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    //Fall fall1 = new Fall("2:00pm", "2/26/24", 86, 0.7, "Back");
    //Fall fall2 = new Fall("12:05pm", "2/21/24", 30, 4.5, "Right");

    private static ArrayList<Fall> fallArrayList = new ArrayList<>();
    private RecyclerView recyclerView;
    private static FallItemAdapter fallItemAdapter;

    User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Intent intent = getIntent();

        if (intent != null){
            String username = intent.getStringExtra("user");
            currentUser = new User(username);
            Log.d(TAG, "USERNAME" + currentUser.getUserName());
        }

        //initReadDB("Bob");
        initReadDB(currentUser.getUserName());

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        String username = "Alice";

        usersRef.orderByChild("name").equalTo(username).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    // User "Bob" does not exist, so create a new entry
                    writeUserData(username);
                }

                // Add a fall entry for user "Bob" (if not already added)
                //addFallEntry(username, "now", "today", 10, 90, "Left", 5.0);

                //addFallEntry("Bob", "newTime", "newDate", 15, 95, "Right", 7.5);


                // Continue with the rest of your code
                recyclerView = findViewById(R.id.fallRecycler);
                fallItemAdapter = new FallItemAdapter(fallArrayList, HomeActivity.this);
                recyclerView.setAdapter(fallItemAdapter);
                recyclerView.setLayoutManager(new LinearLayoutManager(HomeActivity.this, LinearLayoutManager.VERTICAL, false));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });


//        Thread thread = new Thread(){
//            @Override
//            public void run() {
//                try {
//                    initReadDB();
//                    //readDB();
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//                return;
//            };
//        };
//        thread.start();

//        registerBluetoothReceiver();
//        connectToBluetoothDevice();

        //fallArrayList.add(fall1);
        //fallArrayList.add(fall2);


        recyclerView = findViewById(R.id.fallRecycler);
        fallItemAdapter = new FallItemAdapter(fallArrayList, this);
        recyclerView.setAdapter(fallItemAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
    }

    public void addFallEntry(String userName, String time, String date,
                             int deltaHeartRate, int heartRate, String fallDirection, double impactSeverity) {
        // Get a reference to the "users" directory in the Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        // Query the database to find the user with the given name
        Query query = usersRef.orderByChild("name").equalTo(userName);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Assuming there is only one child under 'users' with the specified name
                    DataSnapshot userSnapshot = dataSnapshot.getChildren().iterator().next();

                    // Get the user ID
                    String userId = userSnapshot.getKey();

                    // Get a reference to the "falls" directory under the specific user
                    DatabaseReference fallsRef = usersRef.child(userId).child("falls");

                    // Generate a unique key for the new fall entry
                    String fallId = fallsRef.push().getKey();

                    // Write the fall data to the database under the generated fall ID
                    fallsRef.child(fallId).child("time").setValue(time);
                    fallsRef.child(fallId).child("date").setValue(date);
                    fallsRef.child(fallId).child("deltaHeartRate").setValue(deltaHeartRate);
                    fallsRef.child(fallId).child("heartRate").setValue(heartRate);
                    fallsRef.child(fallId).child("fallDirection").setValue(fallDirection);
                    fallsRef.child(fallId).child("impactSeverity").setValue(impactSeverity);
                } else {
                    // Handle the case where no user with the specified name is found
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors here
            }
        });
    }

    public void readDB() {

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference user = database.getReference("userID");

        user.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                String time = dataSnapshot.child("fallTime").getValue(String.class);
                String date= dataSnapshot.child("fallDate").getValue(String.class);

                String heartRateString = dataSnapshot.child("currentHeartRate").getValue(String.class);
                int heartRate = 0; // Default value or any other appropriate default
                if (heartRateString != null && !heartRateString.isEmpty()) {
                    try {
                        heartRate = Integer.parseInt(heartRateString);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }

                String deltaHeartRateString = dataSnapshot.child("currentHeartRate").getValue(String.class);
                int deltaHeartRate = 0; // Default value or any other appropriate default
                if (deltaHeartRateString != null && !deltaHeartRateString.isEmpty()) {
                    try {
                        deltaHeartRate = Integer.parseInt(deltaHeartRateString);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }

                String impactSeverityString = dataSnapshot.child("impactSeverity").getValue(String.class);
                double impactSeverity = 0; // Default value or any other appropriate default
                if (impactSeverityString != null && !impactSeverityString.isEmpty()) {
                    try {
                        impactSeverity = Integer.parseInt(impactSeverityString);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }

                String fallDirection = dataSnapshot.child("fallDirection").getValue(String.class);

                //fallArrayList.add(new Fall(time, date, heartRate, impactSeverity, fallDirection));


            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    public void initReadDB(String userName) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userRef = database.getReference("users");

        userRef.orderByChild("name").equalTo(userName).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                        String userID = userSnapshot.getKey();

                        // Now you have the user ID (userID) for the specified user
                        // Continue to read falls for this user
                        DatabaseReference fallsRef = userRef.child(userID).child("falls");

                        fallsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot fallsSnapshot) {
                                for (DataSnapshot fallSnapshot : fallsSnapshot.getChildren()) {
                                    // Process each fall as needed
                                    String fallID = fallSnapshot.getKey();
                                    String time = fallSnapshot.child("time").getValue(String.class);
                                    String date = fallSnapshot.child("date").getValue(String.class);

                                    int heartRate = fallSnapshot.child("heartRate").getValue(Integer.class);

                                    int deltaHeartRate = fallSnapshot.child("deltaHeartRate").getValue(Integer.class);

                                    double impactSeverity = fallSnapshot.child("impactSeverity").getValue(Double.class);

                                    String fallDirection = fallSnapshot.child("fallDirection").getValue(String.class);

                                    fallArrayList.add(new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
                                    fallItemAdapter.notifyItemInserted(fallArrayList.size() - 1);

                                    // Now you have access to all fall attributes
                                    // Perform any further processing or UI updates here
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.w(TAG, "Failed to read value.", error.toException());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors here
            }
        });
    }


    // Function to write a user entry
    public static void writeUserData(String userName) {
        // Get a reference to the "users" directory in the Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        // Generate a unique key for the new user entry
        String userId = usersRef.push().getKey();

        // Write the user name to the database under the specified user ID
        usersRef.child(userId).child("name").setValue(userName);
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // Handle connection state changes
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Handle characteristic changes (received data)
            byte[] data = characteristic.getValue();
            if (data != null) {
                // Process and display the received data
                updateData(new String(data));
            }
        }
    };

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handle Bluetooth events here
        }
    };

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothGatt bluetoothGatt;

    private String bleDeviceAddress = "Your_BLE_Device_Address";

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        disconnectBluetoothDevice();
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void connectToBluetoothDevice() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(bleDeviceAddress);
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
        }
    }

    private void disconnectBluetoothDevice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }
    private void updateData(String data) {
        // Update your UI with the received data
    }
}