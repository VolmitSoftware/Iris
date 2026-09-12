package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class HydrologyBlockProbeTest {
    @ClassRule
    public static final PlatformLeakGuard LEAK_GUARD = PlatformLeakGuard.clean();

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.bind(new StubPlatform());
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void generatedCaneChecksRootSubstrateAndActualWaterAcrossNegativeChunkBoundary() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(-1, 0);
        set(chunks, -1, 4, 8, "sugar_cane[age=0]");
        set(chunks, -1, 5, 8, "sugar_cane[age=0]");
        set(chunks, -1, 3, 8, "sand");
        set(chunks, 0, 3, 8, "water[level=0]");
        HydrologyBlockProbe.Evidence valid = inspect(chunks, Map.of());
        assertEquals(1, valid.caneRoots);
        assertEquals(1, valid.validCaneRoots);
        assertEquals(0, valid.censoredCaneRoots);

        set(chunks, -1, 3, 8, "stone");
        HydrologyBlockProbe.Evidence stone = inspect(chunks, Map.of());
        assertEquals(1, stone.invalidCaneSubstrates);
        assertFalse(HydrologyBlockProbe.failures(stone, 8, Set.of()).isEmpty());

        set(chunks, -1, 3, 8, "sand");
        set(chunks, 0, 3, 8, "ice");
        assertEquals(1, inspect(chunks, Map.of()).dryCaneRoots);
        set(chunks, 0, 3, 8, "frosted_ice[age=0]");
        assertEquals(1, inspect(chunks, Map.of()).validCaneRoots);
    }

    @Test
    public void outerCaneBoundaryIsCensoredWithoutPretendingItPassed() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        set(chunks, 0, 4, 8, "sugar_cane");
        set(chunks, 0, 3, 8, "dirt");
        HydrologyBlockProbe.Evidence evidence = inspect(chunks, Map.of());
        assertEquals(1, evidence.censoredCaneRoots);
        assertEquals(0, evidence.dryCaneRoots);
        assertFalse(HydrologyBlockProbe.failures(evidence, 8, Set.of("cane")).isEmpty());
    }

    @Test
    public void retainedWaterloggedVoxelsCountButSolidAndDryChannelVoxelsFail() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        HydrologyColumnLayer layer = layer(HydrologyFeatureType.RIFFLE, 2, 4, true);
        Map<Long, HydrologyBlockProbe.PlannedColumn> planned = Map.of(
                RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(8, false, layer, state("water")));
        set(chunks, 8, 3, 8, "stone");
        set(chunks, 8, 4, 8, "brain_coral[waterlogged=true]");
        HydrologyBlockProbe.Evidence evidence = inspect(chunks, planned);
        assertEquals(2, evidence.plannedWetVoxels);
        assertEquals(1, evidence.missingWetVoxels);
        set(chunks, 8, 3, 8, "water[level=0]");
        assertEquals(0, inspect(chunks, planned).missingWetVoxels);
    }

    @Test
    public void mouthRequiresActualCardinalWaterToAnOceanColumn() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        HydrologyColumnLayer mouth = layer(HydrologyFeatureType.MOUTH, 5, 6, true);
        Map<Long, HydrologyBlockProbe.PlannedColumn> planned = new HashMap<>();
        planned.put(RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(6, false, mouth, state("water")));
        planned.put(RiverFootprint.pack(9, 8), new HydrologyBlockProbe.PlannedColumn(5, true, null, null));
        set(chunks, 8, 6, 8, "water");
        set(chunks, 9, 6, 8, "water");
        HydrologyBlockProbe.Evidence connected = inspect(chunks, planned);
        assertEquals(1, connected.connectedMouthComponents);
        assertEquals(1, connected.drySillCuts);
        assertEquals(0, connected.oceanBedWrites);
        assertTrue(HydrologyBlockProbe.failures(connected, 8, Set.of("mouth", "channel")).isEmpty());

        planned.put(RiverFootprint.pack(9, 8), new HydrologyBlockProbe.PlannedColumn(5, false, null, null));
        assertEquals(1, inspect(chunks, planned).disconnectedMouthComponents);
        planned.put(RiverFootprint.pack(9, 8), new HydrologyBlockProbe.PlannedColumn(5, true, null, null));
        set(chunks, 9, 6, 8, "stone");
        assertEquals(1, inspect(chunks, planned).disconnectedMouthComponents);
    }

    @Test
    public void oceanApronRequiresConnectedOwnedWaterWhenAPoolOwnsTheShoreline() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        HydrologyColumnLayer pool = layer(HydrologyFeatureType.SURFACE_POOL, 5, 6, true);
        HydrologyFeatureRef mouth = new HydrologyFeatureRef(4L, HydrologyFeatureType.MOUTH,
                2L, 5L, 9, 6, 8, 1, 0, false);
        HydrologyColumnLayer apron = new HydrologyColumnLayer(mouth, 6, 6, 6,
                true, false, false, true, false, false, false, false, true,
                "water", "surface", "mouth", "shore", "bank", "cave");
        HydrologyColumnSample ocean = new HydrologyColumnSample(9, 8, 5, 6, true, "ocean", List.of(apron));
        assertTrue(ocean.primarySurfaceLayer().isEmpty());
        assertEquals(apron, HydrologyBlockProbe.surfaceForInspection(ocean));
        assertEquals(pool, HydrologyBlockProbe.surfaceForInspection(
                new HydrologyColumnSample(8, 8, 6, 6, false, "shore", List.of(pool, apron))));
        Map<Long, HydrologyBlockProbe.PlannedColumn> planned = new HashMap<>();
        planned.put(RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(6, false, pool, state("water")));
        planned.put(RiverFootprint.pack(9, 8), new HydrologyBlockProbe.PlannedColumn(5, true, apron, state("water")));
        set(chunks, 8, 6, 8, "water");
        set(chunks, 9, 6, 8, "water");
        assertEquals(1, inspect(chunks, planned).connectedMouthComponents);

        set(chunks, 8, 6, 8, "stone");
        HydrologyBlockProbe.Evidence disconnected = inspect(chunks, planned);
        assertEquals(0, disconnected.connectedMouthComponents);
        assertEquals(1, disconnected.disconnectedMouthComponents);

        HydrologyFeatureRef other = new HydrologyFeatureRef(6L, HydrologyFeatureType.SURFACE_POOL,
                99L, 7L, 8, 6, 8, 1, 0, false);
        HydrologyColumnLayer unrelated = new HydrologyColumnLayer(other, 5, 6, 6,
                true, false, false, true, false, false, true, true, false,
                "water", "surface", "mouth", "shore", "bank", "cave");
        planned.put(RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(6, false, unrelated, state("water")));
        set(chunks, 8, 6, 8, "water");
        assertEquals(0, inspect(chunks, planned).connectedMouthComponents);
    }

    @Test
    public void diagonalOceanWaterAndOutOfBoundsWaterDoNotProveMouthCoverage() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        HydrologyColumnLayer mouth = layer(HydrologyFeatureType.MOUTH, 5, 6, true);
        Map<Long, HydrologyBlockProbe.PlannedColumn> planned = new HashMap<>();
        planned.put(RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(6, false, mouth, state("water")));
        planned.put(RiverFootprint.pack(9, 9), new HydrologyBlockProbe.PlannedColumn(5, true, null, null));
        set(chunks, 8, 6, 8, "water");
        set(chunks, 9, 6, 9, "water");
        assertEquals(1, inspect(chunks, planned).disconnectedMouthComponents);

        planned.clear();
        planned.put(RiverFootprint.pack(0, 8), new HydrologyBlockProbe.PlannedColumn(6, false, mouth, state("water")));
        set(chunks, 0, 6, 8, "water");
        HydrologyBlockProbe.Evidence censored = inspect(chunks, planned);
        assertEquals(1, censored.censoredMouthComponents);
        assertFalse(HydrologyBlockProbe.failures(censored, 8, Set.of("mouth")).isEmpty());
    }

    @Test
    public void bankBudgetsAndOceanBedOwnershipRemainIndependentGates() {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = chunks(0, 0);
        Map<Long, HydrologyBlockProbe.PlannedColumn> planned = Map.of(
                RiverFootprint.pack(8, 8), new HydrologyBlockProbe.PlannedColumn(
                        12, false, layer(HydrologyFeatureType.RIFFLE, 3, 3, false), null),
                RiverFootprint.pack(9, 8), new HydrologyBlockProbe.PlannedColumn(
                        5, true, layer(HydrologyFeatureType.MOUTH, 3, 6, true), state("water")));
        HydrologyBlockProbe.Evidence evidence = inspect(chunks, planned);
        assertEquals(9, evidence.maximumBankCut);
        assertEquals(9, evidence.bankExcavation);
        assertEquals(1, evidence.oceanBedWrites);
        assertTrue(HydrologyBlockProbe.failures(evidence, 8, Set.of()).stream()
                .anyMatch(value -> value.contains("budget")));
    }

    @Test
    public void configurationBoundsAndRequiredCoveragePreventUnboundedOrVacuousProof() {
        assertThrows(IllegalArgumentException.class, () -> new HydrologyBlockProbe.Configuration(
                new File("/pack"), "overworld", 1L, 0, 8, 0, 8, Set.of("mouth"), new File("/output"), false));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyBlockProbe.Configuration(
                new File("/pack"), "overworld", 1L, 0, 0, 0, 0, Set.of("river"), new File("/output"), false));
        assertEquals(4, HydrologyBlockProbe.failures(new HydrologyBlockProbe.Evidence(), 8,
                Set.of("mouth", "cane", "channel", "bank")).size());
    }

    @Test
    public void reportSerializesAbsolutePathsWithoutReflectingIntoFile() {
        HydrologyBlockProbe.Configuration configuration = new HydrologyBlockProbe.Configuration(
                new File("/pack"), "overworld", 1L, 0, 0, 0, 0, Set.of(), new File("/output"), false);
        String json = HydrologyBlockProbe.json(new HydrologyBlockProbe.Result("PASS", configuration,
                -64, 127, new HydrologyBlockProbe.Evidence(), List.of(), List.of()));
        assertTrue(json.contains("\"pack\": \"/pack\""));
        assertTrue(json.contains("worldMinimumY"));
    }

    private static HydrologyBlockProbe.Evidence inspect(Map<Long, RealPackProbeSupport.GeneratedChunk> chunks,
                                                         Map<Long, HydrologyBlockProbe.PlannedColumn> planned) {
        return HydrologyBlockProbe.inspect(new HydrologyBlockProbe.Volume(chunks), planned, 6);
    }

    private static Map<Long, RealPackProbeSupport.GeneratedChunk> chunks(int minimumX, int maximumX) {
        Map<Long, RealPackProbeSupport.GeneratedChunk> chunks = new HashMap<>();
        for (int x = minimumX; x <= maximumX; x++) {
            Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 16, 16);
            Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, 16, 16);
            chunks.put(RiverFootprint.pack(x, 0), new RealPackProbeSupport.GeneratedChunk(x, 0, 16, blocks, biomes));
        }
        return chunks;
    }

    private static void set(Map<Long, RealPackProbeSupport.GeneratedChunk> chunks, int x, int y, int z, String key) {
        chunks.get(RiverFootprint.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))
                .blocks().set(Math.floorMod(x, 16), y, Math.floorMod(z, 16), state(key));
    }

    private static PlatformBlockState state(String key) {
        return IrisPlatforms.get().registries().block("minecraft:" + key);
    }

    private static HydrologyColumnLayer layer(HydrologyFeatureType type, int bed, int head, boolean channel) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(1L, type, 2L, 3L, 8, head, 8, 1, 0, false);
        return new HydrologyColumnLayer(feature, bed, head, head, channel, false, !channel, channel,
                false, false, true, channel, false, "water", "surface", "mouth", "shore", "bank", "cave");
    }
}
