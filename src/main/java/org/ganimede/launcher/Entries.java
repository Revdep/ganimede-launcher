package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.IconLook;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Everything on a home screen is an entry: an app ("component", "component#user"), a pop-up folder ("f:id") or a
 * pinned shortcut / web app ("s:pkg|id|name"). Its name, icon (with the icon picked for it from an icon pack), badge,
 * tap and long-press live here once; List and Classic only lay the same piece out differently.
 */
final class Entries {
    /** Marks the exact square Android's launch and return animations target, including a whole folder tile. */
    static final Object LAUNCH_ICON = new Object();

    private final Ui ui;
    private final Apps apps;
    private final Layout layout;
    private final Launcher launcher;
    private final Notices notices;
    private final Consumer<String> showFolder, menu;
    private final List<WeakReference<FrameLayout>> notificationViews = new ArrayList<>();

    private static final class NotificationView {
        final Item app;
        final String name;
        final LinearLayout messages;
        NotificationView(Item app, String name, LinearLayout messages) {
            this.app = app;
            this.name = name;
            this.messages = messages;
        }
    }

    Entries(Ui ui, Apps apps, Layout layout, Launcher launcher, Notices notices,
            Consumer<String> showFolder, Consumer<String> menu) {
        this.ui = ui;
        this.apps = apps;
        this.layout = layout;
        this.launcher = launcher;
        this.notices = notices;
        this.showFolder = showFolder;
        this.menu = menu;
    }

    /** A pinned shortcut's name ("s:pkg|id|name"). */
    static String shortcutName(String entry) {
        String[] p = entry.substring(2).split("\\|", 3);
        return p.length > 2 ? p[2] : p[p.length - 1];
    }

    String name(String entry) {
        if (entry.startsWith("f:")) return layout.folder(entry.substring(2)).optString("name", ui.tr("Folder"));
        if (entry.startsWith("s:")) return shortcutName(entry);
        Item it = apps.find(entry);
        return it == null ? "" : apps.label(it);
    }

    boolean exists(String entry) {
        return layout.entryExists(entry, k -> apps.find(k) != null);
    }

    /** The entry's icon at this size: an app's own (or picked) icon, a folder tile, a shortcut's icon. */
    private View icon(String entry, int size, boolean drawer) {
        IconLook look = drawer ? layout.drawerIconLook() : ui.look;
        boolean folder = entry.startsWith("f:");
        if (!folder && !entry.startsWith("s:")) {
            Item it = apps.find(entry);
            return it == null ? null : ui.icon(it, size, look); // Apps.load already applied a picked icon
        }
        Drawable picked = Icons.picked(ui.a, ui.prefs, entry);
        if (picked != null) return ui.icon(picked, true, size, look);
        if (folder) return folderTile(ui.a, layout, apps::find, entry.substring(2), size, look, ui.fg, ui.accent);
        String[] p = entry.substring(2).split("\\|", 3);
        Drawable d = p.length > 1 ? apps.shortcutIcon(p[0], p[1]) : null;
        return d != null ? ui.icon(d, false, size, look) : folderTile(ui.a, layout, apps::find, null, size, look, ui.fg, ui.accent);
    }

    /** A folder's icon: a soft tile with the first four apps inside, two by two, in the look of the page it is on (or a folder mark). */
    static View folderTile(Context a, Layout layout, Function<String, Item> find, String id, int size, IconLook look, int fg, int accent) {
        LinearLayout g = new LinearLayout(a);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setGravity(Gravity.CENTER);
        int pad = size / 12, mini = (size - pad * 3) / 2;
        g.setPadding(pad, pad, pad, pad);
        g.setBackground(Icons.softTile(size));
        List<Item> inside = new ArrayList<>();
        for (String k : id == null ? List.<String>of() : layout.folderApps(id)) {
            Item it = find.apply(k);
            if (it != null) inside.add(it);
            if (inside.size() == 4) break;
        }
        if (inside.isEmpty()) g.setBackground(Icons.markedTile(id == null ? "link" : "folder", size, fg));
        for (int r = 0; r < 2 && r * 2 < inside.size(); r++) {
            LinearLayout line = new LinearLayout(a);
            for (int k = r * 2; k < Math.min(inside.size(), r * 2 + 2); k++) {
                ImageView iv = new ImageView(a);
                Item it = inside.get(k); // drawn in the look of the page the tile is on, the tile as its plate
                Icons.apply(iv, it.icon, it.fromPack, it.category, it.info.getUser(), look.inFolder(), fg, accent, mini);
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(mini, mini);
                if (k % 2 == 1) ml.setMarginStart(pad);
                line.addView(iv, ml);
            }
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            if (r == 1) ll.topMargin = pad;
            g.addView(line, ll);
        }
        return g;
    }

