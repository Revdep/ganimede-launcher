package org.ganimede.launcher;

import org.ganimede.launcher.Apps.Item;
import org.ganimede.launcher.core.Colors;
import org.ganimede.launcher.core.Blocks;
import org.ganimede.launcher.DragDrop.Drag;
import org.ganimede.launcher.core.Grid;
import org.ganimede.launcher.core.Paging;

import android.app.Dialog;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Classic mode's home: pages of a fixed grid (swipe sideways), page dots, then the dock at the bottom. */
final class ClassicHome {
    private final Ui ui;
    private final Layout layout;
    private final Apps apps;
    private final Entries entries;
    private final Widgets widgets;
    private final DragDrop drag;
    private final Launcher launcher;
    private final Landing landing;
    private final Handler handler;
    private final Runnable refresh;
    private final Consumer<View> homeMenu;
    private final Consumer<String> blockMenu;
    final HorizontalScrollView pager;
    final LinearLayout pages, dots, dock;
    final ClockView blockClock;
    final TextView blockDate, blockWeather;
    private final LinearLayout clockContent;
    private String resizing;
    private int turnTo = -1;
    private final Runnable pageTurn = this::turnPage;
    /** The swipe's speed while a finger is on the pager, else null. */
    private final VelocityTracker[] speed = new VelocityTracker[1];

