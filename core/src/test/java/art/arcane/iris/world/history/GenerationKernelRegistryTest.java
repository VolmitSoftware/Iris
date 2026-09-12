package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.SeedManager;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class GenerationKernelRegistryTest {
    @Test
    public void selectsAnExactImplementationFingerprint() throws Exception {
        GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(7, 3, 2);
        String fingerprint = "a".repeat(64);
        GenerationKernelRegistry registry = registry(version, fingerprint);

        GenerationKernelRegistry.RuntimeKernel runtime = registry.select(version);

        assertEquals(fingerprint, runtime.implementationFingerprint());
        assertEquals(fingerprint, registry.requireSupported(version, fingerprint).implementationFingerprint());
    }

    @Test
    public void algorithmTupleSelectsItsOwnSeedFactory() throws Exception {
        SeedManager first = mock(SeedManager.class);
        SeedManager second = mock(SeedManager.class);
        GenerationKernelRegistry.Version original = new GenerationKernelRegistry.Version(1, 1, 1);
        GenerationKernelRegistry.Version changed = new GenerationKernelRegistry.Version(1, 2, 3);
        GenerationKernelRegistry registry = new GenerationKernelRegistry(changed, Set.of(
                new GenerationKernelRegistry.Kernel(1, "a".repeat(64), Map.of(
                        new GenerationKernelRegistry.AlgorithmVersion(1, 1), seedFactory(first),
                        new GenerationKernelRegistry.AlgorithmVersion(2, 3), seedFactory(second)))));

        assertSame(first, registry.select(original).createSeedManager(991L));
        assertSame(second, registry.select(changed).createSeedManager(991L));
    }

    @Test
    public void missingSeedFactoryFailsClosed() throws Exception {
        GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(7, 3, 2);
        assertThrows(UnsupportedOperationException.class,
                () -> registry(version, "a".repeat(64)).select(version).createSeedManager(991L));
    }

    @Test
    public void rejectsAnEpochWhoseImplementationChangedWithoutAVersionChange() {
        GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(7, 3, 2);
        GenerationKernelRegistry registry = registry(version, "a".repeat(64));

        assertThrows(IOException.class, () -> registry.requireSupported(version, "b".repeat(64)));
    }

    @Test
    public void rejectsMalformedImplementationFingerprints() {
        GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(7, 3, 2);

        assertThrows(IllegalArgumentException.class, () -> registry(version, "not-a-sha256"));
    }

    private static GenerationKernelRegistry registry(
            GenerationKernelRegistry.Version version,
            String fingerprint
    ) {
        return new GenerationKernelRegistry(
                version,
                Set.of(new GenerationKernelRegistry.Kernel(
                        version.generatorAbi(),
                        fingerprint,
                        Map.of(
                                new GenerationKernelRegistry.AlgorithmVersion(
                                        version.rngVersion(),
                                        version.seedDerivationVersion()
                                ),
                                (engine, transitionPlan, detached) -> mock(IrisComplex.class)
                        )
                ))
        );
    }

    private static GenerationKernelRegistry.RuntimeFactory seedFactory(SeedManager seedManager) {
        return new GenerationKernelRegistry.RuntimeFactory() {
            @Override
            public IrisComplex create(IrisEngine engine, TransitionGenerationPlan transitionPlan, boolean detached) {
                return mock(IrisComplex.class);
            }

            @Override
            public SeedManager createSeedManager(long worldSeed) {
                assertEquals(991L, worldSeed);
                return seedManager;
            }
        };
    }
}
