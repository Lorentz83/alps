package com.github.lorentz83.alps.ui.fragments;

import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.github.lorentz83.alps.Preferences;
import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.communication.Protocol;
import com.github.lorentz83.alps.communication.Sender;
import com.github.lorentz83.alps.communication.SenderCallbacks;
import com.github.lorentz83.alps.ui.views.MyChronometer;
import com.github.lorentz83.alps.ui.views.PlayStopButton;
import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class PreviewFragment extends Fragment {
    private final static LogUtility log = new LogUtility(PreviewFragment.class);

    private Preferences _sharedPref;

    private Protocol _protocol;
    private Sender _sender;

    private Bitmap _fullSizeBitmap = null;
    private Bitmap _bitmap = null;
    private ImageView _preview;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.content_preview, container, false);

        _sharedPref = Preferences.getInstance();

        _preview = root.findViewById(R.id.image_preview);

        PlayStopButton playStop = new PlayStopButton(root.findViewById(R.id.btn_play_stop));
        playStop.setOnPlayListener(v -> buttonPlayClicked());
        playStop.setOnStopListener(v -> buttonStopClicked());

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
                ctx.runOnUiThread(() -> chronometer.start());
            }

            @Override
            public synchronized void done() {
                ctx.runOnUiThread(() -> {
                    playStop.stopped();
                    chronometer.stop();

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
     *
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
        w = (int) Math.round((double) len / h * w);
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
        }
        return true;
    }

    private void showToast(final String msg) {
        final FragmentActivity ctx = getActivity();
        ctx.runOnUiThread(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}

