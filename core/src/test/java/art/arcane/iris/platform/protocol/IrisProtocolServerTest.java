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
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.ProtocolException;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisProtocolServerTest {
    private static final String BRAND = "TestBrand";
    private static final long SERVER_CAPABILITIES = IrisProtocol.CAPABILITY_PREGEN;

    @Test
    public void frameBeforeHelloIsDroppedAndCounted() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.PregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING)));

        assertEquals(IrisSession.State.AWAITING_HELLO, session.state());
        assertEquals(1L, server.droppedBeforeHelloCount());
        assertEquals(0, transport.sent.size());
    }

    @Test
    public void wrongProtocolVersionNeverReachesReadyButStillGetsAnswered() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION + 999, IrisProtocol.CAPABILITY_PREGEN)));

        assertEquals(IrisSession.State.AWAITING_HELLO, session.state());
        assertEquals(1L, server.versionMismatchCount());
        // The reply is what lets the client resolve to INCOMPATIBLE instead of retrying five times and then
        // reporting "server does not run Iris", which is a different and wrong diagnosis.
        assertEquals(1, transport.sent.size());
        IrisMessage.ServerHello answer = (IrisMessage.ServerHello) transport.sent.get(0);
        assertEquals(IrisProtocol.PROTOCOL_VERSION, answer.protocolVersion());
        assertEquals(BRAND, answer.serverBrand());
    }

    @Test
    public void mismatchedHelloStillLeavesTheSessionUnableToRequestAnything() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.setEngineResolver(sessionId -> {
            throw new AssertionError("an incompatible session must never reach the engine");
        });

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION + 1, IrisProtocol.CAPABILITY_CURSOR)));
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(1, 2)));

        assertEquals(0L, session.capabilities());
        assertEquals(1L, server.droppedBeforeHelloCount());
        assertEquals(1, transport.sent.size());
    }

    @Test
    public void cursorRequestBeyondWorldBoundsRejectedWithoutResolvingEngine() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));
        server.setEngineResolver(sessionId -> {
            throw new AssertionError("an out-of-bounds column must never reach the engine");
        });

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(IrisProtocol.MAX_QUERY_BLOCK_COORDINATE + 1, 0)));
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(0, -IrisProtocol.MAX_QUERY_BLOCK_COORDINATE - 1)));
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(Integer.MIN_VALUE, Integer.MAX_VALUE)));

        assertEquals(3L, server.cursorOutOfBoundsCount());
        assertEquals(0L, server.cursorInfoServedCount());
        assertEquals(1, transport.sent.size());
    }

    @Test
    public void cursorBurstBeyondItsOwnBudgetShedsWithoutTouchingTheFrameBudget() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true, () -> 1000L);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));
        server.setEngineResolver(sessionId -> cursorEngine("iris:plains", "iris:temperate", "", 64, "overworld"));

        int overflow = 3;
        int total = IrisProtocol.MAX_CURSOR_INFO_REQUESTS_PER_SECOND + overflow;
        byte[] request = IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(8, 8));
        for (int index = 0; index < total; index++) {
            server.onClientFrame("s1", request);
        }

        assertEquals(IrisProtocol.MAX_CURSOR_INFO_REQUESTS_PER_SECOND, server.cursorInfoServedCount());
        assertEquals(overflow, server.cursorRateLimitedCount());
        assertEquals(0L, server.rateLimitedFrameCount());
        assertTrue("the cursor budget must be tighter than the frame budget",
                IrisProtocol.MAX_CURSOR_INFO_REQUESTS_PER_SECOND < IrisProtocol.MAX_INBOUND_FRAMES_PER_SECOND);
    }

    @Test
    public void matchingHelloReachesReadyAndAnswersServerHello() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));

        assertEquals(IrisSession.State.READY, session.state());
        assertEquals(IrisProtocol.CAPABILITY_PREGEN, session.capabilities());
        assertEquals(1, transport.sent.size());
        IrisMessage.ServerHello answer = (IrisMessage.ServerHello) transport.sent.get(0);
        assertEquals(IrisProtocol.PROTOCOL_VERSION, answer.protocolVersion());
        assertEquals(SERVER_CAPABILITIES, answer.capabilities());
        assertEquals(BRAND, answer.serverBrand());
        assertTrue(answer.irisActive());
    }

    @Test
    public void repeatedHelloResendsServerHelloForPacketLossRecovery() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        byte[] hello = IrisMessageCodec.encode(new IrisMessage.ClientHello(
                IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN));

        server.onClientFrame("s1", hello);
        server.onClientFrame("s1", hello);

        assertEquals(IrisSession.State.READY, session.state());
        assertEquals(2, transport.sent.size());
        assertTrue(transport.sent.get(0) instanceof IrisMessage.ServerHello);
        assertTrue(transport.sent.get(1) instanceof IrisMessage.ServerHello);
    }

    @Test
    public void readySessionReceivesProgressAndEndBroadcasts() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));

        server.broadcastPregenProgress(5L, 10L, 100L, 3.0D, 2000L, IrisMessage.PregenProgress.STATE_PAUSED);
        server.pregenEnd(5L, true);

        assertEquals(3, transport.sent.size());
        IrisMessage.PregenProgress progress = (IrisMessage.PregenProgress) transport.sent.get(1);
        assertEquals(new IrisMessage.PregenProgress(5L, 10L, 100L, 3.0D, 2000L, IrisMessage.PregenProgress.STATE_PAUSED), progress);
        assertEquals(new IrisMessage.PregenEnd(5L, true), transport.sent.get(2));
    }

    @Test
    public void unregisteringStopsBroadcasts() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));
        int afterHandshake = transport.sent.size();

        registry.unregister("s1");
        server.broadcastPregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING);
        server.pregenEnd(1L, true);

        assertEquals(afterHandshake, transport.sent.size());
        assertTrue(registry.isEmpty());
    }

    @Test
    public void broadcastToEmptyRegistryDoesNothing() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);

        server.broadcastPregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING);
        server.pregenEnd(1L, false);

        assertTrue(registry.isEmpty());
    }

    @Test
    public void awaitingSessionDoesNotReceiveBroadcasts() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.broadcastPregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING);

        assertEquals(0, transport.sent.size());
    }

    @Test
    public void readySessionWithoutPregenCapabilityIsNotBroadcastTo() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, 0L)));

        server.broadcastPregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING);

        assertFalse(session.hasCapability(IrisProtocol.CAPABILITY_PREGEN));
        assertEquals(1, transport.sent.size());
    }

    @Test
    public void burstBeyondRateLimitDropsWithoutDispatching() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true, () -> 1000L);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        int overflow = 5;
        int total = IrisProtocol.MAX_INBOUND_FRAMES_PER_SECOND + overflow;
        byte[] preHelloFrame = IrisMessageCodec.encode(new IrisMessage.PregenProgress(1L, 1L, 1L, 1.0D, 1L, IrisMessage.PregenProgress.STATE_RUNNING));
        for (int index = 0; index < total; index++) {
            server.onClientFrame("s1", preHelloFrame);
        }

        assertEquals(IrisProtocol.MAX_INBOUND_FRAMES_PER_SECOND, server.droppedBeforeHelloCount());
        assertEquals(overflow, server.rateLimitedFrameCount());
    }

    @Test
    public void cursorInfoRequestServedForCapableReadySession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));
        server.setEngineResolver(sessionId -> cursorEngine("iris:plains", "iris:temperate", "iris:lush_cave", 88, "overworld"));

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(10, -20)));

        assertEquals(1L, server.cursorInfoServedCount());
        IrisMessage.CursorInfo info = (IrisMessage.CursorInfo) transport.sent.get(1);
        assertEquals(10, info.blockX());
        assertEquals(-20, info.blockZ());
        assertEquals("iris:plains", info.biomeKey());
        assertEquals("iris:temperate", info.regionKey());
        assertEquals("iris:lush_cave", info.caveBiomeKey());
        assertEquals(88, info.height());
        assertEquals("overworld", info.dimensionKey());
    }

    @Test
    public void cursorInfoRequestWithoutCapabilityRejectedWithoutResolvingEngine() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, 0L)));
        server.setEngineResolver(sessionId -> {
            throw new AssertionError("engine must not be resolved without cursor capability");
        });

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(1, 2)));

        assertEquals(1L, server.capabilityRejectedCount());
        assertEquals(1, transport.sent.size());
    }

    @Test
    public void cursorInfoRequestWithNullEngineCountsDrop() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));
        server.setEngineResolver(sessionId -> null);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(1, 2)));

        assertEquals(1L, server.noEngineDropCount());
        assertEquals(0L, server.cursorInfoServedCount());
        assertEquals(1, transport.sent.size());
    }

    @Test
    public void visionTileRequestForwardedForCapableReadySession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION)));
        RecordingVisionHandler handler = new RecordingVisionHandler();
        server.setVisionTileHandler(handler);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(7, -9, 3)));

        assertEquals(1, handler.count);
        assertEquals(7, handler.lastTileX);
        assertEquals(-9, handler.lastTileZ);
        assertEquals(3, handler.lastZoom);
        assertEquals(1L, server.visionTileForwardedCount());
    }

    @Test
    public void visionTileOutsideWorldBoundsIsRejectedBeforeRendering() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(
                IrisProtocol.PROTOCOL_VERSION,
                IrisProtocol.CAPABILITY_VISION)));
        RecordingVisionHandler handler = new RecordingVisionHandler();
        server.setVisionTileHandler(handler);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(Integer.MAX_VALUE, 0, 0)));
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(0, Integer.MIN_VALUE, 8)));
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(0, 0, Integer.MAX_VALUE)));

        assertEquals(1, handler.count);
        assertEquals(2L, server.visionOutOfBoundsCount());
        assertEquals(1L, server.visionTileForwardedCount());
    }

    @Test
    public void visionTileRequestBudgetDropsExcessWithinWindow() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true, () -> 1000L);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION)));
        RecordingVisionHandler handler = new RecordingVisionHandler();
        server.setVisionTileHandler(handler);

        int overflow = 3;
        int total = IrisProtocol.MAX_VISION_TILE_REQUESTS_PER_SECOND + overflow;
        byte[] request = IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(1, 1, 0));
        for (int index = 0; index < total; index++) {
            server.onClientFrame("s1", request);
        }

        assertEquals(IrisProtocol.MAX_VISION_TILE_REQUESTS_PER_SECOND, handler.count);
        assertEquals(IrisProtocol.MAX_VISION_TILE_REQUESTS_PER_SECOND, server.visionTileForwardedCount());
        assertEquals(overflow, server.visionRateLimitedCount());
    }

    @Test
    public void visionTileRequestWithoutCapabilityRejected() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));
        RecordingVisionHandler handler = new RecordingVisionHandler();
        server.setVisionTileHandler(handler);

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(1, 1, 0)));

        assertEquals(0, handler.count);
        assertEquals(1L, server.capabilityRejectedCount());
    }

    @Test
    public void visionTileRequestWithoutHandlerCountsDrop() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION)));

        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(1, 1, 0)));

        assertEquals(1L, server.noEngineDropCount());
    }

    @Test
    public void readySessionReceivesDimensionStatusBroadcast() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION)));

        server.broadcastDimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true);

        assertEquals(new IrisMessage.DimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true), transport.sent.get(1));
    }

    @Test
    public void awaitingSessionDoesNotReceiveDimensionStatusBroadcast() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.broadcastDimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true);

        assertEquals(0, transport.sent.size());
    }

    @Test
    public void sendDimensionStatusTargetsSingleReadySession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR)));

        server.sendDimensionStatus("s1", "minecraft:the_nether", "nether", 42L, 0, 256, true);

        assertEquals(new IrisMessage.DimensionStatus("minecraft:the_nether", "nether", 42L, 0, 256, true), transport.sent.get(1));
    }

    @Test
    public void pregenRegionDeltaReachesPregenCapableSession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));

        server.broadcastPregenRegionDelta(7L, 3, -4, IrisMessage.PregenRegionDelta.STATE_GENERATING);

        assertEquals(new IrisMessage.PregenRegionDelta(7L, 3, -4, IrisMessage.PregenRegionDelta.STATE_GENERATING), transport.sent.get(1));
        assertEquals(1L, server.pregenRegionDeltasBroadcastCount());
    }

    @Test
    public void pregenRegionDeltaNotSentToSessionWithoutPregenCapability() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_STUDIO)));

        server.broadcastPregenRegionDelta(7L, 3, -4, IrisMessage.PregenRegionDelta.STATE_DONE);

        assertEquals(1, transport.sent.size());
        assertEquals(0L, server.pregenRegionDeltasBroadcastCount());
    }

    @Test
    public void studioHotloadReachesStudioCapableSession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_STUDIO)));

        server.broadcastStudioHotload("overworld", 5, false, "");

        assertEquals(new IrisMessage.StudioHotload("overworld", 5, false, ""), transport.sent.get(1));
        assertEquals(1L, server.studioHotloadsBroadcastCount());
    }

    @Test
    public void readySessionWithoutStudioCapabilityReceivesNoStudioMessages() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));

        server.broadcastStudioHotload("overworld", 5, true, "boom");
        server.broadcastToast(IrisMessage.Toast.KIND_ERROR, "Studio Hotload", "overworld failed");

        assertFalse(session.hasCapability(IrisProtocol.CAPABILITY_STUDIO));
        assertEquals(1, transport.sent.size());
        assertEquals(0L, server.studioHotloadsBroadcastCount());
        assertEquals(0L, server.toastsBroadcastCount());
    }

    @Test
    public void broadcastToastReachesStudioCapableSession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_STUDIO)));

        server.broadcastToast(IrisMessage.Toast.KIND_SUCCESS, "Studio Hotload", "overworld");

        assertEquals(new IrisMessage.Toast(IrisMessage.Toast.KIND_SUCCESS, "Studio Hotload", "overworld"), transport.sent.get(1));
        assertEquals(1L, server.toastsBroadcastCount());
    }

    @Test
    public void sendToastTargetsStudioCapableSession() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_STUDIO)));

        server.sendToast("s1", IrisMessage.Toast.KIND_INFO, "Hello", "World");

        assertEquals(new IrisMessage.Toast(IrisMessage.Toast.KIND_INFO, "Hello", "World"), transport.sent.get(1));
        assertEquals(1L, server.toastsSentCount());
    }

    @Test
    public void sendToastWithoutStudioCapabilityDropsWithoutCounting() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);
        server.onClientFrame("s1", IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN)));

        server.sendToast("s1", IrisMessage.Toast.KIND_INFO, "Hello", "World");

        assertEquals(1, transport.sent.size());
        assertEquals(0L, server.toastsSentCount());
    }

    @Test
    public void awaitingSessionDoesNotReceiveStudioHotloadBroadcast() {
        RecordingTransport transport = new RecordingTransport();
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisProtocolServer server = new IrisProtocolServer(registry, SERVER_CAPABILITIES, BRAND, true);
        IrisSession session = new IrisSession("s1", transport);
        registry.register(session);

        server.broadcastStudioHotload("overworld", 1, false, "");

        assertEquals(0, transport.sent.size());
        assertEquals(0L, server.studioHotloadsBroadcastCount());
    }

    private static Engine cursorEngine(String biomeKey, String regionKey, String caveKey, int height, String dimensionKey) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(biomeKey);
        IrisRegion region = new IrisRegion();
        region.setLoadKey(regionKey);
        IrisBiome cave = new IrisBiome();
        cave.setLoadKey(caveKey);
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey(dimensionKey);
        return (Engine) Proxy.newProxyInstance(Engine.class.getClassLoader(), new Class[]{Engine.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getSurfaceBiome" -> biome;
            case "getRegion" -> region;
            case "getCaveBiome" -> cave;
            case "getHeight" -> height;
            case "getMinHeight" -> 0;
            case "getDimension" -> dimension;
            case "toString" -> "proxyEngine";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        });
    }

    private static final class RecordingVisionHandler implements VisionTileRequestHandler {
        private int count;
        private int lastTileX;
        private int lastTileZ;
        private int lastZoom;

        @Override
        public void handle(String sessionId, int tileX, int tileZ, int zoomLevel) {
            count++;
            lastTileX = tileX;
            lastTileZ = tileZ;
            lastZoom = zoomLevel;
        }
    }

    private static final class RecordingTransport implements IrisServerTransport {
        private final List<IrisMessage> sent = new ArrayList<>();

        @Override
        public void sendToClient(String sessionId, byte[] frame) {
            try {
                sent.add(IrisMessageCodec.decode(frame));
            } catch (ProtocolException rejected) {
                throw new AssertionError(rejected);
            }
        }
    }
}
