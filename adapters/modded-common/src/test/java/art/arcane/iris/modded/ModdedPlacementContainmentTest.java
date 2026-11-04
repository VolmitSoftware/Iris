package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeFeatureBiomeSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedImportedFeatures;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureOwnershipRecovery;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructurePostProcessor;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureSurfaceFitter;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureVegetationClearer;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.WorldgenTerrainHeightmaps;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativePlacementFailures;
import art.arcane.volmlib.nativelib.terrain.feature.NativeFeatureTable;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructurePlacementDecision;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureSeaSurface;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStrata;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedPlacementContainmentTest {
    private final IllegalStateException failure = new IllegalStateException("placement failed");
    private final List<String> skipped = new ArrayList<>();
    private final AtomicInteger observedFailures = new AtomicInteger();
    private final AtomicInteger observedPasses = new AtomicInteger();

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @After
    public void clearHandler() {
        NativePlacementFailures.register(null);
    }

    @Test
    public void aThrowingImportedFeatureIsSkippedAndTheNextFeatureStillPlaces() {
        NativePlacementFailures.register((error, kind, id, x, z) -> skipped.add(kind));
        AtomicBoolean nextPlaced = new AtomicBoolean();

        runFeatures(nextPlaced);

        assertEquals(List.of("imported feature"), skipped);
        assertTrue(nextPlaced.get());
        assertEquals(1, observedFailures.get());
        assertEquals(1, observedPasses.get());
    }

    @Test
    public void anEngineFailureFromAnImportedFeatureStopsTheChunk() {
        NativePlacementFailures.register((error, kind, id, x, z) -> {
            throw (IllegalStateException) error;
        });
        AtomicBoolean nextPlaced = new AtomicBoolean();

        IllegalStateException stopped = assertThrows(IllegalStateException.class, () -> runFeatures(nextPlaced));

        assertSame(failure, stopped.getCause());
        assertFalse(nextPlaced.get());
        assertEquals(0, observedPasses.get());
    }

    @Test
    public void aThrowingStartIsSkippedAndTheNextStartStillPlaces() {
        NativePlacementFailures.register((error, kind, id, x, z) -> skipped.add(kind));

        assertEquals(2, placeStructures().get());

        assertEquals(List.of("vanilla structure"), skipped);
    }

    @Test
    public void anEngineFailureFromAStartStopsTheChunk() {
        NativePlacementFailures.register((error, kind, id, x, z) -> {
            throw (IllegalStateException) error;
        });

        NativeStructureGenerationException stopped = assertThrows(NativeStructureGenerationException.class,
                this::placeStructures);

        assertSame(failure, stopped.getCause());
    }

    @SuppressWarnings("unchecked")
    private void runFeatures(AtomicBoolean nextPlaced) {
        NativeImportedFeaturePolicy<Object> policy = mock(NativeImportedFeaturePolicy.class);
        NativeImportedFeatureControl control = mock(NativeImportedFeatureControl.class);
        NativeFeatureBiomeSource source = mock(NativeFeatureBiomeSource.class);
        RegistryAccess registries = mock(RegistryAccess.class);
        Registry<PlacedFeature> featureRegistry = mock(Registry.class);
        Biome biome = mock(Biome.class);
        Holder.Reference<Biome> biomeHolder = mock(Holder.Reference.class);
        LevelChunkSection section = mock(LevelChunkSection.class);
        PalettedContainerRO<Holder<Biome>> biomes = mock(PalettedContainerRO.class);
        WorldGenLevel level = mock(WorldGenLevel.class);
        ChunkAccess chunk = mock(ChunkAccess.class);
        PlacedFeature throwing = feature(world -> {
            throw failure;
        });
        PlacedFeature next = feature(world -> nextPlaced.set(true));
        when(policy.visibleBiomeKeys()).thenReturn(Set.of("minecraft:plains"));
        when(policy.customBiomeDerivatives()).thenReturn(List.of());
        when(policy.placement()).thenReturn(new NativeImportedFeaturePolicy.Placement(
                false, false, null, null, (x, y, z) -> false, null));
        when(policy.trackedBlockPositions(any())).thenReturn(NativeBlockChangeSet.disabled());
        when(control.shouldGenerateStepOrdinal(anyInt())).thenReturn(true);
        when(source.orderedPossibleBiomes()).thenReturn(List.of(biomeHolder));
        when(biomeHolder.unwrapKey()).thenReturn(Optional.of(
                ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains"))));
        when(biomeHolder.value()).thenReturn(biome);
        when(biome.getGenerationSettings()).thenReturn(new BiomeGenerationSettings.PlainBuilder()
                .addFeature(0, Holder.direct(throwing))
                .addFeature(0, Holder.direct(next)).build());
        when(level.registryAccess()).thenReturn(registries);
        when(registries.lookupOrThrow(Registries.PLACED_FEATURE)).thenReturn(featureRegistry);
        when(level.getMinY()).thenReturn(-64);
        when(level.getMinSectionY()).thenReturn(-4);
        when(level.getHeight()).thenReturn(384);
        when(level.getBiome(any())).thenReturn(biomeHolder);
        when(level.getBiomeManager()).thenReturn(new BiomeManager((x, y, z) -> biomeHolder, 7L));
        when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
        when(chunk.getSections()).thenReturn(new LevelChunkSection[]{section});
        when(section.getBiomes()).thenReturn(biomes);
        doAnswer(invocation -> {
            Consumer<Holder<Biome>> consumer = invocation.getArgument(0);
            consumer.accept(biomeHolder);
            return null;
        }).when(biomes).getAll(any());
        NativeModdedImportedFeatures features = new NativeModdedImportedFeatures(source, new NativeModdedImportedFeatures.PlacementObserver() {
            @Override
            public void completed() {
                observedPasses.incrementAndGet();
            }

            @Override
            public void failed(int chunkX, int chunkZ, Throwable error) {
                observedFailures.incrementAndGet();
            }
        });
        NativeFeatureTable table = features.buildTable(policy, control, 1L);

        features.run(level, chunk, mock(ChunkGenerator.class), table);

        verify(level).setCurrentlyGenerating(null);
    }

    @SuppressWarnings("unchecked")
    private AtomicInteger placeStructures() {
        WorldGenLevel world = mock(WorldGenLevel.class);
        ChunkAccess chunk = mock(ChunkAccess.class);
        StructureManager manager = mock(StructureManager.class);
        NativeBlockChangeSet changes = NativeBlockChangeSet.create(new NativeBlockChangeSet.Bounds(-1, -1, 1, 1, -64, 384));
        NativeModdedStructureStage.Policy<Object, StructureStartPlan, Ownership> policy = mock(NativeModdedStructureStage.Policy.class);
        StructureReferencePolicy<StructureStartPlan, Ownership> structures = mock(StructureReferencePolicy.class);
        RegistryAccess registries = mock(RegistryAccess.class);
        Registry<Structure> registry = mock(Registry.class);
        Structure structure = mock(Structure.class);
        StructureStart start = mock(StructureStart.class);
        StructureStart nextStart = mock(StructureStart.class);
        StructurePlacementDecision decision = mock(StructurePlacementDecision.class);
        Holder<Biome> biome = Holder.direct(mock(Biome.class));
        when(world.registryAccess()).thenReturn(registries);
        when(world.getLevel()).thenReturn(mock(ServerLevel.class));
        when(registries.lookupOrThrow(Registries.STRUCTURE)).thenReturn(registry);
        when(registry.iterator()).thenAnswer(invocation -> List.of(structure).iterator());
        when(registry.getKey(structure)).thenReturn(Identifier.parse("minecraft:village_plains"));
        when(structure.step()).thenReturn(GenerationStep.Decoration.SURFACE_STRUCTURES);
        when(manager.shouldGenerateStructures()).thenReturn(true);
        when(manager.startsForStructure(anyInt(), anyInt(), any(Structure.class))).thenReturn(List.of(start, nextStart));
        when(start.getChunkPos()).thenReturn(new ChunkPos(0, 0));
        when(start.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 0, 15, 80, 15));
        when(nextStart.getChunkPos()).thenReturn(new ChunkPos(0, 1));
        when(nextStart.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 16, 15, 80, 31));
        when(policy.allowsFootprint(any(), any())).thenReturn(true);
        when(policy.strata(any())).thenReturn(mock(StructureStrata.class));
        when(policy.water(any())).thenReturn(mock(StructureWater.class));
        when(policy.seaSurface(any())).thenReturn(StructureSeaSurface.NONE);
        when(policy.structurePolicy(any())).thenReturn(structures);
        when(structures.resolve(anyString(), anyBoolean())).thenReturn(decision);
        when(decision.generate()).thenReturn(true);
        when(policy.protectedPositions(any())).thenReturn((x, y, z) -> false);
        when(policy.worldgenHeight(any(), anyInt(), anyBoolean())).thenReturn((x, z) -> 64);
        when(policy.trackBlockChanges(any(), any())).thenReturn(changes);
        when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
        when(chunk.getMinY()).thenReturn(-64);
        when(chunk.getHeight()).thenReturn(384);
        when(chunk.getSections()).thenReturn(new LevelChunkSection[0]);
        when(world.getMinY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getBiome(any())).thenReturn(biome);
        when(world.getBiomeManager()).thenReturn(new BiomeManager((x, y, z) -> biome, 7L));
        NativeModdedStructureStage<Object, StructureStartPlan, Ownership> stage = new NativeModdedStructureStage<>(
                new NativeModdedStructureStage.Options<>(mock(ChunkGenerator.class), () -> null, policy));
        AtomicInteger placed = new AtomicInteger();
        try (MockedStatic<WorldgenTerrainHeightmaps> heights = mockStatic(WorldgenTerrainHeightmaps.class);
             MockedStatic<NativeStructureOwnershipRecovery> ownership = mockStatic(NativeStructureOwnershipRecovery.class);
             MockedStatic<NativeStructureVegetationClearer> vegetation = mockStatic(NativeStructureVegetationClearer.class);
             MockedStatic<NativeStructureSurfaceFitter> surface = mockStatic(NativeStructureSurfaceFitter.class);
             MockedStatic<NativeStructurePostProcessor> post = mockStatic(NativeStructurePostProcessor.class)) {
            surface.when(() -> NativeStructureSurfaceFitter.prepareSurfaceStructures(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new NativeStructureSurfaceFitter.SurfaceTerrainPlan(List.of(), Set.of(), Map.of(), Map.of()));
            post.when(() -> NativeStructurePostProcessor.place(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        if (placed.incrementAndGet() == 1) {
                            throw failure;
                        }
                        return null;
                    });
            stage.placeVanillaStructures(world, chunk, manager);
        }
        verify(policy).invalidateBlockChanges(null, changes);
        return placed;
    }

    @SuppressWarnings("unchecked")
    private static PlacedFeature feature(Consumer<WorldGenLevel> placement) {
        Feature feature = mock(Feature.class);
        when(feature.place(any(), any(), any(), any())).thenAnswer(invocation -> {
            placement.accept(invocation.getArgument(0));
            return true;
        });
        return new PlacedFeature(Holder.direct(feature), List.of());
    }

    private interface Ownership extends StructureOwnershipRecordView<Ownership> {
    }
}
