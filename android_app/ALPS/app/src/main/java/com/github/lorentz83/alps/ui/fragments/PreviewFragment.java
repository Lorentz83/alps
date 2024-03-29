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


package com.github.lorentz83.alps.ui.fragments;

import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.github.lorentz83.alps.MainActivity;
import com.github.lorentz83.alps.Preferences;
import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.communication.Protocol;
import com.github.lorentz83.alps.communication.Sender;
import com.github.lorentz83.alps.communication.SenderCallbacks;
import com.github.lorentz83.alps.ui.views.MyChronometer;
import com.github.lorentz83.alps.ui.views.PlayStopButton;
import com.github.lorentz83.alps.utils.LogUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class PreviewFragment extends Fragment {
    private final static LogUtility log = new LogUtility(PreviewFragment.class);

    private final static String LAST_IMAGE_FILENAME = "last_image.png";

    private Preferences _sharedPref;

    private Protocol _protocol;
    private Sender _sender;

    private Bitmap _fullSizeBitmap = null;
    private Bitmap _bitmap = null;
    private ImageView _preview;
    Button _uploadBtn;
    PlayStopButton _playStopBtn;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restorePreviousImage();
    }

    @Override
    public void onPause(){
        super.onPause();
        storeCurrentImage();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.content_preview, container, false);

        _sharedPref = Preferences.getInstance();

        _preview = root.findViewById(R.id.image_preview);

        _playStopBtn = new PlayStopButton(root.findViewById(R.id.btn_play_stop));
        _playStopBtn.setOnPlayListener(v -> {
            boolean ok = buttonPlayClicked();
            if (ok) {
                _uploadBtn.setEnabled(false);
            }
            return ok;
        });
        _playStopBtn.setOnStopListener(v -> buttonStopClicked());

        _uploadBtn = root.findViewById(R.id.btn_upload);
        _uploadBtn.setOnClickListener(v -> {
            if (buttonUploadClicked()) {
                _playStopBtn.setEnabled(false);
                _uploadBtn.setEnabled(false);
            }
        });

        final ProgressBar progressBar = root.findViewById(R.id.progress_bar);
        final MyChronometer chronometer = new MyChronometer(root.findViewById(R.id.chronometer));
        final FragmentActivity ctx = getActivity();

        _protocol = new Protocol();
        _sender = new Sender(_protocol, new SenderCallbacks() {

            @Override
            public void progress(int percentage) {
                ctx.runOnUiThread(() -> progressBar.setProgress(percentage));
            }

            @Override
            public synchronized void start() {
                ctx.runOnUiThread(chronometer::start);
            }

            @Override
            public void onError(Exception e) {
                ctx.runOnUiThread(() -> {
                    _playStopBtn.setEnabled(true);
                    _uploadBtn.setEnabled(true);
                    if (e.getMessage().equals("Broken pipe")) {
                        // bt disconnected
                        showToast("Bluetooth disconnected");
                        // TODO this is pretty fragile.
                        ((MainActivity) ctx).disconnectBluetooth();
                    } else {
                        showToast(String.format("Connection error: %s", e.getMessage()));
                    }
                });
            }

            @Override
            public synchronized void done() {
                ctx.runOnUiThread(() -> {
                    _playStopBtn.stopped();
                    chronometer.stop();
                    _playStopBtn.setEnabled(true);
                    _uploadBtn.setEnabled(true);
                    new Timer().schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    ctx.runOnUiThread(() -> progressBar.setProgress(0));
                                }
                            },
                            750);
                });
            }
        });

        if (_fullSizeBitmap != null) {
            updatePreview();
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (_sender != null) {
            _sender.kill();
            _sender = null;
        }
    }

    /**
     * Sets the image to preview and send to the stick.
     *
     * @param img the bitmap to send.
     */
    public void setBitmap(@NonNull Bitmap img) {
        _fullSizeBitmap = img;
        updatePreview();
    }

    private @NonNull Bitmap blur(@NonNull Bitmap image, float radius) {
        Bitmap outputBitmap = image.copy(image.getConfig(), image.isMutable());
        if ( radius == 0 ) {
            return outputBitmap;
        }

        final RenderScript renderScript = RenderScript.create(getContext());
        Allocation tmpIn = Allocation.createFromBitmap(renderScript, image);
        Allocation tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap);

        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        theIntrinsic.setRadius(radius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        return outputBitmap;
    }

    /**
     * Updates the image preview.
     * <p>
     * This method also rescales the image.
     * Call it to force generating a new scaled image if the stick length setting changed.
     */
    public void updatePreview() {
        if (_fullSizeBitmap == null) {
            _preview.setImageDrawable(null);
            return;
        }

        int w = _fullSizeBitmap.getWidth();
        int h = _fullSizeBitmap.getHeight();

        int len = _sharedPref.getStickLength();
        float widthMultiplier = _sharedPref.getWidthMultiplier();
        w = (int) Math.round((double) len / h * w * widthMultiplier);
        h = len;

        boolean useBilinear = _sharedPref.getUseBilinearFilter();
        boolean antiAliasing = _sharedPref.getAntiAliasing();
        float blurRadius = 0f;
        if ( antiAliasing ) {
            float blurRadiusH = _fullSizeBitmap.getHeight() / (float)h;
            float blurRadiusW = _fullSizeBitmap.getWidth() / (float)w;
            blurRadius = (blurRadiusH + blurRadiusW) / 2;
        }

        log.i("use bilinear = %s, blur = %s, blur radius %f", useBilinear, antiAliasing, blurRadius);

        _bitmap = blur(_fullSizeBitmap, blurRadius);
        _bitmap = Bitmap.createScaledBitmap(_bitmap, w, h, useBilinear);

        BitmapDrawable myBitmapDrawable = new BitmapDrawable(getResources(), _bitmap);
        myBitmapDrawable.getPaint().setFilterBitmap(false);
        _preview.setImageDrawable(myBitmapDrawable);
    }

    /**
     * Callbeck for when the stop is clicked.
     *
     * @return always true.
     */
    public boolean buttonStopClicked() {
        log.i("stop");
        if (_sender == null) {
            showToast(getString(R.string.not_connected));
            return true;
        }
        _sender.stop();
        return true;
    }

    /**
     * Callback for when the play is clicked.
     *
     * @return true if the image is being sent.
     */
    public boolean buttonPlayClicked() {
        log.i("play");
        try {
            BluetoothSocket dev = _sharedPref.getConnectedBluetooth();
            if (dev == null) {
                showToast(getString(R.string.not_connected));
                return false;
            }
            _protocol.initializeConnection(dev);

            if (_bitmap == null) {
                showToast(getString(R.string.no_image));
                return false;
            }
            _sender.setExtraDelay(_sharedPref.getWaitTimeMs());
            _sender.setLoop(_sharedPref.getLoop());
            _sender.setBrightness(_sharedPref.getStickBrightness());
            _sender.sendBitmap(_bitmap);

        } catch (IOException e) {
            showToast(getString(R.string.bluetooth_error));
            log.w("play error", e);
            return false;
        }
        return true;
    }

    /**
     * Callback for when the upload is clicked.
     *
     * @return true if the image is being sent.
     */
    public boolean buttonUploadClicked() {
        log.i("Upload started");
        // TODO this is too similar to buttonPlayClicked
        try {
            BluetoothSocket dev = _sharedPref.getConnectedBluetooth();
            if (dev == null) {
                showToast(getString(R.string.not_connected));
                return false;
            }
            _protocol.initializeConnection(dev);

            if (_bitmap == null) {
                showToast(getString(R.string.no_image));
                return false;
            }
            _sender.uploadBitmap(_bitmap);

        } catch (IOException e) {
            showToast(getString(R.string.bluetooth_error));
            log.w("Upload error", e);
            return false;
        }
        return true;
    }

    private void showToast(final String msg) {
        final FragmentActivity ctx = getActivity();
        ctx.runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }

    private void storeCurrentImage() {
        File f = new File(getContext().getExternalCacheDir(), LAST_IMAGE_FILENAME);
        if ( _fullSizeBitmap == null ) {
            log.i("no image to store");
            f.delete();
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(f)) {
            _fullSizeBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            log.w("cannot store current image", e);
        }
    }

    private void restorePreviousImage() {
        File f = new File(getContext().getExternalCacheDir(), LAST_IMAGE_FILENAME);
        _fullSizeBitmap = BitmapFactory.decodeFile(f.getPath());
    }

    public Bitmap getBitmap() {
        return _fullSizeBitmap;
    }
}

