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
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Toast;


/**
 * Class ArduinoCom
 *
 * This class handles bluetooth serial communication with the arduino.  First, it establishes
 * a serial connection and confirms that the Arudino is connected.    After setup is complete, it will listen
 * for resistive touch screen events from the arudino and send them to the NativeInput class
 * where they can be handled by the uinput driver in the NDK.
 */
public class ArduinoCom implements Runnable{

    private static final String TAG = "ArduinoCom";

    private NativeInput uInput = null;
    private volatile boolean mConnected = false;
    private volatile boolean mConnectionFinished = false;
    private Context mContext;

    private volatile boolean mRunning = false;

    private SerialHelper mSerialHelper;
    private SerialHelper.DeviceReadyListener readyListener;

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
                mSerialHelper.writeString(data);
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

        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mInputLooper = thread.getLooper();
        mInputHandler = new InputHandler(mInputLooper);

        final SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(mContext);

        String deviceType = sharedPrefs.getString("pref_key_select_device_type", "BLUETOOTH");
        if (deviceType.equals("BLUETOOTH")) {
            // user selected bluetooth device
            mSerialHelper = new BluetoothHelper(mContext);
        }
        else {
            // user selected usb device
            mSerialHelper = new UsbHelper(mContext);

        }

        readyListener = new SerialHelper.DeviceReadyListener() {
            @Override
            public void OnDeviceReady(boolean deviceReadyStatus) {
                mConnected = deviceReadyStatus;
                mConnectionFinished = true;
            }
        };

        if (!connect(sharedPrefs)) {
            return;
        }

        if (!startUinput()) {
            mConnected = false;
            return;
        }
        String orientation = sharedPrefs.getString("pref_key_select_orientation", "Landscape");
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
        if (!mSerialHelper.writeString("<START>")) {
            // unable to write start command
            Log.e(TAG, "Unable to start arduino");
            mConnected = false;
        }

    }

    private boolean connect(SharedPreferences sharedPrefs) {

        final String devId = sharedPrefs.getString("pref_key_select_device", "NO_DEVICE");
        if (devId.equals("NO_DEVICE")){
            return false;
        }

        mSerialHelper.connectDevice(devId, readyListener);

        // wait until the connection is finished.  The readyListener is a callback that will
        // set the variable below and set mConnected to the connection status
        while(!mConnectionFinished);

        return mConnected;

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

    // Reads a message from the arduino and parses it
    private ArduinoMessage readMessage() {

        int bytes = 0;
        byte[] buffer = new byte[256];
        byte ch;

        // get the first byte, anything other than a '<' is trash and will be ignored
        while(mRunning && (mSerialHelper.readByte() != '<')) {
            // sleep for 50ms between polling
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        };

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
            if (mRunning) {
                // Only log an error if the device has been shut down, it always throws an
                // IOExeception when the socket is closed
                Log.e(TAG, "Issue parsing string, invalid data recd");
            }

            ardMsg = null;

        }

        return ardMsg;
    }

    public void disconnect () {
        mRunning = false;

        if (mSerialHelper!= null) {
            mSerialHelper.writeString("<STOP>");
            mSerialHelper.disconnect();
            mConnected = false;
            mConnectionFinished = false;
            mSerialHelper = null;
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
