/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.gui.GuiHost;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.core.gui.GuiMarker;
import art.arcane.iris.core.project.StudioEditor;
import art.arcane.iris.core.project.StudioEditorLauncher;
import art.arcane.iris.core.studio.map.BlockSource;
import art.arcane.iris.core.studio.map.MapHover;
import art.arcane.iris.core.studio.map.MapLabel;
import art.arcane.iris.core.studio.map.MapLayer;
import art.arcane.iris.core.studio.map.MapPickInspection;
import art.arcane.iris.core.studio.map.MapTile;
import art.arcane.iris.core.studio.map.MapView;
import art.arcane.iris.core.studio.assets.MinecraftAssets;
import art.arcane.iris.core.studio.noise.NoiseSources;
import art.arcane.iris.core.studio.noise.NoiseTile;
import art.arcane.iris.core.studio.saved.SavedBiomeLabels;
import art.arcane.iris.core.studio.saved.SavedTile;
import art.arcane.iris.core.studio.saved.SavedWorld;
import art.arcane.iris.core.studio.saved.SavedWorlds;
import art.arcane.iris.core.studio.scene.StudioEditors;
import art.arcane.iris.core.studio.scene.StudioPatterns;
import art.arcane.iris.core.studio.scene.StudioScenes;
import art.arcane.iris.core.studio.scene.StudioSuggestions;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.function.NoiseProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.volmit.object.GeneratedObject;
import com.volmit.tooling.BlockPattern;
import com.volmit.tooling.PatternArea;
import com.volmit.tooling.PatternEntry;
import com.volmit.tooling.BakeRun;
import com.volmit.tooling.BlockParts;
import com.volmit.tooling.SceneEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * A studio app's connection to one world: HTTP on the loopback interface, on a free port, each request carrying the
 * bridge's token as {@code Authorization: Bearer <token>}. JSON for the world, hovers and players; tiles in
 * {@link MapTileEncoding}; {@code /v1/events} streams {@link StudioEvents}. Requests run on a small pool of daemon
 * threads, so a busy studio cannot hold up the server.
 */
public final class StudioBridge implements AutoCloseable {
    private static final String API = "/v1/";
    private static final String ASSETS_ENDPOINT = "assets/";
    private static final String ASSET_RETRY_SECONDS = "2";
    private static final long ASSET_CACHE_SECONDS = 86_400L;
    private static final int TOKEN_BYTES = 24;
    private static final int DEFAULT_TILE_SIZE = 128;
    private static final int LARGEST_TILE_SIZE = 512;
    // A procedural object's file is a few kilobytes; this leaves room for any pack file without holding a flood.
    private static final int LARGEST_BODY_BYTES = 4 * 1024 * 1024;
    private static final double SMALLEST_BLOCKS_PER_PIXEL = 1D / 16D;
    private static final double LARGEST_BLOCKS_PER_PIXEL = 4_096D;
    private static final long DROPPED_TILE_KEPT_MINUTES = 10;
    // Each pixel reads the chunk it lands in, so a saved world is shown no farther out than a pixel per chunk.
    private static final int LARGEST_SAVED_BLOCKS_PER_PIXEL = 16;
    // A palette is picked block by block, so far out a column stands for the blocks around it; past this it says little.
    private static final int LARGEST_PATTERN_BLOCKS_PER_COLUMN = 64;
    // Noise has detail finer than a block, so it may be looked at closer than the world.
    private static final double SMALLEST_NOISE_BLOCKS_PER_PIXEL = 1D / 64D;
    private static final long ENTITY_WAIT_MILLIS = 3_000L;
    private static final int STATUS_OK = 200;
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_CONFLICT = 409;
    private static final int STATUS_UNPROCESSABLE = 422;
    private static final int STATUS_SERVER_ERROR = 500;
    private static final int STATUS_UNAVAILABLE = 503;

