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


package com.github.lorentz83.alps.communication;

/**
 * Defines the callbacks handled by the Sender class.
 * <p>
 * This is useful to monitor the status of the image transfer.
 */
public interface SenderCallbacks {

    /**
     * Called to update on the progress of the image sent.
     * <p>
     * Note: there are no guarantees on how frequently this function will be called.
     *
     * @param percentage between 0 and 100.
     */
    void progress(int percentage);

    /**
     * Called when sending the image is completed.
     * <p>
     * Note: it may be called multiple times in case of communication error.
     */
    void done();

    /**
     * Called when the image is started sending.
     */
    void start();

    /**
     * Called when an error happen.
     * @param e the thrown exception.
     */
    void onError(Exception e);
}
