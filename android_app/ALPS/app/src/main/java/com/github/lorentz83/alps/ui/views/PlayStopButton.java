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


package com.github.lorentz83.alps.ui.views;

import android.widget.Button;

import androidx.annotation.NonNull;

import com.github.lorentz83.alps.R;

/**
 * Wraps a Button to expose a play stop behavior (and icon changes).
 */
public class PlayStopButton {
    private final Button _btn;
    private boolean _isRunning;
    private OnPlayStopButtonListener _onStop;
    private OnPlayStopButtonListener _onPlay;

    /**
     * Wraps the specified android button.
     *
     * @param btn the button to wrap.
     */
    public PlayStopButton(@NonNull Button btn) {
        _btn = btn;
        _isRunning = false;
        _btn.setOnClickListener(v -> {
            OnPlayStopButtonListener callback = _isRunning ? _onStop : _onPlay;
            if (callback == null || callback.onClick(this)) {
                _isRunning = !_isRunning;
                updatePlayStopButton();
            }
        });
        updatePlayStopButton();
    }

    private void updatePlayStopButton() {
        if (_isRunning) {
            _btn.setText(R.string.stop);
            _btn.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_media_pause, 0, 0, 0);
        } else {
            _btn.setText(R.string.play);
            _btn.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_media_play, 0, 0, 0);
        }
    }

    /**
     * Adds a listener to be called when the button is clicked and in the ready to play status.
     *
     * @param onPlay the callback.
     */
    public void setOnPlayListener(OnPlayStopButtonListener onPlay) {
        _onPlay = onPlay;
    }

    /**
     * Adds a listener to be called when the button is clicked and in the playing status (aka: when "stop" is displayed).
     */
    public void setOnStopListener(OnPlayStopButtonListener onStop) {
        _onStop = onStop;
    }

    /**
     * Moves the button in the ready to play status.
     */
    public void stopped() {
        _isRunning = false;
        updatePlayStopButton();
    }

    /**
     * Enables or disables the button.
     * @param enable
     */
    public void setEnabled(boolean enable) {
        _btn.setEnabled(enable);
    }
}

