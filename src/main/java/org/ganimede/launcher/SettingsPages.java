package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Search;
import org.ganimede.launcher.core.Summary;
import org.ganimede.launcher.core.Wx;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** The settings home, and the pages that only list switches and choices: weather, search, wellbeing, backup and the like. */
final class SettingsPages {
    private static final int REQ_SAVE = 21, REQ_LOAD = 22;
    private final SettingsKit kit;
    private final SettingsPerms perms;
    private final Runnable restart;
    private final Runnable restored; // what a successful restore does on the page showing now
    private final SettingsBackup backup;
    private Uri confirming; // the file the open "Restore backup?" asks about
    private FileJob job; // the backup or restore this page shows the end of, while it runs

    /** restart: runs the setup wizard again, from its first step. */
    SettingsPages(SettingsKit kit, SettingsPerms perms, Runnable restart, Runnable restored) {
        this.kit = kit;
        this.perms = perms;
        this.restart = restart;
        this.restored = restored;
        backup = new SettingsBackup(kit.activity(), kit.prefs());
    }

    void home() {
        if (!perms.isHome()) {
            LinearLayout c = kit.card();
            GradientDrawable warn = kit.round(SettingsKit.CARD, 24);
            warn.setStroke(kit.ui.dp(2), SettingsKit.BAD);
            c.setBackground(warn);
            kit.para(c, "⚠  " + kit.ui.tr("Finish setting up"), 18, Colors.WHITE).setTypeface(Typeface.DEFAULT_BOLD);
            perms.homeRow(c, "Make Ganimede your home screen");
        }
        TextView hint = kit.text(kit.ui.tr("Touch a part of the preview to adjust it"), 14, SettingsKit.SUB);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, kit.ui.dp(4));
        kit.box.addView(hint);
        LinearLayout c = null;
        for (Page k : Page.CATEGORIES) {
            if (k.group != null) c = kit.card(k.group);
            kit.link(c, k.glyph, kit.ui.tr(k.title), summary(k), () -> kit.open(k));
        }
    }

    /** One line on the home page saying how each category is set now. */
    private String summary(Page page) {
        return switch (page) {
            case HOMESCREEN -> {
                int accent = SettingsKit.indexOf(SettingsLook.accents(), kit.ui.accent); // -1: a colour of its own, no name
                yield kit.ui.tr(new Layout(kit.activity(), kit.prefs()).classic() ? "Classic" : "List") + " · "
                        + name(ClockView.NAMES, ClockView.STYLES, kit.prefs().getString("clock_style", "overlap")) + " · "
                        + name(SettingsLook.fontNames(), SettingsLook.fonts(), kit.prefs().getString("font", "sans-serif"))
                        + (accent < 0 ? "" : " · " + kit.ui.tr(SettingsLook.accentNames()[accent]));
            }
            case BUTTON -> !kit.prefs().getBoolean("fab", false) ? kit.ui.tr("Off")
                    : kit.ui.tr("Tap") + ": " + Actions.name(kit.activity(), kit.prefs().getString("fab_tap", Actions.DEFAULTS[0]));
            case WEATHER -> {
                JSONObject w = WeatherStore.current(kit.prefs());
                yield w == null ? kit.ui.tr("Waiting for Breezy Weather")
                        : Wx.sym(w.optInt("k", 3)) + " " + Wx.temp(w.optDouble("t"), kit.prefs().getBoolean("fahrenheit", false)) + " · " + kit.ui.f("received at %s", kit.time(w.optLong("time")));
            }
            case CALENDAR -> {
                List<String> on = new ArrayList<>();
                if (kit.prefs().getBoolean("agenda", true)) on.add(kit.ui.tr("Agenda"));
                if (kit.prefs().getBoolean("event_preview", true)) on.add(kit.ui.tr("Next event"));
                if (kit.prefs().getBoolean("media_player", true)) on.add(kit.ui.tr("Media player"));
                if (kit.prefs().getBoolean("music_on_headset", true)) on.add(kit.ui.tr("Music apps"));
                yield on.isEmpty() ? kit.ui.tr("All off") : String.join(", ", on);
            }
            case SEARCH -> {
                int links = Sunflower.links(kit.activity()).length();
                yield kit.ui.f(links == 1 ? "%s · %d custom shortcut" : "%s · %d custom shortcuts", kit.prefs().getString("engine", "DuckDuckGo"), links);
            }
            case GESTURES -> kit.ui.tr(kit.prefs().getBoolean("quick_lock", false) ? "Quick Lock on" : "Quick Lock off") + " · "
                    + kit.ui.tr(kit.prefs().getBoolean("haptics", true) ? "vibration on" : "vibration off");
            case WELLBEING -> kit.prefs().getBoolean("summary", false) ? kit.ui.f("Summary on, next around %s", kit.time(nextSummary())) : kit.ui.tr("Summary off");
            case PRIVACY -> perms.missing() == 0 ? kit.ui.tr("Everything you use is allowed") : kit.ui.tr("Something needs your OK");
            case HIDDEN -> {
                int n = kit.prefs().getStringSet("hidden", Set.of()).size();
                yield n == 0 ? kit.ui.tr("No hidden apps") : kit.ui.f("%d hidden", n);
            }
            case BACKUP -> kit.ui.tr("Save or restore everything with a file");
            case LANGUAGE -> langName();
            case ABOUT -> kit.ui.f("Version %s · free and open source", version());
            case HOME, SHORTCUTS, W_LANG, W_HOME, W_MODE, W_CUSTOM, W_FAVS, W_ICONS, W_FONT, W_CLOCK, W_BUTTON, W_DONE -> null;
        };
    }

    /** The version this build carries (build.gradle versionName). */
    private String version() {
        try {
            return kit.activity().getPackageManager().getPackageInfo(kit.activity().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    void gestures() {
        LinearLayout c = kit.card();
        if (Build.VERSION.SDK_INT >= 28) // Android's lock action exists from Android 9
            kit.toggle(c, kit.ui.tr("Quick Lock"), kit.ui.tr("Double-tap the home screen or the alphabet to turn the screen off"), "quick_lock", false, perms.needLock("quick_lock", kit::render));
        if (kit.prefs().getBoolean("quick_lock", false) && !perms.lockAllowed())
            kit.perm(c, false, kit.ui.tr("Accessibility for Quick Lock"), kit.ui.tr("It only runs system actions (lock, screenshot, recent apps…) and reads nothing on screen"), perms::fixLock);
        kit.toggle(c, kit.ui.tr("Vibration feedback"), null, "haptics", true, null);
    }

    void backup() {
        LinearLayout c = kit.card();
        kit.para(c, kit.ui.tr("Saves favourites, pop-ups, names, hidden apps and every setting to a file you choose. Widgets and pinned shortcuts have to be added again after a restore. Keep the file private: it lists your apps."), 15, SettingsKit.SUB);
        c = kit.card();
        kit.row(c, "backup", kit.ui.tr("Create backup"), kit.ui.tr("Choose where to save the file"), () -> pick(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "ganimede-backup.json"), REQ_SAVE));
        restoreRow(c);
    }

    /** The one way in to a restore, on the Backup page and on the wizard's first step. */
    void restoreRow(LinearLayout c) {
        kit.row(c, "folder", kit.ui.tr("Restore a backup"), kit.ui.tr("Replaces your current setup"), () -> pick(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_LOAD));
    }

    /** The file chosen for a backup (REQ_SAVE) or a restore (REQ_LOAD). */
    void fileResult(int req, int res, Intent data) {
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        SettingsBackup b = backup; // the work holds this and the application's context, never the screen
        if (req == REQ_SAVE) {
            fileJob(() -> { b.save(u); return null; }, "Backup created", "Could not write the backup", null);
        } else if (req == REQ_LOAD) {
            confirmRestore(u);
        }
    }

    /** The restore confirmation; the file it asks about is kept in confirming so a rebuilt screen can ask again. */
    void confirmRestore(Uri u) {
        SettingsBackup b = backup;
        confirming = u;
        Ui.dialog(kit.activity())
                .setTitle(kit.ui.tr("Restore backup?"))
                .setMessage(kit.ui.tr("This replaces your current setup and can't be undone."))
                .setPositiveButton(kit.ui.tr("Restore"), (d, w) -> {
                    confirming = null;
                    fileJob(() -> { b.restore(u); return null; }, "Backup restored", "That file isn't a Ganimede backup", p -> p.restored.run());
                })
                .setNegativeButton(kit.ui.tr("Cancel"), (d, w) -> confirming = null)
                .setOnCancelListener(d -> confirming = null).show();
    }

    /** The file whose restore is being confirmed, or null. */
    Uri confirming() { return confirming; }

    /** A backup or restore still running, for the screen Android builds in place of this one (rotation); null if none. */
    Object running() { return job; }

    /** Takes over the backup or restore of the screen this one replaces: its end now shows on this page. */
    void adopt(Object retained) {
        if (retained instanceof FileJob j) (job = j).owner = this;
    }

    /** The screen is going: a job still running no longer shows on this page (a rebuilt one adopts it). */
    void stop() {
        if (job != null && job.owner == this) job.owner = null;
    }

    /** A picked image becomes the button's private icon; the page shows the result (or the refusal) when it ends. */
    void buttonIcon(Uri uri) {
        Context app = kit.activity().getApplicationContext();
        fileJob(() -> {
            ButtonIcon.save(app, uri);
            Prefs.of(app).edit().putString("fab_icon", "custom").apply();
            return null;
        }, null, "That image could not be used", p -> p.kit.render());
    }

    /** File work off the main thread (a storage provider can take seconds), then one message on how it went. */
    private void fileJob(java.util.concurrent.Callable<?> work, String ok, String failed, Consumer<SettingsPages> after) {
        job = new FileJob(kit.activity(), this);
        job.start(work, ok, failed, after);
    }

    private void ended(Consumer<SettingsPages> after) {
        job = null;
        if (after != null) after.accept(this); // this page, not the one that started the job: that screen may be gone
    }

    /**
     * A backup or restore under way. Like a ViewModel it never holds a screen: the work has the application's context
     * only, and the page showing it registers as owner while it lives. SettingsActivity retains the job across a
     * rebuild (onRetainNonConfigurationInstance) and the new pages adopt it. owner is used on the main thread only.
     */
    private static final class FileJob {
        private final Context app;
        private SettingsPages owner;

        FileJob(Context c, SettingsPages owner) {
            app = c.getApplicationContext();
            this.owner = owner;
        }

        void start(java.util.concurrent.Callable<?> work, String ok, String failed, Consumer<SettingsPages> after) {
            Handler main = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                boolean done = true;
                try {
                    work.call();
                } catch (Exception | StackOverflowError | OutOfMemoryError e) { // deeply nested JSON overflows the parser's stack
                    done = false;
                }
                boolean d = done;
                main.post(() -> end(d ? ok : failed, d ? after : null));
            }, "ganimede-file").start();
        }

        private void end(String message, Consumer<SettingsPages> after) {
            if (message != null) Ui.toast(app, T.t(app, message), Toast.LENGTH_SHORT); // also when no page shows it any more
            if (owner != null) owner.ended(after);
        }
    }

    void language() {
        LinearLayout c = kit.card();
        kit.languages(c);
        kit.para(c, kit.ui.tr("The whole launcher switches language right away."), 14, SettingsKit.SUB);
    }

    void about() {
        LinearLayout c = kit.card();
        kit.para(c, "Ganimede Launcher", 22, Colors.WHITE).setTypeface(Typeface.DEFAULT_BOLD);
        kit.para(c, kit.ui.f("Version %s · GPL-3.0 licence", version()), 15, SettingsKit.SUB);
        kit.para(c, kit.ui.tr("A free, minimalist home screen without Google: no ads, no tracking, no internet."), 15, Colors.WHITE);
        kit.para(c, kit.ui.tr("Weather by Breezy Weather, sent from your own phone."), 15, SettingsKit.SUB);
        kit.para(c, kit.ui.tr("Built-in icons: Arcticons by Donnnno and contributors (github.com/Arcticons-Team/Arcticons), CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/), converted and algorithmically restyled."), 15, SettingsKit.SUB);
        kit.row(c, "gear", kit.ui.tr("App info"), null, () -> Ui.appInfo(kit.activity()));
        kit.row(c, "arrow", kit.ui.tr("Run the setup wizard again"), null, restart);
    }

    private String name(String[] names, String[] values, String v) {
        return kit.ui.tr(names[Math.max(0, SettingsKit.indexOf(values, v))]);
    }

    private long nextSummary() {
        return Summary.upcoming(kit.prefs().getLong("summary_last", 0), System.currentTimeMillis());
    }

    private String langName() {
        String l = kit.prefs().getString("lang", "");
        return l.equals("it") ? "Italiano" : l.equals("en") ? "English" : kit.ui.tr("Phone language");
    }

    private void pick(Intent i, int req) {
        if (job != null) { // one at a time: a backup and a restore read and replace the same settings
            kit.ui.toast(kit.ui.tr("Wait until the backup or restore under way has finished"));
            return;
        }
        try {
            kit.activity().startActivityForResult(i, req);
        } catch (Exception e) {
            kit.ui.toast(kit.ui.tr("No file picker on this phone"));
        }
    }

    void weather() {
        JSONObject w = WeatherStore.current(kit.prefs());
        boolean fahr = kit.prefs().getBoolean("fahrenheit", false);
        LinearLayout c = kit.card("Now");
        if (w == null) {
            kit.row(c, "cloud", kit.ui.tr("No weather received yet"), kit.ui.tr("Switch on sharing in Breezy Weather, as explained below."), null);
        } else {
            String place = w.optString("place");
            kit.row(c, "cloud", Wx.temp(w.optDouble("t"), fahr) + "  " + kit.ui.tr(Wx.txt(w.optInt("k", 3))),
                    kit.ui.f("%s · received at %s", place.isEmpty() ? kit.ui.tr("Breezy Weather") : place, kit.time(w.optLong("time"))), null);
        }

        c = kit.card("How it works");
        kit.para(c, kit.ui.tr("Ganimede never connects to the internet. The weather comes only from the Breezy Weather app on this phone, which passes it to Ganimede through Android's internal sharing every time it updates."), 15, SettingsKit.SUB);
        kit.para(c, kit.ui.tr("To switch it on, in Breezy Weather open:\n1. Settings\n2. Widgets & notifications (or “Data sharing”)\n3. “Send Gadgetbridge data”\n4. Choose Ganimede\nThe names may be slightly different in your version of Breezy."), 15, Colors.WHITE);
        Intent breezy = WeatherStore.breezy(kit.activity());
        if (breezy != null) kit.row(c, "cloud", kit.ui.tr("Open Breezy Weather"), null, () -> Ui.start(kit.activity(), breezy));
        else kit.row(c, "backup", kit.ui.tr("Get Breezy Weather"), kit.ui.tr("Free and open source. Opens the download page in your browser."),
                () -> Ui.start(kit.activity(), new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/breezy-weather/breezy-weather/releases"))));

        c = kit.card("Options");
        kit.toggle(c, kit.ui.tr("Show weather on the home screen"), null, "show_weather", true, null);
        kit.toggle(c, kit.ui.tr("Show °F instead of °C"), null, "fahrenheit", false, kit::render);
        kit.sub(c, kit.ui.tr("Tapping the weather opens"));
        kit.tiles(c, 2, "breezy".equals(kit.prefs().getString("wx_tap", "forecast")) ? 1 : 0, new String[]{kit.ui.tr("Forecast"), kit.ui.tr("Breezy Weather")},
                null, i -> kit.put("wx_tap", i == 0 ? "forecast" : "breezy"));
    }

    void calendar() {
        LinearLayout c = kit.card("Calendar");
        if (!perms.granted(Manifest.permission.READ_CALENDAR))
            kit.perm(c, false, kit.ui.tr("Calendar access"), kit.ui.tr("Needed for the agenda and the next event"), () -> perms.ask(Manifest.permission.READ_CALENDAR));
        kit.toggle(c, kit.ui.tr("Agenda when tapping the date"), null, "agenda", true, null);
        kit.toggle(c, kit.ui.tr("Show the next event on the home screen"), null, "event_preview", true, null);
        int hiddenCals = kit.prefs().getStringSet("cal_hidden", Set.of()).size();
        kit.row(c, "calendar", kit.ui.tr("Choose calendars"), hiddenCals == 0 ? kit.ui.tr("All calendars shown") : kit.ui.f("%d hidden", hiddenCals), this::chooseCalendars);

        c = kit.card("Music");
        perms.mediaPlayerToggle(c);
        kit.toggle(c, kit.ui.tr("Show music apps when headphones or speakers connect"), null, "music_on_headset", true, null);
        kit.toggle(c, kit.ui.tr("Also for hearing aids"), null, "music_hearing_aids", false, null);
        apps(c, "music", kit.ui.tr("Choose music apps"), "music_apps", false);

        c = kit.card("Smart lists");
        kit.toggle(c, kit.ui.tr("Suggest often-used apps under favourites"), null, "suggest", false, null);
        kit.toggle(c, kit.ui.tr("Sort the app list by usage within each letter"), null, "smart_sort", false, null);
        kit.para(c, kit.ui.tr("Pop-up folders can hold apps and widgets; the Ganimede button has its own. Long-press an empty part of the home screen to make one."), 14, SettingsKit.SUB);
    }

    void search() {
        LinearLayout c = kit.card("Search");
        kit.toggle(c, kit.ui.tr("Swipe up on the home screen to search"), null, "swipe_search", true, null);
        kit.toggle(c, kit.ui.tr("Find contacts"), kit.ui.tr("Asks to read your contacts"), "contacts_search", false, () -> {
            if (kit.prefs().getBoolean("contacts_search", false)) perms.ask(Manifest.permission.READ_CONTACTS);
        });
        kit.toggle(c, kit.ui.tr("Search inside apps' shortcuts"), kit.ui.tr("Like “new message” or “navigate home” from your apps"), "search_shortcuts", true, null);
        kit.toggle(c, kit.ui.tr("Search Android settings"), kit.ui.tr("Find pages like Wi-Fi or Bluetooth by name"), "search_settings", true, null);

        c = kit.card("My shortcuts");
        kit.link(c, "star", kit.ui.tr("My shortcuts"), kit.ui.f("%d saved · web pages, numbers, places, app links", Sunflower.links(kit.activity()).length()),
                () -> kit.open(Page.SHORTCUTS));

        c = kit.card("Web search engine");
        String[] engines = new String[Search.ENGINES.length];
        for (int i = 0; i < engines.length; i++) engines[i] = Search.ENGINES[i][0];
        kit.radios(c, engines, Math.max(0, SettingsKit.indexOf(engines, kit.prefs().getString("engine", "DuckDuckGo"))), null, null, i -> kit.put("engine", engines[i]));
        kit.para(c, kit.ui.tr("Web searches open in your browser. Ganimede itself never goes online."), 14, SettingsKit.SUB);
    }

    void shortcuts() {
        LinearLayout c = kit.card();
        kit.para(c, kit.ui.tr("Your own shortcuts show up when you search. One can open a web page, call someone, write an email, show a place on the map or open any app link."), 15, SettingsKit.SUB);
        JSONArray links = Sunflower.links(kit.activity());
        c = kit.card();
        for (int i = 0; i < links.length(); i++) {
            JSONObject o = links.optJSONObject(i);
            if (o == null) continue;
            int k = i;
            TextView uri = kit.summary(o.optString("uri"));
            if (uri != null) uri.setSingleLine(true);
            LinearLayout r = kit.row(c, "star", kit.ui.accent, kit.text(o.optString("name"), 17, Colors.WHITE), uri, () -> editShortcut(k));
            r.addView(kit.text("✎", 20, SettingsKit.SUB));
        }
        if (links.length() == 0) kit.para(c, kit.ui.tr("No shortcuts yet."), 15, SettingsKit.SUB);
        c = kit.card();
        kit.row(c, "plus", kit.ui.tr("Add a shortcut"), null, () -> editShortcut(-1));
    }

    void wellbeing() {
        if (!perms.notifAllowed()) perms.notifRow(kit.card(), "Needed for the summary and the notification dots");
        LinearLayout c = kit.card("Notification summary");
        kit.toggle(c, kit.ui.tr("Notification summary"), kit.ui.tr("Non-urgent notifications arrive together, about every six hours"), "summary", false, perms.needNotif("summary", kit::render));
        kit.toggle(c, kit.ui.tr("Notification preview under favourites"), kit.ui.tr("Otherwise slide a favourite sideways to see them"), "notif_preview", false, perms.needNotif("notif_preview", null));
        String info = kit.ui.f("Calls, alarms, reminders and other urgent notifications always come through at once. %d notifications batched so far.",
                kit.prefs().getInt("summary_count", 0));
        if (kit.prefs().getBoolean("summary", false)) info += "\n" + kit.ui.f("Next summary around %s.", kit.time(nextSummary()));
        kit.para(c, info, 14, SettingsKit.SUB);
        kit.toggle(c, kit.ui.tr("Deliver conversations right away"), null, "summary_skip_chats", true, null);
        kit.toggle(c, kit.ui.tr("Also summarize silent notifications"), null, "summary_silent", false, null);
        apps(c, "bell", kit.ui.tr("Apps to summarize"), "summary_apps", true);

        c = kit.card("Break reminders");
        kit.sub(c, kit.ui.tr("Remind me to take a break after"));
        int[] mins = {10, 15, 30, 45, 60};
        kit.tiles(c, mins.length, SettingsKit.indexOf(mins, kit.prefs().getInt("breaker_min", 15)),
                new String[]{kit.ui.f("%d min", 10), kit.ui.f("%d min", 15), kit.ui.f("%d min", 30), kit.ui.f("%d min", 45), kit.ui.tr("1 hour")},
                null, i -> kit.put("breaker_min", mins[i]));
        apps(c, "pause", kit.ui.tr("Apps for break reminders"), "breaker_apps", false);
        if (Build.VERSION.SDK_INT >= 33 && !kit.prefs().getStringSet("breaker_apps", Set.of()).isEmpty() && !perms.granted(Manifest.permission.POST_NOTIFICATIONS))
            kit.perm(c, false, kit.ui.tr("Post notifications"), kit.ui.tr("Needed to show break reminders"), () -> perms.ask(Manifest.permission.POST_NOTIFICATIONS));
    }

    void privacy() {
        LinearLayout c = kit.card("Permissions");
        perms.homeRow(c, "Make Ganimede your home screen");
        perms.notifRow(c, "Dots, previews, media player and summary");
        kit.perm(c, perms.granted(Manifest.permission.READ_CALENDAR), kit.ui.tr("Calendar"), kit.ui.tr("Agenda and next event"),
                () -> perms.ask(Manifest.permission.READ_CALENDAR));
        kit.perm(c, perms.granted(Manifest.permission.READ_CONTACTS), kit.ui.tr("Contacts"), kit.ui.tr("Find contacts in search"),
                () -> perms.ask(Manifest.permission.READ_CONTACTS));
        if (Build.VERSION.SDK_INT >= 33)
            kit.perm(c, perms.granted(Manifest.permission.POST_NOTIFICATIONS), kit.ui.tr("Post notifications"), kit.ui.tr("Break reminders"),
                    () -> perms.ask(Manifest.permission.POST_NOTIFICATIONS));
        kit.perm(c, perms.lockAllowed(), kit.ui.tr("Accessibility (Quick Lock and button actions)"), kit.ui.tr("Turns the screen off with a double-tap"), perms::fixLock);

        c = kit.card("Privacy");
        kit.para(c, kit.ui.tr("Ganimede never connects to the internet: it doesn't even have permission to. No accounts, no ads, no analytics. The weather comes from Breezy Weather on your phone, and backups stay where you save them."), 15, SettingsKit.SUB);
        kit.row(c, "gear", kit.ui.tr("App info"), kit.ui.tr("Android's page for Ganimede: permissions and restricted settings"), () -> Ui.appInfo(kit.activity()));
    }

    void hidden() {
        Set<String> hidden = kit.prefs().getStringSet("hidden", Set.of());
        LinearLayout c = kit.card();
        if (hidden.isEmpty()) {
            kit.para(c, kit.ui.tr("No hidden apps. Long-press an app and choose Hide. Hidden apps still show up when you search for them."), 15, SettingsKit.SUB);
            return;
        }
        for (String key : hidden) {
            String label = Apps.labelOf(kit.activity(), key); // null once uninstalled: show its id
            LinearLayout r = kit.row(c, null, label == null ? key : label, null, null);
            r.addView(kit.pill(kit.ui.tr("Unhide"), () -> {
                new Layout(kit.activity(), kit.prefs()).toggleSet("hidden", key); // it is in the set: this takes it out
                kit.render();
            }));
        }
    }

    private void chooseCalendars() {
        if (!perms.granted(Manifest.permission.READ_CALENDAR)) { perms.ask(Manifest.permission.READ_CALENDAR); return; }
        new Thread(() -> { // the calendar provider can be slow
            Map<Long, String> cals = Agenda.calendars(kit.activity());
            kit.activity().runOnUiThread(() -> { if (!kit.activity().isDestroyed()) chooseCalendars(cals); });
        }).start();
    }

    private void chooseCalendars(Map<Long, String> cals) {
        if (cals.isEmpty()) { kit.ui.toast(kit.ui.tr("No calendars on this phone")); return; }
        List<Long> ids = new ArrayList<>(cals.keySet());
        String[] names = new String[ids.size()];
        boolean[] on = new boolean[ids.size()];
        Set<String> hidden = kit.prefs().getStringSet("cal_hidden", Set.of());
        for (int i = 0; i < names.length; i++) { names[i] = cals.get(ids.get(i)); on[i] = !hidden.contains(String.valueOf(ids.get(i))); }
        Ui.dialog(kit.activity()).setTitle(kit.ui.tr("Calendars to show"))
                .setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton(kit.ui.tr("Save"), (d, w) -> {
                    Set<String> h = new HashSet<>();
                    for (int i = 0; i < names.length; i++) if (!on[i]) h.add(String.valueOf(ids.get(i)));
                    kit.save("cal_hidden", h);
                    kit.render();
                }).setNegativeButton(kit.ui.tr("Cancel"), null).show();
    }

    /** A row that opens a multi-choice of installed apps; stores component keys, or package names when byPackage. */
    private void apps(LinearLayout card, String glyph, String label, String key, boolean byPackage) {
        Set<String> chosen = kit.prefs().getStringSet(key, Set.of());
        kit.row(card, glyph, label, chosen.isEmpty() ? kit.ui.tr("None chosen") : kit.ui.f("%d chosen", chosen.size()), () -> Apps.named(kit.activity(), l -> {
            String[] names = new String[l.size()], keys = new String[l.size()];
            boolean[] on = new boolean[l.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = l.get(i).label;
                keys[i] = byPackage ? l.get(i).pkg : l.get(i).key;
                on[i] = chosen.contains(keys[i]);
            }
            Ui.dialog(kit.activity()).setTitle(label)
                    .setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                    .setPositiveButton(kit.ui.tr("Save"), (d, w) -> {
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < names.length; i++) if (on[i]) s.add(keys[i]);
                        kit.save(key, s);
                        if (key.equals("breaker_apps") && !s.isEmpty() && Build.VERSION.SDK_INT >= 33)
                            perms.ask(Manifest.permission.POST_NOTIFICATIONS);
                        kit.render();
                    }).setNegativeButton(kit.ui.tr("Cancel"), null).show();
        }));
    }

    /** Add (index -1) or edit a custom shortcut: a name and any link Android can open. */
    private void editShortcut(int index) {
        JSONArray links = Sunflower.links(kit.activity());
        JSONObject old = index >= 0 ? links.optJSONObject(index) : null;
        LinearLayout form = new LinearLayout(kit.activity());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(kit.ui.dp(24), kit.ui.dp(8), kit.ui.dp(24), 0);
        EditText name = new EditText(kit.activity());
        name.setSingleLine(true);
        name.setHint(kit.ui.tr("Name, e.g. Home"));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText link = new EditText(kit.activity());
        link.setSingleLine(true);
        link.setHint(kit.ui.tr("Link"));
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (old != null) { name.setText(old.optString("name")); link.setText(old.optString("uri")); }
        TextView examples = new TextView(kit.activity());
        examples.setTextSize(13);
        examples.setAlpha(0.75f);
        examples.setPadding(0, kit.ui.dp(8), 0, kit.ui.dp(8));
        examples.setText(kit.ui.tr("Examples:\nhttps://example.org — a web page\ntel:+390212345678 — call a number\nmailto:me@example.org — write an email\ngeo:0,0?q=Milano — a place on the map\nor any app link, like spotify:… or whatsapp://…\nhttps://it.wikipedia.org/w/index.php?search=%s — a search shortcut: %s becomes what you type in search"));
        form.addView(name);
        form.addView(link);
        form.addView(examples);
        AlertDialog.Builder b = Ui.dialog(kit.activity())
                .setTitle(kit.ui.tr(old == null ? "Add a shortcut" : "Edit shortcut"))
                .setView(form)
                .setPositiveButton(kit.ui.tr("Save"), null)
                .setNegativeButton(kit.ui.tr("Cancel"), null);
        if (old != null) b.setNeutralButton(kit.ui.tr("Delete"), (d, w) -> {
            links.remove(index);
            kit.save("custom_shortcuts", links.toString());
            kit.render();
        });
        AlertDialog dialog = b.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim(), u = link.getText().toString().trim();
            if (u.matches("[^\\s:/]+\\.[^\\s:]+(/\\S*)?")) u = "https://" + u; // "example.org" means a web page
            if (n.isEmpty()) { name.setError(kit.ui.tr("Give it a name")); return; }
            if (u.isEmpty() || u.contains(" ") || Uri.parse(u).getScheme() == null) {
                link.setError(kit.ui.tr("That doesn't look like a link. See the examples below."));
                return;
            }
            try {
                JSONObject o = new JSONObject().put("name", n).put("uri", u);
                if (index >= 0) links.put(index, o); else links.put(o);
                kit.save("custom_shortcuts", links.toString());
            } catch (Exception e) {
                return;
            }
            dialog.dismiss();
            kit.render();
        }));
        dialog.show();
    }
}
