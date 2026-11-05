package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import static art.arcane.iris.core.localization.IrisMessages.plural;
import static art.arcane.iris.core.localization.IrisMessages.text;

public final class ClientUiMessages {
    public static final TextKey VISION_TITLE = text("iris.client.vision.title");
    public static final TextKey VISION_CONNECTING = text("iris.client.vision.connecting");
    public static final TextKey VISION_NOT_CONNECTED = text("iris.client.vision.not_connected");
    public static final TextKey VISION_NOT_IRIS_WORLD = text("iris.client.vision.not_iris_world");
    public static final TextKey VISION_SERVER_WITHOUT_IRIS = text("iris.client.vision.server_without_iris");
    public static final TextKey VISION_VERSION_MISMATCH = text("iris.client.vision.version_mismatch");
    public static final TextKey VISION_NO_DIMENSION_DATA = text("iris.client.vision.no_dimension_data");
    public static final TextKey VISION_HEADER_DETAIL = text("iris.client.vision.header_detail");
    public static final TextKey VISION_FOOTER_HINT = text("iris.client.vision.footer_hint");
    public static final TextKey VISION_DIMENSION_PACK = text("iris.client.vision.dimension_pack");
    public static final TextKey CREATE_STRUCTURES_REQUIRED_TITLE = text("iris.client.create.structures_required_title");
    public static final TextKey CREATE_STRUCTURES_REQUIRED_BODY = text("iris.client.create.structures_required_body");
    public static final TextKey TOAST_STUDIO_HOTLOAD = text("iris.client.toast.studio_hotload");
    public static final PluralKey TOAST_CHANGED_FILES = plural("iris.client.toast.changed_files");
    public static final TextKey TOAST_RELOAD_FAILED = text("iris.client.toast.reload_failed");
    public static final TextKey TOAST_RELOADED = text("iris.client.toast.reloaded");
    public static final TextKey TOAST_PACK_FAILED = text("iris.client.toast.pack_failed");
    public static final TextKey WHAT_TITLE = text("iris.client.what.title");
    public static final TextKey WHAT_QUERYING = text("iris.client.what.querying");
    public static final TextKey WHAT_BIOME = text("iris.client.what.biome");
    public static final TextKey WHAT_REGION = text("iris.client.what.region");
    public static final TextKey WHAT_CAVE = text("iris.client.what.cave");
    public static final TextKey WHAT_HEIGHT = text("iris.client.what.height");
    public static final TextKey PREGEN_STATS = text("iris.client.pregen.stats");
    public static final TextKey PREGEN_PAUSED = text("iris.client.pregen.paused");
    public static final TextKey PREGEN_STALE = text("iris.client.pregen.stale");
    public static final TextKey PREGEN_RATE = text("iris.client.pregen.rate");
    public static final TextKey PREGEN_RATE_ETA = text("iris.client.pregen.rate_eta");
    public static final TextKey DURATION_HOURS_MINUTES = text("iris.client.duration.hours_minutes");
    public static final TextKey DURATION_MINUTES_SECONDS = text("iris.client.duration.minutes_seconds");
    public static final TextKey DURATION_SECONDS = text("iris.client.duration.seconds");

    private ClientUiMessages() {
    }
}
