package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Search;

import android.Manifest;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** All apps: the search field on top, then the A–Z list (rows of icons in Classic), or what the search found. */
final class AppList {
    /** A search result that isn't an app: a contact, a web search, a suggestion. */
    static final class Extra {
        final String label, glyph;
        final Runnable action;
        final List<Button> buttons = new ArrayList<>(); // small action buttons on the right
        Extra(String glyph, String label, Runnable action) { this.glyph = glyph; this.label = label; this.action = action; }
        Extra button(String glyph, String description, Runnable r) { buttons.add(new Button(glyph, description, r)); return this; }
    }

    /** A glyph-only button, so TalkBack reads its description ("Call Anna") instead of the glyph. */
    private record Button(String glyph, String description, Runnable action) {}

    static final class FolderRow {
        final String id;
        FolderRow(String id) { this.id = id; }
    }

    /** A row of the drawer grid in classic mode. */
    static final class GridRow {
        final List<Item> items;
        GridRow(List<Item> items) { this.items = items; }
    }

    private final Ui ui;
    private final Apps apps;
    private final Layout layout;
    private final Entries entries;
    private final Launcher launcher;
    private final Handler handler;
    private final BooleanSupplier shown;
    final LinearLayout pane;
    final EditText search;
    final ListView list;
    private final List<Object> rows = new ArrayList<>();
    private final BaseAdapter adapter = new RowsAdapter();
    /**
     * Rows are built once and kept until something they show changes (clearCache), so scrolling the list never
     * builds views: no hitches.
     */
    private final Map<Object, View> rowCache = new HashMap<>();
    /** The apps shown, in order: kept while typing, dropped when the search is empty or clearCache runs. */
    private List<Item> visibleCache;
    private final ContactSearch contacts;
    /** The query whose contacts are all shown ("Show all" tapped); any other query shows the first few. */
    private String contactsAll = "";

