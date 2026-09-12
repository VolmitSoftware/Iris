/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.ProtocolException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class IrisProtocolServer {
    private final IrisSessionRegistry registry;
    private final long serverCapabilities;
    private final String serverBrand;
    private final boolean irisActive;
    private final LongSupplier clock;
    private final AtomicLong droppedBeforeHello;
    private final AtomicLong rateLimitedFrames;
    private final AtomicLong rejectedFrames;
    private final AtomicLong versionMismatches;
    private final AtomicLong capabilityRejected;
    private final AtomicLong noEngineDrops;
    private final AtomicLong cursorInfoServed;
    private final AtomicLong cursorRateLimited;
    private final AtomicLong cursorOutOfBounds;
    private final AtomicLong visionTileForwarded;
    private final AtomicLong visionRateLimited;
    private final AtomicLong visionOutOfBounds;
    private final AtomicLong pregenRegionDeltasBroadcast;
    private final AtomicLong studioHotloadsBroadcast;
    private final AtomicLong toastsBroadcast;
    private final AtomicLong toastsSent;
    private volatile EngineResolver engineResolver;
    private volatile CursorInfoRequestHandler cursorInfoHandler;
    private volatile VisionTileRequestHandler visionTileHandler;

    public IrisProtocolServer(IrisSessionRegistry registry, long serverCapabilities, String serverBrand, boolean irisActive) {
        this(registry, serverCapabilities, serverBrand, irisActive, System::currentTimeMillis);
    }

    IrisProtocolServer(IrisSessionRegistry registry, long serverCapabilities, String serverBrand, boolean irisActive, LongSupplier clock) {
        this.registry = Objects.requireNonNull(registry, "session registry");
        this.serverCapabilities = serverCapabilities;
        this.serverBrand = Objects.requireNonNull(serverBrand, "server brand");
        this.irisActive = irisActive;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.droppedBeforeHello = new AtomicLong(0L);
        this.rateLimitedFrames = new AtomicLong(0L);
        this.rejectedFrames = new AtomicLong(0L);
        this.versionMismatches = new AtomicLong(0L);
        this.capabilityRejected = new AtomicLong(0L);
        this.noEngineDrops = new AtomicLong(0L);
        this.cursorInfoServed = new AtomicLong(0L);
        this.cursorRateLimited = new AtomicLong(0L);
        this.cursorOutOfBounds = new AtomicLong(0L);
        this.visionTileForwarded = new AtomicLong(0L);
        this.visionRateLimited = new AtomicLong(0L);
        this.visionOutOfBounds = new AtomicLong(0L);
        this.pregenRegionDeltasBroadcast = new AtomicLong(0L);
        this.studioHotloadsBroadcast = new AtomicLong(0L);
        this.toastsBroadcast = new AtomicLong(0L);
        this.toastsSent = new AtomicLong(0L);
    }

    public IrisSessionRegistry registry() {
        return registry;
    }

    public void setEngineResolver(EngineResolver engineResolver) {
        this.engineResolver = engineResolver;
    }

    public void setCursorInfoHandler(CursorInfoRequestHandler cursorInfoHandler) {
        this.cursorInfoHandler = cursorInfoHandler;
    }

    public void setVisionTileHandler(VisionTileRequestHandler visionTileHandler) {
        this.visionTileHandler = visionTileHandler;
    }

    public void onClientFrame(String sessionId, byte[] frame) {
        IrisSession session = registry.get(sessionId);
        if (session == null) {
            return;
        }
        if (!session.allowInbound(clock.getAsLong())) {
            rateLimitedFrames.incrementAndGet();
            return;
        }
        IrisMessage message;
        try {
            message = IrisMessageCodec.decode(frame);
        } catch (ProtocolException rejected) {
            rejectedFrames.incrementAndGet();
            return;
        }
        if (message == null) {
            return;
        }
        dispatch(session, message);
    }

    public void broadcastPregenProgress(long jobId, long chunksDone, long chunksTotal, double chunksPerSecond, long etaMillis, int state) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_PREGEN)) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.PregenProgress(jobId, chunksDone, chunksTotal, chunksPerSecond, etaMillis, state));
            }
            session.sendRaw(frame);
        }
    }

    public void pregenEnd(long jobId, boolean completed) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_PREGEN)) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.PregenEnd(jobId, completed));
            }
            session.sendRaw(frame);
        }
    }

    public void broadcastDimensionStatus(String dimensionKey, String packKey, long seed, int minY, int maxY, boolean irisWorld) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady()) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.DimensionStatus(dimensionKey, packKey, seed, minY, maxY, irisWorld));
            }
            session.sendRaw(frame);
        }
    }

    public void sendDimensionStatus(String sessionId, String dimensionKey, String packKey, long seed, int minY, int maxY, boolean irisWorld) {
        IrisSession session = registry.get(sessionId);
        if (session == null || !session.isReady()) {
            return;
        }
        session.send(new IrisMessage.DimensionStatus(dimensionKey, packKey, seed, minY, maxY, irisWorld));
    }

    public void broadcastPregenRegionDelta(long jobId, int regionX, int regionZ, int state) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_PREGEN)) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.PregenRegionDelta(jobId, regionX, regionZ, state));
            }
            session.sendRaw(frame);
        }
        if (frame != null) {
            pregenRegionDeltasBroadcast.incrementAndGet();
        }
    }

    public void broadcastStudioHotload(String packKey, int changedFiles, boolean failed, String message) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_STUDIO)) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.StudioHotload(packKey, changedFiles, failed, message));
            }
            session.sendRaw(frame);
        }
        if (frame != null) {
            studioHotloadsBroadcast.incrementAndGet();
        }
    }

    public void broadcastToast(int kind, String title, String body) {
        if (registry.isEmpty()) {
            return;
        }
        byte[] frame = null;
        for (IrisSession session : registry.all()) {
            if (!session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_STUDIO)) {
                continue;
            }
            if (frame == null) {
                frame = IrisMessageCodec.encode(new IrisMessage.Toast(kind, title, body));
            }
            session.sendRaw(frame);
        }
        if (frame != null) {
            toastsBroadcast.incrementAndGet();
        }
    }

    public void sendToast(String sessionId, int kind, String title, String body) {
        IrisSession session = registry.get(sessionId);
        if (session == null || !session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_STUDIO)) {
            return;
        }
        session.send(new IrisMessage.Toast(kind, title, body));
        toastsSent.incrementAndGet();
    }

    public long droppedBeforeHelloCount() {
        return droppedBeforeHello.get();
    }

    public long rateLimitedFrameCount() {
        return rateLimitedFrames.get();
    }

    public long rejectedFrameCount() {
        return rejectedFrames.get();
    }

    public long versionMismatchCount() {
        return versionMismatches.get();
    }

    public long capabilityRejectedCount() {
        return capabilityRejected.get();
    }

    public long noEngineDropCount() {
        return noEngineDrops.get();
    }

    public long cursorInfoServedCount() {
        return cursorInfoServed.get();
    }

    public long cursorRateLimitedCount() {
        return cursorRateLimited.get();
    }

    public long cursorOutOfBoundsCount() {
        return cursorOutOfBounds.get();
    }

    public long visionTileForwardedCount() {
        return visionTileForwarded.get();
    }

    public long visionRateLimitedCount() {
        return visionRateLimited.get();
    }

    public long visionOutOfBoundsCount() {
        return visionOutOfBounds.get();
    }

    public long pregenRegionDeltasBroadcastCount() {
        return pregenRegionDeltasBroadcast.get();
    }

    public long studioHotloadsBroadcastCount() {
        return studioHotloadsBroadcast.get();
    }

    public long toastsBroadcastCount() {
        return toastsBroadcast.get();
    }

    public long toastsSentCount() {
        return toastsSent.get();
    }

    private void dispatch(IrisSession session, IrisMessage message) {
        if (message instanceof IrisMessage.ClientHello clientHello) {
            onClientHello(session, clientHello);
            return;
        }
        if (session.state() == IrisSession.State.AWAITING_HELLO) {
            droppedBeforeHello.incrementAndGet();
            return;
        }
        switch (message) {
            case IrisMessage.CursorInfoRequest cursorInfoRequest -> onCursorInfoRequest(session, cursorInfoRequest);
            case IrisMessage.VisionTileRequest visionTileRequest -> onVisionTileRequest(session, visionTileRequest);
            default -> {
            }
        }
    }

    private void onClientHello(IrisSession session, IrisMessage.ClientHello clientHello) {
        if (clientHello.protocolVersion() != IrisProtocol.PROTOCOL_VERSION) {
            versionMismatches.incrementAndGet();
            // Answer anyway with our version so the client lands in INCOMPATIBLE instead of retrying until it
            // gives up and reports "server does not run Iris". The session stays AWAITING_HELLO and every
            // later frame from it is dropped by dispatch.
            session.send(new IrisMessage.ServerHello(IrisProtocol.PROTOCOL_VERSION, serverCapabilities, serverBrand, irisActive));
            return;
        }
        session.markReady(clientHello.protocolVersion(), clientHello.capabilities());
        session.send(new IrisMessage.ServerHello(IrisProtocol.PROTOCOL_VERSION, serverCapabilities, serverBrand, irisActive));
    }

    private void onCursorInfoRequest(IrisSession session, IrisMessage.CursorInfoRequest request) {
        if (!session.hasCapability(IrisProtocol.CAPABILITY_CURSOR)) {
            capabilityRejected.incrementAndGet();
            return;
        }
        if (outOfWorldBounds(request.blockX()) || outOfWorldBounds(request.blockZ())) {
            cursorOutOfBounds.incrementAndGet();
            return;
        }
        if (!session.allowCursorInfo(clock.getAsLong())) {
            cursorRateLimited.incrementAndGet();
            return;
        }
        CursorInfoRequestHandler handler = cursorInfoHandler;
        if (handler != null) {
            handler.handle(session.id(), request.blockX(), request.blockZ());
            cursorInfoServed.incrementAndGet();
            return;
        }
        EngineResolver resolver = engineResolver;
        if (resolver == null) {
            noEngineDrops.incrementAndGet();
            return;
        }
        Engine engine = resolver.resolve(session.id());
        if (engine == null) {
            noEngineDrops.incrementAndGet();
            return;
        }
        session.send(IrisCursorResolver.resolve(engine, request.blockX(), request.blockZ()));
        cursorInfoServed.incrementAndGet();
    }

    private void onVisionTileRequest(IrisSession session, IrisMessage.VisionTileRequest request) {
        if (!session.hasCapability(IrisProtocol.CAPABILITY_VISION)) {
            capabilityRejected.incrementAndGet();
            return;
        }
        if (visionTileOutOfWorldBounds(request)) {
            visionOutOfBounds.incrementAndGet();
            return;
        }
        if (!session.allowVisionTile(clock.getAsLong())) {
            visionRateLimited.incrementAndGet();
            return;
        }
        VisionTileRequestHandler handler = visionTileHandler;
        if (handler == null) {
            noEngineDrops.incrementAndGet();
            return;
        }
        handler.handle(session.id(), request.tileX(), request.tileZ(), request.zoomLevel());
        visionTileForwarded.incrementAndGet();
    }

    private static boolean outOfWorldBounds(int coordinate) {
        return coordinate > IrisProtocol.MAX_QUERY_BLOCK_COORDINATE || coordinate < -IrisProtocol.MAX_QUERY_BLOCK_COORDINATE;
    }

    private static boolean visionTileOutOfWorldBounds(IrisMessage.VisionTileRequest request) {
        int zoom = Math.max(0, Math.min(request.zoomLevel(), IrisTileEncoder.MAX_ZOOM_LEVEL));
        long tileSize = (long) IrisTileEncoder.TILE_PIXELS << zoom;
        long minBlockX = (long) request.tileX() * tileSize;
        long minBlockZ = (long) request.tileZ() * tileSize;
        long maxBlockX = minBlockX + tileSize - 1L;
        long maxBlockZ = minBlockZ + tileSize - 1L;
        long limit = IrisProtocol.MAX_QUERY_BLOCK_COORDINATE;
        return minBlockX < -limit || maxBlockX > limit || minBlockZ < -limit || maxBlockZ > limit;
    }
}
