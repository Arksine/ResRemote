package com.arksine.resremote;

import android.graphics.Point;

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

    NativeInput() {

        A = 0.0f;
        B = 0.0f;
        C = 0.0f;
        D = 0.0f;
        E = 0.0f;
        F = 0.0f;

    }

    // TODO: Even though I'm calculating Z, I'll probably use an arbitrary pressure when sending
    //       events to uInput in the first implementation.  I want to see how it works.

    NativeInput(float A, float B, float C, float D, float E, float F,
                int zResistanceMin, int zResistanceMax) {

        setCoefficients(A, B, C, D, E, F, zResistanceMin, zResistanceMax);


    }

    public void setCoefficients(float A, float B, float C, float D, float E, float F,
            int zResistanceMin, int zResistanceMax) {

        this.A = A;
        this.B = B;
        this.C = C;
        this.D = D;
        this.E = E;
        this.F = F;

        calibratePressure(zResistanceMin, zResistanceMax);

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

    public void processInput(TouchPoint screenCoord) {

        TouchPoint deviceCoord = getDeviceCoord(screenCoord);

        // TODO: Send deviceCoordinate to JNI code so events cant be processed by uInput
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

        x = Math.round((A * screenCoord.getX()) + (B * screenCoord.getY()) + C);
        y = Math.round((D * screenCoord.getX()) + (E * screenCoord.getY()) + F);
        z = Math.round(255 - ((screenCoord.getZ() - pressureOffset)*pressureCoef));

        deviceCoord = new TouchPoint(x, y, z);
        return deviceCoord;
    }

}
