package com.arksine.resremote.calibrationtool;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.hardware.display.DisplayManager;
import android.os.Bundle;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.amlcurran.showcaseview.ShowcaseDrawer;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.Target;

public class CalibrateTouchActivity extends AppCompatActivity {

    private static final String TAG = "CalibrateTouchActivity";
    private static final int NUMPOINTS = 3;    // The number of points to calibrate per orientation

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    private final Handler mEventHandler = new Handler();
    private CalibrationView mCalView;

    private Point[] mDevicePoints;       // Array containing the location of each device point to show
    private Point mCenterPoint;          // center point of the screen
    private volatile int mPointIndex;
    private int mShapeSize;              // Size of the shapes to draw on the view

    ShowcaseView mShowcase;



    ArduinoCom mArduino;
    ArduinoCom.OnItemReceivedListener mItemReceivedListener;

    private final Runnable mHideRunnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mCalView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);


            // build the showcase view after everything is hidden
            buildShowcase();
            mArduino.connect(mItemReceivedListener);
        }
    };

    private final Runnable exitActivityRunnable = new Runnable() {
        @Override
        public void run() {
            exitActivity();
        }
    };

    /**
     *  Start Runnables for UI updates (they are executed in another thread)
     */
    private final Runnable uiStartRunnable = new Runnable() {
        @Override
        public void run() {
            mDevicePoints = new Point[NUMPOINTS];
            getDevicePoints();
            mCalView.showPoint(mDevicePoints[0], mShapeSize);
            mCalView.invalidate();
            PointTarget target = new PointTarget(mDevicePoints[0]);
            //showcase.setShouldCentreText(true);
            mShowcase.setContentText(getString(R.string.cal_point_text));
            mShowcase.setShowcase(target, true);
        }
    };

    private final Runnable uiMovePointRunnable = new Runnable() {
        @Override
        public void run() {
            mCalView.showPoint(mDevicePoints[mPointIndex], mShapeSize);
            mCalView.invalidate();
            PointTarget target = new PointTarget(mDevicePoints[mPointIndex]);
            mShowcase.setShowcase(target, true);
        }
    };

    private final Runnable uiPressureRunnable = new Runnable() {
        @Override
        public void run() {
            mCalView.showPoint(mCenterPoint, mShapeSize);
            mCalView.invalidate();
            PointTarget target = new PointTarget(mCenterPoint);
            mShowcase.setShowcase(target, true);
            mShowcase.setContentTitle(getString(R.string.cal_pressure_title));
            mShowcase.setContentText(getString(R.string.cal_pressure_text));
        }
    };

    private final Runnable uiFinishedRunnable = new Runnable() {
        @Override
        public void run() {
            mCalView.hidePoint();
            mCalView.invalidate();
            mShowcase.setTarget(Target.NONE);
            mShowcase.setContentTitle(getString(R.string.cal_finshed_title));
            mShowcase.setContentText(getString(R.string.cal_finished_text));
        }
    };
    /**
     * End Runnables for UI updates
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calibrate_touch);
        mCalView = (CalibrationView) findViewById(R.id.CalView);

        /**
         * Override a listener so we can react to events from the touchscreen
         */
        mItemReceivedListener = new ArduinoCom.OnItemReceivedListener() {
            @Override
            public void onStartReceived(boolean connectionStatus) {

                if (connectionStatus) {
                    // device ready, show UI and start the arudino thread
                    runOnUiThread(uiStartRunnable);
                    mArduino.start();
                }
                else {
                    Runnable toast = new Runnable() {
                        @Override
                        public void run() {
                            // unable to connect to device
                            Toast.makeText(getApplicationContext(), "Error connecting to device, exiting",
                                    Toast.LENGTH_SHORT).show();
                        }
                    };
                    runOnUiThread(toast);
                    mArduino.disconnect();
                    mEventHandler.postDelayed(exitActivityRunnable, 3000);
                }

            }


            @Override
            public void onPointReceived(int pointIndex) {

                switch (pointIndex) {
                    case 0:
                    case 1:
                        mPointIndex = pointIndex + 1;
                        runOnUiThread(uiMovePointRunnable);
                        break;
                    case 2:
                        runOnUiThread(uiPressureRunnable);
                }
            }

            @Override
            public void onPressureReceived(boolean success) {
                if (success) {
                    runOnUiThread(uiFinishedRunnable);
                } else {
                    Runnable toast = new Runnable() {
                        @Override
                        public void run() {
                            // unable to connect to device
                            Toast.makeText(getApplicationContext(), "Error receiving pressure, exiting",
                                    Toast.LENGTH_SHORT).show();
                        }
                    };
                    runOnUiThread(toast);

                    mArduino.disconnect();
                    mEventHandler.postDelayed(exitActivityRunnable, 3000);
                }
            }

            @Override
            public void onFinished(boolean success) {
                if (!success) {
                    // error writing calibration values to arduino
                    Runnable toast = new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Error writing calibration values to arduino",
                                    Toast.LENGTH_SHORT).show();
                        }
                    };
                   runOnUiThread(toast);
                }
                mArduino.disconnect();
                mEventHandler.postDelayed(exitActivityRunnable, 3000);
            }
        };

        // Lock orientation to landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mArduino = new ArduinoCom(this);
        mDevicePoints = new Point[3];
        getDevicePoints();
        hide();

    }

    private void exitActivity(){
        this.finish();
    }

    private void buildShowcase() {

        Button.OnClickListener btnListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Exit the activity
                mArduino.disconnect();
                exitActivity();
            }
        };

        mShowcase = new ShowcaseView.Builder(this)
                .setShowcaseDrawer(new CalShowcaseDrawer(mShapeSize / 2))
                .setStyle(R.style.CalibrationShowcaseStyle)
                .setContentTitle(R.string.cal_title)
                .setContentText(R.string.cal_connect)
                .blockAllTouches()
                .setOnClickListener(btnListener)
                .build();
        mShowcase.setButtonText(getString(R.string.cal_button_text));
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Schedule a runnable to remove the status and navigation bar after a delay
        mEventHandler.postDelayed(mHideRunnable, UI_ANIMATION_DELAY);
    }

    private void getDevicePoints() {

        DisplayManager displayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        Display myDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        Point maxSize = new Point();
        myDisplay.getRealSize(maxSize);

        // Get 10% of each axis, as our touch points will be within those ranges
        int xOffset = Math.round(0.1f * maxSize.x);
        int yOffset = Math.round(0.1f * maxSize.y);

        // Points are zero indexed, so we always subtract 1 pixel
        mDevicePoints[0] = new Point(((maxSize.x - xOffset) - 1),
                ((maxSize.y / 2) - 1));
        mDevicePoints[1] = new Point(((maxSize.x / 2) - 1),
                ((maxSize.y - yOffset) - 1));
        mDevicePoints[2] = new Point((xOffset - 1), (yOffset - 1));

        // Shapesize is 10% of the screenwidth or height, whichever is the smallest
        mShapeSize = maxSize.x / 10;
        if (maxSize.y < mShapeSize / 10) {
            mShapeSize = maxSize.y / 10;
        }

        // Dead center of the screen
        mCenterPoint = new Point((maxSize.x / 2 - 1), (maxSize.y / 2 - 1));

    }

    /**
     * This is identical to the MaterialShowcaseDrawer located in the library,
     * however it accepts a custom radius.  I need to be able to set the radius dynamically
     * as it is always a percentage of the screen.  DP doesn't do that.
     */
    private class CalShowcaseDrawer implements ShowcaseDrawer {

        private final float radius;
        private final Paint basicPaint;
        private final Paint eraserPaint;
        private int backgroundColor;

        public CalShowcaseDrawer(int radius) {
            this.radius = radius;
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


    }

}
