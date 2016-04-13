package com.arksine.resremote.calibrationtool;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Context;
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

import com.github.amlcurran.showcaseview.ShowcaseDrawer;
import com.github.amlcurran.showcaseview.ShowcaseView;

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
    private volatile boolean showcaseReady = false;

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
            showcaseReady = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calibrate_touch);
        mCalView = (CalibrationView) findViewById(R.id.CalView);

        getDevicePoints();
        hide();

    }

    // TODO: need a function or broadcast receiver that moves to the next point when ready

    private void exitActivity(){
        this.finish();
    }

    private void buildShowcase() {
        // TODO:  need to create a button listener that exits the app.  Also the showcase
        //        doesn't initially show on the whole screen, likely because there is a delay when
        //        hiding the bars.  Need to fix this.
        mShowcase = new ShowcaseView.Builder(this)
                .setShowcaseDrawer(new CalShowcaseDrawer(mShapeSize / 2))
                .setStyle(R.style.CalibrationShowcaseStyle)
                .setContentTitle(R.string.cal_title)
                .setContentText(R.string.cal_text)
                .blockAllTouches()
                .build();
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
        mShapeSize = maxSize.x;
        if (maxSize.y < mShapeSize) {
            mShapeSize = maxSize.y;
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
