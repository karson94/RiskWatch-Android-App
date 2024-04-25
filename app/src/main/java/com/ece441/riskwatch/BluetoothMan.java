//package com.ece441.riskwatch;
//
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothGatt;
//import android.bluetooth.BluetoothGattCallback;
//import android.bluetooth.BluetoothGattCharacteristic;
//import android.bluetooth.BluetoothGattService;
//import android.bluetooth.BluetoothManager;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.util.Log;
//import android.widget.Toast;
//
//import androidx.core.app.ActivityCompat;
//
//import java.util.UUID;
//
//public class BluetoothMan extends Context {
//
//    private static final String TAG = "BluetoothManager";
//
//    private final Context context;
//    private BluetoothAdapter bluetoothAdapter;
//    private BluetoothGatt bluetoothGatt;
//
//    private static final UUID SERVICE_UUID = UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
//    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
//
//    public BluetoothMan(Context context) {
//        this.context = context;
//    }
//
//    public void connectToDevice(String deviceAddress) {
//        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
//        if (bluetoothManager == null) {
//            Log.e(TAG, "BluetoothManager not available");
//            return;
//        }
//
//        bluetoothAdapter = bluetoothManager.getAdapter();
//        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
//            Log.e(TAG, "BluetoothAdapter not available or not enabled");
//            return;
//        }
//
//        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
//        if (bluetoothDevice == null) {
//            Log.e(TAG, "BluetoothDevice not found");
//            return;
//        }
//
//        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return;
//        }
//        bluetoothGatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback);
//    }
//
//    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
//        @Override
//        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            super.onConnectionStateChange(gatt, status, newState);
//            if (newState == BluetoothGatt.STATE_CONNECTED) {
//                Log.d(TAG, "BluetoothGattCallback: Device connected");
//                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                    // TODO: Consider calling
//                    //    ActivityCompat#requestPermissions
//                    // here to request the missing permissions, and then overriding
//                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                    //                                          int[] grantResults)
//                    // to handle the case where the user grants the permission. See the documentation
//                    // for ActivityCompat#requestPermissions for more details.
//                    return;
//                }
//                gatt.discoverServices();
//            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
//                Log.d(TAG, "BluetoothGattCallback: Device disconnected");
//            }
//        }
//
//        @Override
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            super.onServicesDiscovered(gatt, status);
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                BluetoothGattService service = gatt.getService(SERVICE_UUID);
//                if (service != null) {
//                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
//                    if (characteristic != null) {
//                        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                            // TODO: Consider calling
//                            //    ActivityCompat#requestPermissions
//                            // here to request the missing permissions, and then overriding
//                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                            //                                          int[] grantResults)
//                            // to handle the case where the user grants the permission. See the documentation
//                            // for ActivityCompat#requestPermissions for more details.
//                            return;
//                        }
//                        gatt.setCharacteristicNotification(characteristic, true);
//                        // Optionally enable notifications on the BLE device if needed
//                        // blunoBeetle.setupNotifications(CHARACTERISTIC_UUID);
//                    } else {
//                        Log.e(TAG, "Characteristic not found");
//                    }
//                } else {
//                    Log.e(TAG, "Service not found");
//                }
//            } else {
//                Log.e(TAG, "Service discovery failed with status: " + status);
//            }
//        }
//
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
//            super.onCharacteristicChanged(gatt, characteristic);
//            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
//                byte[] data = characteristic.getValue();
//                if (data != null && data.length > 0) {
//                    String receivedData = new String(data);
//                    Log.d(TAG, "Received data from Bluetooth device: " + receivedData);
//                    // Process received data as needed
//                    processData(receivedData);
//                }
//            }
//        }
//    };
//
//    public void disconnectDevice() {
//        if (bluetoothGatt != null) {
//            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                return;
//            }
//            bluetoothGatt.disconnect();
//            bluetoothGatt.close();
//            bluetoothGatt = null;
//        }
//    }
//
//    private void processData(String data) {
//        String notif = "Fall detected";
//        Toast.makeText(context, notif, Toast.LENGTH_LONG).show();
//        // Process the received data (e.g., update UI, trigger actions)
//        // Implement your data processing logic here
//    }
//
//}
