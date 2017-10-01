package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;

import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;

/**
 * A class used to search for Bluetooth devices. Once found a ArduinoComm object can be
 * created from the Bluetooth device. This classes uses the ArduinoCommManager.ManagerEvent API
 * to indicate when events happen.
 *
 * This class is responsible for enabling Bluetooth and requesting appropriate permissions.
 *
 * ** IMPORTANT **
 * Your Activity must call onRequestPermissionResult and onActivityResult on ArduinoCommManager
 * when those functions are called on the Activity.
 */
public abstract class ArduinoCommManager {

    public ArduinoCommManager(Activity parent) {
        app = parent;
        mBt = BluetoothAdapter.getDefaultAdapter();
    }

    public enum BluetoothStatus {Enabled, Disabled, Searching, Error}

    public static class ManagerEvent {
        public void onFind(BluetoothDevice device, boolean saved) {
        }

        public void onStatusChange(BluetoothStatus state) {
        }

        public void onCreate(ArduinoComm arduino) {
        }
    }

    public abstract void find();

    public abstract void cancelFind();

    public abstract void createArduinoComm(final BluetoothDevice device);

    public abstract void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]);

    // must be called from the parent activity in the corresponding similarly name function.
    public void onActivityResult(int requestCode, int responseCode) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (responseCode == RESULT_OK) {
                btAvailable = true;
                onStatusChange(BluetoothStatus.Enabled);

                ArrayList<AfterEnable> copy = new ArrayList<>(afterEnable);
                afterEnable.clear();
                for (int i = 0; i < copy.size(); i += 1) {
                    copy.get(i).after();
                }
            } else {
                onStatusChange(BluetoothStatus.Error);
            }
        }
    }

    public void addOnManagerEvent(ArduinoCommManagerBle.ManagerEvent onEvent) {
        if (onEvent != null) {
            onEvents.add(onEvent);
        }
    }

    public void removeOnManagerEvent(ManagerEvent onEvent) {
        onEvents.remove(onEvent);
    }

    protected Activity app;
    protected BluetoothAdapter mBt;
    protected boolean btAvailable;
    private ArrayList<ManagerEvent> onEvents = new ArrayList<>();
    private static final int REQUEST_ENABLE_BT = 0x123;

    protected interface AfterEnable {
        void after();
    }

    private ArrayList<AfterEnable> afterEnable = new ArrayList<>();

    protected boolean enable(AfterEnable after) {
        if (afterEnable.size() > 0) {
            afterEnable.add(after);
            return false;
        }

        if (!mBt.isEnabled()) {
            afterEnable.add(after);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            app.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        if (!btAvailable) {
            onStatusChange(BluetoothStatus.Enabled);
        }

        btAvailable = true;
        return true;
    }

    protected void onStatusChange(final BluetoothStatus status) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (ManagerEvent e : onEvents) {
                    e.onStatusChange(status);
                }
            }
        });
    }

    protected void onFind(final BluetoothDevice device) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (ManagerEvent e : onEvents) {
                    e.onFind(device, false);
                }
            }
        });
    }

    protected void onCreateStation(final ArduinoComm arduino) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (ManagerEvent e : onEvents) {
                    e.onCreate(arduino);
                }
            }
        });
    }
};