package com.hejun.floatingclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Calm, slowly breathing backdrop used only by the settings screen. */
public class BackdropView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = Prefs.DEFAULT_ACCENT;
    private float phase;
    private ValueAnimator animator;

    public BackdropView(Context context) {
        super(context);
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w * 0.4f, h,
                new int[]{0xFF0C1119, 0xFF090C11, 0xFF07090D}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        float breathe = 0.78f + 0.22f * (float) Math.sin(phase * Math.PI * 2f);
        float topX = w * 0.92f;
        float topY = h * 0.06f;
        float radius = w * 0.75f;
        glow.setShader(new RadialGradient(topX, topY, radius,
                ClockView.withAlpha(accent, (int) (24 * breathe)), ClockView.withAlpha(accent, 0),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(topX, topY, radius, glow);

        float bottomX = w * 0.05f;
        float bottomY = h * 0.86f;
        float radius2 = w * 0.62f;
        glow.setShader(new RadialGradient(bottomX, bottomY, radius2,
                ClockView.withAlpha(accent, (int) (14 * (1.2f - breathe))), ClockView.withAlpha(accent, 0),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(bottomX, bottomY, radius2, glow);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(9000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                phase = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
