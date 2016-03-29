package com.arksine.resremote;

import android.app.Activity;
import android.preference.PreferenceFragment;
import android.os.Bundle;

public class ResRemoteActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: start the service here if it isn't already started

        getFragmentManager().beginTransaction()
                .add(android.R.id.content, new SettingsFragment())
                .commit();

    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // TODO:  Need to enumerate Bluetooth devices and fill the list preference
            //        Need to create a listener for the list preference to update the summary
            //
            //        Need to create an onclick listener for calibrate screen to start the service
            //        and set the calibrated sharedpreference to true
        }
    }

}
