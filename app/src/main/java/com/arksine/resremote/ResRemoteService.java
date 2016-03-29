package com.arksine.resremote;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

/**
 * TODO: 3/28/2016
 * The service below needs to be launched in the foreground.  We will need an activity to
 * set up any options we require and calibrate the touchscreen.  We will need a class that
 * handles Bluetooth communication with the Arudino that implements a runnable and runs in another
 * thread.  We will also need a class that takes the input recieved from the arduino and calls the
 * native uinput libraries.
 *
 * Right now the minimum api required is api-19, but I may need to change tha to api 21 as I don't
 * believe uinput.h is in the ndk for api-19.
 */


public class ResRemoteService extends Service {

    private static String TAG = "ResRemoteService";

    private ArduinoCom arduino;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private volatile boolean mConnected = false;


    private final class ProcessType {
        public final int CONNECT = 0;
        public final int CALIBRATE = 1;
        public final int LISTEN = 2;
    };


    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {



        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.arg2) {
                case ProcessType.CONNECT:
                    arduino.connect();
                    break;
                case ProcessType.CALIBRATE:
                    arduino.calibrate();
                    break;
                case ProcessType.LISTEN:
                    arduino.listenForInput();
                    break;
                default:
                    stopSelf(msg.arg1);
            }


            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {

        arduino = new ArduinoCom(this);

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();




        // TODO: need to call connect from the Service handler, as it relies on serial communication
        //       and would block the ui thread
        if (!arduino.isConnected()) {

            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job
            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.arg2 = ProcessType.CONNECT;
            mServiceHandler.sendMessage(msg);
        }

        // TODO: Need to get extras from intent to determine if this is a command to calibrate
        //       or a command to process input

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.arg2 = ProcessType.LISTEN;   //
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }



    @Override
    public IBinder onBind(Intent intent) {
        // This service doesn't support binding
        return null;
    }

    @Override
    public void onDestroy() {
        mConnected = false;
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }
}
