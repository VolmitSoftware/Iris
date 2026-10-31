package art.arcane.iris.platform;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ClasspathVanillaDataSourceTest {
    private static final String PLAINS = "data/minecraft/worldgen/biome/plains.json";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anchorResolvesOnceOnFirstUse() throws Exception {
        try (URLClassLoader loader = VanillaDataRoots.directory(folder.newFolder("root").toPath(), Map.of(PLAINS, "{}"))) {
            Class<?> anchor = VanillaDataRoots.anchor(loader);
            AtomicInteger resolutions = new AtomicInteger();
            ClasspathVanillaDataSource source = new ClasspathVanillaDataSource(() -> {
                resolutions.incrementAndGet();
                return anchor;
            });
            assertEquals(0, resolutions.get());
            assertTrue(source.read(PLAINS).isPresent());
            assertEquals(List.of(PLAINS), source.list("data/minecraft"));
            assertEquals(1, resolutions.get());
        }
    }

    @Test
    public void unavailableAnchorIsRetriedOnNextUse() throws Exception {
        try (URLClassLoader loader = VanillaDataRoots.directory(folder.newFolder("root").toPath(), Map.of(PLAINS, "{}"))) {
            Class<?> anchor = VanillaDataRoots.anchor(loader);
            AtomicInteger attempts = new AtomicInteger();
            ClasspathVanillaDataSource source = new ClasspathVanillaDataSource(() -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("No server is running");
                }
                return anchor;
            });
            assertThrows(IllegalStateException.class, () -> source.read(PLAINS));
            assertTrue(source.read(PLAINS).isPresent());
        }
    }

    @Test
    public void missingMarkerNamesTheAnchorClass() throws Exception {
        try (URLClassLoader loader = VanillaDataRoots.withoutMarker(folder.newFolder("root").toPath())) {
            Class<?> anchor = VanillaDataRoots.anchor(loader);
            ClasspathVanillaDataSource source = new ClasspathVanillaDataSource(() -> anchor);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> source.read(PLAINS));
            assertTrue(failure.getMessage(), failure.getMessage().contains(anchor.getName()));
        }
    }
}
