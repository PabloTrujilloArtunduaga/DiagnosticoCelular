package com.phonestatus.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Anillo de progreso dibujado a mano (sin dependencias). */
public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 1f;
    private int color = StatusReport.GREEN;

    public RingView(Context c) {
        super(c);
        float w = 11f * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0xFF1C2531);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(color);
    }

    public void setData(float p, int c) {
        progress = Math.max(0f, Math.min(1f, p));
        color = c;
        arc.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float stroke = arc.getStrokeWidth();
        float r = Math.min(w, h) / 2f - stroke / 2f - 2f;
        oval.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r);
        canvas.drawCircle(w / 2f, h / 2f, r, track);
        if (progress > 0.001f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, arc);
        }
    }
}
