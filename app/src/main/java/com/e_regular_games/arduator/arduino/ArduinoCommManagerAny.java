package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;

/**
 * Created by SRE on 7/3/2017.
 */

public class ArduinoCommManagerAny extends ArduinoCommManager {
    public ArduinoCommManagerAny(Activity parent, String mode) {
        super(parent);
        this.parent = parent;

        setMode(mode);
    }

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
    public void createStation(BluetoothDevice device) {
        any.createStation(device);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        any.onRequestPermissionResult(requestCode, permissions, grantResults);
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
