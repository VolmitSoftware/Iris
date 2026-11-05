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
import art.arcane.iris.core.gui.GuiMarker;
import art.arcane.iris.core.studio.map.MapHover;
import art.arcane.iris.core.studio.map.MapTile;
import art.arcane.iris.core.studio.map.MapView;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A studio app's connection to one world: HTTP on the loopback interface, on a free port, each request carrying the
 * bridge's token as {@code Authorization: Bearer <token>}. JSON for the world, hovers and players; tiles in
 * {@link MapTileEncoding}; {@code /v1/events} streams {@link StudioEvents}. Requests run on a small pool of daemon
 * threads, so a busy studio cannot hold up the server.
 */
public final class StudioBridge implements AutoCloseable {
    private static final String API = "/v1/";
    private static final int TOKEN_BYTES = 24;
    private static final int DEFAULT_TILE_SIZE = 128;
    private static final int LARGEST_TILE_SIZE = 512;
    private static final double SMALLEST_BLOCKS_PER_PIXEL = 1D / 16D;
    private static final double LARGEST_BLOCKS_PER_PIXEL = 4_096D;
    private static final long ENTITY_WAIT_MILLIS = 3_000L;
    private static final int STATUS_OK = 200;
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final int STATUS_SERVER_ERROR = 500;
    private static final int STATUS_UNAVAILABLE = 503;

    private final StudioWorld world;
    private final String token;
    private final Gson gson = new Gson();
    private final HttpServer server;
    private final int port;
    private final ExecutorService workers;
    private final ScheduledExecutorService ticker;
    private final StudioEvents events;
    private final Runnable hotloadHook;
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
        switch (endpoint) {
            case "world" -> sendJson(exchange, STATUS_OK, worldInfo(get(exchange)));
            case "tile" -> sendBytes(exchange, "application/octet-stream", tile(get(exchange), query));
            case "hover" -> sendJson(exchange, STATUS_OK, MapHover.sample(get(exchange).engine(), query.number("x"), query.number("z")));
            case "entities" -> sendJson(exchange, STATUS_OK, entities(get(exchange)));
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

    private byte[] tile(StudioWorld.Session session, Query query) {
        MapView view = MapView.byId(query.text("view"))
                .orElseThrow(() -> new RequestError(STATUS_BAD_REQUEST, "Unknown view: " + query.text("view")));
        int size = query.whole("size", DEFAULT_TILE_SIZE);
        double blocksPerPixel = query.number("scale");
        if (size < 1 || size > LARGEST_TILE_SIZE) {
            throw new RequestError(STATUS_BAD_REQUEST, "A tile is 1 to " + LARGEST_TILE_SIZE + " pixels");
        }
        if (!(blocksPerPixel >= SMALLEST_BLOCKS_PER_PIXEL && blocksPerPixel <= LARGEST_BLOCKS_PER_PIXEL)) {
            throw new RequestError(STATUS_BAD_REQUEST, "The scale is " + SMALLEST_BLOCKS_PER_PIXEL + " to " + LARGEST_BLOCKS_PER_PIXEL + " blocks per pixel");
        }
        double tileBlocks = size * blocksPerPixel;
        Engine engine = session.engine();
        MapTile tile = session.renderer().render(view, query.whole("x", 0) * tileBlocks, query.whole("z", 0) * tileBlocks,
                blocksPerPixel, size, query.flag("heights"), () -> closed || engine.isClosing() || engine.isClosed());
        return MapTileEncoding.encode(tile, gson);
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
        sendJson(exchange, STATUS_OK, new MessageBody(session.overlay().openInEditor(query.number("x"), query.number("z"), type)));
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

        boolean flag(String name) {
            String value = values.get(name);
            return "1".equals(value) || "true".equals(value);
        }
    }

    private record ErrorBody(String error) {
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
