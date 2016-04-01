package com.arksine.resremote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;



/**
 * Class BluetoothManager - Handles basic bluetooth tasks needed for this app.
 */
public class BluetoothManager {

    private static String TAG = "BluetoothManager";

    private Context mContext = null;
    private Activity mActivity = null;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;

    // TODO: this is for retrieving a bluetooth socket also need a name.  Use the insecure
    //      method to retrieve the socket, as I don't want to prompt the user.
    private final String NAME = "ResRemote";
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private boolean isBtRecrRegistered = false;
    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                synchronized (this) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.STATE_OFF);

                    if (state == BluetoothAdapter.STATE_TURNING_ON) {
                        Toast.makeText(mContext, "Bluetooth Turning On", Toast.LENGTH_SHORT).show();
                    }

                }
            }
        }
    };

    public BluetoothManager(Context context){
        mContext = context;

        initBluetooth();
    }

    public BluetoothManager(Activity activity) {
        mActivity = activity;
        mContext = mActivity.getApplicationContext();

        initBluetooth();
    }

    /**
     * Initializes bluetooth adapter, makes sure that it is turned on and prompts
     * user to turn it on if it isn't on.
     */
    private void initBluetooth() {

        // If we need to turn on the device, this broadcast reciever will listen
        // for changes and set initialized to true when the device is on.
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBtReceiver, filter);
        isBtRecrRegistered = true;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth, return an empty list
            Toast.makeText(mContext, "This device does not support bluetooth",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            if (mActivity == null) {
                mContext.startActivity(enableBtIntent);
            }
            else {
                mActivity.startActivityForResult(enableBtIntent, R.integer.REQUEST_ENABLE_BT);
            }

            return;
        }

    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        // just in case bluetooth wasn't uninitialized in service/app onDestroy method
        uninitBluetooth();
    }

    /**
     * This unitializes the bluetooth manager.  It should always be called before a context is
     * destroyed in the onDestroy method.
     */
    public void uninitBluetooth() {

        if (isBtRecrRegistered) {
            isBtRecrRegistered = false;
            mContext.unregisterReceiver(mBtReceiver);
        }
        closeBluetoothSocket();
    }

    public boolean isBluetoothOn() {
        return mBluetoothAdapter.isEnabled();
    }

    public ArrayList<String> enumerateDevices() {

        if (!isBluetoothOn()) {
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
    public boolean requestBluetoothSocket (String macAddr) {

        if (!isBluetoothOn()) {

            return false;
        }

        BluetoothDevice mDevice = mBluetoothAdapter.getRemoteDevice(macAddr);
        if (mDevice == null) {
            // device does not exist
            Log.e(TAG, "Unable to open bluetooth device at " + macAddr);
            return false;
        }

        // Attempt to create an insecure socket.  In the future I should probably
        // add an option for a secure connection, as this is subject to a man
        // in the middle attack.
        try {
            mSocket = mDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
        }
        catch (IOException e) {
            Log.e (TAG, "Unable to retrieve bluetooth socket for device " + macAddr);
            mSocket = null;
            return false;
        }

        mBluetoothAdapter.cancelDiscovery();

        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            mSocket.connect();
        } catch (IOException connectException) {

            Log.e (TAG, "Unable to connect to bluetooth socket for device " + macAddr);
            // Unable to connect; close the socket and get out
            try {
                mSocket.close();
            } catch (IOException closeException) { }

            mSocket = null;
            return false;
        }

        return true;
    }

    public void closeBluetoothSocket() {

        if (mSocket != null) {
            try {
                mSocket.close();
            }
            catch (IOException e) {
            }
        }
    }

    public InputStream getInputStream() {

        InputStream iStream;
        if (mSocket != null) {
            try {
                iStream = mSocket.getInputStream();
            } catch (IOException e) {
                iStream = null;
            }
        }
        else {
            iStream = null;
        }

        return iStream;
    }

    public OutputStream getOutputStream() {

        OutputStream oStream;
        if (mSocket != null) {
            try {
                oStream = mSocket.getOutputStream();
            } catch (IOException e) {
                oStream = null;
            }
        }
        else {
            oStream = null;
        }

        return oStream;
    }


    public boolean isDeviceConnected() {

        if (mSocket != null) {
            return mSocket.isConnected();
        }
        else {
            return false;
        }
    }


}
