package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Created by SRE on 7/5/2017.
 */

public class ArduinoCommUpdaterBt extends ArduinoCommUpdater {
    public ArduinoCommUpdaterBt(Activity app, BluetoothDevice device, String fileName) {
        super(app, device, fileName);
        connThread = new ConnectionThread(device, messenger);
    }

    public void setPin(String pin) {
        pinCode = pin;
    }

    private String pinCode;
    private ConnectionThread connThread;
    private boolean connected = false;
    
    @Override
    protected void connect() {
        if (!connected) {
            if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                deleteBondInformation();
                if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                    onError(ErrorCode.RemovePairing);
                }
            }

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
            app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                        // Discovery has found a device. Get the BluetoothDevice
                        // object and its info from the Intent.
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        device.setPin(pinCode.getBytes());
                        try {
                            device.getClass().getMethod("cancelBondProcess", (Class[]) null).invoke(device, (Object[]) null);
                            device.getClass().getMethod("cancelPairingUserInput", boolean.class).invoke(device);
                        } catch (Exception e) {}
                        app.unregisterReceiver(this);
                    }
                }
            }, filter);

            device.setPin(pinCode.getBytes());
            connThread.start();
            onStatus(StatusCode.Connecting);
        }
    }

    @Override
    protected void disconnect() {
        if (connected) {
            onStatus(StatusCode.Disconnecting);
            connThread.cancel();
        }
    }

    @Override
    protected void send(ActionCode action, int[] packet) {
        connThread.write(packet);
    }

    private static UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private class ConnectionThread extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;
        private boolean closed = false;

        private InputStream recvStream;
        private OutputStream sendStream;
        private byte[] buffer = new byte[1024];
        private Handler messenger;

        public ConnectionThread(BluetoothDevice device, Handler messenger) {
            this.device = device;
            this.messenger = messenger;
        }

        private boolean connect() {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                closed = false;

                return true;
            } catch (IOException connE) {
                try {
                    closed = true;
                    socket.close();
                } catch (IOException closeE) {
                }
                return false;
            }
        }

        public void run() {

            messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Connecting).sendToTarget();
            if (connect()) {
                messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Connected).sendToTarget();
                connected = true;
            } else {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Connect).sendToTarget();
                return;
            }


            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                recvStream = socket.getInputStream();
                sendStream = socket.getOutputStream();
            } catch (IOException e) {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.IO).sendToTarget();
            }

            if (recvStream == null || sendStream == null) {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.IO).sendToTarget();
                return;
            }

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    doAction(ActionCode.Sync1);
                }
            }, 250);


            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = recvStream.read(buffer);
                    // Send the obtained bytes to the UI activity.
                    if (numBytes > 0) {
                        messenger.obtainMessage(MessageConstants.READ, numBytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    if (!closed) {
                        messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.IO).sendToTarget();
                    }
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(int[] bytes) {
            try {
                for (int i = 0; i < bytes.length; i += 1) {
                    sendStream.write(bytes[i]);
                }
                sendStream.flush();
            } catch (IOException e) {
                if (!closed) {
                    messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Send).sendToTarget();
                }
            }
        }

        public void cancel() {
            try {
                closed = true;
                socket.close();
                connected = false;
                messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Disconnected).sendToTarget();
            } catch (IOException closeE) {

            }
        }
    }
}
