package com.arksine.resremote.calibrationtool;

import android.content.Context;

/**
 * Created by Eric on 4/12/2016.
 */
public class ArduinoCom implements Runnable{

    private static final String TAG = "ArduinoCom";
    private Context mContext;

    private volatile boolean mRunning = false;
    private volatile int mPointIndex = 0;

    SerialHelper serialHelper;

    /**
     * This is an interface for a callback the activity can use to be
     * notified when a point has been received.  The Activity
     * must implement this interface in its onCreateMethod
     */
    public interface OnItemReceivedListener {
        void onStartReceived();
        void onPointReceived(int pointIndex);
        void onPressureReceived();
    }
    private OnItemReceivedListener mOnItemRecdListener;


    public ArduinoCom(Context context) {
       this.mContext = context;
    }

    public void connect(OnItemReceivedListener onItemReceivedListener) {
        mOnItemRecdListener = onItemReceivedListener;

    }

    public void disconnect() {

    }

    @Override
    public void run() {

    }


}
