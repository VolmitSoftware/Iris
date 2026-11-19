/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.map.StudioAssets;
import art.arcane.iris.studio.map.StudioHover;
import art.arcane.iris.studio.imagemap.ImageMapPreviews;
import art.arcane.iris.studio.map.PalettePatterns;
import art.arcane.iris.studio.noise.NoiseSources;
import art.arcane.iris.studio.noise.NoiseTile;
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.studio.view.GuiMarker;
import art.arcane.iris.studio.view.PregenRenderSnapshot;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.volmlib.util.function.NoiseProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
 * An Iris Studio app's connection to one world: HTTP on the loopback interface, on a free port, each request carrying
 * the bridge's token as {@code Authorization: Bearer <token>}. JSON for the world, hovers and players; tiles in
 * {@link StudioTile}; {@code /v1/events} streams {@link StudioEvents}. Requests run on a small pool of daemon threads,
 * so a busy studio cannot hold up the server.
 */
public final class StudioBridge implements AutoCloseable {
    private static final String API = "/v1/";
    private static final int TOKEN_BYTES = 24;
    private static final int DEFAULT_TILE_SIZE = 128;
    private static final int LARGEST_TILE_SIZE = 512;
    private static final double SMALLEST_BLOCKS_PER_PIXEL = 1D / 16D;
    private static final double LARGEST_BLOCKS_PER_PIXEL = 4_096D;
    private static final double SMALLEST_NOISE_BLOCKS_PER_PIXEL = 1D / 64D;
    private static final int LARGEST_PATTERN_BLOCKS_PER_COLUMN = 64;
    private static final int DEFAULT_IMAGE_MAP_PREVIEW_PIXELS = 768;
    private static final ModuleViewInfo PALETTES_VIEW = new ModuleViewInfo("iris:terrain/palettes", "Palettes",
            "The blocks each biome's palette layers place");
    private static final long DROPPED_TILE_KEPT_MINUTES = 10;
    private static final long ENTITY_WAIT_MILLIS = 3_000L;
    private static final String ASSETS_ENDPOINT = "assets/";
    private static final String ASSET_RETRY_SECONDS = "2";
    private static final long ASSET_CACHE_SECONDS = 86_400L;
    private static final String OVERRIDE_PREFIX = "set.";
    private static final String VIEW_BIOMES = "biomes";
    private static final String VIEW_REGIONS = "regions";
    private static final String VIEW_RIVERS = "rivers";
    static final int STATUS_OK = 200;
    static final int STATUS_NO_CONTENT = 204;
    static final int STATUS_BAD_REQUEST = 400;
    static final int STATUS_UNAUTHORIZED = 401;
    static final int STATUS_NOT_FOUND = 404;
    static final int STATUS_METHOD_NOT_ALLOWED = 405;
    static final int STATUS_UNPROCESSABLE = 422;
    static final int STATUS_SERVER_ERROR = 500;
    static final int STATUS_UNAVAILABLE = 503;

    private static final String VIEW_HEIGHT = "height";
    private static final String GROUP_WORLD = "WORLD";
    private static final String GROUP_BIOME_LAYERS = "BIOME_LAYERS";
    private static final String GROUP_DIAGNOSTICS = "DIAGNOSTICS";

    private static final List<ViewInfo> VIEWS = List.of(
            new ViewInfo(VIEW_BIOMES, "Biomes", "The biome at each spot, in the biome's colour", GROUP_WORLD, "World"),
            new ViewInfo(VIEW_REGIONS, "Regions", "The region each spot belongs to", GROUP_WORLD, "World"),
            new ViewInfo(VIEW_HEIGHT, "Height", "Terrain height, from deep water to snowy peaks", GROUP_WORLD, "World"),
            new ViewInfo(VIEW_RIVERS, "Rivers", "Streams, rivers, falls, ponds, lakes, sea inlets, grottos and cave pools from the hydrology",
                    GROUP_WORLD, "World"),
            new ViewInfo("land-biomes", "Land biomes", "The land biome each spot would get, also where the sea is", GROUP_BIOME_LAYERS, "Biome layers"),
            new ViewInfo("sea-biomes", "Sea biomes", "The sea biome each spot would get, also where the land is", GROUP_BIOME_LAYERS, "Biome layers"),
            new ViewInfo("cave-biomes", "Cave biomes", "The cave biome under each spot", GROUP_BIOME_LAYERS, "Biome layers"),
            new ViewInfo("object-load", "Object load", "Configuration estimate of object density and chance, not measured performance",
                    GROUP_DIAGNOSTICS, "Diagnostics"),
            new ViewInfo("decorator-load", "Decorator load", "Configuration estimate of decorator chance and stacks, not measured performance",
                    GROUP_DIAGNOSTICS, "Diagnostics"),
            new ViewInfo("layer-load", "Layer load", "Configuration estimate of biome layer count, not measured performance",
                    GROUP_DIAGNOSTICS, "Diagnostics"),
            new ViewInfo("noise", "Noise", "The pack's generators and the built-in noise styles, from above or as terrain",
                    "GENERATORS", "Generators"),
            new ViewInfo("pregen", "Pregeneration", "The chunks of the running pregeneration as they are generated, with its progress", GROUP_WORLD, "World"),
            new ViewInfo("imageMaps","Image maps", "Terrain height, biomes or masks painted from a PNG, previewed over the world area they cover",
                    "GENERATORS", "Generators"));

