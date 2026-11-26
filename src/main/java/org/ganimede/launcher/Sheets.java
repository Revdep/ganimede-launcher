package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Bottom sheet menus: a title, then rows of glyph, label and action; a row without an action is a small section
 * heading. Every menu in Ganimede uses them, and keeps its main rows in the same order every time.
 */
final class Sheets {
    static final class Row {
        final String glyph, label;
        final Runnable action;
        Row(String glyph, String label, Runnable action) { this.glyph = glyph; this.label = label; this.action = action; }
    }

    static Row item(String glyph, String label, Runnable action) {
        return new Row(glyph, label, action);
    }

    private final Ui ui;
    /** The last menu sheet opened: a long-press menu goes away when the finger starts dragging the icon instead. */
    private Dialog last;

    Sheets(Ui ui) {
        this.ui = ui;
    }

    void dismissLast() {
        if (last != null && last.isShowing()) last.dismiss();
    }

    void show(String title, List<Row> rows) {
        Dialog[] dlg = new Dialog[1];
        LinearLayout box = new LinearLayout(ui.a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5161322);
        float r = ui.dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        View grip = new View(ui.a);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x55FFFFFF);
        g.setCornerRadius(ui.dp(3));
        grip.setBackground(g);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = ui.dp(10);
        box.addView(grip, gl);
        if (title != null) {
            TextView t = ui.plain(20, true, Colors.WHITE);
            t.setText(title);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPaddingRelative(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(10));
            box.addView(t);
        }
        for (Row row : rows) {
            if (row.action == null) { // section heading
                TextView h = ui.plain(12, true, ui.accent);
                h.setText(row.label.toUpperCase(T.locale(ui.a)));
                h.setLetterSpacing(0.1f);
                h.setPaddingRelative(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(4));
                box.addView(h);
                continue;
            }
            LinearLayout line = new LinearLayout(ui.a);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(ui.dp(52));
            line.setPaddingRelative(ui.dp(8), ui.dp(4), ui.dp(12), ui.dp(4));
            GradientDrawable press = new GradientDrawable();
            press.setColor(Colors.WHITE);
            press.setCornerRadius(ui.dp(16));
            line.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, press));
            TextView glyph = ui.plain(18, false, ui.accent);
            glyph.setGravity(Gravity.CENTER);
            if (Glyph.has(row.glyph)) { // line icons stand on their own, no circle behind them
                glyph.setBackground(new InsetDrawable(new Glyph(row.glyph, ui.accent), ui.dp(7)));
            } else {
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(Colors.alpha(ui.accent, 0x2E));
                glyph.setText(row.glyph);
                glyph.setBackground(dot);
            }
            line.addView(glyph, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
            TextView l = ui.plain(16, false, Colors.WHITE);
            l.setText(row.label);
            l.setPaddingRelative(ui.dp(16), 0, 0, 0);
            line.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
            line.setOnClickListener(v -> { dlg[0].dismiss(); row.action.run(); });
            box.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        last = dlg[0] = Motion.sheet(ui, box); // every bottom sheet can be pulled down to close
        dlg[0].show();
    }
}
