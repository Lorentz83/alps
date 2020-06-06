package com.github.lorentz83.alps.ui.views;

import androidx.annotation.NonNull;

/**
 * A callback for when a PlayStopButton is clicked.
 */
public interface OnPlayStopButtonListener {
    /**
     * Called when a PlayStopButton is clicked.
     *
     * @param btn the button clicked.
     * @return false if the button shouldn't change state.
     */
    boolean onClick(@NonNull PlayStopButton btn);
}
