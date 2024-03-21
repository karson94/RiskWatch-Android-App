package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

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

    //Fall fall1 = new Fall("2:00pm", "2/26/24", 86, 0.7, "Back");
    //Fall fall2 = new Fall("12:05pm", "2/21/24", 30, 4.5, "Right");

    private static final ArrayList<Fall> fallArrayList = new ArrayList<>();
    private RecyclerView recyclerView;
    private static FallItemAdapter fallItemAdapter;

    User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Intent intent = getIntent();

        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();

        if (intent != null){
            String username = intent.getStringExtra("user");
            currentUser = new User(username);
            Log.d(TAG, "HOME USERNAME " + currentUser.getUserName());
            assert fireUser != null;
            Log.d(TAG, "FireAuth UID: " + fireUser.getUid());
        }

        //initReadDB("Bob");
        initRead();


        TextView userNameDisplay = findViewById(R.id.userNameView);
        userNameDisplay.setText("Hi " + currentUser.getUserName() + "!");

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("users");

        usersRef.orderByChild("name").equalTo(currentUser.getUserName()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    // User "Bob" does not exist, so create a new entry
                    createUserDB(currentUser.getUserName());
                }

                // Add a fall entry for user
//                addFallEntry(fireUser.getUid(),  "06:48 PM", "01/05/24", 15, 92, "Front", 2.6);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        recyclerView = findViewById(R.id.fallRecycler);
        fallItemAdapter = new FallItemAdapter(fallArrayList, this);
        recyclerView.setAdapter(fallItemAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

//        Thread thread = new Thread(){
//            @Override
//            public void run(){
//                try{
//                    initRead();
//                } catch(Exception e){
//                    e.printStackTrace();
//                }
//                return;
//            };
//        };
//
//        thread.start();

//        listenForNewFalls();
//        delay(50000);
//        addFallEntry(fireUser.getUid(),  "06:48 PM", "05/05/24", 15, 92, "Front", 2.6);

        //        registerBluetoothReceiver();
        //        connectToBluetoothDevice();
    }

    public void logOut(View view){
        Intent intent = new Intent(this, LoginScreen.class);
        int faSize = fallArrayList.size();
        fallArrayList.clear();
        fallItemAdapter.notifyItemRangeRemoved(0,(faSize - 1));
        startActivity(intent);
    }

    public void addRandFall(View view){
        FirebaseUser fireUser = FirebaseAuth.getInstance().getCurrentUser();
//        addFallEntry(fireUser.getUid(),  "06:48 PM", "05/05/24", 15, 92, "Front", 2.6);
//            Random random = new Random();
//
//            // Random time in HH:MM AM/PM format
//            int hour = random.nextInt(12) + 1;
//            int minute = random.nextInt(60);
//            String am_pm = random.nextBoolean() ? "AM" : "PM";
//            String timeStr = String.format("%02d:%02d %s", hour, minute, am_pm);
//
//            // Random date in MM/DD/YY format
//            int month = random.nextInt(12) + 1;
//            int day = random.nextInt(28) + 1; // Assuming all months have max 28 days
//            int year = random.nextInt(3) + 22; // Random year between 2022 and 2024
//            String dateStr = String.format("%02d/%02d/%02d", month, day, year);
//
//            // Random delta Heart rate between -30 and 30
//            int deltaHr = random.nextInt(61) - 30;
//
//            // Random heart rate between 60 and 115
//            int heartRate = random.nextInt(56) + 60;
//
//            // Random direction
//            String[] directions = {"Front", "Back", "Left", "Right"};
//            String direction = directions[random.nextInt(directions.length)];
//
//            // Random impact severity in g (increments of 0.2 between 0.3 and 2.4)
//            double impactSeverity = Math.round((random.nextDouble() * 2.1 + 0.3) * 10) / 10.0;

            // Construct the entry string
    //        addFallEntry(fireUser.getUid(),  timeStr, dateStr, deltaHr, heartRate, direction, impactSeverity);
        addFallEntry(fireUser.getUid(),  "03:02 AM", "03/20/24", 20, 100, "Left", 0.4);
        initRead();
    }

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
                    }


