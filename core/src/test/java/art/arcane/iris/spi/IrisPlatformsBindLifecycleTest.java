package art.arcane.iris.spi;

import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisPlatformsBindLifecycleTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void resetBinding() {
        IrisPlatforms.unbind();
    }

    @After
    public void clearBinding() {
        IrisPlatforms.unbind();
    }

    @Test
    public void rebindsAfterUnbind() {
        IrisPlatform first = mock(IrisPlatform.class);
        IrisPlatforms.bind(first);
        assertTrue(IrisPlatforms.isBound());
        assertSame(first, IrisPlatforms.get());

        IrisPlatforms.unbind();
        assertFalse(IrisPlatforms.isBound());

        IrisPlatform second = mock(IrisPlatform.class);
        IrisPlatforms.bind(second);
        assertTrue(IrisPlatforms.isBound());
        assertSame(second, IrisPlatforms.get());
    }

    @Test
    public void unbindIsIdempotentWhenUnbound() {
        IrisPlatforms.unbind();
        assertFalse(IrisPlatforms.isBound());
        IrisPlatforms.unbind();
        assertFalse(IrisPlatforms.isBound());
    }
}
