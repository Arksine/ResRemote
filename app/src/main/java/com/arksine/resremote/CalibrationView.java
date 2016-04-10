package com.arksine.resremote;

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

	private ShapeDrawable point1;
	private ShapeDrawable point2;
	private ShapeDrawable point3;
	private  Paint mPaint;
	private boolean drawablesSet = false;

	public CalibrationView(Context context, AttributeSet attrs) {
		super(context, attrs);

		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setColor(Color.BLACK);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(4);
		mPaint.setStrokeCap(Paint.Cap.BUTT);
		mPaint.setStrokeJoin(Paint.Join.BEVEL);

	}

	public void setDrawables(Point P1, Point P2, Point P3, int shapeSize) {

		// TODO: Paths are drawn in wrong place

		int shapeHalf = shapeSize / 2;
		Path cross =  new Path();
		cross.moveTo(0, shapeHalf);
		cross.lineTo(shapeSize, shapeHalf);
		cross.close();
		cross.moveTo(shapeHalf, 0);
		cross.lineTo(shapeHalf, shapeSize);
		cross.close();

		point1 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point1.setBounds(P1.x - shapeHalf, P1.y - shapeHalf, P1.x + shapeHalf,
				P1.y + shapeHalf);
		point1.getPaint().set(this.mPaint);

		point2 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point2.setBounds(P2.x - shapeHalf, P2.y - shapeHalf, P2.x + shapeHalf,
				P2.y + shapeHalf);
		point2.getPaint().set(this.mPaint);

		point3 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point3.setBounds(P3.x - shapeHalf, P3.y - shapeHalf, P3.x + shapeHalf,
				P3.y + shapeHalf);
		point3.getPaint().set(this.mPaint);

		drawablesSet = true;

	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (drawablesSet) {

			point1.draw(canvas);
			point2.draw(canvas);
			point3.draw(canvas);
		}
	}
}
