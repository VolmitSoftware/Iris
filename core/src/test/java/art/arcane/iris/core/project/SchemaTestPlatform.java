package art.arcane.iris.core.project;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import org.mockito.Answers;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Binds a platform whose registries list nothing, so studio schemas can be built in a test; closing restores the previous one. */
public final class SchemaTestPlatform implements AutoCloseable {
    private final IrisPlatform previous;

    private SchemaTestPlatform(File dataFolder) {
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.dataFolder()).thenReturn(dataFolder);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    public static SchemaTestPlatform bind(File dataFolder) {
        return new SchemaTestPlatform(dataFolder);
    }

    @Override
    public void close() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }
}
