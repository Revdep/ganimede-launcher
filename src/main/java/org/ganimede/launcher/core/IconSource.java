package org.ganimede.launcher.core;

import java.util.function.Predicate;

/**
 * Where an icon's glyph comes from (issue 079), in this order: the built-in set's entry, the adaptive icon's monochrome
 * layer, the silhouette of its alpha (IconMask.isUsable), else its colours as tinted greys. A new source goes in its
 * place in the list; pick does not change.
 */
public enum IconSource {
    ARCTICONS, MONOCHROME, ALPHA, GREYS;

    /**
     * The first source the icon has. {@code has} is asked in order and only until one answers yes (the silhouette needs
     * the icon drawn: an earlier source spares it; a caller that can't draw yet may answer yes and fall to GREYS once a
     * drawn silhouette proves unusable); GREYS is always there. An icon from an installed pack skips the
     * monochrome layer: it is the pack's picture, not the app's.
     */
    public static IconSource pick(boolean fromInstalledPack, Predicate<IconSource> has) {
        for (IconSource s : values())
            if (s == GREYS || !(fromInstalledPack && s == MONOCHROME) && has.test(s)) return s;
        throw new AssertionError(); // GREYS is last
    }
}
