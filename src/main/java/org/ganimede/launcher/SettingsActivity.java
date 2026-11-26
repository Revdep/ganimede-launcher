package org.ganimede.launcher;

import org.ganimede.launcher.core.Colors;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * Settings: a home page with a live preview of the home screen and one card per category, each opening its own
 * page in this same activity (Back goes up one level). Built in code; every text goes through T (Italian in ItSettings).
 */
public class SettingsActivity extends Activity {
    private static final int SCRIM = 0x99000000;
    private static final int REQ_BUTTON_ICON = 25;

    private SettingsKit kit;
    private SettingsPerms perms;
    private SettingsLook look;
    private final ArrayDeque<Page> stack = new ArrayDeque<>();
    private final Bundle scrolls = new Bundle(); // page → scroll y, saved as is when Android rebuilds the screen
    private String shown = "";
    private FrameLayout slot;
    private ScrollView sv;
    private LinearLayout wizBar, topBar;
    private TextView title, back, wizPrev, wizNext;
    private int bottomInset;
    private SettingsPreview preview;
    private SettingsWizard wizard;
    private SettingsBricks bricks;
    private SettingsPages pages;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = Prefs.of(this);
        kit = new SettingsKit(this, p, this::render, this::open, () -> preview.refresh());
        perms = new SettingsPerms(kit);
        SettingsSamples samples = new SettingsSamples(kit);
        look = new SettingsLook(kit, perms, samples, this::pickButtonIcon);
        pages = new SettingsPages(kit, perms, () -> { start(Page.W_LANG); render(); },
                this::restored);
        wizard = new SettingsWizard(kit, perms, look, pages);
        pages.adopt(getLastNonConfigurationInstance()); // a backup or restore the screen before this one started
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = topBar = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(SCRIM);
        back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(Colors.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, SettingsKit.oval(Colors.WHITE)));
        back.setOnClickListener(v -> onBackPressed());
        top.addView(back, new LinearLayout.LayoutParams(kit.ui.dp(48), kit.ui.dp(48)));
        title = kit.text("", 28, Colors.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(kit.ui.dp(8), 0, 0, 0);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView undo = kit.pill("", () -> bricks.undo());
        top.addView(undo);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        slot = new FrameLayout(this); // the preview sits here, over the bare wallpaper
        root.addView(slot, new LinearLayout.LayoutParams(-1, -2));
        kit.pinned.setBackgroundColor(SCRIM); // stays in view under the preview while the page scrolls
        root.addView(kit.pinned, new LinearLayout.LayoutParams(-1, -2));

        sv = new ScrollView(this);
        sv.setBackgroundColor(SCRIM);
        sv.setClipToPadding(false);
        kit.box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(kit.box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // Setup wizard: a fixed bar with Back and a big Next.
        wizBar = new LinearLayout(this);
        wizBar.setGravity(Gravity.CENTER_VERTICAL);
        wizBar.setBackgroundColor(0xFA0E0B18);
        wizPrev = kit.text("", 16, SettingsKit.SUB);
        wizPrev.setGravity(Gravity.CENTER);
        wizPrev.setMinWidth(kit.ui.dp(88));
        wizPrev.setMinHeight(kit.ui.dp(48));
        wizPrev.setOnClickListener(v -> onBackPressed());
        wizBar.addView(wizPrev);
        wizBar.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1)); // pushes Next to the right
        wizNext = kit.text("", 16, Colors.on(kit.ui.accent));
        wizNext.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wizNext.setGravity(Gravity.CENTER);
        wizNext.setPadding(kit.ui.dp(28), 0, kit.ui.dp(28), 0);
        wizNext.setMinHeight(kit.ui.dp(52));
        wizNext.setOnClickListener(v -> wizard.next(stack.size() == 1 && stack.peek() == Page.W_HOME ? Page.W_DONE : stack.peek()));
        wizBar.addView(wizNext);
        wizBar.setVisibility(View.GONE);
        root.addView(wizBar, new LinearLayout.LayoutParams(-1, -2));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            // Everything starts below the status bar: nothing of Ganimede is ever drawn under it.
            root.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), 0);
            top.setPadding(kit.ui.dp(8), kit.ui.dp(8), kit.ui.dp(16), kit.ui.dp(8));
            bottomInset = in.getSystemWindowInsetBottom();
            padBottom();
            wizBar.setPadding(kit.ui.dp(12), kit.ui.dp(10), kit.ui.dp(16), kit.ui.dp(10) + bottomInset);
            return in;
        });
        setContentView(root);

        preview = new SettingsPreview(this);
        preview.onIcons(this::render);
        preview.edit.onPicked = this::pick;
        bricks = new SettingsBricks(kit, perms, look, samples, preview, undo);
        if (b == null) start(getIntent().getBooleanExtra("wizard", false) ? Page.W_LANG : Page.HOME);
        else { // rebuilt by Android (rotation): back where it was
            for (String page : b.getStringArray("stack")) stack.addLast(Page.valueOf(page));
            scrolls.putAll(b.getBundle("scrolls"));
            bricks.pick(b.getString("brick", Brick.SCREEN));
            if (b.getString("restore") != null) pages.confirmRestore(Uri.parse(b.getString("restore"))); // the dialog turned with the screen
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArray("stack", stack.stream().map(Page::name).toArray(String[]::new));
        scrolls.putInt(shown, sv.getScrollY());
        out.putBundle("scrolls", scrolls);
        out.putString("brick", bricks.picked());
        if (pages.confirming() != null) out.putString("restore", pages.confirming().toString());
    }

    /** Android rebuilds this screen (rotation): a running backup or restore goes on to the new one, as a ViewModel would. */
    @Override
    public Object onRetainNonConfigurationInstance() { return pages.running(); }

    @Override
    protected void onDestroy() {
        bricks.stop();
        pages.stop();
        super.onDestroy();
    }

    /** A brick was touched in the preview: show its controls (opening the home-screen page from anywhere else). */
    private void pick(String brick) {
        bricks.pick(brick);
        if (stack.peek() == Page.HOMESCREEN) render(); else open(Page.HOMESCREEN);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        // Already in the wizard (e.g. Android recreated the home screen after "set as default"): carry on, don't restart.
        if (!stack.isEmpty() && stack.peek().step() >= 0) return;
        if (i.getBooleanExtra("wizard", false)) start(Page.W_LANG); // onResume, next, shows it
    }

    /**
     * A restore ended on the page showing now. On a wizard step it is the whole setup: ask only for the home app if
     * Ganimede is not it yet (a wizard that starts at W_HOME ends after that step), else leave for the home screen.
     */
    private void restored() {
        if (stack.peek().step() < 0) render();
        else if (perms.isHome()) wizard.next(Page.W_DONE);
        else { start(Page.W_HOME); render(); }
    }

    /** First run (or "Run setup again") opens the wizard; otherwise the settings home. */
    private void start(Page first) {
        stack.clear();
        stack.push(first);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // permissions may have changed while away
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            scrolls.remove(stack.pop().name());
            render();
            Anim.enter(kit.box, -kit.ui.dp(24), 0, 0); // back from the side it was left on
        } else {
            super.onBackPressed();
        }
    }

    private void open(Page page) {
        scrolls.putInt(shown, sv.getScrollY());
        stack.push(page);
        render();
        Anim.enter(kit.box, kit.ui.dp(24), 0, 0);
    }

    /** Rebuilds the current page (and the preview) in the current language, keeping the scroll position. */
    private void render() {
        Page page = stack.peek();
        String view = page == Page.HOMESCREEN ? page + bricks.picked() : page.name(); // another brick's controls start at their top
        int y = view.equals(shown) ? sv.getScrollY() : scrolls.getInt(view, 0);
        shown = view;
        kit.ui.read();
        kit.box.removeAllViews();
        int step = page.step();
        boolean wiz = step >= 0;
        back.setVisibility(stack.size() > 1 && !wiz ? View.VISIBLE : View.GONE);
        wizBar.setVisibility(wiz ? View.VISIBLE : View.GONE);
        boolean canBack = step > 0 && stack.size() > 1; // a wizard started at W_HOME by a restore has no step behind it
        wizPrev.setText(canBack ? kit.ui.tr("Back") : "");
        wizPrev.setEnabled(canBack);
        wizPrev.setVisibility(canBack ? View.VISIBLE : View.INVISIBLE); // keeps its place, not reachable by focus
        wizNext.setText(kit.ui.tr(page == Page.W_DONE ? "Start" : "Next"));
        wizNext.setTextColor(Colors.on(kit.ui.accent));
        wizNext.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), kit.round(kit.ui.accent, 26), null));
        // The wizard is a full-screen flow of its own: no settings header, a deep scrim so text reads on any wallpaper.
        topBar.setVisibility(wiz ? View.GONE : View.VISIBLE);
        sv.setBackground(wiz ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF00E0B18, 0xFA0E0B18}) : new ColorDrawable(SCRIM));
        int side = kit.ui.dp(wiz ? 20 : 16), top = kit.ui.dp(!wiz ? 12 : page.preview ? 16 : 20);
        kit.box.setPadding(side, top, side, kit.ui.dp(24));
        padBottom();
        back.setContentDescription(kit.ui.tr("Back"));
        bricks.track(page == Page.HOMESCREEN);
        title.setText(kit.ui.tr(page.title));
        slot.removeAllViews();
        kit.pinned.removeAllViews();
        if (page.preview) {
            if (page != Page.HOMESCREEN) preview.showPage(0);
            int screen = getResources().getDisplayMetrics().heightPixels;
            // At most half the screen, less where the controls need the room: the settings get the rest.
            preview.setHeight(wiz || page == Page.HOMESCREEN ? screen * 2 / 5 : screen / 2 - kit.ui.dp(16));
            // The preview is the way in: touch a brick on the settings home to open its controls, move it on its page.
            preview.edit.mode = page == Page.HOMESCREEN ? BrickOverlay.EDIT : page == Page.HOME ? BrickOverlay.PICK : BrickOverlay.OFF;
            preview.refresh();
            if (preview.getParent() != null) ((android.view.ViewGroup) preview.getParent()).removeView(preview);
            if (page == Page.HOME) kit.box.addView(preview, new LinearLayout.LayoutParams(-1, -2));
            else slot.addView(preview, new FrameLayout.LayoutParams(-1, -2));
        }
        slot.setVisibility(slot.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        slot.setPadding(0, wiz ? kit.ui.dp(8) : 0, 0, 0);
        switch (page) {
            case HOME -> pages.home();
            case HOMESCREEN -> bricks.page();
            case BUTTON -> look.button();
            case WEATHER -> pages.weather();
            case CALENDAR -> pages.calendar();
            case SEARCH -> pages.search();
            case SHORTCUTS -> pages.shortcuts();
            case GESTURES -> pages.gestures();
            case WELLBEING -> pages.wellbeing();
            case PRIVACY -> pages.privacy();
            case HIDDEN -> pages.hidden();
            case BACKUP -> pages.backup();
            case LANGUAGE -> pages.language();
            case ABOUT -> pages.about();
            case W_LANG, W_HOME, W_MODE, W_CUSTOM, W_FAVS, W_ICONS, W_FONT, W_CLOCK, W_BUTTON, W_DONE -> wizard.page(page);
        }
        kit.pinned.setVisibility(kit.pinned.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        sv.scrollTo(0, y);
        SettingsKit.afterLayout(sv, () -> sv.scrollTo(0, y));
    }

    /** The page ends clear of the navigation bar, unless the wizard's bar sits over it. */
    private void padBottom() { sv.setPadding(0, 0, 0, wizBar.getVisibility() == View.VISIBLE ? 0 : bottomInset); }

    @Override
    public void onRequestPermissionsResult(int req, String[] names, int[] res) {
        if (req != SettingsPerms.REQ_PERM) return;
        perms.result(names, res);
        render();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == SettingsPerms.REQ_HOME) perms.homeResult(res); // onResume, next, shows the page again
        else if (req == REQ_BUTTON_ICON) {
            if (res == RESULT_OK && data != null && data.getData() != null) pages.buttonIcon(data.getData());
        } else pages.fileResult(req, res, data);
    }

    private void pickButtonIcon() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                    REQ_BUTTON_ICON);
        } catch (RuntimeException e) {
            kit.ui.toast(kit.ui.tr("No file picker on this phone"));
        }
    }
}
