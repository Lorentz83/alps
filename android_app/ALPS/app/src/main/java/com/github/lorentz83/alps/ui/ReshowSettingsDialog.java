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


package com.github.lorentz83.alps.ui;

import android.app.Activity;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.utils.LogUtility;

public class ReshowSettingsDialog {
    private final static LogUtility log = new LogUtility(ReshowSettingsDialog.class);

    private final Activity _ctx;
    private final Callback _callback;
    private AlertDialog _dialog;
    private final AlertDialog.Builder _builder;

    private Values _oldValues = new Values();

    SeekBar _brightness;
    NumberPicker _extraTimeoutPicker;
    Switch _loopSwitch;

    @FunctionalInterface
    public interface Callback {
        /**
         *
         * @param values the new value to send.
         * @return true if the values are applied.
         */
        boolean confirmed(Values values);
    }

    public static class Values {
        public final int delay;
        public final int brightness;
        public final boolean loop;

        Values() {
            delay = 0;
            brightness = 255;
            loop = false;
        }

        Values(int delay, int brightness, boolean loop){
            this.delay = delay;
            this.brightness = brightness;
            this.loop = loop;
        }
    }

    /**
     * Creates the reshow settings dialog.
     *
     * @param ctx the activity which is context of this dialog.
     */
    public ReshowSettingsDialog(@NonNull Activity ctx, @NonNull Callback callback) {
        _ctx = ctx;
        _callback = callback;

        final View layout = ctx.getLayoutInflater().inflate(R.layout.dialog_reshow_settings, null);

        _brightness = layout.findViewById(R.id.seek_bar_red);
        _brightness.setMin(1);
        _brightness.setMax(255);

        _loopSwitch = layout.findViewById(R.id.loop_switch);

        _extraTimeoutPicker = layout.findViewById(R.id.extra_timeout);
        _extraTimeoutPicker.setFormatter(value -> (value == 0) ? "0 ms" : String.format("%d0 ms", value));
        _extraTimeoutPicker.setMinValue(0);
        _extraTimeoutPicker.setMaxValue(20);

        _builder = new AlertDialog.Builder(ctx);
        _builder.setTitle(R.string.reshow_settings_title)
                .setView(layout)
                .setPositiveButton("ok", (dialog, id) -> {
                    Values values = new Values(
                            _extraTimeoutPicker.getValue() * 10,
                            _brightness.getProgress(),
                            _loopSwitch.isChecked()
                    );
                    if (_callback.confirmed(values)) {
                        _oldValues = values;
                    }
                })
                .setNegativeButton("cancel", (dialog, id) -> dialog.cancel());
    }

    /**
     * Shows the dialog.
     */
    public void show() {
        if (_dialog == null) {
            _dialog = _builder.create();
        }
        if (_dialog.isShowing()) {
            log.w("dialog already shown");
            return;
        }

        _brightness.setProgress(_oldValues.brightness);
        _loopSwitch.setChecked(_oldValues.loop);
        _extraTimeoutPicker.setValue(_oldValues.delay);

        _dialog.show();
    }



}
