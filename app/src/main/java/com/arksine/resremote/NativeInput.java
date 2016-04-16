package com.arksine.resremote;

import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;


/**
 * Class NativeInput
 *
 * This class receives touch events from the ArduinoCom class.  It takes the data
 *
 */
public class NativeInput {

    private static String TAG = "NativeInput";

    int rotation;
    int xMax;
    int yMax;

    private boolean uinputOpen = false;

    private native boolean openUinput(int screenSizeX, int screenSizeY);
    private native void processEvent(String command, int x, int y, int z);
    private native void closeUinput();

    static {
        System.loadLibrary("restouchdrv");
    }

    NativeInput(int screenSizeX, int screenSizeY, int rotation) {


        setupVirtualDevice(screenSizeX, screenSizeY, rotation);

    }

    public void setupVirtualDevice(int screenSizeX, int screenSizeY, int rotation) {

        grantUinputPrivs();

        this.rotation = rotation;
        xMax = screenSizeX - 1;
        yMax = screenSizeY - 1;

        // TODO:  I may not need to close and reopen the the device when the orientation changes.
        //        its possible that android changes the x and y values for me.
        // If uinput is already open, close it first
        if (uinputOpen) {
            closeVirtualDevice();
            // Sleep for 200 ms so the device has a chance to be destroyed
            try {
                Thread.sleep(200);
            } catch (InterruptedException e){}
        }
        File uinputFile = new File("/dev/uinput");
        if (uinputFile.exists()) {
            if(uinputFile.canRead()){
                uinputOpen = openUinput(screenSizeX, screenSizeY);
            }
            else {
                Log.e(TAG, "Unable to read /dev/uinput, are permissions set correctly?");
            }
        }
        else {
            Log.e(TAG, "/dev/input does not exist on your device");
        }
    }

    public void processInput(String command, TouchPoint screenCoord) {

        TouchPoint deviceCoord = getDeviceCoord(screenCoord);
        processEvent(command, deviceCoord.getX(), deviceCoord.getY(), deviceCoord.getZ());

    }

    /**
     * Calculates translates coordinates based on device rotation
     *
     * @param screenCoord - coordinate received from the resistive touch screen
     * @return deviceCoord - converted device coordinate
     */
    private TouchPoint getDeviceCoord(TouchPoint screenCoord) {
        TouchPoint deviceCoord;

        int x;
        int y;
        int z;


        if (rotation == Surface.ROTATION_0) {  // Portrait default
            x = screenCoord.getX();
            y = screenCoord.getY();
        }
        else if(rotation == Surface.ROTATION_180){  // portrait flipped (x and y are inverted)
            x = xMax - screenCoord.getX();
            y = yMax - screenCoord.getY();
        }
        else if (rotation == Surface.ROTATION_90){ // landscape normal
            y = screenCoord.getX();
            x = xMax - screenCoord.getY();

        }
        else if (rotation == Surface.ROTATION_270) { // landscape inverted
            y = yMax - screenCoord.getX();
            x = screenCoord.getY();
        }
        else {
            // invalid rotation
            x = screenCoord.getX();
            y = screenCoord.getY();
        }

        z = screenCoord.getZ();

        //Log.d(TAG, "Translated coord: x:" + x + " y:" + y);
        deviceCoord = new TouchPoint(x, y, z);
        return deviceCoord;
    }

    public void closeVirtualDevice() {

        closeUinput();
        uinputOpen = false;
        revokeUinputPrivs();
    }

    public boolean isVirtualDeviceOpen() {
        return uinputOpen;
    }

    // The functions below use Root (su) to grant privileges to use uinput
    private void grantUinputPrivs() {

        String command = "chmod a+rw /dev/uinput";
        Shell.SU.run(command);

        List<String> output = Shell.SU.run("ls -l /dev/uinput");
        Log.i(TAG, "SU output:");
        for (String line : output) {
            Log.i(TAG, line);
        }
    }

    private void revokeUinputPrivs() {

        String command =  "chmod 660 /dev/uinput";
        Shell.SU.run(command);

    }

}
