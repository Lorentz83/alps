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