//                    fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
//                    fallItemAdapter.notifyItemInserted(0);


                    // Notify user of the fall


                    // Now you have access to all fall attributes
                    // Perform any further processing or UI updates here
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    public void initReadDB() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userRef = database.getReference("users");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            return;
        }
        String userID = currentUser.getUid();
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

                    fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
                    fallItemAdapter.notifyItemInserted(0);

                        // Now you have access to all fall attributes
                        // Perform any further processing or UI updates here
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    public void listenForNewFalls() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, handle this case
            return;
        }
        String ownerId = currentUser.getUid();
        final String[] linkedAccountId = new String[1];
        String linkAccID;

        DatabaseReference currentUserFallsRef;
        if (fallArrayList.isEmpty()) {
            // If the falls list is empty, use the current user's ID
            currentUserFallsRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(ownerId).child("falls");
        } else {
            // User does not have falls, check if linked to another account
            DatabaseReference permissionsRef = FirebaseDatabase.getInstance().getReference("permissions");

            permissionsRef.orderByChild("grantedUsers/" + ownerId).equalTo(true).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // User is linked to another account, retrieve the owner ID
                        for (DataSnapshot permissionSnapshot : dataSnapshot.getChildren()) {
                            linkedAccountId[0] = permissionSnapshot.getKey();
                        }
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Failed to read value.", error.toException());
                }
            });

            // If the falls list is not empty, use the linked account's ID
            linkAccID = linkedAccountId[0]; // You need to implement this method
            if (linkAccID == null) {
                // If there's no linked account, return
                return;
            }
            currentUserFallsRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(linkAccID).child("falls");
        }

        // Add a ChildEventListener to listen for new falls
        currentUserFallsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // Retrieve the new fall data and add it to the display
                String fallID = dataSnapshot.getKey();

                boolean fallExists = false;
                for (Fall fall : fallArrayList) {
                    if (fall.getfallID().equals(fallID)) {
                        // Fall already exists, set flag and break loop
                        fallExists = true;
                        break;
                    }

                    if (!fallExists) {
                        String time = dataSnapshot.child("time").getValue(String.class);
                        String date = dataSnapshot.child("date").getValue(String.class);
                        int heartRate = dataSnapshot.child("heartRate").getValue(Integer.class);
                        int deltaHeartRate = dataSnapshot.child("deltaHeartRate").getValue(Integer.class);
                        double impactSeverity = dataSnapshot.child("impactSeverity").getValue(Double.class);
                        String fallDirection = dataSnapshot.child("fallDirection").getValue(String.class);

                        fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
                        fallItemAdapter.notifyItemInserted(0);

                        // Now you have access to all fall attributes
                        // Perform any further processing or UI updates here
                    }

                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // Handle case where a fall's data has changed (if needed)
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                // Handle case where a fall has been removed (if needed)
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                // Handle case where a fall has changed position (if needed)
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

//    public void initReadDB(String userName) {
//        FirebaseDatabase database = FirebaseDatabase.getInstance();
//        DatabaseReference userRef = database.getReference("users");
//
//        userRef.orderByChild("name").equalTo(userName).addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                if (dataSnapshot.exists()) {
//                    for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
//                        String userID = userSnapshot.getKey();
//
//                        // Now you have the user ID (userID) for the specified user
//                        // Continue to read falls for this user
//                        assert userID != null;
//                        DatabaseReference fallsRef = userRef.child(userID).child("falls");
//
//                        fallsRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                            @Override
//                            public void onDataChange(@NonNull DataSnapshot fallsSnapshot) {
//                                for (DataSnapshot fallSnapshot : fallsSnapshot.getChildren()) {
//                                    // Process each fall as needed
//                                    String fallID = fallSnapshot.getKey();
//                                    String time = fallSnapshot.child("time").getValue(String.class);
//                                    String date = fallSnapshot.child("date").getValue(String.class);
//
//                                    int heartRate = fallSnapshot.child("heartRate").getValue(Integer.class);
//
//                                    int deltaHeartRate = fallSnapshot.child("deltaHeartRate").getValue(Integer.class);
//
//                                    double impactSeverity = fallSnapshot.child("impactSeverity").getValue(Double.class);
//
//                                    String fallDirection = fallSnapshot.child("fallDirection").getValue(String.class);
//
//                                    fallArrayList.add(0, new Fall(fallID, time, date, heartRate, deltaHeartRate, impactSeverity, fallDirection));
//                                    fallItemAdapter.notifyItemInserted(0);
//
//                                    // Now you have access to all fall attributes
//                                    // Perform any further processing or UI updates here
//                                }
//                            }
//
//                            @Override
//                            public void onCancelled(DatabaseError error) {
//                                Log.w(TAG, "Failed to read value.", error.toException());
//                            }
//                        });
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError databaseError) {
//                // Handle errors here
//            }
//        });
//    }

    public void accountLink(View view) {
        Intent intent = new Intent(this, AccountLink.class);
        startActivity(intent);
    }


    // Function to write a user entry
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