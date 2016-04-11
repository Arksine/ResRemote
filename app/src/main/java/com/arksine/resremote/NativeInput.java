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

    // coordinate converson coefficients
    private float A;
    private float B;
    private float C;
    private float D;
    private float E;
    private float F;

    private int pressureOffset;
    private float pressureCoef;

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

    NativeInput(float A, float B, float C, float D, float E, float F,
                int zResistanceMin, int zResistanceMax, int screenSizeX, int screenSizeY, int rotation) {

        Log.i(TAG, "A coefficient: " + A);
        Log.i(TAG, "B coefficient: " + B);
        Log.i(TAG, "C coefficient: " + C);
        Log.i(TAG, "D coefficient: " + D);
        Log.i(TAG, "E coefficient: " + E);
        Log.i(TAG, "F coefficient: " + F);

        setupVirtualDevice(A, B, C, D, E, F, zResistanceMin, zResistanceMax, screenSizeX, screenSizeY, rotation);

    }

    public void setupVirtualDevice(float A, float B, float C, float D, float E, float F,
            int zResistanceMin, int zResistanceMax, int screenSizeX, int screenSizeY, int rotation) {

        grantUinputPrivs();

        this.A = A;
        this.B = B;
        this.C = C;
        this.D = D;
        this.E = E;
        this.F = F;

        this.rotation = rotation;
        xMax = screenSizeX - 1;
        yMax = screenSizeY - 1;

        calibratePressure(zResistanceMin, zResistanceMax);


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

	/**
     * Calculates the coefficient required to convert the R-touch (resitance between the x and y
     * screen layers) to ABS_MT_PRESSURE.  Resistance is the inverse of pressure, the lower the
     * resistance the higher the pressure
     *
     * @param zResistanceMin
     * @param zResistanceMax
     */
    private void calibratePressure(int zResistanceMin, int zResistanceMax) {

        // zResistanceMin is proportional to ABS_MT_PRESSURE = 255;
        // zResistanceMax is proportional to ABS_MT_PRESSURE = 0;

        pressureOffset = zResistanceMin;
        pressureCoef = 256 / (zResistanceMax - zResistanceMin);
    }

    public void processInput(String command, TouchPoint screenCoord) {

        TouchPoint deviceCoord = getDeviceCoord(screenCoord);
        processEvent(command, deviceCoord.getX(), deviceCoord.getY(), deviceCoord.getZ());

    }

    /**
     * Calculates a coordinate on the device given a touchscreen coordinate
     *
     * @param screenCoord - coordinate received from the resistive touch screen
     * @return deviceCoord - converted device coordinate
     */
    private TouchPoint getDeviceCoord(TouchPoint screenCoord) {
        TouchPoint deviceCoord;

        int x;
        int y;
        int z;

        //  TODO: each rotation needs coordinates set differently.  Uinput assumes the
        //        initial rotation is Zero, so we need to remap x and y according to our current
        //        rotation
        if (rotation == Surface.ROTATION_0) {  // Portrait default
            x = Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
            y = Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);
        }
        else if(rotation == Surface.ROTATION_180){  // portrait flipped (x and y are inverted)
            x = xMax - Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
            y = yMax - Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);
        }
        else if (rotation == Surface.ROTATION_90){ // landscape normal
            y = Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
            x = xMax - Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);

        }
        else if (rotation == Surface.ROTATION_270) { // landscape inverted
            y = yMax - Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
            x = Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);
        }
        else {
            // invalid rotation
            x = Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
            y = Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);
        }

        z = Math.round(255 - ((screenCoord.getZ() - pressureOffset) * pressureCoef));

        //Log.d(TAG, "Translated coord - x:" + x + " y:" + y);
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
        List<String> output =  Shell.SU.run(command);

        output = Shell.SU.run("ls -l /dev/uinput");
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
