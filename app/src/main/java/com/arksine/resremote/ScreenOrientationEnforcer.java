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

	public ScreenOrientationEnforcer(Context context) {
		windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		view = new View(context);
	}

	public void start() {
		WindowManager.LayoutParams layout = generateLayout();
		windows.addView(view, layout);
		view.setVisibility(View.VISIBLE);
	}

	public void stop() {
		windows.removeView(view);
	}

	private WindowManager.LayoutParams generateLayout() {
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();

		//So we don't need a permission or activity
		layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

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
		layoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

		return layoutParams;
	}
}
