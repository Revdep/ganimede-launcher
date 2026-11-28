package org.ganimede.launcher.core;

import java.util.Optional;

/** The Play Store's app categories, each with the built-in glyph that stands for an app of it that has no icon of its own (issue 087). */
public enum AppCategory {
    ART_AND_DESIGN("emoji_artist_palette"), AUTO_AND_VEHICLES("car"), BEAUTY("emoji_dress"), BOOKS_AND_REFERENCE("books"),
    BUSINESS("emoji_briefcase"), COMICS("emoji_collision_symbol"), COMMUNICATION("chat"), DATING("emoji_two_hearts"),
    EDUCATION("school"), ENTERTAINMENT("tv"), EVENTS("emoji_party_popper"), FINANCE("emoji_classical_building"),
    FOOD_AND_DRINK("emoji_pot_of_food"), HEALTH_AND_FITNESS("workout"), HOUSE_AND_HOME("home"), LIBRARIES_AND_DEMO("emoji_package"),
    LIFESTYLE("emoji_potted_plant"), MAPS_AND_NAVIGATION("maps"), MEDICAL("pill"), MUSIC_AND_AUDIO("music"),
    NEWS_AND_MAGAZINES("emoji_newspaper"), PARENTING("emoji_baby_bottle"), PERSONALIZATION("themes"), PHOTOGRAPHY("camera"),
    PRODUCTIVITY("emoji_clipboard"), SHOPPING("shopping"), SOCIAL("emoji_busts_in_silhouette"), SPORTS("emoji_soccer_ball"),
    TOOLS("emoji_hammer_and_wrench"), TRAVEL_AND_LOCAL("emoji_airplane"), VIDEO_PLAYERS("emoji_clapper_board"), WEATHER("weather"),
    GAME_ACTION("games"), GAME_ADVENTURE("games"), GAME_ARCADE("games"), GAME_BOARD("games"), GAME_CARD("games"),
    GAME_CASINO("games"), GAME_CASUAL("games"), GAME_EDUCATIONAL("games"), GAME_MUSIC("games"), GAME_PUZZLE("games"),
    GAME_RACING("games"), GAME_ROLE_PLAYING("games"), GAME_SIMULATION("games"), GAME_SPORTS("games"), GAME_STRATEGY("games"),
    GAME_TRIVIA("games"), GAME_WORD("games");

    private final String glyph;

    AppCategory(String glyph) { this.glyph = glyph; }

    /** The name of the built-in icon that stands for the category. */
    public String glyph() { return glyph; }

    /**
     * The category an app declares (ApplicationInfo.category, API 26): coarse, so only what maps to one Play category.
     * UNDEFINED and values from a newer Android: none.
     */
    public static Optional<AppCategory> fromManifest(int category) {
        return switch (category) {
            case 0 -> Optional.of(GAME_CASUAL); // no finer genre: any game category has the one glyph
            case 1 -> Optional.of(MUSIC_AND_AUDIO);
            case 2 -> Optional.of(VIDEO_PLAYERS);
            case 3 -> Optional.of(PHOTOGRAPHY);
            case 4 -> Optional.of(SOCIAL);
            case 5 -> Optional.of(NEWS_AND_MAGAZINES);
            case 6 -> Optional.of(MAPS_AND_NAVIGATION);
            case 7 -> Optional.of(PRODUCTIVITY);
            case 8 -> Optional.of(TOOLS); // CATEGORY_ACCESSIBILITY: no glyph of its own, deliberately the tools one
            default -> Optional.empty();
        };
    }
}
