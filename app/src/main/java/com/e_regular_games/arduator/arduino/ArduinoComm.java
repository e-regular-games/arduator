package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * @author S. Ryan Edgar
 * A class to communicate with Arduino devices over bluetooth. Supports connect, send, recv,
 * disconnect. It should handle the pairing process as well without prompting the user. The API
 * for recving events is ArduinoComm.EventHandler. Multiple EventHandlers can be added to a single
 * ArduinoComm instance.
 *
 * Although any bluetooth devide implementing the serial port protocal will work, it need not only
 * be Arduino.
 */
public abstract class ArduinoComm {
    public enum ErrorCode {Connect, IO, Send, Receive, Services, RemovePairing, PinRequired, ServiceIdRequired}
    public enum StatusCode {Connecting, Connected, Disconnected, Disconnecting}

    public ArduinoComm(Activity parent, BluetoothDevice device) {
        app = parent;
        this.device = device;
    }

    public static class EventHandler {
        public void onError(ArduinoComm self, ErrorCode code) {}
        public void onStatus(ArduinoComm self, StatusCode code) {}
        public void onContent(ArduinoComm self, int length, byte[] content) {}
    }

    public String getName() {
        return device.getName() != null ? device.getName() : device.getAddress();
    }

    public void addEventHandler(EventHandler handler) {
        if (handler != null) {
            onEvents.add(handler);
        }
    }

    public void removeEventHandler(EventHandler handler) {
        onEvents.remove(handler);
    }

    public abstract void connect();
    public abstract void disconnect();

    /**
     * @param packet data packet, only the lowest 8 bits of each integer will be sent. Integers are
     *               used to avoid issues with negative numbers.
     */
    public abstract void send(int[] packet);

    protected Activity app;
    protected BluetoothDevice device;
    private ArrayList<EventHandler> onEvents = new ArrayList<>();

    protected void onStatus(final ArduinoComm.StatusCode stat) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (EventHandler e : onEvents) {
                    e.onStatus(ArduinoComm.this, stat);
                }
            }
        });
    }

    protected void onError(final ArduinoComm.ErrorCode err) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (EventHandler e : onEvents) {
                    e.onError(ArduinoComm.this, err);
                }
            }
        });
    }

    protected void onContent(final int length, final byte[] content) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (EventHandler e : onEvents) {
                    e.onContent(ArduinoComm.this, length, content);
                }
            }
        });
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
}
