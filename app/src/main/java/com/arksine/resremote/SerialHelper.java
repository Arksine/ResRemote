package com.arksine.resremote;

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
    boolean writeString(String data);
    boolean writeBytes(byte[] data);
    byte readByte();
}
