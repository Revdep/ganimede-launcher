package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Gestures;

import android.content.ClipData;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Edit mode: long-press an icon (favourite, grid cell or dock slot) and its menu opens at once; moving the finger then
 * (past the touch slop) closes the menu and picks the icon up, to drop it on a cell, the dock, a folder or the bar
 * that appears at the top (Remove, Uninstall).
 */
final class DragDrop {
    /** What is being dragged and from where ("g:12", "d:2", "fav:3"). */
    static final class Drag {
        final String entry, from;
        Drag(String entry, String from) { this.entry = entry; this.from = from; }
    }

    private final Ui ui;
    private final Sheets sheets;
    private final Layout layout;
    private final Apps apps;
    private final Launcher launcher;
    private final Notices notices;
    private final Handler handler;
    private final Consumer<String> menu;
    private final BiConsumer<String, Drag> dropOnGrid;
    private final Runnable refresh;
    /** The bar at the top while dragging: Remove and Uninstall targets. */
    final LinearLayout bar;

    DragDrop(Ui ui, Sheets sheets, Layout layout, Apps apps, Launcher launcher, Notices notices, Handler handler,
             Consumer<String> menu, BiConsumer<String, Drag> dropOnGrid, Runnable refresh) {
        this.ui = ui;
        this.sheets = sheets;
        this.layout = layout;
        this.apps = apps;
        this.launcher = launcher;
        this.notices = notices;
        this.handler = handler;
        this.menu = menu;
        this.dropOnGrid = dropOnGrid;
        this.refresh = refresh;
        bar = buildBar();
    }

    private LinearLayout buildBar() {
        LinearLayout bar = new LinearLayout(ui.a);
        bar.setGravity(Gravity.CENTER);
        bar.setVisibility(View.GONE);
        String[][] targets = {{"remove", "close", "Remove"}, {"uninstall", "trash", "Uninstall"}};
        for (String[] t : targets) {
            TextView b = ui.plain(15, true, Colors.WHITE);
            b.setTag(t[0]);
            b.setText(ui.tr(t[2]));
            b.setGravity(Gravity.CENTER);
            Glyph gl = new Glyph(t[1], Colors.WHITE);
            gl.setBounds(0, 0, ui.dp(22), ui.dp(22));
            b.setCompoundDrawablesRelative(gl, null, null, null);
            b.setCompoundDrawablePadding(ui.dp(8));
            b.setPaddingRelative(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
            b.setOnDragListener((v, e) -> {
                if (!(e.getLocalState() instanceof Drag)) return false;
                switch (e.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED: v.setBackground(outline(true)); break;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED: v.setBackground(null); break;
                    case DragEvent.ACTION_DROP:
                        Drag d = (Drag) e.getLocalState();
                        handler.post(() -> {
                            if (d.from.startsWith("fav:")) dropFavourite(d, (String) v.getTag());
                            else dropOnGrid.accept((String) v.getTag(), d);
                        });
                        break;
                }
                return true;
            });
            bar.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC120F1C);
        bg.setCornerRadius(ui.dp(24));
        bar.setBackground(bg);
        return bar;
    }

    /** A drop target's look while dragging: a faint cell, brighter under the finger. */
    Drawable outline(boolean hot) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(ui.dp(18));
        g.setColor(hot ? Colors.alpha(ui.accent, 0x40) : 0x14FFFFFF);
        g.setStroke(Math.max(1, ui.dp(1)), hot ? ui.accent : 0x33FFFFFF);
        return new InsetDrawable(g, ui.dp(4));
    }

    private void start(View v, String entry, String from) {
        if (ui.haptics()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (bar.getChildCount() > 1) bar.getChildAt(1).setVisibility(from.startsWith("b:") ? View.GONE : View.VISIBLE);
        bar.setVisibility(View.VISIBLE); // before the drag starts, so its targets hear about it
        v.startDragAndDrop(ClipData.newPlainText("ganimede", entry), new View.DragShadowBuilder(v), new Drag(entry, from), 0);
        v.setAlpha(0.25f);
    }

    /** Long-press opens the entry's menu and arms the drag. */
    private boolean[] arm(View v, Runnable openMenu) {
        boolean[] armed = new boolean[1];
        v.setOnLongClickListener(x -> {
            if (ui.haptics()) x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            openMenu.run();
            armed[0] = true;
            x.getParent().requestDisallowInterceptTouchEvent(true); // the pages and lists don't scroll away with the finger
            return true;
        });
        return armed;
    }

    /** While a long-press menu is open and the finger still down: a move past the slop turns it into a drag. */
    private boolean afterMenu(boolean[] armed, View v, MotionEvent e, float[] down, String entry, String where) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { armed[0] = false; return false; }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { armed[0] = false; return false; }
        if (a != MotionEvent.ACTION_MOVE || !armed[0]) return false;
        if (!Gestures.pickedUp(e.getRawX() - down[0], e.getRawY() - down[1], ui.slop())) return true;
        armed[0] = false;
        sheets.dismissLast();
        start(v, entry, where);
        return true;
    }

