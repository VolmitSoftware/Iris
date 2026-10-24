package com.volmit.host;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorldStatesTest {
    static final class Cache {}
    static final class Index {
        final Cache cache;
        Index(Cache cache) { this.cache = cache; }
    }
    static final class Loop {}
    static final class Other {}

    record Closing(String name, List<String> closed, boolean fails) implements AutoCloseable {
        @Override
        public void close() {
            closed.add(name);
            if (fails) {
                throw new IllegalStateException(name + " broke");
            }
        }
    }
    static final class ClosingA {}

    @Test
    void createsEachTypeOnceAndHandsEveryCallerTheSameInstance() {
        var states = new WorldStates();
        var created = new AtomicInteger();
        Cache first = states.state(Cache.class, () -> {
            created.incrementAndGet();
            return new Cache();
        });
        Cache second = states.state(Cache.class, () -> {
            created.incrementAndGet();
            return new Cache();
        });

        assertSame(first, second);
        assertEquals(1, created.get());
    }

    @Test
    void racingThreadsShareOneCreation() throws Exception {
        var states = new WorldStates();
        var created = new AtomicInteger();
        var start = new CountDownLatch(1);
        var threads = 8;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var results = new ArrayList<Future<Cache>>();
            for (int thread = 0; thread < threads; thread++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return states.state(Cache.class, () -> {
                        created.incrementAndGet();
                        return new Cache();
                    });
                }));
            }
            start.countDown();
            Cache shared = results.getFirst().get(10, TimeUnit.SECONDS);
            for (var result : results) {
                assertSame(shared, result.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, created.get());
    }

    @Test
    void aFactoryMayAskForOtherStateButNotForItself() {
        var states = new WorldStates();
        Index index = states.state(Index.class, () -> new Index(states.state(Cache.class, Cache::new)));
        assertSame(index.cache, states.state(Cache.class, Cache::new));

        var loop = assertThrows(IllegalStateException.class, () -> states.state(Loop.class, () -> {
            states.state(Other.class, () -> {
                states.state(Loop.class, Loop::new);
                return new Other();
            });
            return new Loop();
        }));
        assertTrue(loop.getMessage().contains(Loop.class.getName() + " -> " + Other.class.getName() + " -> " + Loop.class.getName()),
                loop.getMessage());
        assertNotNull(states.state(Loop.class, Loop::new), "a failed creation keeps nothing, so the next call creates it");
    }

    @Test
    void aFactoryThatFailsOrReturnsNullKeepsNothing() {
        var states = new WorldStates();
        assertThrows(IllegalArgumentException.class, () -> states.state(Cache.class, () -> {
            throw new IllegalArgumentException("not yet");
        }));
        var nothing = assertThrows(NullPointerException.class, () -> states.state(Cache.class, () -> null));
        assertTrue(nothing.getMessage().contains(Cache.class.getName()), nothing.getMessage());

        var cache = new Cache();
        assertSame(cache, states.state(Cache.class, () -> cache));
    }

    @Test
    void closingClosesEveryCloseableStateNewestFirstAndEndsTheStore() {
        var states = new WorldStates();
        var closed = new ArrayList<String>();
        states.state(Closing.class, () -> new Closing("first", closed, false));
        states.state(Cache.class, Cache::new);
        states.state(ClosingA.class, ClosingA::new);
        states.state(AutoCloseable.class, () -> () -> closed.add("second"));

        states.close();
        states.close();

        assertEquals(List.of("second", "first"), closed);
        var after = assertThrows(IllegalStateException.class, () -> states.state(Cache.class, Cache::new));
        assertTrue(after.getMessage().contains("after the world stopped"), after.getMessage());
    }

    @Test
    void closingContinuesPastAFailureAndReportsEveryOne() {
        var states = new WorldStates();
        var closed = new ArrayList<String>();
        states.state(Closing.class, () -> new Closing("first", closed, true));
        states.state(AutoCloseable.class, () -> new Closing("second", closed, true));

        var failure = assertThrows(IllegalStateException.class, states::close);

        assertEquals(List.of("second", "first"), closed);
        assertTrue(failure.getMessage().contains("second broke"), failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("first broke"));
    }
}
