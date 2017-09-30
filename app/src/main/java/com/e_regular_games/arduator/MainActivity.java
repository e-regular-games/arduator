package com.e_regular_games.arduator;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.e_regular_games.arduator.arduino.ArduinoComm;
import com.e_regular_games.arduator.arduino.ArduinoCommBle;
import com.e_regular_games.arduator.arduino.ArduinoCommBt;
import com.e_regular_games.arduator.arduino.ArduinoCommManager;
import com.e_regular_games.arduator.arduino.ArduinoCommManagerAny;
import com.e_regular_games.arduator.arduino.ArduinoCommUpdater;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityConfig config;
    private ArduinoCommManagerAny arduinoMgr;
    private Button btnSearchStart, btnSearchStop, btnUpload;
    private ImageButton btnFileLookup;
    private LinearLayout layoutStatus;
    private TextView textStatus, textService, textPin;
    private ImageView imgError;
    private ProgressBar prgBusy;
    private Spinner spnDevices;
    ArrayAdapter<BtFoundDevice> spnDevicesAdapter;
    private Spinner spnMode;
    EditText editService, editPin, editFile;
    private Uri uriFirmware;
    private BluetoothDevice device;
    private ArduinoCommUpdater updater;
    private boolean bSearching = false, bError = false;

    private void updateButtons() {
        if (device != null && uriFirmware != null && updater == null) {
            btnUpload.setEnabled(true);
        } else {
            btnUpload.setEnabled(false);
        }

        if (updater != null || bSearching) {
            btnFileLookup.setEnabled(false);
            btnSearchStart.setEnabled(false);
            btnSearchStop.setEnabled(bSearching);
        } else {
            btnSearchStop.setEnabled(false);
            btnFileLookup.setEnabled(true);
            btnSearchStart.setEnabled(true);
        }
    }

    private AdapterView.OnItemSelectedListener btDeviceChange = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            BtFoundDevice sel = (BtFoundDevice) spnDevices.getSelectedItem();
            config.setFavoriteBtDevice(sel.toString(), sel.getDevice().getAddress());
            device = sel.getDevice();
            updateButtons();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            config.setFavoriteBtDevice(null, null);
            device = null;
            updateButtons();
        }
    };

    private AdapterView.OnItemSelectedListener btModeChange = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            updateAfterBtModeChange(spnMode.getItemAtPosition(position).toString());
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            spnMode.setSelection(0);
            updateAfterBtModeChange(spnMode.getItemAtPosition(0).toString());
        }
    };

    private Map<String, Boolean> foundDevices = new HashMap<>();

    private static class BtFoundDevice {
        BluetoothDevice device;
        String sName;

        BtFoundDevice(String sName, BluetoothDevice device) {
            this.device = device;
            this.sName = sName;
        }

        public String toString() {
            return sName;
        }

        public BluetoothDevice getDevice() {
            return device;
        }
    }

    private ArduinoCommManager.ManagerEvent mgrEvent = new ArduinoCommManager.ManagerEvent() {
        public void onFind(BluetoothDevice device, boolean saved) {
            String name = device.getName() == null || device.getName().equals("") ? device.getAddress() : device.getName();

            if (!foundDevices.containsKey(name)) {
                spnDevicesAdapter.add(new BtFoundDevice(name, device));
                foundDevices.put(name, true);
            }
        }

        public void onStatusChange(ArduinoCommManager.BluetoothStatus status) {
            switch (status) {
                case Enabled:
                case Disabled:
                    layoutStatus.setVisibility(View.GONE);
                    break;
                case Searching:
                    showStatus("Searching...");
                    break;
                case Error:
                    showError("Error!");
                    BtFoundDevice sel = (BtFoundDevice) spnDevices.getSelectedItem();
                    updateButtons();
                    break;
            }
        }

        public void onCreate(ArduinoComm arduino) {
            try {
                InputStream in = MainActivity.this.getContentResolver().openInputStream(uriFirmware);
                if (spnMode.getSelectedItem().toString().equals("2.0")) {
                    ((ArduinoCommBt)arduino).setPinCode(editPin.getText().toString());
                } else {
                    ((ArduinoCommBle)arduino).setServiceId(editService.getText().toString());
                }

                updater = new ArduinoCommUpdater(MainActivity.this, arduino);
                updater.setOnStatus(new ArduinoCommUpdater.OnStatus() {
                    @Override
                    public void onError(ArduinoCommUpdater.ErrorCode code) {
                        showError(code.toString());
                        updater = null;
                        bError = true;
                        updateButtons();
                    }

                    @Override
                    public void onStatus(ArduinoCommUpdater.StatusCode progress) {
                        if (bError) {
                            return;
                        }

                        if (progress == ArduinoCommUpdater.StatusCode.Disconnected) {
                            showError("Disconnected");
                            updater = null;
                            updateButtons();
                        } else if (progress == ArduinoCommUpdater.StatusCode.Complete) {
                            showStatus("Upload Complete!");
                            prgBusy.setVisibility(View.GONE);
                            updater = null;
                            updateButtons();
                        } else {
                            showStatus(progress.toString());
                        }
                    }
                });

                updater.upload(in);
            } catch (IOException e) {
                showError("Error reading firmware.");
                updater = null;
            }
        }
    };

    private void showError(String err) {
        layoutStatus.setVisibility(View.VISIBLE);
        prgBusy.setVisibility(View.GONE);
        imgError.setVisibility(View.VISIBLE);
        textStatus.setText(err);
    }

    private void showStatus(String msg) {
        layoutStatus.setVisibility(View.VISIBLE);
        prgBusy.setVisibility(View.VISIBLE);
        imgError.setVisibility(View.GONE);
        textStatus.setText(msg);
    }


    private void updateAfterBtModeChange(String mode) {
        if (mode.equals("2.0")) {
            editPin.setVisibility(View.VISIBLE);
            textPin.setVisibility(View.VISIBLE);

            editService.setVisibility(View.GONE);
            textService.setVisibility(View.GONE);
        } else {
            editPin.setVisibility(View.GONE);
            textPin.setVisibility(View.GONE);

            editService.setVisibility(View.VISIBLE);
            textService.setVisibility(View.VISIBLE);
        }

        if (!config.getBtMode().equals(mode)) {
            config.setBtMode(mode);
            spnDevicesAdapter.clear();
            config.setFavoriteBtDevice(null, null);
            device = null;
        }
    }

    private void loadValuesFromConfig() {
        if (config.getBtMode().equals("2.0")) {
            spnMode.setSelection(0);
        } else {
            spnMode.setSelection(1);
        }

        if (config.getFavoriteBtDeviceName() != null && config.getFavoriteBtDeviceAddr() != null) {
            String name = config.getFavoriteBtDeviceName();
            BluetoothDevice fav = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(config.getFavoriteBtDeviceAddr());
            device = fav;
            spnDevicesAdapter.add(new BtFoundDevice(name, fav));
            spnDevices.setSelection(0);
        }

        editService.setText(config.getBtServiceUuid());
        editPin.setText(config.getBtPinCode());

        updateButtons();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ActivityConfig(this);

        layoutStatus = (LinearLayout) findViewById(R.id.layout_status);
        textStatus = (TextView) findViewById(R.id.bt_text_status);
        spnDevices = (Spinner) findViewById(R.id.bt_dropdown_device);
        imgError = (ImageView) findViewById(R.id.bt_error);
        prgBusy = (ProgressBar) findViewById(R.id.bt_progress);
        editService = (EditText) findViewById(R.id.bt_edit_service);
        btnSearchStart = (Button) findViewById(R.id.bt_button_find_start);
        btnSearchStop = (Button) findViewById(R.id.bt_button_find_stop);
        btnFileLookup = (ImageButton) findViewById(R.id.btn_file_lookup);
        btnUpload = (Button) findViewById(R.id.bt_button_upload);
        editPin = (EditText) findViewById(R.id.bt_edit_pin);
        textService = (TextView) findViewById(R.id.bt_text_service);
        textPin = (TextView) findViewById(R.id.bt_text_pin);
        spnMode = (Spinner) findViewById(R.id.bt_dropdown_mode);
        editFile = (EditText) findViewById(R.id.bt_edit_file);

        spnDevicesAdapter = new ArrayAdapter<BtFoundDevice>(MainActivity.this, android.R.layout.simple_spinner_item);
        spnDevicesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        arduinoMgr = new ArduinoCommManagerAny(this, config.getBtMode());
        arduinoMgr.addOnManagerEvent(mgrEvent);

        spnMode.requestFocus();
        spnMode.setOnItemSelectedListener(btModeChange);

        spnDevices.setAdapter(spnDevicesAdapter);
        spnDevices.setOnItemSelectedListener(btDeviceChange);

        btnSearchStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                foundDevices.clear();
                spnDevicesAdapter.clear();
                config.setFavoriteBtDevice(null, null);
                device = null;

                arduinoMgr.setMode(spnMode.getSelectedItem().toString());
                arduinoMgr.find();
                bSearching = true;
                updateButtons();
            }
        });

        btnSearchStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                arduinoMgr.cancelFind();
                bSearching = false;
                updateButtons();
            }
        });

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                arduinoMgr.cancelFind();
                bSearching = false;
                bError = false;

                BtFoundDevice sel = (BtFoundDevice) spnDevices.getSelectedItem();
                if (sel != null) {
                    arduinoMgr.createArduinoComm(sel.getDevice());
                }
                updateButtons();
            }
        });

        btnFileLookup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performFileSearch();
            }
        });

        editPin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                config.setBtPinCode(s.toString());
            }
        });

        editService.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                config.setBtServiceUuid(s.toString());
            }
        });

        loadValuesFromConfig();
    }

    private static final int READ_REQUEST_CODE = 42;

    private void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    protected void onActivityResult(int requestCode, int responseCode, Intent data) {
        arduinoMgr.onActivityResult(requestCode, responseCode);

        if (requestCode == READ_REQUEST_CODE) {
            if (responseCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                fileDisplayName(uri);
                uriFirmware = uri;
            } else {
                uriFirmware = null;
            }
            updateButtons();
        }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        arduinoMgr.onRequestPermissionResult(requestCode, permissions, grantResults);
    }

    private void fileDisplayName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                editFile.setText(displayName);
            }
        } finally {
            cursor.close();
        }
    }
}
