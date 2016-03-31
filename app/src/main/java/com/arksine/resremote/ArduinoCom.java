package com.arksine.resremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

/**
 * Class ArduinoCom
 *
 * This class handles bluetooth serial communication with the arduino.  First, it establishes
 * a bluetooth connection and confirms that the Arudino is connected.  It will then launch an
 * activity to calibrate the touchscreen if necessary.  After setup is complete, it will listen
 * for resistive touch screen events from the arudino and send them to the NativeInput class
 * where they can be handled by the uinput driver in the NDK.
 */
public class ArduinoCom {

    private static String TAG = "ArduinoCom";

    private NativeInput uInput = null;
    private boolean mConnected = false;
    private Context mContext;
    private volatile boolean mRunning = false;

    // TODO:  Need a broadcast listener to listen for changes to the rotation so
    //        the uInput coordinates can be updated

    // TODO:  Need to add option to lock rotation to landscape, or just do it by default


    ArduinoCom(Context context) {
        mContext = context;
    }


    private void setup() {

    }

    public boolean connect() {

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(mContext);

        // TODO:  need to get bluetooth device from shared preferences here

        // TODO: Need to PUT pref_key_iscalibrated as true in the CalibrateTouchScreen activity
        boolean isCalibrated = sharedPrefs.getBoolean("pref_key_iscalibrated", false);

        if (!isCalibrated) {
            Log.i(TAG, "Touch Screen not calibrated, ");
            Toast.makeText(mContext, "Screen not calibrated", Toast.LENGTH_SHORT).show();

            // TODO: could startservice here with calibration intent?  I shouldn't return false
            //       here as I still need to connect to the device.
        }

        getInputSettings(sharedPrefs);

        mConnected = true;
        return true;
    }

    private void getInputSettings(SharedPreferences sharedPrefs) {

        Point screenTopLeftCoord;
        Point screenTopRightCoord;
        Point screenBottomRightCoord;

        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        int rotation = myDisplay.getRotation();

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);

        // TODO: coordinates need to be added to sharedpreferences in calibration function

        // Get touch screen coordinates based on rotation
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            // Portrait

            // get top left coordinates
            int x = sharedPrefs.getInt("pref_key_portrait_top_left_x", 0);
            int y = sharedPrefs.getInt("pref_key_portrait_top_left_y", 0);
            screenTopLeftCoord = new Point(x,y);

            // get top right coordinates
            x = sharedPrefs.getInt("pref_key_portrait_top_right_x", maxSize.x);
            y = sharedPrefs.getInt("pref_key_portrait_top_right_y", 0);
            screenTopRightCoord = new Point(x,y);

            // get top right coordinates
            x = sharedPrefs.getInt("pref_key_portrait_bottom_right_x", maxSize.x);
            y = sharedPrefs.getInt("pref_key_portrait_bottom_right_y", maxSize.y);
            screenBottomRightCoord = new Point(x,y);

        }
        else {
            // Landscape

            // get top left coordinates
            int x = sharedPrefs.getInt("pref_key_landscape_top_left_x", 0);
            int y = sharedPrefs.getInt("pref_key_landscape_top_left_y", 0);
            screenTopLeftCoord = new Point(x,y);

            // get top right coordinates
            x = sharedPrefs.getInt("pref_key_landscape_top_right_x", maxSize.x);
            y = sharedPrefs.getInt("pref_key_landscape_top_right_y", 0);
            screenTopRightCoord = new Point(x,y);

            // get top right coordinates
            x = sharedPrefs.getInt("pref_key_landscape_bottom_right_x", maxSize.x);
            y = sharedPrefs.getInt("pref_key_landscape_bottom_right_y", maxSize.y);
            screenBottomRightCoord = new Point(x,y);
        }

        if (uInput == null) {
            uInput = new NativeInput(screenTopLeftCoord, screenTopRightCoord, screenBottomRightCoord,
                    maxSize.x, maxSize.y);
        }
        else {
            uInput.setCoordinates(screenTopLeftCoord, screenTopRightCoord, screenBottomRightCoord,
                    maxSize.x, maxSize.y);
        }


    }

    public boolean isConnected() {

        // TODO: Check to see if the bluetooth connection is still established here;
        return mConnected;
    }


    public void listenForInput() {

        mRunning = true;

        while (mRunning) {

            if (isConnected()) {
                // TODO: process incoming serial commands here
            }


            // TODO: might not want to sleep this amount, or at all
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
        }

    }

    public void calibrate () {

        mRunning = true;
        // TODO:  need to launch calibration activity, then listen for calibration date from arduino
        //        as the activity guides the user where to press.  After each press, we send an intent
        //        to the calibration activity guiding the user where to press next until calibration
        //        is complete, at which case we send an intent telling the activity to close itself

    }

    public void disconnect () {

    }

    /**
     * Stops any ongoing action that is thread blocking
     */
    public void stop() {
        mRunning = false;
    }

}
