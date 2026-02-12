package com.example.myapplication;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BleLinkManager {

    private static final String TAG = "BleLinkManager";
    private static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fc");

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothGatt connectedGattClient; // This is the person we send TO
    private MessageListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());

    public interface MessageListener {
        void onMessageReceived(String message);
        void onStatusUpdate(String status);
    }

    public BleLinkManager(Context context, MessageListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void startServer() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ
        );
        service.addCharacteristic(characteristic);

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        gattServer.addService(service);

        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        listener.onStatusUpdate("Server Started");
    }

    public void startScanning() {
        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) return;

        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        scanner.startScan(filters, settings, scanCallback);
        listener.onStatusUpdate("Scanning...");
    }

    public void sendMessage(String message) {
        if (connectedGattClient == null) {
            listener.onStatusUpdate("Error: No one connected to send to!");
            return;
        }
        BluetoothGattService service = connectedGattClient.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic != null) {
                characteristic.setValue(message.getBytes(StandardCharsets.UTF_8));
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                connectedGattClient.writeCharacteristic(characteristic);
            }
        }
    }

    // --- CRITICAL FIX: SERVER CALLBACK ---
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onStatusUpdate("Incoming Connection: " + device.getAddress());

                // FIX: If someone connects to us, we MUST connect back to them
                // to be able to write messages to them.
                if (connectedGattClient == null) {
                    listener.onStatusUpdate("Establishing Return Link...");
                    // Add a tiny delay to avoid clashing with the incoming connection
                    handler.postDelayed(() -> {
                        device.connectGatt(context, false, gattClientCallback);
                    }, 500);
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            String message = new String(value, StandardCharsets.UTF_8);
            new Handler(Looper.getMainLooper()).post(() -> listener.onMessageReceived(message));
        }
    };

    // --- CLIENT CALLBACK ---
    private final BluetoothGattCallback gattClientCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Success! We can now write to this device.
                connectedGattClient = gatt;
                gatt.discoverServices();
                listener.onStatusUpdate("2-Way Link Established!");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedGattClient = null;
                listener.onStatusUpdate("Disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // Ready to talk
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onStatusUpdate("Signal: " + rssi + " dBm");
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            listener.onStatusUpdate("Found " + device.getAddress() + " (" + rssi + ")");

            // Connect to them (Becoming the Client)
            if (connectedGattClient == null) {
                device.connectGatt(context, false, gattClientCallback);
            }

            // Note: We do NOT stop scanning immediately in case we lose connection
            // and need to find them again, but for battery you might want to stop.
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {}
        @Override
        public void onStartFailure(int errorCode) { Log.e(TAG, "Adv failed: " + errorCode); }
    };
}