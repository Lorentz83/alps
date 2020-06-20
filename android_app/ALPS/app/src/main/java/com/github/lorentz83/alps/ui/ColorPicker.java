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
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;

import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.utils.LogUtility;

import java.util.function.Consumer;

/**
 * Implements a color picker dialog.
 */
public class ColorPicker {
    private final static LogUtility log = new LogUtility(ColorPicker.class);

    private final Activity _ctx;
    private AlertDialog _dialog;
    private final AlertDialog.Builder _builder;

    private Consumer<Integer> _okListener = null;
    private Runnable _cancelListener = null;

    private final SeekBar _seekRed;
    private final SeekBar _seekGreen;
    private final SeekBar _seekBlue;
    private final EditText _htmlCode;
    private final View _colorPreview;

    private int _color;

    /**
     * Creates the color picker dialog.
     *
     * @param ctx the activity which is context of this dialog.
     */
    public ColorPicker(Activity ctx) {
        _ctx = ctx;

        final View layout = ctx.getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        _seekRed = layout.findViewById(R.id.seek_bar_red);
        _seekGreen = layout.findViewById(R.id.seek_bar_green);
        _seekBlue = layout.findViewById(R.id.seek_bar_blue);
        _htmlCode = layout.findViewById(R.id.html_code_text);
        _colorPreview = layout.findViewById(R.id.color_preview);

        SeekBar.OnSeekBarChangeListener sl = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser)
                    updateFromSliders();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        _seekRed.setOnSeekBarChangeListener(sl);
        _seekGreen.setOnSeekBarChangeListener(sl);
        _seekBlue.setOnSeekBarChangeListener(sl);

        _htmlCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable text) {
                updateFromText(text.toString());
            }
        });

        updateFromSliders();

        _builder = new AlertDialog.Builder(ctx);
        _builder.setTitle(R.string.color_picker_title)
                .setView(layout)
                .setPositiveButton("ok", (dialog, id) -> {
                    if (_okListener != null)
                        _okListener.accept(_color);
                })
                .setNegativeButton("cancel", (dialog, id) -> {
                    if (_cancelListener != null)
                        _cancelListener.run();
                    dialog.cancel();
                });
        // Cannot create the dialog now because if called during onCreate it messes up with the theme.
    }

    /**
     * Shows the color picker dialog.
     *
     * @param okListener the callback to execute on ok.
     */
    public void show(Consumer<Integer> okListener) {
        show(okListener, null, Color.BLACK);
    }


    /**
     * Shows the color picker dialog.
     *
     * @param okListener   the callback to execute on ok. It gets the color encoded as Color.
     * @param initialColor the initial color to display.
     */
    public void show(Consumer<Integer> okListener, int initialColor) {
        show(okListener, null, initialColor);
    }

    /**
     * Shows the color picker dialog.
     *
     * @param okListener     the callback to execute on ok. It gets the color encoded as Color.
     * @param cancelListener the callback to execute on cancel.
     * @param initialColor   the initial color to display.
     */
    public void show(Consumer<Integer> okListener, Runnable cancelListener, int initialColor) {
        if (_dialog == null) {
            _dialog = _builder.create();
        }
        if (_dialog.isShowing()) {
            log.w("color picker is already shown");
            return;
        }
        _okListener = okListener;
        _cancelListener = cancelListener;
        updateFromText(colorToHex(initialColor));

        _dialog.show();
    }

    /**
     * Updates the color preview and the color hex text from the sliders' value.
     */
    private void updateFromSliders() {
        int red = _seekRed.getProgress();
        int green = _seekGreen.getProgress();
        int blue = _seekBlue.getProgress();

        _color = Color.rgb(red, green, blue);

        _colorPreview.setBackgroundColor(_color);
        _htmlCode.setText(colorToHex(_color));
    }

    private static String colorToHex(int _color) {
        return String.format("#%02X%02X%02X", Color.red(_color), Color.green(_color), Color.blue(_color));
    }

    /**
     * Updates the color preview and the sliders from the text provided.
     * To be called from the hex text onchange callback.
     * It handles invalid values changing the hex input field color.
     */
    private void updateFromText(String s) {
        try {
            if (s.length() < 1)
                return; // otherwise parseColor throws.

            // short format, android doesn't support it.
            if (s.length() == 4 && s.charAt(0) == '#') {
                StringBuilder sb = new StringBuilder("#");
                sb.append(s.charAt(1)).append(s.charAt(1));
                sb.append(s.charAt(2)).append(s.charAt(2));
                sb.append(s.charAt(3)).append(s.charAt(3));
                s = sb.toString();
            }

            _color = Color.parseColor(s);

            _colorPreview.setBackgroundColor(_color);

            _seekRed.setProgress(Color.red(_color));
            _seekGreen.setProgress(Color.green(_color));
            _seekBlue.setProgress(Color.blue(_color));

            _htmlCode.setTextColor(_ctx.getResources().getColor(R.color.design_default_color_on_primary, _ctx.getTheme()));
        } catch (IllegalArgumentException e) {
            _htmlCode.setTextColor(_ctx.getResources().getColor(R.color.design_default_color_error, _ctx.getTheme()));
        }
    }
}
