package com.undatech.opaque.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RdpKeyboardMapperTest {
    @Test
    public void capsLockPreservesItsPhysicalDownAndUp() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(true, false);
        RecordingListener listener = new RecordingListener();
        mapper.reset(listener);

        mapper.processVirtualKeyAction(RdpKeyboardMapper.VK_CAPITAL, true, false);
        mapper.processVirtualKeyAction(RdpKeyboardMapper.VK_CAPITAL, false, false);

        assertEquals(Arrays.asList("20:true:false", "20:false:false"), listener.events);
    }

    @Test
    public void physicalMappedKeyUsesVirtualKeyWhenUnicodeIsPreferred() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(true, false);

        assertFalse(mapper.shouldSendUnicode(
                RdpKeyboardMapper.VK_KEY_A, 'A', false, true));
        assertTrue(mapper.shouldSendUnicode(
                RdpKeyboardMapper.VK_KEY_A, 'A', false, false));
        assertTrue(mapper.shouldSendUnicode(0, 'é', false, true));
    }

    @Test
    public void unicodeKeyUpUsesTheCodeSentOnKeyDown() {
        RdpKeyboardMapper mapper = new RdpKeyboardMapper(true, false);

        mapper.recordUnicodeKeyDown(29, 'A', false);
        mapper.recordUnicodeKeyDown(29, 'a', true);

        assertEquals(Integer.valueOf('A'), mapper.consumeUnicodeKeyDown(29));
        assertNull(mapper.consumeUnicodeKeyDown(29));
    }

    private static class RecordingListener implements RdpKeyboardMapper.KeyProcessingListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void processVirtualKey(int virtualKeyCode, boolean down) {
            processVirtualKey(virtualKeyCode, down, false);
        }

        @Override
        public void processVirtualKey(int virtualKeyCode, boolean down, boolean repeat) {
            events.add(virtualKeyCode + ":" + down + ":" + repeat);
        }

        @Override
        public void processUnicodeKey(int unicodeKey, boolean down, boolean suppressMetaState) {}

        @Override
        public void switchKeyboard(int keyboardType) {}

        @Override
        public void modifiersChanged() {}
    }
}
