package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by SRE on 5/28/2017.
 */

public abstract class ArduinoComm {
    public enum ErrorCode {Connect, IO, Send, Receive, EchoLength, Services, RemovePairing}
    public enum StatusCode {Connecting, Connected, Disconnected, Disconnecting, UnstableConnection, StableConnection}
    public enum RequestCode { Echo, Version, TelemetryStream }

    public ArduinoComm(Activity parent, BluetoothDevice device) {
        app = parent;
        this.device = device;
    }

    public class TelemetryData {
        public float ch1 = 0;
        public float ch2 = 1;
        public float ch3 = 1;
        public float ch4 = 1;
        public float ch5 = 1;
        public float ch6 = 1;

        public String toString() {
            return Float.toString(ch1) + ", " + Float.toString(ch2) + ", " +
                    Float.toString(ch3) + ", " + Float.toString(ch4) + ", " +
                    Float.toString(ch5) + ", " + Float.toString(ch6);
        }
    }

    public static class BaseStationEvent {
        public void onError(ArduinoComm self, ErrorCode code) {}
        public void onStatus(ArduinoComm self, StatusCode code) {}
        public void onData(ArduinoComm self, TelemetryData data) {}
        public void onContent(ArduinoComm self, RequestCode code, String content) {}
    }

    public String getName() {
        return device.getName() != null ? device.getName() : device.getAddress();
    }

    public void addEventHandler(BaseStationEvent handler) {
        if (handler != null) {
            onEvents.add(handler);
        }
    }

    public void removeEventHandler(BaseStationEvent handler) {
        onEvents.remove(handler);
    }

    public abstract void connect();
    public abstract void disconnect();

    public void request(RequestCode code) {
        switch (code) {
            case Version:
                int id = sendCounter;
                sendCounter = (sendCounter + 1) % 0xFFFF;
                byte packet[] = createPacketFixedLength(PacketCodes.VERSION_SEND, id, null);
                send(id, packet);
                break;
        }
    }

    public void request(RequestCode code, int msg) {}

    public void request(RequestCode code, String msg) {
        switch(code) {
            case Echo:
                byte bMsg[] = msg.getBytes(Charset.forName("US-ASCII"));
                if (msg.length() > 254 && bMsg.length == msg.length()) {
                    onError(ErrorCode.EchoLength);
                }

                int id = sendCounter;
                sendCounter = (sendCounter + 1) % 0xFFFF;

                byte packet[] = createPacketWithLength(PacketCodes.ECHO_SEND, id, bMsg);
                send(id, packet);
                break;
        }
    }

    protected Activity app;
    protected BluetoothDevice device;
    protected int sendCounter = 0;
    private ArrayList<BaseStationEvent> onEvents = new ArrayList<>();