    private final StudioWorld world;
    private final String token;
    // The studio tells "none" from "not sent", so a null field is written as null rather than left out.
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final HttpServer server;
    private final int port;
    private final ExecutorService workers;
    private final ScheduledExecutorService ticker;
    private final StudioEvents events;
    private final Runnable hotloadHook;
    // Whether the studio stopped waiting for each tile, by the id it gave the request. A cancel can arrive before its tile
    // does, so each is kept a while rather than for its tile alone.
    private final Cache<String, AtomicBoolean> droppedTiles = Caffeine.newBuilder().expireAfterWrite(DROPPED_TILE_KEPT_MINUTES, TimeUnit.MINUTES).build();
    private volatile boolean closed;

    private StudioBridge(StudioWorld world) throws IOException {
        this.world = world;
        this.token = newToken();
        this.events = new StudioEvents(world, gson);
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.port = server.getAddress().getPort();
        this.workers = Executors.newFixedThreadPool(workerCount(), daemonThreads("Iris Studio Bridge"));
        this.ticker = Executors.newSingleThreadScheduledExecutor(daemonThreads("Iris Studio Events"));
        this.hotloadHook = events::hotloaded;
        server.setExecutor(workers);
        server.createContext(API, this::handle);
    }

    static StudioBridge start(StudioWorld world) throws IOException {
        StudioBridge bridge = new StudioBridge(world);
        bridge.server.start();
        bridge.ticker.scheduleWithFixedDelay(bridge::pollEvents, 0L, StudioEvents.POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        GuiHost.get().registerHotloadHook(bridge.hotloadHook);
        return bridge;
    }

    public String worldName() {
        return world.name();
    }

    public int port() {
        return port;
    }

    public String token() {
        return token;
    }

    /** What the studio app opens to connect. */
    public String link() {
        return "iris-studio://127.0.0.1:" + port() + "/?token=" + token;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        GuiHost.get().unregisterHotloadHook(hotloadHook);
        ticker.shutdownNow();
        events.closeAll();
        server.stop(0);
        workers.shutdownNow();
    }

    private void pollEvents() {
        try {
            events.poll();
        } catch (Throwable error) {
            IrisLogging.debug("Studio events failed: " + error);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            allowAnyOrigin(exchange.getResponseHeaders());
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(STATUS_NO_CONTENT, -1);
                exchange.close();
                return;
            }
            if (!isAuthorized(exchange)) {
                throw new RequestError(STATUS_UNAUTHORIZED, "Missing or wrong token");
            }
            route(exchange);
        } catch (RequestError error) {
            sendJson(exchange, error.status, new ErrorBody(error.getMessage()));
        } catch (CancellationException stopped) {
            sendJson(exchange, STATUS_UNAVAILABLE, new ErrorBody("The world stopped"));
        } catch (IOException clientHungUp) {
            exchange.close();
        } catch (Throwable error) {
            IrisLogging.reportError("Studio bridge request " + exchange.getRequestURI().getPath(), error);
            sendJson(exchange, STATUS_SERVER_ERROR, new ErrorBody(String.valueOf(error.getMessage())));
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String endpoint = exchange.getRequestURI().getPath().substring(API.length());
        Query query = Query.of(exchange.getRequestURI());
        if (endpoint.startsWith(ASSETS_ENDPOINT)) {
            sendAsset(exchange, endpoint);
            return;
        }
        switch (endpoint) {
            case "world" -> sendJson(exchange, STATUS_OK, worldInfo(get(exchange)));
            case "tile" -> sendBytes(exchange, "application/octet-stream", tile(get(exchange), query));
            case "tile-cancel" -> cancelTile(exchange, query);
            case "hover" -> sendJson(exchange, STATUS_OK, hover(get(exchange).engine(), query));
            case "picks" -> sendJson(exchange, STATUS_OK, picks(get(exchange).engine(), query));
            case "regions" -> sendJson(exchange, STATUS_OK, regions(get(exchange).engine()));
            case "entities" -> sendJson(exchange, STATUS_OK, entities(get(exchange)));
            case "scenes" -> sendJson(exchange, STATUS_OK, sceneViews(get(exchange).engine()));
            case "scene-file" -> sendJson(exchange, STATUS_OK, sceneFile(get(exchange).engine(), query));
            case "scene-edit" -> sendJson(exchange, STATUS_OK, sceneEdit(exchange, query));
            case "scene-write" -> sendJson(exchange, STATUS_OK, sceneWrite(exchange, query));
            case "scene-create" -> sendJson(exchange, STATUS_OK, sceneCreate(exchange, query));
            case "scene-uses" -> sendJson(exchange, STATUS_OK, sceneUses(get(exchange).engine(), query));
            case "scene-delete" -> sendJson(exchange, STATUS_OK, sceneDelete(exchange, query));
            case "suggestions" -> sendJson(exchange, STATUS_OK, suggestions(get(exchange).engine(), query));
            case "scene-entries" -> sendJson(exchange, STATUS_OK, sceneEntries(get(exchange).engine(), query));
            case "scene-bake" -> sendJson(exchange, STATUS_OK, sceneBake(get(exchange).engine(), query));
            case "scene" -> sendBytes(exchange, "application/octet-stream", scene(get(exchange).engine(), query));
            case "scene-parts" -> sendBytes(exchange, "application/octet-stream", sceneParts(get(exchange).engine(), query));
            case "saved-worlds" -> sendJson(exchange, STATUS_OK, savedWorlds(exchange));
            case "saved-start" -> sendJson(exchange, STATUS_OK, savedStart(exchange, query));
            case "saved-tile" -> sendBytes(exchange, "application/octet-stream", savedTile(get(exchange).engine(), query));
            case "patterns" -> sendJson(exchange, STATUS_OK, StudioPatterns.of(get(exchange).engine()).views());
            case "pattern-entries" -> sendJson(exchange, STATUS_OK, patternEntries(get(exchange).engine(), query));
            case "pattern" -> sendBytes(exchange, "application/octet-stream", pattern(get(exchange).engine(), query));
            case "noise-sources" -> sendJson(exchange, STATUS_OK, NoiseSources.of(get(exchange).engine()).list());
            case "noise-tile" -> sendBytes(exchange, "application/octet-stream", noiseTile(get(exchange).engine(), query));
            case "events" -> streamEvents(exchange);
            case "teleport" -> teleport(exchange, query);
            case "open" -> openInEditor(exchange, query);
            default -> throw new RequestError(STATUS_NOT_FOUND, "No such endpoint: " + endpoint);
        }
    }

    private StudioWorld.Session get(HttpExchange exchange) {
        requireMethod(exchange, "GET");
        return runningSession();
    }

    private StudioWorld.Session post(HttpExchange exchange) {
        requireMethod(exchange, "POST");
        return runningSession();
    }

    private StudioWorld.Session runningSession() {
        StudioWorld.Session session = world.session();
        if (session == null) {
            throw new RequestError(STATUS_UNAVAILABLE, "The world " + world.name() + " has no running engine");
        }
        return session;
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equals(exchange.getRequestMethod())) {
            throw new RequestError(STATUS_METHOD_NOT_ALLOWED, "Use " + method);
        }
    }

