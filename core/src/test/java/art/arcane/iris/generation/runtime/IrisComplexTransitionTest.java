package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.world.history.TransitionDisplacementField;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.stage.IrisTerrainNormalActuator;
import art.arcane.iris.generation.stage.IrisTransitionGeometryActuator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.terrain.Terrain3DRuntime;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.BoundaryGeometryInfluence;
import art.arcane.iris.world.history.GenerationBlend;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.TransitionGeometryBlender;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexTransitionTest {
    private static final int OLD_EAST_OF = 37;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final Timeout timeout = Timeout.seconds(120L);

    private IrisSettings previousSettings;
    private HydrologyFixture fixture;

    @Before
    public void openFixture() throws Exception {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
        fixture = new HydrologyFixture(HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null), temporaryFolder.newFolder());
    }

    @After
    public void closeFixture() {
        try {
            if (fixture != null) {
                fixture.close();
            }
        } finally {
            IrisPlatforms.unbind();
            IrisServices.remove(PreservationRegistry.class);
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void seamWeightTapersRasterTerrainTowardNaturalGround() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, 156))) {
            IrisComplex complex = seam.complex();
            ChunkWaterRaster raster = seam.hydrology.runtime.raster(0, 0);
            int[] carved = carvedColumns(raster, 150, 3);
            for (int index = 0; index < carved.length; index++) {
                int x = carved[index] & 15;
                int z = carved[index] >> 4;
                double weight = index * 0.5D;
                when(displacement.seamWeight(x, z)).thenReturn(weight);
                complex = seam.complex();
                int tapered = GenerationBlend.interpolateHeight(150, raster.terrainHeight(x, z), weight);
                double expected = tapered == 150 ? 150.3D : tapered;
                assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(complex.resolveHydrologyTerrainHeight(x, z)));
            }
            int untouched = untouchedColumn(raster, 150);
            when(displacement.seamWeight(untouched & 15, untouched >> 4)).thenReturn(0.5D);
            complex = seam.complex();
            assertEquals(150.3D, complex.resolveHydrologyTerrainHeight(untouched & 15, untouched >> 4), 0D);
            assertTrue(complex.isHydrologyPlanned(carved[0] & 15, carved[0] >> 4));
        }
    }

    @Test
    public void coldRasterOnTheMainThreadIsNotPlannedEvenOnTheSeam() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.seamWeight(4, 6)).thenReturn(0D);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> true, natural, 127, 156))) {
            FakeHydrology hydrology = seam.hydrology;
            hydrology.hold();
            IrisComplex complex = seam.complex();
            assertFalse(complex.isHydrologyPlanned(4, 6));
            assertEquals(150.3D, complex.resolveHydrologyTerrainHeight(4, 6), 0D);
            hydrology.release();
            hydrology.awaitSnapshot(0, 0);
            assertTrue(complex.isHydrologyPlanned(4, 6));
            assertEquals(150.3D, complex.resolveHydrologyTerrainHeight(4, 6), 0D);
        }
    }

    @Test
    public void seamKeepsBermsAtFullHeightAndNeverReportsWaterOnGroundAtTheHead() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, 156))) {
            FakeHydrology hydrology = seam.hydrology;
            int berms = 0;
            for (double weight : new double[]{0.05D, 0.3D, 0.5D, 0.8D, 1D}) {
                when(displacement.seamWeight(anyInt(), anyInt())).thenReturn(weight);
                IrisComplex complex = seam.complex();
                for (int chunk = 0; chunk < 9; chunk++) {
                    ChunkWaterRaster raster = hydrology.runtime.raster(chunk % 3, chunk / 3);
                    for (int index = 0; index < 256; index++) {
                        int x = (chunk % 3 << 4) + (index & 15);
                        int z = (chunk / 3 << 4) + (index >> 4);
                        int terrain = raster.empty() ? 150 : raster.terrainHeight(index & 15, index >> 4);
                        double ground = complex.resolveHydrologyTerrainHeight(x, z);
                        if (terrain > 150) {
                            berms++;
                            assertEquals(terrain, ground, 0D);
                            assertTrue(ground >= 156);
                        }
                        assertContained(complex, x, z, ground);
                    }
                }
            }
            assertTrue(berms > 0);
        }
    }

    @Test
    public void seamDriesCarvedBedsWhoseTaperedGroundRisesToTheHead() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, 148))) {
            IrisComplex first = seam.complex();
            ChunkWaterRaster raster = seam.hydrology.runtime.raster(0, 0);
            int dried = 0;
            int wet = 0;
            for (double weight : new double[]{0.1D, 0.5D, 1D}) {
                when(displacement.seamWeight(anyInt(), anyInt())).thenReturn(weight);
                IrisComplex complex = weight == 0.1D ? first : seam.complex();
                for (int index = 0; index < 256; index++) {
                    int x = index & 15;
                    int z = index >> 4;
                    if (raster.waterHead(x, z) == Integer.MIN_VALUE) {
                        continue;
                    }
                    double ground = complex.resolveHydrologyTerrainHeight(x, z);
                    assertTrue(raster.terrainHeight(x, z) < 150);
                    assertContained(complex, x, z, ground);
                    if (ground >= 148) {
                        dried++;
                        assertEquals(127D, complex.resolveWaterHead(x, z), 0D);
                        assertSame(WaterRole.NONE, complex.resolveWaterRole(x, z));
                    } else {
                        wet++;
                        assertEquals(148D, complex.resolveWaterHead(x, z), 0D);
                        assertSame(WaterRole.POND, complex.resolveWaterRole(x, z));
                    }
                }
            }
            assertTrue(dried > 0);
            assertTrue(wet > 0);
        }
    }

    @Test
    public void taperDriedStepColumnsRiseAboveTheirWetterNeighbour() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) >= 16 ? 0.7D : 1D);
        Reach step = WaterScene.line(81L, WaterScene.stream(), 0, 8, 40, 8, (x, z) -> x < 16 ? 149 : 148);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, List.of(step), List.of()))) {
            FakeHydrology hydrology = seam.hydrology;
            IrisComplex complex = seam.natural((x, z) -> 150D).complex();
            for (int z = 7; z <= 9; z++) {
                assertEquals(148, hydrology.runtime.raster(1, 0).waterHead(0, z));
                assertSame(WaterRole.NONE, complex.resolveWaterRole(16, z));
                assertEquals(150D, complex.resolveHydrologyTerrainHeight(16, z), 0D);
            }
            assertTrue(assertSeamStepContained(complex, hydrology, 2, 38, 0, 16) > 0);
        }
    }

    @Test
    public void dammedColumnsBelowATallStepRiseAboveTheUpperWater() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(1);
            return x < 16 ? 1D : z >= 10 ? 0D : 0.002D;
        });
        Reach step = WaterScene.line(82L, WaterScene.stream(), 0, 8, 40, 8, (x, z) -> x < 16 ? 149 : 145);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, List.of(step), List.of()))) {
            FakeHydrology hydrology = seam.hydrology;
            IrisComplex complex = seam.natural((x, z) -> x < 16 ? 150D : 140D).complex();
            assertEquals(145, hydrology.runtime.raster(1, 0).waterHead(0, 9));
            assertSame(WaterRole.NONE, complex.resolveWaterRole(16, 9));
            assertTrue(complex.resolveHydrologyTerrainHeight(16, 9) >= 150D);
            assertTrue(complex.resolveWaterRole(16, 8).wet());
            assertEquals(146D, complex.resolveHydrologyTerrainHeight(17, 9), 0D);
            assertTrue(assertSeamStepContained(complex, hydrology, 2, 38, 0, 9) > 0);
        }
    }

    @Test
    public void seamDamsALakeEdgeAgainstLowerOldGroundInEitherChunkOrder() throws Exception {
        int[][] order = {{1, 0}, {0, 1}, {1, 1}};
        int[][] reversed = {{1, 1}, {0, 1}, {1, 0}};
        assertEquals(seamDamColumns(order), seamDamColumns(reversed));
    }

    @Test
    public void columnsReturnedToNaturalFarFromWaterKeepTheirTerrain3DColumn() throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        Terrain3DColumn column = overhangColumn();
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, 148))) {
            FakeHydrology hydrology = seam.hydrology;
            seam.terrain(column).complex();
            int wet = carvedColumns(hydrology.runtime.raster(0, 0), 150, 1)[0];
            int bank = -1;
            ChunkWaterRaster outside = hydrology.runtime.raster(2, 0);
            for (int index = 0; index < 256 && bank < 0; index++) {
                if (outside.shoreDistance(index & 15, index >> 4) > 2 && outside.terrainHeight(index & 15, index >> 4) < 150) {
                    bank = index;
                }
            }
            assertTrue(bank >= 0);
            int bankX = 32 + (bank & 15);
            int bankZ = bank >> 4;
            when(displacement.seamWeight(anyInt(), anyInt())).thenReturn(1D);
            IrisComplex complex = seam.complex();
            assertNull(complex.terrainColumn(wet & 15, wet >> 4));
            assertNull(complex.terrainColumn(bankX, bankZ));
            when(displacement.seamWeight(anyInt(), anyInt())).thenReturn(0.002D);
            complex = seam.complex();
            assertEquals(150.3D, complex.resolveHydrologyTerrainHeight(bankX, bankZ), 0D);
            assertSame(column, complex.terrainColumn(bankX, bankZ));
            assertEquals(150.3D, complex.resolveHydrologyTerrainHeight(wet & 15, wet >> 4), 0D);
            assertNull(complex.terrainColumn(wet & 15, wet >> 4));
        }
    }

    @Test
    public void pondRimColumnsStaySolidWhileFartherColumnsKeepTheirTerrain3DShape() throws Exception {
        assertRimContainment(156, 1D);
        assertRimContainment(148, 0.002D);
    }

    @Test
    public void transitionStageKeepsTheSeamDamAndLeavesNoWaterBesideLowerGround() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true)) {
            Hunk<NativeBlockState> painted = stage.paint(1);
            Hunk<NativeBlockState> blended = stage.blend(1, painted);
            int dams = 0;
            int wet = 0;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = 16 + lx;
                    if (stage.complex.hydrologyOwnsTerrain(x, lz)) {
                        assertColumn(painted, blended, lx, lz);
                    }
                    if (stage.raster(1).waterHead(lx, lz) != Integer.MIN_VALUE && oldChunkWeight(x, lz) > 0D && oldChunkWeight(x - 1, lz) == 0D) {
                        dams++;
                        assertTrue(x + "," + lz + " dam lost its crest", blended.getRaw(lx, 157, lz) == stage.stone);
                    }
                    if (blended.getRaw(lx, 156, lz) != stage.water) {
                        continue;
                    }
                    wet++;
                    for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = lx + side[0];
                        int nz = lz + side[1];
                        if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                            assertFalse(x + "," + lz + " leaks toward " + (16 + nx) + "," + nz, stage.open(blended.getRaw(nx, 156, nz)));
                        }
                    }
                }
            }
            assertTrue(dams > 0);
            assertTrue(wet > 0);
            assertSame(stage.air, blended.getRaw(0, 145, 0));
        }
    }

    @Test
    public void seamDamFollowsTheSavedOldColumnWhenSmoothedGroundStandsAboveTheHead() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true,
                IrisComplexTransitionTest::raisedOldSide)) {
            Hunk<NativeBlockState> painted = stage.paint(1);
            Hunk<NativeBlockState> blended = stage.blend(1, painted);
            ChunkWaterRaster raster = stage.raster(1);
            int dams = 0;
            int water = 0;
            for (int lz = 0; lz < 16; lz++) {
                assertTrue(stage.complex.naturalTrueHeight(16, lz) >= 156);
                if (raster.waterHead(1, lz) != Integer.MIN_VALUE) {
                    dams++;
                    assertSame(WaterRole.NONE, stage.complex.resolveWaterRole(17, lz));
                    assertTrue(17 + "," + lz + " dam at " + stage.complex.resolveHydrologyTerrainHeight(17, lz),
                            stage.complex.resolveHydrologyTerrainHeight(17, lz) >= 157D);
                    assertSame(stage.stone, blended.getRaw(1, 157, lz));
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = 0; y < TransitionStage.HEIGHT; y++) {
                        if (blended.getRaw(lx, y, lz) != stage.water) {
                            continue;
                        }
                        water++;
                        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = lx + side[0];
                            int nz = lz + side[1];
                            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                                assertTrue((16 + lx) + "," + y + "," + lz + " touches air at " + (16 + nx) + "," + nz,
                                        !stage.open(blended.getRaw(nx, y, nz)));
                            }
                        }
                    }
                }
            }
            assertTrue(dams > 0);
            assertTrue(water > 0);
        }
    }

    @Test
    public void seamDamHoldsNewWaterBesideAnOpenOldRiverOrLakeEdge() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true, (x, z) -> 150.3D,
                TransitionStage.savedColumn(140, 160, -1))) {
            assertTrue(stage.complex.resolveWaterHead(16, 4) < 156D);
            Hunk<NativeBlockState> blended = stage.blend(1, stage.paint(1));
            assertTrue(stage.dams(blended) > 0);
            stage.assertNoWaterTouchesAir(blended);
            assertTrue(stage.open(blended.getRaw(0, 155, 4)));
        }
    }

    @Test
    public void seamDamHoldsNewWaterBesideAProtectedSavedVoxel() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true, (x, z) -> 150.3D,
                TransitionStage.savedColumn(170, 170, 153))) {
            Hunk<NativeBlockState> blended = stage.blend(1, stage.paint(1));
            assertTrue(stage.dams(blended) > 0);
            stage.assertNoWaterTouchesAir(blended);
            assertTrue(stage.open(blended.getRaw(0, 153, 4)));
            assertSame(stage.stone, blended.getRaw(0, 154, 4));
        }
    }

    @Test
    public void seamDamHoldsNewWaterBesideAProtectedSavedVoxelUnderPaintedGround() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true,
                IrisComplexTransitionTest::raisedOldSide, TransitionStage.savedColumn(170, 170, 153))) {
            Hunk<NativeBlockState> painted = stage.paint(1);
            for (int lz = 0; lz < 16; lz++) {
                assertSame(stage.stone, painted.getRaw(0, 153, lz));
                painted.setRaw(0, 153, lz, stage.air);
            }
            Hunk<NativeBlockState> blended = stage.blend(1, painted);
            assertTrue(stage.dams(blended) > 0);
            stage.assertNoWaterTouchesAir(blended);
            assertTrue(stage.open(blended.getRaw(0, 153, 4)));
            assertSame(stage.stone, blended.getRaw(0, 154, 4));
        }
    }

    @Test
    public void fullySolidSavedColumnHoldsTheWaterWithoutADam() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true, (x, z) -> 150.3D,
                TransitionStage.savedColumn(170, 170, -1))) {
            Hunk<NativeBlockState> blended = stage.blend(1, stage.paint(1));
            assertEquals(0, stage.dams(blended));
            int wet = 0;
            for (int lz = 0; lz < 16; lz++) {
                if (stage.raster(1).waterHead(1, lz) != Integer.MIN_VALUE) {
                    wet++;
                    assertSame(WaterRole.POND, stage.complex.resolveWaterRole(17, lz));
                    assertEquals(156D, stage.complex.resolveWaterHead(17, lz), 0D);
                    assertSame(stage.water, blended.getRaw(1, 156, lz));
                    assertSame(stage.stone, blended.getRaw(0, 156, lz));
                }
            }
            assertTrue(wet > 0);
            stage.assertNoWaterTouchesAir(blended);
        }
    }

    @Test
    public void savedColumnSeamDamIsTheSameInEitherChunkOrder() throws Exception {
        int[][] order = {{1, 0}, {0, 1}, {1, 1}};
        int[][] reversed = {{1, 1}, {0, 1}, {1, 0}};
        assertEquals(savedColumnSeam(order), savedColumnSeam(reversed));
    }

    private Map<Long, List<Double>> savedColumnSeam(int[][] chunks) throws Exception {
        Map<Long, List<Double>> columns = new HashMap<>();
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, true,
                IrisComplexTransitionTest::raisedOldSide)) {
            int dams = 0;
            for (int[] chunk : chunks) {
                ChunkWaterRaster raster = stage.hydrology.runtime.raster(chunk[0], chunk[1]);
                for (int index = 0; index < 256; index++) {
                    int x = (chunk[0] << 4) + (index & 15);
                    int z = (chunk[1] << 4) + (index >> 4);
                    double ground = stage.complex.resolveHydrologyTerrainHeight(x, z);
                    WaterRole role = stage.complex.resolveWaterRole(x, z);
                    columns.put(((long) x << 32) | (z & 0xFFFFFFFFL), List.of(ground, stage.complex.resolveWaterHead(x, z),
                            (double) role.ordinal()));
                    boolean beside = oldChunkWeight(x + 1, z) == 0D || oldChunkWeight(x - 1, z) == 0D
                            || oldChunkWeight(x, z + 1) == 0D || oldChunkWeight(x, z - 1) == 0D;
                    if (raster.waterHead(index & 15, index >> 4) != Integer.MIN_VALUE && oldChunkWeight(x, z) > 0D && beside
                            && stage.complex.resolveHydrologyTerrainHeight(x, z) < 156D + 1D) {
                        fail(x + "," + z + " stays wet beside a saved column that opens below its head");
                    }
                    if (role == WaterRole.NONE && raster.waterHead(index & 15, index >> 4) != Integer.MIN_VALUE && beside
                            && oldChunkWeight(x, z) > 0D) {
                        dams++;
                    }
                }
            }
            assertTrue(dams > 0);
        }
        return columns;
    }

    @Test
    public void transitionStageKeepsRimColumnsBesideWaterSolid() throws Exception {
        try (TransitionStage stage = new TransitionStage((x, z) -> oldEastOfWeight(x), true)) {
            Hunk<NativeBlockState> painted = stage.paint(2);
            Hunk<NativeBlockState> blended = stage.blend(2, painted);
            ChunkWaterRaster raster = stage.raster(2);
            int rims = 0;
            int lowered = 0;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = 32 + lx;
                    int distance = raster.shoreDistance(lx, lz);
                    double weight = oldEastOfWeight(x);
                    boolean natural = stage.complex.resolveHydrologyTerrainHeight(x, lz) == 150.3D;
                    if (natural && distance >= 1 && distance <= 2 && weight > 0D && weight < 1D) {
                        rims++;
                        assertColumn(painted, blended, lx, lz);
                        assertSame(stage.stone, blended.getRaw(lx, 150, lz));
                    } else if (natural && distance > 2 && weight < 1D && stage.open(blended.getRaw(lx, 150, lz))) {
                        lowered++;
                    }
                }
            }
            assertTrue(rims > 0);
            assertTrue(lowered > 0);
        }
    }

    @Test
    public void transitionStageBlendsEveryColumnWithHydrologyOff() throws Exception {
        try (TransitionStage stage = new TransitionStage(IrisComplexTransitionTest::oldChunkWeight, false)) {
            Hunk<NativeBlockState> painted = stage.paint(1);
            Hunk<NativeBlockState> blended = stage.blend(1, painted);
            int changed = 0;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = 16 + lx;
                    assertFalse(stage.complex.hydrologyOwnsTerrain(x, lz));
                    BoundaryColumnGeometry expected = TransitionGeometryBlender.blendColumn(stage.plan.geometryAt(x, lz), x, lz,
                            stage.geometry(painted, lx, lz));
                    for (int y = 0; y < TransitionStage.HEIGHT; y++) {
                        assertEquals(x + "," + y + "," + lz, expected.voxelAt(y).stateKey(), stage.key(blended.getRaw(lx, y, lz)));
                    }
                    changed += blended.getRaw(lx, 150, lz) == painted.getRaw(lx, 150, lz) ? 0 : 1;
                }
            }
            assertTrue(changed > 0);
        }
    }

    @FunctionalInterface
    private interface SeamWeights {
        double weight(int x, int z);
    }

    @FunctionalInterface
    private interface NaturalHeights {
        double height(int x, int z);
    }

    private static double raisedOldSide(int x, int z) {
        return oldChunkWeight(x, z) == 0D ? 158.3D : 150.3D;
    }

    private static void assertColumn(Hunk<NativeBlockState> painted, Hunk<NativeBlockState> blended, int lx, int lz) {
        for (int y = 0; y < TransitionStage.HEIGHT; y++) {
            assertSame(lx + "," + y + "," + lz + " was re-blended", painted.getRaw(lx, y, lz), blended.getRaw(lx, y, lz));
        }
    }

    private final class TransitionStage implements AutoCloseable {
        private static final int HEIGHT = 192;
        private static final int OLD_GROUND = 140;

        private final IrisPlatform previous;
        private final NativeBlockState stone = block("minecraft:stone", false, false);
        private final NativeBlockState water = block("minecraft:water[level=0]", false, true);
        private final NativeBlockState air = block("minecraft:air", true, false);
        private final TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        private final TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        private final IrisComplex complex;
        private final FakeHydrology hydrology;

        private TransitionStage(SeamWeights weights, boolean enabled) throws Exception {
            this(weights, enabled, (x, z) -> 150.3D);
        }

        private TransitionStage(SeamWeights weights, boolean enabled, NaturalHeights natural) throws Exception {
            this(weights, enabled, natural, savedColumn(OLD_GROUND, OLD_GROUND, -1));
        }

        private TransitionStage(SeamWeights weights, boolean enabled, NaturalHeights natural, BoundaryColumnGeometry oldColumn)
                throws Exception {
            when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(invocation ->
                    weights.weight(invocation.getArgument(0), invocation.getArgument(1)));
            when(displacement.fluidHeight(anyInt(), anyInt(), anyDouble())).thenAnswer(invocation -> invocation.getArgument(2));
            when(plan.hasTransitionAtChunk(anyInt(), anyInt())).thenReturn(true);
            when(plan.geometryAt(anyInt(), anyInt())).thenAnswer(invocation -> new BoundaryGeometryInfluence(
                    weights.weight(invocation.getArgument(0), invocation.getArgument(1)), 0D,
                    List.of(new BoundaryGeometryInfluence.Contribution(oldColumn, 1D))));
            AtomicReference<IrisComplex> built = new AtomicReference<>();
            hydrology = enabled ? new FakeHydrology(() -> false, (x, z) -> built.get().naturalTrueHeight(x, z), 127, 156) : null;
            complex = fixture.transitionComplex(enabled ? hydrology.runtime : FakeHydrology.disabled(), plan, displacement);
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> natural.height((int) Math.floor(x), (int) Math.floor(z))));
            complex.setFluidHeight(127D);
            built.set(complex);
            doReturn(ProceduralStream.ofDouble(complex::resolveWaterHead)).when(complex).getWaterHeadStream();
            doReturn(ProceduralStream.of(complex::resolveWaterRole, Interpolated.of(value -> 0D, value -> WaterRole.NONE)))
                    .when(complex).getWaterRoleStream();
            doReturn(mock(IrisImageMapRuntime.class)).when(complex).getImageMapRuntime();
            doReturn(water).when(complex).resolveWaterFluid(anyInt(), anyInt());
            doReturn(water).when(complex).resolveSeaFluid(anyDouble(), anyDouble());
            Map<String, NativeBlockState> blocks = Map.of(stone.key(), stone, water.key(), water, air.key(), air);
            PlatformRegistries registries = mock(PlatformRegistries.class);
            when(registries.air()).thenReturn(air);
            when(registries.block("AIR")).thenReturn(air);
            when(registries.block("BEDROCK")).thenReturn(stone);
            when(registries.blockOrNull(anyString())).thenAnswer(invocation -> blocks.get(invocation.<String>getArgument(0)));
            IrisPlatform platform = mock(IrisPlatform.class);
            when(platform.registries()).thenReturn(registries);
            previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
            IrisPlatforms.unbind();
            IrisPlatforms.bind(platform);
        }

        private ChunkWaterRaster raster(int chunkX) {
            return hydrology.runtime.raster(chunkX, 0);
        }

        static BoundaryColumnGeometry savedColumn(int solidTop, int fluidTop, int protectedY) {
            ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(HEIGHT);
            for (int y = 0; y < HEIGHT; y++) {
                voxels.add(y == protectedY ? new BoundaryColumnGeometry.Voxel("minecraft:oak_log", BoundaryColumnGeometry.Phase.SOLID, "", true)
                        : y <= solidTop ? new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false)
                        : y <= fluidTop ? new BoundaryColumnGeometry.Voxel("minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID,
                        "minecraft:water[level=0]", false)
                        : new BoundaryColumnGeometry.Voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false));
            }
            return BoundaryColumnGeometry.fromVoxels(0, voxels);
        }

        private Hunk<NativeBlockState> paint(int chunkX) {
            Engine engine = mock(Engine.class);
            IrisData data = mock(IrisData.class);
            IrisBiome biome = mock(IrisBiome.class);
            IrisRegion region = new IrisRegion();
            IrisDimension dimension = new IrisDimension().setBedrock(false).setHideOresForHiddenOre(true);
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
            when(biome.generateLayers(eq(dimension), anyDouble(), anyDouble(), any(RNG.class), anyInt(), anyInt(), eq(data), same(complex)))
                    .thenAnswer(invocation -> new KList<>(List.of(stone)));
            when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), eq(data))).thenAnswer(invocation -> new KList<>());
            int[] grounds = grounds(chunkX);
            ChunkContext context = mock(ChunkContext.class);
            when(context.getRoundedHeight(anyInt(), anyInt())).thenAnswer(invocation ->
                    grounds[invocation.<Integer>getArgument(1) << 4 | invocation.<Integer>getArgument(0)]);
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> biome)), chunkX << 4, 0, false));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> region)), chunkX << 4, 0, false));
            when(context.getRock()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> stone, Interpolated.of(value -> 0D, value -> stone)), chunkX << 4, 0, false));
            when(context.getFluid()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> water, Interpolated.of(value -> 0D, value -> water)), chunkX << 4, 0, false));
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
            new IrisTerrainNormalActuator(engine).paint(chunkX << 4, 0, blocks, context);
            return blocks;
        }

        private int[] grounds(int chunkX) {
            int[] grounds = new int[256];
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    grounds[lz << 4 | lx] = (int) Math.round(complex.resolveHydrologyTerrainHeight((chunkX << 4) + lx, lz));
                }
            }
            return grounds;
        }

        private boolean open(NativeBlockState state) {
            return state == null || state == air;
        }

        private String key(NativeBlockState state) {
            return state == null ? air.key() : state.key();
        }

        private int dams(Hunk<NativeBlockState> blended) {
            int dams = 0;
            for (int lz = 0; lz < 16; lz++) {
                if (raster(1).waterHead(1, lz) != Integer.MIN_VALUE && complex.resolveWaterRole(17, lz) == WaterRole.NONE) {
                    dams++;
                    assertTrue(17 + "," + lz + " dam below the crest", complex.resolveHydrologyTerrainHeight(17, lz) >= 157D);
                    assertSame(stone, blended.getRaw(1, 157, lz));
                }
            }
            return dams;
        }

        private void assertNoWaterTouchesAir(Hunk<NativeBlockState> blended) {
            int water = 0;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        if (blended.getRaw(lx, y, lz) != this.water) {
                            continue;
                        }
                        water++;
                        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = lx + side[0];
                            int nz = lz + side[1];
                            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                                assertFalse((16 + lx) + "," + y + "," + lz + " touches air at " + (16 + nx) + "," + nz,
                                        open(blended.getRaw(nx, y, nz)));
                            }
                        }
                    }
                }
            }
            assertTrue(water > 0);
        }

        private Hunk<NativeBlockState> blend(int chunkX, Hunk<NativeBlockState> painted) {
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
            int[] grounds = grounds(chunkX);
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        blocks.setRaw(lx, y, lz, painted.getRaw(lx, y, lz));
                    }
                }
            }
            ChunkContext context = mock(ChunkContext.class);
            when(context.getComplex()).thenReturn(complex);
            when(context.getRoundedHeight(anyInt(), anyInt())).thenAnswer(invocation ->
                    grounds[invocation.<Integer>getArgument(1) << 4 | invocation.<Integer>getArgument(0)]);
            new IrisTransitionGeometryActuator(mock(Engine.class)).generate(chunkX << 4, 0, blocks,
                    Hunk.<NativeBiome>newArrayHunk(16, HEIGHT, 16), false, context);
            return blocks;
        }

        private BoundaryColumnGeometry geometry(Hunk<NativeBlockState> blocks, int lx, int lz) {
            ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(HEIGHT);
            for (int y = 0; y < HEIGHT; y++) {
                NativeBlockState state = blocks.getRaw(lx, y, lz);
                BoundaryColumnGeometry.Phase phase = state == stone ? BoundaryColumnGeometry.Phase.SOLID
                        : state == water ? BoundaryColumnGeometry.Phase.FLUID : BoundaryColumnGeometry.Phase.AIR;
                voxels.add(new BoundaryColumnGeometry.Voxel(key(state), phase, state == water ? water.key() : "", false));
            }
            return BoundaryColumnGeometry.fromVoxels(0, voxels);
        }

        private static NativeBlockState block(String key, boolean isAir, boolean fluid) {
            NativeBlockState state = mock(NativeBlockState.class);
            when(state.key()).thenReturn(key);
            when(state.isAir()).thenReturn(isAir);
            when(state.isFluid()).thenReturn(fluid);
            when(state.isSolid()).thenReturn(!isAir && !fluid);
            return state;
        }

        @Override
        public void close() {
            try {
                if (hydrology != null) {
                    hydrology.close();
                }
            } finally {
                IrisPlatforms.unbind();
                if (previous != null) {
                    IrisPlatforms.bind(previous);
                }
            }
        }
    }

    private void assertRimContainment(int level, double weight) throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.seamWeight(anyInt(), anyInt())).thenReturn(weight);
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, level))) {
            FakeHydrology hydrology = seam.hydrology;
            IrisComplex complex = seam.terrain(overhangColumn()).complex();
            int rim = 0;
            int far = 0;
            for (int chunk = 0; chunk < 9; chunk++) {
                ChunkWaterRaster raster = hydrology.runtime.raster(chunk % 3, chunk / 3);
                for (int index = 0; index < 256; index++) {
                    int x = (chunk % 3 << 4) + (index & 15);
                    int z = (chunk / 3 << 4) + (index >> 4);
                    int distance = raster.shoreDistance(index & 15, index >> 4);
                    if (distance == 0 || complex.resolveHydrologyTerrainHeight(x, z) != 150.3D) {
                        continue;
                    }
                    if (distance <= 2) {
                        rim++;
                        assertFalse(level + " rim " + x + "," + z + " opens", complex.isTerrain3DOpening(x, 120, z));
                    } else {
                        far++;
                        assertTrue(level + " far " + x + "," + z + " lost its shape", complex.isTerrain3DOpening(x, 120, z));
                    }
                }
            }
            assertTrue(level + " rim columns at natural height", rim > 0);
            assertTrue(level + " far columns at natural height", far > 0);
        }
    }

    private static Terrain3DColumn overhangColumn() {
        Terrain3DColumn column = mock(Terrain3DColumn.class);
        when(column.shaped()).thenReturn(true);
        when(column.minY()).thenReturn(0);
        when(column.topY()).thenReturn(150);
        when(column.isSolid(anyInt())).thenAnswer(invocation -> invocation.<Integer>getArgument(0) != 120);
        when(column.displaced(anyInt(), anyInt())).thenReturn(column);
        return column;
    }

    private Map<Long, List<Double>> seamDamColumns(int[][] chunks) throws Exception {
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.seamWeight(anyInt(), anyInt())).thenAnswer(invocation -> oldChunkWeight(invocation.getArgument(0), invocation.getArgument(1)));
        Map<Long, List<Double>> columns = new HashMap<>();
        try (Seam seam = new Seam(displacement, natural -> new FakeHydrology(() -> false, natural, 127, 156))) {
            FakeHydrology hydrology = seam.hydrology;
            IrisComplex complex = seam.complex();
            int dams = 0;
            int wet = 0;
            for (int[] chunk : chunks) {
                ChunkWaterRaster raster = hydrology.runtime.raster(chunk[0], chunk[1]);
                for (int index = 0; index < 256; index++) {
                    int x = (chunk[0] << 4) + (index & 15);
                    int z = (chunk[1] << 4) + (index >> 4);
                    double ground = complex.resolveHydrologyTerrainHeight(x, z);
                    double head = complex.resolveWaterHead(x, z);
                    WaterRole role = complex.resolveWaterRole(x, z);
                    columns.put(((long) x << 32) | (z & 0xFFFFFFFFL), List.of(ground, head, (double) role.ordinal()));
                    if (head != 127D) {
                        wet++;
                        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            double neighbourHead = complex.resolveWaterHead(x + side[0], z + side[1]);
                            double neighbourGround = complex.resolveHydrologyTerrainHeight(x + side[0], z + side[1]);
                            assertTrue(x + "," + z + " leaks into " + (x + side[0]) + "," + (z + side[1]),
                                    neighbourHead == head || neighbourGround >= head);
                        }
                    }
                    boolean beside = oldChunkWeight(x + 1, z) == 0D || oldChunkWeight(x - 1, z) == 0D
                            || oldChunkWeight(x, z + 1) == 0D || oldChunkWeight(x, z - 1) == 0D;
                    if (raster.waterHead(index & 15, index >> 4) != Integer.MIN_VALUE && oldChunkWeight(x, z) > 0D && beside) {
                        dams++;
                        assertEquals(127D, head, 0D);
                        assertSame(WaterRole.NONE, role);
                        assertTrue(x + "," + z + " dam at " + ground, ground >= 157D);
                    }
                }
            }
            assertTrue(dams > 0);
            assertTrue(wet > 0);
        }
        return columns;
    }

    private static double oldChunkWeight(int x, int z) {
        int dx = x < 0 ? -x : Math.max(0, x - 15);
        int dz = z < 0 ? -z : Math.max(0, z - 15);
        double distance = Math.hypot(dx, dz);
        return distance <= 1D ? 0D : GenerationBlend.newEpochWeight(distance - 1D, TransitionDisplacementField.SEAM_BLEND_WIDTH);
    }

    private static double oldEastOfWeight(int x) {
        int distance = Math.max(0, OLD_EAST_OF - x);
        return distance <= 1 ? 0D : GenerationBlend.newEpochWeight(distance - 1D, TransitionDisplacementField.SEAM_BLEND_WIDTH);
    }

    private static int assertSeamStepContained(IrisComplex complex, FakeHydrology hydrology, int minX, int maxX, int minZ, int maxZ) {
        int raised = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int ground = (int) Math.round(complex.resolveHydrologyTerrainHeight(x, z));
                int head = effectiveHead(complex, x, z);
                int wettest = Integer.MIN_VALUE;
                for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int neighbour = effectiveHead(complex, x + side[0], z + side[1]);
                    wettest = Math.max(wettest, neighbour);
                    if (head != Integer.MIN_VALUE && neighbour == Integer.MIN_VALUE) {
                        double neighbourGround = complex.resolveHydrologyTerrainHeight(x + side[0], z + side[1]);
                        assertTrue(x + "," + z + " at " + head + " leaks into " + (x + side[0]) + "," + (z + side[1]),
                                neighbourGround >= head);
                    }
                }
                boolean rasterWet = hydrology.runtime.raster(x >> 4, z >> 4).waterHead(x & 15, z & 15) != Integer.MIN_VALUE;
                if (rasterWet && head == Integer.MIN_VALUE && wettest != Integer.MIN_VALUE) {
                    assertTrue(x + "," + z + " stands at " + ground + " beside water at " + wettest, ground >= wettest + 1);
                    raised++;
                }
            }
        }
        return raised;
    }

    private static int effectiveHead(IrisComplex complex, int x, int z) {
        return complex.resolveWaterRole(x, z).wet() ? (int) Math.round(complex.resolveWaterHead(x, z)) : Integer.MIN_VALUE;
    }

    private static void assertContained(IrisComplex complex, int x, int z, double ground) {
        double head = complex.resolveWaterHead(x, z);
        WaterRole role = complex.resolveWaterRole(x, z);
        if (head != 127D) {
            assertTrue(x + "," + z + " reports water at " + head + " over ground " + ground, ground < head);
        }
        if (role == WaterRole.POND || role == WaterRole.CHANNEL) {
            assertTrue(x + "," + z + " has a wet role over ground " + ground, head != 127D);
        }
    }

    private final class Seam implements AutoCloseable {
        private final TransitionDisplacementField displacement;
        private final TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        private final AtomicReference<IrisComplex> latest = new AtomicReference<>();
        private final FakeHydrology hydrology;
        private NaturalHeights natural = (x, z) -> 150.3D;
        private Terrain3DColumn terrain;

        private Seam(TransitionDisplacementField displacement, Function<IntBinaryOperator, FakeHydrology> hydrology) {
            this.displacement = displacement;
            when(plan.geometryAt(anyInt(), anyInt())).thenReturn(BoundaryGeometryInfluence.none());
            when(displacement.fluidHeight(anyInt(), anyInt(), anyDouble())).thenAnswer(invocation -> invocation.getArgument(2));
            this.hydrology = hydrology.apply((x, z) -> latest.get().naturalTrueHeight(x, z));
        }

        private Seam natural(NaturalHeights heights) {
            natural = heights;
            return this;
        }

        private Seam terrain(Terrain3DColumn column) {
            terrain = column;
            return this;
        }

        private IrisComplex complex() {
            IrisComplex complex = fixture.transitionComplex(hydrology.runtime, plan, displacement);
            NaturalHeights heights = natural;
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> heights.height((int) Math.floor(x), (int) Math.floor(z))));
            complex.setFluidHeight(127D);
            if (terrain != null) {
                Terrain3DRuntime terrain3D = mock(Terrain3DRuntime.class);
                when(terrain3D.active()).thenReturn(true);
                when(terrain3D.column(anyInt(), anyInt())).thenReturn(terrain);
                complex.setTerrain3D(terrain3D);
            }
            latest.set(complex);
            return complex;
        }

        @Override
        public void close() {
            hydrology.close();
        }
    }

    private static int[] carvedColumns(ChunkWaterRaster raster, int natural, int count) {
        int[] found = new int[count];
        int size = 0;
        for (int index = 0; index < 256 && size < count; index++) {
            if (raster.terrainHeight(index & 15, index >> 4) < natural) {
                found[size++] = index;
            }
        }
        assertEquals(count, size);
        return found;
    }

    private static int untouchedColumn(ChunkWaterRaster raster, int natural) {
        for (int index = 0; index < 256; index++) {
            if (raster.terrainHeight(index & 15, index >> 4) == natural) {
                return index;
            }
        }
        throw new AssertionError("Every column of the chunk was reshaped");
    }
}
