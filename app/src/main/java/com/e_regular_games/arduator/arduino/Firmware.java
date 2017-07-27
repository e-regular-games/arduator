package com.e_regular_games.arduator.arduino;

import android.app.Activity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by SRE on 6/9/2017.
 */

public class Firmware {
    public Firmware(Activity app, String fileName) {
        this.app = app;
        this.fileName = fileName;
    }

    public boolean load() {
        try {
            InputStream hex = app.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(hex));

            String line;
            boolean firstLine = true;
            int nextAddress = 0;
            while ((line = reader.readLine()) != null) {
                if (line.charAt(0) != ':') {
                    lastError = ErrorCode.FW_StartCode;
                    return false;
                }

                int dataLen = toInt(line.substring(1, 3));
                int lineLen = 2 * dataLen;
                int address = toInt(line.substring(3, 7));
                int checkSum = toInt(line.substring(1 + lineLen + 8, 1 + lineLen + 8 + 2));
                if (dataLen == 0 && address == 0 && toInt(line.substring(7, 8)) == 0) {
                    //last line
                } else if (firstLine) {
                    startAddress = address;
                } else if (address != nextAddress) {
                    lastError = ErrorCode.FW_ContiguousAddressing;
                    return false;
                }

                if (!verifyChecksum(line.substring(1, 1 + lineLen + 8), checkSum)) {
                    lastError = ErrorCode.FW_CheckSum;
                    return false;
                }

                for (int i = 0; i < dataLen; i += 1) {
                    bytes.add(toInt(line.substring(9 + 2 * i, 9 + 2 * i + 2)));
                }

                firstLine = false;
                nextAddress = address + dataLen;
            }
        } catch (IOException e) {
            lastError = ErrorCode.FW_FileName;
            return false;
        }

        return true;
    }

    public enum ErrorCode {FW_FileName, FW_CheckSum, FW_StartCode, FW_ContiguousAddressing}

    public int getStartAddress() {
        return startAddress;
    }

    public ErrorCode getError() {
        return lastError;
    }

    public ArrayList<Integer> getBytes() {
        return bytes;
    }

    private Activity app;
    private String fileName;
    private int startAddress = 0;
    private ErrorCode lastError;
    private ArrayList<Integer> bytes = new ArrayList<>();

    private boolean verifyChecksum(String lineWithoutSum, int sum) {
        if (lineWithoutSum.length() % 2 == 1) {
            return false;
        }

        int runningSum = 0;
        for (int i = 0; i < lineWithoutSum.length(); i += 2) {
            runningSum += toInt(lineWithoutSum.substring(i, i + 2));
        }

        return sum == (0xFF & (0x100 - runningSum));
    }

    private int toInt(String hex) {
        if ((hex.length() % 2) == 1) {
            return 0;
        }

        return Integer.parseInt(hex, 16);
    }
}
