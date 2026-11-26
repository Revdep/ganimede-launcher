package org.ganimede.launcher;

/** The pages of Settings: the categories in their order on the settings home, the home itself, then the wizard steps. */
enum Page {
    HOMESCREEN("home", "Customize", "Your home", true), BUTTON("button", "Ganimede button", null, true),
    WEATHER("cloud", "Weather", "What it shows", false),
    CALENDAR("calendar", "Calendar & productivity", null, false), SEARCH("search", "Search & shortcuts", null, false),
    GESTURES("hand", "Gestures", "How it behaves", false), WELLBEING("bell", "Notifications & wellbeing", null, false),
    HIDDEN("eyeoff", "Hidden apps", null, false), PRIVACY("shield", "Privacy & permissions", "This phone", false),
    BACKUP("backup", "Backup & restore", null, false), LANGUAGE("globe", "Language", null, false), ABOUT("info", "About", null, false),
    HOME(null, "Settings", null, true), SHORTCUTS(null, "My shortcuts", null, false),
    W_LANG("globe", "Welcome", null, false), W_HOME("home", "Make it your home", null, false),
    W_MODE("grid", "Choose a style", null, false), W_CUSTOM("edit", "Make it yours", null, false),
    W_FAVS("star", "Your favourite apps", null, true), W_ICONS("apps", "Icons", null, true), W_FONT("type", "Font", null, true),
    W_CLOCK("clock", "Clock", null, true), W_BUTTON("button", "The Ganimede button", null, true), W_DONE("check", "All set", null, false);

    static final java.util.Set<Page> CATEGORIES = java.util.EnumSet.range(HOMESCREEN, ABOUT);
    static final java.util.List<Page> STEPS = java.util.List.copyOf(java.util.EnumSet.range(W_LANG, W_DONE));
    /** Line icon, English title (through T), the group a category opens on the settings home; preview: the live home above. */
    final String glyph, title, group;
    final boolean preview;

    Page(String glyph, String title, String group, boolean preview) {
        this.glyph = glyph;
        this.title = title;
        this.group = group;
        this.preview = preview;
    }

    /** Position among the wizard steps, negative for a settings page. */
    int step() { return STEPS.indexOf(this); }

    /** The wizard step after this one. */
    Page next() { return STEPS.get(step() + 1); }
}
