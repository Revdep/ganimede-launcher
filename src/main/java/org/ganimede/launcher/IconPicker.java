package org.ganimede.launcher;

import static org.ganimede.launcher.Sheets.item;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * "Icon…" for any entry (app, folder, shortcut): every icon of every installed icon pack in one searchable grid
 * (loaded in the background so it opens at once), each pack on its own, or back to the original icon.
 */
final class IconPicker {
    private final Ui ui;
    private final Sheets sheets;
    private final Handler handler;
    private final Function<String, String> entryName;
    private final Runnable refresh, reloadApps;

    IconPicker(Ui ui, Sheets sheets, Handler handler, Function<String, String> entryName, Runnable refresh, Runnable reloadApps) {
        this.ui = ui;
        this.sheets = sheets;
        this.handler = handler;
        this.entryName = entryName;
        this.refresh = refresh;
        this.reloadApps = reloadApps;
    }

    void choose(String entry) {
        Map<String, String> packs = Icons.packs(ui.a);
        List<Sheets.Row> rows = new ArrayList<>();
        boolean picked = ui.prefs.contains("icon:" + entry);
        rows.add(item(picked ? "reset" : "check", ui.tr("Original icon"), () -> apply(entry, null)));
        if (packs.isEmpty()) rows.add(item("info", ui.tr("No icon packs installed. Install one (for example from F-Droid) and it shows up here."), () -> {}));
        else rows.add(item("search", ui.tr("All icon packs"), () -> grid(entry, new ArrayList<>(packs.keySet()), ui.tr("All icon packs"))));
        for (Map.Entry<String, String> e : packs.entrySet())
            rows.add(item("image", e.getValue(), () -> grid(entry, List.of(e.getKey()), e.getValue())));
        sheets.show(ui.f("Icon for %s", entryName.apply(entry)), rows);
    }

    /** Saves the picked icon ("pack|drawable", or null for the original) and shows it everywhere at once. */
    private void apply(String entry, String pick) {
        if (pick == null) ui.prefs.edit().remove("icon:" + entry).apply();
        else ui.prefs.edit().putString("icon:" + entry, pick).apply();
        if (entry.startsWith("f:") || entry.startsWith("s:")) refresh.run(); else reloadApps.run(); // apps cache their icon
    }

    private void grid(String entry, List<String> packs, String title) {
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText find = new EditText(ui.a);
        find.setHint(ui.tr("Search icons"));
        find.setSingleLine(true);
        // An app's own icons first; a folder's name rarely matches a drawable, so it starts with every icon.
        if (!entry.startsWith("f:")) find.setText(entryName.apply(entry).split(" ")[0].toLowerCase(Locale.ROOT));
        box.addView(find, new LinearLayout.LayoutParams(-1, -2));
        GridView grid = new GridView(ui.a);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(ui.dp(12));
        grid.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        box.addView(grid, new LinearLayout.LayoutParams(-1, Math.min(ui.dp(420), ui.a.getResources().getDisplayMetrics().heightPixels / 2))); // half the screen at most: landscape or split screen keep the search and Cancel in view
        List<String> all = new ArrayList<>(), shown = new ArrayList<>(); // "pack|drawable"
        LruCache<String, Drawable> loaded = new LruCache<>(240);
        BaseAdapter ad = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View old, ViewGroup parent) {
                ImageView iv = old instanceof ImageView ? (ImageView) old : new ImageView(ui.a);
                iv.setLayoutParams(new AbsListView.LayoutParams(-1, ui.dp(52)));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                String k = shown.get(i);
                Drawable d = loaded.get(k);
                if (d == null) {
                    d = Icons.named(ui.a, k);
                    if (d != null) loaded.put(k, d);
                }
                iv.setImageDrawable(d);
                iv.setContentDescription(k.substring(k.indexOf('|') + 1).replace('_', ' '));
                return iv;
            }
        };
        grid.setAdapter(ad);
        Runnable filter = () -> {
            String q = find.getText().toString().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            shown.clear();
            for (String k : all) if (q.isEmpty() || k.substring(k.indexOf('|') + 1).toLowerCase(Locale.ROOT).contains(q)) shown.add(k);
            ad.notifyDataSetChanged();
        };
        find.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { filter.run(); }
        });
        Dialog dlg = ui.ask()
                .setTitle(title).setView(box).setNegativeButton(ui.tr("Cancel"), null).create();
        grid.setOnItemClickListener((p, v, i, id) -> {
            apply(entry, shown.get(i));
            dlg.dismiss();
        });
        dlg.show();
        new Thread(() -> {
            List<String> got = new ArrayList<>();
            for (String pack : packs) for (String n : Icons.packIcons(ui.a, pack)) got.add(pack + "|" + n);
            handler.post(() -> { all.addAll(got); filter.run(); });
        }).start();
    }
}
