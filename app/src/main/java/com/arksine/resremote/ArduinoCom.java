package com.arksine.resremote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class ArduinoCom
 *
 * This class handles bluetooth serial communication with the arduino.  First, it establishes
 * a bluetooth connection and confirms that the Arudino is connected.  It will then launch an
 * activity to calibrate the touchscreen if necessary.  After setup is complete, it will listen
 * for resistive touch screen events from the arudino and send them to the NativeInput class
 * where they can be handled by the uinput driver in the NDK.
 */
public class ArduinoCom implements Runnable{

    private static String TAG = "ArduinoCom";

    private NativeInput uInput = null;
    private boolean mConnected = false;
    private Context mContext;

    private volatile boolean mRunning = false;

    private BluetoothManager btManager;
    private final InputStream arudinoInput;
    private final OutputStream arduinoOutput;

    private InputHandler mInputHandler;
    private Looper mInputLooper;

    // Handler that receives messages from arudino and sends them
    // to NativeInput
    private final class InputHandler extends Handler {

        public InputHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            ArduinoMessage message = parseBytes((byte[])msg.obj, msg.arg1);

            // TODO: send screen input to the NativeInput class here
        }
    }

    @Override
    public void run() {
        listenForInput();
    }

// TODO:  Need a broadcast reciever to listen for changes to the rotation so
    //        the uInput coordinates can be updated

    // TODO: Need a broadcast reciever to listen for write commands.

    private class ArduinoMessage {
        public String command;
        public TouchPoint point;
    }

    ArduinoCom(Context context) {
        mContext = context;
        btManager = new BluetoothManager(mContext);
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mInputLooper = thread.getLooper();
        mInputHandler = new InputHandler(mInputLooper);
        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(mContext);

        mConnected = connect(sharedPrefs);

        InputStream tmpIn = btManager.getInputStream();
        OutputStream tmpOut = btManager.getOutputStream();
        arudinoInput = tmpIn;
        arduinoOutput = tmpOut;

        if (arudinoInput == null || arduinoOutput == null) {
            mConnected = false;
            return;
        }

        // Tell the Arudino that it is time to start
        String start = "<START>";
        if (!writeData(start)) {
            // unable to write start command
            mConnected = false;
            return;
        }

        boolean isCalibrated = sharedPrefs.getBoolean("pref_key_iscalibrated", false);

        if (!isCalibrated) {
            Log.i(TAG, "Touch Screen not calibrated, ");
            Toast.makeText(mContext, "Screen not calibrated, launching calibration activity",
                    Toast.LENGTH_SHORT).show();

            // Since the device isn't calibrated, we'll do it here.
            calibrate(sharedPrefs);
        }

        getInputSettings(sharedPrefs);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        disconnect();
    }

    private boolean connect(SharedPreferences sharedPrefs) {

        final String macAddr = sharedPrefs.getString("pref_key_select_bt_device", "NO_DEVICE");
        if (macAddr.equals("NO_DEVICE")){
            return false;
        }
        // TODO:  I should probably request the socket in another thread, wait here for
        //        a specific period of time, and after the timeout call the cancel function

        Runnable requestBtSocket = new Runnable() {
            @Override
            public void run() {
                btManager.requestBluetoothSocket(macAddr);
            }
        };
        Thread requestBtSocketThread = new Thread(requestBtSocket);
        requestBtSocket.run();

        // Wait one second for the request to complete
        try {
            requestBtSocketThread.join(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (requestBtSocketThread.isAlive()) {
            // request timed out, close and return false
            btManager.closeBluetoothSocket();
            return false;
        }


        if (!btManager.isDeviceConnected()) {
           return false;
        }

        return true;
    }

    // TODO: change name to setScreenCoefficents?
    private void getInputSettings(SharedPreferences sharedPrefs) {

        // Calibration coefficients
        float A;
        float B;
        float C;
        float D;
        float E;
        float F;

        int zResistanceMin;
        int zResistanceMax;

        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        int rotation = myDisplay.getRotation();

        // Retreive coordinate coefficients based on rotation
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            // Portrait

            A = sharedPrefs.getFloat("pref_key_portrait_coefficient_a", 0.0f);
            B = sharedPrefs.getFloat("pref_key_portrait_coefficient_b", 0.0f);
            C = sharedPrefs.getFloat("pref_key_portrait_coefficient_c", 0.0f);
            D = sharedPrefs.getFloat("pref_key_portrait_coefficient_d", 0.0f);
            E = sharedPrefs.getFloat("pref_key_portrait_coefficient_e", 0.0f);
            F = sharedPrefs.getFloat("pref_key_portrait_coefficient_f", 0.0f);
        }
        else {
            // Landscape

            A = sharedPrefs.getFloat("pref_key_landscape_coefficient_a", 0.0f);
            B = sharedPrefs.getFloat("pref_key_landscape_coefficient_b", 0.0f);
            C = sharedPrefs.getFloat("pref_key_landscape_coefficient_c", 0.0f);
            D = sharedPrefs.getFloat("pref_key_landscape_coefficient_d", 0.0f);
            E = sharedPrefs.getFloat("pref_key_landscape_coefficient_e", 0.0f);
            F = sharedPrefs.getFloat("pref_key_landscape_coefficient_f", 0.0f);
        }

        zResistanceMin = sharedPrefs.getInt("pref_key_z_resistance_min", 0);
        zResistanceMax = sharedPrefs.getInt("pref_key_z_resistance_max", 0);

        if (uInput == null) {
            uInput = new NativeInput(A, B, C, D, E, F, zResistanceMin, zResistanceMax);
        }
        else {
            uInput.setCoefficients(A, B, C, D, E, F, zResistanceMin, zResistanceMax);
        }


    }

    public boolean isConnected() {

        // TODO: Check to see if the bluetooth connection is still established here;
        return mConnected;
    }


	/**
     * This function listens for input until the running loop is broken.  It is only
     * called from the Objects run() function, which should never be called from
     * the main thread, as it is blocking
     */
    private void listenForInput() {

        mRunning = true;

        int numBytes;
        byte[] buffer = new byte[256];  // Max line of 256
        while (mRunning) {
            try {
                numBytes = arudinoInput.read(buffer);

                // if a message was received, send it to the message handler
                // for processing
                if (numBytes > 0) {
                    Message msg = mInputHandler.obtainMessage();
                    msg.obj = buffer;
                    msg.arg1 = numBytes;
                    mInputHandler.sendMessage(msg);
                }

            } catch(IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                break;
            }

            // TODO: Should I sleep here at all?
        }

    }

	/**
     * Sends data to the arduino for processing
     * @param data
     * @return
     */
    public boolean writeData(String data) {
        byte[] bytes = data.getBytes();

        try {
            arduinoOutput.write(bytes);
        } catch(IOException e) {
            // Error sending the start command to the arduino
            return false;
        }
        return true;
    }

	/**
     * Parses bytes received from a serial read
	 *
     * @param message - the bytes to parse
     * @param numBytes - the number of bytes received in the message
     */
    private ArduinoMessage parseBytes(byte[] message, int numBytes) {

        // Create string from byte array.  The bytes come in this format:
        // <command:x:y:z>
        // We ignore the opening and closing brackets when creating our string
        String msg = new String(message, 1, numBytes - 2 );
        String[] tokens = msg.split(":");

        if (tokens.length != 4) {
            Log.e(TAG, "Issue parsing string, invalid data recd");
            return null;
        }

        ArduinoMessage ardMsg = new ArduinoMessage();
        ardMsg.command = tokens[0];
        ardMsg.point = new TouchPoint(Integer.parseInt(tokens[1]),
                Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));

        return ardMsg;
    }

    public void calibrate (SharedPreferences sharedPrefs) {

        if (!mConnected) {
            return;
        }

        mRunning = true;

        Intent calibrateIntent = new Intent(mContext, CalibrateTouchScreen.class);
        mContext.startActivity(calibrateIntent);

        // Tell the Arduino to start Calibrate
        writeData("<CAL_START>");
        int numBytes = 0;
        byte[] buffer = new byte[256];  // Max line of 256

        // Get the right center
        // TODO: need to get the bottom center and top left points as well. Then I need to calculate
        //       the conversion coefficients and store them in sharedprefs
        while (numBytes == 0) {

            try {
                numBytes = arudinoInput.read(buffer);
            } catch(IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                break;
            }

            // parse the bytes for x and y values, add them to shared preferences, and broadcast
            // an intent to the activity telling it to move to the next point
            if (numBytes > 0) {

                // Tell the Arudino to go to the next point
                writeData("<CAL_NEXT>");

                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) {

                }
            }
        }

        // TODO: Get zMinResistance and zMaxResistance so it can be calibrated as well

        // TODO: send points received to the calcCoefficients function

        // Tell arduino to stop calibration (If we want to calibrate both Landscape and Portrait
        // well send <CAL_CONTINUE>
        writeData("<CAL_END>");

        // TODO: set isCalibrated to true in shared preferences

    }

	/**
	 * Calculates Coefficients given screen coordinates
     *
     * @param touchP1   Right center point received from touchscreen
     * @param touchP2   Bottom center point received from touchscreen
     * @param touchP3   Top Left point received from touchscreen
     */
    private void calcCoefficients(SharedPreferences sharedPrefs,
                                  Point touchP1, Point touchP2, Point touchP3) {

        // Calibration coefficients
        float A;
        float B;
        float C;
        float D;
        float E;
        float F;

        Point deviceP1;     // Right Center device coordinate
        Point deviceP2;     // Bottom Center device coordinate
        Point deviceP3;     // Top Left device coordinate

        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);

        // Get 10% of each axis, as our touch points will be within those ranges
        int xOffset = Math.round(0.1f * maxSize.x);
        int yOffset = Math.round(0.1f * maxSize.y);

        deviceP1 = new Point((maxSize.x - xOffset), (maxSize.y / 2));
        deviceP2 = new Point((maxSize.x) / 2, (maxSize.y - yOffset));
        deviceP3 = new Point(xOffset, yOffset);

        // TODO: Verify math is correct

        A = (deviceP1.x * (touchP2.y - touchP3.y)) + (deviceP2.x * (touchP3.y - touchP1.y))
                + (deviceP3.x * (touchP1.y - touchP2.y));
        A = A / (touchP1.x * (touchP2.y - touchP3.y) + (touchP2.x * (touchP3.y - touchP1.y))
                + (touchP3.x * (touchP1.y - touchP2.y)));

        B = (A * (touchP3.x - touchP2.x)) + deviceP2.x - deviceP3.x;
        B = B / (touchP2.y - touchP3.y);

        C = deviceP3.x - (A * touchP3.x) - (B * touchP3.y);

        D = ((deviceP1.y * (touchP2.y - touchP3.y)) + (deviceP2.y * (touchP3.y - touchP1.y))
                + (deviceP3.y * (touchP1.y - touchP2.y)));
        D = D / ((touchP1.x * (touchP2.y - touchP3.y)) + (touchP2.x * (touchP3.y - touchP1.y))
                + (touchP3.x * (touchP1.y - touchP2.y)));

        E = ((D * (touchP3.x - touchP2.x)) + deviceP2.y - deviceP3.y);
        E = E / (touchP2.y - touchP3.y);

        F = deviceP3.y - (D * touchP3.x) - (E * touchP3.y);

        int rotation = myDisplay.getRotation();

        // Set coordinate coefficients based on rotation
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            // Portrait

            sharedPrefs.edit().putFloat("pref_key_portrait_coefficient_a", A)
                    .putFloat("pref_key_portrait_coefficient_b", B)
                    .putFloat("pref_key_portrait_coefficient_c", C)
                    .putFloat("pref_key_portrait_coefficient_d", D)
                    .putFloat("pref_key_portrait_coefficient_e", E)
                    .putFloat("pref_key_portrait_coefficient_f", F)
                    .apply();

        }
        else {
            // Landscape

            sharedPrefs.edit().putFloat("pref_key_landscape_coefficient_a", A)
                    .putFloat("pref_key_landscape_coefficient_b", B)
                    .putFloat("pref_key_landscape_coefficient_c", C)
                    .putFloat("pref_key_landscape_coefficient_d", D)
                    .putFloat("pref_key_landscape_coefficient_e", E)
                    .putFloat("pref_key_landscape_coefficient_f", F)
                    .apply();
        }

    }

    public void disconnect () {
        if (btManager!= null) {
            mRunning = false;
            btManager.uninitBluetooth();
            mConnected = false;
            btManager = null;
        }

        // TODO: unregister receivers here if they are registered
    }

    /**
     * Stops the loop in the run() command
     */
    public void stop() {
        // TODO:  should I write a stop command here to the arduino here?
        mRunning = false;
    }



}
