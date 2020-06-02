package com.github.lorentz83.alps.utils;

import android.util.Log;

/**
 * Created by Lorenzo on 3/23/15.
 */
public class LogUtility {
    private final String _tag;

    public LogUtility(String tag) {
        _tag = tag;
    }

    public LogUtility(Class<?> clazz) {
        _tag = clazz.getName();
    }

    public int v(String msg) {
        return Log.v(_tag, msg);
    }

    public int v(String msg, Throwable tr) {
        return Log.v(_tag, msg, tr);
    }

    public int d(String msg) {
        return Log.d(_tag, msg);
    }

    public int d(String msg, Throwable tr) {
        return Log.d(_tag, msg, tr);
    }

    public int i(String msg) {
        return Log.i(_tag, msg);
    }

    public int i(String msg, Throwable tr) {
        return Log.i(_tag, msg, tr);
    }

    public int i(String format, Object... args) {
        return i(String.format(format, args));
    }

    public int w(String msg) {
        return Log.w(_tag, msg);
    }

    public int w(String format, Object... args) {
        return w(String.format(format, args));
    }

    public int w(String msg, Throwable tr) {
        return Log.w(_tag, msg, tr);
    }

    public int e(String msg) {
        return Log.e(_tag, msg);
    }

    public int e(String msg, Throwable tr) {
        return Log.e(_tag, msg, tr);
    }

    public int wtf(String msg) {
        return Log.wtf(_tag, msg);
    }

    public int wtf(String msg, Throwable tr) {
        return Log.wtf(_tag, msg, tr);
    }

}
