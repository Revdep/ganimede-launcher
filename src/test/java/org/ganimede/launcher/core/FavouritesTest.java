package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.Set;

/** Which favourites and suggestions the home column lists; the Settings preview asks the same functions. */
public class FavouritesTest {
    public static void testFoldersAndShortcutsStayAndHiddenOrMissingAppsGo() {
        List<String> favs = List.of("app.one", "f:7", "s:pkg|id|Zz Prova", "app.hidden", "app.gone", "f:9");
        List<String> shown = Favourites.shown(favs, Set.of("app.hidden"), k -> !k.equals("app.gone") && !k.equals("f:9"));
        eq(List.of("app.one", "f:7", "s:pkg|id|Zz Prova"), shown, "kept in order");
    }

    public static void testShownEntriesKeepTheirPlaceInTheSavedList() {
        List<String> favs = List.of("app.hidden", "app.one", "app.gone", "f:7");
        eq(List.of(1, 3), Favourites.positions(favs, Set.of("app.hidden"), k -> !k.equals("app.gone")), "drag and drop index the saved list, not the drawn one");
    }

    public static void testSuggestsTheTwoMostUsedAppsNotFavouriteNorRefused() {
        List<String> visible = List.of("a", "b", "c", "d", "e");
        java.util.Map<String, Integer> uses = java.util.Map.of("a", 9, "b", 2, "c", 5, "d", 7, "e", 6);
        eq(List.of("d", "e"), Favourites.suggested(visible, List.of("a"), Set.of(), k -> uses.get(k)), "most used first");
        eq(List.of("e", "c"), Favourites.suggested(visible, List.of("a"), Set.of("d"), k -> uses.get(k)), "refused ones skipped");
        eq(List.of(), Favourites.suggested(visible, List.of(), Set.of(), k -> 2), "used fewer than 3 times: not suggested");
    }
}
