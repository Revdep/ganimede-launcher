package org.ganimede.launcher.core;

/** The List home column's distances, read by the home screen and by the Settings preview alike. */
public final class HomeColumn {
    /** dp: end padding (clear of the alphabet), bottom padding, then the start margins of the clock, the text lines and the apps. */
    public static int[] list(int alphaOffset, int homeBottom, int clockOffset, int lineOffset, int appsOffset) {
        return new int[]{Gestures.alphabetEndPaddingDp(alphaOffset), homeBottom, clockOffset, lineOffset,
                Gestures.effectiveAppsOffsetDp(appsOffset)};
    }

    private HomeColumn() {}
}
