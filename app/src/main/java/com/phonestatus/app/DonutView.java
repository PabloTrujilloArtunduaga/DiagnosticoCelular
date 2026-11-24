package com.phonestatus.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/** Donut simple para mostrar un porcentaje. */
public class DonutView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 0f;

    public DonutView(Context c) {
        super(c);
        float w = 13f * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0xFF1C2531);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(0xFF35D0BA);
    }

    public void setData(float p, int color) {
        progress = Math.max(0f, Math.min(1f, p));
        arc.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float stroke = arc.getStrokeWidth();
        float r = Math.min(w, h) / 2f - stroke / 2f - dp(2);
        oval.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r);
        canvas.drawCircle(w / 2f, h / 2f, r, track);
        if (progress > 0.002f) canvas.drawArc(oval, -90f, 360f * progress, false, arc);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
