package com.undatech.opaque;

public final class RdpMonitorLayout {
    public static final int MAX_MONITORS = 16;
    public static final int MAX_DESKTOP_DIMENSION = 32766;

    private RdpMonitorLayout() {
    }

    public static int clampCount(int count) {
        return Math.max(1, Math.min(MAX_MONITORS, count));
    }

    public static int maxMonitorWidth(int count) {
        return MAX_DESKTOP_DIMENSION / clampCount(count);
    }

    public static int monitorOriginX(int index, int monitorWidth) {
        return index * monitorWidth;
    }

    public static Region intersect(int x, int y, int width, int height,
                                   int monitorIndex, int monitorWidth) {
        int monitorX = monitorOriginX(monitorIndex, monitorWidth);
        int left = Math.max(x, monitorX);
        int right = Math.min(x + width, monitorX + monitorWidth);
        return left < right
                ? new Region(left, y, left - monitorX, y, right - left, height)
                : null;
    }

    public static final class Region {
        public final int sourceX;
        public final int sourceY;
        public final int destinationX;
        public final int destinationY;
        public final int width;
        public final int height;

        Region(int sourceX, int sourceY, int destinationX, int destinationY,
               int width, int height) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.width = width;
            this.height = height;
        }
    }
}
