package com.phonestatus.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Recolecta el estado del dispositivo y calcula una puntuación de salud. */
public class StatusReport {

    public static final int GREEN = 0xFF35D0BA;
    public static final int AMBER = 0xFFFFB020;
    public static final int RED = 0xFFFF5C6C;

    public String deviceName = "Teléfono";
    public String androidVersion = "";
    public int sdk = Build.VERSION.SDK_INT;
    public String securityPatch = "";
    public String abis = "";
    public String hardware = "";
    public String kernel = "";
    public String locale = "";

    public int batteryPct = -1;
    public float batteryTemp = 0f;
    public int batteryVoltage = 0;
    public String batteryHealth = "";
    public String batteryStatus = "";
    public String batteryTech = "";
    public String batteryPlugged = "No";
    public int batteryCurrentMa = 0;
    public boolean hasCurrent = false;

    public long storageTotal = 0, storageUsed = 0, storageFree = 0;

    public long ramTotal = 0, ramAvail = 0;
    public boolean lowMemory = false;

    public int cores = 1;
    public String cpuMax = "";
    public int sensors = 0;
    public int appCount = 0;
    public boolean rooted = false;

    public boolean networkConnected = false;
    public String netType = "Sin conexión";
    public String netIp = "-";
    public String operator = "";
    public boolean metered = false;

    public int screenW = 0, screenH = 0, densityDpi = 0;
    public float refreshRate = 0f;
    public String orientation = "";

    public long uptimeMs = 0;
    public String thermal = "";

    public int score = 100;
    public String rating = "";
    public String advice = "";
    public final List<String> issues = new ArrayList<String>();

    // ---------------------------------------------------------------- collect

    public static StatusReport collect(Context ctx) {
        StatusReport r = new StatusReport();
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.toLowerCase(Locale.ROOT).startsWith(m.toLowerCase(Locale.ROOT))) {
            r.deviceName = cap(model);
        } else {
            r.deviceName = cap(m) + " " + model;
        }
        r.androidVersion = "Android " + Build.VERSION.RELEASE;
        r.securityPatch = Build.VERSION.SECURITY_PATCH == null ? "-" : Build.VERSION.SECURITY_PATCH;
        StringBuilder abis = new StringBuilder();
        for (String a : Build.SUPPORTED_ABIS) {
            if (abis.length() > 0) abis.append(", ");
            abis.append(a);
        }
        r.abis = abis.toString();
        r.kernel = System.getProperty("os.version", "-");
        r.locale = Locale.getDefault().getDisplayLanguage(Locale.getDefault());

        collectBattery(ctx, r);
        collectStorage(r);
        collectMemory(ctx, r);
        collectCpu(r);
        collectNetwork(ctx, r);
        collectScreen(ctx, r);
        collectSystem(ctx, r);

