package com.github.lorentz83.alps;

import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Preferences {
    private static final String STICK_LENGTH = "stick_length";
    private static final String BT_PAIRED_ADDRESS = "bt_paired_address";
    private static final String LOOP = "loop";
    private static final String WAIT_TIME_MS = "wait_time_ms";
    private static final String STICK_BRIGHTNESS = "stick_brightness";

    private final SharedPreferences _sharedPref;
    private static Preferences _instance;

    private BluetoothSocket _dev;

    public Preferences(@NonNull SharedPreferences sharedPref) {
        _sharedPref = sharedPref;
        _instance = this;
    }

    public static @NonNull
    Preferences getInstance() {
        if (_instance == null) {
            throw new IllegalStateException("getInstance called before the constructor");
        }
        return _instance;
    }

    public void setWaitTimeMs(int ms) {
        SharedPreferences.Editor editor = _sharedPref.edit();
        editor.putInt(WAIT_TIME_MS, ms);
        editor.apply();
    }

    public int getWaitTimeMs() {
        return _sharedPref.getInt(WAIT_TIME_MS, 0);
    }

    public void setLoop(boolean val) {
        SharedPreferences.Editor editor = _sharedPref.edit();
        editor.putBoolean(LOOP, val);
        editor.apply();
    }

    public boolean getLoop() {
        return _sharedPref.getBoolean(LOOP, false);
    }

    public String getBluetoothAddress() {
        return _sharedPref.getString(BT_PAIRED_ADDRESS, "");
    }

    public void setBluetoothAddress(String addr) {
        SharedPreferences.Editor editor = _sharedPref.edit();
        editor.putString(BT_PAIRED_ADDRESS, addr);
        editor.apply();
    }

    public void setStickLength(int val) {
        SharedPreferences.Editor editor = _sharedPref.edit();
        editor.putInt(STICK_LENGTH, val);
        editor.apply();
    }

    public int getStickLength() {
        return _sharedPref.getInt(STICK_LENGTH, 30);
    }

    public void setStickBrightness(float val) {
        SharedPreferences.Editor editor = _sharedPref.edit();
        editor.putFloat(STICK_BRIGHTNESS, val);
        editor.apply();
    }

    public float getStickBrightness() {
        return _sharedPref.getFloat(STICK_BRIGHTNESS, 1);
    }

    public void setConnectedBluetooth(@Nullable BluetoothSocket dev) {
        _dev = dev;
    }

    public @Nullable
    BluetoothSocket getConnectedBluetooth() {
        return _dev;
    }

}
