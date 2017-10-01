package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;

/**
 * A derived class of ArduinoCommManager which represents any Bluetooth mode (ie 2.0 or 4.0 LE).
 * This provides a way to switch managers while allow all other parts of the application to hold
 * on to the same object, ie an instance of ArduinoCommManagerAny.
 */
public class ArduinoCommManagerAny extends ArduinoCommManager {

    /**
     * @param parent The parent Activity.
     * @param mode Either "2.0" or "4.0 LE".
     */
    public ArduinoCommManagerAny(Activity parent, String mode) {
        super(parent);
        this.parent = parent;

        setMode(mode);
    }

    /**
     * @param mode Either "2.0" or "4.0 LE".
     */
    public void setMode(String mode) {
        if (any != null) {
            any.cancelFind();
        }

        if (mode.equals("2.0")) {
            any = new ArduinoCommManagerBt(parent);
        } else if (mode.equals("4.0 LE")) {
            any = new ArduinoCommManagerBle(parent);
        } else {
            throw new Error("Invalid mode: " + mode);
        }

        for (ManagerEvent onEvent : onEvents) {
            any.addOnManagerEvent(onEvent);
        }
    }

    @Override
    public void find() {
        any.find();
    }

    @Override
    public void cancelFind() {
        any.cancelFind();
    }

    @Override
    public void createArduinoComm(BluetoothDevice device) {
        any.createArduinoComm(device);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        any.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int responseCode) {
        any.onActivityResult(requestCode, responseCode);
    }

    @Override
    public void addOnManagerEvent(ManagerEvent onEvent) {
        if (onEvent != null) {
            onEvents.add(onEvent);
        }

        any.addOnManagerEvent(onEvent);
    }

    @Override
    public void removeOnManagerEvent(ManagerEvent onEvent) {
        onEvents.remove(onEvent);
        any.removeOnManagerEvent(onEvent);
    }

    private ArduinoCommManager any;
    private Activity parent;
    private ArrayList<ManagerEvent> onEvents = new ArrayList<>();
}