    /** Opens an entry: an app from its icon (so it can close back onto it), a folder's pop-up, a shortcut. */
    void open(String entry, View from) {
        if (entry.startsWith("f:")) { showFolder.accept(entry.substring(2)); return; }
        if (entry.startsWith("s:")) { launcher.openShortcut(entry, from); return; }
        Item it = apps.find(entry);
        if (it != null) launcher.launch(it, from);
    }

    /**
     * One entry as a view, the same piece in both modes: icon with its unread count, the name (beside it in List,
     * under it in Classic), the newest message under the name in List (preview); tap opens it, long-press its menu.
     */
    View view(String entry, boolean vertical, boolean big, boolean label, boolean preview) {
        return view(entry, vertical, big, label, preview, false, 0, 0, 0);
    }

    /** {@code drawer}: a cell of the app pages, with the drawer's own icon size, font, shape and name size. */
    private View view(String entry, boolean vertical, boolean big, boolean label, boolean preview, boolean drawer,
            int iconDp, float labelBase, int gapDp) { // 0 = the usual size, label size or gap
        boolean app = !entry.startsWith("f:") && !entry.startsWith("s:");
        Item it = app ? apps.find(entry) : null;
        if (app && it == null) return null;
        int size = iconDp > 0 ? ui.dp(iconDp) : vertical ? ui.dp(drawer ? layout.drawerInt("grid_icon", 48) : ui.prefs.getInt("grid_icon", 48))
                : ui.dp(big ? ui.prefs.getInt("icon_size", 54) : 44);
        View icon = icon(entry, size, drawer);
        if (icon == null) return null;
        icon.setTag(LAUNCH_ICON);
        int count = it == null ? 0 : NotifService.count(it.notifGroup);
        FrameLayout iconBox = new FrameLayout(ui.a);
        iconBox.addView(icon, new FrameLayout.LayoutParams(size, size));
        if (count > 0) iconBox.addView(badge(count), badgeParams());
        String name = name(entry);
        LinearLayout v = new LinearLayout(ui.a);
        TextView nameView = null;
        LinearLayout messages = null;
        if (label) {
            nameView = vertical ? new TextView(ui.a) : ui.text(ui.labelSp(big ? 22 : 19), big);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setText(name);
        }
        if (vertical) { // Classic: icon over its name, 14.4 / 13 sp labels and a 7dp gap
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER_HORIZONTAL);
            v.setPaddingRelative(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(6));
            v.addView(iconBox, new LinearLayout.LayoutParams(size, size));
            if (nameView != null) {
                float base = labelBase > 0 ? labelBase : layout.defaults()[0] >= 5 ? 14.4f : 13;
                nameView.setTextSize(drawer ? base * layout.drawerInt("label_scale", 100) / 100f : ui.labelSp(base));
                nameView.setTextColor(ui.fg);
                Typeface face = drawer ? Ui.font(layout.drawerString("font", "sans-serif")) : ui.face;
                nameView.setTypeface(face);
                if (ui.light()) nameView.setShadowLayer(ui.dp(3), 0, ui.dp(1), 0x99000000);
                nameView.setGravity(Gravity.CENTER);
                nameView.setPaddingRelative(0, ui.dp(gapDp > 0 ? gapDp : 7), 0, 0);
                v.addView(nameView, new LinearLayout.LayoutParams(-1, -2));
            }
        } else { // List: icon, then the name, with the newest message under it
            v.setGravity(Gravity.CENTER_VERTICAL);
            int gap = ui.dp(big ? ui.prefs.getInt("fav_gap", 8) : 7); // favourites: the spacing chosen in Settings
            v.setPaddingRelative(0, gap, 0, gap);
            v.addView(iconBox, new LinearLayout.LayoutParams(size + ui.dp(4), size + ui.dp(2)));
            if (nameView != null) {
                LinearLayout col = new LinearLayout(ui.a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPaddingRelative(ui.dp(big ? 22 : 16), 0, 0, 0);
                col.addView(nameView);
                if (preview) {
                    StatusBarNotification sbn = count > 0 ? NotifService.latest(it.notifGroup) : null;
                    if (sbn != null) col.addView(notices.bubble(sbn, it, count));
                    messages = col;
                }
                v.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        if (it != null && NotifService.connected) {
            iconBox.setTag(new NotificationView(it, name, messages));
            notificationViews.add(new WeakReference<>(iconBox));
        }
        v.setContentDescription(described(name, count));
        v.setOnClickListener(x -> open(entry, x));
        v.setOnLongClickListener(x -> { menu.accept(entry); return true; });
        return v;
    }

    /** Updates only the unread badge and newest-message preview of every live app entry. */
    void refreshNotifications() {
        if (!NotifService.connected) { notificationViews.clear(); return; }
        for (Iterator<WeakReference<FrameLayout>> it = notificationViews.iterator(); it.hasNext(); ) {
            FrameLayout iconBox = it.next().get();
            if (iconBox == null) { it.remove(); continue; }
            NotificationView row = (NotificationView) iconBox.getTag();
            int count = NotifService.count(row.app.notifGroup);
            if (iconBox.getChildCount() > 1) iconBox.removeViews(1, iconBox.getChildCount() - 1);
            if (count > 0) iconBox.addView(badge(count), badgeParams());
            ((View) iconBox.getParent()).setContentDescription(described(row.name, count));
            if (row.messages == null) continue;
            if (row.messages.getChildCount() > 1) row.messages.removeViews(1, row.messages.getChildCount() - 1);
            StatusBarNotification latest = count > 0 ? NotifService.latest(row.app.notifGroup) : null;
            if (latest != null) row.messages.addView(notices.bubble(latest, row.app, count));
        }
    }

    /** What TalkBack reads for an entry: its name, then the unread count the badge shows. */
    private String described(String name, int count) {
        return count == 0 ? name : count == 1 ? ui.f("%s, 1 notification", name) : ui.f("%s, %d notifications", name, count);
    }

    private TextView badge(int count) {
        TextView badge = new TextView(ui.a);
        badge.setText(count > 9 ? "9+" : String.valueOf(count));
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(Colors.on(ui.accent));
        badge.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setCornerRadius(ui.dp(10));
        dot.setColor(ui.accent);
        dot.setStroke(ui.dp(2), 0x66000000);
        badge.setBackground(dot);
        badge.setMinWidth(ui.dp(20));
        badge.setPaddingRelative(ui.dp(5), 0, ui.dp(5), 0);
        return badge;
    }

    /** The icon's top corner, inside its box: no cell or row around it can cut the badge. */
    private FrameLayout.LayoutParams badgeParams() {
        return new FrameLayout.LayoutParams(-2, ui.dp(20), Gravity.TOP | Gravity.END);
    }

    private boolean hideLabels() {
        return ui.prefs.getBoolean("hide_labels", false);
    }

    /** A row of the app list, a folder or the suggestions (List mode): no swipe there, so always with its preview. */
    View appRow(Item it, boolean big) {
        return view(it.key, false, big, !(big && hideLabels()), true);
    }

    /** A favourite's row: its preview only if chosen in Settings; a swipe on it shows the messages (DragDrop). */
    View favouriteRow(Item it) {
        return view(it.key, false, true, !hideLabels(), ui.prefs.getBoolean("notif_preview", false));
    }

    View folderRow(String id, boolean big) {
        return view("f:" + id, false, big, !(big && hideLabels()), true);
    }

    View shortcutRow(String entry) {
        return shortcutRow(entry, true);
    }

    View shortcutRow(String entry, boolean favourite) {
        return view(entry, false, favourite, !favourite || !hideLabels(), true);
    }

    /** A cell of the home grid, the dock or the drawer (Classic mode); {@code drawer}: on the app pages (Layout.appPage). */
    View cell(String entry, boolean label, boolean drawer) {
        return view(entry, true, false, label, true, drawer, 0, 0, 0);
    }

    /** A small cell (a folder pop-up's): an {@code iconDp} icon, a {@code labelBase} sp name (times the label-size setting), {@code gapDp} between. */
    View cell(String entry, int iconDp, float labelBase, int gapDp) {
        return view(entry, true, false, true, true, false, iconDp, labelBase, gapDp);
    }

    View cell(String entry, boolean label) {
        return cell(entry, label, false);
    }
}
