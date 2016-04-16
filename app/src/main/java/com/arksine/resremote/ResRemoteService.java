package com.arksine.resremote;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;


/**
 * Service that listens for events from a resistive touchscreen connected to an arduino,
 * translating them to android device input events
 */
public class ResRemoteService extends Service {

    private static String TAG = "ResRemoteService";

    private ArduinoCom arduino = null;
    private ScreenOrientationEnforcer landscapeEnforcer = null;
    private static volatile boolean policiesRelaxed = false;

    public class StopReciever extends BroadcastReceiver {
        public StopReciever() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (getString(R.string.ACTION_STOP_SERVICE).equals(action)) {

                if (arduino != null) {
                    arduino.disconnect();
                }
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

            // If root access is not available and granted, stop the service
            if (Shell.SU.available()) {
                if (Shell.SU.isSELinuxEnforcing()) {
                    Log.i(TAG, "SELinux is Enforcing");
                    if (! policiesRelaxed) {
                        /** TODO: SELinux is enforcing, run su command supolicy
                         *  to relax restrictions.
                         *
                         *  Currently known SELinux audit params:
                         *  source class: untrusted_app
                         *  target class: uhid_device
                         *  permission class: chr_file
                         *  permission: {read write}
                         *  Note - there are probably more permissions i'm missing
                         */
                        /*String sePolicyLocation = PreferenceManager
                                .getDefaultSharedPreferences(getApplicationContext())
                                .getString("pref_key_sepolicy_inject_location", "");


                        String args = "-s untrusted_app -t uhid_device -c chr_file -p append,write,read,ioctl,getattr,lock,open -l";
                        String command = sePolicyLocation + " " + args;*/
                        // TODO: try supolicy instead of seinject.  I still can't write to the device
                        // If it works remove all sepolicy-inject functionality
                        //String command = "supolicy --live " + //"'allow untrusted_app uhid_device dir { ioctl read getattr search open }' " +
                        //                "'allow untrusted_app uhid_device chr_file { write, read, ioctl, getattr, lock, append, open }'";
                        String command =  "setenforce Permissive";
                        List<String> output = Shell.SU.run(command);
                        Log.d(TAG, "sepolicy-inject output:");
                        for (String o: output) {
                            Log.d(TAG, o);
                        }
                        policiesRelaxed = true;
                    }
                }
            }
            else {
                Log.e(TAG, "Root access not granted:");
                stopSelf(msg.arg1);
                return;

            }

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
        // If this is the first time the service has been run

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstrun = sharedPrefs.getBoolean("pref_key_first_run", true);
        if (firstrun) {
            //TODO:  if we are using sepolicy-inject then uncomment this, otherwise remove this
            //       functionality as its not necessary

            copySepolicyInject();
            sharedPrefs.edit().putBoolean("pref_key_first_run", false).apply();
        }

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

        landscapeEnforcer = new ScreenOrientationEnforcer(this);

        // The code below registers a receiver that allows us
        // to stop the service through an action shown on the notification.
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_STOP_SERVICE));
        registerReceiver(mStopReceiver, filter);

        Intent stopIntent = new Intent(getString(R.string.ACTION_STOP_SERVICE));

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

        // Force a specific orientation  if it is set in shared preferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String orientation = sharedPrefs.getString("pref_key_select_orientation", "Landscape");
        switch (orientation) {
            case "Landscape":
                landscapeEnforcer.start(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case "Portrait":
                landscapeEnforcer.start(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case "Dynamic":
                landscapeEnforcer.stop();
                break;
            default:
                Log.e(TAG, "Invalid orientation selected");
                landscapeEnforcer.stop();
                break;
        }

        // Stop the current thread if its running, so we can launch a new one (perhaps to calibrate)
        if (arduino != null) {
            arduino.disconnect();
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

        // re-enable SELinux policies
        if(policiesRelaxed) {
            //String command =
            //        "supolicy --live 'deny appdomain uhid_device chr_file { write, read, ioctl, getattr, lock, append, open }'";
            //TODO: determine which policies need to be deleted
            String command = "setenforce Enforcing";
            List<String> output = Shell.SU.run(command);
            Log.d(TAG, "sepolicy-inject output:");
            for (String o: output) {
                Log.d(TAG, o);
            }

            policiesRelaxed = false;

        }

        unregisterReceiver(mStopReceiver);
        if (arduino != null) {
            arduino.disconnect();
        }

        if (landscapeEnforcer != null) {
            landscapeEnforcer.stop();
        }

    }

    //TODO:  Keep bottom 3 functions or delete?
    /**Copies sepolicy-inject binary from assets to private storage and sets it
     * as executable so we can change policies if necessary
     */
    private void copySepolicyInject() {
        AssetManager assetManager = getAssets();

        String fileName = findAsset(assetManager);

        if (fileName.equals("")){
            Log.i(TAG, "No matching sepolicy-inject binary found for your ABI");
            return;
        }

        InputStream in;
        OutputStream out;
        String outputFileName = getFilesDir().getAbsolutePath() + "/sepolicy-inject";

        try {
            in = assetManager.open(fileName);

            File outFile = new File(outputFileName);
            out = new FileOutputStream(outFile);

            copyFile(in, out);
            in.close();
            out.flush();
            out.close();

        }
        catch (IOException e){
            Log.e(TAG, "Failed to copy asset file: " + outputFileName, e);
        }

        // set permissions (shouldn't need root)
        String command = "chmod a+x " + outputFileName;
        List<String> cmdOutput = Shell.SH.run(command);
        Log.d(TAG, "chmod output:");
        for (String o: cmdOutput) {
            Log.d(TAG, o);
        }

        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("pref_key_sepolicy_inject_location", outputFileName)
                .apply();

    }

    private String findAsset(AssetManager assetManager){

        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e(TAG, "Failed to get asset file list.", e);
        }
        if (files == null){
            return "";
        }

        String assetLocation;
        for (String abi: Build.SUPPORTED_ABIS ) {
            assetLocation = abi;
            Log.d(TAG, "Searching for asset: " + assetLocation);
            for (String filename : files) {
                Log.d(TAG, "Checking against asset: " +filename);
                if (filename.equals(assetLocation)) {
                    Log.i(TAG, "Asset location match: " + assetLocation);
                    return assetLocation + "/sepolicy-inject";
                }
            }
        }
        return "";
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