    ClassicHome(Ui ui, Layout layout, Apps apps, Entries entries, Widgets widgets, DragDrop drag, Launcher launcher,
                Landing landing, Handler handler, Runnable refresh, Consumer<View> homeMenu,
                Consumer<String> blockMenu) {
        this.ui = ui;
        this.layout = layout;
        this.apps = apps;
        this.entries = entries;
        this.widgets = widgets;
        this.drag = drag;
        this.launcher = launcher;
        this.landing = landing;
        this.handler = handler;
        this.refresh = refresh;
        this.homeMenu = homeMenu;
        this.blockMenu = blockMenu;
        pages = new LinearLayout(ui.a) {
            // Focus stays on the page shown (Paging.focusPages): a key or typed text otherwise scrolled back to page 1.
            @Override public void addFocusables(ArrayList<View> views, int direction, int mode) {
                int side = direction == View.FOCUS_LEFT ? -1 : direction == View.FOCUS_RIGHT ? 1 : 0;
                for (int pg : Paging.focusPages(currentPage(), getChildCount(), side))
                    getChildAt(pg).addFocusables(views, direction, mode);
            }
        };
        pager = new HorizontalScrollView(ui.a);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setFillViewport(true);
        // Left to right for both hands (Paging): page i at i * width, cells at their left margin, edges as on screen.
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        pager.setFocusable(false); // out of touch mode it takes focus at start: no grey box over the page (icons stay focusable)
        pager.addView(pages, new FrameLayout.LayoutParams(-2, -1));
        // A short swipe turns the page (Paging.target). The pager only sees the touch once it starts scrolling,
        // after its children had the press, so the swipe is measured from the first event it gets.
        float[] pageDown = new float[1];
        int[] pageFrom = new int[1];
        //noinspection ClickableViewAccessibility: it only watches the swipe and consumes nothing; taps reach the icons
        pager.setOnTouchListener((v, e) -> {
            if (speed[0] == null) {
                speed[0] = VelocityTracker.obtain();
                pageDown[0] = e.getRawX();
                pageFrom[0] = currentPage();
            }
            MotionEvent onScreen = MotionEvent.obtain(e);
            onScreen.setLocation(e.getRawX(), e.getRawY()); // the pager scrolls under the finger: measure on the screen
            speed[0].addMovement(onScreen);
            onScreen.recycle();
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                speed[0].computeCurrentVelocity(1000);
                int to = a == MotionEvent.ACTION_CANCEL ? pageFrom[0] : Paging.target(pageFrom[0], layout.pages(), e.getRawX() - pageDown[0],
                        speed[0].getXVelocity(), pager.getWidth(), ui.dp(400));
                speed[0].recycle();
                speed[0] = null;
                snapPage(to);
            }
            return false;
        });
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) {
                int oldWidth = or - ol;
                int page = oldWidth > 0 ? Math.round((float) pager.getScrollX() / oldWidth) : currentPage();
                v.post(() -> {
                    sizePages();
                    pager.scrollTo(Math.min(page, layout.pages() - 1) * pager.getWidth(), 0);
                });
            }
        });
        dots = new LinearLayout(ui.a);
        dots.setGravity(Gravity.CENTER);
        dots.setContentDescription(ui.tr("Arrange pages"));
        dots.setOnClickListener(v -> arrangePages());
        dock = new LinearLayout(ui.a);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPaddingRelative(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4));

        clockContent = new LinearLayout(ui.a);
        clockContent.setOrientation(LinearLayout.VERTICAL);
        blockClock = new ClockView(ui.a);
        clockContent.addView(blockClock, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout line = new LinearLayout(ui.a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        blockDate = ui.text(18, true);
        blockWeather = ui.text(18, true);
        blockWeather.setPaddingRelative(ui.dp(14), ui.dp(4), ui.dp(8), ui.dp(4));
        line.addView(blockDate);
        line.addView(blockWeather);
        clockContent.addView(line, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Each page a grid of evenly sized cells filling the space, then the dock slots. */
    void build() {
        layout.ensureFavourites();
        placeAllApps();
        pages.removeAllViews();
        List<Blocks.Item> blocks = layout.blocks();
        int cols = layout.cols(), rows = layout.rows();
        // An icon may never hide under a block (e.g. favourites placed before the clock block existed).
        Blocks.Result clear = Blocks.evict(blocks, layout.grid(), layout.pages(), cols, rows);
        if (clear.changed) layout.savePlacement(clear.blocks, clear.cells, clear.pages);
        int count = layout.pages();
        Map<Integer, String> g = layout.grid();
        boolean labels = !ui.prefs.getBoolean("hide_labels", false);
        for (int pg = 0; pg < count; pg++) {
            boolean app = layout.appPage(pg); // the app pages may look different from the first page
            FrameLayout page = new FrameLayout(ui.a) {
                @Override public boolean dispatchTouchEvent(MotionEvent e) {
                    return touchOutsideResize(e, this) || super.dispatchTouchEvent(e);
                }
            };
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = pg * rows * cols + r * cols + c;
                    View slot = slot("g:" + idx, g.getOrDefault(idx, ""), app ? layout.drawerLabels() : labels, app);
                    page.addView(slot, cellParams(c, r, 1, 1));
                }
            }
            for (Blocks.Item b : blocks) if (b.page == pg) page.addView(block(b), cellParams(b.col, b.row, b.colSpan, b.rowSpan));
            pages.addView(page, new LinearLayout.LayoutParams(Math.max(1, pager.getWidth()), -1));
        }
        dots.removeAllViews();
        dots.setVisibility(View.VISIBLE);
        for (int i = 0; i < count; i++) {
            View dot = new View(ui.a);
            dot.setBackground(dot(i == currentPage()));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(7), ui.dp(7));
            lp.setMargins(ui.dp(4), 0, ui.dp(4), 0);
            dots.addView(dot, lp);
        }
        dock.removeAllViews();
        String[] slots = layout.dockSlots();
        dock.setVisibility(slots.length == 0 ? View.GONE : View.VISIBLE);
        dock.setWeightSum(Math.max(1, slots.length));
        int dockH = ui.dp(ui.prefs.getInt("grid_icon", 48)) + ui.dp(20);
        for (int i = 0; i < slots.length; i++) dock.addView(slot("d:" + i, slots[i], false, false), new LinearLayout.LayoutParams(0, dockH, 1));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.dp(28));
        bg.setColor(Ui.dockColor(ui.prefs, !ui.light()));
        dock.setBackground(bg);
        sizePages();
    }

    private FrameLayout.LayoutParams cellParams(int col, int row, int colSpan, int rowSpan) {
        int w = Math.max(1, pager.getWidth()), h = Math.max(1, pager.getHeight());
        int left = w * col / layout.cols(), top = h * row / layout.rows();
        int right = w * (col + colSpan) / layout.cols(), bottom = h * (row + rowSpan) / layout.rows();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(right - left, bottom - top);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private View block(Blocks.Item b) {
        BlockBox box = new BlockBox();
        box.setTag(b);
        box.setLayoutDirection(Ui.handDirection(ui.prefs)); // its content follows the hand, as in the preview; its place on the page does not
        View content = b.id.equals("clock") ? clockContent : widgets.view(b.id);
        if (content == null) {
            TextView missing = ui.plain(14, false, ui.fg);
            missing.setText(ui.tr("Missing widget"));
            missing.setGravity(Gravity.CENTER);
            content = missing;
        }
        if (content.getParent() instanceof ViewGroup) ((ViewGroup) content.getParent()).removeView(content);
        box.addView(content, new FrameLayout.LayoutParams(-1, -1));
        View blockContent = content;
        if (!b.id.equals("clock")) box.post(() -> widgets.size(blockContent, box.getWidth(), box.getHeight()));
        box.gesture = drag.block(box, b.id, () -> blockMenu.accept(b.id));
        if (b.id.equals(resizing)) addHandles(box, b);
        return box;
    }

    private final class BlockBox extends FrameLayout {
        DragDrop.BlockGesture gesture;
        BlockBox() { super(ui.a); }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            return resizing == null && gesture != null && gesture.dispatch(e) || super.dispatchTouchEvent(e);
        }
    }

    /** While a block is being resized, a touch anywhere else on the page ends it. */
    boolean touchOutsideResize(MotionEvent e, View page) {
        if (resizing == null || e.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        for (int i = 0; i < ((ViewGroup) page).getChildCount(); i++) {
            View c = ((ViewGroup) page).getChildAt(i);
            if (c.getTag() instanceof Blocks.Item && ((Blocks.Item) c.getTag()).id.equals(resizing)) {
                android.graphics.Rect r = new android.graphics.Rect();
                c.getGlobalVisibleRect(r);
                if (r.contains((int) e.getRawX(), (int) e.getRawY())) return false;
            }
        }
        return stopResizing();
    }

    private GradientDrawable dot(boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(on ? ui.fg : Colors.alpha(ui.fg, 0x66));
        return d;
    }

    int currentPage() {
        int w = Math.max(1, pager.getWidth());
        return Math.round((float) pager.getScrollX() / w);
    }

    private void sizePages() {
        int w = pager.getWidth(), h = pager.getHeight();
        if (w == 0 || h == 0) return;
        for (int i = 0; i < pages.getChildCount(); i++) {
            if (!(pages.getChildAt(i) instanceof ViewGroup)) continue; // a page being swapped out by a rebuild
            ViewGroup p = (ViewGroup) pages.getChildAt(i);
            ViewGroup.LayoutParams lp = p.getLayoutParams();
            if (lp == null) continue;
            if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; p.setLayoutParams(lp); }
            for (int n = 0; n < p.getChildCount(); n++) {
                View child = p.getChildAt(n);
                int col, row, colSpan, rowSpan;
                if (child.getTag() instanceof Blocks.Item) {
                    Blocks.Item b = (Blocks.Item) child.getTag();
                    col = b.col; row = b.row; colSpan = b.colSpan; rowSpan = b.rowSpan;
                } else if (child.getTag() instanceof String && ((String) child.getTag()).startsWith("g:")) {
                    int cell = at((String) child.getTag()) % layout.per();
                    col = cell % layout.cols(); row = cell / layout.cols(); colSpan = rowSpan = 1;
                } else continue;
                child.setLayoutParams(cellParams(col, row, colSpan, rowSpan));
            }
        }
    }

    private void snapPage(int page) {
        int w = Math.max(1, pager.getWidth());
        handler.post(() -> {
            if (Anim.on()) pager.smoothScrollTo(page * w, 0); else pager.scrollTo(page * w, 0);
            for (int i = 0; i < dots.getChildCount(); i++) dots.getChildAt(i).setBackground(dot(i == page));
        });
    }

    /**
     * Home pressed on the home: page 1 at once, so the entrance that follows glides its icons in. Not while a finger
     * swipes the pages or holds an icon (Launcher3's !isHandlingTouch): that touch decides the page.
     */
    void firstPage() {
        if (speed[0] != null || drag.bar.getVisibility() == View.VISIBLE) return; // (so no edge turn is queued either)
        pager.scrollTo(0, 0);
        snapPage(0);
    }

    /** Where something new goes: the cell pressed ("g:N") while still empty, else the first free one of its page
     * (or of the page shown); -1: full. */
    int freeCell(String cell) {
        int per = layout.per(), want = cell == null ? -1 : at(cell), page = want >= 0 ? want / per : currentPage();
        return Grid.freeCell(layout.grid(), layout.blockCells(), want, page * per, (page + 1) * per);
    }

    /** The grid fills the pager from the status-bar side down to the page dots. */
    int gridBottom() {
        return pager.getBottom();
    }

    /** The icons on the page shown now (for the home's entrance). */
    List<View> visibleIcons() {
        List<View> items = new ArrayList<>();
        ViewGroup page = pages.getChildCount() > currentPage() ? (ViewGroup) pages.getChildAt(currentPage()) : null;
        for (int i = 0; page != null && i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            if (child.getTag() instanceof String && ((String) child.getTag()).startsWith("g:")
                    && child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0) items.add(child);
        }
        return items;
    }

    // ---- the grid's cells: what's on them, adding, removing

    boolean hasBlock(String id) { return Blocks.contains(layout.blocks(), id); }

    void addClock() {
        if (hasBlock("clock")) return;
        Blocks.Result r = Blocks.add("clock", layout.cols(), Math.min(2, layout.rows()), currentPage(),
                layout.pages(), layout.cols(), layout.rows(), layout.blocks(), layout.grid());
        if (r.pages > 100) { ui.toast(ui.tr("The home screen is full")); return; }
        save(r);
    }

    void addWidget(int id, int page, AppWidgetProviderInfo info) {
        int cw = Math.max(1, pager.getWidth() / layout.cols()), ch = Math.max(1, pager.getHeight() / layout.rows());
        int cols = Math.max(1, Math.min(layout.cols(), (Math.max(1, info.minWidth) + cw - 1) / cw));
        int rows = Math.max(1, Math.min(layout.rows(), (Math.max(1, info.minHeight) + ch - 1) / ch));
        Blocks.Result r = layout.placeBlock(String.valueOf(id), cols, rows, page);
        if (r.pages > 100) { widgets.delete(String.valueOf(id)); ui.toast(ui.tr("The home screen is full")); return; }
        save(r);
    }

    void resize(String id) {
        resizing = id;
        refresh.run();
    }

    /** Leaves resizing (Back, or a touch outside the block); false when nothing was being resized. */
    boolean stopResizing() {
        if (resizing == null) return false;
        resizing = null;
        pager.post(refresh);
        return true;
    }

    void removeBlock(String id) {
        List<Blocks.Item> blocks = layout.blocks();
        blocks.removeIf(b -> b.id.equals(id));
        layout.saveBlocks(blocks);
        if (!id.equals("clock")) widgets.delete(id);
        resizing = null;
        refresh.run();
    }

    void clockClicks(Outlook outlook) {
        blockClock.setClockTapListener(ui, outlook);
        blockDate.setOnClickListener(v -> outlook.onDateTap());
        blockWeather.setOnClickListener(v -> outlook.onWeatherTap());
    }

    private void save(Blocks.Result r) {
        if (!r.changed) return;
        layout.savePlacement(r.blocks, r.cells, r.pages);
        refresh.run();
        Blocks.Item added = r.blocks.get(r.blocks.size() - 1);
        pager.post(() -> snapPage(added.page));
    }

    /** One cell on one edge for a screen reader, by the same rules as the drag; resizing stays on so it can repeat. */
    private void stepEdge(String id, String edge, int cells) {
        for (Blocks.Item b : layout.blocks()) {
            if (!b.id.equals(id)) continue;
            Blocks.Result r = Blocks.put(Blocks.edgeStep(b, edge, cells), layout.pages(), layout.cols(), layout.rows(), layout.blocks(), layout.grid());
            if (!r.changed) {
                pager.announceForAccessibility(ui.tr("Limit reached"));
                return;
            }
            save(r);
            Blocks.Item saved = r.blocks.stream().filter(x -> x.id.equals(id)).findFirst().orElse(b);
            // the refresh built the handles again and TalkBack lost its focus: it goes back to the same edge
            pager.post(() -> {
                ArrayList<View> found = new ArrayList<>();
                pager.findViewsWithText(found, ui.tr("Resize " + edge + " edge"), View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                //noinspection AccessibilityFocus: the focus the user had before the handles were rebuilt, on the same edge
                for (View h : found) h.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                pager.announceForAccessibility(ui.f("Width %d, height %d", saved.colSpan, saved.rowSpan));
            });
            return;
        }
    }

    private void addHandles(FrameLayout box, Blocks.Item original) {
        String[] edges = {"left", "right", "top", "bottom"};
        //noinspection RtlHardcoded: the edges Blocks.edgeStep moves are the screen's, for both hands (the box follows the hand)
        int[] gravity = {Gravity.LEFT, Gravity.RIGHT, Gravity.TOP, Gravity.BOTTOM};
        List<Blocks.Item> blocks = layout.blocks();
        Map<Integer, String> cells = layout.grid();
        int pageCount = layout.pages(), cols = layout.cols(), rows = layout.rows();
        for (int i = 0; i < edges.length; i++) {
            String edge = edges[i];
            View handle = new View(ui.a);
            handle.setBackground(drag.outline(true));
            handle.setContentDescription(ui.tr("Resize " + edge + " edge"));
            FrameLayout.LayoutParams lp = edge.equals("left") || edge.equals("right")
                    ? new FrameLayout.LayoutParams(ui.dp(18), -1, gravity[i])
                    : new FrameLayout.LayoutParams(-1, ui.dp(18), gravity[i]);
            Blocks.Item[] latest = {original};
            float[] down = new float[2];
            //noinspection ClickableViewAccessibility: a drag-only handle, there is no tap to report
            handle.setOnTouchListener((v, e) -> {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    down[0] = e.getRawX(); down[1] = e.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    int dc = Math.round((e.getRawX() - down[0]) * layout.cols() / Math.max(1f, pager.getWidth()));
                    int dr = Math.round((e.getRawY() - down[1]) * layout.rows() / Math.max(1f, pager.getHeight()));
                    boolean start = edge.equals("left") || edge.equals("top");
                    int d = edge.equals("left") || edge.equals("right") ? dc : dr;
                    Blocks.Item b = Blocks.edgeStep(original, edge, start ? -d : d);
                    Blocks.Result test = Blocks.put(b, pageCount, cols, rows, blocks, cells);
                    if (b.equals(original) || test.changed) {
                        latest[0] = b;
                        box.setTag(b);
                        box.setLayoutParams(cellParams(b.col, b.row, b.colSpan, b.rowSpan));
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    Blocks.Result result = Blocks.put(latest[0], pageCount, cols, rows, blocks, cells);
                    resizing = null;
                    if (result.changed) layout.savePlacement(result.blocks, result.cells, result.pages);
                    pager.post(refresh); // after this touch: rebuilding now would remove the handle mid-event
                    return true;
                }
                return true;
            });
            handle.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_grow, ui.tr("Grow")));
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_shrink, ui.tr("Shrink")));
                }

                @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                    if (action != R.id.action_grow && action != R.id.action_shrink)
                        return super.performAccessibilityAction(host, action, args);
                    stepEdge(original.id, edge, action == R.id.action_grow ? 1 : -1);
                    return true;
                }
            });
            box.addView(handle, lp);
        }
    }

    /**
     * Drawer off: every app lives on the home pages. Apps not on a page, in the dock or in a folder there (new installs
     * too) go in alphabetical order into the free cells from page two on, adding pages as needed (Grid.placeAll).
     */
    private void placeAllApps() {
        if (layout.drawerOn() || apps.isEmpty()) return;
        List<Item> sorted = apps.visible();
        Map<Item, String> names = new HashMap<>();
        for (Item it : sorted) names.put(it, apps.label(it));
        sorted.sort((a, b) -> names.get(a).compareToIgnoreCase(names.get(b)));
        List<String> keys = new ArrayList<>();
        for (Item it : sorted) keys.add(it.key);
        Map<Integer, String> g = layout.grid();
        List<Integer> reserved = new ArrayList<>();
        for (int i : layout.blockCells()) if (!g.containsKey(i)) { g.put(i, "\u0000"); reserved.add(i); }
        int pages = Grid.placeAll(g, layout.per(), layout.pages(), layout.dock(), keys, e -> layout.folderApps(e.substring(2)));
        for (int i : reserved) if ("\u0000".equals(g.get(i))) g.remove(i);
        if (pages == 0) return;
        layout.saveGrid(g);
        layout.setPages(pages);
    }

    // ---- pages

    void createPage() {
        layout.setPages(Math.min(100, layout.pages() + 1)); // the backup keeps up to 100
        refresh.run();
        pager.post(() -> snapPage(layout.pages() - 1));
    }

    private void movePage(int from, int to) {
        if (from == 0 || to == 0) return; // page one is the favourites page
        layout.savePlacement(Blocks.movePage(layout.blocks(), layout.pages(), from, to),
                Grid.movePage(layout.grid(), layout.per(), layout.pages(), from, to), layout.pages());
    }

    /** Deletes a page (after asking when it holds anything): the later pages move back one. */
    void deletePage(int page, Runnable after) {
        if (page == 0) return; // page one is the favourites page
        int per = layout.per();
        Map<Integer, String> g = layout.grid();
        boolean empty = true;
        for (int c = 0; c < per; c++) if (!g.getOrDefault(page * per + c, "").isEmpty()) empty = false;
        List<Blocks.Item> blocks = layout.blocks();
        for (Blocks.Item b : blocks) if (b.page == page) empty = false;
        Runnable go = () -> {
            int count = layout.pages();
            if (count <= 1) return;
            List<Blocks.Item> oldBlocks = layout.blocks();
            List<String> removed = new ArrayList<>();
            for (Blocks.Item b : oldBlocks) if (b.page == page && !b.id.equals("clock")) removed.add(b.id);
            layout.savePlacement(Blocks.deletePage(oldBlocks, page),
                    Grid.deletePage(layout.grid(), per, count, page), count - 1);
            widgets.delete(removed);
            refresh.run();
            if (after != null) after.run();
        };
        if (layout.pages() <= 1) return;
        if (empty) { go.run(); return; }
        ui.ask()
                .setTitle(ui.tr("Delete this page?"))
                .setMessage(layout.drawerOn() ? ui.tr("Its icons and blocks leave the home screen (the apps stay in the drawer).") : ui.tr("Its apps move to free spots on the other pages; its blocks are removed."))
                .setPositiveButton(ui.tr("Delete"), (d, w) -> go.run())
                .setNegativeButton(ui.tr("Cancel"), null)
                .show();
    }

    /**
     * "Arrange pages": the home pages side by side, small. Drag one onto another place to move it, tap one to go
     * there, the cross deletes it, + adds one.
     */
    void arrangePages() {
        Dialog[] dlg = new Dialog[1];
        LinearLayout box = new LinearLayout(ui.a);
        box.setLayoutParams(new FrameLayout.LayoutParams(-1, -1)); // the whole screen, between the bars
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xE60E0B18);
        TextView hint = ui.plain(15, false, 0xCCFFFFFF);
        hint.setText(ui.tr("Drag a page to move it, tap it to go there."));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(20));
        box.addView(hint);
        HorizontalScrollView hs = new HorizontalScrollView(ui.a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ui.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        hs.addView(row);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(hs, hl);
        TextView done = ui.plain(16, true, ui.accent);
        done.setText(ui.tr("Done"));
        done.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        done.setOnClickListener(v -> dlg[0].dismiss());
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(done, dl);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            row.removeAllViews();
            int w = Math.max(1, pager.getWidth()), h = Math.max(1, pager.getHeight()), count = layout.pages();
            float k = 0.24f; // small enough for three pages and the + at once
            for (int i = 0; i < count && i < pages.getChildCount(); i++) {
                int pg = i;
                View page = pages.getChildAt(i);
                // A picture of the page as it is on the home screen, drawn small.
                Bitmap bm = Bitmap.createBitmap(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bm);
                cv.scale(k, k);
                page.draw(cv);
                FrameLayout card = new FrameLayout(ui.a);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(ui.dp(18));
                bg.setColor(0x22FFFFFF);
                bg.setStroke(ui.dp(2), pg == currentPage() ? ui.accent : 0x44FFFFFF);
                card.setBackground(bg);
                card.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(28));
                ImageView shot = new ImageView(ui.a);
                shot.setImageBitmap(bm);
                card.addView(shot, new FrameLayout.LayoutParams(bm.getWidth(), bm.getHeight()));
                TextView num = ui.plain(13, false, 0xCCFFFFFF);
                num.setText(ui.f("Page %d", pg + 1));
                card.addView(num, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                if (count > 1 && pg > 0) {
                    ImageView x = new ImageView(ui.a);
                    x.setImageDrawable(new Glyph("close", Colors.WHITE));
                    x.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
                    x.setContentDescription(ui.tr("Delete page"));
                    x.setOnClickListener(v -> deletePage(pg, () -> card.post(fill[0])));
                    card.addView(x, new FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.TOP | Gravity.END));
                }
                card.setOnClickListener(v -> {
                    dlg[0].dismiss();
                    snapPage(pg);
                });
                if (pg > 0) card.setOnLongClickListener(v -> {
                    v.startDragAndDrop(null, new View.DragShadowBuilder(v), pg, 0);
                    v.setAlpha(0.3f);
                    return true;
                });
                if (pg > 0) card.setOnDragListener((v, ev) -> {
                    if (ev.getAction() == DragEvent.ACTION_DROP && ev.getLocalState() instanceof Integer) {
                        int from = (Integer) ev.getLocalState();
                        if (from > 0 && from != pg) { movePage(from, pg); refresh.run(); card.post(fill[0]); }
                    } else if (ev.getAction() == DragEvent.ACTION_DRAG_ENDED) v.setAlpha(1);
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMargins(ui.dp(8), 0, ui.dp(8), 0);
                row.addView(card, lp);
            }
            ImageView add = new ImageView(ui.a);
            add.setImageDrawable(new Glyph("plus", Colors.WHITE));
            add.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            add.setContentDescription(ui.tr("New page"));
            add.setOnClickListener(v -> { layout.setPages(Math.min(100, layout.pages() + 1)); refresh.run(); add.post(fill[0]); });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52));
            alp.setMargins(ui.dp(8), 0, ui.dp(8), 0);
            row.addView(add, alp);
        };
        fill[0].run();
        dlg[0] = Motion.pop(ui, box, null);
        dlg[0].show();
    }

    // ---- cells and drops

    /** A grid cell or dock slot: holds an icon or nothing, and accepts drops. */
    private View slot(String where, String entry, boolean label, boolean app) {
        FrameLayout s = new FrameLayout(ui.a);
        s.setTag(where);
        boolean gone = !entry.isEmpty() && !entries.exists(entry);
        View c = entry.isEmpty() || gone ? null : entries.cell(entry, label, app);
        if (c != null) {
            s.addView(c, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            landing.track(entry, s);
            drag.cell(c, entry, where);
        } else {
            // An empty spot: long-press opens the home menu, as on any empty part of the home screen.
            s.setOnLongClickListener(v -> { homeMenu.accept(v); return true; });
        }
        s.setOnDragListener(this::onSlotDrag);
        return s;
    }

    private boolean onSlotDrag(View s, DragEvent e) {
        if (!(e.getLocalState() instanceof Drag)) return false;
        Drag d = (Drag) e.getLocalState();
        if (d.from.startsWith("b:") && !((String) s.getTag()).startsWith("g:")) return false;
        ViewGroup box = (ViewGroup) s;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // Edit mode: every empty spot shows as a faint cell so it's clear where things can go.
                if (box.getChildCount() == 0) s.setBackground(drag.outline(false));
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                s.setBackground(drag.outline(true));
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                edgeTurn(s, e.getX());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                handler.removeCallbacks(pageTurn);
                turnTo = -1; // the next edge cell starts the wait again
                s.setBackground(box.getChildCount() == 0 ? drag.outline(false) : null);
                return true;
            case DragEvent.ACTION_DROP:
                handler.post(() -> dropOn((String) s.getTag(), d));
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                handler.removeCallbacks(pageTurn);
                turnTo = -1; // the next drag starts afresh
                s.setBackground(null);
                for (int i = 0; i < box.getChildCount(); i++) box.getChildAt(i).setAlpha(1);
                drag.bar.setVisibility(View.GONE);
                return true;
        }
        return true;
    }

    /** Held near a side edge of the grid while dragging: after half a second the next page slides in. */
    private void edgeTurn(View s, float x) {
        if (!((String) s.getTag()).startsWith("g:")) return;
        int[] at = new int[2], pg = new int[2];
        s.getLocationOnScreen(at);
        pager.getLocationOnScreen(pg);
        float sx = at[0] + x - pg[0];
        int dir = sx < ui.dp(28) ? -1 : sx > pager.getWidth() - ui.dp(28) ? 1 : 0;
        int target = dir == 0 ? -1 : currentPage() + dir;
        if (target == turnTo && dir != 0) return;
        handler.removeCallbacks(pageTurn);
        turnTo = target;
        if (dir != 0) handler.postDelayed(pageTurn, 500);
    }

    private void turnPage() {
        if (turnTo < 0 || turnTo >= layout.pages()) return;
        snapPage(turnTo);
    }

    private static int at(String where) {
        return Integer.parseInt(where.substring(2));
    }

    /**
     * A drop: back on its own spot opens its menu; on an empty cell or dock slot it moves there; onto another app it
     * makes a folder of the two (onto a folder, it joins it); a folder dropped on an icon swaps places; the bar at the
     * top removes it from the home screen or uninstalls the app.
     */
    void dropOn(String target, Drag d) {
        if (d.from.startsWith("b:")) {
            String id = d.from.substring(2);
            if (target.equals("remove") || target.equals("uninstall")) { removeBlock(id); return; }
            if (!target.startsWith("g:")) return;
            List<Blocks.Item> blocks = layout.blocks();
            Blocks.Item old = null;
            for (Blocks.Item b : blocks) if (b.id.equals(id)) old = b;
            if (old == null) return;
            int cell = at(target), per = layout.per(), onPage = cell % per;
            Blocks.Item moved = new Blocks.Item(id, cell / per, onPage % layout.cols(), onPage / layout.cols(),
                    old.colSpan, old.rowSpan);
            Blocks.Result result = Blocks.put(moved, layout.pages(), layout.cols(), layout.rows(),
                    blocks, layout.grid());
            if (result.changed) layout.savePlacement(result.blocks, result.cells, result.pages);
            refresh.run();
            return;
        }
        if (target.equals(d.from)) return; // back on its own place: its menu already opened on the long-press
        Map<Integer, String> g = layout.grid();
        String[] slots = layout.dockSlots();
        String savedOccupant = target.startsWith("g:") ? g.getOrDefault(at(target), "") : target.startsWith("d:") ? slots[at(target)] : "";
        final String occupant = !savedOccupant.isEmpty() && entries.exists(savedOccupant) ? savedOccupant : "";
        // take it out of where it came from
        if (d.from.startsWith("g:")) g.remove(at(d.from));
        if (d.from.startsWith("d:")) slots[at(d.from)] = "";
        if (target.equals("uninstall")) {
            Item it = apps.find(d.entry);
            if (it != null) launcher.uninstall(it);
            build();
            return;
        }
        if (!target.equals("remove")) { // "remove": nothing else, it's off the home screen
            String put = d.entry, back = "";
            if (!occupant.isEmpty()) {
                if (occupant.startsWith("f:") && !d.entry.startsWith("f:")) { // into an existing folder
                    layout.editFolderApps(occupant.substring(2), a -> { if (!a.contains(d.entry)) a.add(d.entry); });
                    put = occupant;
                } else if (!d.entry.startsWith("f:") && target.startsWith("g:")) { // two apps make a folder
                    String id = layout.newFolder(ui.tr("Folder"), List.of(occupant, d.entry));
                    if (id == null) return;
                    put = "f:" + id;
                    ui.toast(ui.tr("Folder created: tap it to open, long-press to rename"));
                } else {
                    back = occupant; // swap places
                }
            }
            if (target.startsWith("g:")) g.put(at(target), put);
            else slots[at(target)] = put;
            if (!back.isEmpty()) {
                if (d.from.startsWith("g:")) g.put(at(d.from), back);
                else if (d.from.startsWith("d:")) slots[at(d.from)] = back;
            }
        }
        layout.saveGrid(g);
        layout.saveDock(slots);
        if (target.equals("remove")) {
            List<String> fav = layout.favourites();
            if (fav.remove(d.entry)) layout.saveFavourites(fav);
        }
        refresh.run();
    }
}
