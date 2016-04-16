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
            ArduinoMessage message = (ArduinoMessage) msg.obj;
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
        //TODO: we need to break here, as at times we are somehow dereferencing a null inputstream
        //      (or we are reading/writing to one)

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

        if (!startUinput()) {
            mConnected = false;
            return;
        }

        // Set up the orientation listener
        orientationListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int orientation) {

                startUinput();
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

        // Tell the Arudino that it is time to start
        if (!writeData("<START>")) {
            // unable to write start command
            Log.e(TAG, "Unable to start arduino");
            mConnected = false;
        }

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

    private boolean startUinput() {

        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        int rotation = myDisplay.getRotation();

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);
        int xMax;
        int yMax;

        // Retreive coordinate coefficients based on rotation
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            // Portrait

            xMax = maxSize.x;
            yMax = maxSize.y;
        }
        else {
            // Landscape

            xMax = maxSize.y;
            yMax = maxSize.x;
        }

        if (uInput == null) {
            uInput = new NativeInput(xMax, yMax, rotation);
        }
        else {
            uInput.setupVirtualDevice(xMax, yMax, rotation);
        }

        return uInput.isVirtualDeviceOpen();
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

        while (mRunning) {

            ArduinoMessage message = readMessage();
            if (message != null) {
                if (message.command.equals("LOG")) {
                    Log.i("Arduino", message.desc);
                    if (message.desc.equals("Device not calibrated")) {
                        Toast.makeText(mContext, "Touchscreen is not calibrated", Toast.LENGTH_SHORT).show();
                    }
                } else {

                    Message msg = mInputHandler.obtainMessage();
                    msg.obj = message;
                    mInputHandler.sendMessage(msg);
                }
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


    // Reads a message from the arduino and parses it
    private ArduinoMessage readMessage() {

        int bytes = 0;
        byte[] buffer = new byte[256];
        byte ch;
        try {
            // get the first byte, anything other than a '<' is trash and will be ignored
            while((ch = (byte)arudinoInput.read()) != '<');

            // First byte is good, capture the rest until we get to the end of the message
            while ((ch = (byte)arudinoInput.read()) != '>') {
                buffer[bytes] = ch;
                bytes++;
            }
        }
        catch (IOException e){ return null;}

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
            Log.e(TAG, "Issue parsing string, invalid data recd");
            ardMsg = null;
        }

        return ardMsg;
    }

    public void disconnect () {
        mRunning = false;
        writeData("<STOP>");
        if (btManager!= null) {
            btManager.uninitBluetooth();
            mConnected = false;
            btManager = null;
        }

        if (uInput != null) {
            uInput.closeVirtualDevice();
        }

        if (isWriteReceiverRegistered) {
            mContext.unregisterReceiver(writeReciever);
            isWriteReceiverRegistered = false;
        }
    }

}
