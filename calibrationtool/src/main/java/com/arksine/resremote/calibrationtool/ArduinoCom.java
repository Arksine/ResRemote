package com.arksine.resremote.calibrationtool;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;


/**
 * Class that communicates with the arudino via bluetooth or use to retreive points for calibration
 */
public class ArduinoCom extends Thread{

    private static final String TAG = "ArduinoCom";
    private Context mContext;
    private volatile boolean mRunning = true;
    private volatile boolean mCalSuccess;

    SerialHelper mSerialHelper;
    SerialHelper.DeviceReadyListener readyListener;

    /**
     * Container for a message recieved from the arduino.  There are two message types we can receive,
     * logging types and point types.  Logging types fill in the desc string, point types fill in
     * the TouchPoint.
     */
    private class ArduinoMessage {
        public String command;
        public String desc;
        public TouchPoint point;
    }

    /**
     * This is an interface for a callback the activity can use to be
     * notified when a point has been received.  The Activity
     * must implement this interface in its onCreateMethod
     */
    public interface OnItemReceivedListener {
        void onStartReceived(boolean connectionStatus);
        void onPointReceived(int pointIndex);
        void onPressureReceived(boolean success);
        void onFinished(boolean success);
    }
    private OnItemReceivedListener mOnItemRecdListener;


    public ArduinoCom(Context context) {
        mCalSuccess = false;
        this.mContext = context;
        String deviceType = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("pref_key_select_device_type", "BT_UINPUT");
        switch (deviceType) {
            case "BT_UINPUT":  //TODO: need to determine if I need different functionality for uinput and hid bluetooth
            case "BT_HID":
                mSerialHelper = new BluetoothHelper(mContext);
                break;
            case "USB_HID":
                mSerialHelper = new UsbHelper(mContext);
                break;
        }

        readyListener = new SerialHelper.DeviceReadyListener() {

            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {

                mOnItemRecdListener.onStartReceived(deviceReadyStatus);
            }
        };
    }

    public void connect(OnItemReceivedListener onItemReceivedListener) {
        mOnItemRecdListener = onItemReceivedListener;

        String devId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("pref_key_select_device", "NO_DEVICE");

        // Make sure a valid device was chosen
        if (devId.equals("NO_DEVICE")) {
            mOnItemRecdListener.onStartReceived(false);
            return;
        }
        mSerialHelper.connectDevice(devId, readyListener);
        // Execution now moves to the DeviceReady listener
    }

    public void disconnect() {
        mRunning = false;
        if (mCalSuccess) {
            mSerialHelper.writeString("<CAL_SUCCESS>");
        }
        mSerialHelper.disconnect();
    }

    /**
     * Sets the rotation for the device controller based on the current orientation of the android device.
     * It executes a read, which is blocking, so do not call in UI thread.
     */
    public void setDeviceRotation () {
        // make sure the device is initialized and connected
        if (mSerialHelper == null) {
            return;
        }

        String orientationPref = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("pref_key_select_orientation", "0");
        int selectedOrientation = Integer.parseInt(orientationPref);

        int rotation;

        switch (selectedOrientation) {
            case 0:
                rotation = 0;
                break;
            case 1:
                rotation = 1;
                break;
            case 2:
                rotation = 2;
                break;
            case 3:
                rotation = 3;
                break;
            case 4:
                DisplayManager displayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
                Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

                rotation = myDisplay.getRotation();
                break;
            default:
                rotation = 0;
        }



        String data = "<SET_ROTATION:" + Integer.toString(rotation) + ">";
        mSerialHelper.writeString(data);
        ArduinoMessage receipt = readMessage();
        if (receipt == null) {
            mOnItemRecdListener.onFinished(false);
            return;
        }
        if (receipt.desc.equals("OK")) {
            ArduinoMessage value = readMessage();
            Log.i(TAG, value.desc);
            mCalSuccess = true;
            mOnItemRecdListener.onFinished(true);
        } else {
            Log.e(TAG, receipt.command + " " + receipt.desc);
            mSerialHelper.writeString("<ERROR>");
            mOnItemRecdListener.onFinished(false);
        }

    }

    @Override
    public void run() {
        mRunning = true;
        Point[] touchPoints;
        Point resMinMax;

        touchPoints = getTouchPoints();
        resMinMax = calcResistance();

        if (touchPoints != null && mRunning) {
            mCalSuccess = calcCoefficients(touchPoints[0], touchPoints[1], touchPoints[2], resMinMax);
        }

        if (mCalSuccess) {
            // Tell the microcontroller to use the current rotation for coordinates
            setDeviceRotation();
        } else {
            mOnItemRecdListener.onFinished(false);
        }

    }

