package art.arcane.iris.probe;

import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.structure.StructureFingerprint;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureFactory;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeStructureOwnershipFingerprint;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import com.mojang.serialization.Lifecycle;
import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.spigotmc.SpigotWorldConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class HeadlessStructurePlannerTest {
    private static final long SEED = 69420L;
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private HolderLookup.Provider lookup;
    private MappedRegistry<Biome> biomes;
    private Holder.Reference<Biome> biome;
    private RandomState random;
    private ChunkGenerator generator;

    @Before
    public void initialize() {
        HeadlessNativeBootstrap.initialize();
        lookup = VanillaRegistries.createWorldLookup();
        biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Biome value = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(
                ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains"))).value();
        biome = biomes.register(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains")),
                value, RegistrationInfo.BUILT_IN);
        biomes.freeze();
        Holder.Reference<NoiseGeneratorSettings> settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        random = RandomState.create(lookup.lookupOrThrow(Registries.NOISE), SEED, settings.value());
        generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
    }

    @Test
    public void weightedFailedTrialsMatchNativeSelectionAndClimateAtNegativeCoordinates() throws Exception {
        generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        List<String> attempts = new ArrayList<>();
        Set<String> climates = new HashSet<>();
        MappedRegistry<Structure> structures = new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        List<StructureSet.StructureSelectionEntry> entries = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            String key = "probe:rejected_" + index;
            Holder.Reference<Structure> holder = structures.register(ResourceKey.create(Registries.STRUCTURE,
                    Identifier.parse(key)), new RejectedStructure(
                    HolderSet.direct(List.copyOf(generator.getBiomeSource().possibleBiomes())), key, attempts), RegistrationInfo.BUILT_IN);
            entries.add(new StructureSet.StructureSelectionEntry(holder, index + 1));
        }
        structures.freeze();
        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, structures));
        ChunkGeneratorStructureState state = state(entries, "probe:weighted", 18373);
        try (MultiPackResourceManager resources = resources();
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(temporary.newFolder().toPath()).createAccess("world")) {
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            HeadlessStructurePlanner.NativeContext context = new HeadlessStructurePlanner.NativeContext(
                    registries, generator, state, templates, Level.OVERWORLD, new WorldOptions(SEED, true, false), true);
            for (ChunkPos position : List.of(new ChunkPos(-17, 31), new ChunkPos(0, 0), new ChunkPos(17, -31))) {
                attempts.clear();
                generator.createStructures(registries, state,
                        new StructureManager(null, new WorldOptions(SEED, true, false), null), chunk(position, registries),
                        templates, Level.OVERWORLD);
                List<String> nativeAttempts = List.copyOf(attempts);
                attempts.clear();
                HeadlessStructurePlanner.generateNaturalStarts(context, chunk(position, registries));
                assertEquals(nativeAttempts, attempts);
                assertEquals(4, attempts.size());
                climates.add(attempts.getFirst().split("\\|", 3)[1]);
            }
            assertTrue(climates.size() > 1);
        }
    }

    @Test
    public void nativePiecesAndConfiguredPlacementSaltArePreserved() throws Exception {
        MappedRegistry<Structure> structures = new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        Holder.Reference<Structure> hut = structures.register(ResourceKey.create(Registries.STRUCTURE,
                Identifier.parse("probe:hut")), new SwampHutStructure(new Structure.StructureSettings(HolderSet.direct(biome))),
                RegistrationInfo.BUILT_IN);
        structures.freeze();
        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, structures));
        ChunkGeneratorStructureState state = state(List.of(new StructureSet.StructureSelectionEntry(hut, 1)),
                "minecraft:villages", 18373);
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) state.possibleStructureSets().getFirst().value().placement();
        try (MultiPackResourceManager resources = resources();
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(temporary.newFolder().toPath()).createAccess("world")) {
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            HeadlessStructurePlanner.NativeContext context = new HeadlessStructurePlanner.NativeContext(
                    registries, generator, state, templates, Level.OVERWORLD, new WorldOptions(SEED, true, false), true);
            ChunkPos position = placement.getPotentialStructureChunk(SEED, -33, 17);
            ProtoChunk chunk = chunk(position, registries);
            HeadlessStructurePlanner.generateNaturalStarts(context, chunk);
            StructureStart expected = hut.value().generate(hut, Level.OVERWORLD, registries, generator,
                    generator.getBiomeSource(), random.createClimateSampler(SamplerContext.builder().enableCaches().build()),
                    random, templates, SEED, position, 0, chunk, hut.value().biomes()::contains);
            assertTrue(expected.isValid());
            StructurePieceSerializationContext serialization = new StructurePieceSerializationContext(resources, registries, templates);
            CompoundTag expectedTag = expected.createTag(serialization, position);
            assertEquals(expectedTag, chunk.getStartForStructure(hut.value()).createTag(serialization, position));
            RandomSpreadStructurePlacement configured = new RandomSpreadStructurePlacement(7, 1, RandomSpreadType.LINEAR, 17);
            assertEquals(configured.getPotentialStructureChunk(SEED, -33, 17), position);
            HeadlessStructurePlanner.NativeContext disabled = new HeadlessStructurePlanner.NativeContext(
                    registries, generator, state, templates, Level.OVERWORLD, new WorldOptions(SEED, false, false), true);
            ProtoChunk empty = chunk(position, registries);
            HeadlessStructurePlanner.generateNaturalStarts(disabled, empty);
            assertTrue(empty.getAllStarts().isEmpty());
        }
    }

    private ChunkGeneratorStructureState state(List<StructureSet.StructureSelectionEntry> entries, String key, int salt) {
        MappedRegistry<StructureSet> sets = new MappedRegistry<>(Registries.STRUCTURE_SET, Lifecycle.stable());
        sets.register(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(key)), new StructureSet(entries,
                new RandomSpreadStructurePlacement(key.equals("minecraft:villages") ? 7 : 1,
                        key.equals("minecraft:villages") ? 1 : 0, RandomSpreadType.LINEAR, salt)), RegistrationInfo.BUILT_IN);
        sets.freeze();
        return ChunkGeneratorStructureState.createForNormal(random, SEED, generator.getOrigin(random),
                generator.getBiomeSource(), sets, configuration());
    }

    @Test
    public void configuredStartsKeepOwnershipTerrainAndReferencesBeyondPieceBounds() throws Exception {
        MappedRegistry<Structure> structures = new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        Holder.Reference<Structure> hut = structures.register(ResourceKey.create(Registries.STRUCTURE,
                Identifier.parse("probe:hut")), new SwampHutStructure(new Structure.StructureSettings(HolderSet.direct(biome))),
                RegistrationInfo.BUILT_IN);
        structures.freeze();
        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, structures));
        MappedRegistry<StructureSet> sets = new MappedRegistry<>(Registries.STRUCTURE_SET, Lifecycle.stable());
        sets.freeze();
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(random, SEED,
                generator.getOrigin(random), generator.getBiomeSource(), sets, configuration());
        IrisStructureTerrain terrain = new IrisStructureTerrain().setMode(StructureTerrainMode.BORE).setHorizontalPadding(24);
        NativeStructureStartPlan plan = new NativeStructureStartPlan(
                new IrisStructurePlacement().setUnderground(true).setTerrain(terrain),
                new IrisNativeStructure().setStructure("probe:hut"), -1, 0, 30);
        TestPolicy policy = new TestPolicy(plan);
        HeadlessStructurePlanner.Policies<NativeStructureStartPlan, NativeStructureOwnershipRecord> policies =
                new HeadlessStructurePlanner.Policies<>(policy, policy, footprint -> true);
        try (MultiPackResourceManager resources = resources();
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(temporary.newFolder().toPath()).createAccess("world")) {
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            HeadlessStructurePlanner.NativeContext context = new HeadlessStructurePlanner.NativeContext(
                    registries, generator, state, templates, Level.OVERWORLD, new WorldOptions(SEED, true, false), true);
            Map<Long, HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord>> grid = new HashMap<>();
            for (int x = -10; x <= 6; x++) {
                for (int z = -8; z <= 8; z++) {
                    ChunkPos position = new ChunkPos(x, z);
                    grid.put(position.pack(), HeadlessStructurePlanner.plan(context, policies, position));
                }
            }
            HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> origin = grid.get(new ChunkPos(-1, 0).pack());
            StructureStart actual = origin.chunk().getStartForStructure(hut.value());
            StructureStart expected = NativeStructureFactory.generate(new NativeStructureFactory.GenerationContext(
                    registries, generator, generator.getBiomeSource(), random, templates, SEED, Level.OVERWORLD,
                    origin.chunk(), selected -> true, generator.getSeaLevel(), policy::surfaceHeight), hut, plan, 0);
            StructurePieceSerializationContext serialization = new StructurePieceSerializationContext(resources, registries, templates);
            assertEquals(expected.createTag(serialization, origin.chunk().getPos()), actual.createTag(serialization, origin.chunk().getPos()));
            assertEquals(30, actual.getBoundingBox().minY());
            assertTrue(NativeStructureOwnershipFingerprint.matches(origin.ownership().get(hut.value()), actual));
            assertEquals(StructureTerrainMode.BORE, origin.terrain().get(hut.value()).resolvedMode());
            HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> target = grid.get(new ChunkPos(-2, 0).pack());
            assertFalse(actual.getBoundingBox().intersects(-32, 0, -17, 15));
            Map<Long, HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord>> incomplete = new HashMap<>(grid);
            incomplete.remove(new ChunkPos(-10, -8).pack());
            assertThrows(IllegalArgumentException.class, () -> HeadlessStructurePlanner.references(policies, target, incomplete));
            assertTrue(target.chunk().getAllReferences().isEmpty());
            HeadlessStructurePlanner.references(policies, target, grid);
            assertTrue(target.chunk().getAllReferences().get(hut.value()).contains(new ChunkPos(-1, 0).pack()));
            assertEquals(ChunkStatus.STRUCTURE_REFERENCES, target.chunk().getPersistedStatus());
            HeadlessStructurePlanner.Policies<NativeStructureStartPlan, NativeStructureOwnershipRecord> excluded =
                    new HeadlessStructurePlanner.Policies<>(policy, policy, footprint -> false);
            HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> rejected = HeadlessStructurePlanner.plan(
                    context, excluded, new ChunkPos(-1, 0));
            assertFalse(rejected.chunk().getStartForStructure(hut.value()).isValid());
            assertTrue(rejected.ownership().isEmpty());
            assertTrue(rejected.terrain().isEmpty());
            assertEquals(1, policy.discards);
            policy.hideOwnership = true;
            assertThrows(IllegalStateException.class, () -> HeadlessStructurePlanner.plan(context, policies, new ChunkPos(-1, 0)));
            assertEquals(2, policy.discards);
            for (boolean adapterEnabled : new boolean[]{true, false}) {
                HeadlessStructurePlanner.NativeContext disabled = new HeadlessStructurePlanner.NativeContext(
                        registries, generator, state, templates, Level.OVERWORLD,
                        new WorldOptions(SEED, !adapterEnabled, false), adapterEnabled);
                HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> empty = HeadlessStructurePlanner.plan(
                        disabled, policies, new ChunkPos(-1, 0));
                assertTrue(empty.chunk().getAllStarts().isEmpty());
                assertTrue(empty.ownership().isEmpty());
                if (!adapterEnabled) {
                    int scopes = policy.scopes;
                    HeadlessStructurePlanner.references(policies, empty, Map.of());
                    assertEquals(scopes, policy.scopes);
                    assertTrue(empty.chunk().getAllReferences().isEmpty());
                    assertEquals(ChunkStatus.STRUCTURE_REFERENCES, empty.chunk().getPersistedStatus());
                }
            }
            assertEquals(2, policy.discards);
            assertThrows(IllegalArgumentException.class, () -> new HeadlessStructurePlanner.NativeContext(
                    registries, generator, state, templates, Level.OVERWORLD,
                    new WorldOptions(SEED + 1, true, false), true));
        }
    }

    private static SpigotWorldConfig configuration() {
        return new SpigotWorldConfig("probe", Key.key("probe:world")) {
            @Override
            public void init() {
                villageSeed = 17;
            }
        };
    }

    private static final class TestPolicy implements StructureInjectionPolicy<NativeStructureStartPlan>,
            StructureReferencePolicy<NativeStructureStartPlan, NativeStructureOwnershipRecord> {
        private final NativeStructureStartPlan plan;
        private NativeStructureOwnershipRecord ownership;
        private boolean hideOwnership;
        private int discards;
        private int scopes;

        private TestPolicy(NativeStructureStartPlan plan) { this.plan = plan; }
        @Override public List<NativeStructureStartPlan> plansAt(int x, int z) {
            return x == plan.chunkX() && z == plan.chunkZ() ? List.of(plan) : List.of();
        }
        @Override public boolean sourceReplaced(String key, boolean underground) { return false; }
        @Override public int surfaceHeight(int x, int z) { return 80; }
        @Override public void record(NativeStructureStartPlan selected, StructureFingerprint fingerprint) {
            ownership = NativeStructureOwnershipRecord.capture(fingerprint, selected);
        }
        @Override public void discard(String key, int x, int z) { ownership = null; discards++; }
        @Override public void duplicate(String key) { throw new AssertionError(key); }
        @Override public NativeStructureStartPlan matchingPlan(String key, int x, int z) {
            return plansAt(x, z).stream().findFirst().orElse(null);
        }
        @Override public NativeStructureOwnershipRecord findPersisted(String key, int x, int z) {
            return !hideOwnership && ownership != null && ownership.structureKey().equals(key) && ownership.originChunkX() == x
                    && ownership.originChunkZ() == z ? ownership : null;
        }
        @Override public NativeStructureOwnershipRecord capture(StructureFingerprint fingerprint, NativeStructureStartPlan selected) {
            return NativeStructureOwnershipRecord.capture(fingerprint, selected);
        }
        @Override public void record(NativeStructureOwnershipRecord record) { ownership = record; }
        @Override public NativeGenerationScope openOriginScope(int x, int z) { scopes++; return () -> {}; }
        @Override public StructurePlacementDecision resolve(String key, boolean underground) {
            return new IrisNativeStructureDecision(NativeStructureGenerationStatus.GENERATE_NATIVE, 0, null,
                    true, null, new IrisStructureTerrain());
        }
        @Override public void invalidated(String key) { throw new AssertionError(key); }
    }

    private static ProtoChunk chunk(ChunkPos position, RegistryAccess registries) {
        return new ProtoChunk(position, UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                PalettedContainerFactory.create(registries), null);
    }

    private static MultiPackResourceManager resources() {
        return new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
    }

    private static final class RejectedStructure extends Structure {
        private final String key;
        private final List<String> attempts;

        private RejectedStructure(HolderSet<Biome> biomes, String key, List<String> attempts) {
            super(new StructureSettings(biomes));
            this.key = key;
            this.attempts = attempts;
        }

        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            int x = context.chunkPos().getMinBlockX() >> 2;
            int z = context.chunkPos().getMinBlockZ() >> 2;
            attempts.add(key + "|" + context.climateSampler().sample(x, 20, z)
                    + "|" + context.biomeResolver().getNoiseBiome(x, 20, z).unwrapKey().orElseThrow());
            return Optional.empty();
        }

        @Override
        public StructureType<?> type() {
            return StructureType.SWAMP_HUT;
        }
    }
}
