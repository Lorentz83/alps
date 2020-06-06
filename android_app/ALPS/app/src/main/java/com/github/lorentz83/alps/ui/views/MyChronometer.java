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
