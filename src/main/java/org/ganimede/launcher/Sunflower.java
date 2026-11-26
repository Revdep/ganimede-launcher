package org.ganimede.launcher;

import org.ganimede.launcher.core.Search;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal search, entirely offline: the shortcuts apps publish
 * (Android's own shortcut API, readable by the default home app), Android settings pages, and the person's
 * own links (pref "custom_shortcuts": [{"name": ..., "uri": ...}]).
 */
final class Sunflower {
    static final class Hit {
        final String label, detail;
        final ShortcutInfo shortcut; // an app shortcut, or null
        final Intent intent;         // a settings page or custom link, or null

        Hit(String label, String detail, ShortcutInfo shortcut, Intent intent) {
            this.label = label;
            this.detail = detail;
            this.shortcut = shortcut;
            this.intent = intent;
        }
    }

    /** Android settings pages: English name (translated by T) and the screen's action. */
    private static final String[][] PAGES = {
            {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS}, {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Mobile data", Settings.ACTION_DATA_ROAMING_SETTINGS}, {"Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS},
            {"Hotspot and tethering", "android.settings.TETHER_SETTINGS"}, {"NFC", Settings.ACTION_NFC_SETTINGS},
            {"Display and brightness", Settings.ACTION_DISPLAY_SETTINGS}, {"Sound and vibration", Settings.ACTION_SOUND_SETTINGS},
            {"Do Not Disturb", "android.settings.ZEN_MODE_SETTINGS"}, {"Battery", Intent.ACTION_POWER_USAGE_SUMMARY},
            {"Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS}, {"Apps", Settings.ACTION_APPLICATION_SETTINGS},
            {"Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS}, {"App notifications", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"},
            {"Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS}, {"Security", Settings.ACTION_SECURITY_SETTINGS},
            {"Privacy", Settings.ACTION_PRIVACY_SETTINGS}, {"Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS},
            {"Language and keyboard", Settings.ACTION_LOCALE_SETTINGS}, {"Date and time", Settings.ACTION_DATE_SETTINGS},
            {"Accounts", Settings.ACTION_SYNC_SETTINGS}, {"Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
            {"About phone", Settings.ACTION_DEVICE_INFO_SETTINGS}, {"Wallpaper and style", Intent.ACTION_SET_WALLPAPER},
            {"VPN", Settings.ACTION_VPN_SETTINGS}, {"Cast", Settings.ACTION_CAST_SETTINGS},
            {"Night Light", "android.settings.NIGHT_DISPLAY_SETTINGS"}, {"Data usage", Settings.ACTION_DATA_USAGE_SETTINGS}};

    /**
     * Ready-made quick actions (new alarm, new timer, compose…), kept to the ones
     * Android itself offers: English name, then the intent action and optional data after "|".
     */
    private static final String[][] ACTIONS = {
            {"New alarm", android.provider.AlarmClock.ACTION_SET_ALARM}, {"New timer", android.provider.AlarmClock.ACTION_SET_TIMER},
            {"Alarms", android.provider.AlarmClock.ACTION_SHOW_ALARMS}, {"New event", Intent.ACTION_INSERT + "|event"},
            {"New contact", Intent.ACTION_INSERT + "|contact"}, {"New email", Intent.ACTION_SENDTO + "|mailto:"},
            {"New message", Intent.ACTION_SENDTO + "|smsto:"}, {"Phone dialer", Intent.ACTION_DIAL},
            {"Take a photo", android.provider.MediaStore.ACTION_IMAGE_CAPTURE},
            {"Record a video", android.provider.MediaStore.ACTION_VIDEO_CAPTURE}};

    static List<Hit> actions(Context c, String q) {
        List<Hit> out = new ArrayList<>();
        for (String[] a : ACTIONS) {
            String name = T.t(c, a[0]);
            if (Search.score(name, q) == 0 && Search.score(a[0], q) == 0) continue;
            String[] p = a[1].split("\\|", 2);
            Intent i = new Intent(p[0]);
            if (p.length > 1) {
                if (p[1].equals("event")) i.setData(android.provider.CalendarContract.Events.CONTENT_URI);
                else if (p[1].equals("contact")) i.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
                else i.setData(Uri.parse(p[1]));
            }
            if (opens(c, i)) out.add(new Hit(name, null, null, i));
        }
        Search.sortByScore(out, h -> h.label, q);
        return out;
    }

    /** Whether some app opens an intent; asked once per app load (reindex forgets), not on every key typed. */
    private static final Map<String, Boolean> opens = new HashMap<>();

    static boolean opens(Context c, Intent i) {
        String k = i.toUri(0);
        synchronized (opens) {
            Boolean known = opens.get(k);
            if (known != null) return known;
        }
        boolean found = i.resolveActivity(c.getPackageManager()) != null;
        synchronized (opens) {
            opens.put(k, found);
        }
        return found;
    }

    /** My links ("custom_shortcuts": [{"name": ..., "uri": ...}]); empty when the text isn't such a list. */
    static JSONArray links(String json) {
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static JSONArray links(Context c) {
        return links(Prefs.of(c).getString("custom_shortcuts", "[]"));
    }

    /** My links containing %s ("search shortcuts"): what was typed goes in place of %s. */
    static List<Hit> searches(Context c, String q) {
        List<Hit> out = new ArrayList<>();
        JSONArray a = links(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            String name = o == null ? "" : o.optString("name"), uri = o == null ? "" : o.optString("uri");
            if (!uri.contains("%s")) continue;
            Intent it = intent(uri.replace("%s", Uri.encode(q)));
            if (it != null) out.add(new Hit(T.f(c, "Search “%s” on %s", q, name), uri, null, it));
        }
        return out;
    }

    private static final List<ShortcutInfo> index = new ArrayList<>();

    /** Re-reads every app's shortcuts (only the default home app may; otherwise the index stays empty). */
    static void reindex(Context c) {
        List<ShortcutInfo> out = new ArrayList<>();
        LauncherApps la = c.getSystemService(LauncherApps.class);
        try {
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            for (UserHandle u : la.getProfiles()) {
                List<ShortcutInfo> l = la.getShortcuts(q, u);
                if (l != null) out.addAll(l);
            }
        } catch (Exception ignored) {
            // Not the default home app yet.
        }
        synchronized (index) {
            index.clear();
            index.addAll(out);
        }
        synchronized (opens) {
            opens.clear(); // apps came or went
        }
    }

    static List<Hit> shortcuts(String q, java.util.function.Function<String, String> appName) {
        List<Hit> out = new ArrayList<>();
        synchronized (index) {
            for (ShortcutInfo s : index) {
                CharSequence l = s.getLongLabel() != null ? s.getLongLabel() : s.getShortLabel();
                if (l == null || !s.isEnabled()) continue;
                String app = appName.apply(s.getPackage());
                if (Search.score(l.toString(), q) > 0 || (app != null && Search.score(app, q) >= 16 * q.length()))
                    out.add(new Hit(l.toString(), app, s, null));
                if (out.size() >= 8) break;
            }
        }
        Search.sortByScore(out, h -> h.label, q);
        return out;
    }

    static List<Hit> settings(Context c, String q) {
        List<Hit> out = new ArrayList<>();
        for (String[] p : PAGES) {
            String name = T.t(c, p[0]);
            if (Search.score(name, q) > 0 || Search.score(p[0], q) > 0) {
                Intent i = new Intent(p[1]);
                if (opens(c, i)) out.add(new Hit(name, T.t(c, "Android settings"), null, i));
            }
        }
        return out;
    }

    static List<Hit> custom(Context c, String q) {
        List<Hit> out = new ArrayList<>();
        JSONArray a = links(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            String name = o == null ? "" : o.optString("name"), uri = o == null ? "" : o.optString("uri");
            if (name.isEmpty() || uri.isEmpty() || uri.contains("%s") || (!q.isEmpty() && Search.score(name, q) == 0)) continue;
            Intent it = intent(uri);
            if (it != null) out.add(new Hit(name, uri, null, it));
        }
        return out;
    }

    /** "intent:" links become explicit intents; anything else (https:, tel:, geo:, app links) opens with VIEW. */
    static Intent intent(String uri) {
        try {
            Intent i;
            if (uri.startsWith("intent:")) {
                i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                i.setSelector(null);
                i.setComponent(null); // only implicit intents: a link can't target a private component
                // Never hand on Ganimede's own access (contacts, calendar) through a stored link, as browsers do.
                i.setFlags(i.getFlags() & ~(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setClipData(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            }
            String scheme = i.getData() == null ? null : i.getData().getScheme();
            if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) return null; // no private data
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    static void open(Context c, Hit h) {
        if (h.shortcut == null) { Ui.start(c, new Intent(h.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
        try {
            c.getSystemService(LauncherApps.class).startShortcut(h.shortcut, null, null);
        } catch (RuntimeException e) {
            Ui.cantOpen(c);
        }
    }

    private Sunflower() {}
}
