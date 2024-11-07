package com.ece441.riskwatch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.*;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;

public class FallAnalysisActivity extends AppCompatActivity {
    private TextView avgSeverityText, commonDirectionText, avgHeartRateText, commonTimeText;
    private TextView locationAnalysisText;
    private RecyclerView recyclerView;
    private MapView mapView;
    private static final String MAPS_API_KEY = BuildConfig.MAPS_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fall_analysis);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.navigation_analysis);
        
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
                Intent intent = new Intent(this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_analysis) {
                return true;
            }
            return false;
        });

        initializeViews();
        analyzeFallData();
    }

    private void initializeViews() {
        avgSeverityText = findViewById(R.id.avgSeverityText);
        commonDirectionText = findViewById(R.id.commonDirectionText);
        avgHeartRateText = findViewById(R.id.avgHeartRateText);
        commonTimeText = findViewById(R.id.commonTimeText);
        recyclerView = findViewById(R.id.fallAnalysisRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationAnalysisText = findViewById(R.id.locationAnalysisText);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(null);
        mapView.getMapAsync(this::onMapReady);
    }

    private void analyzeFallData() {
        ArrayList<Fall> fallList = HomeActivity.getFallArrayList();
        Map<String, Integer> directionCount = new HashMap<>();
        Map<String, Integer> timeCount = new HashMap<>();
        Map<String, Integer> locationCount = new HashMap<>();
        List<LatLng> fallLocations = new ArrayList<>();
        
        double totalSeverity = 0;
        double totalHeartRateChange = 0;
        double avgLat = 0, avgLng = 0;
        int count = fallList.size();

        for (Fall fall : fallList) {
            directionCount.put(fall.getFallDirection(), 
                directionCount.getOrDefault(fall.getFallDirection(), 0) + 1);
            timeCount.put(fall.getTime(), 
                timeCount.getOrDefault(fall.getTime(), 0) + 1);
            String address = fall.getAddress();
            locationCount.put(address, locationCount.getOrDefault(address, 0) + 1);
            fallLocations.add(new LatLng(fall.getLatitude(), fall.getLongitude()));
            avgLat += fall.getLatitude();
            avgLng += fall.getLongitude();
            totalSeverity += fall.getImpactSeverity();
            totalHeartRateChange += fall.getDeltaHeartRate();
        }

        if (count > 0) {
            avgLat /= count;
            avgLng /= count;
        }

        updateAnalysis(directionCount, timeCount, locationCount, totalSeverity, 
            totalHeartRateChange, count, fallLocations, new LatLng(avgLat, avgLng));
    }

    private void updateAnalysis(Map<String, Integer> directionCount, 
                              Map<String, Integer> timeCount,
                              Map<String, Integer> locationCount,
                              double totalSeverity, 
                              double totalHeartRateChange, 
                              int count,
                              List<LatLng> fallLocations,
                              LatLng centerPoint) {
        if (count > 0) {
            double avgSeverity = totalSeverity / count;
            avgSeverityText.setText(String.format("Average Severity: %.2f", avgSeverity));

            String commonDirection = Collections.max(directionCount.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            commonDirectionText.setText("Most Common Direction: " + commonDirection);

            double avgHeartRateChange = totalHeartRateChange / count;
            avgHeartRateText.setText(String.format("Average Heart Rate Change: %.1f", 
                avgHeartRateChange));

            String commonTime = Collections.max(timeCount.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            commonTimeText.setText("Most Common Time: " + commonTime);

            String commonLocation = Collections.max(locationCount.entrySet(), 
                Map.Entry.comparingByValue()).getKey();
            int commonLocationCount = locationCount.get(commonLocation);
            
            locationAnalysisText.setText(String.format(
                "Most Common Location: %s (%d falls)\nTotal Locations: %d", 
                commonLocation, commonLocationCount, locationCount.size()));
            
            updateMap(fallLocations, centerPoint);
        } else {
            avgSeverityText.setText("No falls recorded");
            commonDirectionText.setText("No falls recorded");
            avgHeartRateText.setText("No falls recorded");
            commonTimeText.setText("No falls recorded");
            locationAnalysisText.setText("No locations recorded");
            
            updateMap(new ArrayList<>(), new LatLng(0, 0));
        }
    }

    private void updateMap(List<LatLng> fallLocations, LatLng centerPoint) {
        mapView.getMapAsync(googleMap -> {
            googleMap.clear();
            
            // Add markers for each fall
            for (LatLng location : fallLocations) {
                googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            }
            
            // Add heat map if more than 3 falls
            if (fallLocations.size() > 3) {
                HeatmapTileProvider provider = new HeatmapTileProvider.Builder()
                    .data(fallLocations)
                    .radius(50)
                    .build();
                googleMap.addTileOverlay(new TileOverlayOptions().tileProvider(provider));
            }
            
            // Move camera to center of falls
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerPoint, 15));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
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

    private void onMapReady(GoogleMap googleMap) {
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        
        // Initial camera position (will be updated when falls are loaded)
        LatLng defaultLocation = new LatLng(0, 0);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2));
        
        // Analyze and display fall data
        analyzeFallData();
    }
} 