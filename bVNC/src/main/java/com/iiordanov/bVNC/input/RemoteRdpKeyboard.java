package com.iiordanov.bVNC.input;

import static com.undatech.opaque.util.GeneralUtils.debugLog;

import android.os.Handler;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.iiordanov.bVNC.App;
import com.undatech.opaque.InputCarriable;
import com.undatech.opaque.RdpCommunicator;
import com.undatech.opaque.Viewable;
import com.undatech.opaque.input.RdpKeyboardMapper;
import com.undatech.opaque.input.RemotePointer;

public class RemoteRdpKeyboard extends RemoteKeyboard {
    private final static String TAG = "RemoteRdpKeyboard";
    protected RdpKeyboardMapper keyboardMapper;
    protected Viewable canvas;
    protected InputCarriable remoteInput;
    private RdpCommunicator rdpcomm;

    public RemoteRdpKeyboard(
            RdpCommunicator r, Viewable v, InputCarriable i, Handler h,
            boolean debugLog, boolean preferSendingUnicode
    ) {
        super(r, v.getContext(), h, debugLog);
        rdpcomm = r;
        canvas = v;
        remoteInput = i;
        keyboardMapper = new RdpKeyboardMapper(preferSendingUnicode, debugLog);
        keyboardMapper.init(context);
        keyboardMapper.reset((RdpKeyboardMapper.KeyProcessingListener) r);
    }

    public boolean processLocalKeyEvent(int keyCode, KeyEvent evt, int additionalMetaState) {
        debugLog(App.debugLog, TAG, "processLocalKeyEvent: " + evt.toString() + " " + keyCode);
        // Drop repeated modifiers
        if (shouldDropModifierKeys(evt))
            return true;
        boolean isRepeat = evt.getRepeatCount() > 0;

        if (rdpcomm != null && rdpcomm.isInNormalProtocol()) {
            RemotePointer pointer = remoteInput.getPointer();
            boolean down = (evt.getAction() == KeyEvent.ACTION_DOWN) ||
                    (evt.getAction() == KeyEvent.ACTION_MULTIPLE);
            boolean hardwareKeyEvent = evt.getScanCode() != 0 || evt.getDeviceId() >= 0;
            int eventMetaState = additionalMetaState | convertEventMetaState(evt);
            if (hardwareKeyEvent)
                rdpcomm.remoteKeyboardState.detectHardwareMetaState(evt);

            if (keyCode == KeyEvent.KEYCODE_MENU)
                return true;                           // Ignore menu key

            if (pointer.hardwareButtonsAsMouseEvents(
                    keyCode, evt, eventMetaState | onScreenMetaState))
                return true;

            // Physical modifiers are sent by their own key events. Only soft/on-screen sources
            // need modifier synthesis around another key.
            int syntheticMetaState = onScreenMetaState | additionalMetaState;
            if (!hardwareKeyEvent) {
                syntheticMetaState |= convertEventMetaState(evt);
                evt = replaceMetaState(evt, evt.getMetaState() | syntheticMetaState);
            }
            rdpcomm.writeKeyEvent(keyCode, syntheticMetaState, down);

            if (keyCode == 0 && evt.getCharacters() != null /*KEYCODE_UNKNOWN*/) {
                String s = evt.getCharacters();
                debugLog(App.debugLog, TAG, "processLocalKeyEvent: getCharacters: " + s);

                if (s != null) {
                    int numchars = s.length();
                    for (int i = 0; i < numchars; i++) {
                        KeyEvent event = new KeyEvent(evt.getEventTime(), s.substring(i, i + 1), KeyCharacterMap.FULL, 0);
                        keyboardMapper.processAndroidKeyEvent(event, isRepeat, hardwareKeyEvent);
                    }
                }
                return true;
            } else {
                // Send the key to be processed through the KeyboardMapper.
                return keyboardMapper.processAndroidKeyEvent(evt, isRepeat, hardwareKeyEvent);
            }
        } else {
            return false;
        }
    }

    @Override
    public void releaseAllKeys() {
        clearMetaState();
        keyboardMapper.clearAllModifiers();
        keyboardMapper.clearPressedKeys();
        rdpcomm.releaseAllKeys();
    }

    public void sendMetaKey(MetaKeyBean meta) {
        RemotePointer pointer = remoteInput.getPointer();
        int x = pointer.getX();
        int y = pointer.getY();

        if (meta.isMouseClick()) {
            //Log.i("RemoteRdpKeyboard", "is a mouse click");
            int button = meta.getMouseButtons();
            switch (button) {
                case RemoteVncPointer.MOUSE_BUTTON_LEFT:
                    pointer.leftButtonDown(x, y, meta.getMetaFlags() | onScreenMetaState);
                    break;
                case RemoteVncPointer.MOUSE_BUTTON_RIGHT:
                    pointer.rightButtonDown(x, y, meta.getMetaFlags() | onScreenMetaState);
                    break;
                case RemoteVncPointer.MOUSE_BUTTON_MIDDLE:
                    pointer.middleButtonDown(x, y, meta.getMetaFlags() | onScreenMetaState);
                    break;
                case RemoteVncPointer.MOUSE_BUTTON_SCROLL_UP:
                    pointer.scrollUp(x, y, meta.getMetaFlags() | onScreenMetaState);
                    break;
                case RemoteVncPointer.MOUSE_BUTTON_SCROLL_DOWN:
                    pointer.scrollDown(x, y, meta.getMetaFlags() | onScreenMetaState);
                    break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            pointer.releaseButton(x, y, meta.getMetaFlags() | onScreenMetaState);

            //rfb.writePointerEvent(x, y, meta.getMetaFlags()|onScreenMetaState, button);
            //rfb.writePointerEvent(x, y, meta.getMetaFlags()|onScreenMetaState, 0);
        } else if (meta.equals(MetaKeyBean.keyCtrlAltDel)) {
            // TODO: I should not need to treat this specially anymore.
            int savedMetaState = onScreenMetaState;
            // Update the metastate
            rfb.writeKeyEvent(0, RemoteKeyboard.CTRL_MASK | RemoteKeyboard.ALT_MASK, false);
            keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, 112), false);
            keyboardMapper.processAndroidKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, 112), false);
            rfb.writeKeyEvent(0, savedMetaState, false);
        } else {
            sendKeySym(meta.getKeySym(), meta.getMetaFlags());
        }
    }
}
