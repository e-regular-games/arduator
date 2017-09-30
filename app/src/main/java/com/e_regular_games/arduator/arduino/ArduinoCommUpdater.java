package com.e_regular_games.arduator.arduino;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author S. Ryan Edgar
 * Provided an ArduinoComm object, upload the specified Firmware to it. This relies on the
 * arduino power reseting when the ArduinoComm connects to it. The power reset triggers the
 * bootloader to watch for incoming commands from the serial port.
 *
 * Use ArduinoCommUpdater.OnStatus API to recieve status updates when each part of the upload
 * process completes, or if there is an error.
 */
public class ArduinoCommUpdater {
    public enum ErrorCode { Connect, IO, Send, Receive, Services, RemovePairing, Sync, GetParams, SetProgParams, Program, VerifyProgram, Timeout, PinRequired, ServiceIdRequired, FW_FileName, FW_CheckSum, FW_StartCode, FW_ContiguousAddressing, Upload };

    public enum StatusCode {Connecting, FileCheck, Connected, Sync, GetParams, SetProgParams, Upload25, Upload50, Upload75, Upload100, Verifying, Complete, Disconnecting, Disconnected}

    public interface OnStatus {
        void onError(ErrorCode code);
        void onStatus(StatusCode progress);
    }

    public ArduinoCommUpdater(Activity app, ArduinoComm device) {
        this.app = app;
        this.device = device;

        device.addEventHandler(new ArduinoComm.EventHandler() {
            public void onError(ArduinoComm self, ArduinoComm.ErrorCode code) {
                ArduinoCommUpdater.this.onError(ErrorCode.valueOf(code.name()));
            }
            
            public void onStatus(ArduinoComm self, ArduinoComm.StatusCode code) {
                switch(code) {
                    case Connected:
                        if (inProgress == ActionCode.Wait) {
                            Timer t = new Timer();
                            t.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    doAction(ActionCode.Sync1);
                                }
                            }, 100);
                        }
                    break;
                    
                    case Disconnected:
                        if (taskPendingResponse != null) {
                            taskPendingResponse.cancel();
                        }
                        inProgress = ActionCode.Wait;
                        break;
                }

                ArduinoCommUpdater.this.onStatus(StatusCode.valueOf(code.name()));
            }
            
            public void onContent(ArduinoComm self, int length, byte[] content) {
                System.out.print("Recv: ");
                for (int i = 0; i < length; i += 1) {
                    System.out.print(Byte.toString(content[i]) + " ");
                    toParse.add(content[i]);
                }
                System.out.println();

                parsePending(toParse);
            }
        });
    }

    public void setOnStatus(OnStatus status) {
        this.status = status;
    }

    public void upload(InputStream in) {
        completed = false;
        onStatus(StatusCode.FileCheck);

        firmware = new Firmware();
        if (!firmware.load(in)) {
            Firmware.ErrorCode ferr = firmware.getError();
            onError(ErrorCode.valueOf(ferr.name()));
            return;
        }

        fwBytes = firmware.getBytes();
        fwAddress = firmware.getStartAddress();
        fwBytesWritten = 0;
        fwBytesVerified = 0;

        device.connect();
    }

    public boolean success() {
        return completed;
    }

    private boolean completed = false;
    protected Activity app;
    protected ArduinoComm device;
    private OnStatus status;
    private Firmware firmware;
    private ArrayList<Integer> fwBytes;
    private int fwBytesWritten = 0, fwAddress = 0, fwBytesVerified = 0;
    private ActionCode inProgress = ActionCode.Wait;
    private ArrayList<Byte> toParse = new ArrayList<>();
    protected Timer timer = new Timer();
    private TimerTask taskPendingResponse;
    private int retries = 0;
    private static final int PAGE_LEN = 0x80;

    protected enum ActionCode {Wait, InitSerialService, Sync1, Sync2, Sync3, GetParam1, GetParam2, SetProgParams, SetExProgParams, EnterProgMode, ReadSignature, LoadAddressToWrite, LoadAddressToVerify, ExitProgMode, ReadPage, Program, WriteNext}
    protected interface MessageConstants {
        int ERROR = 2;
    }

    protected class OpTimeout extends TimerTask {
        private Handler messenger;

        public OpTimeout(Handler handler) {
            messenger = handler;
        }

        @Override
        public void run() {
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
                if (validEnding) {
                    fwBytesWritten += PAGE_LEN;
                    fwAddress += (PAGE_LEN / 2); // page_len is in bytes, address is in words.

                    if (fwBytesWritten >= fwBytes.size()) {
                        onStatus(StatusCode.Verifying);
                        fwAddress = firmware.getStartAddress();
                        fwBytesVerified = 0;
                        doAction(ActionCode.LoadAddressToVerify);
                    } else if (fwBytesWritten >= fwBytes.size() * 0.75) {
                        onStatus(StatusCode.Upload75);
                        doAction(ActionCode.LoadAddressToWrite);
                    } else if (fwBytesWritten >= fwBytes.size() * 0.50) {
                        onStatus(StatusCode.Upload50);
                        doAction(ActionCode.LoadAddressToWrite);
                    } else if (fwBytesWritten >= fwBytes.size() * 0.25) {
                        onStatus(StatusCode.Upload25);
                        doAction(ActionCode.LoadAddressToWrite);
                    } else {
                        doAction(ActionCode.LoadAddressToWrite);
                    }
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
                    onStatus(StatusCode.Complete); // must set status before completed!
                    completed = true;
                    device.disconnect();
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
        device.disconnect();
    }

    private StatusCode lastStatus;
    protected void onStatus(final StatusCode stat) {
        if (lastStatus == stat || completed) {
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
                device.send(new int[] { 0x30, 0x20 });
                break;
            case GetParam1:
                device.send(new int[] { 0x41, 0x81, 0x20 });
                break;
            case GetParam2:
                device.send(new int[] { 0x41, 0x82, 0x20 });
                break;
            case SetProgParams:
                device.send(new int[] { 0x42, 0x86, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x03, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x80, 0x04, 0x00, 0x00, 0x00, 0x80, 0x00, 0x20 });
                break;
            case SetExProgParams:
                device.send(new int[] { 0x45, 0x05, 0x04, 0xD7, 0xC2, 0x00, 0x20 });
                break;
            case EnterProgMode:
                device.send(new int[] { 0x50, 0x20 });
                break;
            case ReadSignature:
                device.send(new int[] { 0x75, 0x20 });
                break;
            case LoadAddressToVerify:
            case LoadAddressToWrite:
                device.send(new int[] { 0x55, 0xFF & fwAddress, 0xFF & (fwAddress >> 8), 0x20 });
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
                device.send(bytes);
                break;
            case ReadPage:
                device.send(new int[] { 0x74, (PAGE_LEN >> 8) & 0xFF, PAGE_LEN & 0xFF, 0x46, 0x20 });
                break;
            case ExitProgMode:
                device.send(new int[] { 0x51, 0x20 });
                break;
        }

        taskPendingResponse = new OpTimeout(messenger);
        timer.schedule(taskPendingResponse, 3000);
    }
}