        r.computeScore();
        return r;
    }

    private static void collectBattery(Context ctx, StatusReport r) {
        try {
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) r.batteryPct = Math.round(level * 100f / scale);
                r.batteryTemp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
                r.batteryVoltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                r.batteryTech = safe(i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                switch (plugged) {
                    case BatteryManager.BATTERY_PLUGGED_AC: r.batteryPlugged = "Cargador"; break;
                    case BatteryManager.BATTERY_PLUGGED_USB: r.batteryPlugged = "USB"; break;
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS: r.batteryPlugged = "Inalámbrico"; break;
                    default: r.batteryPlugged = "Batería"; break;
                }
                r.batteryHealth = healthName(i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0));
                r.batteryStatus = statusName(i.getIntExtra(BatteryManager.EXTRA_STATUS, 0));
            }
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (now != 0 && now != Integer.MIN_VALUE) {
                    int v = Math.abs(now);
                    if (v > 5000) v = v / 1000;   // µA -> mA
                    if (v < 20000) {
                        r.batteryCurrentMa = v;
                        r.hasCurrent = true;
                    }
                }
                if (r.batteryPct < 0) {
                    int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (cap > 0) r.batteryPct = cap;
                }
            }
        } catch (Exception ignored) { }
    }

    private static void collectStorage(StatusReport r) {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            r.storageTotal = fs.getBlockCountLong() * fs.getBlockSizeLong();
            r.storageFree = fs.getAvailableBytes();
            r.storageUsed = r.storageTotal - r.storageFree;
        } catch (Exception ignored) { }
    }

    private static void collectMemory(Context ctx, StatusReport r) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            r.ramTotal = mi.totalMem;
            r.ramAvail = mi.availMem;
            r.lowMemory = mi.lowMemory;
        } catch (Exception ignored) { }
    }

    private static void collectCpu(StatusReport r) {
        r.cores = Runtime.getRuntime().availableProcessors();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware")) {
                    String[] p = line.split(":", 2);
                    if (p.length == 2) { r.hardware = p[1].trim(); break; }
                }
            }
            br.close();
        } catch (Exception ignored) { }
        if (r.hardware.isEmpty()) r.hardware = Build.HARDWARE;
        long maxKhz = 0;
        for (int c = 0; c < r.cores && c < 16; c++) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(
                        "/sys/devices/system/cpu/cpu" + c + "/cpufreq/cpuinfo_max_freq"));
                String s = br.readLine();
                br.close();
                if (s != null) maxKhz = Math.max(maxKhz, Long.parseLong(s.trim()));
            } catch (Exception ignored) { }
        }
        if (maxKhz > 0) r.cpuMax = String.format(Locale.US, "%.2f GHz", maxKhz / 1000000.0);
    }

    private static void collectNetwork(Context ctx, StatusReport r) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                r.networkConnected = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) r.netType = "Wi-Fi";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) r.netType = "Datos móviles";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) r.netType = "Ethernet";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) r.netType = "VPN";
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) r.netType = "Bluetooth";
                else r.netType = "Conectado";
                r.metered = !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            }
        } catch (Exception ignored) { }
        r.netIp = localIp();
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                r.operator = safe(tm.getNetworkOperatorName());
                if (r.operator.isEmpty()) r.operator = "-";
            }
        } catch (Exception ignored) { }
    }

    private static void collectScreen(Context ctx, StatusReport r) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            r.screenW = dm.widthPixels;
            r.screenH = dm.heightPixels;
            r.densityDpi = dm.densityDpi;
            r.refreshRate = d.getRefreshRate();
            r.orientation = ctx.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE ? "Horizontal" : "Vertical";
        } catch (Exception ignored) { }
    }

    private static void collectSystem(Context ctx, StatusReport r) {
        r.uptimeMs = SystemClock.elapsedRealtime();
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) r.sensors = sm.getSensorList(Sensor.TYPE_ALL).size();
        } catch (Exception ignored) { }
        try {
            r.appCount = ctx.getPackageManager().getInstalledPackages(0).size();
        } catch (Exception ignored) { }
        r.rooted = isRooted();
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null) r.thermal = thermalName(pm.getCurrentThermalStatus());
            } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------------ score

    private void computeScore() {
        issues.clear();
        int s = 100;

        if (batteryPct >= 0) {
            if (batteryPct < 10) { s -= 20; issues.add("Batería casi agotada (" + batteryPct + "%)"); }
            else if (batteryPct < 25) { s -= 9; issues.add("Batería baja (" + batteryPct + "%)"); }
        }
        if (batteryTemp >= 43f) { s -= 12; issues.add("Batería caliente (" + String.format(Locale.US, "%.1f", batteryTemp) + " °C)"); }
        else if (batteryTemp >= 40f) { s -= 5; issues.add("Temperatura elevada (" + String.format(Locale.US, "%.1f", batteryTemp) + " °C)"); }
        if (!batteryHealth.isEmpty() && !batteryHealth.equals("Buena")) {
            s -= 6;
            issues.add("Salud de batería: " + batteryHealth.toLowerCase(Locale.ROOT));
        }
        if (storageTotal > 0) {
            double freePct = storageFree * 100.0 / storageTotal;
            if (freePct < 5) { s -= 16; issues.add("Almacenamiento casi lleno"); }
            else if (freePct < 12) { s -= 7; issues.add("Poco espacio libre"); }
        }
        if (ramTotal > 0) {
            double freePct = ramAvail * 100.0 / ramTotal;
            if (freePct < 6) { s -= 13; issues.add("Memoria RAM muy saturada"); }
            else if (freePct < 14) { s -= 5; issues.add("Poca memoria RAM libre"); }
        }
        if (lowMemory) { s -= 8; issues.add("El sistema reporta memoria baja"); }
        if (uptimeMs > 14L * 24 * 3600 * 1000) { s -= 3; }
        if (rooted) { s -= 2; }

        if (s < 0) s = 0;
        if (s > 100) s = 100;
        score = s;

        if (score >= 85) { rating = "Excelente"; advice = "Tu teléfono está en muy buen estado. No hay nada urgente que hacer."; }
        else if (score >= 70) { rating = "Buen estado"; advice = "Todo funciona bien. Revisa las observaciones para mantenerlo así."; }
        else if (score >= 50) { rating = "Regular"; advice = "Hay algunos puntos que conviene atender para mejorar el rendimiento."; }
        else { rating = "Necesita atención"; advice = "Se detectaron varios problemas. Sigue las recomendaciones de abajo."; }

        if (issues.isEmpty()) issues.add("Todo en orden");
    }

    public int scoreColor() {
        if (score >= 70) return GREEN;
        if (score >= 45) return AMBER;
        return RED;
    }

    public int batteryColor() {
        if (batteryPct >= 50) return GREEN;
        if (batteryPct >= 20) return AMBER;
        return RED;
    }

    public String uptimeText() {
        long s = uptimeMs / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m + " min";
        return m + " min";
    }

    public String shareText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Estado del teléfono · ").append(deviceName).append("\n");
        sb.append(androidVersion).append(" (API ").append(sdk).append(")\n\n");
        sb.append("Salud general: ").append(score).append("/100 (").append(rating).append(")\n");
        for (String i : issues) sb.append("• ").append(i).append("\n");
        sb.append("\nBatería: ").append(batteryPct).append("% · ")
                .append(String.format(Locale.US, "%.1f", batteryTemp)).append(" °C · ").append(batteryHealth)
                .append("\nAlmacenamiento libre: ").append(Fmt.bytes(storageFree)).append(" de ").append(Fmt.bytes(storageTotal))
                .append("\nRAM disponible: ").append(Fmt.bytes(ramAvail)).append(" de ").append(Fmt.bytes(ramTotal))
                .append("\nRed: ").append(netType).append(" · IP ").append(netIp)
                .append("\nPantalla: ").append(screenW).append("×").append(screenH).append(" px · ").append(densityDpi).append(" dpi")
                .append("\nEncendido hace: ").append(uptimeText());
        return sb.toString();
    }

    // ----------------------------------------------------------------- helpers

    private static String localIp() {
        try {
            List<NetworkInterface> ifs = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : ifs) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }
        return "-";
    }

    private static boolean isRooted() {
        String[] paths = {"/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"};
        for (String p : paths) {
            if (new File(p).exists()) return true;
        }
        return false;
    }

    private static String healthName(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Buena";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Sobrecalentada";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Agotada";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Sobretensión";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Fría";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Fallando";
            default: return "Desconocida";
        }
    }

    private static String statusName(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Cargando";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Descargando";
            case BatteryManager.BATTERY_STATUS_FULL: return "Completa";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Sin carga";
            default: return "Desconocido";
        }
    }

    private static String thermalName(int t) {
        switch (t) {
            case PowerManager.THERMAL_STATUS_NONE: return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Leve";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderada";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severa";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Crítica";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergencia";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Apagado térmico";
            default: return "Desconocido";
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String cap(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** Formatos numéricos. */
    public static final class Fmt {
        public static String bytes(long b) {
            if (b <= 0) return "0 B";
            if (b < 1024) return b + " B";
            double gb = b / 1073741824.0;
            if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
            double mb = b / 1048576.0;
            if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb);
            return String.format(Locale.US, "%.0f KB", b / 1024.0);
        }

        public static String pct(long part, long total) {
            if (total <= 0) return "0%";
            return Math.round(part * 100.0 / total) + "%";
        }
    }
}
