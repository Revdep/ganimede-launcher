package org.ganimede.launcher;

import org.ganimede.launcher.core.Backup;
import org.ganimede.launcher.core.Blocks;
import org.ganimede.launcher.core.LimitedInputStream;
import org.ganimede.launcher.core.Paging;
import org.ganimede.launcher.core.Safe;

import android.appwidget.AppWidgetHost;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serializes settings and safely replaces them from a backup chosen by the user. */
final class SettingsBackup {
    private final Context context;
    private final SharedPreferences prefs;

    /** context: the application's, since the work outlives the screen that starts it. */
    SettingsBackup(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    /** Writes the backup to a file chosen by the user; called on a background thread. */
    void save(Uri uri) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            out.write(create().toString(1).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject create() throws Exception {
        JSONObject vals = new JSONObject();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            String k = e.getKey();
            if (!Backup.travels(k)) continue;
            if (k.equals("blocks")) v = portableBlocks(String.valueOf(v));
            if (k.startsWith("folder:") && (v = portableFolder(String.valueOf(v))) == null) continue;
            JSONObject t = new JSONObject();
            if (v instanceof Set) t.put("t", "set").put("v", new JSONArray((Set<?>) v));
            else if (v instanceof Boolean) t.put("t", "b").put("v", v);
            else if (v instanceof Integer) t.put("t", "i").put("v", v);
            else if (v instanceof Long) t.put("t", "l").put("v", v);
            else if (v instanceof Float) t.put("t", "f").put("v", ((Float) v).doubleValue());
            else t.put("t", "s").put("v", String.valueOf(v));
            vals.put(k, t);
        }
        return new JSONObject().put("app", "ganimede-launcher").put("version", 1).put("values", vals);
    }

    /** Replaces the settings with a backup file's; called on a background thread. */
    void restore(Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            byte[] b = LimitedInputStream.readAll(in, 4 << 20); // a real backup is a few KB
            JSONObject o = new JSONObject(new String(b, java.nio.charset.StandardCharsets.UTF_8));
            if (!"ganimede-launcher".equals(o.optString("app")) || o.optInt("version") > 1) throw new Exception("not ours");
            JSONObject vals = o.getJSONObject("values");
            Map<String, Object> restored = new HashMap<>();
            for (Iterator<String> it = vals.keys(); it.hasNext(); ) {
                String k = it.next();
                if (!Backup.restorable(k)) continue; // only Ganimede's own settings come back from a file
                JSONObject t = vals.getJSONObject(k);
                Object v;
                switch (t.getString("t")) {
                    case "set": {
                        Set<String> s = new HashSet<>();
                        JSONArray a = t.getJSONArray("v");
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        v = s;
                        break;
                    }
                    case "b": v = t.getBoolean("v"); break;
                    case "i": v = t.getInt("v"); break;
                    case "l": v = t.getLong("v"); break;
                    case "f": v = (float) t.getDouble("v"); break;
                    default: v = safeLinks(k, t.getString("v"));
                }
                if (v == null) continue; // a link that may not come back
                if (!fits(k, v)) continue; // a wrong type or size would crash the home screen: left out
                if (k.startsWith("folder:") && (v = portableFolder((String) v)) == null) continue; // an old file may still hold the other phone's ids
                restored.put(k, v);
            }
            Map<String, ?> old = prefs.getAll();
            List<Integer> folderWidgets = new ArrayList<>();
            for (Map.Entry<String, ?> f : old.entrySet()) {
                if (!f.getKey().startsWith("folder:") || !(f.getValue() instanceof String)) continue;
                JSONArray ws;
                try { ws = new JSONObject((String) f.getValue()).optJSONArray("widgets"); } catch (Exception broken) { continue; } // a broken folder has no widgets to free
                for (int i = 0; ws != null && i < ws.length(); i++) {
                    int wid = ws.optInt(i, -1);
                    if (wid >= 0) folderWidgets.add(wid);
                }
            }
            List<String> blockIds = new ArrayList<>();
            Object oldBlocks = old.get("blocks"), oldStack = old.get("stack");
            if (oldBlocks instanceof String) try {
                JSONArray bs = new JSONArray((String) oldBlocks);
                for (int i = 0; i < bs.length(); i++) blockIds.add(bs.optJSONObject(i) == null ? "" : bs.optJSONObject(i).optString("id"));
            } catch (Exception ignored) { }
            List<Integer> widgetIds = Backup.widgetsToFree(folderWidgets, blockIds, oldStack instanceof String ? (String) oldStack : "");
            Map<String, Object> values = Backup.kept(old); // this phone's own state, then the file's settings
            values.putAll(restored);
            SharedPreferences.Editor e = prefs.edit().clear();
            for (Map.Entry<String, Object> value : values.entrySet()) Prefs.put(e, value.getKey(), value.getValue());
            if (!e.putBoolean("onboarded", true).commit()) throw new Exception("could not save");
            Prefs.prepare(prefs);
            T.refresh(prefs); // Android 8–10 do not consistently report a cleared preference to the language listener
            // Only now can old widget ids be freed: until commit succeeded, the old preferences still referred to them.
            AppWidgetHost host = new AppWidgetHost(context, 1024);
            for (int wid : widgetIds) host.deleteAppWidgetId(wid);
            if (restored.containsKey("blocks")) restoreStackBlocks();
        }
    }

