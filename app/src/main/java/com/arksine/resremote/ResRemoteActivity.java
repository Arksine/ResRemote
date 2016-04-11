package com.arksine.resremote;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class ResRemoteActivity extends Activity {

    private static String TAG = "ResRemoteActivity";

    private boolean bluetoothEnabled = false;
    private static BluetoothManager btManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        btManager = new BluetoothManager(this);

        if (btManager.isBluetoothOn() || bluetoothEnabled) {

            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == R.integer.REQUEST_ENABLE_BT) {
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


    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen root = this.getPreferenceScreen();
            PreferenceScreen calibrate = (PreferenceScreen) root.findPreference("pref_key_calibrate") ;
            PreferenceScreen startService = (PreferenceScreen) root.findPreference("pref_key_start_service");
            PreferenceScreen stopService = (PreferenceScreen) root.findPreference("pref_key_stop_service");
            ListPreference selectDevice = (ListPreference) root.findPreference("pref_key_select_bt_device");
            ListPreference selectOrientation = (ListPreference) root.findPreference("pref_key_select_orientation");

            ArrayList<String> mAdapterList = btManager.enumerateDevices();
            populateDeviceListView(mAdapterList, selectDevice);

            selectDevice.setSummary(selectDevice.getEntry());
            selectOrientation.setSummary(selectOrientation.getEntry());

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

            selectOrientation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);
                    return true;
                }
            });

            startService.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Context mContext = getActivity();
                    Intent startIntent = new Intent(mContext, ResRemoteService.class);
                    mContext.startService(startIntent);
                    return true;
                }
            });

            stopService.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Context mContext = getActivity();
                    Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));
                    mContext.sendBroadcast(stopIntent);
                    return true;
                }
            });

            // Listen for a click on the calibrate preference, and start the service in calibrate
            // mode if it exists.
            calibrate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    // Set isCalibrated shared preference to false and launch service
                    Context mContext = getActivity();
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(mContext);

                    // set the is_x_calibrated preference to false, depending on orientation
                    String orientation = sharedPrefs.getString("pref_key_select_orientation", "Landscape");
                    switch (orientation) {
                        case "Landscape":
                            sharedPrefs.edit().putBoolean("pref_key_is_landscape_calibrated", false).apply();
                            break;
                        case "Portrait":
                            sharedPrefs.edit().putBoolean("pref_key_is_portrait_calibrated", false).apply();
                            break;
                        case "Dynamic":
                            sharedPrefs.edit().putBoolean("pref_key_is_landscape_calibrated", false).apply();
                            sharedPrefs.edit().putBoolean("pref_key_is_portrait_calibrated", false).apply();
                            break;
                        default:
                            // Error: invalid orientation
                            Log.e(TAG, "Invalid orientation selected");
                            break;
                    }

                    Intent startIntent = new Intent(mContext, ResRemoteService.class);
                    mContext.startService(startIntent);
                    return true;
                }
            });
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
