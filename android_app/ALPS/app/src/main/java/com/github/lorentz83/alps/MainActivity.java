/**
 *  Copyright 2020 Lorenzo Bossi
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


package com.github.lorentz83.alps;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuCompat;
import androidx.viewpager.widget.ViewPager;

import com.github.lorentz83.alps.communication.Protocol;
import com.github.lorentz83.alps.ui.AboutWindow;
import com.github.lorentz83.alps.ui.ColorPicker;
import com.github.lorentz83.alps.ui.MyPagerAdapter;
import com.github.lorentz83.alps.ui.ReshowSettingsDialog;
import com.github.lorentz83.alps.utils.BluetoothHelper;
import com.github.lorentz83.alps.utils.CustomTextResult;
import com.github.lorentz83.alps.utils.LogUtility;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(MainActivity.class);

    private static final int REQUEST_OPEN_IMAGE = 40;
    private static final int REQUEST_CUSTOM_TEXT = 41;
    private static final int REQUEST_EDIT_IMAGE = 42;

    private static final int REQUEST_ENABLE_BT = 871;
    private static final int SELECT_DEVICE_REQUEST_CODE = 872;

    private ExecutorService _executor = Executors.newFixedThreadPool(2);
    private ColorPicker _colorPicker;

    private BluetoothHelper _btHelper;
    private BluetoothSocket _socket = null;
    private Preferences _sharedPref;
    private MyPagerAdapter _myPagerAdapter;
    private MenuItem _actionBt;
    private ReshowSettingsDialog _reshowSettingsDialog;

    private File _editedFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        _sharedPref = new Preferences(getPreferences(Context.MODE_PRIVATE));

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        _myPagerAdapter = new MyPagerAdapter(this, getSupportFragmentManager());

        ViewPager viewPager = findViewById(R.id.view_pager);
        _myPagerAdapter.linkTo(viewPager);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        try {
            _btHelper = new BluetoothHelper();
            Intent intent = _btHelper.enableBluetoothIntent();
            if (intent != null) {
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }
        } catch (IOException e) {
            log.wtf("no bluetooth device found", e);
            showToast("There is no bluetooth here!");
        }

        _colorPicker = new ColorPicker(this);
        _reshowSettingsDialog = new ReshowSettingsDialog(this, (pref)->{
            BluetoothSocket dev = _sharedPref.getConnectedBluetooth();
            if (dev == null) {
                showToast("Bluetooth is disconnected");
                return false;
            }
            Protocol p = new Protocol();
            try {
                p.initializeConnection(dev);
                p.replaySettings(pref.delay, pref.brightness, pref.loop);
                return true;
            } catch (IOException e) {
                log.w("protocol error", e);
                showToast("Error: "+e.getMessage());
                return false;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        _actionBt = menu.findItem(R.id.action_toggle_bt);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_find_device:
                findNewDevice();
                return true;
            case R.id.action_toggle_bt:
                toggleBluetooth();
                return true;
            case R.id.action_load_image:
                launchOpenImageDialog();
                return true;
            case R.id.action_write_text:
                writeText();
                return true;
            case R.id.action_fill_color:
                colorPicker();
                return true;
            case R.id.action_reshow_settings:
                _reshowSettingsDialog.show();
                return true;
            case R.id.action_about:
                AboutWindow.show(this);
                return true;
            case R.id.action_edit_image:
                editImage();
                return true;
            default:
                log.i("OptionItemSelected, unknown menu entry %s", id);
        }
        return super.onOptionsItemSelected(item);
    }

    private void writeText() {
        Intent open = new Intent(this, CustomTextActivity.class);
        startActivityForResult(open, REQUEST_CUSTOM_TEXT);
    }

    private void colorPicker() {
        _colorPicker.show(color -> {
            Bitmap image = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            canvas.drawColor(color);
            openImage(image);
        });
    }

    private void writeText(String text, int backgroundColor, int textColor, Typeface face) {
        // https://stackoverflow.com/questions/8799290/convert-string-text-to-bitmap
        float textSize = 300;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(face);

        float baseline = -paint.ascent(); // ascent() is negative
        int width = (int) paint.measureText(text);
        int height = (int) (baseline + paint.descent());

        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);
        canvas.drawColor(backgroundColor);
        canvas.drawText(text, 0, baseline, paint);

        openImage(image);
    }

    private void launchOpenImageDialog() {
        Intent open = new Intent(Intent.ACTION_GET_CONTENT);
        open.setType("image/*");
        open.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(open, REQUEST_OPEN_IMAGE);
    }

    private void toggleBluetooth() {
        log.i("toggle bluetooth, socket == null ? %s , connected %s", _socket == null, _socket != null && _socket.isConnected());
        if (_socket != null) {
            disconnectBluetooth();
            return;
        } else {
            connectBluetooth();
        }
    }

    public void disconnectBluetooth() {
        if (_socket != null) {
            try {
                _socket.close();
            } catch (IOException e) {
                log.w("error closing bluetooth", e);
            }
            _sharedPref.setConnectedBluetooth(null);
            _socket = null;
        }
        _actionBt.setIcon(R.drawable.ic_bt_disconnected);
    }

    private void connectBluetooth() {
        String btAddr = _sharedPref.getBluetoothAddress();
        if (btAddr.isEmpty()) {
            showToast("Pair a device first");
            findNewDevice();
            return;
        }

        BluetoothDevice dev = _btHelper.getDevice(btAddr);
        if (dev == null) {
            showToast("The stick is not paired");
            return;
        }
        connectBluetooth(dev);
    }

    private void findNewDevice() {
        _btHelper.findNewDevice(this, new CompanionDeviceManager.Callback() {
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
        });
    }

    private void connectBluetooth(BluetoothDevice device) {
        _actionBt.setIcon(R.drawable.ic_bt_connecting);
        ((AnimationDrawable) _actionBt.getIcon()).start();

        _executor.execute(() -> {
            try {
                _socket = _btHelper.connectDevice(device);
                runOnUiThread(() -> {
                    _sharedPref.setConnectedBluetooth(_socket);
                    _actionBt.setIcon(R.drawable.ic_bt_connected);
                });
            } catch (IOException e) {
                showToast(String.format("error connecting to bluetooth: %s", e.getMessage()));
                log.w("cannot connect to bluetooth", e);
                runOnUiThread(() -> _actionBt.setIcon(R.drawable.ic_bt_disconnected));
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        log.i("onActivityResult %d, result: %d", requestCode, resultCode);

        switch (requestCode) {
            case SELECT_DEVICE_REQUEST_CODE: {
                if (resultCode == Activity.RESULT_OK) {
                    // User has chosen to pair with the Bluetooth device.
                    BluetoothDevice deviceToPair =
                            data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    boolean newBond = deviceToPair.createBond();
                    _sharedPref.setBluetoothAddress(deviceToPair.getAddress());

                    log.i("createBond = %s", newBond);
                    if ( !newBond ) {
                        connectBluetooth(deviceToPair);
                    }
                    // TODO register to ACTION_BOND_STATE_CHANGED to know when the pin is set and
                    // start the connection.
                } else {
                    showToast("No bluetooth device found");
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                if (resultCode != Activity.RESULT_OK) {
                    showToast("Bluetooth is required to work");
                }
                break;
            }
            case REQUEST_OPEN_IMAGE: {
                if (resultCode != Activity.RESULT_OK) {
                    showToast("No image selected");
                    return;
                }
                try {
                    openImage(data.getData());
                } catch (IOException e) {
                    log.w("cannot open image", e);
                    showToast("Cannot open image");
                }
                log.i("open image: %d %s", resultCode, data.getData());
                break;
            }
            case REQUEST_CUSTOM_TEXT: {
                if (resultCode == Activity.RESULT_OK) {
                    CustomTextResult res = (CustomTextResult) data.getSerializableExtra(CustomTextResult.CUSTOM_TEXT_RESULT);
                    Typeface face = Typeface.DEFAULT;
                    writeText(res.getText(), res.getBackgroundColor(), res.getForegroundColor(), face);
                } else {
                    showToast("No text provided");
                }
                break;
            }
            case REQUEST_EDIT_IMAGE: {
                readReturnBitmap(requestCode, resultCode, data);
            }
            default:
                log.i("onActivityResult unknown code %d, result: %d", requestCode, resultCode);
        }
    }

    public void readReturnBitmap(int requestCode, int resultCode, Intent data) {
        log.i("result %d, %d, %s", requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            log.i("the image wasn't edited");
            return;
        }
        // Some app returns the edited image as new data.
        if (data != null) {
            log.i("result uri: %s", data.getData());
            try {
                openImage(MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData()));
            } catch (IOException e) {
                showToast("Cannot load new image");
                log.e("cannot load edited image", e);
            } catch (SecurityException e) {
                // TODO add storage read permission request and support it.
                log.e("Storage permission is required", e);
                showToast("Need storage permission");
            }
        } else { // Others overwrite the same
            log.i("loading temporary file; %s", _editedFile.getAbsolutePath());
            Bitmap bmp = BitmapFactory.decodeFile(_editedFile.getAbsolutePath());
            if (bmp == null) {
                log.e("cannot load the image on file %s", _editedFile.getAbsolutePath());
            } else {
                openImage(bmp);
            }
        }
    }

    public void openImage(Uri uri) throws IOException {
        log.i("opening image %s", uri);
        // https://stackoverflow.com/questions/3879992/how-to-get-bitmap-from-an-uri
        openImage(MediaStore.Images.Media.getBitmap(getContentResolver(), uri));
    }

    private void openImage(Bitmap img) {
        _myPagerAdapter.getPreviewFragment().setBitmap(img);
        _myPagerAdapter.switchToPreviewFragment();
    }

    private void showToast(final String msg) {
        final Context ctx = this;
        runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }


    private File getNewTempFile() throws IOException {
        // Directory defined in provider_paths.xml
        File cachePath = new File(getExternalCacheDir(), "shared_images");
        cachePath.mkdirs();
        for ( File f: cachePath.listFiles() ) {
            log.i("deleting old temporary file %s", f.getAbsolutePath());
            f.delete();
        }
        // Some apps (com.google.android.markup) seems to have a very aggressive cache, they don't
        // realize that the file changed, so we have to generate a new file name to be safe.
        return File.createTempFile("img_", ".png", cachePath);
    }

    private boolean editImage() {
        Bitmap bmp = _myPagerAdapter.getPreviewFragment().getBitmap();

        if (bmp == null) {
            log.w("nothing to edit");
            return false;
        }

        // https://codestringz.com/share-intent-for-a-bitmap-without-saving-a-file/
        // https://stackoverflow.com/questions/15699299/android-edit-image-intent


        try {
            _editedFile = getNewTempFile();
            log.i("writing temporary file on %s", _editedFile.getAbsolutePath());
            FileOutputStream fos = new FileOutputStream(_editedFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (IOException e) {
            log.e("cannot write temporary file", e);
            showToast("ERROR: writing temporary file");
            return true;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", _editedFile);

        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // com.google.android.markup gets the image from here.
        intent.setDataAndType(uri, "image/png");

        // Snapseed gets the image from here.
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        log.i("sending uri %s, intent %s", uri, intent);

        startActivityForResult(Intent.createChooser(intent, "Edit in"), REQUEST_EDIT_IMAGE);
        return true;
    }
}