    /** A folder without its widget ids (they belong to one phone: elsewhere they point at nothing or the wrong widget), null if broken. */
    private static String portableFolder(String json) {
        try { JSONObject f = new JSONObject(json); f.remove("widgets"); return f.toString(); } catch (Exception broken) { return null; }
    }

    /** A value from a backup file: safe to keep, and a home grid of cell numbers only. */
    static boolean fits(String k, Object v) {
        if (!Backup.fits(k, v)) return false;
        if (k.equals("blocks")) {
            try {
                JSONArray a = new JSONArray((String) v);
                if (a.length() > 1) return false;
                if (a.length() == 0) return true;
                JSONObject b = a.getJSONObject(0);
                return b.length() == 6 && b.optString("id").equals("clock") && b.optInt("page", -1) >= 0
                        && b.optInt("page") < Paging.MAX_PAGES && b.optInt("col", -1) >= 0 && b.optInt("col") < 10
                        && b.optInt("row", -1) >= 0 && b.optInt("row") < 8
                        && b.optInt("colSpan", 0) > 0 && b.optInt("colSpan") <= 10
                        && b.optInt("rowSpan", 0) > 0 && b.optInt("rowSpan") <= 8;
            } catch (Exception e) { return false; }
        }
        if (!k.equals("grid")) return true;
        try {
            return Backup.cellsFit(new JSONObject((String) v).keys());
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns restored text after rejecting links which must not travel between phones. */
    private static String safeLinks(String k, String v) {
        if (k.equals("custom_shortcuts")) {
            JSONArray in = Sunflower.links(v), out = new JSONArray();
            for (int i = 0; i < in.length(); i++) {
                JSONObject o = in.optJSONObject(i);
                if (o != null && Safe.restorableLink(o.optString("uri"))) out.put(o);
            }
            return out.toString();
        }
        if (k.startsWith("fab_") && v.startsWith("link:")) {
            String[] parts = v.substring(5).split("\\|", 2);
            return parts.length == 2 && Safe.restorableLink(parts[1]) ? v : null;
        }
        return v;
    }

    /** Widget ids stay on this phone; only the clock rectangle is portable. */
    private static String portableBlocks(String saved) {
        JSONArray out = new JSONArray();
        try {
            JSONArray in = new JSONArray(saved);
            for (int i = 0; i < in.length(); i++) {
                JSONObject b = in.optJSONObject(i);
                if (b != null && b.optString("id").equals("clock")) { out.put(b); break; }
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    /** Re-adds this phone's preserved List-mode stack around the restored portable clock block. */
    private void restoreStackBlocks() {
        Layout l = new Layout(context, prefs);
        List<Blocks.Item> blocks = l.blocks();
        Map<Integer, String> cells = l.grid();
        int pages = l.pages(), height = Math.min(2, l.rows());
        String stack = prefs.getString("stack", "");
        for (String id : stack.isEmpty() ? List.<String>of() : java.util.Arrays.asList(stack.split(","))) {
            Blocks.Result r = Blocks.add(id, l.cols(), height, 0, pages, l.cols(), l.rows(), blocks, cells);
            blocks = r.blocks;
            cells = r.cells;
            pages = r.pages;
        }
        l.savePlacement(blocks, cells, pages);
    }
}
