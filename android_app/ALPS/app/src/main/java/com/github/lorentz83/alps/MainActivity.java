package com.github.lorentz83.alps;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.lorentz83.alps.communication.Protocol;
import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/*
 *
 * http://solderer.tv/data-transfer-between-android-and-arduino-via-bluetooth/
 * https://developer.android.com/guide/topics/connectivity/bluetooth#SettingUp
 *
 * */

public class MainActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(MainActivity.class);

    private static final int SELECT_DEVICE_REQUEST_CODE = 42;
    private static final int REQUEST_ENABLE_BT = 41;
    private static final int REQUEST_OPEN_IMAGE = 40;

    private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final Pattern _bluetoohNamePattern = Pattern.compile("alps-.*");


    private BluetoothSocket _socket = null;
    private SharedPreferences _sharedPref;
    private BluetoothAdapter _bluetoothAdapter;
    private Sender _sender;

    private Bitmap _bitmap = null;
    private ImageView _preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.i("OnCreate called");
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        _preview = findViewById(R.id.image_preview);
        ProgressBar _progressBar = findViewById(R.id.progress_bar);

        _sharedPref = getPreferences(Context.MODE_PRIVATE);
        _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (_bluetoothAdapter == null) {
            if (_bluetoothAdapter == null) {
                log.wtf("no bluetooth device found");
                showToast("There is no bluetooth here!");
            }
        }

        requestEnableBluetoothIfNeeded();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (_socket != null) {
            try {
                log.i("closing bluetooth connection");
                _socket.close();
            } catch (IOException e) {
                log.w("onStop error", e);
            }
            _socket = null;
        }
        if (_sender != null) {
            _sender.interrupt();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_find_device:
                findNewDevice();
                return true;
            case R.id.action_connect_bt:
                connectBluetooth();
                return true;
            case R.id.action_load_image:
                openImage();
                return true;
            // case R.id.action_settings:
            //    return true;
            default:
                log.i("OptionItemSelected, unknown menu entry %s", id);
        }

        return super.onOptionsItemSelected(item);
    }

    private void openImage() {
        Intent open = new Intent(Intent.ACTION_GET_CONTENT);
        open.setType("image/*");
        open.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(open, REQUEST_OPEN_IMAGE);
    }

    private void requestEnableBluetoothIfNeeded() {
        if (!_bluetoothAdapter.isEnabled()) {
            log.i("asking to enable bluetooth");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            log.i("bluetooth is enabled");
        }
    }

    private void findNewDevice() {
        log.i("Requiring bluetooth companion");

        BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder()
                .setNamePattern(_bluetoohNamePattern)
                .addServiceUuid(new ParcelUuid(RFCOMM_UUID), null)
                .build();

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(true)
                .build();

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        CompanionDeviceManager deviceManager = getSystemService(CompanionDeviceManager.class);
        deviceManager.associate(pairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            log.i("bluetooth onDeviceFound");
                            startIntentSenderForResult(chooserLauncher,
                                    SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            log.w("error sending intent", e);
                            showToast("Error handling bluetooth " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        log.w("error from companion device manager", error);
                        showToast("Error connecting to bluetooth" + error);
                    }
                },
                null);
    }

    private void connectBluetooth() {
        String btAddr = _sharedPref.getString(getString(R.string.bt_paired_address), "");
        if (btAddr.isEmpty()) {
            showToast("Pair a device first");
            return;
        }

        log.i("searching for bluetooth %s", btAddr);
        Set<BluetoothDevice> pairedDevices = _bluetoothAdapter.getBondedDevices();

        BluetoothDevice rightDev = null;
        for (BluetoothDevice device : pairedDevices) {
            log.i("paired with bluetooth %s %s", device.getAddress(), device.getName());
            if (device.getAddress().equals(btAddr)) {
                rightDev = device;
                //break;
            }
        }

        if (rightDev == null) {
            showToast("The stick is not paired");
            return;
        }

        handleCommunication(rightDev);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SELECT_DEVICE_REQUEST_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    // User has chosen to pair with the Bluetooth device.
                    BluetoothDevice deviceToPair =
                            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    deviceToPair.createBond();

                    log.i("device " + deviceToPair.getName() + " " + deviceToPair.getAddress());
                    for (ParcelUuid p : deviceToPair.getUuids()) {
                        log.i("device UUID %s", p.getUuid());
                    }

                    SharedPreferences.Editor editor = _sharedPref.edit();
                    editor.putString(getString(R.string.bt_paired_address), deviceToPair.getAddress());
                    editor.apply();

                    handleCommunication(deviceToPair);


                    // ... Continue interacting with the paired device.
                } else {
                    showToast("No bluetooth device found");
                }
            }
            case REQUEST_ENABLE_BT: {
                if (resultCode != Activity.RESULT_OK) {
                    showToast("Bluetooth is required to work");
                }
            }
            case REQUEST_OPEN_IMAGE: {
                if (resultCode != Activity.RESULT_OK) {
                    showToast("No image selected");
                    return;
                }
                // https://stackoverflow.com/questions/3879992/how-to-get-bitmap-from-an-uri
                try {
                    Bitmap img = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());

                    int w = img.getWidth();
                    int h = img.getHeight();

                    w = (int)Math.round(30.0 / h * w);
                    h = 30;

                    _bitmap = Bitmap.createScaledBitmap(img, w, h,true);

                    BitmapDrawable myBitmapDrawable = new BitmapDrawable(getResources(), _bitmap);
                    myBitmapDrawable.getPaint().setFilterBitmap(false);
                    _preview.setImageDrawable(myBitmapDrawable);

                } catch (IOException e) {
                    log.w("cannot open image", e);
                    showToast("Cannot open image");
                }
                log.i("open image: %d %s", resultCode, data.getData());
                return;
            }
            default:
                log.i("onActivityResult unknown code %d, result: %d", requestCode, resultCode);
        }
    }

    private void handleCommunication(BluetoothDevice device) {
        try {
            log.i("starting rfcomm connection");
            if (_socket == null) { // TODO: make this better.
                _socket = device.createRfcommSocketToServiceRecord(RFCOMM_UUID);
                _socket.connect(); //https://stackoverflow.com/questions/42570426/why-do-input-outputstream-from-bluetoothsocket-get-evaluated-as-not-null-then-t/42570610#42570610
            }
            InputStream in = _socket.getInputStream();
            OutputStream out = _socket.getOutputStream();


            Protocol p = new Protocol(in, out);
            _sender = new Sender(p);
            _sender.start();
//            for (int i = 0; i<3; i++){
//                ArrayList<PixelColor> line = new ArrayList<>();
//                for (int x = 0; x < 30; x++) {
//                    int r = (x+i) % 3 == 0 ? 20 : 0;
//                    int g = (x+i) % 3 == 1 ? 20 : 0;
//                    int b = (x+i) % 3 == 2 ? 20 : 0;
//                    line.add(new PixelColor(r, g, b));
//                }
//                p.writePixels(0, line.iterator());
//                p.show();
//                Thread.sleep(500);
//            }
//            p.off();
//            p.show();

        } catch (IOException e) {
            log.w("error in communication protocol", e);
        }
//        catch (InterruptedException e) {
//            log.w("error in sleeping protocol", e);
//        }
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    public void buttonStopClicked(View view) {
        log.i("stop");
        if (_sender == null) {
            showToast("not connected");
            return;
        }
        _sender.interrupt();
    }

    public void buttonPlayClicked(View view) {
        log.i("play");
        if (_sender == null) {
            showToast("not connected");
            return;
        }
        if (_bitmap == null) {
            showToast("no image loaded");
            return;
        }
        _sender.sendBitmap(_bitmap);
    }
}
