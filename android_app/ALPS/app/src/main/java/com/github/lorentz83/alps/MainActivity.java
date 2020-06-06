package com.github.lorentz83.alps;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import androidx.core.view.MenuCompat;
import androidx.viewpager.widget.ViewPager;

import com.github.lorentz83.alps.ui.ColorPicker;
import com.github.lorentz83.alps.ui.MyPagerAdapter;
import com.github.lorentz83.alps.utils.BluetoothHelper;
import com.github.lorentz83.alps.utils.CustomTextResult;
import com.github.lorentz83.alps.utils.LogUtility;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private final static LogUtility log = new LogUtility(MainActivity.class);

    private static final int REQUEST_OPEN_IMAGE = 40;
    private static final int REQUEST_CUSTOM_TEXT = 41;
    private static final int REQUEST_ENABLE_BT = 871;
    private static final int SELECT_DEVICE_REQUEST_CODE = 872;

    private ExecutorService _executor = Executors.newFixedThreadPool(2);
    private ColorPicker _colorPicker;

    private BluetoothHelper _btHelper;
    private BluetoothSocket _socket = null;
    private Preferences _sharedPref;
    private MyPagerAdapter _myPagerAdapter;
    private MenuItem _actionBt;

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

    private void writeText(String text, int backgroundColor, int textColor) {
        // https://stackoverflow.com/questions/8799290/convert-string-text-to-bitmap
        float textSize = 300;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        paint.setTextAlign(Paint.Align.LEFT);

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
        }
        if (_socket == null) {
            connectBluetooth();
        }
    }

    private void disconnectBluetooth() {
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
                runOnUiThread(() -> {
                    _actionBt.setIcon(R.drawable.ic_bt_disconnected);
                });
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
                    deviceToPair.createBond();
                    _sharedPref.setBluetoothAddress(deviceToPair.getAddress());

                    connectBluetooth(deviceToPair);
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
                    writeText(res.getText(), res.getBackgroundColor(), res.getForegroundColor());
                } else {
                    showToast("No text provided");
                }
                break;
            }
            default:
                log.i("onActivityResult unknown code %d, result: %d", requestCode, resultCode);
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

}


