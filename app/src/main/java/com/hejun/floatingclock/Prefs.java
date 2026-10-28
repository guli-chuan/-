package com.hejun.floatingclock;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, dependency-free preference store shared by the activity and the overlay service. */
final class Prefs {

    static final int DEFAULT_ACCENT = 0xFF63F5C8;
    static final int[] ACCENTS = new int[]{
            0xFF63F5C8, // 薄荷
            0xFF6CC8FF, // 冰蓝
            0xFFFFB86B, // 蜜橘
            0xFFFF9DBB  // 樱粉
    };
    static final String[] ACCENT_NAMES = new String[]{"薄荷", "冰蓝", "蜜橘", "樱粉"};

    private static final String FILE = "floating_clock";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_HUNDREDTHS = "hundredths";
    private static final String KEY_COMPACT = "compact";
    private static final String KEY_X = "pos_x";
    private static final String KEY_Y = "pos_y";
    private static final String KEY_RUNNING = "running";

    private Prefs() {
    }

    static SharedPreferences store(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int accent(Context context) {
        return store(context).getInt(KEY_ACCENT, DEFAULT_ACCENT);
    }

    static void setAccent(Context context, int color) {
        store(context).edit().putInt(KEY_ACCENT, color).apply();
    }

    static boolean hundredths(Context context) {
        return store(context).getBoolean(KEY_HUNDREDTHS, false);
    }

    static void setHundredths(Context context, boolean value) {
        store(context).edit().putBoolean(KEY_HUNDREDTHS, value).apply();
    }

    static boolean compact(Context context) {
        return store(context).getBoolean(KEY_COMPACT, false);
    }

    static void setCompact(Context context, boolean value) {
        store(context).edit().putBoolean(KEY_COMPACT, value).apply();
    }

    static int posX(Context context) {
        return store(context).getInt(KEY_X, -1);
    }

    static int posY(Context context) {
        return store(context).getInt(KEY_Y, -1);
    }

    static void setPosition(Context context, int x, int y) {
        store(context).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply();
    }

    static boolean running(Context context) {
        return store(context).getBoolean(KEY_RUNNING, false);
    }

    static void setRunning(Context context, boolean running) {
        store(context).edit().putBoolean(KEY_RUNNING, running).apply();
    }
}
