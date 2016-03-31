package com.arksine.resremote;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class ResRemoteActivity extends Activity {

    private static String TAG = "ResRemoteActivity";
    private static final int REQUEST_ENABLE_BT = 6001;

    private boolean bluetoothEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startBluetooth();

        if (bluetoothEnabled) {
            // TODO: start the service here if it isn't already started

            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                bluetoothEnabled = true;
            }
            else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "Error starting bluetooth device");
                Toast.makeText(this, "Error starting bluetooth device",
                        Toast.LENGTH_SHORT).show();
                bluetoothEnabled = false;
            }
        }
    }

    private void startBluetooth() {

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth, return an empty list
            Toast.makeText(this, "This device does not support bluetooth",
                    Toast.LENGTH_SHORT).show();

            bluetoothEnabled = false;
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            bluetoothEnabled = true;
        }
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen root = this.getPreferenceScreen();
            PreferenceScreen calibrate = (PreferenceScreen) root.findPreference("pref_key_calibrate") ;
            ListPreference selectDevice = (ListPreference) root.findPreference("pref_key_select_bt_device");

            ArrayList<String> mAdapterList = enumerateBluetoothDevices();
            populateDeviceListView(mAdapterList, selectDevice);

            selectDevice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);

                    return true;
                }
            });

            // Listen for a click on the calibrate preference, and start the service in calibrate
            // mode if it exists.
            calibrate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    // TODO: launch service in calibrate mode


                    return true;
                }
            });
        }

        private ArrayList<String> enumerateBluetoothDevices() {

            ArrayList<String> mAdapterList = new ArrayList<>(5);

            // We know the bluetooth adapter is enabled, so we can retrieve it
            // and get devices that are mapped
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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

        private void populateDeviceListView(ArrayList<String> mAdapterList,
                                            ListPreference selectDevice) {

            CharSequence[] entries;
            CharSequence[] entryValues;

            if (mAdapterList.isEmpty()) {
                Log.i(TAG, "No compatible bluetooth devices found on system");
                entries = new CharSequence[1];
                entryValues = new CharSequence[1];

                entries[0]  = "No devices found";
                entryValues[0] = "NO_DEVICE";

                selectDevice.setSummary(entries[0]);
            }
            else {

                entries = new CharSequence[mAdapterList.size()];
                entryValues = new CharSequence[mAdapterList.size()];

                for (int i = 0; i < mAdapterList.size(); i++) {

                    String[] deviceInfo = mAdapterList.get(i).split("\n");

                    entries[i] = mAdapterList.get(i);
                    entryValues[i] = deviceInfo[1];

                }
            }

            selectDevice.setEntries(entries);
            selectDevice.setEntryValues(entryValues);
        }
    }

}
