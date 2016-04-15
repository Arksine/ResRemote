package com.arksine.resremote.calibrationtool;

import java.util.ArrayList;

/**
 * Interface for basic serial device functionality
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
