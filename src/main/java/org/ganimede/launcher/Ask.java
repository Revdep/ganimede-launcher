package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * The home screen's dialogs: a card with a title, a message, a view or a list, and text buttons, in the look of its
 * menus. Built like Android's own dialogs, shown as a Motion pop-up so it grows out of what was touched and goes
 * back onto it, whichever way it closes.
 */
final class Ask {
    private final Ui ui;
    private final LinearLayout card;
    private final TextView[] buttons = new TextView[3]; // neutral, negative, positive: Android's order
    private final Dialog dlg;
    private boolean built;

    Ask(Ui ui) {
        this.ui = ui;
        card = new LinearLayout(ui.a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, ui.dp(22), 0, ui.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5161322);
        bg.setCornerRadius(ui.dp(28));
        card.setBackground(bg);
        int w = Math.min(ui.a.getResources().getDisplayMetrics().widthPixels - ui.dp(48), ui.dp(420));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2, Gravity.CENTER);
        lp.topMargin = lp.bottomMargin = ui.dp(24);
        card.setLayoutParams(lp);
        dlg = Motion.pop(ui, card, null);
    }

    Ask setTitle(CharSequence title) {
        TextView t = ui.plain(20, true, Colors.WHITE);
        t.setText(title);
        t.setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(12));
        card.addView(t);
        return this;
    }

    Ask setMessage(CharSequence message) {
        TextView m = ui.plain(15, false, 0xDDFFFFFF);
        m.setText(message);
        m.setLineSpacing(0, 1.2f);
        m.setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(8));
        card.addView(m);
        return this;
    }

    Ask setView(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2, 1);
        lp.setMargins(ui.dp(20), 0, ui.dp(20), 0);
        card.addView(v, lp);
        return this;
    }

    Ask setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
        ListView list = list(items, android.R.layout.simple_list_item_1);
        list.setOnItemClickListener((p, v, i, id) -> { l.onClick(dlg, i); dlg.dismiss(); });
        return this;
    }

    Ask setMultiChoiceItems(CharSequence[] items, boolean[] on, DialogInterface.OnMultiChoiceClickListener l) {
        ListView list = list(items, android.R.layout.simple_list_item_multiple_choice);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        for (int i = 0; i < on.length; i++) list.setItemChecked(i, on[i]);
        list.setOnItemClickListener((p, v, i, id) -> l.onClick(dlg, i, list.isItemChecked(i)));
        return this;
    }

    /** A list that takes the room left between the title and the buttons, scrolling inside it. */
    private ListView list(CharSequence[] items, int row) {
        ListView list = new ListView(ui.a);
        list.setAdapter(new ArrayAdapter<>(ui.a, row, items));
        list.setDivider(null);
        list.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        list.setClipToPadding(false);
        card.addView(list, new LinearLayout.LayoutParams(-1, -2, 1));
        return list;
    }

    Ask setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
        return button(2, text, l, DialogInterface.BUTTON_POSITIVE);
    }

    Ask setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
        return button(1, text, l, DialogInterface.BUTTON_NEGATIVE);
    }

    Ask setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
        return button(0, text, l, DialogInterface.BUTTON_NEUTRAL);
    }

    private Ask button(int slot, CharSequence text, DialogInterface.OnClickListener l, int which) {
        TextView b = ui.plain(15, true, ui.accent);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(ui.dp(48));
        b.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        GradientDrawable press = new GradientDrawable();
        press.setColor(Colors.WHITE);
        press.setCornerRadius(ui.dp(24));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, press));
        b.setOnClickListener(v -> { if (l != null) l.onClick(dlg, which); dlg.dismiss(); });
        buttons[slot] = b;
        return this;
    }

    Dialog create() {
        if (built) return dlg;
        built = true;
        if (buttons[0] != null || buttons[1] != null || buttons[2] != null) {
            LinearLayout row = new LinearLayout(ui.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), 0);
            if (buttons[0] != null) row.addView(buttons[0]);
            row.addView(new View(ui.a), new LinearLayout.LayoutParams(0, 0, 1));
            if (buttons[1] != null) row.addView(buttons[1]);
            if (buttons[2] != null) row.addView(buttons[2]);
            card.addView(row);
        }
        return dlg;
    }

    Dialog show() {
        create().show();
        return dlg;
    }
}
