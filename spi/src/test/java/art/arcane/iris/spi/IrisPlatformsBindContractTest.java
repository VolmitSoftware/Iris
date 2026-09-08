package art.arcane.iris.spi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisPlatformsBindContractTest {
    @Before
    public void requireUnbound() {
        IrisPlatforms.unbind();
    }

    @After
    public void releaseBinding() {
        IrisPlatforms.unbind();
    }

    @Test
    public void unboundLookupsReportAbsenceInsteadOfImprovising() {
        assertFalse(IrisPlatforms.isBound());
        assertNull(IrisPlatforms.getOrNull());

        IllegalStateException failure = assertThrows(IllegalStateException.class, IrisPlatforms::get);

        assertTrue(failure.getMessage(), failure.getMessage().contains("No Iris platform is bound"));
    }

    @Test
    public void bindingIsVisibleThroughEveryAccessor() {
        IrisPlatform platform = new StubPlatform();

        IrisPlatforms.bind(platform);

        assertTrue(IrisPlatforms.isBound());
        assertSame(platform, IrisPlatforms.get());
        assertSame(platform, IrisPlatforms.getOrNull());
    }

    @Test
    public void rebindingTheSameInstanceIsAccepted() {
        IrisPlatform platform = new StubPlatform();

        IrisPlatforms.bind(platform);
        IrisPlatforms.bind(platform);

        assertSame(platform, IrisPlatforms.get());
    }

    @Test
    public void aSecondPlatformCannotDisplaceTheBoundOne() {
        IrisPlatform first = new StubPlatform();
        IrisPlatforms.bind(first);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> IrisPlatforms.bind(new StubPlatform()));

        assertTrue(failure.getMessage(), failure.getMessage().contains("already bound"));
        assertSame(first, IrisPlatforms.get());
    }

    @Test
    public void unbindIsIdempotentAndClearsTheBinding() {
        IrisPlatforms.bind(new StubPlatform());

        IrisPlatforms.unbind();
        IrisPlatforms.unbind();

        assertFalse(IrisPlatforms.isBound());
        assertNull(IrisPlatforms.getOrNull());
    }

    @Test
    public void unbindingClearsTheOnceKeysSoLaterRunsWarnAgain() {
        IrisPlatforms.bind(new StubPlatform());
        assertTrue(IrisLogging.warnOnce("spi-contract-once", "first"));
        assertFalse(IrisLogging.warnOnce("spi-contract-once", "second"));

        IrisPlatforms.unbind();

        assertTrue(IrisLogging.warnOnce("spi-contract-once", "third"));
    }

    private static final class StubPlatform implements IrisPlatform {
        @Override
        public String platformName() {
            return "stub";
        }

        @Override
        public String minecraftVersion() {
            return "26.2";
        }

        @Override
        public PlatformRegistries registries() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlatformScheduler scheduler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlatformStructureHooks structureHooks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlatformBiomeWriter biomeWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public File dataFolder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public File dataFile(String... path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File pluginJar() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int irisVersionNumber() {
            return 0;
        }

        @Override
        public int minecraftVersionNumber() {
            return 0;
        }

        @Override
        public void callEvent(Object event) {
        }

        @Override
        public void dispatchConsoleCommand(String command) {
        }

        @Override
        public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
            return false;
        }

        @Override
        public void log(LogLevel level, String message) {
        }

        @Override
        public void msg(String message) {
        }

        @Override
        public void reportError(Throwable error) {
        }
    }
}
