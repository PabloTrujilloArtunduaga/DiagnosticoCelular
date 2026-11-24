package com.phonestatus.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;

/** Panel de estadísticas con datos del dispositivo actualizados cada segundo. */
public class StatsActivity extends Activity {

    private static final int BG = 0xFF0B0F14;
    private static final int CARD = 0xFF151B24;
    private static final int TEXT = 0xFFE9EFF7;
    private static final int MUTED = 0xFF8B97A8;
    private static final int ACCENT = 0xFF35D0BA;
    private static final int PURPLE = 0xFF8F7BFF;
    private static final int AMBER = 0xFFFFB020;
    private static final int RED = 0xFFFF5C6C;

    private static final int N = 60;
    private static final IntentFilter BATTERY_FILTER =
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    private final float[] batt = new float[N];
    private final float[] temp = new float[N];
    private final float[] ram = new float[N];
    private final float[] netDown = new float[N];
    private final float[] netUp = new float[N];

    private final Handler handler = new Handler();
    private boolean running = false;
    private boolean first = true;
    private long lastRx = -1, lastTx = -1;

    private ChartView battChart, tempChart, ramChart, netChart;
    private DonutView storageDonut;
    private TextView battValue, tempValue, ramValue, netValue, storageValue, storagePct;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            sample();
            if (running) handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        contentRoot = new LinearLayout(this);
        LinearLayout content = contentRoot;
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(22), dp(18), dp(26));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        // Cabecera
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header);

        TextView back = tv("‹", 28f, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(2), 0, dp(10), dp(3));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back, lp(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView kicker = tv("ESTADÍSTICAS", 10.5f, ACCENT, true);
        kicker.setLetterSpacing(0.16f);
        titles.addView(kicker);
        TextView title = tv("Datos en vivo", 23f, TEXT, true);
        add(titles, title, 0, 4, 0, 0);
        TextView sub = tv("Muestras cada segundo · últimos 60 s", 11.5f, MUTED, false);
        add(titles, sub, 0, 3, 0, 0);

        TextView liveDot = tv("●", 12f, ACCENT, true);
        liveDot.setGravity(Gravity.END);
        AlphaAnimation pulse = new AlphaAnimation(0.3f, 1f);
        pulse.setDuration(750);
        pulse.setRepeatCount(AlphaAnimation.INFINITE);
        pulse.setRepeatMode(AlphaAnimation.REVERSE);
        liveDot.startAnimation(pulse);
        header.addView(liveDot, lp(dp(16), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView liveText = tv("EN VIVO", 10f, ACCENT, true);
        liveText.setLetterSpacing(0.08f);
        header.addView(liveText);

        // Tarjetas
        buildBattCard();
        buildTempCard();
        buildRamCard();
        buildNetCard();
        buildStorageCard();

        TextView foot = tv("Todo se mide localmente en tu teléfono. No se envía ningún dato.",
                10.5f, 0xFF66707F, false);
        foot.setGravity(Gravity.CENTER);
        add(content, foot, 0, 6, 0, 0);

        sample();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!running) {
            running = true;
            handler.postDelayed(tick, 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        handler.removeCallbacks(tick);
    }

    // ------------------------------------------------------------- tarjetas

    private void buildBattCard() {
        LinearLayout card = card();
        sectionHeader(card, "Nivel de batería", ACCENT);
        battValue = tv("--", 30f, TEXT, true);
        card.addView(battValue);

        battChart = new ChartView(this);
        card.addView(battChart, chartLp(battChart));
        info(card, "Qué significa: el porcentaje de carga baja según la pantalla y las apps. "
                + "Si cae muy rápido, revisa el consumo por app en Ajustes › Batería.");
    }

    private void buildTempCard() {
        LinearLayout card = card();
        sectionHeader(card, "Temperatura de la batería", AMBER);
        tempValue = tv("--", 30f, TEXT, true);
        card.addView(tempValue);

        tempChart = new ChartView(this);
        card.addView(tempChart, chartLp(tempChart));
        info(card, "Qué significa: el calor de la batería. Sobre 40 °C se acelera su desgaste; "
                + "evita cargar bajo el sol, con funda gruesa o mientras juegas.");
    }

    private void buildRamCard() {
        LinearLayout card = card();
        sectionHeader(card, "Memoria RAM en uso", PURPLE);
        ramValue = tv("--", 30f, TEXT, true);
        card.addView(ramValue);

        ramChart = new ChartView(this);
        card.addView(ramChart, chartLp(ramChart));
        info(card, "Qué significa: cuánta memoria están usando las apps. Si se mantiene sobre "
                + "el 85 %, Android cierra procesos en segundo plano y puede aparecer lentitud.");
    }

    private void buildNetCard() {
        LinearLayout card = card();
        sectionHeader(card, "Tráfico de red", ACCENT);
        netValue = tv("--", 19f, TEXT, true);
        card.addView(netValue);

        netChart = new ChartView(this);
        card.addView(netChart, chartLp(netChart));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lgp.topMargin = dp(4);
        card.addView(legend, lgp);
        TextView d = tv("● Entrada", 11f, ACCENT, true);
        legend.addView(d);
        TextView u = tv("● Salida", 11f, PURPLE, true);
        u.setPadding(dp(12), 0, 0, 0);
        legend.addView(u);

        info(card, "Qué significa: kilobytes por segundo que entran (↓) y salen (↑) del "
                + "teléfono. Los picos sostenidos suelen ser sincronizaciones, copias en la "
                + "nube o actualizaciones de apps.");
    }

    private void buildStorageCard() {
        LinearLayout card = card();
        sectionHeader(card, "Almacenamiento interno", ACCENT);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);

        storageDonut = new DonutView(this);
        row.addView(storageDonut, lp(dp(108), dp(108)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cdp.leftMargin = dp(16);
        row.addView(col, cdp);

        storagePct = tv("--", 27f, TEXT, true);
        col.addView(storagePct);
        TextView cap = tv("ocupado", 11.5f, MUTED, false);
        add(col, cap, 0, 2, 0, 0);
        storageValue = tv("--", 14f, ACCENT, true);
        add(col, storageValue, 0, 10, 0, 0);

        info(card, "Qué significa: cuánto espacio del almacenamiento está ocupado. Con menos "
                + "del 10 % libre, el teléfono puede ir más lento y fallar las actualizaciones.");
    }

    // ------------------------------------------------------------- muestreo

    private void sample() {
        int pct = -1;
        float degrees = 0f;
        String health = "";
        try {
            Intent i = registerReceiver(null, BATTERY_FILTER);
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) pct = Math.round(level * 100f / scale);
                degrees = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
                health = String.valueOf(i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0));
            }
        } catch (Exception ignored) { }

        long ramTotal = 0, ramAvail = 0;
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ramTotal = mi.totalMem;
            ramAvail = mi.availMem;
        } catch (Exception ignored) { }

        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        float kbDown = 0f, kbUp = 0f;
        if (rx >= 0 && tx >= 0) {
            if (lastRx >= 0) kbDown = Math.max(0f, (rx - lastRx) / 1024f);
            if (lastTx >= 0) kbUp = Math.max(0f, (tx - lastTx) / 1024f);
            lastRx = rx;
            lastTx = tx;
        }

        long total = 0, free = 0;
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            total = fs.getBlockCountLong() * fs.getBlockSizeLong();
            free = fs.getAvailableBytes();
        } catch (Exception ignored) { }
        long used = total - free;
        float storageP = total > 0 ? used * 100f / total : 0f;
        float ramP = ramTotal > 0 ? (ramTotal - ramAvail) * 100f / ramTotal : 0f;

        if (first) {
            first = false;
            if (pct >= 0) Arrays.fill(batt, pct);
            if (degrees > 0) Arrays.fill(temp, degrees);
            if (ramTotal > 0) Arrays.fill(ram, ramP);
        } else {
            push(batt, pct >= 0 ? pct : 0);
            push(temp, degrees);
            push(ram, ramP);
        }
        push(netDown, kbDown);
        push(netUp, kbUp);

        // Valores
        if (battValue != null) {
            battValue.setText(pct >= 0 ? pct + " %" : "No disponible");
            battValue.setTextColor(pct >= 0 && pct < 20 ? RED : TEXT);
            battChart.setData(batt, null, ACCENT, PURPLE, 0f, 100f);
        }
        if (tempValue != null) {
            tempValue.setText(degrees > 0 ? String.format(Locale.US, "%.1f °C", degrees) : "--");
            tempValue.setTextColor(degrees >= 42 ? RED : degrees >= 40 ? AMBER : TEXT);
            tempChart.setData(temp, null, AMBER, PURPLE, 20f, 50f);
        }
        if (ramValue != null) {
            ramValue.setText(String.format(Locale.US, "%.0f %% en uso", ramP));
            ramValue.setTextColor(ramP >= 90 ? RED : TEXT);
            ramChart.setData(ram, null, PURPLE, ACCENT, 0f, 100f);
        }
        if (netValue != null) {
            boolean supported = rx >= 0 && tx >= 0;
            netValue.setText(supported
                    ? String.format(Locale.US, "↓ %.0f KB/s    ↑ %.0f KB/s", kbDown, kbUp)
                    : "Contador de red no disponible");
            netChart.setData(netDown, netUp, ACCENT, PURPLE, 0f, 0f);
        }
        if (storageValue != null) {
            storagePct.setText(String.format(Locale.US, "%.0f %%", storageP));
            storagePct.setTextColor(storageP > 92 ? RED : TEXT);
            storageValue.setText(StatusReport.Fmt.bytes(free) + " libres de "
                    + StatusReport.Fmt.bytes(total));
            storageDonut.setData(storageP / 100f, storageP > 92 ? RED : ACCENT);
        }
    }

    private void push(float[] arr, float value) {
        System.arraycopy(arr, 1, arr, 0, arr.length - 1);
        arr[arr.length - 1] = value;
    }

    // ------------------------------------------------------------- helpers

    private void sectionHeader(LinearLayout card, String title, int dotColor) {
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
        row.addView(tv(title, 15f, TEXT, true), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void info(LinearLayout card, String text) {
        TextView t = tv(text, 11.5f, 0xFF98A3B4, false);
        t.setLineSpacing(dp(3), 1f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF101720);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.addView(t, lp);
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
        c.setPadding(p, dp(15), p, dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        contentRoot.addView(c, lp);
        return c;
    }

    private LinearLayout contentRoot;

    private LinearLayout.LayoutParams chartLp(ChartView chart) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        lp.topMargin = dp(8);
        return lp;
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

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
