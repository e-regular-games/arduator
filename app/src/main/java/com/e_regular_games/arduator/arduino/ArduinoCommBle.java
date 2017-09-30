package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author S. Ryan Edgar
 * Communicate with a Bluetooth 4.0 Low-Energy device. You must set the service id before
 * attempting to connect to the device.
 */
public class ArduinoCommBle extends ArduinoComm {
    
    public ArduinoCommBle(Activity app, BluetoothDevice device) {
        super(app, device);
    }

    /**
     * @param uuid the last 4 hex characters of the UUID representing the Serial port service.
     */
    public void setServiceId(String uuid) {
        serviceId = uuid;
    }

    @Override
    public void connect() {
        if (!connected) {
            if (serviceId == null) {
                onError(ErrorCode.ServiceIdRequired);
                return;
            }

            if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                deleteBondInformation();
                if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                    onError(ErrorCode.RemovePairing);
                }
            }

            btGatt = device.connectGatt(app, false, btgCallback);
            onStatus(StatusCode.Connecting);

            // will be canceled if connection is successful or explicitly fails.
            taskConnectTimeout = new TimerTask() {
                @Override
                public void run() {
                    onError(ErrorCode.Connect);
                    btGatt.disconnect();
                }
            };
            timer.schedule(taskConnectTimeout, 10000);
        }
    }

    @Override
    public void disconnect() {
        if (connected) {
            taskConnectTimeout.cancel();
            pendingWrite = false;
            toSend.clear();
            onStatus(StatusCode.Disconnecting);
            btGatt.disconnect();
        }
    }

    @Override
    public void send(int packet[]) {
        for (int i = 0; i < packet.length; i += 1) {
            toSend.add(packet[i]);
        }

        if (!pendingWrite) {
            pendingWrite = true;
            sendNextChunk();
        }
    }

    private BluetoothGatt btGatt;
    private ArrayList<Integer> toSend = new ArrayList<>();
    private boolean pendingWrite = false;

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

                case MessageConstants.ACTION:
                    doAction((ActionCode) message.obj);
                    break;

                case MessageConstants.READ:
                    byte[] content = (byte[]) message.obj;
                    onContent(content.length, content);
                    break;
            }

            return false;
        }
    });
    private BluetoothGattCallback btgCallback = new ArduinoGattCallback(messenger);

    private enum ActionCode {InitSerialService, WriteNext}

    private String serviceId;
    private BluetoothGattCharacteristic charSerial;
    private boolean connected = false;

    private Timer timer = new Timer();
    private TimerTask taskConnectTimeout;
    
    private interface MessageConstants {
        int READ = 1;
        int ERROR = 2;
        int STATUS = 4;
        int ACTION = 8;
    }

    private void doAction(ActionCode action) {
        switch (action) {
            case InitSerialService:
                initService();
                break;
            case WriteNext:
                sendNextChunk();
                break;
        }
    }

    /**
     * For BLE we can only send 20 bytes at a time.
     * Convert the integers from toSend and turn them into bytes.
     * Ensure btGatt is valid, because sendNextChunk can be called after a disconnect.
     */
    private void sendNextChunk() {
        if (toSend.size() > 0 && btGatt != null) {
            int chunkSize = toSend.size() > 20 ? 20 : toSend.size();
            byte chunk[] = new byte[chunkSize];
            for (int i = 0; i < chunkSize; i += 1) {
                chunk[i] = toSend.remove(0).byteValue();
            }

            charSerial.setValue(chunk);
            btGatt.writeCharacteristic(charSerial);
        } else {
            pendingWrite = false;
        }
    }

    private void initService() {
        List<BluetoothGattService> list = btGatt.getServices();
        BluetoothGattService service = null;
        for (BluetoothGattService s : list) {
            if (s.getUuid().toString().substring(4, 8).equalsIgnoreCase(serviceId)) {
                service = s;
                break;
            }
        }

        if (service == null) {
            onError(ErrorCode.IO);
            taskConnectTimeout.cancel();
            return;
        }

        BluetoothGattCharacteristic characteristic = null;
        List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
        for (BluetoothGattCharacteristic c : chars) {
            int props = c.getProperties();
            int desiredProps = BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_NOTIFY;
            if ((props & desiredProps) == desiredProps) {
                characteristic = c;
                break;
            }
        }

        if (characteristic == null) {
            onError(ErrorCode.IO);
            taskConnectTimeout.cancel();
            return;
        }
        charSerial = characteristic;

        BluetoothGattDescriptor clientConfig = null;
        List<BluetoothGattDescriptor> descs = charSerial.getDescriptors();
        for (BluetoothGattDescriptor d : descs) {
            if (d.getUuid().toString().substring(4, 8).equalsIgnoreCase("2902")) {
                clientConfig = d;
                break;
            }
        }

        if (clientConfig == null) {
            onError(ErrorCode.IO);
            taskConnectTimeout.cancel();
            return;
        }

        btGatt.setCharacteristicNotification(charSerial, true);

        clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        btGatt.writeDescriptor(clientConfig);
    }

    private class ArduinoGattCallback extends BluetoothGattCallback {

        private Handler messenger;

        public ArduinoGattCallback(Handler handler) {
            messenger = handler;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                btGatt.close();
                btGatt = null;
                charSerial = null;
                messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Disconnected).sendToTarget();
            } else {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Connect).sendToTarget();
                taskConnectTimeout.cancel();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                messenger.obtainMessage(MessageConstants.ACTION, ActionCode.InitSerialService).sendToTarget();
            } else {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Services).sendToTarget();
                taskConnectTimeout.cancel();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (characteristic.equals(charSerial) && status == BluetoothGatt.GATT_SUCCESS) {
                messenger.obtainMessage(MessageConstants.ACTION, ActionCode.WriteNext).sendToTarget();
            } else {
                messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Send).sendToTarget();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            if (characteristic.equals(charSerial)) {
                messenger.obtainMessage(MessageConstants.READ, characteristic.getValue()).sendToTarget();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            if (descriptor.getUuid().toString().substring(4, 8).equalsIgnoreCase("2902")) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    taskConnectTimeout.cancel();
                    messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Connected).sendToTarget();
                } else {
                    messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.IO).sendToTarget();
                    taskConnectTimeout.cancel();
                }
            }
        }
    }
}