    /**
     * Reads a message from the arduino and parses it
     * @return Arduino message containing point data
     */
    private ArduinoMessage readMessage() {
        int bytes = 0;
        byte[] buffer = new byte[256];
        byte ch;

        // get the first byte, anything other than a '<' is trash and will be ignored
        while(mRunning && (mSerialHelper.readByte() != '<')) {
            // sleep for a 50ms before polling again
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Log.i(TAG, "Sleep interrupted", e);
            }

        }

        // First byte is good, capture the rest until we get to the end of the message
        while (mRunning && ((ch = mSerialHelper.readByte()) != '>')) {
            buffer[bytes] = ch;
            bytes++;
        }
        ArduinoMessage ardMsg;
        String message = new String(buffer, 0, bytes);
        String[] tokens = message.split(":");

        if (tokens.length == 2) {
            // command received or log message
            ardMsg = new ArduinoMessage();

            ardMsg.command = tokens[0];
            ardMsg.desc = tokens[1];

        }
        else if (tokens.length == 4) {
            // Point received
            ardMsg = new ArduinoMessage();
            ardMsg.command = tokens[0];
            ardMsg.point = new TouchPoint(Integer.parseInt(tokens[1]),
                    Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));

        }
        else {
            Log.e(TAG, "Issue parsing string, invalid data recd: " + message);
            ardMsg = null;
        }

        return ardMsg;
    }

    @Nullable
    private Point[] getTouchPoints() {
        Point[] touchPoints = new Point[3];
        ArduinoMessage screenPt;

        // Get the three calibration touch points
        for (int i = 0; i < 3; i++) {

            // Tell the Arudino to recieve a single point
            mSerialHelper.writeString("<CAL_POINT>");

            screenPt = readMessage();
            if (screenPt == null){
                // Error parsing bytes
                Log.e(TAG, "Error Parsing Calibration data from arduino");
                return null;
            }
            touchPoints[i] = new Point(screenPt.point.getX(), screenPt.point.getY());

            Log.i(TAG, "Point " + i + " X value: " + touchPoints[i].x);
            Log.i(TAG, "Point " + i + " Y value: " + touchPoints[i].y);

            // Callback to the activity telling it we received a point
            mOnItemRecdListener.onPointReceived(i);

            // Sleep for one second so the UI has time to animate to the next point
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.i(TAG, "Sleep interrupted", e);
            }

        }

        return touchPoints;

    }

    private Point calcResistance() {

        // We are going to read pressure in another thread so we can set
        // a timeout
        final ReadResistanceRunnable readResistance = new ReadResistanceRunnable();
        Thread readResistanceThread = new Thread(readResistance);
        readResistanceThread.start();

        // give a 10 second timeout
        try {
            readResistanceThread.join(10000);
        }
        catch (InterruptedException e) {
            Log.i(TAG, "Resistance Thread interrupted", e);
        }

        // kill the thread if its still alive
        if (readResistanceThread.isAlive())
        {
            readResistance.stopRunning();
            mOnItemRecdListener.onPressureReceived(false);
        }

        mOnItemRecdListener.onPressureReceived(true);
        return new Point(readResistance.getResistanceMin(), readResistance.getResistanceMax());
    }

    /**
     *  Reads Z-resistance min and max from arduino
     */
    private class ReadResistanceRunnable implements Runnable {
        private volatile boolean isRunning = true;

        private int resistanceMin = 65535;
        private int resistanceMax = 0;

        public int getResistanceMin() {
            return resistanceMin;
        }

        public int getResistanceMax() {
            return resistanceMax;
        }

        public void stopRunning() {
            isRunning = false;
        }

        @Override
        public void run() {
            isRunning = true;

            ArduinoMessage screenPt;

            mSerialHelper.writeString("<CAL_PRESSURE>");
            while (isRunning) {

                screenPt = readMessage();
                if (screenPt == null){
                    // Error parsing bytes
                    Log.e(TAG, "Error Parsing Calibration data from arduino");
                    break;
                }
                // We'll receive a stop command from the arduino after the user has
                // lifted their finger
                if (screenPt.command.equals("STOP")){
                    isRunning = false;
                    break;
                }

                if (screenPt.point.getZ() < resistanceMin) {
                    resistanceMin = screenPt.point.getZ();
                }
                if (screenPt.point.getZ() > resistanceMax) {
                    resistanceMax = screenPt.point.getZ();
                }

            }

            Log.i(TAG, "Min resistance: " + resistanceMin);
            Log.i(TAG, "Max resistance: " + resistanceMax);
        }
    }

    /**
     * Calculates Coefficients given screen coordinates
     *
     * @param T1   Right center point received from touchscreen
     * @param T2   Bottom center point received from touchscreen
     * @param T3   Top Left point received from touchscreen
     */
    private boolean calcCoefficients(Point T1, Point T2, Point T3, Point resistance) {

        //TODO: is double accurate enough, or should I use BigDecimal?
        // Calibration coefficients
        double A;
        double B;
        double C;
        double D;
        double E;
        double F;

        Point D1;     // Right Center device coordinate
        Point D2;     // Bottom Center device coordinate
        Point D3;     // Top Left device coordinate

        String deviceType = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("pref_key_select_device_type", "BT_UINPUT");

        if (deviceType.equals("BT_UINPUT")) {
            DisplayManager displayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

            Point maxSize = new Point();
            myDisplay.getRealSize(maxSize);

            // Get 10% of each axis, as our touch points will be within those ranges
            int xOffset = Math.round(0.1f * maxSize.x);
            int yOffset = Math.round(0.1f * maxSize.y);

            // Points are zero indexed, so we always subtract 1 pixel
            D1 = new Point(((maxSize.x - xOffset) - 1),
                    ((maxSize.y / 2) - 1));
            D2 = new Point(((maxSize.x / 2) - 1),
                    ((maxSize.y - yOffset) - 1));
            D3 = new Point((xOffset - 1), (yOffset - 1));
        } else {
            // HID devices use percentages rather than pixels, with a range from 0 to 10000.  Since
            // each point is 10% of the screen, the offset is 1000

            D1 = new Point(9000, 5000);
            D2 = new Point(5000, 9000);
            D3 = new Point(1000, 1000);
        }

        Log.i(TAG, "Device Point 1: x:" + D1.x + " y:" +D1.y);
        Log.i(TAG, "Device Point 2: x:" + D2.x + " y:" +D2.y);
        Log.i(TAG, "Device Point 3: x:" + D3.x + " y:" +D3.y);

        A = (D1.x * (T2.y - T3.y)) + (D2.x * (T3.y - T1.y))
                + (D3.x * (T1.y - T2.y));
        A = A / ((T1.x * (T2.y - T3.y)) + (T2.x * (T3.y - T1.y))
                + (T3.x * (T1.y - T2.y)));

        B = (A * (T3.x - T2.x)) + D2.x - D3.x;
        B = B / (T2.y - T3.y);

        C = D3.x - (A * T3.x) - (B * T3.y);

        D = ((D1.y * (T2.y - T3.y)) + (D2.y * (T3.y - T1.y))
                + (D3.y * (T1.y - T2.y)));
        D = D / ((T1.x * (T2.y - T3.y)) + (T2.x * (T3.y - T1.y))
                + (T3.x * (T1.y - T2.y)));

        E = ((D * (T3.x - T2.x)) + D2.y - D3.y);
        E = E / (T2.y - T3.y);

        F = D3.y - (D * T3.x) - (E * T3.y);

        Log.i(TAG, "A coefficient: " + A);
        Log.i(TAG, "B coefficient: " + B);
        Log.i(TAG, "C coefficient: " + C);
        Log.i(TAG, "D coefficient: " + D);
        Log.i(TAG, "E coefficient: " + E);
        Log.i(TAG, "F coefficient: " + F);

        long tmp;  // all floats will be sent as integers, with 3 decimal places of precision

        // TODO: multiplying by 10000 may make my numbers too large if touch screen coordinates get too big.  Probably not though,
        //       I would guess that they will stay under 1024 without a more precise ADC measurement
        // Send coefficients to the arduino.  They will be sent as integers with 4 decimals of precision.
        tmp = Math.round(A * 10000);
        if (!sendCalibrationVariable("<$A:" + Long.toString(tmp) + ">")) return false;

        tmp = Math.round(B * 10000);
        if (!sendCalibrationVariable("<$B:" + Long.toString(tmp) + ">")) return false;

        tmp = Math.round(C * 10000);
        if (!sendCalibrationVariable("<$C:" + Long.toString(tmp) + ">")) return false;

        tmp = Math.round(D * 10000);
        if (!sendCalibrationVariable("<$D:" + Long.toString(tmp) + ">")) return false;

        tmp = Math.round(E * 10000);
        if (!sendCalibrationVariable("<$E:" + Long.toString(tmp) + ">")) return false;

        tmp = Math.round(F * 10000);
        if (!sendCalibrationVariable("<$F:" + Long.toString(tmp) + ">")) return false;

        if (!sendCalibrationVariable("<$M:" + Integer.toString(resistance.x) +">")) return false;

        return true;
    }

    private boolean sendCalibrationVariable(String varData) {

        ArduinoMessage receipt;
        mSerialHelper.writeString(varData);
        receipt = readMessage();
        if (!receipt.desc.equals("OK")) {
            Log.e(TAG, receipt.command + " " + receipt.desc);
            mSerialHelper.writeString("<ERROR>");
            return false;
        } else {
            ArduinoMessage value = readMessage();
            Log.i(TAG, value.desc);
            return true;
        }
    }

}
