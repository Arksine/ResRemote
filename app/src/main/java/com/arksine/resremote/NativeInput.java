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

    private Point screenTopLeftCoord;
    private Point screenTopRightCoord;
    private Point screenBottomRightCoord;

    // TODO: Android device coordinates should go from top to right (X) and top to bottom (Y),
    //       so we only need the max coordinates.  We need to check this to make sure
    private int deviceXMax;
    private int deviceYMax;

    private int xOffset;
    private int yOffset;

    private float xMultiplier;
    private float yMultiplier;

    private boolean xInverted;
    private boolean yInverted;

    NativeInput() {

        screenTopLeftCoord = new Point(0,0);
        screenTopRightCoord = new Point(1920, 0);
        screenBottomRightCoord = new Point(1920, 1080);

        deviceXMax = 1920;
        deviceYMax = 1080;

        xOffset = 0;
        yOffset = 0;

        xMultiplier = 1;
        yMultiplier = 1;

    }

    NativeInput(Point screenTopLeftCoord, Point screenTopRightCoord,
                Point screenBottomRightCoord, int deviceXMax,
                int deviceYMax) {

        setCoordinates(screenTopLeftCoord, screenTopRightCoord, screenBottomRightCoord,
                deviceXMax, deviceYMax);

    }

    public void setCoordinates(Point screenTopLeftCoord, Point screenTopRightCoord,
                                 Point screenBottomRightCoord, int deviceXMax,
                                 int deviceYMax) {

        this.screenTopLeftCoord = screenTopLeftCoord;
        this.screenTopRightCoord = screenTopRightCoord;
        this.screenBottomRightCoord = screenBottomRightCoord;

        this.deviceXMax = deviceXMax;
        this.deviceYMax = deviceYMax;

        calibrate();

    }

    private void calibrate() {

        int difference;

        if (screenTopLeftCoord.x < screenTopRightCoord.x) {
            //x coordinates go from right to left
            xOffset = screenTopLeftCoord.x;
            difference = screenTopRightCoord.x - screenTopLeftCoord.x;
            xInverted = false;
        }
        else
        {
            //x coordinates go from left to right
            xOffset = screenTopRightCoord.x;
            difference = screenTopLeftCoord.x - screenTopRightCoord.x;
            xInverted = true;
        }

        xMultiplier =  deviceXMax / difference;

        if (screenTopRightCoord.y < screenBottomRightCoord.y) {
            //y coordinates from top to bottom
            yOffset = screenTopRightCoord.y;
            difference = screenBottomRightCoord.y - screenTopRightCoord.y;
            yInverted = false;
        }
        else {
            // y coordinates go from bottom to top
            yOffset = screenBottomRightCoord.y;
            difference = screenTopRightCoord.y - screenBottomRightCoord.y;
            yInverted = true;
        }

        yMultiplier = deviceYMax / difference;
    }

    /**
     * Calculates a coordinate on the device given a touchscreen coordinate
     * @param screenCoord
     * @return
     */
    private Point getDeviceCoord(Point screenCoord) {

        int xCoord;
        int yCoord;

        // Calculate X
        xCoord = Math.round((screenCoord.x - xOffset) * xMultiplier);

        if (xCoord < 0) {
            xCoord = 0;
        }
        else if (xCoord > deviceXMax) {
            xCoord = deviceXMax;
        }

        if (xInverted) {
            xCoord = deviceXMax- xCoord;
        }

        // Calculate Y
        yCoord = Math.round((screenCoord.y - yOffset) * yMultiplier);

        if (yCoord < 0) {
            yCoord = 0;
        }
        else if (yCoord > deviceYMax) {

            yCoord = deviceYMax;
        }

        if (yInverted) {
            yCoord = deviceYMax - yCoord;
        }

        return new Point(xCoord, yCoord);
    }

}
