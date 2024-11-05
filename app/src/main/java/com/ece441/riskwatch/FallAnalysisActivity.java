package com.ece441.riskwatch;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class FallAnalysisActivity extends AppCompatActivity {
    private TextView avgSeverityText, commonDirectionText, avgHeartRateText, commonTimeText;
    private RecyclerView recyclerView;
    private List<Fall> fallList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fall_analysis);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.navigation_analysis);
        
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_bluetooth) {
                if (!(this instanceof BluetoothActivity)) {
                    startActivity(new Intent(this, BluetoothActivity.class));
                }
                return true;
            } else if (itemId == R.id.navigation_settings) {
                showSettingsDialog();
                return true;
            } else if (itemId == R.id.navigation_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (itemId == R.id.navigation_analysis) {
                if (!(this instanceof FallAnalysisActivity)) {
                    startActivity(new Intent(this, FallAnalysisActivity.class));
                }
                return true;
            }
            return false;
        });

        initializeViews();
        loadFallData();
    }

    private void initializeViews() {
        avgSeverityText = findViewById(R.id.avgSeverityText);
        commonDirectionText = findViewById(R.id.commonDirectionText);
        avgHeartRateText = findViewById(R.id.avgHeartRateText);
        commonTimeText = findViewById(R.id.commonTimeText);
        recyclerView = findViewById(R.id.fallAnalysisRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadFallData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference fallsRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("falls");

        fallsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                fallList.clear();
                Map<String, Integer> directionCount = new HashMap<>();
                Map<String, Integer> timeCount = new HashMap<>();
                double totalSeverity = 0;
                double totalHeartRateChange = 0;
                int count = 0;

                for (DataSnapshot fallSnapshot : dataSnapshot.getChildren()) {
                    Fall fall = fallSnapshot.getValue(Fall.class);
                    if (fall != null) {
                        fallList.add(fall);
                        
                        // Update statistics
                        directionCount.put(fall.getFallDirection(), 
                            directionCount.getOrDefault(fall.getFallDirection(), 0) + 1);
                        timeCount.put(fall.getTime(), 
                            timeCount.getOrDefault(fall.getTime(), 0) + 1);
                        totalSeverity += fall.getImpactSeverity();
                        totalHeartRateChange += fall.getDeltaHeartRate();
                        count++;
                    }
                }

                updateAnalysis(directionCount, timeCount, totalSeverity, totalHeartRateChange, count);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void updateAnalysis(Map<String, Integer> directionCount, 
                              Map<String, Integer> timeCount,
                              double totalSeverity, 
                              double totalHeartRateChange, 
                              int count) {
        if (count > 0) {
            // Update average severity
            double avgSeverity = totalSeverity / count;
            avgSeverityText.setText(String.format("Average Severity: %.2f", avgSeverity));

            // Update most common direction
            String commonDirection = Collections.max(directionCount.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            commonDirectionText.setText("Most Common Direction: " + commonDirection);

            // Update average heart rate change
            double avgHeartRateChange = totalHeartRateChange / count;
            avgHeartRateText.setText(String.format("Average Heart Rate Change: %.1f", 
                avgHeartRateChange));

            // Update most common time
            String commonTime = Collections.max(timeCount.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            commonTimeText.setText("Most Common Time: " + commonTime);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings")
               .setItems(new CharSequence[]{"Account Linking", "Logout"}, (dialog, which) -> {
                   if (which == 0) {
                       startActivity(new Intent(this, AccountLink.class));
                   } else if (which == 1) {
                       startActivity(new Intent(this, LoginScreen.class));
                   }
               })
               .show();
    }
} 