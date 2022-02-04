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


package com.github.lorentz83.alps.utils;

import java.io.Serializable;

public class CustomTextResult implements Serializable {
    public static final String CUSTOM_TEXT_RESULT = "CUSTOM_TEXT_RESULT";

    private String text;
    private int backgroundColor;
    private int foregroundColor;

    public CustomTextResult(String text, int backgroundColor, int foregroundColor) {
        this.text = text;
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
    }

    public String getText() {
        return text;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getForegroundColor() {
        return foregroundColor;
    }

    @Override
    public String toString() {
        return String.format("CustomTextResult{%s, %d, %d}", text, backgroundColor, foregroundColor);
    }
}
