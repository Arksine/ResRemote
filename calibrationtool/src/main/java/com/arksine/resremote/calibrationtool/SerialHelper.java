package com.arksine.resremote.calibrationtool;

import java.util.ArrayList;

/**
 * Created by Eric on 4/12/2016.
 */
public interface SerialHelper {

    interface DeviceReadyListener{
        void OnDeviceReady(boolean deviceReadyStatus);
    }

    ArrayList<String> enumerateDevices();
    void connectDevice(String id, DeviceReadyListener deviceReadyListener);
    void disconnect();
    boolean isDeviceConnected();
    boolean writeData(String data);
    byte readByte();
}
