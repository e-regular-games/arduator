package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;

import com.e_regular_games.arduator.arduino.Firmware;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by SRE on 6/9/2017.
 */

public abstract class ArduinoCommUpdater {
    public enum ErrorCode { RemovePairing, Connect, IO, Services, Send, Sync, GetParams, SetProgParams, Program, VerifyProgram, Timeout, FW_FileName, FW_CheckSum, FW_StartCode, FW_ContiguousAddressing, Upload };

    public enum StatusCode {Connecting, FileCheck, Connected, Sync, GetParams, SetProgParams, Upload25, Upload50, Upload75, Upload100, Verifying, Complete, Disconnecting, Disconnected}

    public interface OnStatus {
        void onError(ErrorCode code);
        void onStatus(StatusCode progress);
    }

    public ArduinoCommUpdater(Activity app, BluetoothDevice device, String fileName) {
        this.app = app;
        this.device = device;
        this.fileName = fileName;
    }

    public void setOnStatus(OnStatus status) {
        this.status = status;
    }

    public void upload() {
        completed = false;
        firmware = new Firmware(app, fileName);
        if (!firmware.load()) {
            Firmware.ErrorCode ferr = firmware.getError();
            onError(ErrorCode.valueOf(ferr.name()));
            return;
        }

        fwBytes = firmware.getBytes();
        fwAddress = firmware.getStartAddress();
        fwBytesWritten = 0;
        fwBytesVerified = 0;

        onStatus(StatusCode.FileCheck);

        connect();
    }

    public boolean success() {
        return completed;
    }

    private boolean completed = false;
    protected Activity app;
    protected BluetoothDevice device;
    private String fileName;
    private OnStatus status;
    private Firmware firmware;
    private ArrayList<Integer> fwBytes;
    private int fwBytesWritten = 0, fwAddress = 0, fwBytesVerified = 0;
    private ActionCode inProgress;
    private ArrayList<Byte> toParse = new ArrayList<>();
    protected Timer timer = new Timer();
    private TimerTask taskPendingResponse;
    private static final int PAGE_LEN = 0x80;

    protected enum ActionCode {InitSerialService, Sync1, Sync2, Sync3, GetParam1, GetParam2, SetProgParams, SetExProgParams, EnterProgMode, ReadSignature, LoadAddressToWrite, LoadAddressToVerify, ExitProgMode, ReadPage, Program, WriteNext}
    protected interface MessageConstants {
        int READ = 1;
        int ERROR = 2;
        int STATUS = 4;
        int ACTION = 8;
    }

    protected class OpTimeout extends TimerTask {
        private Handler messenger;

        public OpTimeout(Handler handler) {
            messenger = handler;
        }

        @Override
        public void run() {
            System.out.println("Timeout: " + inProgress.name());
            messenger.obtainMessage(MessageConstants.ERROR, ErrorCode.Timeout).sendToTarget();
        }
    }

