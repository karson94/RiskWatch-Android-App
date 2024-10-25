package com.ece441.riskwatch;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.widget.BaseAdapter;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public abstract class BlunoLibrary extends Activity {

    protected Context mainContext;

    protected BluetoothAdapter mBluetoothAdapter;
    protected BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mSCharacteristic;
    protected boolean mConnected = false;
    private String mDeviceAddress;

    protected static final int REQUEST_ENABLE_BT = 1;

    public enum connectionStateEnum {isNull, isScanning, isToScan, isConnecting, isConnected, isDisconnecting}
    public connectionStateEnum mConnectionState = connectionStateEnum.isNull;

    private static final String TAG = BlunoLibrary.class.getSimpleName();

    public abstract void onConectionStateChange(final connectionStateEnum theConnectionState);
    public abstract void onSerialReceived(String theString);

    public BlunoLibrary() {
        super();
    }

    public void setMainContext(Context context) {
        this.mainContext = context;
    }

    public void onCreateProcess() {
        if (!initiate()) {
            Toast.makeText(mainContext, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            ((Activity) mainContext).finish();
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void onResumeProcess() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        IntentFilter filter = makeGattUpdateIntentFilter();
        mainContext.registerReceiver(mGattUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    public void onPauseProcess() {
        mainContext.unregisterReceiver(mGattUpdateReceiver);
    }

    public void onStopProcess() {
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
    }

    public void onDestroyProcess() {
        mainContext.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void serialSend(String theString) {
        if (mConnectionState == connectionStateEnum.isConnected) {
            mSCharacteristic.setValue(theString);
            mBluetoothLeService.writeCharacteristic(mSCharacteristic);
        }
    }

    private boolean initiate() {
        if (!mainContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return false;
        }
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        return true;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            } 
            // The line below was removed to prevent immediate disconnection
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                mConnectionState = connectionStateEnum.isConnected;
                onConectionStateChange(mConnectionState);
                Log.i(TAG, "Connected to GATT server.");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                mConnectionState = connectionStateEnum.isToScan;
                onConectionStateChange(mConnectionState);
                Log.i(TAG, "Disconnected from GATT server.");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if (gattCharacteristic.getUuid().toString().equals("0000dfb1-0000-1000-8000-00805f9b34fb")) {
                    mSCharacteristic = gattCharacteristic;
                    mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
                    mBluetoothLeService.readCharacteristic(mSCharacteristic);
                }
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void scanLeDevice() {
        if (ContextCompat.checkSelfPermission(mainContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mainContext, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_ENABLE_BT);
            return;
        }

        if (mBluetoothAdapter.isEnabled()) {
            mConnectionState = connectionStateEnum.isScanning;
            onConectionStateChange(mConnectionState);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mConnectionState = connectionStateEnum.isToScan;
                    onConectionStateChange(mConnectionState);
                }
            }, SCAN_PERIOD);
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity) mainContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private static final long SCAN_PERIOD = 10000;
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            ((Activity) mainContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDeviceDiscovered(device);
                }
            });
        }
    };

    protected abstract void onDeviceDiscovered(BluetoothDevice device);

    public void connect(String address) {
        if (mBluetoothAdapter == null || address == null || mBluetoothLeService == null) {
            Log.e(TAG, "Bluetooth not initialized, address missing, or BluetoothLeService is null.");
            return;
        }

        mDeviceAddress = address;
        mConnectionState = connectionStateEnum.isConnecting;
        onConectionStateChange(mConnectionState);
        Log.d(TAG, "Attempting to connect to " + address + ". Connection state: " + mConnectionState);

        // Attempt connection
        mBluetoothLeService.connect(address);
    }

    public void disconnect() {
        mBluetoothLeService.disconnect();
    }

    public void serialBegin(int baudRate) {
        if (mBluetoothLeService != null && mConnectionState == connectionStateEnum.isConnected) {
            mBluetoothLeService.setBaudRate(baudRate);
        } else {
            Log.e(TAG, "Error: BluetoothLeService is null or not connected.");
        }
    }
}
