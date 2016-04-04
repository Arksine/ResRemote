package com.arksine.resremote;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private ArduinoCom arduino = null;

    public class StopReciever extends BroadcastReceiver {
        public StopReciever() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (getString(R.string.ACTION_STOP_SERVICE).equals(action)) {
                // stops all queued services
                stopSelf();
            }

        }
    }
    private final StopReciever mStopReceiver = new StopReciever();

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            arduino = new ArduinoCom((Context)msg.obj);
            if (arduino.isConnected()) {
                arduino.run();
            }

            arduino.disconnect();
            arduino = null;

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;

    @Override
    public void onCreate() {

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

        // The code below registers a receiver that allows us
        // to stop the service through an action shown on the notification.
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_STOP_SERVICE));
        registerReceiver(mStopReceiver, filter);

        //TODO:  Need to make sure that adding .setClass works for the broadcast reciever
        Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE))
                .setClass(getApplicationContext(), ResRemoteService.StopReciever.class);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, R.integer.REQUEST_STOP_SERVICE,
                stopIntent, 0);

        // TODO: need to create and add .setLargeIcon(bitmap) to the notification.
        // TODO: need to create another action, that allows me to send a write command to the
        //         arduino that toggles the touchscreen switcher on and off (it probably just
        //         needs to send a pulse
        Intent notificationIntent = new Intent(this, ResRemoteActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                R.integer.REQUEST_START_RESREMOTE_ACTIVITY, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.service_notification_title))
                .setContentText("Service Running")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop,
                        "Stop Service", stopPendingIntent)
                .build();

        startForeground(R.integer.ONGOING_NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Stop the current thread if its running, so we can launch a new one (perhaps to calibrate)
        if (arduino != null) {
            arduino.stop();
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = this;
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

        unregisterReceiver(mStopReceiver);
        if (arduino != null) {
            arduino.disconnect();
        }
    }
}