    /** A grid cell's or dock slot's icon: long-press for its menu, then move to drag it. */
    void cell(View c, String entry, String where) {
        boolean[] armed = arm(c, () -> menu.accept(entry));
        float[] down = new float[2];
        //noinspection ClickableViewAccessibility: only the drag after the long-press menu is taken; taps reach the click listener
        c.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { down[0] = e.getRawX(); down[1] = e.getRawY(); }
            return afterMenu(armed, v, e, down, entry, where);
        });
    }

    /** Observes touches before a widget's remote child handles them: menu first, drag after movement. */
    final class BlockGesture {
        private final View view;
        private final String id;
        private final Runnable longPress;
        private float x, y;
        private boolean armed;

        BlockGesture(View view, String id, Runnable openMenu) {
            this.view = view;
            this.id = id;
            longPress = () -> {
                if (ui.haptics()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                openMenu.run();
                armed = true;
                view.getParent().requestDisallowInterceptTouchEvent(true);
            };
        }

        boolean dispatch(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                armed = false;
                x = e.getRawX(); y = e.getRawY();
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return false;
            }
            float distance = (float) Math.hypot(e.getRawX() - x, e.getRawY() - y);
            if (action == MotionEvent.ACTION_MOVE && !armed && distance > ui.slop()) handler.removeCallbacks(longPress);
            if (action == MotionEvent.ACTION_MOVE && armed && Gestures.pickedUp(e.getRawX() - x, e.getRawY() - y, ui.slop())) {
                armed = false;
                handler.removeCallbacks(longPress);
                sheets.dismissLast();
                start(view, id, "b:" + id);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(longPress);
                boolean consume = armed;
                armed = false;
                return consume;
            }
            return armed;
        }
    }

    BlockGesture block(View view, String id, Runnable openMenu) {
        view.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                v.setAlpha(1);
                bar.setVisibility(View.GONE);
            }
            return true;
        });
        return new BlockGesture(view, id, openMenu);
    }

    /** A favourite: long-press for its menu, then move to drag it up or down the list; slide it sideways for notifications. */
    void favourite(View row, String entry, int index) {
        String where = "fav:" + index;
        row.setTag(where);
        boolean[] armed = arm(row, () -> menu.accept(entry));
        // Slide a favourite sideways for its notifications (folders and shortcuts have none).
        Item app = entry.startsWith("f:") || entry.startsWith("s:") ? null : apps.find(entry);
        View.OnTouchListener swipe = app == null ? null : notices.swipeOpens(app.notifGroup);
        if (app != null) notices.accessibleOpens(row, app.notifGroup);
        float[] down = new float[2];
        //noinspection ClickableViewAccessibility: drag and swipe only (the swipe is also an accessibility action); taps reach the click listener
        row.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { down[0] = e.getRawX(); down[1] = e.getRawY(); }
            if (afterMenu(armed, v, e, down, entry, where)) return true;
            return swipe != null && swipe.onTouch(v, e);
        });
        row.setOnDragListener((v, e) -> {
            if (!(e.getLocalState() instanceof Drag) || !((Drag) e.getLocalState()).from.startsWith("fav:")) return false;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED: v.setBackground(outline(true)); break;
                case DragEvent.ACTION_DRAG_EXITED: v.setBackground(null); break;
                case DragEvent.ACTION_DROP:
                    Drag d = (Drag) e.getLocalState();
                    handler.post(() -> dropFavourite(d, where));
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackground(null);
                    v.setAlpha(1);
                    bar.setVisibility(View.GONE);
                    break;
            }
            return true;
        });
    }

    /** Favourites (List mode): dropped on another favourite it takes that place. */
    private void dropFavourite(Drag d, String target) {
        List<String> fav = layout.favourites();
        int from = Integer.parseInt(d.from.substring(4));
        if (target.equals(d.from)) return; // back on its own place: its menu already opened on the long-press
        if (from >= fav.size() || !fav.get(from).equals(d.entry)) return;
        if (target.equals("uninstall")) {
            Item it = apps.find(d.entry);
            if (it != null) launcher.uninstall(it);
            return;
        }
        fav.remove(from);
        if (target.startsWith("fav:")) fav.add(Math.min(fav.size(), Integer.parseInt(target.substring(4))), d.entry);
        layout.saveFavourites(fav);
        refresh.run();
    }
}
