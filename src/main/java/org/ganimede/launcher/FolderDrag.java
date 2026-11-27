package org.ganimede.launcher;

import org.ganimede.launcher.core.FolderOrder;
import org.ganimede.launcher.core.Gestures;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reordering the icons of an open pop-up folder, the way the home does it (DragDrop, as Launcher3): a long-press opens
 * the icon's menu at once; held in place and released, the menu stays. Moving past the touch slop closes the menu and
 * picks the icon up: it follows the finger but stays inside the folder's grid, the others make room, and the new order
 * is saved on release. TalkBack gets "Move up" / "Move down" instead of the drag.
 */
final class FolderDrag {
    private final Ui ui;
    private final List<View> views = new ArrayList<>(); // as laid out when the pop-up opened
    private final List<String> keys = new ArrayList<>();
    private final List<View> order = new ArrayList<>();  // as shown now
    private final Consumer<List<String>> save;           // the keys in their new order
    private final Consumer<String> menu;                 // opens an entry's long-press menu
    private final Runnable closeMenu;                    // closes it again when the icon is picked up
    private float[] sx, sy;                              // top-left of each layout slot on screen
    private List<String> committed;                      // the keys as last saved
    private List<View> before;                         // the order when the drag began, to go back to on cancel
    private View touched, held; // the icon the last finger went down on; the one picked up by a long-press
    private int active; // the id of the finger that holds the icon (Android's "active pointer")
    private boolean dragging;
    private float downX, downY, baseX, baseY;

    FolderDrag(Ui ui, Consumer<String> menu, Runnable closeMenu, Consumer<List<String>> save) {
        this.ui = ui;
        this.menu = menu;
        this.closeMenu = closeMenu;
        this.save = save;
    }

    private List<String> keysInOrder() {
        List<String> out = new ArrayList<>();
        for (View v : order) out.add(keys.get(views.indexOf(v)));
        return out;
    }

