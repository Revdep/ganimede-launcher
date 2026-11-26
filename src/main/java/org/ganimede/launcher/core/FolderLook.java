package org.ganimede.launcher.core;

/** The resolved background, layout and readable text colour of an open folder. */
public record FolderLook(int color, int columns, int text) {
    public static final String BACKGROUND_KEY = "folder_bg", ALPHA_KEY = "folder_alpha", COLUMNS_KEY = "folder_cols";
    public static final int DEFAULT_BACKGROUND = 0, DEFAULT_COLUMNS = 1;

    public static int defaultAlpha(boolean lightText) { return lightText ? 94 : 96; }

    public static FolderLook resolve(Integer bg, Integer alpha, Integer cols,
            int defaultBg, int defaultAlpha, int defaultCols, boolean lightText) {
        int background = bg == null ? defaultBg : bg;
        if (background == 0) background = lightText ? 0xFF201A33 : 0xFFFFF8F0;
        int opacity = Math.max(0, Math.min(100, alpha == null ? defaultAlpha : alpha));
        int columns = Math.max(1, Math.min(5, cols == null ? defaultCols : cols));
        return new FolderLook(Colors.alpha(background, Math.round(opacity * 2.55f)), columns, Colors.on(background));
    }

    /** Width in px of a pop-up of {@code columns} cells of {@code cell} px with {@code padding} px each side, at least {@code minWidth}, at most {@code screenMax}. */
    public static int popupWidth(int columns, int cell, int padding, int minWidth, int screenMax) {
        return Math.max(0, Math.min(Math.max(columns * cell + 2 * padding, minWidth), screenMax));
    }
}
