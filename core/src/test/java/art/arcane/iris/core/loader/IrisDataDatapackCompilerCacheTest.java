package art.arcane.iris.core.loader;

import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisDataDatapackCompilerCacheTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisData runtimeData;
    private IrisData compilerData;

    @Before
    public void registerPreservationService() {
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
    }

    @After
    public void closeDataAndServices() {
        if (compilerData != null) {
            compilerData.close();
        }
        if (runtimeData != null) {
            runtimeData.close();
        }
        IrisServices.remove(PreservationRegistry.class);
    }

    @Test
    public void closingCompilerDoesNotEvictRuntimeDataForSameDirectory() throws Exception {
        File packDirectory = temporaryFolder.newFolder("pack");
        runtimeData = IrisData.get(packDirectory);
        compilerData = IrisData.openDatapackCompiler(packDirectory);

        compilerData.close();
        compilerData = null;

        assertSame(runtimeData, IrisData.getLoaded(packDirectory).orElseThrow());
    }

    @Test
    public void structureInvalidationMatchesNormalizedLoadedPackPaths() throws Exception {
        File packDirectory = temporaryFolder.newFolder("invalidate-pack");
        runtimeData = IrisData.get(new File(packDirectory, "."));

        assertTrue(IrisData.invalidateLoadedStructureResources(packDirectory));
        assertFalse(IrisData.invalidateLoadedStructureResources(
                temporaryFolder.newFolder("unloaded-pack")));
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
