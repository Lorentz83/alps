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


package com.github.lorentz83.alps.ui.views;

import android.os.SystemClock;
import android.widget.Chronometer;

import androidx.annotation.NonNull;

/**
 * Extends an android chronometer to add 1/10th of second support.
 */
public class MyChronometer {
    private final Chronometer _chronometer;
    private long _start;

    /**
     * Wraps an android chronometer.
     *
     * @param c the chronometer to wrap.
     */
    public MyChronometer(@NonNull Chronometer c) {
        _chronometer = c;
        _chronometer.setFormat("%s.00");
        _chronometer.setText("00:00.00");
    }

    /**
     * Starts the chronometer.
     */
    public void start() {
        _start = System.currentTimeMillis();
        _chronometer.setBase(SystemClock.elapsedRealtime());
        _chronometer.start();
    }


    /**
     * Stops the chronometer.
     * <p>
     * It is safe to call it multiple times.
     */
    public void stop() {
        if (_start == 0) {
            return;
        }
        _chronometer.stop();

        long csec = (System.currentTimeMillis() - _start) / 10;

        long sec = csec / 100;
        csec = csec % 100;
        long min = sec / 60;
        sec = sec % 60;

        // You wouldn't run this more than 1 hour, right?

        _chronometer.setText(String.format("%02d:%02d.%02d", min, sec, csec));

        _start = 0;
    }
}
