package com.e_regular_games.arduator;

import android.bluetooth.BluetoothDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.e_regular_games.arduator.arduino.ArduinoComm;
import com.e_regular_games.arduator.arduino.ArduinoCommManager;
import com.e_regular_games.arduator.arduino.ArduinoCommManagerAny;

public class MainActivity extends AppCompatActivity {

    private ActivityConfig config;
    private ArduinoCommManagerAny arduinoMgr;
    private Button btnSearchStart, btnSearchStop, btnUpload;

    private ArduinoCommManager.ManagerEvent mgrEvent = new ArduinoCommManager.ManagerEvent() {
        public void onFind(BluetoothDevice device, boolean saved) {
        }

        public void onStatusChange(String state) {
        }

        public void onCreateStation(ArduinoComm station) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ActivityConfig(this);
        arduinoMgr = new ArduinoCommManagerAny(this, config.getBtMode());
        arduinoMgr.addOnManagerEvent(mgrEvent);

        btnSearchStart = (Button)findViewById(R.id.bt_button_find_start);
        btnSearchStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSearchStart.setEnabled(false);
                btnSearchStop.setEnabled(true);
            }
        });

        btnSearchStop = (Button)findViewById(R.id.bt_button_find_stop);
        btnUpload = (Button)findViewById(R.id.bt_button_upload);

    }
}
