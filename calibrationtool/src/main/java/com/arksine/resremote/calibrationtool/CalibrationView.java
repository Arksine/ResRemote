package com.arksine.resremote.calibrationtool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.util.AttributeSet;
import android.view.View;

/**
 * A View that draws calibration points on a surface
 */
public class CalibrationView extends View {

	private ShapeDrawable pointDrawable;
	private Paint mPaint;
	private boolean drawableVisible = false;

	public CalibrationView(Context context, AttributeSet attrs) {
		super(context, attrs);

		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setColor(Color.BLACK);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(4);
		mPaint.setStrokeCap(Paint.Cap.BUTT);
		mPaint.setStrokeJoin(Paint.Join.BEVEL);

	}

	public void showPoint(Point point, int shapeSize) {

		int shapeHalf = shapeSize / 2;
		Path cross =  new Path();
		cross.moveTo(0, shapeHalf);
		cross.lineTo(shapeSize, shapeHalf);
		cross.close();
		cross.moveTo(shapeHalf, 0);
		cross.lineTo(shapeHalf, shapeSize);
		cross.close();

		pointDrawable = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		pointDrawable.getPaint().set(this.mPaint);
		pointDrawable.setBounds(point.x - shapeHalf, point.y - shapeHalf, point.x + shapeHalf,
				point.y + shapeHalf);


		drawableVisible = true;

	}

	public void hidePoint() {
		drawableVisible = false;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (drawableVisible) {
			pointDrawable.draw(canvas);
		}
	}
}
