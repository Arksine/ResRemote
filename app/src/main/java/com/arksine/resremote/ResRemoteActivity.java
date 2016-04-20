package com.arksine.resremote;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class ResRemoteActivity extends Activity {

    private static String TAG = "ResRemoteActivity";

    private boolean bluetoothEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        getFragmentManager().beginTransaction()
                .add(android.R.id.content, new SettingsFragment())
                .commit();

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

        SerialHelper mSerialHelper;
        private String mDeviceType;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen root = this.getPreferenceScreen();
            PreferenceScreen calibrate = (PreferenceScreen) root.findPreference("pref_key_calibrate") ;
            PreferenceScreen startService = (PreferenceScreen) root.findPreference("pref_key_start_service");
            PreferenceScreen stopService = (PreferenceScreen) root.findPreference("pref_key_stop_service");
            ListPreference selectDeviceType = (ListPreference) root.findPreference("pref_key_select_device_type");
            ListPreference selectDevice = (ListPreference) root.findPreference("pref_key_select_device");
            ListPreference selectOrientation = (ListPreference) root.findPreference("pref_key_select_orientation");

            mDeviceType = selectDeviceType.getValue();
            populateDeviceListView();

            selectDeviceType.setSummary(selectDeviceType.getEntry());
            selectOrientation.setSummary(selectOrientation.getEntry());

            selectDeviceType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);

                    mDeviceType = (String)newValue;
                    populateDeviceListView();

                    return true;
                }
            });
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

                    PackageManager packageManager = getActivity().getPackageManager();
                    Intent launchIntent = packageManager
                            .getLaunchIntentForPackage("com.arksine.resremote.calibrationtool");
                    if (launchIntent != null) {
                        // App exists, start it.
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(getActivity(), "Calibration tool not installed", Toast.LENGTH_SHORT).show();
                    }

                    return true;
                }
            });
        }

        private void populateDeviceListView() {

            PreferenceScreen root = this.getPreferenceScreen();
            ListPreference selectDevicePref =
                    (ListPreference) root.findPreference("pref_key_select_device");

            if (mDeviceType.equals("BLUETOOTH")) {
                // user selected bluetooth device
                mSerialHelper = new BluetoothHelper(getActivity());
            }
            else {
                // user selected usb device
                mSerialHelper = new UsbHelper(getActivity());

            }

            ArrayList<String> mAdapterList = mSerialHelper.enumerateDevices();

            CharSequence[] entries;
            CharSequence[] entryValues;

            if (mAdapterList == null || mAdapterList.isEmpty()) {
                Log.i(TAG, "No compatible bluetooth devices found on system");
                entries = new CharSequence[1];
                entryValues = new CharSequence[1];

                entries[0]  = "No devices found";
                entryValues[0] = "NO_DEVICE";

                selectDevicePref.setSummary(entries[0]);
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

            selectDevicePref.setEntries(entries);
            selectDevicePref.setEntryValues(entryValues);

            // if the currently stored value isn't in the new list, reset the summary
            int index = selectDevicePref.findIndexOfValue(selectDevicePref.getValue());
            if (index == -1) {
                selectDevicePref.setSummary("");
            }
            else {
                selectDevicePref.setSummary(selectDevicePref.getEntry());
            }
        }

    }

}
