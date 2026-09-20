package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;
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
