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
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.protocol.IrisProtocol;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCursorRequestServiceTest {
    private static final Executor DIRECT = Runnable::run;

    @Test
    public void burstCoalescesToLatestRequestPerSessionAndOneDrain() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1");
        QueuedExecutor executor = new QueuedExecutor();
        IrisCursorRequestService service = new IrisCursorRequestService(
                sessionId -> {
                    throw new AssertionError("queued work must not run early");
                },
                registry,
                executor,
                8);

        for (int index = 0; index < 10; index++) {
            service.handle("s1", index, -index);
        }

        assertEquals(1, service.pendingSize());
        assertEquals(9L, service.coalescedCount());
        assertEquals(1, executor.size());
    }

    @Test
    public void saturationDropsOldestSessionsButKeepsQueueBounded() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        QueuedExecutor executor = new QueuedExecutor();
        IrisCursorRequestService service = new IrisCursorRequestService(sessionId -> null, registry, executor, 2);

        service.handle("s1", 1, 1);
        service.handle("s2", 2, 2);
        service.handle("s3", 3, 3);

        assertEquals(2, service.pendingSize());
        assertEquals(1L, service.droppedSaturatedCount());
        assertEquals(1, executor.size());
    }

    @Test
    public void directDrainResolvesAndSendsCursorResponse() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        CountingTransport transport = registerReady(registry, "s1");
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.isClosed()).thenReturn(false);
        when(engine.getSurfaceBiome(4, 8)).thenReturn(null);
        when(engine.getRegion(4, 8)).thenReturn(null);
        when(engine.getCaveBiome(4, 8)).thenReturn(null);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(4, 8)).thenReturn(80);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getLoadKey()).thenReturn("overworld");
        IrisCursorRequestService service = new IrisCursorRequestService(sessionId -> engine, registry, DIRECT, 8);

        service.handle("s1", 4, 8);

        assertEquals(1L, service.resolvedCount());
        assertEquals(1, transport.sent.get());
        assertEquals(0, service.pendingSize());
    }

    @Test
    public void blankSessionIsRejectedWithoutSchedulingWork() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        QueuedExecutor executor = new QueuedExecutor();
        IrisCursorRequestService service = new IrisCursorRequestService(sessionId -> null, registry, executor, 8);

        service.handle(null, 0, 0);
        service.handle("", 0, 0);

        assertEquals(2L, service.droppedNoSessionCount());
        assertEquals(0, service.pendingSize());
        assertEquals(0, executor.size());
    }

    private static CountingTransport registerReady(IrisSessionRegistry registry, String sessionId) {
        CountingTransport transport = new CountingTransport();
        IrisSession session = new IrisSession(sessionId, transport);
        session.markReady(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_CURSOR);
        registry.register(session);
        return transport;
    }

    private static final class CountingTransport implements IrisServerTransport {
        private final AtomicInteger sent = new AtomicInteger(0);

        @Override
        public void sendToClient(String sessionId, byte[] frame) {
            sent.incrementAndGet();
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private int size() {
            return tasks.size();
        }
    }
}
