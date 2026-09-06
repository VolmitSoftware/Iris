package art.arcane.iris.core.localization;

import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class BukkitUiMessages {
    public static final TextKey SCOREBOARD_TITLE = TextKey.of(
            "iris.bukkit.scoreboard.title",
            C.GREEN + "Iris"
    );
    public static final TextKey SCOREBOARD_SPEED = TextKey.of(
            "iris.bukkit.scoreboard.speed",
            C.GREEN + "Speed" + C.GRAY + ":  {speed}/s {duration}"
    );
    public static final TextKey SCOREBOARD_CACHE = TextKey.of(
            "iris.bukkit.scoreboard.cache",
            C.AQUA + "Cache" + C.GRAY + ": {count}"
    );
    public static final TextKey SCOREBOARD_MANTLE = TextKey.of(
            "iris.bukkit.scoreboard.mantle",
            C.AQUA + "Mantle" + C.GRAY + ": {count}"
    );
    public static final TextKey SCOREBOARD_CARVING = TextKey.of(
            "iris.bukkit.scoreboard.carving",
            C.LIGHT_PURPLE + "Carving" + C.GRAY + ": {state}"
    );
    public static final TextKey SCOREBOARD_REGION = TextKey.of(
            "iris.bukkit.scoreboard.region",
            C.AQUA + "Region" + C.GRAY + ": {region}"
    );
    public static final TextKey SCOREBOARD_BIOME = TextKey.of(
            "iris.bukkit.scoreboard.biome",
            C.AQUA + "Biome" + C.GRAY + ":  {biome}"
    );
    public static final TextKey SCOREBOARD_BIOME_LOADING = TextKey.of(
            "iris.bukkit.scoreboard.biome_loading",
            "Loading"
    );
    public static final TextKey SCOREBOARD_BIOME_UNAVAILABLE = TextKey.of(
            "iris.bukkit.scoreboard.biome_unavailable",
            "Unavailable"
    );
    public static final TextKey SCOREBOARD_HEIGHT = TextKey.of(
            "iris.bukkit.scoreboard.height",
            C.AQUA + "Height" + C.GRAY + ": {height}"
    );
    public static final TextKey SCOREBOARD_SLOPE = TextKey.of(
            "iris.bukkit.scoreboard.slope",
            C.AQUA + "Slope" + C.GRAY + ":  {slope}"
    );
    public static final TextKey SCOREBOARD_BLOCK_UPDATES = TextKey.of(
            "iris.bukkit.scoreboard.block_updates",
            C.AQUA + "BUD/s" + C.GRAY + ": {updates}"
    );

    private static final List<MessageKey> KEYS = List.of(
            SCOREBOARD_TITLE,
            SCOREBOARD_SPEED,
            SCOREBOARD_CACHE,
            SCOREBOARD_MANTLE,
            SCOREBOARD_CARVING,
            SCOREBOARD_REGION,
            SCOREBOARD_BIOME,
            SCOREBOARD_BIOME_LOADING,
            SCOREBOARD_BIOME_UNAVAILABLE,
            SCOREBOARD_HEIGHT,
            SCOREBOARD_SLOPE,
            SCOREBOARD_BLOCK_UPDATES
    );

    private BukkitUiMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
