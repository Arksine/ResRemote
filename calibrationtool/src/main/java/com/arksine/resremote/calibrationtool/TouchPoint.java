package com.arksine.resremote.calibrationtool;

/**
 * Represents a coordinate of a touchscreen. Z = pressure;
 */
public class TouchPoint {
    private final int x;
    private final int y;
    private final int z;

    public TouchPoint(int x, int y, int z) {

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {return x;}
    public int getY() {return y;}
    public int getZ() {return z;}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TouchPoint)) return false;
        TouchPoint coord = (TouchPoint)o;

        return (this.x == coord.getX() && this.y == coord.getY()
                && this.z == coord.getZ());
    }
}