    protected void onStatus(final ArduinoComm.StatusCode stat) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (BaseStationEvent e : onEvents) {
                    e.onStatus(ArduinoComm.this, stat);
                }
            }
        });
    }

    protected void onError(final ArduinoComm.ErrorCode err) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (BaseStationEvent e : onEvents) {
                    e.onError(ArduinoComm.this, err);
                }
            }
        });
    }

    protected void onContent(final ArduinoComm.RequestCode code, final String content) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (BaseStationEvent e : onEvents) {
                    e.onContent(ArduinoComm.this, code, content);
                }
            }
        });
    }

    protected void onData(final TelemetryData content) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (BaseStationEvent e : onEvents) {
                    e.onData(ArduinoComm.this, content);
                }
            }
        });
    }

    protected interface PacketCodes {
        // fixed length of 6
        byte ACK = 0x01;
        byte VERSION_SEND = 0x02;
        byte VERSION_RECV = 0x03;

        // variable length
        byte ECHO_SEND = (byte)0x81;
        byte ECHO_RECV = (byte)0x82;
        byte TELEM_RECV = (byte)0x90;
    }

    protected byte[] createPacketWithLength(byte code, int counter, byte[] data) {
        byte packet[] = new byte[data.length + 7];
        packet[0] = '$';
        packet[1] = '>';
        packet[2] = (byte)(counter >> 8);
        packet[3] = (byte)(counter & 0xFF);
        packet[4] = code;
        packet[5] = (byte)(data.length + 1); // length is less than 255;

        for (int i = 0; i <data.length; i += 1) {
            packet[6 + i] = data[i];
        }

        packet[6 + data.length] = '<';

        return packet;
    }

    protected byte[] createPacketFixedLength(byte code, int counter, byte[] data) {
        byte packet[] = new byte[12];
        packet[0] = '$';
        packet[1] = '>';
        packet[2] = (byte)(counter >> 8);
        packet[3] = (byte)(counter & 0xFF);

        packet[4] = code;
        for (int i = 0; i < 6; i += 1) {
            if (data != null && i < data.length) {
                packet[5 + i] = data[i];
            } else {
                packet[5 + i] = 0x00;
            }
        }

        packet[11] = '<';

        return packet;
    }

    protected abstract void send(int id, byte[] packet);

    // Message Format:
    // Header: $, >, <counter_high>, <counter_low>, <code>, <?length>, ..., <
    // if code >= 0x80 next byte is length, else length is 6
    // note: length includes its own byte in the count, thus length 6 is 1 byte length, 5 data
    protected boolean parsePending(ArrayList<Byte> toParse, boolean messageAtHead) {
        while (!messageAtHead && toParse.size() > 1) {
            if (toParse.get(0) == '$' && toParse.get(1) == '>') {
                messageAtHead = true;
            } else {
                toParse.remove(0);
            }
        }

        if (messageAtHead && toParse.size() >= 6) {
            int counter = (toParse.get(2) * 0x100) + toParse.get(3);
            byte code = toParse.get(4);
            boolean hasLengthByte = code < 0;
            int length = hasLengthByte ? ((toParse.get(5) & 0xFF) - 1) : 6;
            int readFrom = hasLengthByte ? 6 : 5;

            if (toParse.size() > readFrom + length) {
                if (toParse.get(readFrom + length) != '<') {
                    toParse.remove(0);
                } else {
                    byte data[] = new byte[length];

                    for (int i = 0; i < length; toParse.remove(0), i += 1) {
                        data[i] = toParse.get(readFrom);
                    }

                    // clean-up the header and length bytes and trailing '<'
                    for (int i = 0; i < (readFrom + 1); i += 1) {
                        toParse.remove(0);
                    }

                    handlePacket(counter, code, data);
                }

                messageAtHead = false;
                parsePending(toParse, messageAtHead); // handle other messages which may be in the queue.
            }
        }

        return messageAtHead;
    }

    protected abstract void recvAck(int counter);

    protected void handlePacket(int counter, int code, byte[] data) {
        switch (code) {
            case PacketCodes.ACK:
                recvAck(counter);
                break;
            case PacketCodes.ECHO_RECV:
                onContent(RequestCode.Echo, new String(data));
                break;
            case PacketCodes.TELEM_RECV:
                onTelemetry(data);
                break;
            case PacketCodes.VERSION_RECV:
                onContent(RequestCode.Version, new String(data));
                break;
        }
    }

    // https://stackoverflow.com/questions/38055699/programmatically-pairing-with-a-ble-device-on-android-4-4
    protected void deleteBondInformation() {
        try {
            // FFS Google, just unhide the method.
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {

        }
    }

    protected void onTelemetry(byte data[]) {
        int unpacked[] = new int[6];
        for (int i = 0; i < 3; i += 1) {
            unpacked[2*i] = data[3*i] << 4;
            unpacked[2*i] += (data[3*i+1] >> 4) & 0x0F;

            unpacked[2*i+1] = (data[3*i+1] & 0x0F) << 8;
            unpacked[2*i+1] += data[3*i+2] & 0xFF;
        }

        final TelemetryData telem = new TelemetryData();
        telem.ch1 = unpacked[0] / 2000f;
        telem.ch2 = unpacked[1] / 2000f;
        telem.ch3 = unpacked[2] / 2000f;
        telem.ch4 = unpacked[3] / 2000f;
        telem.ch5 = unpacked[4] / 2000f;
        telem.ch6 = unpacked[5] / 2000f;

        onData(telem);
    }
}
