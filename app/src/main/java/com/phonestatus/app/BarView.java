package com.phonestatus.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Barra de progreso redondeada. */
public class BarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress = 0f;
    private int color = StatusReport.GREEN;

    public BarView(Context c) {
        super(c);
        track.setColor(0xFF1C2531);
        fill.setColor(color);
    }

    public void setData(float p, int c) {
        progress = Math.max(0f, Math.min(1f, p));
        color = c;
        fill.setColor(c);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        float fw = Math.max(h, w * progress);
        rect.set(0, 0, fw, h);
        canvas.drawRoundRect(rect, r, r, fill);
    }
}
