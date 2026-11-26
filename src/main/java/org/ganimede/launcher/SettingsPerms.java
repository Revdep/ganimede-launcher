package org.ganimede.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;

import java.util.Set;

/** What Ganimede needs allowed: whether it is, and the way to ask for it. */
final class SettingsPerms {
    static final int REQ_PERM = 23, REQ_HOME = 24;
    private final SettingsKit kit;

    SettingsPerms(SettingsKit kit) {
        this.kit = kit;
    }

    boolean notifAllowed() { return Ui.notifAllowed(kit.activity()); }

    boolean lockAllowed() {
        String s = Settings.Secure.getString(kit.activity().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(new ComponentName(kit.activity(), LockService.class).flattenToString());
    }

    boolean isHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = kit.activity().getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
        }
        ResolveInfo r = kit.activity().getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        return r != null && kit.activity().getPackageName().equals(r.activityInfo.packageName);
    }

    boolean granted(String perm) {
        return kit.activity().checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Important things that are switched on but not allowed. */
    int missing() {
        int n = (isHome() ? 0 : 1) + (notifAllowed() ? 0 : 1);
        if (kit.prefs().getBoolean("quick_lock", false) && !lockAllowed()) n++;
        if (kit.prefs().getBoolean("contacts_search", false) && !granted(Manifest.permission.READ_CONTACTS)) n++;
        if (Build.VERSION.SDK_INT >= 33 && !kit.prefs().getStringSet("breaker_apps", Set.of()).isEmpty()
                && !granted(Manifest.permission.POST_NOTIFICATIONS)) n++;
        return n;
    }

    /**
     * Default home app: Android 10+ shows its own small "use Ganimede as home app?" window on top of this screen
     * (RoleManager), so the setup carries on right here. Older phones, or a refused request, open the system list.
     */
    void fixHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = kit.activity().getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                    && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    kit.save("return_to_settings", true); // MainActivity reopens this screen
                    kit.activity().startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                } catch (Exception ignored) {
                    // No role window on this phone: fall back to the system list.
                }
            }
        }
        homeList();
    }

    /** The answer to fixHome's window: Ganimede is home now, or nothing changed. */
    void homeResult(int res) {
        if (isHome()) {
            kit.ui.toast(kit.ui.tr("Ganimede is now your home screen"));
            return;
        }
        kit.save("return_to_settings", null); // nothing changed: no need to come back
        if (res != Activity.RESULT_OK) homeList(); // the window was refused or blocked
    }

    private void homeList() { Ui.start(kit.activity(), new Intent(Settings.ACTION_HOME_SETTINGS)); }

    void fixNotif() { Ui.explainNotif(kit.activity(), null); }

    /**
     * Toggle callback for a feature that needs notification access: switching it on without access explains first;
     * if the person does not go on, the switch goes back off.
     */
    Runnable needNotif(String key, Runnable then) { return guard(key, this::notifAllowed, Ui::explainNotif, then); }

    /** Same for Accessibility (Quick Lock). */
    Runnable needLock(String key, Runnable then) { return guard(key, this::lockAllowed, Ui::explainLock, then); }

    private Runnable guard(String key, java.util.function.BooleanSupplier allowed, java.util.function.BiConsumer<Activity, Runnable> explain, Runnable then) {
        return () -> {
            if (kit.prefs().getBoolean(key, false) && !allowed.getAsBoolean())
                explain.accept(kit.activity(), () -> { kit.save(key, false); kit.render(); });
            if (then != null) then.run();
        };
    }

    /** The media-player switch, with the notification-access row under it while the player is on without access. */
    void mediaPlayerToggle(LinearLayout c) {
        kit.toggle(c, kit.ui.tr("Media player on the home screen"), kit.ui.tr("Needs notification access"), "media_player", true,
                needNotif("media_player", kit::render));
        if (kit.prefs().getBoolean("media_player", true) && !notifAllowed()) notifRow(c, "Needed for the media player");
    }

    /** The two rows every setup page shows; why: what it is for, on that page. */
    void homeRow(LinearLayout c, String why) { kit.perm(c, isHome(), kit.ui.tr("Default home app"), kit.ui.tr(why), this::fixHome); }
    void notifRow(LinearLayout c, String why) { kit.perm(c, notifAllowed(), kit.ui.tr("Notification access"), kit.ui.tr(why), this::fixNotif); }

    void fixLock() { Ui.explainLock(kit.activity(), null); }

    void ask(String perm) {
        if (granted(perm)) return;
        if (!kit.activity().shouldShowRequestPermissionRationale(perm) && kit.prefs().getBoolean("asked:" + perm, false)) {
            // Android stops asking after two refusals: send the person to App info instead.
            kit.ui.toast(kit.ui.tr("Allow it under Permissions in App info"));
            Ui.appInfo(kit.activity());
            return;
        }
        kit.save("asked:" + perm, true);
        kit.activity().requestPermissions(new String[]{perm}, REQ_PERM);
    }
}
