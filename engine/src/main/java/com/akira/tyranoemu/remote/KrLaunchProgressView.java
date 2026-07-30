package com.akira.tyranoemu.remote;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small determinate circular progress with a centered percentage label. */
public final class KrLaunchProgressView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float progress;
    private ValueAnimator animator;

    public KrLaunchProgressView(Context context) { this(context, null); }

    public KrLaunchProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        progressPaint.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
    }

    public void setColors(int active, int inactive, int text) {
        progressPaint.setColor(active);
        track.setColor(inactive);
        label.setColor(text);
        invalidate();
    }

    public void setErrorColor(int color) {
        if (animator != null) animator.cancel();
        progressPaint.setColor(color);
        label.setColor(color);
        invalidate();
    }

    public void setProgressAnimated(int target) {
        int bounded = Math.max(0, Math.min(100, target));
        if (bounded <= progress) return;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, bounded);
        animator.setDuration(Math.min(600L, (long) (180L + (bounded - progress) * 14L)));
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimations() {
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = Math.max(5f, getWidth() * .075f);
        track.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);
        float inset = stroke / 2f + 2f;
        arc.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(arc, 0, 360, false, track);
        canvas.drawArc(arc, -90, 360f * progress / 100f, false, progressPaint);
        label.setTextSize(getWidth() * .23f);
        Paint.FontMetrics metrics = label.getFontMetrics();
        float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(Math.round(progress) + "%", getWidth() / 2f, baseline, label);
    }
}
