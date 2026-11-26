package org.ganimede.launcher;

import org.ganimede.launcher.core.Fit;

import android.content.SharedPreferences;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Where the parts moved in Settings (Position) sit: the same rules for the home screen and its preview. */
final class Positions {
    /** The one List-mode order used by both the home and its preview. */
    static void listBlocks(List<View[]> blocks, List<String> keys, boolean widgetBelow,
                           View[] widget, View[] clock, View[] line, View[] apps) {
        if (!widgetBelow) add(blocks, keys, widget, "list_widget_y");
        add(blocks, keys, clock, "list_clock_y");
        add(blocks, keys, line, "list_line_y");
        if (widgetBelow) add(blocks, keys, widget, "list_widget_y");
        add(blocks, keys, apps, "list_apps_y");
    }

    private static void add(List<View[]> blocks, List<String> keys, View[] views, String key) {
        blocks.add(views);
        keys.add(key);
    }

    /**
     * Moves each block by its saved offset (dp) with Fit.stack. The part moved last in
     * Settings ("last_moved") pushes its neighbours; nothing leaves [lo, hi] or covers another part. When the moved
     * part hits a limit, its offset is saved back so Settings shows where it really is.
     */
    static void fitBlocks(SharedPreferences p, List<View[]> blocks, List<String> keys, List<int[]> spans,
                          int lo, int hi, float density) {
        List<View[]> bs = new ArrayList<>();
        List<String> ks = new ArrayList<>();
        List<int[]> ss = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (spans.get(i) != null) { bs.add(blocks.get(i)); ks.add(keys.get(i)); ss.add(spans.get(i)); }
        int n = bs.size();
        int[] top = new int[n], bottom = new int[n], shift = new int[n];
        for (int i = 0; i < n; i++) {
            top[i] = ss.get(i)[0];
            bottom[i] = ss.get(i)[1];
            shift[i] = ks.get(i) == null ? 0 : Math.round(p.getInt(ks.get(i), 0) * density);
        }
        int pinned = ks.indexOf(p.getString("last_moved", ""));
        int[] fit = Fit.stack(top, bottom, shift, lo, hi, pinned);
        for (int i = 0; i < n; i++) {
            if (ks.get(i) == null) continue;
            for (View v : bs.get(i)) v.setTranslationY(fit[i]);
        }
        if (pinned >= 0 && Math.abs(fit[pinned] - shift[pinned]) > density)
            p.edit().putInt(ks.get(pinned), Math.round(fit[pinned] / density)).apply();
    }

    /** Sideways: keeps a part's drawing 4 dp inside the screen; a limited offset is saved back. */
    static void fitSide(SharedPreferences p, View[] vs, int[] span, String key, int lo, int hi, float density) {
        if (span == null) return;
        int want = Math.round(p.getInt(key, 0) * density);
        int got = Fit.span(span[0], span[1], want, lo, hi);
        for (View v : vs) v.setTranslationX(got);
        if (Math.abs(got - want) > density) p.edit().putInt(key, Math.round(got / density)).apply();
    }

    /**
     * The favourites panel always takes its full chosen height (content at its top, scrolling past it), so adding,
     * removing or rebuilding rows never moves its top edge or the clock above it. A tighter parent limit still wins.
     */
    static int fixedHeightSpec(int parentSpec, int maxHeight) {
        int mode = View.MeasureSpec.getMode(parentSpec);
        int height = mode == View.MeasureSpec.UNSPECIFIED ? maxHeight
                : Math.min(maxHeight, View.MeasureSpec.getSize(parentSpec));
        return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
    }

    private Positions() {}
}