    private WorldInfo worldInfo(StudioWorld.Session session) {
        Engine engine = session.engine();
        List<ViewInfo> views = Arrays.stream(MapView.values())
                .map(view -> new ViewInfo(view.id(), view.label(), view.description(), view.group().name(), view.group().label()))
                .toList();
        return new WorldInfo(
                world.name(),
                engine.getDimension().getLoadKey(),
                engine.getDimension().getName(),
                Long.toString(engine.getWorld().getRawWorldSeed()),
                engine.getMinHeight(),
                engine.getHeight(),
                engine.getDimension().getFluidHeight(),
                DEFAULT_TILE_SIZE,
                session.overlay() != null,
                views
        );
    }

    private static MapView view(Query query) {
        return MapView.byId(query.text("view"))
                .orElseThrow(() -> new RequestError(STATUS_BAD_REQUEST, "Unknown view: " + query.text("view")));
    }

    /** The view, and the region the region view previews when the query names one in {@code preview}. */
    private static MapLayer layer(Engine engine, Query query) {
        MapView view = view(query);
        if (!query.has("preview")) {
            return MapLayer.of(view);
        }
        if (view != MapView.REGIONS) {
            throw new RequestError(STATUS_BAD_REQUEST, "Only the region view previews a region");
        }
        IrisRegion region = engine.getComplex().preparedRegion(query.text("preview"));
        if (region == null) {
            throw new RequestError(STATUS_BAD_REQUEST, "This world has no region " + query.text("preview"));
        }
        return MapLayer.preview(region);
    }

