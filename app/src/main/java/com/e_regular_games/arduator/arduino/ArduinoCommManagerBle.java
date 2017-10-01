package com.e_regular_games.arduator.arduino;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static android.app.Activity.RESULT_OK;

/**
 * @author S. Ryan Edgar
 *         A derived class of ArduinoCommManager specifically for finding and creatng ArduinoCommBle
 *         devices, ie Bluetooth 4.0 LE devices.
 */
public class ArduinoCommManagerBle extends ArduinoCommManager {

    public ArduinoCommManagerBle(Activity parent) {
        super(parent);
    }

    public void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]) {
        if (requestCode == REQUEST_ENABLE_FIND) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (enableFind()) {
                    find();
                }
            } else {
                onStatusChange(BluetoothStatus.Error);
            }
        }
    }

    public void find() {
        AfterEnable afterEnableFind = new AfterEnable() {
            @Override
            public void after() {
                find();
            }
        };

        if (!enable(afterEnableFind)) {
            return;
        }

        if (finding || !enableFind()) {
            return;
        }
        onStatusChange(BluetoothStatus.Searching);
        finding = true;

        stopFindTask = new TimerTask() {
            @Override
            public void run() {
                mBt.stopLeScan(scanLeDevices);
                finding = false;
                stopFindTask = null;
                onStatusChange(BluetoothStatus.Enabled);
            }
        };

        // stop find after 2 minutes.
        stopFindTimer = new Timer();
        stopFindTimer.schedule(stopFindTask, 120 * 1000);

        mBt.startLeScan(scanLeDevices);
    }

    public void cancelFind() {
        if (finding && stopFindTask != null) {
            stopFindTimer.cancel();
            stopFindTimer.purge();
            stopFindTask.run();
        }
    }

    public void addOnManagerEvent(ManagerEvent onEvent) {
        if (onEvent != null) {
            super.addOnManagerEvent(onEvent);

            if (finding) {
                onEvent.onStatusChange(BluetoothStatus.Searching);
            } else if (btAvailable || mBt.isEnabled()) {
                onEvent.onStatusChange(BluetoothStatus.Enabled);
            } else {
                onEvent.onStatusChange(BluetoothStatus.Disabled);
            }
        }
    }

    @Override
    public void createArduinoComm(final BluetoothDevice device) {
        AfterEnable afterEnableCreate = new AfterEnable() {
            @Override
            public void after() {
                ArduinoComm station = new ArduinoCommBle(app, device);
                onCreateStation(station);
            }
        };

        if (!enable(afterEnableCreate)) {
            return;
        }

        if (finding) {
            onStatusChange(BluetoothStatus.Enabled);
            cancelFind();
        }

        ArduinoComm station = new ArduinoCommBle(app, device);
        onCreateStation(station);
    }

    private boolean finding = false;
    private Timer stopFindTimer;
    private TimerTask stopFindTask;

    private static final int REQUEST_ENABLE_FIND = 0x124;
    private static final int REQUEST_ENABLE_LOCATION = 0x125;

    private BluetoothAdapter.LeScanCallback scanLeDevices = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int i, byte[] bytes) {
            if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                onFind(device);
            }
        }
    };

    private boolean enableFind() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }

        final LocationManager manager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(app);
            builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            app.startActivityForResult(intent, REQUEST_ENABLE_LOCATION);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            onStatusChange(BluetoothStatus.Error);
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
            return false;
        } else if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(app, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_FIND);
            return false;
        }

        return true;
    }

    // must be called from the parent activity in the corresponding similarly name function.
    public void onActivityResult(int requestCode, int responseCode) {
        super.onActivityResult(requestCode, responseCode);

        if (requestCode == REQUEST_ENABLE_LOCATION) {
            if (enableFind()) {
                find();
            }
        }
    }
}
