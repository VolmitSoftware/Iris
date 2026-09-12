package art.arcane.iris.generation.locator;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocatorCancellerTest {
    @Test
    public void cancellingOneRequestDoesNotCancelAnother() throws Exception {
        CompletableFuture<String> firstDelegate = new CompletableFuture<>();
        CompletableFuture<String> secondDelegate = new CompletableFuture<>();
        AtomicBoolean firstStop = new AtomicBoolean();
        AtomicBoolean secondStop = new AtomicBoolean();
        Future<String> first = LocatorCanceller.requestScoped(firstDelegate, firstStop);
        Future<String> second = LocatorCanceller.requestScoped(secondDelegate, secondStop);

        assertTrue(first.cancel(false));
        secondDelegate.complete("found");

        assertTrue(firstStop.get());
        assertTrue(first.isCancelled());
        assertTrue(firstDelegate.isCancelled());
        assertFalse(secondStop.get());
        assertFalse(second.isCancelled());
        assertEquals("found", second.get());
    }

    @Test
    public void completedRequestCannotBeCancelled() throws Exception {
        CompletableFuture<String> delegate = CompletableFuture.completedFuture("done");
        AtomicBoolean stop = new AtomicBoolean();
        Future<String> request = LocatorCanceller.requestScoped(delegate, stop);

        assertFalse(request.cancel(false));
        assertFalse(stop.get());
        assertEquals("done", request.get());
    }
}
