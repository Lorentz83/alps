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

import androidx.appcompat.app.AlertDialog;

import com.github.lorentz83.alps.R;

public class AboutWindow {

    public static void show(Activity ctx) {
        new AlertDialog.Builder(ctx)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_text)
                .setPositiveButton("ok", null)
                .show();
    }
}
