package art.arcane.iris.core.pregenerator.cache;

import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PregenCacheExecutorRegistrationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final class RecordingPreservation implements PreservationRegistry {
        private final List<ExecutorService> executors = new CopyOnWriteArrayList<>();

        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
            executors.add(service);
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    @After
    public void clearServices() {
        IrisServices.remove(PreservationRegistry.class);
    }

    @Test
    public void dispatcherExecutorIsRegisteredForShutdown() throws Exception {
        RecordingPreservation preservation = new RecordingPreservation();
        IrisServices.register(PreservationRegistry.class, preservation);

        File directory = Files.createTempDirectory("iris-pregen-exec-reg").toFile();
        PregenCache.create(directory);

        assertFalse("PregenCacheImpl must register its dispatcher executor for shutdown", preservation.executors.isEmpty());
        for (ExecutorService service : preservation.executors) {
            assertFalse("registered executor must be live so preservation can shut it down later", service.isShutdown());
        }
    }
}
