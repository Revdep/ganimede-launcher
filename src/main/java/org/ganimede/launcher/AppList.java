package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Gestures;
import org.ganimede.launcher.core.Search;

import android.Manifest;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** How many contacts match a query, and the rows read so far: the first few, or all of them after "Show all". */
    private record NumberRow(long id, String digits) {}

    private static final class ContactHits {
        final int count;
        final List<Extra> people;
        ContactHits(int count, List<Extra> people) { this.count = count; this.people = people; }
    }

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
    /** Contacts read one query at a time, off the main thread. */
    private static final ExecutorService CONTACTS = Executors.newSingleThreadExecutor();
    /** Contacts found per query (the last 20): typing back and forth costs nothing. */
    private final Map<String, ContactHits> contactHits = new LinkedHashMap<String, ContactHits>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ContactHits> e) { return size() > 20; }
    };
    /** The read asked last ("\n" at the end: all of them); a queued read that is no longer it is skipped. */
    private volatile String contactAsked = "";
    /** (contact id, digits of a number) of every phone number, read on the first number search and dropped when the search closes. */
    private volatile List<NumberRow> numberIndex;
    /** Bumped each time the search closes: a scan that began before it must not store its index. */
    private volatile int numberEpoch;
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
        if (q.isEmpty()) { numberIndex = null; numberEpoch++; contactHits.clear(); visibleCache = null; } // closed: contacts may change before the next search
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
        ContactHits known = contactHits.get(q);
        boolean all = q.equals(contactsAll);
        int n = known == null ? 0 : Search.shownContacts(known.count, all);
        if (known == null || known.people.size() < n) readContacts(q, all); // the first read, or the rest after Show all
        if (known == null || known.count == 0) return;
        n = Math.min(n, known.people.size());
        rows.add(ui.tr("Contacts"));
        rows.addAll(known.people.subList(0, n));
        if (n < known.count) rows.add(new Extra("…", ui.f("Show all (%d)", known.count), () -> { contactsAll = q; rebuild(); }));
    }

    private void readContacts(String q, boolean all) {
        String asked = all ? q + "\n" : q;
        if (asked.equals(contactAsked)) return; // already being read
        contactAsked = asked;
        CONTACTS.execute(() -> {
            if (!asked.equals(contactAsked)) return; // the text changed while this waited its turn
            ContactHits found = queryContacts(q, all);
            handler.post(() -> {
                // A failed read isn't cached as "nobody", nor asked again at once (that would loop while the text
                // stays): contactAsked keeps it until the text changes.
                if (found == null) return;
                contactHits.put(q, found);
                if (asked.equals(contactAsked)) contactAsked = "";
                if (shown.getAsBoolean() && q.equals(search.getText().toString().trim())) rebuild(); // still what's typed
            });
        });
    }

    /**
     * Only people I can call or write to (messenger accounts like WhatsApp or Telegram add contacts with a name and
     * neither). How many contacts match, starred first then by name, with the details (number, email) of only the rows shown:
     * the first few, or all of them after "Show all". Only contacts the search rule matches (name, email, company or nickname) are counted and shown.
     */
    private ContactHits queryContacts(String q, boolean all) {
        List<Long> found = new ArrayList<>();
        Set<Long> phoneless = new HashSet<>(); // reachable only if they have an email
        List<Uri> foundLooks = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();
        boolean byNumber = Search.isNumberQuery(q); // digits: the contacts whose number holds them, not the name filter
        Uri u = byNumber ? ContactsContract.Contacts.CONTENT_URI
                : Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q));
        try (Cursor c = ui.a.getContentResolver().query(u, new String[]{ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER},
                byNumber ? ContactsContract.Contacts._ID + " IN (" + numberContactIds(q) + ")" : null, null,
                ContactsContract.Contacts.STARRED + " DESC, " + ContactsContract.Contacts.SORT_KEY_PRIMARY)) {
            while (c != null && c.moveToNext()) {
                if (c.getString(2) == null) continue;
                if (!byNumber && c.getInt(3) == 0) phoneless.add(c.getLong(0));
                found.add(c.getLong(0));
                foundLooks.add(ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)));
                foundNames.add(c.getString(2));
            }
        } catch (Exception unavailable) { // provider busy or gone
            return null;
        }
        // the provider also matches mid-word and on other entries: each contact passes the search rule on its name or
        // on its email, company or nickname
        Set<Long> mailed = new HashSet<>();
        Map<Long, List<String>> others = byNumber ? Map.of() : otherEntries(found, mailed);
        List<Long> ids = new ArrayList<>();
        List<Uri> looks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < found.size(); i++) {
            if (phoneless.contains(found.get(i)) && !mailed.contains(found.get(i))) continue;
            if (!byNumber && !Search.contactMatches(foundNames.get(i), others.getOrDefault(found.get(i), List.of()), q)) continue;
            if (ids.size() >= Search.shownContacts(++count, all)) continue;
            ids.add(found.get(i));
            looks.add(foundLooks.get(i));
            names.add(foundNames.get(i));
        }
        Map<String, String> data = contactData(ids);
        List<Extra> people = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Uri look = looks.get(i);
            String name = names.get(i);
            Extra e = new Extra("☺", name, () -> ui.open(new Intent(Intent.ACTION_VIEW, look)));
            // Contact actions: call, message, email in one tap (the dialer opens; no calling permission).
            String phone = data.get(ids.get(i) + "|" + Phone.CONTENT_ITEM_TYPE), mail = data.get(ids.get(i) + "|" + Email.CONTENT_ITEM_TYPE);
            if (phone != null) {
                e.button("✆", ui.f("Call %s", name), () -> ui.open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))));
                e.button("✉", ui.f("Message %s", name), () -> ui.open(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))));
            }
            if (mail != null) e.button("@", ui.f("Email %s", name), () -> ui.open(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(mail, "@")))));
            people.add(e);
        }
        return new ContactHits(count, people);
    }

    /** The email addresses, companies and nicknames of each contact, by contact id; the ids of those with an email go into mailed. */
    private Map<Long, List<String>> otherEntries(List<Long> all, Set<Long> mailed) {
        Map<Long, List<String>> out = new HashMap<>();
        for (int from = 0; from < all.size(); from += 500) {
            List<Long> ids = all.subList(from, Math.min(all.size(), from + 500));
            String[] args = new String[ids.size() + 3];
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) { in.append(i == 0 ? "?" : ",?"); args[i] = String.valueOf(ids.get(i)); }
            args[ids.size()] = Email.CONTENT_ITEM_TYPE;
            args[ids.size() + 1] = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE;
            args[ids.size() + 2] = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE;
            try (Cursor d = ui.a.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1, ContactsContract.Data.MIMETYPE},
                    ContactsContract.Data.CONTACT_ID + " IN (" + in + ") AND " + ContactsContract.Data.MIMETYPE + " IN (?,?,?)",
                    args, null)) {
                while (d != null && d.moveToNext()) {
                    out.computeIfAbsent(d.getLong(0), k -> new ArrayList<>()).add(d.getString(1));
                    if (Email.CONTENT_ITEM_TYPE.equals(d.getString(2))) mailed.add(d.getLong(0));
                }
            } catch (Exception e) {
                // the name alone decides
            }
        }
        return out;
    }

    /** Ids (comma separated) of the contacts with a phone number holding the typed digits; the rule is Search.numberMatches. */
    private String numberContactIds(String q) {
        List<NumberRow> index = numberIndex;
        if (index == null) { // one scan of all the numbers while the search is open, not one per key
            int epoch = numberEpoch;
            index = new ArrayList<>();
            try (Cursor p = ui.a.getContentResolver().query(Phone.CONTENT_URI, new String[]{Phone.CONTACT_ID, Phone.NUMBER}, null, null, null)) {
                while (p != null && p.moveToNext()) if (p.getString(1) != null) index.add(new NumberRow(p.getLong(0), Search.digits(p.getString(1))));
            }
            if (epoch == numberEpoch) numberIndex = index; // not if the search closed meanwhile
        }
        StringBuilder ids = new StringBuilder();
        Search.NumberQuery typed = Search.numberQuery(q); // read once, not per number
        for (NumberRow n : index) if (typed.matches(n.digits)) ids.append(ids.length() == 0 ? "" : ",").append(n.id);
        return ids.toString();
    }

    /**
     * The first phone number and email of each contact (data1, primary first), keyed "id|mimetype": one query for up to
     * 500 of them instead of two per contact (SQLite takes at most 999 parameters before Android 11).
     */
    private Map<String, String> contactData(List<Long> all) {
        Map<String, String> first = new HashMap<>();
        for (int from = 0; from < all.size(); from += 500) {
            List<Long> ids = all.subList(from, Math.min(all.size(), from + 500));
            String[] args = new String[ids.size() + 2];
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) { in.append(i == 0 ? "?" : ",?"); args[i] = String.valueOf(ids.get(i)); }
            args[ids.size()] = Phone.CONTENT_ITEM_TYPE;
            args[ids.size() + 1] = Email.CONTENT_ITEM_TYPE;
            try (Cursor d = ui.a.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1},
                    ContactsContract.Data.CONTACT_ID + " IN (" + in + ") AND " + ContactsContract.Data.MIMETYPE + " IN (?,?)",
                    args, ContactsContract.Data.IS_PRIMARY + " DESC")) {
                while (d != null && d.moveToNext()) {
                    String k = d.getLong(0) + "|" + d.getString(1);
                    if (!first.containsKey(k)) first.put(k, d.getString(2));
                }
            } catch (Exception e) {
                // no buttons
            }
        }
        return first;
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
