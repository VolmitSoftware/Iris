package art.arcane.iris.world.history;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public final class GenerationHistoryPathsTest {
    @Test
    public void derivesTheCanonicalDimensionHistoryLayout() {
        Path dimensionRoot = Path.of("build", "world", "..", "world");
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(dimensionRoot);
        Path expectedRoot = dimensionRoot.toAbsolutePath().normalize();
        String epochId = "a".repeat(64);

        assertEquals(expectedRoot, paths.dimensionRoot());
        assertEquals(expectedRoot.resolve("iris"), paths.irisRoot());
        assertEquals(expectedRoot.resolve("iris/generation"), paths.generationRoot());
        assertEquals(expectedRoot.resolve("iris/generation/manifest.json"), paths.manifest());
        assertEquals(expectedRoot.resolve("iris/generation/epochs"), paths.epochsRoot());
        assertEquals(expectedRoot.resolve("iris/generation/activations"), paths.activationsRoot());
        assertEquals(expectedRoot.resolve("iris/generation/ownership"), paths.ownershipRoot());
        assertEquals(expectedRoot.resolve("iris/generation/epochs").resolve(epochId), paths.epochRoot(epochId));
        assertEquals(expectedRoot.resolve("iris/generation/epochs").resolve(epochId).resolve("pack"),
                paths.packRoot(epochId));
        assertEquals(expectedRoot.resolve("iris/generation/activations/2"), paths.activationRoot(2L));
        assertEquals(expectedRoot.resolve("iris/generation/activations/2/mantle-hydrology"),
                paths.activationMantleRoot(2L));
        assertEquals(expectedRoot.resolve("iris/pack"), paths.legacyPackRoot());
        assertEquals(expectedRoot.resolve("mantle-hydrology"), paths.legacyMantleRoot());
        assertEquals(expectedRoot.resolve("region"), paths.regionRoot());
        assertThrows(IllegalArgumentException.class, () -> paths.epochRoot("../escape"));
        assertThrows(IllegalArgumentException.class, () -> paths.activationRoot(0L));
    }

    @Test
    public void repeatedPackRootsStayValidatedAndFollowTheRequestedEpoch() {
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(Path.of("build", "world"));
        String first = "a".repeat(64);
        String second = "b".repeat(64);

        Path firstRoot = paths.packRoot(first);
        assertSame(firstRoot, paths.packRoot(first));
        assertEquals(paths.epochRoot(second).resolve("pack"), paths.packRoot(second));
        assertEquals(firstRoot, paths.packRoot(first));
        assertThrows(IllegalArgumentException.class, () -> paths.packRoot("../escape"));
        assertThrows(NullPointerException.class, () -> paths.packRoot(null));
    }
}
