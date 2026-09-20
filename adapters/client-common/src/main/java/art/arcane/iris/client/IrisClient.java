package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.ProtocolException;

public final class IrisClient {
    public static final String HUD_ELEMENT_ID = "irisworldgen:pregen_hud";
    public static final String KEYBIND_CATEGORY_ID = "irisworldgen:iris";
    public static final String KEYBIND_TOGGLE_HUD = "key.irisworldgen.toggle_pregen_hud";
    public static final String KEYBIND_OPEN_MAP = "key.irisworldgen.open_vision_map";
    public static final String KEYBIND_TOGGLE_WHAT = "key.irisworldgen.toggle_what_overlay";

    private static final IrisClientSession SESSION = new IrisClientSession();
    private static final IrisClientPregenState PREGEN = new IrisClientPregenState();
    private static final IrisClientDimension DIMENSION = new IrisClientDimension();
    private static final IrisClientTileCache TILES = new IrisClientTileCache(IrisClient::sendFrame, System::currentTimeMillis);
    private static final IrisClientCursor CURSOR = new IrisClientCursor(IrisClient::sendFrame, System::currentTimeMillis);
    private static final IrisClientMarkers MARKERS = new IrisClientMarkers();
    private static final IrisClientRegionMap REGIONS = new IrisClientRegionMap();
    private static final IrisClientToasts TOASTS = new IrisClientToasts();
    private static volatile ClientPacketSink sink;
    private static volatile boolean hudVisible = true;
    private static volatile boolean whatVisible = false;

    private IrisClient() {
    }

    public static IrisClientSession session() {
        return SESSION;
    }

    public static IrisClientPregenState pregen() {
        return PREGEN;
    }

    public static IrisClientDimension dimension() {
        return DIMENSION;
    }

    public static IrisClientTileCache tiles() {
        return TILES;
    }

    public static IrisClientCursor cursor() {
        return CURSOR;
    }

    public static IrisClientMarkers markers() {
        return MARKERS;
    }

    public static IrisClientRegionMap regions() {
        return REGIONS;
    }

    public static IrisClientToasts toasts() {
        return TOASTS;
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    public static boolean whatVisible() {
        return whatVisible;
    }

    public static void toggleWhat() {
        whatVisible = !whatVisible;
    }

    public static boolean visionAvailable() {
        return SESSION.isReady() && DIMENSION.irisWorld() && (SESSION.serverCapabilities() & IrisProtocol.CAPABILITY_VISION) != 0L;
    }

    public static boolean cursorAvailable() {
        return SESSION.isReady() && DIMENSION.irisWorld() && (SESSION.serverCapabilities() & IrisProtocol.CAPABILITY_CURSOR) != 0L;
    }

    public static void bindSender(ClientPacketSink boundSink) {
        sink = boundSink;
        SESSION.bind(boundSink);
    }

    public static void sendFrame(byte[] frame) {
        ClientPacketSink activeSink = sink;
        if (activeSink != null) {
            activeSink.send(frame);
        }
    }

    public static void onWorldJoin() {
        clearWorldState();
        SESSION.sendHello();
    }

    public static void onDisconnect() {
        SESSION.reset();
        clearWorldState();
    }

    public static void tick() {
        SESSION.tick();
    }

    private static void clearWorldState() {
        PREGEN.clear();
        DIMENSION.clear();
        TILES.clear();
        CURSOR.clear();
        MARKERS.clear();
        REGIONS.clear();
        TOASTS.clear();
    }

    public static void onInbound(byte[] frame) {
        if (frame == null) {
            return;
        }
        IrisMessage message;
        try {
            message = IrisMessageCodec.decode(frame);
        } catch (ProtocolException rejected) {
            return;
        }
        if (message == null) {
            return;
        }
        route(message);
    }

    private static void route(IrisMessage message) {
        switch (message) {
            case IrisMessage.ServerHello serverHello -> SESSION.onServerHello(serverHello);
            case IrisMessage.PregenProgress progress -> PREGEN.onProgress(progress);
            case IrisMessage.PregenEnd end -> onPregenEnd(end.jobId());
            case IrisMessage.DimensionStatus status -> onDimensionStatus(status);
            case IrisMessage.CursorInfo cursorInfo -> CURSOR.onCursorInfo(cursorInfo);
            case IrisMessage.VisionTile visionTile -> TILES.onVisionTile(visionTile);
            case IrisMessage.VisionMarkers visionMarkers -> MARKERS.onMarkers(visionMarkers);
            case IrisMessage.PregenRegionDelta delta -> REGIONS.onDelta(delta, PREGEN.activeJobId());
            case IrisMessage.StudioHotload hotload -> onStudioHotload(hotload);
            case IrisMessage.Toast toast -> TOASTS.enqueue(toast.kind(), toast.title(), toast.body());
            default -> {
            }
        }
    }

    private static void onPregenEnd(long jobId) {
        PREGEN.onEnd(jobId);
        REGIONS.onEnd(jobId);
    }

    private static void onDimensionStatus(IrisMessage.DimensionStatus status) {
        boolean changed = DIMENSION.onDimensionStatus(status);
        if (changed) {
            TILES.clear();
            MARKERS.clear();
            CURSOR.clear();
            REGIONS.clear();
        }
    }

    private static void onStudioHotload(IrisMessage.StudioHotload hotload) {
        if (shouldInvalidateForHotload(DIMENSION.status(), hotload)) {
            TILES.clear();
            MARKERS.clear();
            CURSOR.clear();
        }
        TOASTS.enqueueHotload(
                hotload.packKey(),
                hotload.changedFiles(),
                hotload.failed(),
                hotload.message()
        );
    }

    static boolean shouldInvalidateForHotload(
            IrisMessage.DimensionStatus status,
            IrisMessage.StudioHotload hotload
    ) {
        return status != null
                && status.irisWorld()
                && hotload != null
                && !hotload.failed()
                && status.packKey().equals(hotload.packKey());
    }
}
