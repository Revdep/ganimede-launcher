package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;

import java.util.List;
import java.util.Set;

public class SuggestionsTest {
    public static void testRemovedFavouriteStaysUnsuggestedUntilReAdded() {
        Set<String> unsuggested = Suggestions.unsuggested(Set.of("already"), List.of("a", "b"), List.of("b"));
        eq(Set.of("already", "a"), unsuggested, "a removed favourite joins the unsuggested apps");

        unsuggested = Suggestions.unsuggested(unsuggested, List.of("b"), List.of("a", "b"));
        eq(Set.of("already"), unsuggested, "a re-added favourite becomes suggestible again");
    }
}
