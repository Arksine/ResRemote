package com.arksine.resremote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Eric on 3/29/2016.
 */
public class BluetoothManager {

    private static String TAG = "BluetoothManager";

    // We need this lock to synchronize accesses to methods accessing the deviceList, as multiple
    // threads will be accessing it
    public static final Object lock = new Object();
    private static boolean bluetoothOn = false;
    public static final int REQUEST_ENABLE_BT = 6001;

    private static BluetoothAdapter mBluetoothAdapter;

    // TODO: this is for retrieving a bluetooth socket also need a name.  Use the insecure
    //      method to retrieve the socket, as I don't want to prompt the user.
    private static final String NAME = "ResRemote";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private static final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                synchronized (this) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);

                    if (state == BluetoothAdapter.STATE_ON) {
                        bluetoothOn = true;
                    }
                }
            }
        }
    };

    private BluetoothManager(){}

    /**
     * Prior to using a any methods in this class, initBluetooth must always be called first
     * to make sure bluetooth is available and turned on.  It should typically called in a
     * constructor or onCreate method.
     * @param context - context of the calling activity or service
     */
    public static void initBluetooth(Context context) {

        if (bluetoothOn) {
            // already initialized
            return;
        }

        // If we need to turn on the device, this broadcast reciever will listen
        // for changes and set initialized to true when the device is on.
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mBtReceiver, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth, return an empty list
            Toast.makeText(context, "This device does not support bluetooth",
                    Toast.LENGTH_SHORT).show();

            bluetoothOn = false;
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.startActivity(enableBtIntent);
            bluetoothOn = false;
            return;
        }

        bluetoothOn = true;
    }

    /**
     * This unitializes the bluetooth manager.  It should always be called before a context is
     * destroyed in the onDestroy method.
     * @param context - context of the calling activity or service
     */
    public static void uninitBluetooth(Context context) {

        bluetoothOn = false;
        context.unregisterReceiver(mBtReceiver);
    }

    public static boolean isBluetoothOn() {
        return bluetoothOn;
    }

    public static ArrayList<String> enumerateDevices() {

        if (!bluetoothOn) {
            return null;
        }

        ArrayList<String> mAdapterList = new ArrayList<>(5);

        // We know the bluetooth adapter is enabled, so we can retrieve it
        // and get devices that are mapped
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {

                // TODO: may need to compare the device name to a list of supported
                //       devices before adding, as it doesn't appear I can get the
                //       device profiles supported

                // Add the name and address to an array adapter to show in a ListView
                mAdapterList.add(device.getName() + "\n" + device.getAddress());
            }
        }

        return mAdapterList;
    }

    /**
     * Retrieves a bluetooth socket from a specified device.  WARNING: This function is blocking, do
     * not call from the UI thread!
     * @param macAddr - The mac address of the device to connect
     * @return
     */
    public static BluetoothSocket requestBluetoothSocket (String macAddr) {

        if (!bluetoothOn) {

            return null;
        }

        // TODO: should probably get a socket instead of a device.  Or just get the Streams for
        //       reading and writing
        BluetoothDevice mDevice = mBluetoothAdapter.getRemoteDevice(macAddr);

        if (mDevice == null) {
            // device does not exist
            return null;
        }
    }

    public static void cancelSocketRequest() {

    }

}
