package com.hejun.floatingclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/** A tiny hand-drawn switch so the app does not need any UI dependency. */
public class ToggleSwitch extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private boolean checked;
    private float progress;
    private int accent = Prefs.DEFAULT_ACCENT;
    private ValueAnimator animator;
    private Runnable onCheckedChange;

    public ToggleSwitch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value) {
            return;
        }
        checked = value;
        if (animator != null) {
            animator.cancel();
        }
        float target = value ? 1f : 0f;
        if (!animate) {
            progress = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(180);
        animator.setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public void setOnCheckedChange(Runnable listener) {
        onCheckedChange = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float inset = h * 0.08f;
        trackRect.set(inset, inset, w - inset, h - inset);
        float radius = trackRect.height() * 0.5f;
        int off = 0x24FFFFFF;
        int on = ClockView.withAlpha(accent, 155);
        trackPaint.setColor(blend(off, on, progress));
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        if (progress > 0.02f) {
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(1f));
            trackPaint.setColor(ClockView.withAlpha(accent, (int) (110 * progress)));
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
            trackPaint.setStyle(Paint.Style.FILL);
        }

        float knobRadius = trackRect.height() * 0.5f - dp(2.5f);
        float start = trackRect.left + dp(4f) + knobRadius;
        float end = trackRect.right - dp(4f) - knobRadius;
        float cx = start + (end - start) * progress;
        float cy = trackRect.centerY();

        knobPaint.setColor(0x38000000);
        canvas.drawCircle(cx, cy + dp(1f), knobRadius + dp(0.5f), knobPaint);
        knobPaint.setColor(blend(0xFFDCE3EA, 0xFFFFFFFF, progress));
        canvas.drawCircle(cx, cy, knobRadius, knobPaint);
        knobPaint.setColor(0x66FFFFFF);
        canvas.drawCircle(cx - knobRadius * 0.25f, cy - knobRadius * 0.28f, knobRadius * 0.28f, knobPaint);
    }

    @Override
    public boolean performClick() {
        checked = !checked;
        float target = checked ? 1f : 0f;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(180);
        animator.setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
        if (onCheckedChange != null) {
            onCheckedChange.run();
        }
        return true;
    }

    private static int blend(int from, int to, float ratio) {
        float r = Color.red(from) + (Color.red(to) - Color.red(from)) * ratio;
        float g = Color.green(from) + (Color.green(to) - Color.green(from)) * ratio;
        float b = Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio;
        float a = Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
