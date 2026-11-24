package com.phonestatus.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.View;

/** Gráfica de líneas (1 o 2 series) dibujada directamente en Canvas. */
public class ChartView extends View {

    private float[] a = new float[0];
    private float[] b = null;
    private int colorA = 0xFF35D0BA;
    private int colorB = 0xFF8F7BFF;
    private float min = 0f;
    private float max = 100f;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labels = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotB = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChartView(Context c) {
        super(c);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(dp(1));

        labels.setColor(0xFF6E7A8A);
        labels.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9,
                getResources().getDisplayMetrics()));

        for (Paint p : new Paint[]{lineA, lineB}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.4f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        lineA.setColor(colorA);
        lineB.setColor(colorB);

        dotA.setColor(colorA);
        dotB.setColor(colorB);
        dotA.setStyle(Paint.Style.FILL);
        dotB.setStyle(Paint.Style.FILL);
    }

    /**
     * @param va primer serie
     * @param vb segunda serie (puede ser null)
     * @param fixedMin valor mínimo del eje; si es >= fixedMax se calcula solo
     */
    public void setData(float[] va, float[] vb, int ca, int cb, float fixedMin, float fixedMax) {
        a = va == null ? new float[0] : va;
        b = vb;
        colorA = ca;
        colorB = cb;
        lineA.setColor(ca);
        lineB.setColor(cb);
        dotA.setColor(ca);
        dotB.setColor(cb);
        if (fixedMax > fixedMin) {
            min = fixedMin;
            max = fixedMax;
        } else {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float[] s : new float[][]{a, b}) {
                if (s == null) continue;
                for (float v : s) {
                    if (v < lo) lo = v;
                    if (v > hi) hi = v;
                }
            }
            if (lo == Float.MAX_VALUE) { lo = 0f; hi = 1f; }
            float pad = Math.max(0.5f, (hi - lo) * 0.2f);
            min = Math.max(0f, lo - pad);
            max = hi + pad;
            if (max - min < 0.5f) max = min + 0.5f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w < 10 || h < 10 || a.length < 2) return;

        float padL = dp(24), padT = dp(10), padR = dp(8), padB = dp(17);
        float x0 = padL, y0 = padT;
        float pw = w - padL - padR, ph = h - padT - padB;
        if (pw <= 0 || ph <= 0) return;

        // rejilla
        for (int i = 0; i <= 4; i++) {
            float y = y0 + ph * i / 4f;
            canvas.drawLine(x0, y, x0 + pw, y, grid);
        }
        labels.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(fmt(max), x0 - dp(5), y0 + dp(3), labels);
        canvas.drawText(fmt(min), x0 - dp(5), y0 + ph + dp(3), labels);
        labels.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("hace 60 s", x0, h - dp(3), labels);
        labels.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("ahora", x0 + pw, h - dp(3), labels);

        drawSeries(canvas, a, lineA, x0, y0, pw, ph, true);
        if (b != null && b.length > 1) drawSeries(canvas, b, lineB, x0, y0, pw, ph, false);
    }

    private void drawSeries(Canvas canvas, float[] v, Paint line,
                            float x0, float y0, float pw, float ph, boolean primary) {
        float step = pw / (v.length - 1);
        Path path = new Path();
        float lastY = y0 + ph;
        for (int i = 0; i < v.length; i++) {
            float x = x0 + i * step;
            float t = (v[i] - min) / (max - min);
            t = Math.max(0f, Math.min(1f, t));
            float y = y0 + ph * (1f - t);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            lastY = y;
        }
        if (primary) {
            Path area = new Path(path);
            area.lineTo(x0 + pw, y0 + ph);
            area.lineTo(x0, y0 + ph);
            area.close();
            fillA.setShader(new LinearGradient(0, y0, 0, y0 + ph,
                    Color.argb(55, Color.red(colorA), Color.green(colorA), Color.blue(colorA)),
                    Color.argb(0, Color.red(colorA), Color.green(colorA), Color.blue(colorA)),
                    Shader.TileMode.CLAMP));
            canvas.drawPath(area, fillA);
        }
        canvas.drawPath(path, line);
        canvas.drawCircle(x0 + pw, lastY, dp(3.5f), primary ? dotA : dotB);
    }

    private String fmt(float v) {
        if (Math.abs(v) >= 100) return String.valueOf(Math.round(v));
        if (Math.abs(v) >= 10) return String.format(java.util.Locale.US, "%.0f", v);
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
