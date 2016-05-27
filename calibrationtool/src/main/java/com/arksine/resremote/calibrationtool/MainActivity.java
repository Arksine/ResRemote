package com.arksine.resremote.calibrationtool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

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

        public static final String ACTION_DEVICE_CHANGED = "com.arksine.resremote.ACTION_DEVICE_CHANGED";
        private SerialHelper mSerialHelper;
        private String mDeviceType;

        private final BroadcastReceiver deviceListReciever = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_DEVICE_CHANGED)) {
                    populateDeviceListView();
                }
            }
        };

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
                    (PreferenceScreen) root.findPreference("pref_key_start_calibration");
            ListPreference selectOrientationPref =
                    (ListPreference) root.findPreference("pref_key_select_orientation");
            PreferenceScreen setOrientationPref =
                    (PreferenceScreen) root.findPreference("pref_key_set_controller_orientation");

            mDeviceType = selectDeviceTypePref.getValue();
            populateDeviceListView();

            selectDeviceTypePref.setSummary(selectDeviceTypePref.getEntry());
            selectOrientationPref.setSummary(selectOrientationPref.getEntry());

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

                    mDeviceType = (String)newValue;
                    populateDeviceListView();

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

            selectOrientationPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
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
                    // Stop the resremote service if it is installed
                    Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));
                    getActivity().sendBroadcast(stopIntent);

                    Intent startCal = new Intent(getActivity(), CalibrateTouchActivity.class);
                    startActivity(startCal);
                    return true;
                }
            });

            setOrientationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(R.string.orientation_dialog_message)
                            .setTitle(R.string.orientation_dialog_title)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // No need to do anything here
                                }
                            });

                    final AlertDialog alert = builder.create();
                    alert.show();
                    alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.INVISIBLE);
                    final ArduinoCom arduino = new ArduinoCom(getActivity());

                    Runnable connectRunnable = new Runnable() {
                        ArduinoCom.OnItemReceivedListener listener = new ArduinoCom.OnItemReceivedListener() {
                            @Override
                            public void onStartReceived(boolean connectionStatus) {
                                if (connectionStatus) {
                                    arduino.setDeviceRotation();
                                }
                                else {
                                    Runnable uiRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            alert.setMessage(getString(R.string.orientation_dialog_failed));
                                            alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                                        }
                                    };
                                    getActivity().runOnUiThread(uiRunnable);
                                }
                            }

                            @Override
                            public void onPointReceived(int pointIndex) {
                                // EMPTY, DO NOT NEED
                            }

                            @Override
                            public void onPressureReceived(boolean success) {
                                // EMPTY, DO NOT NEED
                            }

                            @Override
                            public void onFinished(boolean success) {
                                Runnable uiRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        alert.setMessage(getString(R.string.orientation_dialog_finished));
                                        alert.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                                    }
                                };
                                getActivity().runOnUiThread(uiRunnable);
                                arduino.disconnect();
                            }
                        };
                        @Override
                        public void run() {
                            arduino.connect(listener);
                        }
                    };

                    Thread connectThread = new Thread(connectRunnable);
                    connectThread.start();

                    return true;
                }
            });

            IntentFilter filter = new IntentFilter(ACTION_DEVICE_CHANGED);
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(deviceListReciever, filter);

        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(deviceListReciever);
        }

        private void populateDeviceListView() {

            PreferenceScreen root = this.getPreferenceScreen();
            ListPreference selectDevicePref =
                    (ListPreference) root.findPreference("pref_key_select_device");


            ArrayList<String> deviceList;
            switch (mDeviceType) {
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
                Log.i(TAG, "No compatible devices found on system");
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
