package com.phonestatus.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Explorador de archivos del dispositivo. */
public class FilesActivity extends Activity {

    private static final int BG = 0xFF0B0F14;
    private static final int CARD = 0xFF151B24;
    private static final int TEXT = 0xFFE9EFF7;
    private static final int MUTED = 0xFF8B97A8;
    private static final int ACCENT = 0xFF35D0BA;

    private final List<File> volumes = new ArrayList<File>();
    private final List<File> items = new ArrayList<File>();
    private final Collator collator = Collator.getInstance(Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    private File root;
    private File current;
    private boolean showHidden = false;
    private boolean loadedOnce = false;

    private LinearLayout breadcrumb;
    private HorizontalScrollView pathScroll;
    private LinearLayout chipsRow;
    private FrameLayout body;
    private ListView list;
    private TextView empty;
    private TextView count;
    private LinearLayout permissionBox;
    private TextView hiddenChip;
    private EntryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        initVolumes();
        if (!volumes.isEmpty()) root = volumes.get(0);

        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(BG);

        rootView.addView(buildHeader(), lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        pathScroll = new HorizontalScrollView(this);
        pathScroll.setHorizontalScrollBarEnabled(false);
        breadcrumb = new LinearLayout(this);
        breadcrumb.setOrientation(LinearLayout.HORIZONTAL);
        breadcrumb.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumb.setPadding(dp(16), 0, dp(16), dp(4));
        pathScroll.addView(breadcrumb);
        rootView.addView(pathScroll, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        chipsRow.setPadding(dp(14), dp(4), dp(14), dp(8));
        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.addView(chipsRow);
        rootView.addView(chipsScroll, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        body = new FrameLayout(this);
        list = buildList();
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = tv("Carpeta vacía", 13f, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.CENTER;
        body.addView(empty, elp);

        permissionBox = buildPermissionBox();
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.leftMargin = dp(20);
        plp.rightMargin = dp(20);
        body.addView(permissionBox, plp);

        rootView.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        count = tv("", 11f, 0xFF66707F, false);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(8), 0, dp(12));
        rootView.addView(count, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(rootView);
        buildChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = hasAccess();
        permissionBox.setVisibility(ok ? View.GONE : View.VISIBLE);
        if (!ok) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            count.setText("");
            return;
        }
        list.setVisibility(View.VISIBLE);
        if (!loadedOnce) {
            loadedOnce = true;
            if (root != null) navigate(root);
        } else if (current != null) {
            loadEntries();
        }
    }

    // ------------------------------------------------------------- navegación

    private void initVolumes() {
        File internal = Environment.getExternalStorageDirectory();
        volumes.add(internal);
        try {
            File[] dirs = getExternalFilesDirs(null);
            if (dirs != null) {
                for (File d : dirs) {
                    if (d == null) continue;
                    String p = d.getAbsolutePath();
                    int i = p.indexOf("/Android/data");
                    if (i > 0) {
                        File v = new File(p.substring(0, i));
                        if (v.exists() && !volumes.contains(v)) volumes.add(v);
                    }
                }
            }
        } catch (Exception ignored) { }
        root = volumes.isEmpty() ? new File("/storage/emulated/0") : volumes.get(0);
    }

    private void navigate(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        current = dir;
        for (File v : volumes) {
            if (dir.getAbsolutePath().startsWith(v.getAbsolutePath())) {
                root = v;
                break;
            }
        }
        updateBreadcrumb();
        loadEntries();
        list.setSelection(0);
        buildChips();
    }

    private void goUp() {
        if (current == null) { finish(); return; }
        if (!current.equals(root) && current.getParentFile() != null
                && current.getAbsolutePath().startsWith(root.getAbsolutePath())) {
            navigate(current.getParentFile());
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (current != null && !current.equals(root)) {
            navigate(current.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    private void loadEntries() {
        items.clear();
        boolean failed = false;
        if (current != null) {
            try {
                File[] fs = current.listFiles();
                if (fs == null) {
                    failed = true;
                } else {
                    for (File f : fs) {
                        if (!showHidden && f.getName().startsWith(".")) continue;
                        items.add(f);
                    }
                }
            } catch (Exception e) {
                failed = true;
            }
        }
        Collections.sort(items, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                boolean da = a.isDirectory(), db = b.isDirectory();
                if (da != db) return da ? -1 : 1;
                return collator.compare(a.getName(), b.getName());
            }
        });
        adapter.notifyDataSetChanged();

        int dirs = 0, files = 0;
        for (File f : items) {
            if (f.isDirectory()) dirs++; else files++;
        }
        empty.setText(failed ? "No se pudo leer esta carpeta"
                : "Carpeta vacía");
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        String label = dirs + (dirs == 1 ? " carpeta" : " carpetas")
                + " · " + files + (files == 1 ? " archivo" : " archivos");
        count.setText(label + freeSpace());
    }

    private String freeSpace() {
        try {
            if (root == null) return "";
            StatFs fs = new StatFs(root.getPath());
            long free = fs.getAvailableBytes();
            return "  ·  " + StatusReport.Fmt.bytes(free) + " libres";
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------- UI

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(10), 0, dp(10), 0);

        TextView back = tv("‹", 28f, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(6), 0, dp(10), dp(3));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goUp(); }
        });
        h.addView(back, lp(dp(40), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = tv("Archivos", 18f, TEXT, true);
        h.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hiddenChip = tv("Ocultos", 11.5f, MUTED, true);
        hiddenChip.setGravity(Gravity.CENTER);
        hiddenChip.setPadding(dp(12), dp(7), dp(12), dp(7));
        styleHiddenChip();
        hiddenChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHidden = !showHidden;
                styleHiddenChip();
                loadEntries();
            }
        });
        h.addView(hiddenChip);
        return h;
    }

    private void styleHiddenChip() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        if (showHidden) {
            g.setColor(ACCENT);
            hiddenChip.setTextColor(0xFF06201C);
        } else {
            g.setColor(0xFF1A212C);
            hiddenChip.setTextColor(MUTED);
        }
        hiddenChip.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), g, null));
    }

    private ListView buildList() {
        ListView l = new ListView(this);
        l.setDivider(new ColorDrawable(0x12FFFFFF));
        l.setDividerHeight(1);
        l.setBackgroundColor(BG);
        l.setCacheColorHint(0);
        l.setVerticalScrollBarEnabled(false);
        l.setPadding(0, dp(4), 0, 0);
        l.setClipToPadding(false);
        adapter = new EntryAdapter();
        l.setAdapter(adapter);
        return l;
    }

    private void buildChips() {
        if (chipsRow == null) return;
        chipsRow.removeAllViews();
        for (final File v : volumes) {
            String name = v.equals(volumes.get(0)) ? "Interno" : v.getName();
            chipsRow.addView(chip(name, v.equals(root), new View.OnClickListener() {
                @Override public void onClick(View view) { navigate(v); }
            }));
        }
        File internal = volumes.get(0);
        addShortcut(internal, Environment.DIRECTORY_DOWNLOADS, "Descargas");
        addShortcut(internal, Environment.DIRECTORY_DCIM, "Cámara");
        addShortcut(internal, Environment.DIRECTORY_PICTURES, "Imágenes");
        addShortcut(internal, Environment.DIRECTORY_MUSIC, "Música");
    }

    private void addShortcut(File base, String dir, String label) {
        final File f = new File(base, dir);
        if (!f.isDirectory()) return;
        chipsRow.addView(chip(label, false, new View.OnClickListener() {
            @Override public void onClick(View view) { navigate(f); }
        }));
    }

    private TextView chip(String text, boolean active, View.OnClickListener l) {
        TextView t = tv(text, 12f, active ? 0xFF06201C : 0xFFC8D2DF, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(13), dp(7), dp(13), dp(7));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(17));
        g.setColor(active ? ACCENT : 0xFF1A212C);
        if (!active) g.setStroke(dp(1), 0x1AFFFFFF);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private void updateBreadcrumb() {
        breadcrumb.removeAllViews();
        if (current == null) return;
        List<File> chain = new ArrayList<File>();
        File f = current;
        while (f != null) {
            chain.add(f);
            if (root != null && f.equals(root)) break;
            f = f.getParentFile();
        }
        Collections.reverse(chain);
        for (int i = 0; i < chain.size(); i++) {
            final File segment = chain.get(i);
            boolean last = i == chain.size() - 1;
            String label = segment.equals(root) && segment.equals(volumes.get(0))
                    ? "Interno" : segment.getName();
            if (label.isEmpty()) label = "/";
            TextView t = tv(label, 12.5f, last ? TEXT : MUTED, last);
            t.setPadding(dp(4), dp(4), dp(4), dp(4));
            if (!last) {
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { navigate(segment); }
                });
            }
            breadcrumb.addView(t);
            if (!last) {
                TextView sep = tv("›", 12f, 0xFF4A5462, false);
                sep.setPadding(dp(1), dp(2), dp(1), dp(4));
                breadcrumb.addView(sep);
            }
        }
        pathScroll.post(new Runnable() {
            @Override public void run() { pathScroll.fullScroll(View.FOCUS_RIGHT); }
        });
    }

    private LinearLayout buildPermissionBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), 0x1AFFFFFF);
        box.setBackground(g);
        int p = dp(20);
        box.setPadding(p, p, p, p);
        box.setVisibility(View.GONE);

        TextView title = tv("Acceso a archivos", 17f, TEXT, true);
        box.addView(title);

        TextView info = tv("Para ver todas las carpetas del teléfono, Android necesita que "
                + "autorices el acceso a los archivos.\n\nToca «Abrir ajustes», busca esta app "
                + "y activa «Permitir acceso a todos los archivos».\n\n"
                + "La app solo lee: no modifica ni borra nada.",
                12.5f, MUTED, false);
        info.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(8);
        box.addView(info, ilp);

        TextView go = tv("Abrir ajustes", 13.5f, 0xFF06201C, true);
        go.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(12));
        go.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestAccess(); }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        glp.topMargin = dp(16);
        box.addView(go, glp);
        return box;
    }

    // ---------------------------------------------------------- permisos

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception e2) {
                    Toast.makeText(this, "Abre Ajustes › Aplicaciones › Acceso especial",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 7);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 7) {
            if (hasAccess()) {
                permissionBox.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                if (current == null) navigate(root);
                else loadEntries();
            } else {
                Toast.makeText(this, "Sin este permiso no se pueden listar las carpetas",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------- archivos

    private void openFile(File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(FileContentProvider.uriFor(f), mimeOf(f));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No hay ninguna app para abrir este archivo",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private String mimeOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
            if ("apk".equals(ext)) return "application/vnd.android.package-archive";
        }
        return "application/octet-stream";
    }

    private class EntryAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView name, sub, chevron;
            if (convertView == null) {
                row = new LinearLayout(FilesActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(11), dp(12), dp(11));
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18FFFFFF),
                        null, null));

                icon = new ImageView(FilesActivity.this);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
                ilp.rightMargin = dp(14);
                row.addView(icon, ilp);

                LinearLayout col = new LinearLayout(FilesActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                row.addView(col, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                name = tv("", 14f, TEXT, false);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                col.addView(name);

                sub = tv("", 11f, MUTED, false);
                sub.setMaxLines(1);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = dp(2);
                col.addView(sub, slp);

                chevron = tv("›", 18f, 0xFF5C6674, false);
                chevron.setPadding(dp(8), 0, dp(2), 0);
                row.addView(chevron);

                row.setTag(new Object[]{name, sub, chevron, icon});
            } else {
                row = (LinearLayout) convertView;
                Object[] tags = (Object[]) row.getTag();
                name = (TextView) tags[0];
                sub = (TextView) tags[1];
                chevron = (TextView) tags[2];
                icon = (ImageView) tags[3];
            }

            final File f = items.get(position);
            boolean dir = f.isDirectory();
            name.setText(f.getName());
            icon.setImageResource(dir ? R.drawable.ic_folder : R.drawable.ic_file);
            chevron.setVisibility(dir ? View.VISIBLE : View.INVISIBLE);
            if (dir) {
                sub.setText("Carpeta");
            } else {
                sub.setText(StatusReport.Fmt.bytes(f.length()) + "  ·  "
                        + dateFmt.format(new Date(f.lastModified())));
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (f.isDirectory()) navigate(f);
                    else openFile(f);
                }
            });
            return row;
        }
    }

    // ------------------------------------------------------------- helpers

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
