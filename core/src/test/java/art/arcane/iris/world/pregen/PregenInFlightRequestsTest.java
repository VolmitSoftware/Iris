package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PregenInFlightRequestsTest {
    @Test
    public void settlingARequestReturnsItsListenerOnlyOnce() {
        PregenInFlightRequests requests = new PregenInFlightRequests();
        PregenListener listener = mock(PregenListener.class);
        long key = PregenInFlightRequests.key(48, -4);

        assertTrue(requests.add(key, listener));
        assertEquals(1, requests.size());
        assertSame(listener, requests.settle(key));
        assertNull(requests.settle(key));
        assertEquals(0, requests.size());
    }

    @Test
    public void aDuplicateSubmissionKeepsTheOriginalRequest() {
        PregenInFlightRequests requests = new PregenInFlightRequests();
        PregenListener first = mock(PregenListener.class);
        PregenListener second = mock(PregenListener.class);
        long key = PregenInFlightRequests.key(1, 2);

        assertTrue(requests.add(key, first));
        assertFalse(requests.add(key, second));
        assertSame(first, requests.settle(key));
    }

    @Test
    public void drainingHandsBackEveryOutstandingRequestExactlyOnce() {
        PregenInFlightRequests requests = new PregenInFlightRequests();
        PregenListener listener = mock(PregenListener.class);
        requests.add(PregenInFlightRequests.key(0, 0), listener);
        requests.add(PregenInFlightRequests.key(1, 0), listener);
        requests.add(PregenInFlightRequests.key(-7, 9), listener);

        Map<Long, PregenListener> drained = requests.drain();

        assertEquals(3, drained.size());
        assertEquals(0, requests.size());
        assertTrue(requests.drain().isEmpty());
        assertNull(requests.settle(PregenInFlightRequests.key(1, 0)));
    }

    @Test
    public void keysDistinguishNegativeCoordinates() {
        assertTrue(PregenInFlightRequests.key(1, -1) != PregenInFlightRequests.key(-1, 1));
        assertTrue(PregenInFlightRequests.key(0, -1) != PregenInFlightRequests.key(-1, 0));
        assertEquals(PregenInFlightRequests.key(48, -4), PregenInFlightRequests.key(48, -4));
    }
}
