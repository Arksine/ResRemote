package com.arksine.resremote;

import android.content.Context;
import android.graphics.Canvas;
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

	private boolean drawablesSet = false;

	public CalibrationView(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	public void setDrawables(Point P1, Point P2, Point P3, int shapeSize) {

		int shapeHalf = shapeSize / 2;
		Path cross =  new Path();
		cross.moveTo(shapeSize, shapeSize);
		cross.lineTo(shapeSize * 2, shapeSize);
		cross.moveTo(shapeSize + shapeHalf, shapeSize - shapeHalf);
		cross.lineTo(shapeSize + shapeHalf, shapeSize + shapeHalf);

		point1 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point1.setBounds(P1.x - shapeHalf, P1.y - shapeHalf, P1.x + shapeHalf,
				P1.y + shapeHalf);

		point2 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point2.setBounds(P2.x - shapeHalf, P2.y - shapeHalf, P2.x + shapeHalf,
				P2.y + shapeHalf);

		point3 = new ShapeDrawable(new PathShape(cross, shapeSize, shapeSize));
		point3.setBounds(P3.x - shapeHalf, P3.y - shapeHalf, P3.x + shapeHalf,
				P3.y + shapeHalf);

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
