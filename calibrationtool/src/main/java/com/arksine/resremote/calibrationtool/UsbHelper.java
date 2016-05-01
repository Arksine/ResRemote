package com.arksine.resremote.calibrationtool;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.felhr.deviceids.CH34xIds;
import com.felhr.deviceids.CP210xIds;
import com.felhr.deviceids.FTDISioIds;
import com.felhr.deviceids.PL2303Ids;
import com.felhr.deviceids.XdcVcpIds;
import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 *  Helper class to enumerate usb devices and establish a connection
 */
public class UsbHelper implements SerialHelper {

    private static final String TAG = "UsbHelper";

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_DEVICE_CHANGED = "com.arksine.resremote.ACTION_DEVICE_CHANGED";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

    private Context mContext;
    private UsbManager mUsbManager;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbConnection;
    private UsbSerialDevice mSerialPort;

    private volatile boolean serialPortConnected = false;
    private static SerialHelper.DeviceReadyListener mReadyListener;

    private volatile boolean requestApproved = false;
    private volatile boolean requestFinished = false;
    private boolean usbReceiverRegistered = false;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_USB_PERMISSION)) {
                synchronized (this) {
                    UsbDevice uDev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean accessGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (accessGranted) {
                        if (uDev != null) {
                            requestApproved = true;
                        } else {
                            Log.d(TAG, "USB Device not valid");
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + uDev);
                    }
                    requestFinished = true;
                }
            } else if (action.equals(ACTION_USB_ATTACHED) || action.equals(ACTION_USB_DETACHED)) {
                // send a broadcast for the main activity to repopulate the device list if a device
                // is connected or disconnected
                Intent devChanged = new Intent(ACTION_DEVICE_CHANGED);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(devChanged);
                Log.i(TAG, "Usb device attached or removed");
            }
        }
    };

   LinkedBlockingQueue<Byte> serialBuffer;

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(byte[] arg0)
        {
            // add the incoming bytes to a buffer
            for (byte ch : arg0) {
                try {
                    serialBuffer.put(ch);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Unable to add incoming byte to queue", e);
                }
            }
        }
    };


    public UsbHelper(Context context) {
        this.mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        serialBuffer = new LinkedBlockingQueue<>(128);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_DETACHED);
        mContext.registerReceiver(mUsbReceiver, filter);
        usbReceiverRegistered = true;
    }

    public ArrayList<String> enumerateDevices() {

        ArrayList<String> deviceList = new ArrayList<>(5);

        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();

        for (UsbDevice uDevice : usbDeviceList.values()) {

            String name;
            if (UsbSerialDevice.isCdcDevice(uDevice)) {
                name = "CDC serial device";
            }
            else if (CH34xIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())){
                name = "CH34x serial device";
            }
            else if (CP210xIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                name = "CP210X serial device";
            }
            else if (FTDISioIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                name = "FTDI serial device";
            }
            else if (PL2303Ids.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                name = "PL2303 serial device";
            }
            else if (XdcVcpIds.isDeviceSupported(uDevice.getVendorId(), uDevice.getProductId())) {
                name = "Virtual serial device";
            }
            else {
                // not supported
                break;
            }

            // replace the name with the real name if android supports it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                name = uDevice.getProductName();
            }

            Log.i(TAG, "usb device found: " + name);
            Log.i(TAG, "Device ID: " + uDevice.getDeviceId());
            Log.i(TAG, "Device Name: " + uDevice.getDeviceName());
            Log.i(TAG, "Vendor: ID " + uDevice.getVendorId());
            Log.i(TAG, "Product ID: " + uDevice.getProductId());

            String entry = name + "\n" +  uDevice.getDeviceName();
            deviceList.add(entry);
        }

        return deviceList;
    }

    public void connectDevice(String usbName, SerialHelper.DeviceReadyListener readyListener ) {

        mReadyListener = readyListener;
        HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();
        mUsbDevice = usbDeviceList.get(usbName);

        if (mUsbDevice != null) {
            // valid device, request permission to use
            new ConnectionThread().start();

        } else {
            Log.i(TAG, "Invalid usb device: " + usbName);
            readyListener.OnDeviceReady(false);
        }

    }

    public void disconnect() {

        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
        serialPortConnected = false;

        if (usbReceiverRegistered) {
            mContext.unregisterReceiver(mUsbReceiver);
            usbReceiverRegistered = false;
        }
    }

    public boolean writeString(String data) {

        if (mSerialPort != null) {
            mSerialPort.write(data.getBytes());
            return true;
        }

        return false;
    }

    public boolean writeBytes(byte[] data) {
        if (mSerialPort != null) {
            mSerialPort.write(data);
            return true;
        }

        return false;
    }

    public byte readByte() {

        if (mSerialPort != null) {
            Byte ch;

            try {
                // blocks until something is received, but there should always be something
                ch = serialBuffer.poll(100, TimeUnit.MILLISECONDS);
                if (ch == null) {
                    // timeout occured, return null byte
                    return 0;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Unable to retrieve byte", e);
                return 0;
            }

            return ch;
        }

        return 0;
    }

    public boolean isDeviceConnected() {
        return serialPortConnected;
    }

    // This thread opens a usb serial connection on the specified device
    private class ConnectionThread extends Thread {

        @Override
        public void run() {
            PendingIntent mPendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(mUsbDevice, mPendingIntent);

            while(!requestFinished) {
                // wait for the request to finish
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.i(TAG, "Sleep interrupted", e);
                }
            }

            if (!requestApproved) {
                mReadyListener.OnDeviceReady(false);
                return;
            }

            mUsbConnection = mUsbManager.openDevice(mUsbDevice);

            mSerialPort = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mUsbConnection);
            if (mSerialPort != null) {
                if (mSerialPort.open()) {
                    mSerialPort.setBaudRate(BAUD_RATE);
                    mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    mSerialPort.read(mCallback);

                    // Device is open and ready
                    serialPortConnected = true;
                    mReadyListener.OnDeviceReady(true);
                 } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (mSerialPort instanceof CDCSerialDevice) {
                        Log.i(TAG, "Unable to open CDC Serial device");
                        mReadyListener.OnDeviceReady(false);
                    } else {
                        Log.i(TAG, "Unable to open serial device");
                        mReadyListener.OnDeviceReady(false);
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Log.i(TAG, "Serial Device not supported");
                mReadyListener.OnDeviceReady(false);
            }
        }
    }
}
