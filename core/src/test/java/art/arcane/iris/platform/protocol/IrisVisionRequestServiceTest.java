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

import art.arcane.iris.spi.protocol.IrisProtocol;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class IrisVisionRequestServiceTest {
    private static final Executor DIRECT = Runnable::run;
    private static final Executor DISABLED = runnable -> {
    };

    @Test
    public void saturationDropsOldestRequestsAndCounts() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> {
            throw new AssertionError("disabled executor must not process requests");
        };
        int maxPending = 4;
        int overflow = 6;
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DISABLED, maxPending);

        for (int index = 0; index < maxPending + overflow; index++) {
            service.handle("s1", index, 0, 0);
        }

        assertEquals(overflow, service.droppedSaturatedCount());
        assertEquals(maxPending, service.pendingSize());
    }

    @Test
    public void burstUsesAtMostTwoDrainsAndDrainsEveryRetainedRequest() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> null;
        QueuedExecutor executor = new QueuedExecutor();
        int maxPending = 4;
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, executor, maxPending);

        for (int index = 0; index < 10; index++) {
            service.handle("s1", index, 0, 0);
        }

        assertEquals(2, executor.size());
        assertEquals(maxPending, service.pendingSize());
        executor.runAll();
        assertEquals(0, service.pendingSize());
        assertEquals(maxPending, service.droppedNoEngineCount());
    }

    @Test
    public void duplicateTileRequestsCoalesceWithoutConsumingCapacity() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        IrisVisionRequestService service = new IrisVisionRequestService(sessionId -> null, registry, DISABLED, 8);

        service.handle("s1", 4, -7, 2);
        service.handle("s1", 4, -7, 2);
        service.handle("s1", 4, -7, 2);

        assertEquals(1, service.pendingSize());
        assertEquals(0L, service.droppedSaturatedCount());
    }

    @Test
    public void drainRotatesAcrossSessionsInsteadOfServingOneBurstFirst() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        registerReady(registry, "s2", IrisProtocol.CAPABILITY_VISION);
        ArrayList<String> resolutionOrder = new ArrayList<>();
        EngineResolver resolver = sessionId -> {
            resolutionOrder.add(sessionId);
            return null;
        };
        QueuedExecutor executor = new QueuedExecutor();
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, executor, 8);

        service.handle("s1", 0, 0, 0);
        service.handle("s1", 1, 0, 0);
        service.handle("s2", 0, 0, 0);
        service.handle("s2", 1, 0, 0);
        executor.runAll();

        assertEquals(List.of("s1", "s2", "s1", "s2"), resolutionOrder);
    }

    @Test
    public void saturatedSessionCannotExcludeAnotherSession() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "noisy", IrisProtocol.CAPABILITY_VISION);
        registerReady(registry, "other", IrisProtocol.CAPABILITY_VISION);
        IrisVisionRequestService service = new IrisVisionRequestService(sessionId -> null, registry, DISABLED, 4);

        for (int tile = 0; tile < 20; tile++) {
            service.handle("noisy", tile, 0, 0);
        }
        service.handle("other", 0, 0, 0);

        assertEquals(4, service.pendingSize());
        service.clearSession("noisy");
        assertEquals(1, service.pendingSize());
    }

    @Test
    public void blankSessionIsRejectedWithoutSchedulingWork() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        QueuedExecutor executor = new QueuedExecutor();
        IrisVisionRequestService service = new IrisVisionRequestService(sessionId -> null, registry, executor, 8);

        service.handle(null, 0, 0, 0);
        service.handle("", 0, 0, 0);
        service.handle("   ", 0, 0, 0);

        assertEquals(3L, service.droppedNoSessionCount());
        assertEquals(0, service.pendingSize());
        assertEquals(0, executor.size());
    }

    @Test
    public void nullEngineDropsAndCounts() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        CountingTransport transport = registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> null;
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DIRECT, 8);

        service.handle("s1", 0, 0, 0);

        assertEquals(1L, service.droppedNoEngineCount());
        assertEquals(0L, service.tilesEncodedCount());
        assertEquals(0, transport.sent.get());
    }

    @Test
    public void missingReadyOrCapableSessionDropsWithoutResolvingEngine() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        IrisSession awaiting = new IrisSession("awaiting", new CountingTransport());
        registry.register(awaiting);
        IrisSession noCapability = new IrisSession("nocap", new CountingTransport());
        noCapability.markReady(IrisProtocol.PROTOCOL_VERSION, 0L);
        registry.register(noCapability);
        EngineResolver resolver = sessionId -> {
            throw new AssertionError("engine must not be resolved for an ineligible session");
        };
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DIRECT, 8);

        service.handle("unregistered", 0, 0, 0);
        service.handle("awaiting", 0, 0, 0);
        service.handle("nocap", 0, 0, 0);

        assertEquals(3L, service.droppedNoSessionCount());
        assertEquals(0L, service.droppedNoEngineCount());
        assertEquals(0L, service.tilesEncodedCount());
    }

    @Test
    public void clearSessionDropsOnlyThatSessionsQueuedRequests() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        registerReady(registry, "s10", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> {
            throw new AssertionError("disabled executor must not process requests");
        };
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DISABLED, 8);

        service.handle("s1", 0, 0, 0);
        service.handle("s1", 1, 0, 0);
        service.handle("s10", 0, 0, 0);
        assertEquals(3, service.pendingSize());

        service.clearSession("s1");

        assertEquals(1, service.pendingSize());
        assertEquals(0L, service.droppedSaturatedCount());
    }

    @Test
    public void clearSessionIgnoresBlankIdsWithoutTouchingTheQueue() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> {
            throw new AssertionError("disabled executor must not process requests");
        };
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DISABLED, 8);

        service.handle("s1", 0, 0, 0);
        service.clearSession(null);
        service.clearSession("");

        assertEquals(1, service.pendingSize());
    }

    @Test
    public void saturationCountsEveryShedRequestNotJustTheLastBurst() {
        IrisSessionRegistry registry = new IrisSessionRegistry();
        registerReady(registry, "s1", IrisProtocol.CAPABILITY_VISION);
        EngineResolver resolver = sessionId -> {
            throw new AssertionError("disabled executor must not process requests");
        };
        int maxPending = 2;
        IrisVisionRequestService service = new IrisVisionRequestService(resolver, registry, DISABLED, maxPending);

        for (int index = 0; index < 10; index++) {
            service.handle("s1", index, 0, 0);
        }

        assertEquals(8L, service.droppedSaturatedCount());
        assertEquals(maxPending, service.pendingSize());
    }

    private static CountingTransport registerReady(IrisSessionRegistry registry, String sessionId, long capabilities) {
        CountingTransport transport = new CountingTransport();
        IrisSession session = new IrisSession(sessionId, transport);
        session.markReady(IrisProtocol.PROTOCOL_VERSION, capabilities);
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

        private void runNext() {
            tasks.removeFirst().run();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }
}
