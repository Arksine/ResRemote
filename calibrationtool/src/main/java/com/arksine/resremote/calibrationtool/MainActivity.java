package com.arksine.resremote.calibrationtool;

import android.content.Intent;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .add(android.R.id.content, new SettingsFragment())
                .commit();
    }


    public static class SettingsFragment extends PreferenceFragment {

        private SerialHelper mSerialHelper;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);



            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen root = this.getPreferenceScreen();
            ListPreference selectDeviceTypePref =
                    (ListPreference) root.findPreference("pref_key_select_device_type");
            ListPreference selectDevicePref =
                    (ListPreference) root.findPreference("pref_key_select_device");
            PreferenceScreen startCalibrationPref  =
                    (PreferenceScreen) root.findPreference("pref_key_start_calbration") ;

            String deviceType = selectDeviceTypePref.getValue();
            populateDeviceListView(deviceType);

            selectDeviceTypePref.setSummary(selectDeviceTypePref.getEntry());
            selectDevicePref.setSummary(selectDevicePref.getEntry());

            /**
             * The listeners below update the preference summary after they have been changed
             */
            selectDeviceTypePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);

                    populateDeviceListView((String)newValue);

                    return true;
                }
            });
            selectDevicePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ListPreference list = (ListPreference)preference;
                    CharSequence[] entries = list.getEntries();
                    int index = list.findIndexOfValue((String)newValue);
                    preference.setSummary(entries[index]);
                    return true;
                }
            });

            /**
             * The listener below launches the calibration activity when clicked
             */
            startCalibrationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent startCal = new Intent(getActivity(), CalibrateTouchActivity.class);
                    startActivity(startCal);
                    return true;
                }
            });

        }

        private void populateDeviceListView(String deviceType) {

            PreferenceScreen root = this.getPreferenceScreen();
            ListPreference selectDevicePref =
                    (ListPreference) root.findPreference("pref_key_select_device");

            ArrayList<String> deviceList;
            switch (deviceType) {
                case "BT_UINPUT":  //TODO: need to determine if I need different functionality for uinput and hid bluetooth
                case "BT_HID":
                    mSerialHelper = new BluetoothHelper(getActivity());
                    break;
                case "USB_HID":
                    mSerialHelper = new UsbHelper(getActivity());
                    break;
            }

            deviceList = mSerialHelper.enumerateDevices();

            CharSequence[] entries;
            CharSequence[] entryValues;

            if (deviceList == null || deviceList.isEmpty()) {
                Log.i(TAG, "No compatible bluetooth devices found on system");
                entries = new CharSequence[1];
                entryValues = new CharSequence[1];

                entries[0]  = "No devices found";
                entryValues[0] = "NO_DEVICE";

                selectDevicePref.setSummary(entries[0]);
            }
            else {

                entries = new CharSequence[deviceList.size()];
                entryValues = new CharSequence[deviceList.size()];

                for (int i = 0; i < deviceList.size(); i++) {

                    String[] deviceInfo = deviceList.get(i).split("\n");

                    entries[i] = deviceList.get(i);
                    entryValues[i] = deviceInfo[1];

                }
            }

            selectDevicePref.setEntries(entries);
            selectDevicePref.setEntryValues(entryValues);
        }
    }
}
