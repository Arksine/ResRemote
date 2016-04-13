package com.arksine.resremote.calibrationtool;

import android.content.Context;
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
import com.felhr.usbserial.UsbSerialDevice;


import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by Eric on 4/12/2016.
 */
public class UsbHelper {

    private static final String TAG = "UsbHelper";

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

    private Context mContext;
    private UsbManager mUsbManager;
    private UsbDevice mUsbdevice;
    private UsbDeviceConnection mUsbConnection;
    private UsbSerialDevice mSerialPort;

    private boolean serialPortConnected;

    public UsbHelper(Context context) {
        this.mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(mContext.USB_SERVICE);
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

    }

    public boolean writeData(String data) {

    }

    public byte readByte() {

    }
}