    void add(View v, String key) {
        views.add(v);
        keys.add(key);
        order.add(v);
        committed = new ArrayList<>(keys);
        v.setOnLongClickListener(x -> {
            if (touched != null && x != touched) return true; // a finger that came down later elsewhere owns downX/downY
            if (ui.haptics()) x.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            menu.accept(keys.get(views.indexOf(x)));
            if (x != touched) return true; // TalkBack's long-press: no finger is down, nothing to drag
            held = x;
            x.getParent().requestDisallowInterceptTouchEvent(true); // the pop-up doesn't scroll away with the finger
            return true;
        });
        //noinspection ClickableViewAccessibility: only the drag after the long-press menu is taken; taps reach the click listener
        v.setOnTouchListener((x, e) -> touch(x, e));
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int at = order.indexOf(host);
                if (at > 0) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_earlier, ui.tr("Move up")));
                if (at < order.size() - 1) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_move_later, ui.tr("Move down")));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action != R.id.action_move_earlier && action != R.id.action_move_later)
                    return super.performAccessibilityAction(host, action, args);
                int at = order.indexOf(host), to = action == R.id.action_move_earlier ? at - 1 : at + 1;
                if (to < 0 || to >= order.size()) return true;
                measure();
                place(host, to);
                saved();
                reparent();
                // the user's own Move up/down on this node: re-parenting detached it and so cleared its focus; giving it back
                // keeps TalkBack on the icon just moved instead of jumping elsewhere
                //noinspection AccessibilityFocus: focus returns to the node the action was performed on
                host.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                host.announceForAccessibility(ui.f("Position %1$d of %2$d", to + 1, order.size()));
                return true;
            }
        });
    }

    private boolean touch(View v, MotionEvent e) {
        int a = e.getActionMasked();
        // while one icon is held, a second finger or the palm on another icon is ignored: it must not reset this drag
        if (held != null && v != held) return true;
        if (a == MotionEvent.ACTION_DOWN) {
            touched = v; dragging = false; active = e.getPointerId(0); downX = e.getRawX(); downY = e.getRawY();
            return false;
        }
        if (held == null) {
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) touched = null;
            return false;
        }
        if (a == MotionEvent.ACTION_POINTER_DOWN) return true; // a second finger on the held icon doesn't take it
        if (a == MotionEvent.ACTION_POINTER_UP) { // the holding finger lifts: the other one carries on from where it is
            int up = e.getActionIndex();
            if (e.getPointerId(up) == active) {
                int keep = up == 0 ? 1 : 0;
                downX += rawX(e, keep) - rawX(e, up); // re-based, so dx and dy (and the icon) don't jump
                downY += rawY(e, keep) - rawY(e, up);
                active = e.getPointerId(keep);
            }
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE) {
            int i = e.findPointerIndex(active);
            if (i < 0) return true;
            float dx = rawX(e, i) - downX, dy = rawY(e, i) - downY;
            if (!dragging && !Gestures.pickedUp(dx, dy, ui.slop())) return true; // held in place: the menu stays
            if (!dragging) { closeMenu.run(); lift(v); }
            follow(v, dx, dy);
            return true;
        }
        boolean wasDragging = dragging;
        held = touched = null;
        dragging = false;
        if (wasDragging) drop(v, a == MotionEvent.ACTION_UP);
        return true;
    }

    /** Pointer i's screen position (MotionEvent.getRawX(int) needs API 29): its local one plus the view's offset. */
    private static float rawX(MotionEvent e, int i) { return e.getX(i) + e.getRawX() - e.getX(); }

    private static float rawY(MotionEvent e, int i) { return e.getY(i) + e.getRawY() - e.getY(); }

    private void lift(View v) {
        dragging = true;
        before = new ArrayList<>(order);
        measure();
        baseX = v.getTranslationX(); baseY = v.getTranslationY();
        for (ViewParent p = v.getParent(); p instanceof ViewGroup && !(p.getParent() instanceof ScrollView); p = p.getParent())
            ((ViewGroup) p).setClipChildren(false); // the icon leaves its row while it moves; the card itself keeps clipping
        raise(v, ui.dp(8));
    }

    /** The finger moved (dx, dy) from where it went down: the icon goes along, kept inside the slots' bounding box. */
    private void follow(View v, float dx, float dy) {
        int home = views.indexOf(v);
        float x = clamp(sx[home] + baseX + dx, sx), y = clamp(sy[home] + baseY + dy, sy);
        v.setTranslationX(x - sx[home]);
        v.setTranslationY(y - sy[home]);
        int to = FolderOrder.nearest(x, y, sx, sy);
        if (to != order.indexOf(v)) { place(v, to); settle(v); }
    }

    private void drop(View v, boolean commit) {
        raise(v, 0);
        if (!commit) { order.clear(); order.addAll(before); settle(null); return; }
        saved();
        v.post(this::reparent); // not while v is still dispatching its own touch event
    }

    /**
     * Every icon moves into the parent slot it now shows in, gliding there from where it is drawn: shown by translation
     * alone, an icon stays a child of its old row, where a tap or long-press can't reach it and TalkBack reads it in the
     * old order. The slots themselves (sx, sy) don't move.
     */
    private void reparent() {
        int n = views.size();
        ViewGroup[] parent = new ViewGroup[n];
        int[] index = new int[n];
        ViewGroup.LayoutParams[] params = new ViewGroup.LayoutParams[n];
        float[] tx = new float[n], ty = new float[n];
        for (int i = 0; i < n; i++) {
            View slot = views.get(i), w = order.get(i);
            parent[i] = (ViewGroup) slot.getParent();
            index[i] = parent[i].indexOfChild(slot);
            params[i] = slot.getLayoutParams();
            w.animate().cancel();
            int home = views.indexOf(w);
            tx[i] = sx[home] + w.getTranslationX() - sx[i];
            ty[i] = sy[home] + w.getTranslationY() - sy[i];
        }
        List<String> shown = keysInOrder();
        for (int i = 0; i < n; i++) parent[i].removeView(views.get(i));
        for (int i = 0; i < n; i++) { // ascending, so each recorded index is valid again when its turn comes
            View w = order.get(i);
            parent[i].addView(w, index[i], params[i]);
            w.setTranslationX(tx[i]);
            w.setTranslationY(ty[i]);
            w.animate().translationX(0).translationY(0).setDuration(150);
        }
        views.clear();
        views.addAll(order);
        keys.clear();
        keys.addAll(shown);
    }

    /** The icon, and its row if it has one, drawn above their neighbours (not the card, which holds everything). */
    private static void raise(View v, float z) {
        v.setTranslationZ(z);
        ViewParent row = v.getParent();
        if (row instanceof ViewGroup && !(row.getParent() instanceof ScrollView)) ((ViewGroup) row).setTranslationZ(z);
    }

    private void saved() {
        List<String> out = keysInOrder();
        if (out.equals(committed)) return; // dropped where it was: nothing to write, nothing to refresh
        committed = out;
        save.accept(out);
    }

    private void place(View v, int to) {
        List<View> moved = FolderOrder.move(order, order.indexOf(v), to);
        order.clear();
        order.addAll(moved);
    }

    /** Every icon but `except` glides to the slot it now holds. */
    private void settle(View except) {
        for (int i = 0; i < order.size(); i++) {
            View w = order.get(i);
            int home = views.indexOf(w);
            if (w != except) w.animate().translationX(sx[i] - sx[home]).translationY(sy[i] - sy[home]).setDuration(150);
        }
    }

    private void measure() {
        sx = new float[views.size()];
        sy = new float[views.size()];
        int[] p = new int[2];
        for (int i = 0; i < sx.length; i++) {
            View w = views.get(i);
            w.getLocationOnScreen(p);
            sx[i] = p[0] - w.getTranslationX();
            sy[i] = p[1] - w.getTranslationY();
        }
    }

    private static float clamp(float v, float[] slots) {
        float min = slots[0], max = slots[0];
        for (float s : slots) { min = Math.min(min, s); max = Math.max(max, s); }
        return Math.max(min, Math.min(max, v));
    }
}
