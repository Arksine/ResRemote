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


/**
 *  Helper class to enumerate usb devices and establish a connection
 */
public class UsbHelper implements SerialHelper {

    private static final String TAG = "UsbHelper";

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

    private Context mContext;
    private UsbManager mUsbManager;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbConnection;
    private UsbSerialDevice mSerialPort;

    private volatile boolean serialPortConnected = false;
    private SerialHelper.DeviceReadyListener mReadyListener;

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
                        if(uDev != mUsbDevice) {
                            Log.i(TAG, "Wrong usb device received");
                            mReadyListener.OnDeviceReady(false);
                            return;
                        }

                        if (uDev != null) {
                            mUsbConnection = mUsbManager.openDevice(mUsbDevice);
                            new ConnectionThread().start();
                        } else {
                            Log.d(TAG, "USB Device not valid");
                            mReadyListener.OnDeviceReady(false);
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + uDev);
                        mReadyListener.OnDeviceReady(false);
                    }
                }
            }

        }
    };

    public UsbHelper(Context context) {
        this.mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
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
            PendingIntent mPendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(mUsbDevice, mPendingIntent);
        } else {
            Log.i(TAG, "Invalid usb device: " + usbName);
            readyListener.OnDeviceReady(false);
        }

    }

    public void disconnect() {
        if (usbReceiverRegistered) {
            mContext.unregisterReceiver(mUsbReceiver);
            usbReceiverRegistered = false;
        }

        if (mSerialPort != null) {
            mSerialPort.syncClose();
            mSerialPort = null;
        }
        serialPortConnected = false;
    }

    public boolean writeData(String data) {

        if (mSerialPort != null) {
            mSerialPort.syncWrite(data.getBytes(), 0);
            return true;
        }

        return false;
    }

    public byte readByte() {

        if (mSerialPort != null) {

            // we are only reading one byte
            byte[] buffer = new byte[1];

            mSerialPort.syncRead(buffer, 0);
            return buffer[0];
        }
        else {
            return 0;
        }
    }

    public boolean isDeviceConnected() {
        return serialPortConnected;
    }

    // This thread opens a usb serial connection on the specified device
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            mSerialPort = UsbSerialDevice.createUsbSerialDevice(mUsbDevice, mUsbConnection);
            if (mSerialPort != null) {
                if (mSerialPort.syncOpen()) {
                    mSerialPort.setBaudRate(BAUD_RATE);
                    mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                    mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

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
