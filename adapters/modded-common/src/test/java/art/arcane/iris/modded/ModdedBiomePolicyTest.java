package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedBiomePolicyTest {
    @Test
    public void registryOrderAndHolderIdentitySurviveThePolicyBoundaryAndRefreshOnRepoint() {
        BiomeToken ocean = new BiomeToken("minecraft:ocean");
        BiomeToken forest = new BiomeToken("minecraft:forest");
        List<BiomeToken> entries = new ArrayList<>(List.of(forest, ocean));
        AtomicReference<Set<String>> configured = new AtomicReference<>(Set.of(ocean.key(), forest.key()));
        ModdedBiomePolicy<BiomeToken, Object> policy = policy(entries, configured);

        List<BiomeToken> first = policy.orderedPossibleBiomes();
        assertSame(forest, first.getFirst());
        assertSame(ocean, first.getLast());
        assertEquals(first, new ArrayList<>(policy.possibleBiomes()));

        BiomeToken desert = new BiomeToken("minecraft:desert");
        entries.clear();
        entries.add(desert);
        configured.set(Set.of(desert.key()));
        assertSame(first, policy.orderedPossibleBiomes());

        policy.clearCaches();

        assertEquals(1L, policy.packGeneration());
        assertEquals(1, policy.orderedPossibleBiomes().size());
        assertSame(desert, policy.orderedPossibleBiomes().getFirst());
    }

    @Test
    public void missingRequiredStructureBiomesFailBeforePublishingAPartialSet() {
        AtomicReference<Set<String>> configured = new AtomicReference<>(Set.of("example:missing"));
        ModdedBiomePolicy<BiomeToken, Object> policy = policy(
                new ArrayList<>(List.of(new BiomeToken("minecraft:plains"))), configured);

        IllegalStateException failure = assertThrows(IllegalStateException.class, policy::possibleBiomes);

        assertTrue(failure.getMessage().contains("example:missing"));
    }

    @Test
    public void structureRingBiomesResolveFromTheNaturalSurfaceWithoutPlanningHydrology() throws Exception {
        BiomeToken forest = new BiomeToken("minecraft:forest");
        IrisBiome natural = mock(IrisBiome.class);
        when(natural.getStructureDerivativeKey()).thenReturn(forest.key());
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(
                ProceduralStream.of((x, z) -> natural, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getTrueBiomeStream()).thenThrow(
                new AssertionError("Stronghold ring lookups must not plan hydrology"));
        Engine engine = mock(Engine.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(
                new Access(new ArrayList<>(List.of(forest))));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> engine, () -> engine, bound -> true,
                () -> Set.of(forest.key()), bound -> Set.of(), () -> 0L));

        assertSame(forest, policy.requiredStructureBiome(6000, 0, -6000, null));
        assertSame(forest, policy.requiredStructureBiome(6000, -30, -6000, null));
    }

    /**
     * Stacked initial mob spawning asks for the visible surface biome. A null answer made it spawn from the chunk's
     * top biome instead, so a sealed or unready engine has to fail the chunk.
     */
    @Test
    public void surfaceSpawnBiomeRefusesAnEngineInTransitionInsteadOfAnsweringNothing() throws Exception {
        Engine sealed = mock(Engine.class);
        when(sealed.isClosing()).thenReturn(true);
        when(sealed.acquireGenerationLease(anyString())).thenThrow(new GenerationSessionException("sealed", true));
        Engine unready = mock(Engine.class);
        when(unready.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());

        assertThrows(IllegalStateException.class, () -> boundTo(sealed).getVisibleSurfaceBiome(8, 8));
        assertThrows(IllegalStateException.class, () -> boundTo(unready).getVisibleSurfaceBiome(8, 8));
    }

    private static ModdedBiomePolicy<BiomeToken, Object> boundTo(Engine engine) {
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(new Access(new ArrayList<>()));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> engine, () -> engine, bound -> true,
                Set::of, bound -> Set.of(), () -> 0L));
        return policy;
    }

    private static ModdedBiomePolicy<BiomeToken, Object> policy(List<BiomeToken> entries,
                                                               AtomicReference<Set<String>> configured) {
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(new Access(entries));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> null, () -> null, engine -> false,
                configured::get, engine -> Set.of(), () -> 0L));
        return policy;
    }

    private record BiomeToken(String key) {
    }

    private record Access(List<BiomeToken> entries) implements NativeBiomeSourceAccess<BiomeToken, Object>,
            NativeBiomeSourceAccess.RegistryView<BiomeToken> {
        @Override
        public RegistryView<BiomeToken> registry() {
            return this;
        }

        @Override
        public Set<BiomeToken> serializedBiomes() {
            throw new AssertionError("A loaded registry must not use serialized biomes");
        }

        @Override
        public BiomeToken serializedNoise(int x, int y, int z, Object sampler) {
            throw new AssertionError("Listing biome holders must not sample terrain");
        }

        @Override
        public String holderKey(BiomeToken holder) {
            return holder.key();
        }

        @Override
        public void forEach(Consumer<? super BiomeToken> consumer) {
            entries.forEach(consumer);
        }

        @Override
        public BiomeToken lookup(String key) {
            for (BiomeToken biome : entries) {
                if (biome.key().equals(key)) {
                    return biome;
                }
            }
            return null;
        }
    }
}
