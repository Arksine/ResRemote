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

            // TODO: send screen input to the NativeInput class here
        }
    }

    @Override
    public void run() {
        listenForInput();
    }

// TODO:  Need a broadcast reciever to listen for changes to the rotation so
    //        the uInput coordinates can be updated

    // TODO: Need a broadcast reciever to listen for write commands?

    // TODO:  Need to add option to lock rotation to landscape, or just do it by default


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
        String start = "<start>";
        if (!writeData(start)) {
            // unable to write start command
            mConnected = false;
            return;
        }

        // TODO: Need to PUT pref_key_iscalibrated as true in the CalibrateTouchScreen activity
        boolean isCalibrated = sharedPrefs.getBoolean("pref_key_iscalibrated", false);

        if (!isCalibrated) {
            Log.i(TAG, "Touch Screen not calibrated, ");
            Toast.makeText(mContext, "Screen not calibrated, launching calibration activity",
                    Toast.LENGTH_SHORT).show();

            // Since the device isn't calibrated, we'll do it here.
            calibrate();
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

    // TODO: change name to setScreenCoordinates?
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


    // TODO:  This needs its own thread
    public void listenForInput() {

        mRunning = true;

        int numBytes;
        byte[] buffer = new byte[256];  // Max line of 256
        while (mRunning) {

            // TODO:  send command to NativeInput.  Should probably do it via message to
            //        a handler in another thread
            try {
                numBytes = arudinoInput.read(buffer);
            } catch(IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                break;
            }

        }

    }

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
     * @param command - the command received from the bytes
     * @param coordinates - the coordinates related to the command received
     */
    private void parseBytes(byte[] message, int numBytes, String command, Point coordinates) {

    }

    public void calibrate () {

        if (!mConnected) {
            return;
        }

        mRunning = true;

        Intent calibrateIntent = new Intent(mContext, CalibrateTouchScreen.class);
        mContext.startActivity(calibrateIntent);
        // TODO:  need to listen for calibration data from arduino
        //        as the activity guides the user where to press.  After each press, we send an intent
        //        to the calibration activity guiding the user where to press next until calibration
        //        is complete, at which case we send an intent telling the activity to close itself

        // Get the first three touch points

        int numBytes = 0;
        byte[] buffer = new byte[256];  // Max line of 256

        // Get the top left point
        while (numBytes == 0) {

            // TODO:  send command to NativeInput.  Should probably do it via message to
            //        a handler in another thread
            try {
                numBytes = arudinoInput.read(buffer);
            } catch(IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                break;
            }

            // parse the bytes for x and y values, add them to shared preferences, and broadcast
            // an intent to the activity telling it to move to the next point
            if (numBytes > 0) {

            }
        }

        // TODO: Top right point

        // TODO: Bottom right point

        // TODO: set isCalibrated to true in shared preferences

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
