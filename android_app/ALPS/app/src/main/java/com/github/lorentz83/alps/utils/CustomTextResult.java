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
