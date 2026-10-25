package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class RasterFailuresTest {
    private static final long PAST_WINDOW = RasterFailures.BACKOFF_NANOS + TimeUnit.SECONDS.toNanos(1L);

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void overBudgetSummaryIsWrittenOnceTheWindowClosesWithoutAnotherFailure() {
        try (EngineFixture.Environment environment = EngineFixture.environment()) {
            AtomicLong ticker = new AtomicLong();
            RasterFailures failures = new RasterFailures(ticker::get);
            for (int chunk = 0; chunk < 20; chunk++) {
                failures.report(chunk, "chunk " + chunk, new IllegalStateException("raster exploded"));
            }
            verify(environment.platform, times(RasterFailures.TRACE_BUDGET)).reportError(anyString(), any(Throwable.class));
            assertTrue(failures.backingOff(3L));
            assertFalse(failures.backingOff(99L));
            verify(environment.platform, never()).log(eq(LogLevel.WARN), anyString());

            ticker.addAndGet(PAST_WINDOW);
            assertFalse(failures.backingOff(99L));
            verify(environment.platform).log(eq(LogLevel.WARN), argThat((String message) -> message.contains("4 more chunks")));
            assertFalse(failures.backingOff(99L));
            assertFalse(failures.backingOff(3L));
            failures.failed(5L);
            verify(environment.platform, times(1)).log(eq(LogLevel.WARN), anyString());
            verify(environment.platform, times(RasterFailures.TRACE_BUDGET)).reportError(anyString(), any(Throwable.class));
        }
    }

    @Test
    public void tracesAndSummariesAreLoggedAfterTheMonitorIsReleased() {
        try (EngineFixture.Environment environment = EngineFixture.environment()) {
            AtomicLong ticker = new AtomicLong();
            RasterFailures failures = new RasterFailures(ticker::get);
            AtomicBoolean heldWhileLogging = new AtomicBoolean();
            AtomicInteger logged = new AtomicInteger();
            doAnswer(invocation -> {
                heldWhileLogging.compareAndSet(false, Thread.holdsLock(failures));
                logged.incrementAndGet();
                return null;
            }).when(environment.platform).reportError(anyString(), any(Throwable.class));
            doAnswer(invocation -> {
                heldWhileLogging.compareAndSet(false, Thread.holdsLock(failures));
                logged.incrementAndGet();
                return null;
            }).when(environment.platform).log(any(LogLevel.class), anyString());

            for (int chunk = 0; chunk < 20; chunk++) {
                failures.report(chunk, "chunk " + chunk, new IllegalStateException("raster exploded"));
            }
            ticker.addAndGet(PAST_WINDOW);
            failures.report(40L, "chunk 40", new IllegalStateException("raster exploded"));

            assertEquals(RasterFailures.TRACE_BUDGET + 2, logged.get());
            assertFalse(heldWhileLogging.get());
        }
    }
}
