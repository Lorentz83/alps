/**
 *  Copyright 2020-2021 Lorenzo Bossi
 *
 *  This file is part of ALPS (Another Light Painting Stick).
 *
 *  ALPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ALPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ALPS.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.github.lorentz83.alps.utils;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Helper class to handle bluetooth.
 * <p>
 * It abstracts a little of android complexity.
 */
public class BluetoothHelper {
    /*
     * Some useful resources:
     * http://solderer.tv/data-transfer-between-android-and-arduino-via-bluetooth/
     * https://developer.android.com/guide/topics/connectivity/bluetooth#SettingUp
     */

    private final static LogUtility log = new LogUtility(BluetoothHelper.class);

    private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final Pattern _bluetoothNamePattern = Pattern.compile("^ALPS-.*");

    private BluetoothAdapter _bluetoothAdapter;

    /**
     * Creates a new instance of the helper.
     *
     * @throws IOException if there is no bluetooth adapted on the device.
     */
    public BluetoothHelper() throws IOException {
        _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (_bluetoothAdapter == null) {
            throw new IOException("Missing bluetooth");
        }
    }

    /**
     * Gets an intent to request to enable bluetooth.
     *
     * @return null if bluetooth is already enabled.
     */
    public @Nullable
    Intent enableBluetoothIntent() {
        if (_bluetoothAdapter.isEnabled()) {
            log.i("bluetooth is already enabled");
            return null;
        }
        return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    }

    /**
     * Opens the dialog to pair a new device.
     *
     * @param ctx      the context activity.
     * @param callback the callback to be executed on result
     */
    public void findNewDevice(@NonNull Activity ctx, @NonNull CompanionDeviceManager.Callback callback) {
        log.i("Requiring bluetooth companion");

        BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder()
                .setNamePattern(_bluetoothNamePattern)
                // TODO this seems making the selector crashing.
                // .addServiceUuid(new ParcelUuid(RFCOMM_UUID), null)
                .build();

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(true)
                .build();

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        CompanionDeviceManager deviceManager = ctx.getSystemService(CompanionDeviceManager.class);
        deviceManager.associate(pairingRequest, callback, null);
    }

    /**
     * Returns the specified bluetooth device.
     * <p>
     * Checks on all the paired devices and returns the one with the specified address.
     *
     * @param btAddress the address to search.
     * @return the bluetooth device or null if no device is found.
     */
    public @Nullable
    BluetoothDevice getDevice(@NonNull String btAddress) {
        log.i("searching for bluetooth %s", btAddress);
        Set<BluetoothDevice> pairedDevices = _bluetoothAdapter.getBondedDevices();

        BluetoothDevice rightDev = null;
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(btAddress)) {
                rightDev = device;
                break;
            }
        }
        return rightDev;
    }

    /**
     * Connects to the specified bluetooth device.
     *
     * @param device the device to connect.
     * @return the connected socket.
     * @throws IOException in case of IO error (e.g: the device is not in range).
     */
    public @NonNull
    BluetoothSocket connectDevice(@NonNull BluetoothDevice device) throws IOException {
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(RFCOMM_UUID);
        if (!socket.isConnected()) {
            // https://stackoverflow.com/questions/42570426/why-do-input-outputstream-from-bluetoothsocket-get-evaluated-as-not-null-then-t/42570610#42570610
            socket.connect();
        }
        return socket;
    }
}
