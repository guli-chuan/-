package com.hejun.floatingclock;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;

/** Owns the floating window. Everything is created in code, nothing is inflated. */
public class ClockService extends Service {

    static final String ACTION_STOP = "com.hejun.floatingclock.STOP";
    private static final String CHANNEL_ID = "floating_clock";
    private static final int NOTIFICATION_ID = 73;
    private static final int SIZE_ANIM_MS = 260;

    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    private WindowManager windowManager;
    private ClockView clockView;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int screenWidth;
    private int screenHeight;
    private int statusBarHeight;
    private int navBarHeight;
    private int fullWidth;
    private int fullHeight;
    private int miniWidth;
    private int miniHeight;

    private float downRawX;
    private float downRawY;
    private int dragStartX;
    private int dragStartY;
    private long downTime;
    private boolean dragging;
    private boolean longPressFired;
    private int pendingAction;
    private ValueAnimator sizeAnimator;
    private ValueAnimator snapAnimator;
    private int touchSlop;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging && clockView != null) {
                longPressFired = true;
                // Actions need the full card width, so a long press on the compact
                // pill expands it first. This also avoids a hidden actions state.
                if (clockView.isCompact()) {
                    Prefs.setCompact(ClockService.this, false);
                    clockView.setCompact(false, true);
                    animateWindowSize(false);
                }
                clockView.setActionsVisible(true);
                clockView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (clockView == null) {
                                return;
                            }
                            if ("accent".equals(key)) {
                                clockView.setAccent(Prefs.accent(ClockService.this));
                            } else if ("hundredths".equals(key)) {
                                clockView.setHundredths(Prefs.hundredths(ClockService.this));
                            }
                        }
                    });
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Prefs.setRunning(this, true);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.store(this);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        touchSlop = Math.max(dp(6), ViewConfiguration.get(this).getScaledTouchSlop());

        createNotificationChannel();
        if (!startAsForeground()) {
            return;
        }

        updateMetrics();
        fullWidth = dp(244);
        fullHeight = dp(108);
        miniWidth = dp(148);
        miniHeight = dp(52);

        clockView = new ClockView(this);
        clockView.setAccent(Prefs.accent(this));
        clockView.setHundredths(Prefs.hundredths(this));
        boolean compact = Prefs.compact(this);
        clockView.setCompact(compact, false);
        clockView.setActionListener(new ClockView.ActionListener() {
            @Override
            public void onOpenSettings() {
                openSettings();
            }

            @Override
            public void onCloseClock() {
                stopSelf();
            }
        });

        params = new WindowManager.LayoutParams(
                compact ? miniWidth : fullWidth,
                compact ? miniHeight : fullHeight,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("FloatingClock");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        int width = params.width;
        int height = params.height;
        int savedX = Prefs.posX(this);
        int savedY = Prefs.posY(this);
        if (savedX < 0 || savedY < 0) {
            params.x = screenWidth - width - dp(14);
            params.y = statusBarHeight + dp(58);
        } else {
            params.x = savedX;
            params.y = savedY;
        }
        clampPosition();

        clockView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleTouch(view, event);
            }
        });

        try {
            windowManager.addView(clockView, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private boolean startAsForeground() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Exception e) {
            // If the platform rejects the foreground service there is no point in showing an
            // unmanaged overlay window.
            stopSelf();
            return false;
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ClockService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_clock)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_stat_clock, "停止悬浮", stopPending);
        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_desc));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (clockView == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                dragStartX = params.x;
                dragStartY = params.y;
                downTime = SystemClock.uptimeMillis();
                dragging = false;
                longPressFired = false;
                pendingAction = 0;
                if (clockView.isActionsVisible()) {
                    pendingAction = clockView.hitAction(event.getX(), event.getY());
                    if (pendingAction != 0) {
                        return true;
                    }
                }
                handler.postDelayed(longPressRunnable, 520);
                view.setPressed(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                    longPressFired = false;
                    handler.removeCallbacks(longPressRunnable);
                    clockView.setActionsVisible(false);
                }
                if (dragging) {
                    params.x = dragStartX + Math.round(dx);
                    params.y = dragStartY + Math.round(dy);
                    clampPosition();
                    try {
                        windowManager.updateViewLayout(clockView, params);
                    } catch (Exception ignored) {
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                view.setPressed(false);
                if (pendingAction != 0) {
                    if (pendingAction == 1) {
                        openSettings();
                    } else {
                        stopSelf();
                    }
                    pendingAction = 0;
                    return true;
                }
                if (dragging) {
                    snapToEdge();
                } else if (!longPressFired) {
                    if (clockView.isActionsVisible()) {
                        clockView.setActionsVisible(false);
                    } else if (SystemClock.uptimeMillis() - downTime < 500) {
                        toggleCompact();
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                view.setPressed(false);
                if (dragging) {
                    snapToEdge();
                }
                dragging = false;
                pendingAction = 0;
                return true;
            default:
                return false;
        }
    }

    private void toggleCompact() {
        if (clockView == null) {
            return;
        }
        boolean compact = !clockView.isCompact();
        Prefs.setCompact(this, compact);
        clockView.setCompact(compact, true);
        animateWindowSize(compact);
    }

    private void animateWindowSize(boolean compact) {
        cancelAnimator(sizeAnimator);
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        int fromWidth = params.width;
        int fromHeight = params.height;
        int toWidth = compact ? miniWidth : fullWidth;
        int toHeight = compact ? miniHeight : fullHeight;
        boolean rightSide = params.x + fromWidth * 0.5f > screenWidth * 0.5f;
        int rightEdge = params.x + fromWidth;
        int leftEdge = params.x;

        sizeAnimator = ValueAnimator.ofFloat(0f, 1f);
        sizeAnimator.setDuration(SIZE_ANIM_MS);
        sizeAnimator.setInterpolator(new PathInterpolator(0.22f, 0.8f, 0.2f, 1f));
        sizeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float f = (float) animation.getAnimatedValue();
                int w = Math.round(fromWidth + (toWidth - fromWidth) * f);
                int h = Math.round(fromHeight + (toHeight - fromHeight) * f);
                params.width = w;
                params.height = h;
                params.x = rightSide ? rightEdge - w : leftEdge;
                clampPosition();
                try {
                    windowManager.updateViewLayout(clockView, params);
                } catch (Exception ignored) {
                }
            }
        });
        sizeAnimator.start();
    }

    private void snapToEdge() {
        dragging = false;
        int targetX = params.x + params.width * 0.5f < screenWidth * 0.5f
                ? dp(10) : screenWidth - params.width - dp(10);
        cancelAnimator(snapAnimator);
        snapAnimator = ValueAnimator.ofInt(params.x, targetX);
        snapAnimator.setDuration(280);
        snapAnimator.setInterpolator(new PathInterpolator(0.18f, 0.9f, 0.22f, 1f));
        snapAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                params.x = (int) animation.getAnimatedValue();
                clampPosition();
                try {
                    windowManager.updateViewLayout(clockView, params);
                } catch (Exception ignored) {
                }
            }
        });
        snapAnimator.start();
        Prefs.setPosition(this, targetX, params.y);
    }

    private void cancelAnimator(ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    private void clampPosition() {
        if (params == null) {
            return;
        }
        int minY = statusBarHeight + dp(2);
        int maxY = screenHeight - navBarHeight - params.height - dp(6);
        if (maxY < minY) {
            maxY = minY;
        }
        params.x = Math.max(0, Math.min(screenWidth - params.width, params.x));
        params.y = Math.max(minY, Math.min(maxY, params.y));
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            clockView.setActionsVisible(false);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void updateMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        statusBarHeight = systemDimen("status_bar_height");
        navBarHeight = systemDimen("navigation_bar_height");
    }

    private int systemDimen(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateMetrics();
        if (params != null && clockView != null) {
            params.width = clockView.isCompact() ? miniWidth : fullWidth;
            params.height = clockView.isCompact() ? miniHeight : fullHeight;
            clampPosition();
            try {
                windowManager.updateViewLayout(clockView, params);
            } catch (Exception ignored) {
            }
            Prefs.setPosition(this, params.x, params.y);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cancelAnimator(sizeAnimator);
        cancelAnimator(snapAnimator);
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        if (clockView != null && windowManager != null) {
            try {
                windowManager.removeView(clockView);
            } catch (Exception ignored) {
            }
            clockView = null;
        }
        running = false;
        Prefs.setRunning(this, false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