    private final StudioWorld world;
    private final String token;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final HttpServer server;
    private final int port;
    private final ExecutorService workers;
    private final ScheduledExecutorService ticker;
    private final StudioEvents events;
    private final Runnable hotloadHook;
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
            case "picks" -> sendJson(exchange, STATUS_OK, new PickInspection("", List.of()));
            case "regions" -> sendJson(exchange, STATUS_OK, StudioHover.previewableRegions(get(exchange).engine()));
            case "entities" -> sendJson(exchange, STATUS_OK, entities(get(exchange)));
            case "scenes" -> sendJson(exchange, STATUS_OK, profileViews());
            case "scene-entries" -> sendJson(exchange, STATUS_OK, profileEntries(get(exchange).engine(), query));
            case "scene" -> sendBytes(exchange, "application/octet-stream", profileScene(get(exchange).engine(), query));
            case "scene-bake" -> sendJson(exchange, STATUS_OK, sceneBake(get(exchange).engine(), query));
            case "profile-form" -> sendJson(exchange, STATUS_OK, profileForm(get(exchange).engine(), query));
            case "noise-sources" -> sendJson(exchange, STATUS_OK, NoiseSources.of(get(exchange).engine()).list());
            case "noise-tile" -> sendBytes(exchange, "application/octet-stream", noiseTile(get(exchange).engine(), query));
            case "patterns" -> sendJson(exchange, STATUS_OK, List.of(PALETTES_VIEW));
            case "pattern-entries" -> sendJson(exchange, STATUS_OK, patternEntries(get(exchange).engine(), query));
            case "pattern" -> sendBytes(exchange, "application/octet-stream", pattern(get(exchange).engine(), query));
            case "pregen" -> sendJson(exchange, STATUS_OK, pregenStatus(exchange));
            case "pregen-map" -> sendBytes(exchange, "image/png", pregenMap(exchange));
            case "pregen-pause" -> sendJson(exchange, STATUS_OK, pregenPause(exchange));
            case "image-maps" -> sendJson(exchange, STATUS_OK, ImageMapPreviews.entries(get(exchange).engine()));
            case "image-map" -> sendJson(exchange, STATUS_OK, profiling(() -> ImageMapPreviews.details(get(exchange).engine(), query.text("key"))));
            case "image-map-preview" -> sendBytes(exchange, "image/png", imageMapPreview(get(exchange).engine(), query));
            case "saved-worlds" -> sendJson(exchange, STATUS_OK, List.of());
            case "events" -> streamEvents(exchange);
            case "teleport" -> teleport(exchange, query);
            case "open" -> throw new RequestError(STATUS_NOT_FOUND, "Opening a pack file from the studio is not available in Iris v4");
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
                session.tiles().hydrologyEnabled(),
                VIEWS
        );
    }

    private static String viewOf(Query query) {
        String view = query.text("view");
        if (VIEWS.stream().noneMatch(known -> known.id().equals(view))) {
            throw new RequestError(STATUS_BAD_REQUEST, "Unknown view: " + view);
        }
        return view;
    }

    private static String previewOf(Query query) {
        if (!query.has("preview")) {
            return null;
        }
        if (!VIEW_REGIONS.equals(viewOf(query))) {
            throw new RequestError(STATUS_BAD_REQUEST, "Only the region view previews a region");
        }
        return query.text("preview");
    }

    private static StudioHover.Hover hover(Engine engine, Query query) {
        String view = viewOf(query);
        String preview = previewOf(query);
        if (preview != null && engine.getData().getRegionLoader().load(preview) == null) {
            throw new RequestError(STATUS_BAD_REQUEST, "This world has no region " + preview);
        }
        return StudioHover.sample(engine, query.number("x"), query.number("z"), preview, !VIEW_REGIONS.equals(view) || preview != null);
    }

    private byte[] tile(StudioWorld.Session session, Query query) {
        Engine engine = session.engine();
        String view = viewOf(query);
        String preview = previewOf(query);
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
        StudioTile tile = VIEW_RIVERS.equals(view)
                ? session.tiles().rivers(startX, startZ, blocksPerPixel, size, cancelled)
                : session.tiles().biomesOrRegions(view, preview, startX, startZ, blocksPerPixel, size, query.flag("heights"), cancelled);
        return tile.encode(gson);
    }

    private static PregenStatus pregenStatus(HttpExchange exchange) {
        requireMethod(exchange, "GET");
        PregeneratorJob job = PregeneratorJob.getInstance();
        if (job == null) {
            return new PregenStatus(false, null);
        }
        return new PregenStatus(true, job.renderSnapshot());
    }

    private static byte[] pregenMap(HttpExchange exchange) {
        requireMethod(exchange, "GET");
        PregeneratorJob job = PregeneratorJob.getInstance();
        if (job == null) {
            throw new RequestError(STATUS_NOT_FOUND, "No pregeneration is running");
        }
        return job.renderMapState().png();
    }

    private static MessageBody pregenPause(HttpExchange exchange) {
        requireMethod(exchange, "POST");
        if (!PregeneratorJob.pauseResume()) {
            throw new RequestError(STATUS_NOT_FOUND, "No pregeneration to pause or resume");
        }
        return new MessageBody(PregeneratorJob.isPaused() ? "Paused" : "Running");
    }

    private static byte[] imageMapPreview(Engine engine, Query query) {
        try {
            return profiling(() -> ImageMapPreviews.preview(engine, query.text("key"), query.whole("size", DEFAULT_IMAGE_MAP_PREVIEW_PIXELS)));
        } catch (IllegalArgumentException refused) {
            throw new RequestError(STATUS_BAD_REQUEST, refused.getMessage());
        }
    }

    private static List<PalettePatterns.Entry> patternEntries(Engine engine, Query query) {
        requirePalettesView(query);
        return PalettePatterns.entries(engine);
    }

    private byte[] pattern(Engine engine, Query query) {
        requirePalettesView(query);
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
        PalettePatterns.Pattern pattern = PalettePatterns.pattern(engine, key, query.whole("depth", 0),
                        query.whole("x", 0) * tileBlocks, query.whole("z", 0) * tileBlocks, columns, blocksPerColumn)
                .orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No palette " + key));
        return PatternEncoding.encode(pattern, blocksPerColumn, gson);
    }

    private static void requirePalettesView(Query query) {
        if (!PALETTES_VIEW.id().equals(query.text("view"))) {
            throw new RequestError(STATUS_NOT_FOUND, "No pattern view " + query.text("view"));
        }
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

    // A source that cannot be sampled fails every tile alike, so it is told once to the studio rather than logged per tile.
    private static NoiseProvider noiseSampler(NoiseSources sources, String source, long seed) {
        try {
            return sources.sampler(source, seed);
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        } catch (IllegalStateException unsampleable) {
            throw new RequestError(STATUS_UNPROCESSABLE, unsampleable.getMessage());
        }
    }

    // Asset paths name one file of one Minecraft version, so a studio may keep what it fetched for as long as it runs.
    private static void sendAsset(HttpExchange exchange, String endpoint) throws IOException {
        requireMethod(exchange, "GET");
        byte[] asset;
        try {
            asset = StudioAssets.read(endpoint).orElseThrow(() -> new RequestError(STATUS_NOT_FOUND, "No asset " + endpoint));
        } catch (StudioAssets.NotReadyException notReady) {
            exchange.getResponseHeaders().set("Retry-After", ASSET_RETRY_SECONDS);
            throw new RequestError(STATUS_UNAVAILABLE, notReady.getMessage());
        }
        exchange.getResponseHeaders().set("Cache-Control", "max-age=" + ASSET_CACHE_SECONDS + ", immutable");
        sendBytes(exchange, endpoint.endsWith(".png") ? "image/png" : "application/json", asset);
    }

    private static List<SceneViewInfo> profileViews() {
        return StudioProfiles.views().stream()
                .map(view -> new SceneViewInfo(view.id(), view.label(), view.description(), false, false, view.isBaked(), false, view.sizes(), view.hasForm()))
                .toList();
    }

    private static List<StudioProfiles.Entry> profileEntries(Engine engine, Query query) {
        return profileView(query).entries(engine);
    }

    private static StudioObjects.BakeTiming sceneBake(Engine engine, Query query) {
        StudioProfiles.ProfileView view = profileView(query);
        if (!view.isBaked()) {
            throw new RequestError(STATUS_BAD_REQUEST, "View " + view.id() + " is not baked");
        }
        return profiling(() -> view.timeBake(engine, query.text("key")));
    }

    private static ProfileForm.Form profileForm(Engine engine, Query query) {
        return profiling(() -> profileView(query).form(engine, query.text("key"), query.prefixed(OVERRIDE_PREFIX)));
    }

    private byte[] profileScene(Engine engine, Query query) {
        StudioProfiles.ProfileView view = profileView(query);
        int size = query.whole("size", view.sizes() == null ? 0 : view.sizes().opening());
        if (view.sizes() != null && !view.sizes().blocksAcross().contains(size)) {
            throw new RequestError(STATUS_BAD_REQUEST, "View " + view.id() + " builds scenes " + view.sizes().blocksAcross() + " blocks across, not " + size);
        }
        return profiling(() -> view.scene(engine, query.text("key"), query.whole("variant", 0), size, query.prefixed(OVERRIDE_PREFIX)).encode(gson));
    }

    private static StudioProfiles.ProfileView profileView(Query query) {
        return profiling(() -> StudioProfiles.view(query.text("view")));
    }

    // A profile the pack does not have is the studio's to be told about, not a failure of the bridge.
    private static <T> T profiling(ProfileTask<T> task) {
        try {
            return task.run();
        } catch (NoSuchElementException unknown) {
            throw new RequestError(STATUS_NOT_FOUND, unknown.getMessage());
        } catch (IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @FunctionalInterface
    private interface ProfileTask<T> {
        T run() throws IOException;
    }

    private void cancelTile(HttpExchange exchange, Query query) throws IOException {
        requireMethod(exchange, "POST");
        droppedFlagOf(query.text("request")).set(true);
        sendJson(exchange, STATUS_OK, new MessageBody(null));
    }

    private AtomicBoolean droppedFlagOf(String requestId) {
        return droppedTiles.get(requestId, id -> new AtomicBoolean());
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
        if (session.overlay() == null) {
            throw new RequestError(STATUS_NOT_FOUND, "This server cannot do that from the studio");
        }
        session.overlay().teleport(query.number("x"), query.number("z"));
        sendJson(exchange, STATUS_OK, new MessageBody(null));
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

    static final class RequestError extends RuntimeException {
        final int status;

        RequestError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** A request's query parameters; a missing or malformed one is a bad request. */
    record Query(Map<String, String> values) {
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

        /** The parameters that start with {@code prefix}, by the rest of their names. */
        Map<String, String> prefixed(String prefix) {
            Map<String, String> found = new HashMap<>();
            values.forEach((name, value) -> {
                if (name.startsWith(prefix)) {
                    found.put(name.substring(prefix.length()), value);
                }
            });
            return found;
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

    private record MessageBody(String message) {
    }

    /** A profile view as the studio lists its scene views; {@code form} when its entries have a settings form. */
    private record SceneViewInfo(String id, String label, String description, boolean editable, boolean creatable, boolean baked,
                                 boolean parts, StudioProfiles.Sizes sizes, boolean form) {
    }

    /** @param snapshot null while no pregeneration runs */
    private record PregenStatus(boolean active, PregenRenderSnapshot snapshot) {
    }

    /** A view the studio lists beside the scene and pattern views. */
    private record ModuleViewInfo(String id, String label, String description) {
    }

    private record PickInspection(String detail, List<Object> sections) {
    }

    /** @param seed as text, as JavaScript numbers cannot hold every long */
    private record WorldInfo(String world, String dimension, String dimensionName, String seed, int minY, int height,
                             int fluidHeight, int tileSize, boolean hasPlayers, boolean hydrology, List<ViewInfo> views) {
    }

    private record ViewInfo(String id, String label, String description, String group, String groupLabel) {
    }
}
