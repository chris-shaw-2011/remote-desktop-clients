package com.undatech.opaque;

/** A monitor-local view of one shared multi-monitor RDP transport. */
public class RdpMonitorConnection extends RfbConnectable {
    private final RdpCommunicator delegate;
    private final int monitorIndex;
    private final Viewable viewable;
    private boolean closed;

    public RdpMonitorConnection(RdpCommunicator delegate, int monitorIndex, Viewable viewable) {
        super(false, delegate.getHandler(), true);
        this.delegate = delegate;
        this.monitorIndex = monitorIndex;
        this.viewable = viewable;
        remoteKeyboardState = delegate.remoteKeyboardState;
    }

    @Override public int framebufferWidth() { return delegate.getMonitorWidth(); }
    @Override public int framebufferHeight() { return delegate.framebufferHeight(); }
    @Override public String desktopName() { return delegate.desktopName(); }
    @Override public void requestUpdate(boolean incremental) { delegate.requestUpdate(incremental); }
    @Override public void requestResolution(int x, int y) { }
    @Override public void writeClientCutText(String text) { delegate.writeClientCutText(text); }
    @Override public void setIsInNormalProtocol(boolean state) { delegate.setIsInNormalProtocol(state); }
    @Override public boolean isInNormalProtocol() { return delegate.isInNormalProtocol(); }
    @Override public String getEncoding() { return delegate.getEncoding(); }
    @Override public void writePointerEvent(int x, int y, int metaState, int pointerMask, boolean relative) {
        delegate.writePointerEvent(x + RdpMonitorLayout.monitorOriginX(monitorIndex, delegate.getMonitorWidth()),
                y, metaState, pointerMask, relative);
    }
    @Override public void writeKeyEvent(int key, int metaState, boolean down) { delegate.writeKeyEvent(key, metaState, down); }
    @Override public void writeSetPixelFormat(int bitsPerPixel, int depth, boolean bigEndian,
            boolean trueColour, int redMax, int greenMax, int blueMax, int redShift, int greenShift,
            int blueShift, boolean fGreyScale) {
        delegate.writeSetPixelFormat(bitsPerPixel, depth, bigEndian, trueColour, redMax, greenMax,
                blueMax, redShift, greenShift, blueShift, fGreyScale);
    }
    @Override public void writeFramebufferUpdateRequest(int x, int y, int w, int h, boolean incremental) { }
    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            delegate.detachMonitor(monitorIndex, viewable);
        }
    }
    @Override public boolean isCertificateAccepted() { return delegate.isCertificateAccepted(); }
    @Override public void setCertificateAccepted(boolean accepted) { delegate.setCertificateAccepted(accepted); }
}
