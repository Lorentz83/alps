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


package com.github.lorentz83.alps.ui.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
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

    private static final int REQUEST_EDIT_IMAGE = 123;

    private Preferences _sharedPref;

    private Protocol _protocol;
    private Sender _sender;

    private Bitmap _fullSizeBitmap = null;
    private Bitmap _bitmap = null;
    private File _editedFile = null;
    private ImageView _preview;
    Button _uploadBtn;
    PlayStopButton _playStopBtn;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.content_preview, container, false);

        _sharedPref = Preferences.getInstance();

        _preview = root.findViewById(R.id.image_preview);

        _preview.setOnLongClickListener(v -> editImage());

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

        // TODO it would be nice to restore the last image used.

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

        // bilinear scaling down, nearest neighbor scaling up.
        // TODO this should probably be an option.
        boolean useBilinear = _fullSizeBitmap.getHeight() > h;

        _bitmap = Bitmap.createScaledBitmap(_fullSizeBitmap, w, h, useBilinear);

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        log.i("result %d, %d, %s", requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            log.i("the image wasn't edited");
            return;
        }
        // Some app returns the edited image as new data.
        if (data != null) {
            log.i("result uri: %s", data.getData());
            try {
                setBitmap(MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), data.getData()));
            } catch (IOException e) {
              showToast("Cannot load new image");
              log.e("cannot load edited image", e);
            } catch (SecurityException e) {
                log.e("Storage permission is required", e);
                showToast("Need storage permission");
            }
        } else { // Others overwrite the same
            log.i("loading temporary file; %s", _editedFile.getAbsolutePath());
            Bitmap bmp = BitmapFactory.decodeFile(_editedFile.getAbsolutePath());
            if (bmp == null) {
                log.e("cannot load the image on file %s", _editedFile.getAbsolutePath());
            } else {
                setBitmap(bmp);
            }
        }
    }

    private File getNewTempFile() throws IOException {
        // Directory defined in provider_paths.xml
        File cachePath = new File(getContext().getExternalCacheDir(), "shared_images");
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
        if (_fullSizeBitmap == null) {
            log.i("nothing to edit");
            return false;
        }

        // https://codestringz.com/share-intent-for-a-bitmap-without-saving-a-file/
        // https://stackoverflow.com/questions/15699299/android-edit-image-intent


        try {
            _editedFile = getNewTempFile();
            log.i("writing temporary file on %s", _editedFile.getAbsolutePath());
            FileOutputStream fos = new FileOutputStream(_editedFile);
            _fullSizeBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (IOException e) {
            log.e("cannot write temporary file", e);
            showToast("ERROR: writing temporary file");
            return true;
        }

        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", _editedFile);

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

