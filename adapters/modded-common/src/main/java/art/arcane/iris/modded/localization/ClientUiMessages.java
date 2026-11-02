package art.arcane.iris.modded.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Map;

public final class ClientUiMessages {
    public static final TextKey VISION_TITLE = TextKey.of(
            "iris.client.vision.title",
            "Iris Vision"
    );
    public static final TextKey VISION_CONNECTING = TextKey.of(
            "iris.client.vision.connecting",
            "Connecting to Iris server..."
    );
    public static final TextKey VISION_NOT_CONNECTED = TextKey.of(
            "iris.client.vision.not_connected",
            "not connected"
    );
    public static final TextKey VISION_NOT_IRIS_WORLD = TextKey.of(
            "iris.client.vision.not_iris_world",
            "Not an Iris world"
    );
    public static final TextKey VISION_SERVER_WITHOUT_IRIS = TextKey.of(
            "iris.client.vision.server_without_iris",
            "This server does not run Iris"
    );
    public static final TextKey VISION_VERSION_MISMATCH = TextKey.of(
            "iris.client.vision.version_mismatch",
            "Iris version mismatch between client and server"
    );
    public static final TextKey VISION_NO_DIMENSION_DATA = TextKey.of(
            "iris.client.vision.no_dimension_data",
            "no dimension data"
    );
    public static final TextKey VISION_HEADER_DETAIL = TextKey.of(
            "iris.client.vision.header_detail",
            "{status}  zoom {zoom}  x{x} z{z}"
    );
    public static final TextKey VISION_FOOTER_HINT = TextKey.of(
            "iris.client.vision.footer_hint",
            "Drag to pan   Scroll to zoom   Esc to close"
    );
    public static final TextKey VISION_DIMENSION_PACK = TextKey.of(
            "iris.client.vision.dimension_pack",
            "{dimension}  pack {pack}"
    );
    public static final TextKey CREATE_STRUCTURES_REQUIRED_TITLE = TextKey.of(
            "iris.client.create.structures_required_title",
            "Iris requires Generate Structures"
    );
    public static final TextKey CREATE_STRUCTURES_REQUIRED_BODY = TextKey.of(
            "iris.client.create.structures_required_body",
            "Iris places its own structures through the structure generation step, and refuses to load a world that was created with Generate Structures off. Turn Generate Structures back on, or choose a different world type."
    );
    public static final TextKey TOAST_STUDIO_HOTLOAD = TextKey.of("iris.client.toast.studio_hotload", "Studio Hotload");
    public static final PluralKey TOAST_CHANGED_FILES = PluralKey.of(
            "iris.client.toast.changed_files",
            "count",
            Map.of(
                    "one", "{count} file",
                    "other", "{count} files"
            )
    );
    public static final TextKey TOAST_RELOAD_FAILED = TextKey.of("iris.client.toast.reload_failed", "reload failed");
    public static final TextKey TOAST_RELOADED = TextKey.of("iris.client.toast.reloaded", "reloaded");
    public static final TextKey WHAT_TITLE = TextKey.of("iris.client.what.title", "Iris What");
    public static final TextKey WHAT_QUERYING = TextKey.of("iris.client.what.querying", "Querying {x}, {z}...");
    public static final TextKey WHAT_BIOME = TextKey.of("iris.client.what.biome", "Biome: {biome}");
    public static final TextKey WHAT_REGION = TextKey.of("iris.client.what.region", "Region: {region}");
    public static final TextKey WHAT_CAVE = TextKey.of("iris.client.what.cave", "Cave: {cave}");
    public static final TextKey WHAT_HEIGHT = TextKey.of("iris.client.what.height", "Height: {height}   ({x}, {z})");
    public static final TextKey PREGEN_STATS = TextKey.of("iris.client.pregen.stats", "{done} / {total}  ({percent}%)");
    public static final TextKey PREGEN_PAUSED = TextKey.of("iris.client.pregen.paused", "PAUSED");
    public static final TextKey PREGEN_STALE = TextKey.of("iris.client.pregen.stale", "no updates for {seconds}s");
    public static final TextKey PREGEN_RATE = TextKey.of("iris.client.pregen.rate", "{rate}/s");
    public static final TextKey PREGEN_RATE_ETA = TextKey.of("iris.client.pregen.rate_eta", "{rate}/s  ETA {eta}");
    public static final TextKey DURATION_HOURS_MINUTES = TextKey.of("iris.client.duration.hours_minutes", "{hours}h {minutes}m");
    public static final TextKey DURATION_MINUTES_SECONDS = TextKey.of("iris.client.duration.minutes_seconds", "{minutes}m {seconds}s");
    public static final TextKey DURATION_SECONDS = TextKey.of("iris.client.duration.seconds", "{seconds}s");

    private static final List<MessageKey> KEYS = List.of(
            VISION_TITLE,
            VISION_CONNECTING,
            VISION_NOT_CONNECTED,
            VISION_NOT_IRIS_WORLD,
            VISION_SERVER_WITHOUT_IRIS,
            VISION_VERSION_MISMATCH,
            VISION_NO_DIMENSION_DATA,
            VISION_HEADER_DETAIL,
            VISION_FOOTER_HINT,
            VISION_DIMENSION_PACK,
            CREATE_STRUCTURES_REQUIRED_TITLE,
            CREATE_STRUCTURES_REQUIRED_BODY,
            TOAST_STUDIO_HOTLOAD,
            TOAST_CHANGED_FILES,
            TOAST_RELOAD_FAILED,
            TOAST_RELOADED,
            WHAT_TITLE,
            WHAT_QUERYING,
            WHAT_BIOME,
            WHAT_REGION,
            WHAT_CAVE,
            WHAT_HEIGHT,
            PREGEN_STATS,
            PREGEN_PAUSED,
            PREGEN_STALE,
            PREGEN_RATE,
            PREGEN_RATE_ETA,
            DURATION_HOURS_MINUTES,
            DURATION_MINUTES_SECONDS,
            DURATION_SECONDS
    );

    private ClientUiMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
