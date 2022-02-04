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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.lorentz83.alps.Preferences;
import com.github.lorentz83.alps.R;

// TODO: check the library androidx.preference.

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.content_settings, container, false);

        final Preferences _sharedPref = Preferences.getInstance();

        NumberPicker extraTimeoutPicker = root.findViewById(R.id.extra_timeout);
        extraTimeoutPicker.setFormatter(value -> (value == 0) ? "0 ms" : String.format("%d0 ms", value));
        extraTimeoutPicker.setMinValue(0);
        extraTimeoutPicker.setMaxValue(20);
        extraTimeoutPicker.setValue(_sharedPref.getWaitTimeMs() / 10);
        extraTimeoutPicker.setOnValueChangedListener((picker, oldVal, newVal) -> _sharedPref.setWaitTimeMs(newVal * 10));

        Switch loopSwitch = root.findViewById(R.id.loop_switch);
        loopSwitch.setChecked(_sharedPref.getLoop());
        loopSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> _sharedPref.setLoop(isChecked));

        EditText pixNum = root.findViewById(R.id.num_of_pixels);
        pixNum.setText(Integer.toString(_sharedPref.getStickLength()));
        pixNum.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int val;
                try {
                    val = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    // If the user deletes all the text, we replace it with 1.
                    val = 1; // TODO think for a better UX and better validation too.
                }
                _sharedPref.setStickLength(val);
            }
        });

        SeekBar brightness = root.findViewById(R.id.brightness);
        TextView brightnessLabel = root.findViewById(R.id.brightness_label);
        brightness.setMin(1);
        brightness.setMax(100);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessLabel.setText(String.format("%s: % 3d%%", getString(R.string.brightness), progress));
                _sharedPref.setStickBrightness((float) (progress / 100.0));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        brightness.setProgress(Math.round(_sharedPref.getStickBrightness() * 100));

        SeekBar width = root.findViewById(R.id.width);
        TextView widthLabel = root.findViewById(R.id.width_label);
        width.setMin(20);
        width.setMax(100);
        width.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                widthLabel.setText(String.format("%s: % 3d%%", getString(R.string.width), progress));
                _sharedPref.setWidthMultiplier((float) (progress / 100.0));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        width.setProgress(Math.round(_sharedPref.getWidthMultiplier() * 100));

        return root;
    }
}
