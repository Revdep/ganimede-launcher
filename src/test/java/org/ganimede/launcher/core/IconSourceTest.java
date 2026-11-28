package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;
import static org.ganimede.launcher.core.IconSource.ALPHA;
import static org.ganimede.launcher.core.IconSource.ARCTICONS;
import static org.ganimede.launcher.core.IconSource.CATEGORY;
import static org.ganimede.launcher.core.IconSource.GREYS;
import static org.ganimede.launcher.core.IconSource.MONOCHROME;

import java.util.Set;

/** Where an icon's glyph comes from (issue 079): the first source the icon has, in a fixed order. */
public class IconSourceTest {
    public static void testOrder() {
        // ARCTICONS is in the set only when the built-in set is chosen and the app is in its index
        eq(ARCTICONS, IconSource.pick(false, Set.of(ARCTICONS, MONOCHROME, ALPHA)::contains), "built-in set, entry found: Arcticons");
        eq(MONOCHROME, IconSource.pick(false, Set.of(MONOCHROME, ALPHA)::contains), "built-in set, no entry, a monochrome layer: monochrome");
        IconSource packed = IconSource.pick(true, Set.of(MONOCHROME, ALPHA)::contains);
        yes(packed != MONOCHROME, "an icon from an installed pack skips the monochrome layer (got " + packed + ")");
        eq(ALPHA, packed, "an installed pack's icon with a usable alpha: its silhouette");
        eq(ALPHA, IconSource.pick(false, Set.of(ALPHA)::contains), "no monochrome, a usable alpha: silhouette");
        eq(GREYS, IconSource.pick(false, Set.of()::contains), "no monochrome, alpha not usable: greys");
    }

    public static void testAlphaIsOnlyAskedWhenReached() {
        // the silhouette needs the icon drawn: an earlier source must spare that work
        IconSource s = IconSource.pick(false, src -> {
            if (src == ALPHA) throw new AssertionError("alpha asked although the monochrome layer comes first");
            return src == MONOCHROME;
        });
        eq(MONOCHROME, s, "monochrome found before alpha");
    }

    /** Issue 087, order B: the category glyph replaces the alpha silhouette, not the monochrome layer. */
    public static void testCategoryOrder() {
        eq(MONOCHROME, IconSource.pick(false, Set.of(MONOCHROME, CATEGORY, ALPHA)::contains), "monochrome beats the category");
        eq(CATEGORY, IconSource.pick(false, Set.of(CATEGORY, ALPHA)::contains), "no monochrome, a category: its glyph");
        eq(ALPHA, IconSource.pick(false, Set.of(ALPHA)::contains), "no category (or the setting off): silhouette");
        eq(ARCTICONS, IconSource.pick(false, Set.of(ARCTICONS, CATEGORY)::contains), "the set's own entry beats the category");
        eq(CATEGORY, IconSource.pick(true, Set.of(MONOCHROME, CATEGORY, ALPHA)::contains), "an installed pack's icon skips monochrome, takes the category");
    }
}