    protected Handler messenger = new Handler(new Handler.Callback() {
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
                    byte bytes[] = (byte[]) message.obj;
                    int length = message.arg1;
                    for (int i = 0; i < length; i += 1) {
                        toParse.add(bytes[i]);
                    }

                    parsePending(toParse);
                    break;
            }

            return false;
        }
    });

    private int getExpectedMessageLength() {
        switch (inProgress) {
            case Sync1:
            case Sync2:
            case Sync3:
            case SetProgParams:
            case SetExProgParams:
            case EnterProgMode:
            case LoadAddressToWrite:
            case LoadAddressToVerify:
            case Program:
            case ExitProgMode:
                return 2;
            case GetParam1:
            case GetParam2:
                return 3;
            case ReadSignature:
                return 5;
            case ReadPage:
                return 2 + PAGE_LEN;
        }

        return 0;
    };

    private void parsePending(ArrayList<Byte> toParse) {
        while(toParse.size() > 0 && toParse.get(0) != 0x14) {
            toParse.remove(0);
        }

        int msgLen = getExpectedMessageLength();
        if (msgLen == 0 || toParse.size() < msgLen) {
            return;
        }

        if (taskPendingResponse != null) {
            taskPendingResponse.cancel();
        }

        boolean validEnding = toParse.get(msgLen - 1) == 0x10;
        switch(inProgress) {
            case Sync1:
                if (validEnding) {
                    doAction(ActionCode.Sync2);
                } else {
                    onError(ErrorCode.Sync);
                }
                break;

            case Sync2:
                if (validEnding) {
                    doAction(ActionCode.Sync3);
                } else {
                    onError(ErrorCode.Sync);
                }
                break;

            case Sync3:
                if (validEnding) {
                    doAction(ActionCode.GetParam1);
                    onStatus(StatusCode.Sync);
                } else {
                    onError(ErrorCode.Sync);
                }
                break;

            case GetParam1:
                if (validEnding && (toParse.get(1) == 0x04 || toParse.get(1) == 0x01)) {
                    doAction(ActionCode.GetParam2);
                } else {
                    onError(ErrorCode.GetParams);
                }
                break;

            case GetParam2:
                if (validEnding && (toParse.get(1) == 0x04 || toParse.get(1) == 0x10)) {
                    doAction(ActionCode.SetProgParams);
                    onStatus(StatusCode.GetParams);
                } else {
                    onError(ErrorCode.GetParams);
                }
                break;

            case SetProgParams:
                if (validEnding) {
                    doAction(ActionCode.SetExProgParams);
                } else {
                    onError(ErrorCode.SetProgParams);
                }
                break;

            case SetExProgParams:
                if (validEnding) {
                    doAction(ActionCode.EnterProgMode);
                    onStatus(StatusCode.SetProgParams);
                } else {
                    onError(ErrorCode.SetProgParams);
                }
                break;

            case EnterProgMode:
                if (validEnding) {
                    doAction(ActionCode.ReadSignature);
                } else {
                    onError(ErrorCode.Program);
                }
                break;

            case ReadSignature:
                if (validEnding && toParse.get(1) == 0x1E && toParse.get(2) == (byte)0x95 && toParse.get(3) == 0x0F) {
                    fwAddress = firmware.getStartAddress();
                    fwBytesWritten = 0;
                    doAction(ActionCode.LoadAddressToWrite);
                } else {
                    onError(ErrorCode.Program);
                }
                break;

            case Program:
                if (validEnding && fwBytesWritten < fwBytes.size()) {
                    fwAddress += (PAGE_LEN / 2); // page_len is in bytes, address is in words.

                    if (fwBytesWritten >= fwBytes.size() * 0.75) {
                        onStatus(StatusCode.Upload75);
                    } else if (fwBytesWritten >= fwBytes.size() * 0.50) {
                        onStatus(StatusCode.Upload50);
                    } else if (fwBytesWritten >= fwBytes.size() * 0.25) {
                        onStatus(StatusCode.Upload25);
                    }

                    doAction(ActionCode.LoadAddressToWrite);
                } else if (validEnding && fwBytesWritten >= fwBytes.size()) {
                    onStatus(StatusCode.Upload100);
                    fwAddress = firmware.getStartAddress();
                    fwBytesVerified = 0;
                    doAction(ActionCode.LoadAddressToVerify);
                } else {
                    onError(ErrorCode.Program);
                }
                break;

            case ReadPage:
                if (validEnding) {
                    for (int i = 0; i < PAGE_LEN; i += 1) {
                        int verify = fwBytesVerified + i < fwBytes.size() ? fwBytes.get(fwBytesVerified + i) : 0xFF;
                        if (toParse.get(1 + i) != (byte)verify) {
                            onError(ErrorCode.VerifyProgram);
                            return;
                        }
                    }

                    fwBytesVerified += PAGE_LEN;

                    if (fwBytesVerified < fwBytes.size()) {
                        fwAddress += (PAGE_LEN / 2); // page_len is in bytes, address is in words.
                        doAction(ActionCode.LoadAddressToVerify);
                    } else if (fwBytesVerified >= fwBytes.size()) {
                        doAction(ActionCode.ExitProgMode);
                    }
                } else {
                    onError(ErrorCode.VerifyProgram);
                }
                break;

            case LoadAddressToWrite:
                if (validEnding) {
                    doAction(ActionCode.Program);
                } else {
                    onError(ErrorCode.Program);
                }
                break;

            case LoadAddressToVerify:
                if (validEnding) {
                    doAction(ActionCode.ReadPage);
                } else {
                    onError(ErrorCode.VerifyProgram);
                }
                break;

            case ExitProgMode:
                if (validEnding) {
                    onStatus(StatusCode.Complete);
                    completed = true;
                    disconnect();
                } else {
                    onError(ErrorCode.VerifyProgram);
                }
                break;
        }

        for (; msgLen > 0; msgLen--) {
            toParse.remove(0);
        }
    }

    private void handleError(ErrorCode err) {
        disconnect();
    }

    private StatusCode lastStatus;
    protected void onStatus(final StatusCode stat) {
        if (lastStatus == stat) {
            return;
        }

        lastStatus = stat;
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.onStatus(stat);
                }
            }
        });
    }

    protected void onError(final ErrorCode err) {
        app.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.onError(err);
                }
            }
        });

        handleError(err);
    }

    protected void doAction(ActionCode action) {
        inProgress = action;

        switch (action) {
            case Sync1:
            case Sync2:
            case Sync3:
                send(action, new int[] { 0x30, 0x20 });
                break;
            case GetParam1:
                send(action, new int[] { 0x41, 0x81, 0x20 });
                break;
            case GetParam2:
                send(action, new int[] { 0x41, 0x82, 0x20 });
                break;
            case SetProgParams:
                send(action, new int[] { 0x42, 0x86, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x03, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x80, 0x04, 0x00, 0x00, 0x00, 0x80, 0x00, 0x20 });
                break;
            case SetExProgParams:
                send(action, new int[] { 0x45, 0x05, 0x04, 0xD7, 0xC2, 0x00, 0x20 });
                break;
            case EnterProgMode:
                send(action, new int[] { 0x50, 0x20 });
                break;
            case ReadSignature:
                send(action, new int[] { 0x75, 0x20 });
                break;
            case LoadAddressToVerify:
            case LoadAddressToWrite:
                send(action, new int[] { 0x55, 0xFF & fwAddress, 0xFF & (fwAddress >> 8), 0x20 });
                break;
            case Program:
                int bytes[] = new int[5 + PAGE_LEN];
                bytes[0] = 0x64;
                bytes[1] = (PAGE_LEN >> 8) & 0xFF;
                bytes[2] = PAGE_LEN & 0xFF;
                bytes[3] = 0x46;
                for (int i = 0; i < PAGE_LEN; i += 1) {
                    if (fwBytesWritten + i < fwBytes.size()) {
                        bytes[4 + i] = fwBytes.get(fwBytesWritten + i);
                    } else {
                        bytes[4 + i] = 0xFF;
                    }
                }
                bytes[4 + PAGE_LEN] = 0x20;

                fwBytesWritten += PAGE_LEN;
                send(action, bytes);
                break;
            case ReadPage:
                send(action, new int[] { 0x74, (PAGE_LEN >> 8) & 0xFF, PAGE_LEN & 0xFF, 0x46, 0x20 });
                break;
            case ExitProgMode:
                send(action, new int[] { 0x51, 0x20 });
                break;
            default:
                System.out.println("Action Missing For:" + action.name());
        }

        taskPendingResponse = new OpTimeout(messenger);
        timer.schedule(taskPendingResponse, 3000);
    }

    protected abstract  void connect();
    protected abstract void disconnect();
    protected abstract void send(ActionCode action, int packet[]);

    // https://stackoverflow.com/questions/38055699/programmatically-pairing-with-a-ble-device-on-android-4-4
    protected void deleteBondInformation() {
        try {
            // FFS Google, just unhide the method.
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {

        }
    }
}
