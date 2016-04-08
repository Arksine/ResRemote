package com.arksine.resremote;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;

/**
 * Forces system orientation to landscape when the service is active
 */
public class ScreenOrientationEnforcer {
	private final View view;
	private final WindowManager windows;

	boolean isEnforced;

	public ScreenOrientationEnforcer(Context context) {
		windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		view = new View(context);
		isEnforced = false;
	}

	/**
	 * Start enforcing a selected Orientation
	 * @param orientation - should be equal to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE or
	 *                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
	 */
	public void start(int orientation) {
		if (!isEnforced) {
			WindowManager.LayoutParams layout = generateLayout(orientation);
			windows.addView(view, layout);
			view.setVisibility(View.VISIBLE);
			isEnforced = true;
		}
	}

	public void stop() {
		if (isEnforced) {
			windows.removeView(view);
			isEnforced = false;
		}
	}

	private WindowManager.LayoutParams generateLayout(int orientation) {
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();

		//So we don't need a permission or activity
		layoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;

		//Just in case the window type somehow doesn't enforce this
		layoutParams.flags = layoutParams.flags |
				WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

		//Prevents breaking apps that detect overlying windows enabling
		//(eg UBank app, or enabling an accessibility service)
		layoutParams.width = 0;
		layoutParams.height = 0;

		//Try to make it completely invisible
		layoutParams.format = PixelFormat.TRANSPARENT;
		layoutParams.alpha = 0f;

		//The orientation to force
		layoutParams.screenOrientation = orientation;

		return layoutParams;
	}
}
