package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Owns the first-run wizard's steps, navigation and app selection. */
final class SettingsWizard {
    private final SettingsKit kit;
    private final SettingsLook look;
    private final SettingsPerms perms;
    private final SettingsPages pages;

    SettingsWizard(SettingsKit kit, SettingsPerms perms, SettingsLook look, SettingsPages pages) {
        this.kit = kit;
        this.perms = perms;
        this.look = look;
        this.pages = pages;
    }

    void next(Page page) {
        if (page.step() < 0 || page == Page.W_DONE) { finish(); return; }
        kit.open(page.next());
    }

    private void finish() {
        kit.save("onboarded", true);
        // Close the wizard only once the home screen is in front, or Android may fall back to the old launcher.
        kit.home(600);
    }

    /** The top of a wizard step: progress segments, line icon, title and one short paragraph. */
    private void hero(Page page, String title, String text) {
        int i = page.step();
        LinearLayout bar = new LinearLayout(kit.activity());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(kit.ui.dp(8), kit.ui.dp(8), kit.ui.dp(8), kit.ui.dp(28));
        for (int k = 0; k < Page.STEPS.size(); k++) {
            View seg = new View(kit.activity());
            seg.setBackground(kit.round(k <= i ? kit.ui.accent : 0x33FFFFFF, 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, kit.ui.dp(5), 1);
            lp.setMarginEnd(kit.ui.dp(4));
            bar.addView(seg, lp);
        }
        TextView count = kit.text((i + 1) + "/" + Page.STEPS.size(), 13, SettingsKit.SUB);
        count.setPadding(kit.ui.dp(8), 0, 0, 0);
        bar.addView(count);
        kit.box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        boolean compact = page.preview; // the preview is above: keep the choices in view
        if (compact) bar.setPadding(kit.ui.dp(8), 0, kit.ui.dp(8), kit.ui.dp(16));
        View icon = new View(kit.activity());
        icon.setBackground(new Glyph(page.glyph, kit.ui.accent));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(kit.ui.dp(52), kit.ui.dp(52));
        il.setMargins(kit.ui.dp(6), 0, 0, kit.ui.dp(18));
        if (!compact) kit.box.addView(icon, il);
        TextView h = kit.text(title, compact ? 24 : 30, Colors.WHITE);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.setPadding(kit.ui.dp(8), 0, kit.ui.dp(8), kit.ui.dp(10));
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        kit.box.addView(h);
        TextView s = kit.text(text, 16, 0xFFCFC9DC);
        s.setLineSpacing(0, 1.25f);
        s.setPadding(kit.ui.dp(8), 0, kit.ui.dp(8), kit.ui.dp(24));
        kit.box.addView(s);
    }

    void page(Page page) {
        LinearLayout c;
        switch (page) {
            case W_LANG:
                hero(page, kit.ui.tr("Welcome to Ganimede"), kit.ui.tr("A fast, private home screen: no Google, no ads, no internet. Let's set it up in a minute. First, your language."));
                c = kit.card();
                kit.languages(c);
                pages.restoreRow(c);
                break;
            case W_HOME:
                hero(page, kit.ui.tr("Make it your home"), kit.ui.tr("One quick permission. It opens a small window right here and brings you back."));
                c = kit.card();
                perms.homeRow(c, "Ganimede opens when you press Home");
                kit.para(c, kit.ui.tr("You can skip this and allow it later in Settings."), 14, SettingsKit.SUB);
                break;
            case W_MODE:
                hero(page, kit.ui.tr("Choose a style"), kit.ui.tr("List keeps a few favourites and an A–Z list. Classic is a grid of icons with a dock and an app drawer. You can switch any time."));
                look.modeChooser(kit.card());
                break;
            case W_CUSTOM:
                hero(page, kit.ui.tr("Make it yours"), kit.ui.tr("Pick your apps, icons, font, clock and the Ganimede button now, or start with the default setup and change things later."));
                c = kit.card();
                kit.link(c, "edit", kit.ui.tr("Customize now"), kit.ui.tr("Five short steps"), () -> next(page));
                kit.row(c, "check", kit.ui.tr("Use the default setup"), kit.ui.tr("Start right away; everything is in Settings"), this::finish);
                break;
            case W_FAVS:
                hero(page, kit.ui.tr("Your favourite apps"), kit.ui.tr("Tick the apps you use most. They go on the home screen; the rest are one swipe away."));
                favouriteChoices(kit.card());
                break;
            case W_ICONS:
                hero(page, kit.ui.tr("Icons"), kit.ui.tr("How app icons look. The preview above changes as you choose."));
                c = kit.card();
                look.iconChoices(c);
                kit.sub(c, kit.ui.tr("Icon pack"));
                look.iconPacks(c);
                look.categoryIcons(c);
                break;
            case W_FONT:
                hero(page, kit.ui.tr("Font"), kit.ui.tr("Each one is shown in its own letters."));
                look.fontChoices(kit.card());
                break;
            case W_CLOCK:
                hero(page, kit.ui.tr("Clock"), kit.ui.tr("Nine styles, from a simple time to words and a flip clock."));
                look.clockChoices(kit.card());
                break;
            case W_BUTTON:
                hero(page, kit.ui.tr("The Ganimede button"), kit.ui.tr("A round button that does what you choose: one tap, a double or triple tap, or a long press can open an app or a shortcut, take a screenshot, turn on the flashlight, open the camera, switch light and dark, and more. Drag it anywhere; it snaps to the nearest edge."));
                c = kit.card();
                kit.toggle(c, kit.ui.tr("Use the Ganimede button"), null, "fab", false, kit::render);
                if (kit.prefs().getBoolean("fab", false)) look.buttonGestures(c);
                else kit.para(c, kit.ui.tr("Leave it off for now and try it later in Settings → Ganimede button."), 14, SettingsKit.SUB);
                break;
            default: // w_done
                hero(page, kit.ui.tr("All set"), kit.ui.tr("Swipe up to search apps, contacts and shortcuts. Long-press the home screen for widgets, folders and settings. Long-press an app for its options."));
                c = kit.card();
                kit.row(c, "gear", kit.ui.tr("Everything can be changed in Settings"), kit.ui.tr("Long-press the home screen → Ganimede settings"), null);
        }
    }

    /** Every app with a check box; ticked ones become favourites. A tick changes its own row and the preview, not the page. */
    private void favouriteChoices(LinearLayout c) {
        Layout layout = new Layout(kit.activity(), kit.prefs());
        List<String> fav = layout.favourites();
        Apps.named(kit.activity(), apps -> fillFavourites(c, layout, fav, apps));
    }

    /** The rows, as soon as the apps are read; their icons come after, one by one, off the main thread. */
    private void fillFavourites(LinearLayout c, Layout layout, List<String> fav, List<Apps.Item> apps) {
        List<ImageView> icons = new ArrayList<>();
        for (Apps.Item app : apps) {
            String key = app.key;
            TextView check = kit.text("", 20, kit.ui.accent);
            LinearLayout r = kit.row(c, null, app.label, null, () -> {
                if (fav.contains(key)) fav.remove(key); else fav.add(key);
                layout.saveFavourites(fav);
                // First setup only: a grid laid out by hand stays.
                if (!kit.prefs().getBoolean("onboarded", false)) kit.prefs().edit().remove("grid").apply();
                tick(check, fav.contains(key));
                kit.refresh();
            });
            ImageView icon = new ImageView(kit.activity());
            icons.add(icon);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(kit.ui.dp(36), kit.ui.dp(36));
            il.rightMargin = kit.ui.dp(16);
            r.addView(icon, 0, il);
            check.setGravity(Gravity.CENTER);
            r.addView(check, new LinearLayout.LayoutParams(kit.ui.dp(28), kit.ui.dp(28)));
            tick(check, fav.contains(key));
        }
        new Thread(() -> {
            for (int i = 0; i < icons.size(); i++) {
                Drawable d = apps.get(i).info.getBadgedIcon(0);
                ImageView v = icons.get(i);
                v.post(() -> v.setImageDrawable(d));
            }
        }).start();
    }

    /** Shows a row as ticked or not: its check mark, and its state for TalkBack (the row holds the check). */
    private void tick(TextView check, boolean on) {
        check.setText(on ? "✓" : "");
        check.setBackground(kit.round(on ? Colors.alpha(kit.ui.accent, 0x33) : 0x14FFFFFF, 8));
        SettingsKit.selectable((View) check.getParent(), on);
    }
}
