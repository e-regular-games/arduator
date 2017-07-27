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

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

/**
 * Created by SRE on 7/5/2017.
 */

public class ArduinoCommUpdaterBle extends ArduinoCommUpdater {
    public ArduinoCommUpdaterBle(Activity app, BluetoothDevice device, String fileName) {
        super(app, device, fileName);
    }

    public void setServiceId(String uuid) {
        serviceId = uuid;
    }

    private BluetoothGatt btGatt;

    private ArrayList<Integer> toSend = new ArrayList<>();
    private boolean pendingWrite = false;

    private String serviceId;
    private BluetoothGattCharacteristic charSerial;
    private boolean connected = false;
    private TimerTask taskConnectTimeout;

    private BluetoothGattCallback btgCallback = new UpdaterGattCallback(messenger);
    
    @Override
    protected void doAction(ActionCode action) {
        if (action == ActionCode.WriteNext) {
            sendNextChunk();
        } else if (action == ActionCode.InitSerialService) {
            initService();
        } else {
            super.doAction(action);
        }
    }

    private void sendNextChunk() {
        if (toSend.size() > 0) {
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

    protected void connect() {
        if (!connected) {
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
                    if (btGatt != null) {
                        btGatt.disconnect();
                    }
                }
            };
            timer.schedule(taskConnectTimeout, 6000);
        }
    }

    protected void disconnect() {
        if (connected) {
            taskConnectTimeout.cancel();
            onStatus(StatusCode.Disconnecting);
            btGatt.disconnect();
        }
    }

    protected void send(ActionCode action, int packet[]) {
        for (int i = 0; i < packet.length; i += 1) {
            toSend.add(packet[i]);
        }

        if (!pendingWrite) {
            pendingWrite = true;
            sendNextChunk();
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

    private class UpdaterGattCallback extends BluetoothGattCallback {

        private Handler messenger;

        public UpdaterGattCallback(Handler handler) {
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
                messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Disconnected).sendToTarget();
            } else {
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

            System.out.println("CharRead: " + Integer.toHexString(status));
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
                byte bytes[] = characteristic.getValue();
                messenger.obtainMessage(MessageConstants.READ, bytes.length, -1, bytes).sendToTarget();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            if (descriptor.getUuid().toString().substring(4, 8).equalsIgnoreCase("2902")) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    taskConnectTimeout.cancel();
                    messenger.obtainMessage(MessageConstants.STATUS, StatusCode.Connected).sendToTarget();

                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            messenger.obtainMessage(MessageConstants.ACTION, ActionCode.Sync1).sendToTarget();
                        }
                    }, 250);
                } else {
                    messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.IO).sendToTarget();
                    taskConnectTimeout.cancel();
                }
            }
        }
    }

}
