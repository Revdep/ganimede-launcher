package org.ganimede.launcher;

import org.ganimede.launcher.core.FolderLook;

import android.content.SharedPreferences;
import android.widget.LinearLayout;

import java.util.function.IntConsumer;

/** The three folder-look controls shared by Settings and the home-side folder dialog. */
final class FolderControls {
    /** The global folder look {background, opacity, columns}; a folder's own values override it. */
    static int[] defaults(SharedPreferences prefs, boolean lightText) {
        return new int[]{prefs.getInt(FolderLook.BACKGROUND_KEY, FolderLook.DEFAULT_BACKGROUND),
                prefs.getInt(FolderLook.ALPHA_KEY, FolderLook.defaultAlpha(lightText)),
                prefs.getInt(FolderLook.COLUMNS_KEY, FolderLook.DEFAULT_COLUMNS)};
    }

    static void add(SettingsKit kit, LinearLayout card, int background, int alpha, int columns,
            IntConsumer setBackground, IntConsumer setAlpha, IntConsumer setColumns) {
        int[] accents = SettingsLook.accents(), colors = new int[accents.length + 1];
        System.arraycopy(accents, 0, colors, 1, accents.length);
        String[] names = new String[colors.length];
        names[0] = kit.ui.tr("Automatic");
        String[] accentNames = kit.tr(SettingsLook.accentNames());
        System.arraycopy(accentNames, 0, names, 1, accentNames.length);
        SettingsSamples samples = new SettingsSamples(kit);

        kit.sub(card, kit.ui.tr("Background colour"));
        kit.tiles(card, colors.length, SettingsKit.indexOf(colors, background), names,
                i -> samples.swatch(i == 0
                        ? FolderLook.resolve(FolderLook.DEFAULT_BACKGROUND, 100, 1,
                                FolderLook.DEFAULT_BACKGROUND, 100, 1, kit.ui.light()).color()
                        : colors[i]),
                i -> setBackground.accept(colors[i]));
        kit.slider(card, kit.ui.tr("Background opacity (%)"), alpha, 0, 100, setAlpha);
        kit.slider(card, kit.ui.tr("Columns"), columns, 1, 5, setColumns);
    }

    private FolderControls() {}
}
