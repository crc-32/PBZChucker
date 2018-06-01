package org.crc32.pbzchucker;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static java.lang.Thread.sleep;

public class PebbleProtocolHelper {
    private static final String[] hwRevisions = {
            // Emulator
            "silk_bb2", "robert_bb", "silk_bb",
            "spalding_bb2", "snowy_bb2", "snowy_bb",
            "bb2", "bb",
            "unknown",
            // Pebble Classic Series
            "ev1", "ev2", "ev2_3", "ev2_4", "v1_5", "v2_0",
            // Pebble Time Series
            "snowy_evt2", "snowy_dvt", "spalding_dvt", "snowy_s3", "spalding",
            // Pebble 2 Series
            "silk_evt", "robert_evt", "silk"
    };
    private InputStream inputStream;
    private OutputStream outputStream;

    PebbleProtocolHelper(InputStream pInputStream, OutputStream pOutputStream) {
        inputStream = pInputStream;
        outputStream = pOutputStream;
    }

    private byte[] encodePhoneVersion(byte os) {
        ByteBuffer buf = ByteBuffer.allocate(29);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) 25);
        buf.putShort((short) 17);
        buf.put((byte) 0x01);
        buf.putInt(-1);
        buf.putInt(0);
        buf.putInt(os);
        buf.put((byte) 2);
        buf.put((byte) 4);
        buf.put((byte) 1);
        buf.put((byte) 1);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0x00000000000029af);
        return buf.array();
    }

    private String getStringFromBuf(ByteBuffer buf, int length) {
        byte[] b = new byte[length];
        buf.get(b, 0, b.length);
        return new String(b).trim();
    }

    public HandleResponse handlePacket(byte[] buffer) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(buffer);
        buf.order(ByteOrder.BIG_ENDIAN);
        short length = buf.getShort();
        short endpoint = buf.getShort();
        byte cmd;

        switch (endpoint) {
            default:
                cmd = buf.get();
                Log.d("Blue2", String.valueOf(cmd) + ' ' + String.valueOf(endpoint) + ' ' + String.valueOf(length));
                return new HandleResponse(null, cmd, endpoint, buffer, false);
            case 17:
                cmd = buf.get();
                byte[] phoneVer = encodePhoneVersion((byte) 2);
                sendRawMessage(phoneVer, outputStream);
                return new HandleResponse(null, cmd, endpoint, buffer, true);
            case 16:
                cmd = buf.get();
                buf.getInt();
                String fwVer;
                String hwRevString = null;
                fwVer = getStringFromBuf(buf, 32);
                Log.i("Blue", "Pebble firmware detected as " + (fwVer));
                byte[] tmp = new byte[9];
                buf.get(tmp, 0, 9);
                int hwRev = buf.get() + 8;
                if (hwRev >= 0 && hwRev < hwRevisions.length) {
                    hwRevString = hwRevisions[hwRev];
                }
                if (hwRevString != null) {
                    Log.i("Blue", "Pebble fw HWREV: " + hwRevString);
                } else {
                    Log.e("Blue", "HWREV Invalid: " + hwRev);
                }
                String[] interpreted = {hwRevString, fwVer, parseForDevice(hwRevString)};
                return new HandleResponse(interpreted, cmd, endpoint, buffer, true);
        }
    }

    public void sendMessage(short endpoint, byte cmd) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) 1);
        buf.putShort(endpoint);
        buf.put(cmd);
        Log.d("Blue", "Sending CMD");
        byte[] message = buf.array();
        outputStream.write(message);
        outputStream.flush();
        try {
            sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    String parseForDevice(String hwrev) {
        if (hwrev == null) {
            return "UNKNOWN";
        }
        if (hwrev.startsWith("snowy")) {
            return "basalt";
        } else if (hwrev.startsWith("spalding")) {
            return "chalk";
        } else if (hwrev.startsWith("silk")) {
            return "diorite";
        } else if (hwrev.startsWith("robert")) {
            return "emery";
        } else {
            return "aplite";
        }
    }

    private void sendRawMessage(byte[] rawMessage, OutputStream pOutputStream) throws IOException {
        pOutputStream.write(rawMessage);
    }

    class HandleResponse {
        String[] interpretedData;
        long triggerCmd;
        long triggerEndpoint;
        byte[] rawData;
        boolean handlingComplete;

        HandleResponse(String[] mInterpretedData, long mTriggerCmd, long mTriggerEndpoint, byte[] mRawData, boolean mHandlingComplete) {
            interpretedData = mInterpretedData;
            triggerCmd = mTriggerCmd;
            triggerEndpoint = mTriggerEndpoint;
            rawData = mRawData;
            handlingComplete = mHandlingComplete;
        }
    }
}
