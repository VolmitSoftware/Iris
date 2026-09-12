package art.arcane.iris.world.lifecycle;

import java.util.Locale;

public enum ServerFamily {
    BUKKIT,
    SPIGOT,
    PAPER,
    PURPUR,
    FOLIA,
    CANVAS,
    UNKNOWN;

    public boolean isPaperLike() {
        return this == PAPER || this == PURPUR || this == FOLIA || this == CANVAS;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
