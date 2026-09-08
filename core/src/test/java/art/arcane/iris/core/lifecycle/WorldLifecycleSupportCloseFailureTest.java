package art.arcane.iris.core.lifecycle;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A level storage access that will not close still holds the world's session lock, and the next attempt to
 * open that world fails with a lock message that names no cause. The close failure used to be swallowed
 * whole, so the console had nothing to connect the two.
 */
public class WorldLifecycleSupportCloseFailureTest {
    private IrisPlatform capturingPlatform;
    private IrisPlatform previousPlatform;

    @Before
    public void captureReports() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        capturingPlatform = mock(IrisPlatform.class);
        IrisPlatforms.bind(capturingPlatform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void aFailedStorageCloseIsReportedWithItsCause() {
        WorldLifecycleSupport.closeLevelStorageAccess(new FailingStorageAccess());

        ArgumentCaptor<Throwable> reported = ArgumentCaptor.forClass(Throwable.class);
        verify(capturingPlatform).reportError(anyString(), reported.capture());
        assertEquals("session lock still held", reported.getValue().getMessage());
    }

    @Test
    public void aClosableStorageAccessReportsNothing() {
        WorldLifecycleSupport.closeLevelStorageAccess(new ClosableStorageAccess());

        verify(capturingPlatform, org.mockito.Mockito.never()).reportError(anyString(), org.mockito.ArgumentMatchers.any());
    }

    public static final class FailingStorageAccess {
        public void close() {
            throw new IllegalStateException("session lock still held");
        }
    }

    public static final class ClosableStorageAccess {
        public void close() {
        }
    }
}
