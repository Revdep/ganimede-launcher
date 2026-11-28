package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.IconLook;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/** Draws the compact theme, clock, font, colour, dim and icon samples used by settings. */
final class SettingsSamples {
    private final SettingsKit kit;
    private Drawable launcherIcon; // one for every tile: the render cache, keyed by the drawable, draws it once a look

    SettingsSamples(SettingsKit kit) {
        this.kit = kit;
    }

    View presetCard(Object[] pr, Runnable onTap) {
        boolean dark = "dark".equals(val(pr, "text_color"));
        int fg = dark ? Colors.INK : Colors.WHITE, acc = (Integer) val(pr, "accent");
        Typeface face = Ui.font((String) val(pr, "font"));
        boolean on = true;
        Map<String, ?> all = kit.prefs().getAll();
        for (int i = 2; i < pr.length; i += 2) on &= pr[i + 1].equals(all.get((String) pr[i]));

        LinearLayout c = new LinearLayout(kit.activity());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(kit.ui.dp(14), kit.ui.dp(12), kit.ui.dp(14), kit.ui.dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, dark
                ? new int[]{0xFFF4F1FA, blend(acc, 0xFFF4F1FA, 0.65f)}
                : new int[]{blend(acc, 0xFF15121F, 0.7f), 0xFF15121F});
        bg.setCornerRadius(kit.ui.dp(22));
        if (on) bg.setStroke(kit.ui.dp(3), dark ? Colors.INK : Colors.WHITE);
        c.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        FrameLayout clockBox = new FrameLayout(kit.activity());
        clockBox.addView(miniClock((String) val(pr, "clock_style"), face, fg, acc, 0.26f),
                new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL));
        if (on) clockBox.addView(kit.text("✓", 18, dark ? Colors.INK : Colors.WHITE),
                new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        c.addView(clockBox, new LinearLayout.LayoutParams(-1, kit.ui.dp(64)));
        TextView name = kit.text(kit.ui.tr((String) pr[0]), 16, fg);
        name.setTypeface(Typeface.create(face, Typeface.BOLD));
        c.addView(name);
        TextView desc = kit.text(kit.ui.tr((String) pr[1]), 12, Colors.alpha(fg, 0xB3));
        desc.setTypeface(face);
        desc.setMinLines(2);
        c.addView(desc);
        LinearLayout marks = new LinearLayout(kit.activity());
        marks.setGravity(Gravity.CENTER_VERTICAL);
        marks.setPadding(0, kit.ui.dp(8), 0, 0);
        marks.addView(sampleIcon(IconLook.of((String) val(pr, "icon_glyph"), (String) val(pr, "icon_container"),
                (String) val(pr, "icon_fill"), (String) val(pr, "icon_color")), 28, fg, acc));
        View dot = new View(kit.activity());
        dot.setBackground(SettingsKit.oval(acc));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(kit.ui.dp(14), kit.ui.dp(14));
        dlp.leftMargin = kit.ui.dp(10);
        marks.addView(dot, dlp);
        c.addView(marks);
        c.setContentDescription(kit.ui.tr((String) pr[0]) + ". " + kit.ui.tr((String) pr[1]));
        SettingsKit.selectable(c, on);
        c.setOnClickListener(v -> onTap.run());
        return c;
    }

    private View miniClock(String style, Typeface face, int fg, int acc, float scale) {
        if (style.equals("none")) {
            TextView t = kit.text("—", 22, fg);
            t.setGravity(Gravity.CENTER);
            return t;
        }
        ClockView clock = new ClockView(kit.activity());
        clock.look(style, Prefs.clock24(kit.activity(), kit.prefs()), fg, acc, face);
        clock.measure(View.MeasureSpec.makeMeasureSpec(kit.ui.dp(300), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return new SettingsZoom(kit.activity(), clock, scale, kit.ui.dp(300), clock.getMeasuredHeight());
    }

    View clockTile(String style, Typeface face, int fg, boolean dark) {
        FrameLayout f = new FrameLayout(kit.activity());
        f.setBackground(kit.round(dark ? 0xFFEDE9F5 : 0x33000000, 14));
        f.setPadding(kit.ui.dp(6), kit.ui.dp(4), kit.ui.dp(6), kit.ui.dp(4));
        f.addView(miniClock(style, face, fg, kit.ui.accent, 0.3f), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(kit.ui.dp(108), kit.ui.dp(80)));
        return f;
    }

    View aa(boolean dark, Typeface face) {
        TextView t = kit.text("Aa", 22, dark ? Colors.INK : Colors.WHITE);
        t.setTypeface(Typeface.create(face, Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setBackground(kit.round(dark ? 0xFFEDE9F5 : 0xFF15121F, 14));
        t.setLayoutParams(new LinearLayout.LayoutParams(kit.ui.dp(72), kit.ui.dp(48)));
        return t;
    }

    View swatch(int color) {
        View v = new View(kit.activity());
        GradientDrawable g = SettingsKit.oval(color);
        g.setStroke(kit.ui.dp(1), 0x55FFFFFF);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(kit.ui.dp(36), kit.ui.dp(36)));
        return v;
    }

    View dimTile(int dim) {
        View v = new View(kit.activity());
        v.setBackground(kit.round(Colors.veil(dim, false), 10));
        v.setLayoutParams(new LinearLayout.LayoutParams(kit.ui.dp(52), kit.ui.dp(44)));
        return v;
    }

    View picture(Drawable d) {
        ImageView v = new ImageView(kit.activity());
        v.setImageDrawable(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(kit.ui.dp(40), kit.ui.dp(40)));
        return v;
    }

    View sampleIcon(IconLook look, float sizeDp, int fg, int acc) {
        int size = kit.ui.dp(sizeDp);
        if (launcherIcon == null) launcherIcon = kit.activity().getDrawable(R.drawable.ic_launcher);
        Drawable d = launcherIcon;
        ImageView icon = new ImageView(kit.activity());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Text-coloured icons in dark text would vanish on these dark cards: show them white here.
        int sampleFg = fg == Colors.INK ? Colors.WHITE : fg;
        // Line, Bold, Sharp, Duotone and Hollow differ only on built-in glyphs: their tiles show one (the asset is always there).
        if (look.glyph().equals("original") || look.glyph().equals("dot")) Icons.apply(icon, d, false, null, null, look, sampleFg, acc, size);
        else Icons.applySample(icon, d, look, sampleFg, acc, size);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return icon;
    }

    private static Object val(Object[] preset, String key) {
        for (int i = 2; i < preset.length; i += 2) if (preset[i].equals(key)) return preset[i + 1];
        return null;
    }

    private static int blend(int a, int b, float towardB) {
        int r = (int) (((a >> 16) & 255) * (1 - towardB) + ((b >> 16) & 255) * towardB);
        int g = (int) (((a >> 8) & 255) * (1 - towardB) + ((b >> 8) & 255) * towardB);
        int bl = (int) ((a & 255) * (1 - towardB) + (b & 255) * towardB);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
