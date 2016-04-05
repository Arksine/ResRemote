package com.arksine.resremote;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import com.github.amlcurran.showcaseview.MaterialShowcaseDrawer;
import com.github.amlcurran.showcaseview.ShowcaseDrawer;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.Target;


/**
 *Provides a User Interface for Resistive Touch Screen Calibration
 */
public class CalibrateTouchScreen extends Activity {

    private static final String TAG = "CalibrateTouchScreen";
    private static final int NUMPOINTS = 3;    // The number of points to calibrate per orientation
    private static final String ACTION_CALIBRATE_START = "com.arksine.resremote.ACTION_CALIBRATE_START";
    private static final String ACTION_CALIBRATE_NEXT = "com.arksine.resremote.ACTION_CALIBRATE_NEXT";
    private static final String ACTION_CALIBRATE_PRESSURE = "com.arksine.resremote.ACTION_CALIBRATE_PRESSURE";
    private static final String ACTION_CALIBRATE_END = "com.arksine.resremote.ACTION_CALIBRATE_END";

    // our receiver only needs to get data from our service, so we will use a LocalBroadcastManager
    private LocalBroadcastManager localBroadcast;

    private Point[] devicePoints;       // Array containing the location of each device point to show
    private int shapeSize;              // Size of the shapes to draw on the view
    private int pointsCalibrated = 0;   // number of points calibrated so far;
    private String orientationPref;     // the orientation requested in shared preferences

    ShowcaseView showcase;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private CalibrationView calView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            calView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.show();
            }

        }
    };

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public class CalibrateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PointTarget target;

            switch (action){
                case ACTION_CALIBRATE_START:
                    if (orientationPref.equals("Dynamic")) {
                        String curOrientation = intent.getStringExtra("orientation");
                        if (curOrientation.equals("Landscape")) {
                            // Lock orientation to landscape
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                        else {
                            // Lock orientation to Portrait
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        }
                    }
                    devicePoints = new Point[NUMPOINTS];
                    getDevicePoints();
                    calView.setDrawables(devicePoints[0], devicePoints[1], devicePoints[2],
                            shapeSize);
                    calView.invalidate();
                    target = new PointTarget(devicePoints[0]);
                    showcase.setTarget(target);
                    break;
                case ACTION_CALIBRATE_NEXT:
                    int pointIdx = intent.getIntExtra("point_index", 0);
                    target = new PointTarget(devicePoints[pointIdx]);
                    showcase.setTarget(target);
                    break;
                case ACTION_CALIBRATE_PRESSURE:
                    showcase.setTarget(Target.NONE);
                    showcase.setContentTitle(getString(R.string.cal_pressure_title));
                    showcase.setContentText(getString(R.string.cal_pressure_text));
                    break;
                case ACTION_CALIBRATE_END:
                    exitActivity();
                    break;
            }

        }
    }
    private final CalibrateReceiver calReceiver = new CalibrateReceiver();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_calibrate_touch_screen);
        calView = (CalibrationView) findViewById(R.id.CalView);

        localBroadcast = LocalBroadcastManager.getInstance(this);

        mHideHandler.post(mHideRunnable);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        orientationPref = sharedPrefs.getString("pref_key_select_orientation", "Landscape");

        getShapeSize();

        showcase = new ShowcaseView.Builder(this)
                .setShowcaseDrawer(new CalShowcaseDrawer(getResources(), (shapeSize / 2)))
                .setStyle(R.style.CalibrationShowcaseStyle)
                .setContentTitle(R.string.cal_title)
                .setContentText(R.string.cal_text)
                .blockAllTouches()
                .build();

        // register receiver
        IntentFilter filter = new IntentFilter(getString(R.string.ACTION_CALIBRATE_START));
        filter.addAction(getString(R.string.ACTION_CALIBRATE_NEXT));
        filter.addAction(getString(R.string.ACTON_CALIBRATE_PRESSURE));
        filter.addAction(getString(R.string.ACTION_CALIBRATE_END));
        localBroadcast.registerReceiver(calReceiver, filter);

    }

    private void exitActivity() {
        this.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        localBroadcast.unregisterReceiver(calReceiver);
    }

    private void getShapeSize() {
        DisplayManager displayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);

        // Shapesize is 10% of the screenwidth or height, whichever is the smallest
        shapeSize = maxSize.x / 10;
        if ((maxSize.y / 10) < shapeSize) {
            shapeSize = maxSize.y / 10;
        }
    }

    private void getDevicePoints() {

        DisplayManager displayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);

        // Get 10% of each axis, as our touch points will be within those ranges
        int xOffset = maxSize.x / 10;
        int yOffset = maxSize.y / 10;

        devicePoints[0] = new Point((maxSize.x - xOffset), (maxSize.y / 2));
        devicePoints[1] = new Point((maxSize.x) / 2, (maxSize.y - yOffset));
        devicePoints[2] = new Point(xOffset, yOffset);

    }


    private void hide() {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

	/**
     * This is identical to the MaterialShowcaseDrawer located in the library,
     * however it accepts a custom radius.  I need to be able to set the radius dynamically
     * as it is always a percentage of the screen.  DP doesn't do that.
     */
    public class CalShowcaseDrawer implements ShowcaseDrawer {

        private final float radius;
        private final Paint basicPaint;
        private final Paint eraserPaint;
        private int backgroundColor;

        public CalShowcaseDrawer(Resources resources, int radius) {
            this.radius = resources.getDimension(radius);
            this.eraserPaint = new Paint();
            this.eraserPaint.setColor(0xFFFFFF);
            this.eraserPaint.setAlpha(0);
            this.eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            this.eraserPaint.setAntiAlias(true);
            this.basicPaint = new Paint();
        }

        @Override
        public void setShowcaseColour(int color) {
            // no-op
        }

        @Override
        public void drawShowcase(Bitmap buffer, float x, float y, float scaleMultiplier) {
            Canvas bufferCanvas = new Canvas(buffer);
            bufferCanvas.drawCircle(x, y, radius, eraserPaint);
        }

        @Override
        public int getShowcaseWidth() {
            return (int) (radius * 2);
        }

        @Override
        public int getShowcaseHeight() {
            return (int) (radius * 2);
        }

        @Override
        public float getBlockedRadius() {
            return radius;
        }

        @Override
        public void setBackgroundColour(int backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        @Override
        public void erase(Bitmap bitmapBuffer) {
            bitmapBuffer.eraseColor(backgroundColor);
        }

        @Override
        public void drawToCanvas(Canvas canvas, Bitmap bitmapBuffer) {
            canvas.drawBitmap(bitmapBuffer, 0, 0, basicPaint);
        }


    };


}