    AppList(Ui ui, Apps apps, Layout layout, Entries entries, Launcher launcher, Handler handler, BooleanSupplier shown) {
        this.ui = ui;
        this.apps = apps;
        this.layout = layout;
        this.entries = entries;
        this.launcher = launcher;
        this.handler = handler;
        this.shown = shown;
        contacts = new ContactSearch(ui, handler);
        pane = new LinearLayout(ui.a);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPaddingRelative(ui.dp(Gestures.OPPOSITE_EDGE_STRIP_DP), ui.dp(16), ui.dp(64), 0);
        pane.setVisibility(View.GONE);
        LinearLayout bar = new LinearLayout(ui.a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(ui.a);
        search.setHint(ui.tr("Search apps, contacts, shortcuts…"));
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x33FFFFFF);
        pill.setCornerRadius(ui.dp(24));
        search.setBackground(pill);
        search.setPaddingRelative(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(10));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                rebuild();
            }
        });
        search.setOnEditorActionListener((v, id, ev) -> { // "Go" opens the first result
            for (Object r : rows) {
                if (r instanceof Item) { launcher.launch((Item) r, v); return true; }
                if (r instanceof Extra) { ((Extra) r).action.run(); return true; }
            }
            return false;
        });
        bar.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        TextView gear = ui.text(22, false);
        gear.setText("⚙");
        gear.setPaddingRelative(ui.dp(14), ui.dp(8), ui.dp(4), ui.dp(8));
        gear.setContentDescription(ui.tr("Launcher settings"));
        gear.setOnClickListener(v -> ui.settings());
        bar.addView(gear);
        pane.addView(bar);
        list = new ListView(ui.a);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPaddingRelative(0, ui.dp(8), 0, ui.dp(24));
        list.setAdapter(adapter);
        pane.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    /** Labels, icons or notifications may have changed: rows are built again. */
    void clearCache() {
        rowCache.clear();
        visibleCache = null;
    }

    int indexOf(String letter) {
        return rows.indexOf(letter);
    }

    boolean empty() {
        return search.getText().length() == 0;
    }

    void rebuild() {
        rows.clear();
        String q = search.getText().toString().trim();
        String ql = q.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { contacts.closed(); visibleCache = null; }
        if (!q.equals(contactsAll)) contactsAll = ""; // another text, or search closed: the first few again
        if (visibleCache == null) visibleCache = apps.visible(); // reordered once, not at every key
        List<Item> visible = visibleCache;
        if (!ql.isEmpty()) {
            JSONArray links = Sunflower.links(ui.a); // parsed once per key
            // Ranked by the search score, worked out once per app; ties go to the app you open most.
            rows.addAll(Search.rank(visible, apps::label, apps::uses, ql));
            // Hidden apps still turn up when searched for by name.
            List<Item> hiddenHits = new ArrayList<>();
            Set<String> hidden = apps.hidden();
            for (Item it : apps.all()) if (hidden.contains(it.key) && Search.score(apps.label(it), ql) > 0) hiddenHits.add(it);
            if (!hiddenHits.isEmpty()) { rows.add(ui.tr("Hidden")); rows.addAll(hiddenHits); }
            if (ui.prefs.getBoolean("contacts_search", false) && q.length() >= 2) addContacts(q); // one letter matches nearly everyone
            addSunflower(q, visible, links);
            rows.add(ui.tr("Web"));
            String[] engine = Search.engine(ui.prefs.getString("engine", "DuckDuckGo"));
            rows.add(new Extra("⌕", ui.f("Search %s for “%s”", engine[0], q), () -> web(q)));
            // Search shortcuts: my links with %s become "search there for what I typed".
            for (Sunflower.Hit h : Sunflower.searches(ui.a, links, q)) rows.add(new Extra("⌕", h.label, () -> Sunflower.open(ui.a, h)));
        } else {
            Map<String, String> listFolders = new LinkedHashMap<>(); // id → section letter, each folder read once
            for (String id : layout.folderIds()) {
                JSONObject f = layout.folder(id);
                if ("list".equals(f.optString("where"))) listFolders.put(id, Search.letter(f.optString("name")));
            }
            String cur = null;
            boolean grid = layout.classic();
            int cols = layout.drawerCols();
            List<Item> pending = new ArrayList<>();
            for (Item it : visible) {
                String l = Search.letter(apps.label(it));
                if (!l.equals(cur)) {
                    if (grid && !pending.isEmpty()) { rows.add(new GridRow(pending)); pending = new ArrayList<>(); }
                    rows.add(cur = l);
                    for (Map.Entry<String, String> f : listFolders.entrySet()) if (f.getValue().equals(l)) rows.add(new FolderRow(f.getKey()));
                }
                if (!grid) { rows.add(it); continue; }
                // Classic drawer: each letter's apps as rows of icons with names.
                pending.add(it);
                if (pending.size() == cols) { rows.add(new GridRow(pending)); pending = new ArrayList<>(); }
            }
            if (!pending.isEmpty()) rows.add(new GridRow(pending));
        }
        adapter.notifyDataSetChanged();
    }

    /** Contacts for the query: from the cache, or read on a background thread and shown when ready. */
    private void addContacts(String q) {
        if (ui.a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        ContactSearch.Hits known = contacts.get(q);
        boolean all = q.equals(contactsAll);
        int n = known == null ? 0 : Search.shownContacts(known.count, all);
        if (known == null || known.people.size() < n) contacts.read(q, all, () -> { if (shown.getAsBoolean() && q.equals(search.getText().toString().trim())) rebuild(); }); // the first read, or the rest after Show all
        if (known == null || known.count == 0) return;
        n = Math.min(n, known.people.size());
        rows.add(ui.tr("Contacts"));
        rows.addAll(known.people.subList(0, n));
        if (n < known.count) rows.add(new Extra("…", ui.f("Show all (%d)", known.count), () -> { contactsAll = q; rebuild(); }));
    }

    /** Search results beyond apps: my links, quick actions, shortcuts inside apps, settings pages. All offline. */
    private void addSunflower(String q, List<Item> visible, JSONArray links) {
        // QuickSearch: "<app name> <words>" searches inside that app, when the app accepts searches.
        int space = q.indexOf(' ');
        if (space > 0 && space < q.length() - 1) {
            String name = q.substring(0, space), terms = q.substring(space + 1).trim();
            for (Item it : visible) {
                if (!Search.startsWith(apps.label(it), name)) continue; // typed from the name's start
                Intent si = new Intent(Intent.ACTION_SEARCH).setPackage(it.pkg);
                if (!Sunflower.opens(ui.a, si)) continue; // asked without the words, so the answer is kept
                si.putExtra(SearchManager.QUERY, terms);
                rows.add(new Extra("⌕", ui.f("Search “%s” in %s", terms, apps.label(it)), () -> ui.open(si)));
                break;
            }
        }
        List<Sunflower.Hit> actions = Sunflower.actions(ui.a, q);
        if (!actions.isEmpty()) {
            rows.add(ui.tr("Quick actions"));
            for (Sunflower.Hit h : actions) rows.add(new Extra("✦", h.label, () -> Sunflower.open(ui.a, h)));
        }
        List<Sunflower.Hit> mine = Sunflower.custom(links, q);
        List<Sunflower.Hit> inApps = ui.prefs.getBoolean("search_shortcuts", true) ? Sunflower.shortcuts(q, pkg -> {
            Item a = apps.byPackage(pkg);
            return a == null ? null : apps.label(a);
        }) : List.of();
        if (!mine.isEmpty() || !inApps.isEmpty()) {
            rows.add(ui.tr("Shortcuts"));
            for (Sunflower.Hit h : mine) rows.add(new Extra("★", h.label, () -> Sunflower.open(ui.a, h)));
            for (Sunflower.Hit h : inApps) rows.add(new Extra("↪", h.label + (h.detail == null ? "" : "  · " + h.detail), () -> Sunflower.open(ui.a, h)));
        }
        if (ui.prefs.getBoolean("search_settings", true)) {
            List<Sunflower.Hit> pages = Sunflower.settings(ui.a, q);
            if (!pages.isEmpty()) {
                rows.add(ui.tr("Settings pages"));
                for (Sunflower.Hit h : pages) rows.add(new Extra("⚙", h.label, () -> Sunflower.open(ui.a, h)));
            }
        }
    }

    private void web(String q) {
        try {
            ui.open(new Intent(Intent.ACTION_VIEW, Uri.parse(Search.engine(ui.prefs.getString("engine", "DuckDuckGo"))[1]
                    + URLEncoder.encode(q, "UTF-8"))));
        } catch (Exception e) {
            ui.toast(ui.tr("No browser found"));
        }
    }

    // ---- rows

    private View extraRow(Extra e) {
        LinearLayout row = new LinearLayout(ui.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(0, ui.dp(9), 0, ui.dp(9));
        TextView g = ui.text(20, true);
        g.setGravity(Gravity.CENTER);
        g.setText(e.glyph);
        row.addView(g, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(36)));
        TextView t = ui.text(18, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(e.label);
        t.setPaddingRelative(ui.dp(20), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        for (Button button : e.buttons) {
            TextView b = ui.text(20, false);
            b.setGravity(Gravity.CENTER);
            b.setText(button.glyph());
            b.setContentDescription(button.description());
            b.setOnClickListener(v -> button.action().run());
            row.addView(b, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        }
        row.setOnClickListener(v -> e.action.run());
        return row;
    }

    private View gridRow(GridRow g) {
        LinearLayout line = new LinearLayout(ui.a);
        line.setWeightSum(layout.drawerCols());
        boolean names = ui.prefs.getBoolean("drawer_labels", true); // set apart from the home's: known apps can go without
        for (Item it : g.items) {
            View v = entries.cell(it.key, names);
            if (v != null) line.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return line;
    }

    private static String gridKey(GridRow g) {
        StringBuilder s = new StringBuilder("g:");
        for (Item it : g.items) s.append(it.key).append(',');
        return s.toString();
    }

    private final class RowsAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }
        public int getViewTypeCount() { return 5; }
        public int getItemViewType(int i) {
            Object r = rows.get(i);
            return r instanceof String ? 0 : r instanceof Item ? 1 : r instanceof Extra ? 2 : r instanceof GridRow ? 4 : 3;
        }

        public View getView(int i, View old, ViewGroup parent) {
            Object r = rows.get(i);
            Object key = r instanceof GridRow ? gridKey((GridRow) r) : r instanceof FolderRow ? "f:" + ((FolderRow) r).id : r;
            View cached = r instanceof Extra ? null : rowCache.get(key);
            if (cached != null && cached.getParent() == null) return cached;
            View v;
            if (r instanceof Item) v = entries.appRow((Item) r, false);
            else if (r instanceof Extra) return extraRow((Extra) r);
            else if (r instanceof FolderRow) v = entries.folderRow(((FolderRow) r).id, false);
            else if (r instanceof GridRow) v = gridRow((GridRow) r);
            else {
                TextView h = ui.text(15, true);
                h.setTextColor(ui.accent);
                h.setText((String) r);
                h.setPaddingRelative(ui.dp(14), ui.dp(18), 0, ui.dp(4));
                v = h;
            }
            rowCache.put(key, v);
            return v;
        }
    }
}
