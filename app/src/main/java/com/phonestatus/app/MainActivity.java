package com.phonestatus.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0B0F14;
    private static final int CARD = 0xFF151B24;
    private static final int TEXT = 0xFFE9EFF7;
    private static final int MUTED = 0xFF8B97A8;
    private static final int ACCENT = 0xFF35D0BA;
    private static final int PURPLE = 0xFF8F7BFF;

    private ScrollView scroll;
    private LinearLayout content;
    private StatusReport rep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        content.setPadding(pad, dp(22), pad, dp(26));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (!liveRunning) {
            liveRunning = true;
            liveHandler.postDelayed(liveTick, 5000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        liveRunning = false;
        liveHandler.removeCallbacks(liveTick);
    }

    private final android.os.Handler liveHandler = new android.os.Handler();
    private boolean liveRunning = false;
    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            if (liveRunning) {
                refresh();
                liveHandler.postDelayed(this, 5000);
            }
        }
    };

    private void refresh() {
        int y = scroll.getScrollY();
        rep = StatusReport.collect(this);
        content.removeAllViews();
        build();
        scroll.scrollTo(0, y);
    }

    // --------------------------------------------------------------- building

    private void build() {
        // Cabecera
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top);

        TextView kicker = tv("ANÁLISIS DEL DISPOSITIVO", 10.5f, ACCENT, true);
        kicker.setLetterSpacing(0.16f);
        top.addView(kicker, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView live = tv("● EN VIVO", 9.5f, ACCENT, true);
        live.setGravity(Gravity.END);
        top.addView(live);

        TextView title = tv(rep.deviceName, 25f, TEXT, true);
        title.setLetterSpacing(-0.01f);
        add(content, title, 0, 8, 0, 0);

        String sub = rep.androidVersion + "  ·  API " + rep.sdk;
        TextView subtitle = tv(sub, 12.5f, MUTED, false);
        add(content, subtitle, 0, 5, 0, 16);

        buildScoreCard();
        buildIssuesCard();
        buildStatsCard();
        buildBatteryCard();
        buildStorageCard();
        buildRamCard();
        buildNetworkCard();
        buildScreenCard();
        buildSystemCard();
        buildFilesCard();
        buildTipsCard();
        buildActions();

        TextView foot = tv("Los datos se leen en tu teléfono y no se envían a ningún servidor.",
                10.5f, 0xFF66707F, false);
        foot.setGravity(Gravity.CENTER);
        add(content, foot, 0, 6, 0, 0);
    }

    private void buildScoreCard() {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);

        FrameLayout ringBox = new FrameLayout(this);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(106), dp(106));
        row.addView(ringBox, ringLp);

        final RingView ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        ringBox.addView(center, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView num = tv(String.valueOf(rep.score), 30f, TEXT, true);
        num.setGravity(Gravity.CENTER);
        center.addView(num);
        TextView of = tv("de 100", 9.5f, MUTED, false);
        of.setGravity(Gravity.CENTER);
        center.addView(of);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(18);
        row.addView(col, colLp);

        TextView cap = tv("SALUD GENERAL", 9.5f, MUTED, true);
        cap.setLetterSpacing(0.14f);
        col.addView(cap);

        final TextView rating = tv(rep.rating, 21f, rep.scoreColor(), true);
        add(col, rating, 0, 3, 0, 0);

        TextView advice = tv(rep.advice, 12f, MUTED, false);
        advice.setLineSpacing(dp(2), 1f);
        add(col, advice, 0, 6, 0, 0);

        final int color = rep.scoreColor();
        postAnim(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                ring.setData((Float) a.getAnimatedValue(), color);
            }
        }, 0f, rep.score / 100f, 700);
    }

    private void buildIssuesCard() {
        LinearLayout card = card();
        sectionHeader(card, "Observaciones", null, ACCENT);
        int shown = 0;
        for (String issue : rep.issues) {
            if (shown++ >= 5) break;
            int color = issue.equals("Todo en orden") ? ACCENT : 0xFFFFC24B;
            addBullet(card, issue, color, shown == rep.issues.size());
        }
    }

    private void buildStatsCard() {
        LinearLayout card = card();
        sectionHeader(card, "Estadísticas en vivo", "cada 1 s", ACCENT);

        TextView desc = tv("Gráficas de batería, temperatura, memoria, red y almacenamiento, "
                + "con datos en tiempo real y una explicación sencilla de cada una.",
                12.5f, MUTED, false);
        desc.setLineSpacing(dp(2), 1f);
        add(card, desc, 0, 6, 0, 12);

        TextView open = button("Ver gráficas estadísticas", true);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StatsActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(8);
        card.addView(open, lp);
    }

    private void buildBatteryCard() {
        LinearLayout card = card();
        int c = rep.batteryColor();
        sectionHeader(card, "Batería", rep.batteryStatus, c);

        if (rep.batteryPct >= 0) {
            LinearLayout big = new LinearLayout(this);
            big.setOrientation(LinearLayout.HORIZONTAL);
            big.setGravity(Gravity.BOTTOM);
            TextView pct = tv(rep.batteryPct + "%", 32f, c, true);
            big.addView(pct);
            TextView rest = tv(rep.batteryPlugged.toLowerCase(Locale.ROOT), 12f, MUTED, false);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = dp(10);
            rlp.bottomMargin = dp(6);
            big.addView(rest, rlp);
            card.addView(big);
        } else {
            TextView big = tv("No disponible", 20f, MUTED, true);
            card.addView(big);
        }

        final BarView bar = new BarView(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(9);
        barLp.bottomMargin = dp(6);
        card.addView(bar, barLp);
        final int color = c;
        postAnim(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                bar.setData((Float) a.getAnimatedValue(), color);
            }
        }, 0f, Math.max(0.02f, rep.batteryPct / 100f), 700);

        if (!rep.batteryHealth.isEmpty()) row(card, "Salud", rep.batteryHealth);
        if (rep.batteryTemp > 0) row(card, "Temperatura", String.format(Locale.US, "%.1f °C", rep.batteryTemp));
        if (rep.batteryVoltage > 0) row(card, "Voltaje", String.format(Locale.US, "%.3f V", rep.batteryVoltage / 1000f));
        if (rep.hasCurrent) row(card, "Corriente aprox.", rep.batteryCurrentMa + " mA");
        if (!rep.batteryTech.isEmpty()) row(card, "Tecnología", rep.batteryTech);
        finishRows(card);
    }

    private void buildStorageCard() {
        LinearLayout card = card();
        sectionHeader(card, "Almacenamiento", StatusReport.Fmt.pct(rep.storageUsed, rep.storageTotal) + " usado", ACCENT);

        TextView big = tv(StatusReport.Fmt.bytes(rep.storageFree) + " libres", 22f, TEXT, true);
        card.addView(big);
        TextView cap = tv("de " + StatusReport.Fmt.bytes(rep.storageTotal), 11.5f, MUTED, false);
        card.addView(cap);

        final BarView bar = new BarView(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(10);
        barLp.bottomMargin = dp(6);
        card.addView(bar, barLp);
        final float p = rep.storageTotal > 0 ? (float) rep.storageUsed / rep.storageTotal : 0f;
        final int color = p > 0.9f ? StatusReport.RED : ACCENT;
        postAnim(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                bar.setData((Float) a.getAnimatedValue(), color);
            }
        }, 0f, Math.max(0.02f, p), 700);

        row(card, "Usado", StatusReport.Fmt.bytes(rep.storageUsed));
        row(card, "Disponible", StatusReport.Fmt.bytes(rep.storageFree));
        row(card, "Total", StatusReport.Fmt.bytes(rep.storageTotal));
        finishRows(card);
    }

    private void buildRamCard() {
        LinearLayout card = card();
        long used = rep.ramTotal - rep.ramAvail;
        sectionHeader(card, "Memoria RAM", StatusReport.Fmt.pct(used, rep.ramTotal) + " en uso", PURPLE);

        TextView big = tv(StatusReport.Fmt.bytes(rep.ramAvail) + " disponibles", 22f, TEXT, true);
        card.addView(big);
        TextView cap = tv("de " + StatusReport.Fmt.bytes(rep.ramTotal), 11.5f, MUTED, false);
        card.addView(cap);

        final BarView bar = new BarView(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barLp.topMargin = dp(10);
        barLp.bottomMargin = dp(6);
        card.addView(bar, barLp);
        final float p = rep.ramTotal > 0 ? (float) used / rep.ramTotal : 0f;
        final int color = p > 0.9f ? StatusReport.RED : PURPLE;
        postAnim(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                bar.setData((Float) a.getAnimatedValue(), color);
            }
        }, 0f, Math.max(0.02f, p), 700);

        row(card, "En uso", StatusReport.Fmt.bytes(used));
        row(card, "Disponible", StatusReport.Fmt.bytes(rep.ramAvail));
        row(card, "Total", StatusReport.Fmt.bytes(rep.ramTotal));
        row(card, "Memoria baja", rep.lowMemory ? "Sí" : "No");
        finishRows(card);
    }

    private void buildNetworkCard() {
        LinearLayout card = card();
        sectionHeader(card, "Red", rep.networkConnected ? "Conectado" : "Sin conexión",
                rep.networkConnected ? ACCENT : StatusReport.RED);

        row(card, "Conexión", rep.netType);
        row(card, "IP local", rep.netIp);
        if (!rep.operator.isEmpty() && !rep.operator.equals("-")) row(card, "Operador", rep.operator);
        row(card, "Red de pago", rep.metered ? "Sí" : "No");
        finishRows(card);
    }

    private void buildScreenCard() {
        LinearLayout card = card();
        sectionHeader(card, "Pantalla", null, ACCENT);
        row(card, "Resolución", rep.screenW + " × " + rep.screenH + " px");
        row(card, "Densidad", rep.densityDpi + " dpi");
        if (rep.refreshRate > 0) row(card, "Tasa de refresco", String.format(Locale.US, "%.0f Hz", rep.refreshRate));
        row(card, "Orientación", rep.orientation);
        finishRows(card);
    }

    private void buildSystemCard() {
        LinearLayout card = card();
        sectionHeader(card, "Sistema y hardware", null, MUTED);
        row(card, "Android", rep.androidVersion + " (API " + rep.sdk + ")");
        if (!rep.securityPatch.isEmpty()) row(card, "Parche de seguridad", rep.securityPatch);
        row(card, "Encendido hace", rep.uptimeText());
        row(card, "Procesador", rep.hardware);
        row(card, "Núcleos", rep.cores + (rep.cpuMax.isEmpty() ? "" : " · hasta " + rep.cpuMax));
        row(card, "Arquitectura", rep.abis);
        row(card, "Núcleo (kernel)", rep.kernel);
        if (rep.sensors > 0) row(card, "Sensores", String.valueOf(rep.sensors));
        if (rep.appCount > 0) row(card, "Apps visibles", String.valueOf(rep.appCount));
        row(card, "Root", rep.rooted ? "Sí" : "No");
        if (!rep.thermal.isEmpty()) row(card, "Estado térmico", rep.thermal);
        finishRows(card);
    }

    private void buildFilesCard() {
        LinearLayout card = card();
        sectionHeader(card, "Archivos", null, PURPLE);

        TextView desc = tv("Explora todas las carpetas y archivos del teléfono, con su "
                + "tamaño y fecha. Puedes abrir cada archivo con la app que prefieras.",
                12.5f, MUTED, false);
        desc.setLineSpacing(dp(2), 1f);
        add(card, desc, 0, 6, 0, 12);

        TextView open = button("Abrir explorador de archivos", true);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, FilesActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(8);
        card.addView(open, lp);
    }

    private void buildTipsCard() {
        List<String> tips = new ArrayList<String>();
        if (rep.batteryTemp >= 40f) tips.add("Evita usar el teléfono mientras carga y retira la funda si se calienta.");
        if (rep.batteryPct >= 0 && rep.batteryPct < 25) tips.add("Conecta el cargador cuando puedas; evita descargas por debajo del 15%.");
        if (rep.storageTotal > 0 && rep.storageFree * 100.0 / rep.storageTotal < 15)
            tips.add("Libera espacio: borra archivos grandes, caché y apps que no uses.");
        if (rep.ramTotal > 0 && rep.ramAvail * 100.0 / rep.ramTotal < 18)
            tips.add("Cierra apps en segundo plano o reinicia para liberar memoria RAM.");
        if (rep.uptimeMs > 7L * 24 * 3600 * 1000)
            tips.add("Reinicia el teléfono al menos una vez por semana para mantenerlo fluido.");
        tips.add("Mantén las apps actualizadas y revisa el consumo de batería en Ajustes.");

        LinearLayout card = card();
        sectionHeader(card, "Recomendaciones", null, StatusReport.AMBER);
        int i = 0;
        for (String t : tips) {
            i++;
            addBullet(card, t, StatusReport.AMBER, i == tips.size());
        }
    }

    private void buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        content.addView(row, lp);

        TextView again = button("Volver a analizar", true);
        again.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                refresh();
                Toast.makeText(MainActivity.this, "Análisis actualizado", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        alp.rightMargin = dp(5);
        row.addView(again, alp);

        TextView share = button("Compartir", false);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, "Estado del teléfono");
                i.putExtra(Intent.EXTRA_TEXT, rep.shareText());
                startActivity(Intent.createChooser(i, "Compartir análisis"));
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        slp.leftMargin = dp(5);
        row.addView(share, slp);
    }

    // ---------------------------------------------------------------- helpers

    private void sectionHeader(LinearLayout card, String title, String trailing, int dotColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(dotColor);
        dot.setBackground(d);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dlp.rightMargin = dp(9);
        row.addView(dot, dlp);

        TextView t = tv(title, 15f, TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t, tlp);

        if (trailing != null) {
            TextView tr = tv(trailing, 11.5f, MUTED, false);
            tr.setGravity(Gravity.END);
            row.addView(tr);
        }
    }

    private void row(LinearLayout card, String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(9);
        card.addView(r, rlp);

        TextView l = tv(label, 12.5f, MUTED, false);
        r.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = tv(value, 12.5f, TEXT, true);
        v.setGravity(Gravity.END);
        int w = getResources().getDisplayMetrics().widthPixels - dp(68);
        v.setMaxWidth((int) (w * 0.6f));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(12);
        r.addView(v, vlp);
    }

    private void addBullet(LinearLayout card, String text, int color, boolean last) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        if (last) rlp.bottomMargin = dp(4);
        card.addView(r, rlp);

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dlp.topMargin = dp(6);
        dlp.rightMargin = dp(9);
        r.addView(dot, dlp);

        TextView t = tv(text, 12.5f, 0xFFC8D2DF, false);
        t.setLineSpacing(dp(2), 1f);
        r.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void finishRows(LinearLayout card) {
        // espacio inferior suave
        card.setPadding(card.getPaddingLeft(), card.getPaddingTop(),
                card.getPaddingRight(), dp(14));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), 0x14FFFFFF);
        c.setBackground(g);
        int p = dp(16);
        c.setPadding(p, dp(15), p, dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        content.addView(c, lp);
        return c;
    }

    private TextView button(String label, boolean filled) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (filled) {
            g.setColor(ACCENT);
            b.setTextColor(0xFF06201C);
        } else {
            g.setColor(0xFF1A212C);
            g.setStroke(dp(1), 0x33FFFFFF);
            b.setTextColor(TEXT);
        }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void add(LinearLayout parent, View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(l);
        lp.topMargin = dp(t);
        lp.rightMargin = dp(r);
        lp.bottomMargin = dp(b);
        parent.addView(v, lp);
    }

    private void postAnim(final ValueAnimator.AnimatorUpdateListener listener,
                          float from, float to, long duration) {
        final ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(duration);
        a.addUpdateListener(listener);
        content.post(new Runnable() {
            @Override public void run() { a.start(); }
        });
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
