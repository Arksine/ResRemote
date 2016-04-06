package com.arksine.resremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
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

    private static final String TAG = "ArduinoCom";

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
            uInput.processInput(message.command, message.point);
        }
    }

    @Override
    public void run() {
        listenForInput();
    }

    // Event listener to detect changes in orientation
    private OrientationEventListener orientationListener;

    // Broadcast reciever to listen for write commands.
    public class WriteReciever extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mContext.getString(R.string.ACTION_SEND_DATA).equals(action)) {
                // stops all queued services
                String data = intent.getStringExtra(mContext.getString(R.string.EXTRA_DATA));
                writeData(data);
            }
        }
    }
    private final WriteReciever writeReciever = new WriteReciever();
    boolean isWriteReceiverRegistered = false;

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
        final SharedPreferences sharedPrefs =
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

        // Determine if the screen is calibrated
        String orientation = sharedPrefs.getString("pref_key_select_orientation", "Landscape");
        boolean isCalibrated;
        switch (orientation) {
            case "Landscape":
                isCalibrated = sharedPrefs.getBoolean("pref_key_is_landscape_calibrated", false);
                break;
            case "Portrait":
                isCalibrated = sharedPrefs.getBoolean("pref_key_is_portrait_calibrated", false);
                break;
            case "Dynamic":
                isCalibrated = (sharedPrefs.getBoolean("pref_key_is_landscape_calibrated", false) &&
                        sharedPrefs.getBoolean("pref_key_is_portrait_calibrated", false));
                break;
            default:
                Log.e(TAG, "Invalid orientation selected");
                mConnected = false;
                return;
        }

        if (!isCalibrated) {
            Log.i(TAG, "Touch Screen not calibrated, ");
            Toast.makeText(mContext, "Screen not calibrated, launching calibration activity",
                    Toast.LENGTH_SHORT).show();

            // Since the device isn't calibrated, we'll do it here.
            calibrate(sharedPrefs);
        }

        // Tell the Arudino that it is time to start
        String start = "<START>";
        if (!writeData(start)) {
            // unable to write start command
            mConnected = false;
            return;
        }

        setCoefficients(sharedPrefs);

        // Set up the orientation listener
        orientationListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                setCoefficients(PreferenceManager.getDefaultSharedPreferences(mContext));
            }
        };

        // Only enable the orientationListener if we are allowing dynamic orientation
        if (orientation.equals("Dynamic")) {
            orientationListener.enable();
        }
        else {
            orientationListener.disable();
        }

        //Register write data receiver
        IntentFilter sendDataFilter = new IntentFilter(mContext.getString(R.string.ACTION_SEND_DATA));
        mContext.registerReceiver(writeReciever, sendDataFilter);
        isWriteReceiverRegistered = true;

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

        // Request a socket in another thread so we can timeout after one second
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


        return btManager.isDeviceConnected();

    }

    private void setCoefficients(SharedPreferences sharedPrefs) {

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

                // blocks until bytes are read
                numBytes = arudinoInput.read(buffer);

                // if a message was received, send it to the message handler
                // for processing

                Message msg = mInputHandler.obtainMessage();
                msg.obj = buffer;
                msg.arg1 = numBytes;
                mInputHandler.sendMessage(msg);


            } catch(IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                break;
            }

        }

    }

	/**
     * Sends data to the arduino for processing
     * @param data  Data to write to the arduino
     * @return  true is successful, false otherwise
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

        LocalBroadcastManager localBroadcast = LocalBroadcastManager.getInstance(mContext);
        Point[] touchPoints;

        if (!mConnected) {
            return;
        }

        mRunning = true;

        Intent calibrateIntent = new Intent(mContext, CalibrateTouchScreen.class);
        mContext.startActivity(calibrateIntent);

        String orientation = sharedPrefs.getString("pref_key_select_orientation", "Landscape");
        // If the orientation is dynamic, we need run through the orientation twice
        if (orientation.equals("Dynamic")) {
            // Get Landscape touchpoints
            calibrateIntent = new Intent(mContext.getString(R.string.ACTION_CALIBRATE_START));
            calibrateIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
            calibrateIntent.putExtra("orientation", "Landscape");
            localBroadcast.sendBroadcastSync(calibrateIntent);
            touchPoints = getTouchPoints(localBroadcast);
            if (touchPoints == null) {
                return;
            }
            calcCoefficients(sharedPrefs, touchPoints[0], touchPoints[1], touchPoints[2]);

            // Get portrait touchpoints
            calibrateIntent = new Intent(mContext.getString(R.string.ACTION_CALIBRATE_START));
            calibrateIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
            calibrateIntent.putExtra("orientation", "Portrait");
            localBroadcast.sendBroadcastSync(calibrateIntent);
            touchPoints = getTouchPoints(localBroadcast);
            if (touchPoints == null) {
                return;
            }
            calcCoefficients(sharedPrefs, touchPoints[0], touchPoints[1], touchPoints[2]);

        }
        else {

            // Rotation is locked, so this will work for both Portrait and Landscape
            calibrateIntent = new Intent(mContext.getString(R.string.ACTION_CALIBRATE_START));
            calibrateIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
            localBroadcast.sendBroadcastSync(calibrateIntent);
            touchPoints = getTouchPoints(localBroadcast);
            if (touchPoints == null) {
                return;
            }
            calcCoefficients(sharedPrefs, touchPoints[0], touchPoints[1], touchPoints[2]);

        }

        calcResistance(localBroadcast, sharedPrefs);


        // broadcast an intent to the calibration activity telling it to end
        calibrateIntent = new Intent(mContext.getString(R.string.ACTION_CALIBRATE_END));
        calibrateIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
        localBroadcast.sendBroadcast(calibrateIntent);

        // Set ix_x_calibrated to true
        switch (orientation) {
            case "Landscape":
                sharedPrefs.edit().putBoolean("pref_key_is_landscape_calibrated", true).apply();
                break;
            case "Portrait":
                sharedPrefs.edit().putBoolean("pref_key_is_portrait_calibrated", true).apply();
                break;
            case "Dynamic":
                sharedPrefs.edit().putBoolean("pref_key_is_landscape_calibrated", true)
                        .putBoolean("pref_key_is_portrait_calibrated", true)
                        .apply();
                break;
            default:
                // Invalid orientation selection
                Log.e(TAG, "Invalid Orientation selected");
                break;
        }

    }

    private Point[] getTouchPoints(LocalBroadcastManager localBroadcast) {

        Point[] touchPoints = new Point[3];
        int numBytes;
        byte[] buffer = new byte[256]; // Max line size of 256
        ArduinoMessage screenPt;
        Intent calIntent;

        // Get the three calibration touch points
        for (int i = 0; i < 3; i++) {

            // Tell the Arudino to recieve a single point
            writeData("<CAL_POINT>");

            try {
                numBytes = arudinoInput.read(buffer);

                screenPt = parseBytes(buffer, numBytes);
                if (screenPt == null){
                    // Error parsing bytes
                    Log.e(TAG, "Error Parsing Calibration data from arduino");
                    return null;
                }
                touchPoints[i] = new Point(screenPt.point.getX(), screenPt.point.getY());

            } catch (IOException e) {
                // If the device is disconnected or we have a read error, break the loop
                return null;
            }

            // Since we are telling the activity to move to the next point, we don't need
            // to move past index 2 (which is the third point)
            if (i < 2) {

                calIntent = new Intent(mContext.getString(R.string.ACTION_CALIBRATE_NEXT));
                calIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
                // send an index to the next point
                calIntent.putExtra("point_index", (i + 1));
                localBroadcast.sendBroadcastSync(calIntent);
            }

            // Sleep for one second so the UI has time to animate to the next point
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { }

        }
        return touchPoints;
    }

	/**
	 *  Reads Z-resistance min and max from arduino
     */
    private class ReadResistanceRunnable implements Runnable {
        private volatile boolean isRunning = true;

        private int resistanceMin = 0xFFFFFFFF;
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

            int numBytes;
            byte[] buffer = new byte[256];
            ArduinoMessage screenPt;

            writeData("<CAL_PRESSURE>");
            while (isRunning) {

                try {
                    numBytes = arudinoInput.read(buffer);

                    screenPt = parseBytes(buffer, numBytes);
                    if (screenPt == null){
                        // Error parsing bytes
                        Log.e(TAG, "Error Parsing Calibration data from arduino");
                        break;
                    }
                    // We'll receive a stop command from the arduino after the user has
                    // lifted their finger
                    if (screenPt.command.equals("<STOP>")){
                        isRunning = false;
                        break;
                    }

                    if (screenPt.point.getZ() < resistanceMin) {
                        resistanceMin = screenPt.point.getZ();
                    }
                    if (screenPt.point.getZ() > resistanceMax) {
                        resistanceMax = screenPt.point.getZ();
                    }

                } catch (IOException e) {
                    // If the device is disconnected or we have a read error, break the loop
                    break;
                }
            }

        }
    }

    private void calcResistance(LocalBroadcastManager localBroadcast,
                                SharedPreferences sharedPrefs) {

        // We are going to read pressure in another thread so we can set
        // a timeout
        final ReadResistanceRunnable readResistance = new ReadResistanceRunnable();
        Thread readResistanceThread = new Thread(readResistance);

        // Tell the calibration activity to start receiving pressure
        Intent calIntent = new Intent(mContext.getString(R.string.ACTON_CALIBRATE_PRESSURE));
        calIntent.setClass(mContext, CalibrateTouchScreen.CalibrateReceiver.class);
        localBroadcast.sendBroadcastSync(calIntent);

        // Start the thread
        readResistanceThread.start();

        // give a 10 second timeout
        try {
            readResistanceThread.join(10000);
        }
        catch (InterruptedException e) {}

        // kill the thread if its still alive
        if (readResistanceThread.isAlive())
        {
            readResistance.stopRunning();
            // TODO: should probably tell the activity that the request to get pressure timed out
        }

        writeData("<CAL_PRESSURE_END>");

        sharedPrefs.edit().putInt("pref_key_z_resistance_min", readResistance.getResistanceMin())
                .putInt("pref_key_z_resistance_max", readResistance.getResistanceMax())
                .apply();

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

        if (isWriteReceiverRegistered) {
            mContext.unregisterReceiver(writeReciever);
            isWriteReceiverRegistered = false;
        }
    }

    /**
     * Stops the loop in the run() command
     */
    public void stop() {

        mRunning = false;
    }



}
