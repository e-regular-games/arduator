package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ArduinoCommBt extends ArduinoComm {

    public ArduinoCommBt(Activity app, BluetoothDevice device) {
        super(app, device);
        connThread = new ConnectionThread(device, messenger);
    }

    public void connect() {
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

    public void disconnect() {
        if (connected) {
            onStatus(StatusCode.Disconnecting);
            connThread.cancel();
        }
    }

    public void setPinCode(String pin) {
        pinCode = pin;
    }

    private ConnectionThread connThread;
    private ArrayList<Byte> toParse = new ArrayList<>();
    private Map<Integer, byte[]> toAck = new HashMap<>();
    private boolean messageAtHead = false;
    private boolean connected = false;
    private Timer timer = new Timer();
    private String pinCode;

    private interface MessageConstants {
        int READ = 1;
        int ERROR = 2;
        int STATUS = 4;
        int ACTION = 8;
    }

    protected void recvAck(int counter) {
        toAck.remove(counter);
    }

    private class AckCheck extends TimerTask {

        private int id;
        private Handler messenger;

        public AckCheck(int id, Handler handler) {
            this.id = id;
            messenger = handler;
        }

        @Override
        public void run() {
            if (toAck.containsKey(id)) {
                messenger.obtainMessage(MessageConstants.STATUS, StatusCode.UnstableConnection).sendToTarget();
            }
        }
    }

    protected void send(int id, byte[] packet) {
        toAck.put(id, packet);
        timer.schedule(new AckCheck(id, messenger), 500);

        connThread.write(packet);
    }

    private Handler messenger = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MessageConstants.ERROR:
                    onError((ErrorCode) message.obj);
                    break;

                case MessageConstants.STATUS:
                    onStatus((StatusCode) message.obj);
                    break;

                case MessageConstants.READ:
                    byte bytes[] = (byte[]) message.obj;
                    for (int i = 0; i < message.arg1 && i < bytes.length; i += 1) {
                        toParse.add(bytes[i]);
                    }

                    messageAtHead = parsePending(toParse, messageAtHead);
                    break;
            }

            return false;
        }
    });

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

            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = recvStream.read(buffer);
                    // Send the obtained bytes to the UI activity.
                    messenger.obtainMessage(MessageConstants.READ, numBytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    if (!closed) {
                        messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Receive).sendToTarget();
                    }
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                sendStream.write(bytes);
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
