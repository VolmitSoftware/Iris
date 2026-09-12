package art.arcane.iris.world.storage.matter;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.bukkit.World;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisMatterPlatformSelectionTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void hybridClasspathDoesNotEnableBukkitMatterIoForModdedPlatform() {
        IrisPlatform platform = mock(IrisPlatform.class);
        withPlatform(platform, () -> {
            assertNull(new EntityMatter().readFrom(World.class));
            assertNull(new TileMatter().readFrom(World.class));
        });
    }

    @Test
    public void bukkitPlatformCapabilityEnablesBukkitMatterIo() {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.supportsMatterWorldIo()).thenReturn(true);
        withPlatform(platform, () -> {
            assertNotNull(new EntityMatter().readFrom(World.class));
            assertNotNull(new TileMatter().readFrom(World.class));
        });
    }

    private void withPlatform(IrisPlatform platform, Runnable test) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            test.run();
        } finally {
            IrisPlatforms.unbind();
            if (previous != null) {
                IrisPlatforms.bind(previous);
            }
        }
    }
}