    private static MapHover hover(Engine engine, Query query) {
        return MapHover.sample(engine, layer(engine, query), query.number("x"), query.number("z"));
    }

    private static MapPickInspection.Inspection picks(Engine engine, Query query) {
        MapLayer layer = layer(engine, query);
        try {
            MapPickInspection.Area area = new MapPickInspection.Area(
                    query.number("minX"), query.number("minZ"), query.number("maxX"), query.number("maxZ"));
            return MapPickInspection.measure(engine, layer, area);
        } catch (IllegalArgumentException badArea) {
            throw new RequestError(STATUS_BAD_REQUEST, badArea.getMessage());
        }
    }

    private static List<SceneViewInfo> sceneViews(Engine engine) {
        StudioEditors editors = StudioEditors.of(engine);
        StudioScenes scenes = StudioScenes.of(engine);
        return scenes.views().stream()
                .map(view -> new SceneViewInfo(view.id(), view.label(), view.description(), editors.edits(view.id()), editors.edits(view.id()) && editors.creates(view.id()), scenes.isBaked(view.id()), scenes.hasParts(view.id())))
                .toList();
    }

    private static StudioEditors.EditorState sceneFile(Engine engine, Query query) throws IOException {
        return editing(() -> StudioEditors.of(engine).read(query.text("view"), query.text("key")));
    }

    private StudioEditors.EditorState sceneEdit(HttpExchange exchange, Query query) throws IOException {
        Engine engine = post(exchange).engine();
        String body = readBody(exchange);
        return editing(() -> {
            GraphEditRequest request = GraphEditRequest.parse(body);
            return StudioEditors.of(engine).edit(query.text("view"), query.text("key"), request.revision(), request.edit());
        });
    }

    private StudioEditors.EditorState sceneWrite(HttpExchange exchange, Query query) throws IOException {
        Engine engine = post(exchange).engine();
        String body = readBody(exchange);
        return editing(() -> {
            GraphEditRequest.FileWrite write = GraphEditRequest.FileWrite.parse(body);
            return StudioEditors.of(engine).write(query.text("view"), query.text("key"), write.revision(), write.text());
        });
    }

    private MessageBody sceneCreate(HttpExchange exchange, Query query) throws IOException {
        Engine engine = post(exchange).engine();
        return editing(() -> {
            StudioEditors.of(engine).create(query.text("view"), query.text("key"));
            return new MessageBody("Created " + query.text("key"));
        });
    }

    private static UsesBody sceneUses(Engine engine, Query query) throws IOException {
        return editing(() -> new UsesBody(StudioEditors.of(engine).usesOf(query.text("view"), query.text("key"))));
    }

    private TrashedBody sceneDelete(HttpExchange exchange, Query query) throws IOException {
        Engine engine = post(exchange).engine();
        return editing(() -> new TrashedBody(StudioEditors.of(engine).delete(query.text("view"), query.text("key"))));
    }

    // A refused edit is the user's to fix, so it answers with why rather than as a failure of the bridge.
    private static <T> T editing(EditorTask<T> task) throws IOException {
        try {
            return task.run();
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        } catch (StudioEditors.StaleEditException stale) {
            throw new RequestError(STATUS_CONFLICT, stale.getMessage());
        } catch (StudioEditors.EditsLockedException locked) {
            throw new RequestError(STATUS_FORBIDDEN, locked.getMessage());
        } catch (IllegalArgumentException | JsonParseException refused) {
            throw new RequestError(STATUS_UNPROCESSABLE, refused.getMessage());
        }
    }

