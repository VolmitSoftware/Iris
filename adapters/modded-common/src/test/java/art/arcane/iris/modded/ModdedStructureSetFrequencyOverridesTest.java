package art.arcane.iris.modded;

import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.placement.IrisStructureSetFrequencyOverride;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ModdedStructureSetFrequencyOverridesTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void createStateReplacementScalesOnlyTheExactSetAndRetainsEntries() throws Exception {
        Holder<StructureSet> complexes = structureSet(
                "minecraft:nether_complexes", 27, 4, 30084232);
        Holder<StructureSet> fossils = structureSet(
                "minecraft:nether_fossils", 2, 1, 14357921);
        ChunkGeneratorStructureState state = state(List.of(complexes, fossils));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        ChunkGeneratorStructureState returned =
                ModdedStructureSetFrequencyOverrides.apply(state, control);

        assertSame(state, returned);
        List<Holder<StructureSet>> scaledSets = state.possibleStructureSets();
        RandomSpreadStructurePlacement scaledComplexes =
                (RandomSpreadStructurePlacement) scaledSets.get(0).value().placement();
        assertEquals(26, scaledComplexes.spacing());
        assertEquals(4, scaledComplexes.separation());
        assertSame(complexes.value().structures(), scaledSets.get(0).value().structures());
        assertSame(fossils, scaledSets.get(1));
    }

    @Test
    public void unrelatedCustomPlacementTypeRemainsUntouched() {
        Holder<StructureSet> custom = new BoundStructureSetHolder(
                "example:custom",
                new StructureSet(List.of(), new UnsupportedPlacement()));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        List<Holder<StructureSet>> scaled =
                ModdedStructureSetFrequencyOverrides.scaleSets(List.of(custom), control);

        assertSame(custom, scaled.getFirst());
    }

    @Test
    public void unrelatedExclusionCycleRemainsUntouched() {
        BoundStructureSetHolder first = new BoundStructureSetHolder("example:first");
        BoundStructureSetHolder second = new BoundStructureSetHolder("example:second");
        first.bind(new StructureSet(List.of(), placement(32, 8, 1, second)));
        second.bind(new StructureSet(List.of(), placement(40, 10, 2, first)));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        List<Holder<StructureSet>> scaled = ModdedStructureSetFrequencyOverrides.scaleSets(
                List.of(first, second), control);

        assertSame(first, scaled.get(0));
        assertSame(second, scaled.get(1));
    }

    @Test
    public void affectedExclusionCycleUsesBoundScaledHolders() {
        BoundStructureSetHolder first = new BoundStructureSetHolder("example:first");
        BoundStructureSetHolder second = new BoundStructureSetHolder("example:second");
        first.bind(new StructureSet(List.of(), placement(32, 8, 1, second)));
        second.bind(new StructureSet(List.of(), placement(40, 10, 2, first)));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("example:first")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        List<Holder<StructureSet>> scaled = ModdedStructureSetFrequencyOverrides.scaleSets(
                List.of(first, second), control);

        assertEquals(31, ((RandomSpreadStructurePlacement)
                scaled.get(0).value().placement()).spacing());
        assertEquals(40, ((RandomSpreadStructurePlacement)
                scaled.get(1).value().placement()).spacing());
    }

    @Test
    public void affectedRandomSpreadSubclassFailsInsteadOfLosingSubtypeBehavior() {
        Holder<StructureSet> custom = new BoundStructureSetHolder(
                "example:custom",
                new StructureSet(List.of(), new CustomRandomSpreadPlacement()));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("example:custom")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        assertThrows(IllegalStateException.class, () ->
                ModdedStructureSetFrequencyOverrides.scaleSets(List.of(custom), control));
    }

    private static Holder<StructureSet> structureSet(
            String key,
            int spacing,
            int separation,
            int salt
    ) {
        StructureSet value = new StructureSet(
                List.of(),
                new RandomSpreadStructurePlacement(
                        spacing, separation, RandomSpreadType.LINEAR, salt));
        return new BoundStructureSetHolder(key, value);
    }

    private static RandomSpreadStructurePlacement placement(
            int spacing,
            int separation,
            int salt,
            Holder<StructureSet> exclusionTarget
    ) {
        return new RandomSpreadStructurePlacement(
                Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT,
                1F,
                salt,
                Optional.of(new StructurePlacement.ExclusionZone(exclusionTarget, 1)),
                spacing,
                separation,
                RandomSpreadType.LINEAR);
    }

    private static ChunkGeneratorStructureState state(
            List<Holder<StructureSet>> sets
    ) throws Exception {
        Constructor<ChunkGeneratorStructureState> constructor =
                ChunkGeneratorStructureState.class.getDeclaredConstructor(
                        RandomState.class,
                        BiomeSource.class,
                        long.class,
                        long.class,
                        List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                null,
                new EmptyBiomeSource(),
                1L,
                1L,
                sets);
    }

    private static final class BoundStructureSetHolder extends Holder.Reference<StructureSet> {
        private BoundStructureSetHolder(String key) {
            this(key, null);
        }

        private BoundStructureSetHolder(String key, StructureSet value) {
            super(Type.STAND_ALONE, new HolderOwner<>() {
            }, ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(key)), value);
        }

        private void bind(StructureSet value) {
            bindValue(value);
        }
    }

    private static final class EmptyBiomeSource extends BiomeSource {
        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            return null;
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected java.util.stream.Stream<Holder<Biome>> collectPossibleBiomes() {
            return HolderSet.<Biome>empty().stream();
        }
    }

    private static final class UnsupportedPlacement extends StructurePlacement {
        private UnsupportedPlacement() {
            super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1F, 1, Optional.empty());
        }

        @Override
        protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
            return false;
        }

        @Override
        public StructurePlacementType<?> type() {
            return null;
        }
    }

    private static final class CustomRandomSpreadPlacement extends RandomSpreadStructurePlacement {
        private CustomRandomSpreadPlacement() {
            super(27, 4, RandomSpreadType.LINEAR, 30084232);
        }
    }
}
