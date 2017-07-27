package com.e_regular_games.arduator;

import android.content.Context;
import android.content.SharedPreferences;

import com.e_regular_games.arduator.arduino.ArduinoComm;

/**
 * Created by SRE on 5/29/2017.
 */

public class ActivityConfig {
    private SharedPreferences preferences;
    private ArduinoComm arduino;

    private static final String fileKey ="com.e_regular_games.arduator.PREFERENCES";
    private static final String keyFavoriteBtDeviceAddr = "FAV_BT_DEV_ADDR";
    private static final String keyFavoriteBtDeviceName = "FAV_BT_DEV_NAME";
    private static final String keyBtMode = "BT_MODE";
    private static final String keyBtServiceUuid = "BT_SERV_UUID";
    private static final String keyBtPinCode = "BT_PIN_CODE";

    public ActivityConfig(Context context) {
        preferences = context.getSharedPreferences(fileKey, Context.MODE_PRIVATE);
    }

    public void setFavoriteBtDevice(String name, String address) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(keyFavoriteBtDeviceName, name);
        edit.putString(keyFavoriteBtDeviceAddr, address);
        edit.commit();
    }

    public String getFavoriteBtDeviceName() {
        return preferences.getString(keyFavoriteBtDeviceName, null);
    }

    // MAC address of bluetooth device
    public String getFavoriteBtDeviceAddr() {
        return preferences.getString(keyFavoriteBtDeviceAddr, null);
    }

    public void setBtMode(String mode) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(keyBtMode, mode);
        edit.commit();
    }

    public String getBtMode() {
        return preferences.getString(keyBtMode, "2.0");
    }

    // should be 4 characters long
    public void setBtServiceUuid(String uuid) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(keyBtServiceUuid, uuid);
        edit.commit();
    }

    public String getBtServiceUuid() {
        return preferences.getString(keyBtServiceUuid, "FFE0");
    }

    // should be 4 characters long
    public void setBtPinCode(String pin) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(keyBtPinCode, pin);
        edit.commit();
    }

    public String getBtPinCode() {
        return preferences.getString(keyBtPinCode, "1234");
    }


    public void setActiveStation(ArduinoComm arduino) {
        this.arduino = arduino;
    }

    public ArduinoComm getActiveStation() {
        return arduino;
    }
}