    @FunctionalInterface
    private interface EditorTask<T> {
        T run() throws IOException;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(LARGEST_BODY_BYTES + 1);
        if (body.length > LARGEST_BODY_BYTES) {
            throw new RequestError(STATUS_BAD_REQUEST, "The body is over " + LARGEST_BODY_BYTES + " bytes");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static List<SceneEntryWire> sceneEntries(Engine engine, Query query) {
        try {
            return StudioScenes.of(engine).entries(query.text("view")).stream().map(SceneEntryWire::of).toList();
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    // What a setting naming a platform entry (reference) or a pack resource (resource) may be typed as.
    private static List<String> suggestions(Engine engine, Query query) {
        try {
            return query.has("reference")
                    ? StudioSuggestions.ofReference(query.text("reference"))
                    : StudioSuggestions.ofResource(engine, query.text("resource"));
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    private static BakeRun sceneBake(Engine engine, Query query) {
        String key = query.text("key");
        try {
            return StudioScenes.of(engine).timeBake(query.text("view"), key)
                    .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No bake to time for " + key));
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    private byte[] scene(Engine engine, Query query) {
        String key = query.text("key");
        Optional<GeneratedObject> scene;
        try {
            scene = StudioScenes.of(engine).scene(query.text("view"), key, query.whole("variant", 0));
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
        return SceneEncoding.encode(scene.orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No scene " + key)), gson);
    }

    private static byte[] sceneParts(Engine engine, Query query) {
        String key = query.text("key");
        int variant = query.whole("variant", 0);
        try {
            StudioScenes scenes = StudioScenes.of(engine);
            GeneratedObject scene = scenes.scene(query.text("view"), key, variant)
                    .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No scene " + key));
            BlockParts parts = scenes.parts(query.text("view"), key, variant)
                    .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No parts for " + key));
            return SceneEncoding.encodeParts(scene, parts);
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    private static List<PatternEntryWire> patternEntries(Engine engine, Query query) {
        try {
            return StudioPatterns.of(engine).entries(query.text("view")).stream().map(PatternEntryWire::of).toList();
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    private byte[] pattern(Engine engine, Query query) {
        int columns = query.whole("size", DEFAULT_TILE_SIZE);
        int blocksPerColumn = query.whole("scale", 1);
        if (columns < 1 || columns > LARGEST_TILE_SIZE) {
            throw new RequestError(STATUS_BAD_REQUEST, "A pattern is 1 to " + LARGEST_TILE_SIZE + " columns across");
        }
        if (blocksPerColumn < 1 || blocksPerColumn > LARGEST_PATTERN_BLOCKS_PER_COLUMN) {
            throw new RequestError(STATUS_BAD_REQUEST, "The scale is 1 to " + LARGEST_PATTERN_BLOCKS_PER_COLUMN + " whole blocks per column");
        }
        String key = query.text("key");
        int tileBlocks = columns * blocksPerColumn;
        PatternArea area = new PatternArea(query.whole("x", 0) * tileBlocks, query.whole("z", 0) * tileBlocks, columns, blocksPerColumn);
        Optional<BlockPattern> pattern;
        try {
            pattern = StudioPatterns.of(engine).pattern(query.text("view"), key, query.whole("depth", 0), area);
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
        return PatternEncoding.encode(pattern.orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No pattern " + key)), blocksPerColumn, gson,
                StudioBridge::pluginLookOf);
    }

    private static Optional<CustomBlockLook> pluginLookOf(String state) {
        CustomBlockSource plugins = IrisServices.getOrNull(CustomBlockSource.class);
        return plugins == null ? Optional.empty() : plugins.lookOf(state);
    }

    private static List<SavedWorlds.Info> savedWorlds(HttpExchange exchange) {
        requireMethod(exchange, "GET");
        return SavedWorlds.shared().list();
    }

    private static SavedWorld.Start savedStart(HttpExchange exchange, Query query) {
        requireMethod(exchange, "GET");
        return savedWorld(query).start()
                .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "Nothing finished is saved in " + query.text("world")));
    }

    private byte[] savedTile(Engine engine, Query query) {
        int size = query.whole("size", DEFAULT_TILE_SIZE);
        int blocksPerPixel = query.whole("scale", 1);
        if (size < 1 || size > LARGEST_TILE_SIZE) {
            throw new RequestError(STATUS_BAD_REQUEST, "A tile is 1 to " + LARGEST_TILE_SIZE + " pixels");
        }
        if (blocksPerPixel < 1 || blocksPerPixel > LARGEST_SAVED_BLOCKS_PER_PIXEL) {
            throw new RequestError(STATUS_BAD_REQUEST, "The scale is 1 to " + LARGEST_SAVED_BLOCKS_PER_PIXEL + " whole blocks per pixel");
        }
        SavedTile tile = SavedTile.sample(savedWorld(query), query.whole("x", 0), query.whole("z", 0), blocksPerPixel, size);
        return SavedTileEncoding.encode(tile, SavedBiomeLabels.of(engine).labelsOf(tile.biomeNames()), gson);
    }

    private byte[] noiseTile(Engine engine, Query query) {
        int size = query.whole("size", DEFAULT_TILE_SIZE);
        double blocksPerPixel = query.number("scale");
        if (size < 1 || size > LARGEST_TILE_SIZE) {
            throw new RequestError(STATUS_BAD_REQUEST, "A tile is 1 to " + LARGEST_TILE_SIZE + " pixels");
        }
        if (!(blocksPerPixel >= SMALLEST_NOISE_BLOCKS_PER_PIXEL && blocksPerPixel <= LARGEST_BLOCKS_PER_PIXEL)) {
            throw new RequestError(STATUS_BAD_REQUEST, "The scale is " + SMALLEST_NOISE_BLOCKS_PER_PIXEL + " to " + LARGEST_BLOCKS_PER_PIXEL + " blocks per pixel");
        }
        NoiseSources sources = NoiseSources.of(engine);
        String source = query.text("source");
        NoiseProvider noise = noiseSampler(sources, source, query.longNumber("seed"));
        return NoiseTile.sample(noise, query.whole("x", 0), query.whole("z", 0), blocksPerPixel, size, sources.isSmooth(source),
                () -> closed || engine.isClosing() || engine.isClosed()).encode();
    }

    // A source that can't be sampled fails every tile alike, so it is told once to the studio rather than logged per tile.
    private static NoiseProvider noiseSampler(NoiseSources sources, String source, long seed) {
        try {
            return sources.sampler(source, seed);
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        } catch (IllegalStateException unsampleable) {
            throw new RequestError(STATUS_UNPROCESSABLE, unsampleable.getMessage());
        }
    }

    private static SavedWorld savedWorld(Query query) {
        try {
            return SavedWorlds.shared().open(query.text("world"));
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        }
    }

    // Asset paths name one file of one Minecraft version, so a studio may keep what it fetched for as long as it runs.
    private static void sendAsset(HttpExchange exchange, String endpoint) throws IOException {
        requireMethod(exchange, "GET");
        byte[] asset;
        try {
            asset = MinecraftAssets.shared().read(endpoint)
                    .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No asset " + endpoint));
        } catch (MinecraftAssets.NotReadyException notReady) {
            exchange.getResponseHeaders().set("Retry-After", ASSET_RETRY_SECONDS);
            throw new RequestError(STATUS_UNAVAILABLE, notReady.getMessage());
        }
        exchange.getResponseHeaders().set("Cache-Control", "max-age=" + ASSET_CACHE_SECONDS + ", immutable");
        sendBytes(exchange, endpoint.endsWith(".png") ? "image/png" : "application/json", asset);
    }

    private static List<MapLabel> regions(Engine engine) {
        return engine.getComplex().preparedRegions().stream().map(region -> MapLabel.of(engine, region)).toList();
    }

    private byte[] tile(StudioWorld.Session session, Query query) {
        Engine engine = session.engine();
        MapLayer layer = layer(engine, query);
        int size = query.whole("size", DEFAULT_TILE_SIZE);
        double blocksPerPixel = query.number("scale");
        if (size < 1 || size > LARGEST_TILE_SIZE) {
            throw new RequestError(STATUS_BAD_REQUEST, "A tile is 1 to " + LARGEST_TILE_SIZE + " pixels");
        }
        if (!(blocksPerPixel >= SMALLEST_BLOCKS_PER_PIXEL && blocksPerPixel <= LARGEST_BLOCKS_PER_PIXEL)) {
            throw new RequestError(STATUS_BAD_REQUEST, "The scale is " + SMALLEST_BLOCKS_PER_PIXEL + " to " + LARGEST_BLOCKS_PER_PIXEL + " blocks per pixel");
        }
        double tileBlocks = size * blocksPerPixel;
        double startX = query.whole("x", 0) * tileBlocks;
        double startZ = query.whole("z", 0) * tileBlocks;
        AtomicBoolean dropped = query.has("request") ? droppedFlagOf(query.text("request")) : new AtomicBoolean();
        BooleanSupplier cancelled = () -> dropped.get() || closed || engine.isClosing() || engine.isClosed();
        MapTile tile = session.renderer().render(layer, startX, startZ, blocksPerPixel, size, tileExtras(query), cancelled);
        if (query.has("blocks")) {
            tile = withBlocks(session, tile, startX, startZ, blockSource(query), cancelled);
        }
        return MapTileEncoding.encode(tile, gson, StudioBridge::pluginLookOf);
    }

    // A tile with blocks may generate thousands of chunks, so one the studio stopped waiting for is let go of.
    private void cancelTile(HttpExchange exchange, Query query) throws IOException {
        requireMethod(exchange, "POST");
        droppedFlagOf(query.text("request")).set(true);
        sendJson(exchange, STATUS_OK, new MessageBody(null));
    }

    private AtomicBoolean droppedFlagOf(String requestId) {
        return droppedTiles.get(requestId, id -> new AtomicBoolean());
    }

    private static MapTile withBlocks(StudioWorld.Session session, MapTile tile, double startX, double startZ, BlockSource source,
                                      BooleanSupplier cancelled) {
        try {
            return session.renderer().withBlocks(tile, startX, startZ, source, cancelled);
        } catch (IllegalArgumentException tooCoarse) {
            throw new RequestError(STATUS_BAD_REQUEST, tooCoarse.getMessage());
        }
    }

    private static BlockSource blockSource(Query query) {
        return BlockSource.byId(query.text("blocks"))
                .orElseThrow(() -> new RequestError(STATUS_BAD_REQUEST, "No block source " + query.text("blocks")));
    }

    private static Set<MapTile.Extra> tileExtras(Query query) {
        Set<MapTile.Extra> extras = EnumSet.noneOf(MapTile.Extra.class);
        if (query.flag("heights")) {
            extras.add(MapTile.Extra.HEIGHTS);
        }
        if (query.flag("noise")) {
            extras.add(MapTile.Extra.PICK_NOISE);
        }
        return extras;
    }

    private List<GuiMarker> entities(StudioWorld.Session session) {
        if (session.overlay() == null) {
            return List.of();
        }
        CompletableFuture<List<GuiMarker>> found = new CompletableFuture<>();
        session.overlay().requestEntities(found::complete);
        try {
            return found.get(ENTITY_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            throw new RequestError(STATUS_UNAVAILABLE, "The server did not list its entities in time");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Bridge closing");
        } catch (ExecutionException failed) {
            throw new IllegalStateException(failed.getCause());
        }
    }

    private void teleport(HttpExchange exchange, Query query) throws IOException {
        StudioWorld.Session session = post(exchange);
        requireOverlay(session);
        session.overlay().teleport(query.number("x"), query.number("z"));
        sendJson(exchange, STATUS_OK, new MessageBody(null));
    }

    private void openInEditor(HttpExchange exchange, Query query) throws IOException {
        StudioWorld.Session session = post(exchange);
        requireOverlay(session);
        MapView view = MapView.byId(query.text("view")).orElse(MapView.BIOMES);
        RenderType type = view == MapView.REGIONS ? RenderType.REGION : RenderType.BIOME;
        StudioEditor editor = requireLaunchableEditor();
        String opened = session.overlay().openInEditor(query.number("x"), query.number("z"), type);
        if (opened == null) {
            throw new RequestError(STATUS_UNAVAILABLE, "Could not open " + editor.displayName() + "; see the server console");
        }
        sendJson(exchange, STATUS_OK, new MessageBody("Opened " + opened + " in " + editor.displayName()));
    }

    private static StudioEditor requireLaunchableEditor() {
        StudioEditor editor = StudioEditorLauncher.configuredEditor();
        if (editor == StudioEditor.NONE) {
            throw new RequestError(STATUS_UNAVAILABLE, "No studio editor is set; set studio.editor in Iris settings");
        }
        if (!StudioEditorLauncher.canLaunch(editor)) {
            throw new RequestError(STATUS_UNAVAILABLE, "The server cannot open " + editor.displayName() + " (no desktop)");
        }
        return editor;
    }

    private static void requireOverlay(StudioWorld.Session session) {
        if (session.overlay() == null) {
            throw new RequestError(STATUS_NOT_FOUND, "This server cannot do that from the studio");
        }
    }

    private void streamEvents(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(STATUS_OK, 0);
        OutputStream body = exchange.getResponseBody();
        body.flush();
        events.listen(body);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + token;
        return header != null && MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static void allowAnyOrigin(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Authorization");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, gson.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        send(exchange, STATUS_OK, body);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static int workerCount() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    }

    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, name + " " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class RequestError extends RuntimeException {
        private final int status;

        private RequestError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** A request's query parameters; a missing or malformed one is a bad request. */
    private record Query(Map<String, String> values) {
        static Query of(URI uri) {
            Map<String, String> values = new HashMap<>();
            String raw = uri.getRawQuery();
            if (raw != null) {
                for (String pair : raw.split("&")) {
                    int equals = pair.indexOf('=');
                    String name = equals < 0 ? pair : pair.substring(0, equals);
                    String value = equals < 0 ? "" : pair.substring(equals + 1);
                    values.put(decode(name), decode(value));
                }
            }
            return new Query(values);
        }

        private static String decode(String text) {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        }

        String text(String name) {
            String value = values.get(name);
            if (value == null) {
                throw new RequestError(STATUS_BAD_REQUEST, "Missing " + name);
            }
            return value;
        }

        double number(String name) {
            try {
                double value = Double.parseDouble(text(name));
                if (!Double.isFinite(value)) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException malformed) {
                throw new RequestError(STATUS_BAD_REQUEST, name + " must be a number");
            }
        }

        int whole(String name, int fallback) {
            if (!values.containsKey(name)) {
                return fallback;
            }
            try {
                return Integer.parseInt(values.get(name));
            } catch (NumberFormatException malformed) {
                throw new RequestError(STATUS_BAD_REQUEST, name + " must be a whole number");
            }
        }

        long longNumber(String name) {
            try {
                return Long.parseLong(text(name));
            } catch (NumberFormatException malformed) {
                throw new RequestError(STATUS_BAD_REQUEST, name + " must be a whole number");
            }
        }

        boolean has(String name) {
            return values.containsKey(name);
        }

        boolean flag(String name) {
            String value = values.get(name);
            return "1".equals(value) || "true".equals(value);
        }
    }

    private record ErrorBody(String error) {
    }

    /** A scene view as the studio lists it; {@code editable} when its entries can be edited as nodes, {@code creatable} when they can also be created and deleted, {@code baked} when their bake can be timed, {@code parts} when the blocks of a scene can be told by the part that placed them. */
    private record SceneViewInfo(String id, String label, String description, boolean editable, boolean creatable, boolean baked, boolean parts) {
    }

    private record UsesBody(List<String> files) {
    }

    private record TrashedBody(String to) {
    }

    /** A {@link SceneEntry} as the studio reads it, with a blank group for none. */
    private record SceneEntryWire(String key, String name, String group, int variants) {
        static SceneEntryWire of(SceneEntry entry) {
            return new SceneEntryWire(entry.key(), entry.name(), entry.group().orElse(""), entry.variants());
        }
    }

    /** A {@link PatternEntry} as the studio reads it, with a blank group for none. */
    private record PatternEntryWire(String key, String name, String group, int depths, List<String> facts) {
        static PatternEntryWire of(PatternEntry entry) {
            return new PatternEntryWire(entry.key(), entry.name(), entry.group().orElse(""), entry.depths(), entry.facts());
        }
    }

    private record MessageBody(String message) {
    }

    /** @param seed as text, as JavaScript numbers cannot hold every long */
    private record WorldInfo(String world, String dimension, String dimensionName, String seed, int minY, int height,
                             int fluidHeight, int tileSize, boolean hasPlayers, List<ViewInfo> views) {
    }

    private record ViewInfo(String id, String label, String description, String group, String groupLabel) {
    }
}
