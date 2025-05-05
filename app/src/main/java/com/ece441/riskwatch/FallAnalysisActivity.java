package com.ece441.riskwatch;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

public class FallAnalysisActivity extends AppCompatActivity implements OnMapReadyCallback {
    private TextView avgSeverityText, commonDirectionText, avgHeartRateText, commonTimeText;
    private TextView locationAnalysisText;
    private RecyclerView recyclerView;
    private MapView mapView;
    private GoogleMap googleMapInstance = null;
    private List<LatLng> fallLocationsForMap = new ArrayList<>();
    private LatLng centerPointForMap = new LatLng(0, 0);

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
                // Finish current activity to go back to existing HomeActivity instance
                finish(); 
                return true;
            } else if (itemId == R.id.navigation_analysis) {
                return true;
            }
            return false;
        });

        initializeViews();
    }

    private void initializeViews() {
        avgSeverityText = findViewById(R.id.avgSeverityText);
        commonDirectionText = findViewById(R.id.commonDirectionText);
        avgHeartRateText = findViewById(R.id.avgHeartRateText);
        commonTimeText = findViewById(R.id.commonTimeText);
        recyclerView = findViewById(R.id.fallAnalysisRecycler);
        if (recyclerView != null) {
             recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
        locationAnalysisText = findViewById(R.id.locationAnalysisText);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(null);
        mapView.getMapAsync(this);
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
        Log.d("FallAnalysisActivity", "analyzeFallData started. Fall count from HomeActivity: " + count);

        for (Fall fall : fallList) {
            String address = fall.getAddress();
            if (address != null && !address.equalsIgnoreCase("Unknown location") &&
                fall.getLatitude() != 0 && fall.getLongitude() != 0 &&
                !Double.isNaN(fall.getLatitude()) && !Double.isNaN(fall.getLongitude()) &&
                !Double.isInfinite(fall.getLatitude()) && !Double.isInfinite(fall.getLongitude())) {
                 fallLocations.add(new LatLng(fall.getLatitude(), fall.getLongitude()));
                 avgLat += fall.getLatitude();
                 avgLng += fall.getLongitude();
            } else {
                 Log.w("FallAnalysisActivity", "Skipping fall with potentially invalid coordinates: Lat=" + fall.getLatitude() + ", Lng=" + fall.getLongitude());
            }
            directionCount.put(fall.getFallDirection(), 
                directionCount.getOrDefault(fall.getFallDirection(), 0) + 1);
            timeCount.put(fall.getTime(), 
                timeCount.getOrDefault(fall.getTime(), 0) + 1);
            locationCount.put(address, locationCount.getOrDefault(address, 0) + 1);
            totalSeverity += fall.getImpactSeverity();
            totalHeartRateChange += fall.getDeltaHeartRate();
        }

        int validLocationCount = fallLocations.size();
        Log.d("FallAnalysisActivity", "Valid locations count for map: " + validLocationCount);
        if (validLocationCount > 0) {
            avgLat /= validLocationCount;
            avgLng /= validLocationCount;
            this.centerPointForMap = new LatLng(avgLat, avgLng);
        } else {
            this.centerPointForMap = new LatLng(0, 0);
            Log.w("FallAnalysisActivity", "No valid fall locations found for map centering. Using (0,0).");
        }
        this.fallLocationsForMap = fallLocations;

        updateAnalysisTextViews(directionCount, timeCount, locationCount, totalSeverity, 
            totalHeartRateChange, count);

        Log.d("FallAnalysisActivity", "analyzeFallData finished.");

        if (this.googleMapInstance != null) {
            Log.d("FallAnalysisActivity", "Map was already ready, calling updateMap now.");
            updateMap(this.googleMapInstance, this.fallLocationsForMap, this.centerPointForMap);
        }
    }

    private void updateAnalysisTextViews(Map<String, Integer> directionCount, 
                                      Map<String, Integer> timeCount,
                                      Map<String, Integer> locationCount,
                                      double totalSeverity, 
                                      double totalHeartRateChange, 
                                      int count) {
        Log.d("FallAnalysisActivity", "updateAnalysisTextViews called. Total fall count: " + count);
        if (count > 0) {
            double avgSeverity = totalSeverity / count;
            avgSeverityText.setText(String.format("Average Severity: %.2f", avgSeverity));

            String commonDirection = "N/A";
            if (!directionCount.isEmpty()) {
                commonDirection = Collections.max(directionCount.entrySet(), 
                    Map.Entry.comparingByValue()).getKey();
            }
            commonDirectionText.setText("Most Common Direction: " + commonDirection);

            double avgHeartRateChange = totalHeartRateChange / count;
            avgHeartRateText.setText(String.format("Average Heart Rate Change: %.1f", 
                avgHeartRateChange));

            String commonTime = "N/A";
            if (!timeCount.isEmpty()) {
                commonTime = Collections.max(timeCount.entrySet(), 
                    Map.Entry.comparingByValue()).getKey();
            }
            commonTimeText.setText("Most Common Time: " + commonTime);

            String commonLocation = "N/A";
            int commonLocationCount = 0;
            if (!locationCount.isEmpty()) {
                commonLocation = Collections.max(locationCount.entrySet(), 
                    Map.Entry.comparingByValue()).getKey();
                commonLocationCount = locationCount.getOrDefault(commonLocation, 0);
            }
            
            locationAnalysisText.setText(String.format(
                "Most Common Location: %s (%d falls)\nTotal Locations: %d", 
                commonLocation, commonLocationCount, locationCount.size()));
        } else {
            avgSeverityText.setText("No falls recorded");
            commonDirectionText.setText("No falls recorded");
            avgHeartRateText.setText("No falls recorded");
            commonTimeText.setText("No falls recorded");
            locationAnalysisText.setText("No locations recorded");
        }
    }

    private void updateMap(GoogleMap googleMap, List<LatLng> fallLocations, LatLng centerPoint) {
        Log.d("FallAnalysisActivity", "updateMap called. Locations count: " + fallLocations.size() + ", Center: " + centerPoint);
        if (googleMap == null) {
            Log.e("FallAnalysisActivity", "updateMap called with null GoogleMap object!");
            return;
        }

        googleMap.clear();

        if (fallLocations.isEmpty()) {
             Log.d("FallAnalysisActivity", "No fall locations to display on map.");
             return;
        }

        Log.d("FallAnalysisActivity", "First few fall locations for map:");
        for(int i=0; i<Math.min(fallLocations.size(), 3); i++) {
            Log.d("FallAnalysisActivity", "  Location " + i + ": " + fallLocations.get(i));
        }

        for (LatLng location : fallLocations) {
            googleMap.addMarker(new MarkerOptions()
                .position(location)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
        Log.d("FallAnalysisActivity", "Added " + fallLocations.size() + " markers.");

        // Add heat map if 1 or more falls
        // Note: Heatmap might not be visible if radius/zoom/point distribution makes it very small or faint
        if (!fallLocations.isEmpty()) { // Already checked for empty list, but safe check
            Log.d("FallAnalysisActivity", "Attempting to add heatmap.");
            try {
                // Consider making radius dynamic based on zoom or point density if needed
                HeatmapTileProvider provider = new HeatmapTileProvider.Builder()
                    .data(fallLocations)
                    .radius(50) // Experiment with this radius (pixels)
                    .build();
                googleMap.addTileOverlay(new TileOverlayOptions().tileProvider(provider));
                Log.d("FallAnalysisActivity", "Heatmap Tile Overlay added.");
            } catch (Exception e) {
                // Catch potential errors from the heatmap library (e.g., invalid data points)
                Log.e("FallAnalysisActivity", "Error creating or adding heatmap provider: " + e.getMessage(), e);
            }
        }

        // Move camera to center of falls
        try {
             Log.d("FallAnalysisActivity", "Moving camera to: " + centerPoint + " with zoom 15");
             googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centerPoint, 15));
        } catch (Exception e) {
             Log.e("FallAnalysisActivity", "Error moving camera: " + e.getMessage(), e);
        }
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
        this.googleMapInstance = null;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings")
               .setItems(new CharSequence[]{"Account Linking", "Logout"}, (dialog, which) -> {
                   if (which == 0) {
                       startActivity(new Intent(this, AccountLink.class));
                   } else if (which == 1) {
                       startActivity(new Intent(this, LoginScreen.class));
                       finishAffinity();
                   }
               })
               .show();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d("FallAnalysisActivity", "onMapReady received GoogleMap object.");
        this.googleMapInstance = googleMap;

        try {
             googleMap.getUiSettings().setZoomControlsEnabled(true);
             googleMap.getUiSettings().setCompassEnabled(true);
        } catch (Exception e) {
            Log.e("FallAnalysisActivity", "Error setting map UI settings: " + e.getMessage(), e);
        }

        // Removed initial camera move to default position
        /*
        // Set an initial reasonable camera position before data loads
        // This avoids starting at (0,0) if data loading is slow
        LatLng initialPos = new LatLng(40.7128, -74.0060); // Example: NYC
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 10));
        */

        // Analyze data (which now stores results in fields)
        analyzeFallData();

        Log.d("FallAnalysisActivity", "Calling updateMap from onMapReady using stored data.");
        if (!this.fallLocationsForMap.isEmpty()) {
            updateMap(googleMap, this.fallLocationsForMap, this.centerPointForMap);
        } else {
            Log.d("FallAnalysisActivity", "No fall locations available yet when onMapReady finished analysis.");
        }
    }
} 