package com.ece441.riskwatch;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Helper class for interacting with Amazon Alexa devices
 */
public class AmazonAlexaHelper {
    private static final String TAG = "AmazonAlexaHelper";
    private static final String ALEXA_DEVICES_API_URL = "https://api.amazonalexa.com/v1/devices";
    
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Context context;
    
    public AmazonAlexaHelper(Context context) {
        this.context = context;
    }
    
    /**
     * Interface for handling Alexa device list results
     */
    public interface AlexaDevicesCallback {
        void onDevicesRetrieved(List<AlexaDevice> devices);
        void onError(String errorMessage);
    }
    
    /**
     * Class representing an Alexa device
     */
    public static class AlexaDevice {
        private final String deviceId;
        private final String deviceName;
        private final String deviceType;
        
        public AlexaDevice(String deviceId, String deviceName, String deviceType) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public String getDeviceName() {
            return deviceName;
        }
        
        public String getDeviceType() {
            return deviceType;
        }
    }
    
    /**
     * Get the list of Alexa devices associated with the user's Amazon account
     * @param accessToken Amazon access token
     * @param callback Callback to handle the result
     */
    public void getAlexaDevices(String accessToken, AlexaDevicesCallback callback) {
        if (accessToken == null || accessToken.isEmpty()) {
            callback.onError("Access token is null or empty");
            return;
        }
        
        executor.execute(() -> {
            try {
                URL url = new URL(ALEXA_DEVICES_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    List<AlexaDevice> devices = parseDevicesResponse(response.toString());
                    callback.onDevicesRetrieved(devices);
                } else {
                    callback.onError("Error retrieving Alexa devices: " + responseCode);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error retrieving Alexa devices", e);
                callback.onError("Error retrieving Alexa devices: " + e.getMessage());
            }
        });
    }
    
    /**
     * Parse the JSON response from the Alexa devices API
     * @param response JSON response string
     * @return List of Alexa devices
     */
    private List<AlexaDevice> parseDevicesResponse(String response) {
        List<AlexaDevice> devices = new ArrayList<>();
        
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray devicesArray = jsonResponse.getJSONArray("devices");
            
            for (int i = 0; i < devicesArray.length(); i++) {
                JSONObject deviceObj = devicesArray.getJSONObject(i);
                String deviceId = deviceObj.getString("deviceId");
                String deviceName = deviceObj.getString("deviceName");
                String deviceType = deviceObj.getString("deviceType");
                
                devices.add(new AlexaDevice(deviceId, deviceName, deviceType));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Alexa devices response", e);
        }
        
        return devices;
    }
    
    /**
     * Send a notification to an Alexa device
     * @param accessToken Amazon access token
     * @param deviceId Alexa device ID
     * @param message Message to send
     */
    public void sendNotificationToDevice(String accessToken, String deviceId, String message) {
        // This is a placeholder for the actual implementation
        // In a real implementation, you would use the Alexa Notifications API
        Toast.makeText(context, "Notification sent to device: " + message, Toast.LENGTH_SHORT).show();
    }
} 