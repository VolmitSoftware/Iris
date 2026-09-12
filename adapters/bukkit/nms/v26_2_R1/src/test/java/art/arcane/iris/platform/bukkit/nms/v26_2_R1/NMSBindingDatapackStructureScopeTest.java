package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.iris.pack.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.placement.IrisStructureSetFrequencyOverride;
import art.arcane.volmlib.util.collection.KList;
import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.junit.Test;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class NMSBindingDatapackStructureScopeTest {
    private static final String SOURCE = "https://example.test/managed.zip";

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void managedSetContainingVanillaStructureIsAbsentOutsideDeclaringDimension() {
        Holder<Structure> vanillaStructure = structureHolder("minecraft:pillager_outpost");
        Holder<StructureSet> managedSet = structureSetHolder(
                "managed:illager_barracks", vanillaStructure);
        DatapackStructureScopeIndex index = index(
                List.of(),
                List.of("managed:illager_barracks"));

        DatapackStructureStateFilter.Selection vanilla = DatapackStructureStateFilter.filter(
                List.of(managedSet), index, Set.of());
        DatapackStructureStateFilter.Selection declaring = DatapackStructureStateFilter.filter(
                List.of(managedSet), index, index.declaredSources(List.of(SOURCE)));

        assertEquals(0, vanilla.structureSets().size());
        assertEquals(1, vanilla.excludedManagedSets());
        assertEquals(1, declaring.structureSets().size());
        assertSame(managedSet, declaring.structureSets().getFirst());
    }

    @Test
    public void unmanagedSetRetainsOnlyDefinitionsAllowedInTheWorld() {
        Holder<Structure> vanillaStructure = structureHolder("minecraft:village_plains");
        Holder<Structure> managedStructure = structureHolder("managed:tavern");
        Holder<StructureSet> vanillaSet = structureSetHolder(
                "minecraft:villages", vanillaStructure, managedStructure);
        DatapackStructureScopeIndex index = index(
                List.of("managed:tavern"),
                List.of());

        DatapackStructureStateFilter.Selection vanilla = DatapackStructureStateFilter.filter(
                List.of(vanillaSet), index, Set.of());
        DatapackStructureStateFilter.Selection declaring = DatapackStructureStateFilter.filter(
                List.of(vanillaSet), index, index.declaredSources(List.of(SOURCE)));

        assertEquals(1, vanilla.structureSets().size());
        assertEquals(1, vanilla.structureSets().getFirst().value().structures().size());
        assertSame(vanillaStructure,
                vanilla.structureSets().getFirst().value().structures().getFirst().structure());
        assertSame(vanillaSet.value().placement(),
                vanilla.structureSets().getFirst().value().placement());
        assertSame(vanillaSet, declaring.structureSets().getFirst());
    }

    @Test
    public void setWithNoAllowedDefinitionsIsRemoved() {
        Holder<StructureSet> unmanagedSet = structureSetHolder(
                "minecraft:custom", structureHolder("managed:only"));
        DatapackStructureScopeIndex index = index(List.of("managed:only"), List.of());

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(unmanagedSet), index, Set.of());

        assertEquals(0, selection.structureSets().size());
    }

    @Test
    public void spigotDirectHolderRecoversItsRegistryKeyFromSharedEntries() {
        ResourceKey<StructureSet> key = ResourceKey.create(
                Registries.STRUCTURE_SET,
                Identifier.parse("managed:illager_barracks"));
        List<StructureSet.StructureSelectionEntry> entries = List.of(
                new StructureSet.StructureSelectionEntry(
                        structureHolder("minecraft:pillager_outpost"), 1));
        Holder<StructureSet> registered = new KeyedHolder<>(key, new StructureSet(
                entries,
                new RandomSpreadStructurePlacement(
                        34, 8, RandomSpreadType.LINEAR, 10387312)));
        Holder<StructureSet> spigotDirect = Holder.direct(new StructureSet(
                entries,
                new RandomSpreadStructurePlacement(
                        34, 8, RandomSpreadType.LINEAR, 14357620)));
        DatapackStructureScopeIndex scopeIndex = index(
                List.of(), List.of("managed:illager_barracks"));
        DatapackStructureStateFilter.StructureSetKeyIndex keyIndex =
                DatapackStructureStateFilter.keyIndex(List.of(registered));

        DatapackStructureStateFilter.Selection excluded =
                DatapackStructureStateFilter.filter(
                        List.of(spigotDirect),
                        scopeIndex,
                        Set.of(),
                        new IrisImportedStructureControl(),
                        keyIndex);
        DatapackStructureStateFilter.Selection retained =
                DatapackStructureStateFilter.filter(
                        List.of(spigotDirect),
                        scopeIndex,
                        scopeIndex.declaredSources(List.of(SOURCE)),
                        new IrisImportedStructureControl(),
                        keyIndex);

        assertEquals(0, excluded.structureSets().size());
        assertEquals(1, excluded.excludedManagedSets());
        assertSame(spigotDirect, retained.structureSets().getFirst());
        assertEquals(1, retained.retainedManagedSets());
    }

    @Test
    public void structureScopeDoesNotLinkPaperOnlyPlacementClasses() throws IOException {
        InputStream classResource = NMSBindingDatapackStructureScopeTest.class
                .getResourceAsStream("DatapackStructureStateFilter.class");
        assertNotNull(classResource);
        try (InputStream input = classResource) {
            String classFile = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(classFile.contains("KeyedRandomSpreadStructurePlacement"));
        }
    }

    @Test
    public void exactFrequencyOverridesScaleOnlyTheNamedNativeStructureSet() {
        Holder<StructureSet> complexes = structureSetHolder(
                "minecraft:nether_complexes",
                new RandomSpreadStructurePlacement(27, 4, RandomSpreadType.LINEAR, 30084232),
                structureHolder("minecraft:fortress"));
        Holder<StructureSet> fossils = structureSetHolder(
                "minecraft:nether_fossils",
                new RandomSpreadStructurePlacement(2, 1, RandomSpreadType.LINEAR, 14357921),
                structureHolder("minecraft:nether_fossil"));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(complexes, fossils), index(List.of(), List.of()), Set.of(), control);

        RandomSpreadStructurePlacement scaledComplexes =
                (RandomSpreadStructurePlacement) selection.structureSets().get(0).value().placement();
        RandomSpreadStructurePlacement unchangedFossils =
                (RandomSpreadStructurePlacement) selection.structureSets().get(1).value().placement();
        assertEquals(26, scaledComplexes.spacing());
        assertEquals(4, scaledComplexes.separation());
        assertEquals(2, unchangedFossils.spacing());
        assertSame(fossils, selection.structureSets().get(1));
        assertEquals(27, ((RandomSpreadStructurePlacement) complexes.value().placement()).spacing());
    }

    @Test
    public void frequencyOnlyPassLeavesUnrelatedCustomPlacementUntouched() {
        Holder<StructureSet> exclusionTarget = structureSetHolder(
                "example:target", structureHolder("example:target"));
        Holder<StructureSet> custom = structureSetHolder(
                "example:custom",
                new UnsupportedPlacement(Optional.of(
                        new StructurePlacement.ExclusionZone(exclusionTarget, 1))),
                structureHolder("example:custom"));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("minecraft:nether_complexes")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(custom), index(List.of(), List.of()), Set.of(), control);

        assertSame(custom, selection.structureSets().getFirst());
    }

    @Test
    public void frequencyOnlyPassRebindsDependentExclusionTarget() {
        Holder<StructureSet> target = structureSetHolder(
                "example:target",
                new RandomSpreadStructurePlacement(27, 4, RandomSpreadType.LINEAR, 30084232),
                structureHolder("example:target"));
        RandomSpreadStructurePlacement dependentPlacement = new RandomSpreadStructurePlacement(
                Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT,
                1.0F,
                4567,
                Optional.of(new StructurePlacement.ExclusionZone(target, 1)),
                32,
                8,
                RandomSpreadType.LINEAR);
        Holder<StructureSet> dependent = structureSetHolder(
                "example:dependent",
                dependentPlacement,
                structureHolder("example:dependent"));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("example:target")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(dependent, target), index(List.of(), List.of()), Set.of(), control);

        Holder<StructureSet> scaledDependent = selection.structureSets().get(0);
        Holder<StructureSet> scaledTarget = selection.structureSets().get(1);
        assertNotSame(dependent, scaledDependent);
        assertNotSame(target, scaledTarget);
        assertSame(scaledTarget, DatapackStructureStateFilter.exclusionZone(
                scaledDependent.value().placement()).orElseThrow().otherSet());
        assertEquals(26, ((RandomSpreadStructurePlacement)
                scaledTarget.value().placement()).spacing());
    }

    @Test
    public void frequencyOnlyPassRebindsAffectedExclusionCycle() {
        MutableStructureSetHolder first = new MutableStructureSetHolder("example:first");
        MutableStructureSetHolder second = new MutableStructureSetHolder("example:second");
        first.bind(structureSet(
                new RandomSpreadStructurePlacement(
                        Vec3i.ZERO,
                        StructurePlacement.FrequencyReductionMethod.DEFAULT,
                        1.0F,
                        101,
                        Optional.of(new StructurePlacement.ExclusionZone(second, 1)),
                        32,
                        8,
                        RandomSpreadType.LINEAR),
                "example:first"));
        second.bind(structureSet(
                new RandomSpreadStructurePlacement(
                        Vec3i.ZERO,
                        StructurePlacement.FrequencyReductionMethod.DEFAULT,
                        1.0F,
                        102,
                        Optional.of(new StructurePlacement.ExclusionZone(first, 1)),
                        27,
                        4,
                        RandomSpreadType.LINEAR),
                "example:second"));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("example:second")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(first, second), index(List.of(), List.of()), Set.of(), control);

        Holder<StructureSet> scaledFirst = selection.structureSets().get(0);
        Holder<StructureSet> scaledSecond = selection.structureSets().get(1);
        assertSame(scaledSecond, DatapackStructureStateFilter.exclusionZone(
                scaledFirst.value().placement()).orElseThrow().otherSet());
        assertSame(scaledFirst, DatapackStructureStateFilter.exclusionZone(
                scaledSecond.value().placement()).orElseThrow().otherSet());
        assertEquals(26, ((RandomSpreadStructurePlacement)
                scaledSecond.value().placement()).spacing());
    }

    @Test
    public void randomSpreadSubtypeUsesTheCanonicalPlacementContract() {
        Holder<StructureSet> custom = structureSetHolder(
                "example:custom",
                new CustomRandomSpreadPlacement(),
                structureHolder("example:custom"));
        KList<IrisStructureSetFrequencyOverride> overrides = new KList<>();
        overrides.add(new IrisStructureSetFrequencyOverride()
                .setStructureSet("example:custom")
                .setMultiplier(1.1D));
        IrisImportedStructureControl control = new IrisImportedStructureControl()
                .setFrequencyOverrides(overrides);

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(custom), index(List.of(), List.of()), Set.of(), control);
        StructurePlacement placement = selection.structureSets().getFirst().value().placement();

        assertEquals(RandomSpreadStructurePlacement.class, placement.getClass());
        assertEquals(26, ((RandomSpreadStructurePlacement) placement).spacing());
    }

    @Test
    public void excludedManagedSetCannotSuppressAnAllowedSetThroughExclusionZone() {
        Holder<StructureSet> managedSet = structureSetHolder(
                "managed:blocked",
                structureHolder("managed:blocked"));
        RandomSpreadStructurePlacement originalPlacement = new RandomSpreadStructurePlacement(
                Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT,
                1.0F,
                4567,
                Optional.of(new StructurePlacement.ExclusionZone(managedSet, 1)),
                32,
                8,
                RandomSpreadType.LINEAR);
        Holder<StructureSet> vanillaSet = structureSetHolder(
                "minecraft:allowed",
                originalPlacement,
                structureHolder("minecraft:village_plains"));
        DatapackStructureScopeIndex index = index(
                List.of("managed:blocked"),
                List.of("managed:blocked"));

        DatapackStructureStateFilter.Selection selection = DatapackStructureStateFilter.filter(
                List.of(vanillaSet, managedSet), index, Set.of());

        assertEquals(1, selection.structureSets().size());
        StructurePlacement scopedPlacement = selection.structureSets().getFirst().value().placement();
        assertEquals(0, DatapackStructureStateFilter.exclusionZone(scopedPlacement).stream().count());
    }

    private static DatapackStructureScopeIndex index(
            List<String> structureKeys,
            List<String> structureSetKeys
    ) {
        return DatapackStructureScopeIndex.create(List.of(
                new DatapackIngestService.StructureScopeResources(
                        SOURCE,
                        structureKeys,
                        structureSetKeys)));
    }

    private static Holder<Structure> structureHolder(String key) {
        return new KeyedHolder<>(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(key)), null);
    }

    private static Holder<StructureSet> structureSetHolder(
            String key,
            Holder<Structure>... structures
    ) {
        return structureSetHolder(
                key,
                new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 12345),
                structures);
    }

    private static Holder<StructureSet> structureSetHolder(
            String key,
            StructurePlacement placement,
            Holder<Structure>... structures
    ) {
        List<StructureSet.StructureSelectionEntry> entries = Stream.of(structures)
                .map(structure -> new StructureSet.StructureSelectionEntry(structure, 1))
                .toList();
        StructureSet value = new StructureSet(entries, placement);
        return new KeyedHolder<>(
                ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(key)),
                value);
    }

    private static StructureSet structureSet(StructurePlacement placement, String structureKey) {
        return new StructureSet(
                List.of(new StructureSet.StructureSelectionEntry(
                        structureHolder(structureKey), 1)),
                placement);
    }

    private static final class KeyedHolder<T> implements Holder<T> {
        private final ResourceKey<T> key;
        private final T value;

        private KeyedHolder(ResourceKey<T> key, T value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean areComponentsBound() {
            return true;
        }

        @Override
        public boolean is(Identifier identifier) {
            return key.identifier().equals(identifier);
        }

        @Override
        public boolean is(ResourceKey<T> candidate) {
            return key.equals(candidate);
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return predicate.test(key);
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return false;
        }

        @Override
        public boolean is(Holder<T> holder) {
            return holder == this;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.empty();
        }

        @Override
        public DataComponentMap components() {
            return DataComponentMap.EMPTY;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(key);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(key);
        }

        @Override
        public Kind kind() {
            return Kind.REFERENCE;
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> owner) {
            return true;
        }
    }

    private static final class UnsupportedPlacement extends StructurePlacement {
        private UnsupportedPlacement(Optional<ExclusionZone> exclusionZone) {
            super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1F, 1, exclusionZone);
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

    private static final class MutableStructureSetHolder extends Holder.Reference<StructureSet> {
        private MutableStructureSetHolder(String key) {
            super(Type.STAND_ALONE, new HolderOwner<>() {
            }, ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(key)), null);
        }

        private void bind(StructureSet structureSet) {
            bindValue(structureSet);
        }
    }

    private static final class CustomRandomSpreadPlacement extends RandomSpreadStructurePlacement {
        private CustomRandomSpreadPlacement() {
            super(27, 4, RandomSpreadType.LINEAR, 30084232);
        }
    }
}
