package com.hejun.floatingclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.Calendar;
import java.util.Locale;

/**
 * The whole clock face is drawn by hand: no XML, no image assets, no third-party UI kit.
 * It stays deliberately light-weight because it is repainted above other apps.
 */
public class ClockView extends View {

    public interface ActionListener {
        void onOpenSettings();

        void onCloseClock();
    }

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final int FRAME_INTERVAL_MS = 32; // ~30 fps is plenty for a clock

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint secondPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path cardPath = new Path();
    private final RectF cardRect = new RectF();
    private final RectF settingsRect = new RectF();
    private final RectF closeRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideActions = new Runnable() {
        @Override
        public void run() {
            setActionsVisible(false);
        }
    };

    private int accent = Prefs.DEFAULT_ACCENT;
    private boolean compact;
    private boolean hundredths;
    private boolean actionsVisible;
    private float compactProgress;
    private ValueAnimator compactAnimator;
    private ActionListener actionListener;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!loopRunning) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastInvalidate >= FRAME_INTERVAL_MS) {
                lastInvalidate = now;
                invalidate();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private boolean loopRunning;
    private boolean screenOn = true;
    private long lastInvalidate;

    private final Calendar calendar = Calendar.getInstance();
    private final Typeface boldTypeface = Typeface.create("sans-serif", Typeface.BOLD);

    public ClockView(Context context) {
        this(context, null);
    }

    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        Typeface timeface = boldTypeface;
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        timePaint.setTypeface(timeface);
        timePaint.setColor(Color.WHITE);
        timePaint.setTextAlign(Paint.Align.LEFT);
        secondPaint.setTypeface(timeface);
        secondPaint.setTextAlign(Paint.Align.LEFT);
        datePaint.setTypeface(medium);
        datePaint.setColor(0xA8FFFFFF);
        datePaint.setTextAlign(Paint.Align.LEFT);
        datePaint.setLetterSpacing(0.06f);
        actionTextPaint.setTypeface(medium);
        actionTextPaint.setTextAlign(Paint.Align.CENTER);

        progressTrackPaint.setStyle(Paint.Style.STROKE);
        progressTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        progressTrackPaint.setColor(0x24FFFFFF);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        borderPaint.setStyle(Paint.Style.STROKE);
        actionPaint.setColor(0x26FFFFFF);
        actionTextPaint.setColor(0xE6FFFFFF);
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    public void setAccent(int color) {
        this.accent = color;
        invalidate();
    }

    public boolean isCompact() {
        return compact;
    }

    public void setCompact(boolean compact, boolean animate) {
        if (this.compact == compact && !animate) {
            compactProgress = compact ? 1f : 0f;
            invalidate();
            return;
        }
        this.compact = compact;
        if (compactAnimator != null) {
            compactAnimator.cancel();
        }
        float target = compact ? 1f : 0f;
        if (!animate) {
            compactProgress = target;
            invalidate();
            return;
        }
        compactAnimator = ValueAnimator.ofFloat(compactProgress, target);
        compactAnimator.setDuration(260);
        compactAnimator.setInterpolator(new PathInterpolator(0.22f, 0.8f, 0.2f, 1f));
        compactAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                compactProgress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        compactAnimator.start();
    }

    public void setHundredths(boolean value) {
        hundredths = value;
        invalidate();
    }

    public void setActionsVisible(boolean visible) {
        if (actionsVisible == visible) {
            return;
        }
        actionsVisible = visible;
        handler.removeCallbacks(hideActions);
        if (visible) {
            handler.postDelayed(hideActions, 4200);
        }
        invalidate();
    }

    public boolean isActionsVisible() {
        return actionsVisible;
    }

    /** @return 1 for settings, 2 for close, 0 for no action hit. */
    public int hitAction(float x, float y) {
        if (!actionsVisible || compactProgress > 0.25f) {
            return 0;
        }
        layoutActionRects(getWidth(), getHeight());
        if (settingsRect.contains(x, y)) {
            return 1;
        }
        if (closeRect.contains(x, y)) {
            return 2;
        }
        return 0;
    }

    private void layoutActionRects(float w, float h) {
        float scale = clamp(Math.min(w / dp(244f), h / dp(108f)), 0.82f, 1.45f);
        float top = dp(9f);
        float height = dp(24f);
        settingsRect.set(dp(11f), top, dp(58f), top + height);
        closeRect.set(w - dp(58f), top, w - dp(11f), top + height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        calendar.setTimeInMillis(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        int millis = calendar.get(Calendar.MILLISECOND);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int weekday = calendar.get(Calendar.DAY_OF_WEEK) - 1;

        float p = compactProgress;
        float radius = dp(26f);
        float cardW = Math.min(w, dp(300f));
        float left = (w - cardW) * 0.5f;
        cardRect.set(left, 0f, left + cardW, h);

        cardPath.reset();
        cardPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);

        // Deep glass body.
        cardPaint.setShader(new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                new int[]{0xF0181E28, 0xF00A0D13}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint);
        cardPaint.setShader(null);

        // Slow moving accent light, clipped inside the card.
        canvas.save();
        canvas.clipPath(cardPath);
        drawAurora(canvas, cardRect, now);
        canvas.restore();

        // Hairline border with a brighter top edge.
        borderPaint.setStrokeWidth(dp(1f));
        borderPaint.setShader(new LinearGradient(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                new int[]{0x45FFFFFF, 0x0CFFFFFF, 0x18FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        RectF border = new RectF(cardRect);
        border.inset(dp(0.7f), dp(0.7f));
        canvas.drawRoundRect(border, radius, radius, borderPaint);
        borderPaint.setShader(null);

        float scale = clamp(Math.min(cardW / dp(244f), h / dp(108f)), 0.84f, 1.45f);

        if (p < 0.999f) {
            drawFullFace(canvas, cardW, h, scale, hour, minute, second, millis, month, day, weekday, now, 1f - p);
        }
        if (p > 0.001f) {
            drawCompactFace(canvas, cardW, h, p, hour, minute, second, millis);
        }
        drawProgress(canvas, cardW, h, scale, second, millis, Math.max(1f - p, p * 0.75f));
        if (actionsVisible && p < 0.2f) {
            drawActions(canvas, cardW, h, scale);
        }
    }

    private void drawAurora(Canvas canvas, RectF rect, long now) {
        float t = now * 0.001f;
        float w = rect.width();
        float h = rect.height();
        float ax = rect.left + w * (0.80f + 0.05f * (float) Math.sin(t * 0.55f));
        float ay = rect.top + h * (0.20f + 0.06f * (float) Math.cos(t * 0.43f));
        float radius = Math.max(w, h) * 0.72f;
        glowPaint.setShader(new RadialGradient(ax, ay, radius,
                withAlpha(accent, 42), withAlpha(accent, 0), Shader.TileMode.CLAMP));
        canvas.drawCircle(ax, ay, radius, glowPaint);

        float bx = rect.left + w * (0.12f + 0.045f * (float) Math.cos(t * 0.31f));
        float by = rect.top + h * (0.94f + 0.03f * (float) Math.sin(t * 0.49f));
        glowPaint.setShader(new RadialGradient(bx, by, radius * 0.62f,
                withAlpha(accent, 20), withAlpha(accent, 0), Shader.TileMode.CLAMP));
        canvas.drawCircle(bx, by, radius * 0.62f, glowPaint);
        glowPaint.setShader(null);
    }

    private void drawFullFace(Canvas canvas, float w, float h, float scale,
                              int hour, int minute, int second, int millis,
                              int month, int day, int weekday, long now, float alpha) {
        int a = (int) (255 * clamp(alpha, 0f, 1f));
        float dateCenterY = h * 0.245f;
        float timeCenterY = h * 0.605f;
        float dateSize = sp(10.8f) * scale;
        float mainSize = sp(37f) * scale;
        float secSize = sp(23f) * scale;
        float smallSize = sp(13.5f) * scale;

        String date = String.format(Locale.CHINA, "%d月%d日  %s", month, day, WEEKDAYS[weekday]);
        datePaint.setTextSize(dateSize);
        datePaint.setColor(0xA8FFFFFF);
        datePaint.setAlpha(a);
        float dateWidth = datePaint.measureText(date);
        float dateX = (w - dateWidth) * 0.5f;
        float dateBaseline = centerBaseline(datePaint, dateCenterY);
        canvas.drawText(date, dateX, dateBaseline, datePaint);

        // Pulsing live dot to the left of the date.
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 480.0);
        dotPaint.setColor(withAlpha(accent, (int) ((130 + 110 * pulse) * alpha)));
        canvas.drawCircle(dateX - dp(9f) * scale, dateCenterY, dp(2.1f) * scale, dotPaint);
        dotPaint.setColor(withAlpha(accent, (int) (28 * alpha)));
        canvas.drawCircle(dateX - dp(9f) * scale, dateCenterY, dp(5.5f) * scale, dotPaint);

        String hhmm = String.format(Locale.US, "%02d:%02d", hour, minute);
        String ss = String.format(Locale.US, "%02d", second);
        String cs = String.format(Locale.US, ".%02d", millis / 10);

        timePaint.setTextSize(mainSize);
        timePaint.setColor(0xFFFFFFFF);
        timePaint.setAlpha(a);
        secondPaint.setTextSize(secSize);
        secondPaint.setColor(accent);
        secondPaint.setAlpha(a);
        secondPaint.setTypeface(boldTypeface);

        float hhmmWidth = timePaint.measureText(hhmm);
        float ssWidth = secondPaint.measureText(ss);
        float gap = dp(3.2f) * scale;
        float csWidth = 0f;
        if (hundredths) {
            secondPaint.setTextSize(smallSize);
            secondPaint.setColor(withAlpha(accent, (int) (175 * alpha)));
            csWidth = dp(1.5f) * scale + secondPaint.measureText(cs);
        }
        float total = hhmmWidth + gap + ssWidth + csWidth;
        float startX = (w - total) * 0.5f;
        float base = centerBaseline(timePaint, timeCenterY);

        canvas.drawText(hhmm, startX, base, timePaint);

        // Little "tick" pop on the seconds every time it changes.
        float tick = 1f + 0.055f * Math.max(0f, 1f - (millis / 170f));
        float secCenterX = startX + hhmmWidth + gap + ssWidth * 0.5f;
        canvas.save();
        canvas.scale(tick, tick, secCenterX, base);
        secondPaint.setTextSize(secSize);
        secondPaint.setColor(accent);
        secondPaint.setAlpha(a);
        canvas.drawText(ss, startX + hhmmWidth + gap, base, secondPaint);
        canvas.restore();

        if (hundredths) {
            secondPaint.setTextSize(smallSize);
            secondPaint.setColor(withAlpha(accent, (int) (205 * alpha)));
            canvas.drawText(cs, startX + hhmmWidth + gap + ssWidth, base, secondPaint);
        }
    }

    private void drawCompactFace(Canvas canvas, float w, float h, float p,
                                 int hour, int minute, int second, int millis) {
        float scale = clamp(Math.min(w / dp(148f), h / dp(52f)), 0.80f, 1.6f);
        float size = sp(21.5f) * scale;
        String hhmm = String.format(Locale.US, "%02d:%02d:", hour, minute);
        String ss = String.format(Locale.US, "%02d", second);
        int a = (int) (255 * clamp(p, 0f, 1f));

        timePaint.setTextSize(size);
        timePaint.setColor(0xFFFFFFFF);
        timePaint.setAlpha(a);
        secondPaint.setTextSize(size);
        secondPaint.setColor(accent);
        secondPaint.setAlpha(a);
        secondPaint.setTypeface(boldTypeface);

        float mainWidth = timePaint.measureText(hhmm);
        float secWidth = secondPaint.measureText(ss);
        float startX = (w - mainWidth - secWidth) * 0.5f;
        float base = centerBaseline(timePaint, h * 0.52f);

        float tick = 1f + 0.04f * Math.max(0f, 1f - (millis / 170f));
        canvas.drawText(hhmm, startX, base, timePaint);
        canvas.save();
        canvas.scale(tick, tick, startX + mainWidth + secWidth * 0.5f, base);
        canvas.drawText(ss, startX + mainWidth, base, secondPaint);
        canvas.restore();
    }

    private void drawProgress(Canvas canvas, float w, float h, float scale,
                              int second, int millis, float alpha) {
        float margin = dp(15f) * scale;
        float y = h - dp(9.5f) * scale;
        float right = w - margin;
        float fraction = (second + millis / 1000f) / 60f;
        float stroke = dp(2f) * scale * (0.75f + 0.25f * alpha);

        progressTrackPaint.setStrokeWidth(stroke);
        progressTrackPaint.setColor(withAlpha(0xFFFFFF, (int) (36 * alpha)));
        canvas.drawLine(margin, y, right, y, progressTrackPaint);

        float tip = margin + (right - margin) * fraction;
        if (tip > margin + dp(0.5f)) {
            progressPaint.setStrokeWidth(stroke);
            progressPaint.setShader(new LinearGradient(margin, y, right, y,
                    new int[]{withAlpha(accent, (int) (210 * alpha)), withAlpha(mix(accent, Color.WHITE, 0.32f), (int) (235 * alpha))},
                    null, Shader.TileMode.CLAMP));
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(margin, y, tip, y, progressPaint);
            progressPaint.setShader(null);
        }
        dotPaint.setColor(withAlpha(accent, (int) (45 * alpha)));
        canvas.drawCircle(tip, y, dp(5.2f) * scale, dotPaint);
        dotPaint.setColor(withAlpha(accent, (int) (245 * alpha)));
        canvas.drawCircle(tip, y, dp(2f) * scale, dotPaint);
    }

    private void drawActions(Canvas canvas, float w, float h, float scale) {
        layoutActionRects(w, h);
        drawActionPill(canvas, settingsRect, "设置", 0xE6FFFFFF);
        drawActionPill(canvas, closeRect, "关闭", 0xFFFFA8B4);
    }

    private void drawActionPill(Canvas canvas, RectF rect, String label, int textColor) {
        actionPaint.setColor(0x2EFFFFFF);
        canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, actionPaint);
        borderPaint.setStrokeWidth(dp(0.8f));
        borderPaint.setColor(0x24FFFFFF);
        canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, borderPaint);
        actionTextPaint.setTextSize(sp(10.2f));
        actionTextPaint.setColor(textColor);
        float baseline = rect.centerY() - (actionTextPaint.ascent() + actionTextPaint.descent()) * 0.5f;
        canvas.drawText(label, rect.centerX(), baseline, actionTextPaint);
    }

    private float centerBaseline(Paint paint, float centerY) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) * 0.5f;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        // Fixed-size overlay: system font scaling must not break the card layout.
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static int mix(int from, int to, float ratio) {
        float r = Color.red(from) + (Color.red(to) - Color.red(from)) * ratio;
        float g = Color.green(from) + (Color.green(to) - Color.green(from)) * ratio;
        float b = Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoop();
        handler.removeCallbacks(hideActions);
        if (compactAnimator != null) {
            compactAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow() && screenOn) {
            startLoop();
        } else {
            stopLoop();
        }
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        screenOn = screenState == SCREEN_STATE_ON;
        if (screenOn && isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
            startLoop();
        } else {
            stopLoop();
        }
    }

    private void startLoop() {
        if (loopRunning) {
            return;
        }
        loopRunning = true;
        lastInvalidate = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopLoop() {
        loopRunning = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }
}
