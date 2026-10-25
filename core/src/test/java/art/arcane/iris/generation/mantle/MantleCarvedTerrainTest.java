package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.underground.CavePoolFiller;
import art.arcane.iris.generation.hydrology.underground.CaveVoxel;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MantleCarvedTerrainTest {
    private static final int HEIGHT = 64;
    private static final long SEED = 1337L;
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    @BeforeClass
    public static void registerMatter() {
        PreObjectMatterTest.setUpBukkit();
        IrisMatterSupport.ensureRegistered();
    }

    @Test
    public void terrainPhaseCavernsBecomeAirAndEverythingThePoolMustNotTouchIsForeign() {
        Scene scene = new Scene();
        Matter[] sections = scene.chunk(0, 0);
        cavern(sections, 1, 20, 1, (byte) 0);
        cavern(sections, 2, 20, 2, (byte) 1);
        cavern(sections, 3, 20, 3, (byte) 2);
        cavern(sections, 4, 5, 4, (byte) 0);
        cavern(sections, 7, 20, 7, (byte) 3);
        sections[1].<PreObjectMatterCell>slice(PreObjectMatterCell.class)
                .set(8, 4, 8, PreObjectMatterCell.cavern(new MatterCavern(true, "", (byte) 0)));
        cavern(sections, 9, 20, 9, (byte) 0);
        sections[1].<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(9, 4, 9, PreObjectMatterCell.cavern(null));
        IrisBiome flooded = new IrisBiome();
        flooded.setHydrologyPolicy(new IrisHydrologyPolicy().setFluid("lava"));
        when(scene.engine.getCaveBiome(1, 20, 1)).thenReturn(flooded);
        when(scene.engine.getCaveBiome(5, 20, 5)).thenReturn(new IrisBiome());

        MantleCarvedTerrain carved = scene.carved();

        assertEquals(40, carved.surface(3, 3));
        assertEquals(CaveVoxel.AIR, carved.voxel(1, 20, 1));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(2, 20, 2));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(3, 20, 3));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(4, 5, 4));
        assertEquals(CaveVoxel.SOLID, carved.voxel(5, 20, 5));
        assertEquals(CaveVoxel.AIR, carved.voxel(7, 20, 7));
        assertEquals("objects that filled a cavern later still read as terrain-phase air", CaveVoxel.AIR, carved.voxel(8, 20, 8));
        assertEquals("objects that carved later still read as terrain-phase rock", CaveVoxel.SOLID, carved.voxel(9, 20, 9));
        assertEquals(CaveVoxel.SOLID, carved.voxel(6, 40, 6));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(6, 41, 6));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(6, 0, 6));
        assertEquals("lava", carved.caveFluid(1, 20, 1));
        assertNull(carved.caveFluid(5, 20, 5));
        assertThrows(IllegalStateException.class, () -> carved.voxel(32, 20, 0));
        assertThrows(IllegalStateException.class, () -> carved.voxel(0, 20, -17));
    }

    @Test
    public void historicalAndAlreadyGeneratedChunksReadAsForeignNeverAsRock() {
        Scene scene = new Scene();
        cavern(scene.chunk(1, 0), 3, 20, 3, (byte) 0);
        cavern(scene.chunk(0, 1), 3, 20, 3, (byte) 0);
        scene.chunk(-1, 0);
        when(scene.complex.allowsMantleChunkWrite(1, 0)).thenReturn(false);
        scene.flag(0, 1, MantleFlag.REAL);

        MantleCarvedTerrain carved = scene.carved();

        assertEquals(CaveVoxel.FOREIGN, carved.voxel(19, 20, 3));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(20, 30, 4));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(3, 20, 19));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(4, 30, 20));
        assertEquals(CaveVoxel.SOLID, carved.voxel(-4, 30, 4));
    }

    @Test
    public void readingANeighbourBeforeItIsCarvedFailsTheChunk() {
        Scene scene = new Scene();
        scene.chunk(-1, 0);
        scene.unflag(-1, 0, MantleFlag.CARVED);

        MantleCarvedTerrain carved = scene.carved();

        assertThrows(IllegalStateException.class, () -> carved.voxel(-3, 20, 3));
    }

    @Test
    public void subterrainFeatureCellsAndTerrain3DVoidsReadAsForeign() {
        Scene scene = new Scene();
        cavern(scene.chunk(0, 0), 2, 20, 2, (byte) 0);
        SubterrainPlanner planner = mock(SubterrainPlanner.class);
        List<SubterrainPlan> plans = List.of(mock(SubterrainPlan.class));
        when(planner.isEmpty()).thenReturn(false);
        when(planner.plansForBounds(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(plans);
        when(planner.sample(eq(plans), anyInt(), anyInt(), anyInt())).thenReturn(SubterrainCell.OUTSIDE);
        when(planner.sample(plans, 2, 20, 2)).thenReturn(new SubterrainCell(SubterrainCell.Kind.AIR, "", null));
        when(planner.sample(plans, 3, 21, 3)).thenReturn(new SubterrainCell(SubterrainCell.Kind.SOLID, "minecraft:stone", null));
        when(scene.complex.getSubterrainPlanner()).thenReturn(planner);
        when(scene.complex.hasTerrain3D()).thenReturn(true);
        when(scene.complex.terrainColumn(anyInt(), anyInt())).thenReturn(null);
        when(scene.complex.terrainColumn(5, 5)).thenReturn(Terrain3DColumn.fromOccupancy(40D, HEIGHT, y -> y <= 40 && y != 25));

        MantleCarvedTerrain carved = scene.carved();

        assertEquals(CaveVoxel.FOREIGN, carved.voxel(2, 20, 2));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(3, 21, 3));
        assertEquals(CaveVoxel.FOREIGN, carved.voxel(5, 25, 5));
        assertEquals(CaveVoxel.SOLID, carved.voxel(5, 24, 5));
        assertEquals(CaveVoxel.SOLID, carved.voxel(4, 25, 4));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void provisionalSurfaceAnswersFailTheChunk() {
        Scene scene = new Scene();
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenAnswer(invocation -> {
            ProvisionalSampling.mark();
            return 40D;
        });
        when(scene.complex.getHeightStream()).thenReturn(heights);

        MantleCarvedTerrain carved = scene.carved();

        assertThrows(IllegalStateException.class, () -> carved.surface(1, 1));
        assertThrows(IllegalStateException.class, () -> carved.voxel(1, 20, 1));
    }

    @Test
    public void inletSpaceFromEveryNeighbourChunkReadsAsForeignAndPoolsBesideItStayOutInEitherChunkOrder() {
        Scene probe = inletScene();
        MantleCarvedTerrain carved = probe.carved();
        assertEquals("passage water", CaveVoxel.FOREIGN, carved.voxel(7, 28, 21));
        assertEquals("passage air", CaveVoxel.FOREIGN, carved.voxel(10, 33, 21));
        assertEquals("seal under the passage", CaveVoxel.FOREIGN, carved.voxel(10, 24, 21));
        assertEquals("seal beside the passage", CaveVoxel.FOREIGN, carved.voxel(7, 25, 18));
        assertEquals(CaveVoxel.SOLID, carved.voxel(7, 23, 18));
        assertEquals(CaveVoxel.AIR, carved.voxel(7, 25, 17));
        assertEquals(CaveVoxel.SOLID, carved.voxel(10, 34, 21));
        assertEquals(CaveVoxel.SOLID, carved.voxel(10, 25, 26));

        Map<Long, HydrologyCaveCell> forward = inletScene().generate(0, 1);
        Map<Long, HydrologyCaveCell> reverse = inletScene().generate(1, 0);

        assertEquals(forward, reverse);
        int top = Integer.MIN_VALUE;
        for (Map.Entry<Long, HydrologyCaveCell> entry : forward.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.POOL_SOURCE) {
                continue;
            }
            long cell = entry.getKey();
            top = Math.max(top, cellY(cell));
            for (int[] face : FACES) {
                HydrologyCaveCell neighbour = forward.get(cell(cellX(cell) + face[0], cellY(cell) + face[1], cellZ(cell) + face[2]));
                assertFalse("pool water at " + cellX(cell) + "," + cellY(cell) + "," + cellZ(cell) + " touches the inlet",
                        neighbour != null && HydrologyCaveCell.SEA_FLUID.equals(neighbour.fluidProfileKey()));
            }
        }
        assertEquals("the pool stops one level below the inlet seal its next level would reach", 24, top);
        for (int y = 24; y <= 30; y++) {
            assertInlet(forward, 7, y, 19, HydrologyCaveAction.SEAL_GUARD);
        }
        for (int y = 27; y <= 30; y++) {
            assertInlet(forward, 7, y, 21, HydrologyCaveAction.WET_SOURCE);
        }
        for (int y = 31; y <= 33; y++) {
            assertInlet(forward, 7, y, 21, HydrologyCaveAction.DRY_AIR);
        }
    }

    private static Scene inletScene() {
        Scene scene = new Scene();
        Matter[] room = scene.chunk(0, 0);
        for (int x = 2; x <= 13; x++) {
            for (int z = 4; z <= 15; z++) {
                for (int y = 22; y <= 36; y++) {
                    cavern(room, x, y, z, (byte) 0);
                }
            }
        }
        Matter[] channel = scene.chunk(0, 1);
        for (int x = 6; x <= 8; x++) {
            for (int z = 16; z <= 19; z++) {
                for (int y = 25; y <= 30; y++) {
                    cavern(channel, x, y, z, (byte) 0);
                }
            }
        }
        scene.inlets.add(new SeaInlet(77L, new float[]{40f, -8f}, new float[]{22f, 22f}, new float[]{2f, 2f}, 30, new int[]{26, 26},
                new int[]{33, 33}, -8, 22, 2));
        return scene;
    }

    private static void assertInlet(Map<Long, HydrologyCaveCell> written, int x, int y, int z, HydrologyCaveAction action) {
        HydrologyCaveCell cell = written.get(cell(x, y, z));
        assertTrue("inlet " + action + " at " + x + "," + y + "," + z + " but found " + cell,
                cell != null && cell.action() == action && HydrologyCaveCell.SEA_FLUID.equals(cell.fluidProfileKey()));
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) y << 20) | (z + 4096);
    }

    private static int cellX(long cell) {
        return (int) (cell >>> 40) - 4096;
    }

    private static int cellZ(long cell) {
        return (int) (cell & 0xFFFFF) - 4096;
    }

    private static int cellY(long cell) {
        return (int) ((cell >>> 20) & 0xFFFFF);
    }

    private static void cavern(Matter[] sections, int x, int y, int z, byte liquid) {
        sections[y >> 4].<MatterCavern>slice(MatterCavern.class).set(x & 15, y & 15, z & 15, new MatterCavern(true, "", liquid));
    }

    private static final class Scene {
        private final Map<Long, MantleChunk<Matter>> chunks = new HashMap<>();
        private final Map<Long, Matter[]> sections = new HashMap<>();
        private final MantleWriter writer = mock(MantleWriter.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final Engine engine = mock(Engine.class);
        private final List<SeaInlet> inlets = new ArrayList<>();
        private final Map<Long, HydrologyCaveCell> cells = new HashMap<>();

        @SuppressWarnings("unchecked")
        private Scene() {
            Mantle<Matter> mantle = mock(Mantle.class);
            when(mantle.getWorldHeight()).thenReturn(HEIGHT);
            when(writer.getMantle()).thenReturn(mantle);
            when(writer.acquireChunk(anyInt(), anyInt())).thenAnswer(invocation -> mantleChunk(invocation.getArgument(0), invocation.getArgument(1)));
            ProceduralStream<Double> heights = mock(ProceduralStream.class);
            when(heights.getDouble(anyDouble(), anyDouble())).thenReturn(40.4D);
            when(complex.getHeightStream()).thenReturn(heights);
            when(complex.allowsMantleChunkWrite(anyInt(), anyInt())).thenReturn(true);
            HydrologyRuntime runtime = mock(HydrologyRuntime.class);
            when(runtime.caveFluidPolicies()).thenReturn(true);
            when(runtime.inlets(anyInt(), anyInt())).thenAnswer(invocation -> touching(invocation.getArgument(0), invocation.getArgument(1)));
            doAnswer(invocation -> {
                cells.put(cell(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)), invocation.getArgument(3));
                return null;
            }).when(writer).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
            when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class))).thenAnswer(invocation ->
                    cells.get(cell(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(2).run();
                return null;
            }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
            when(complex.getHydrologyRuntime()).thenReturn(runtime);
            when(engine.getDimension()).thenReturn(new IrisDimension());
            when(engine.getWorld()).thenReturn(mock(IrisWorld.class));
            when(engine.getCaveBiome(anyInt(), anyInt(), anyInt())).thenReturn(null);
        }

        private MantleCarvedTerrain carved() {
            return new MantleCarvedTerrain(writer, complex, engine, 0, 0);
        }

        private List<SeaInlet> touching(int chunkX, int chunkZ) {
            List<SeaInlet> touching = new ArrayList<>();
            for (SeaInlet inlet : inlets) {
                if (SeaInletRasterizer.touches(inlet, chunkX, chunkZ)) {
                    touching.add(inlet);
                }
            }
            return touching;
        }

        private Map<Long, HydrologyCaveCell> generate(int... chunkZs) {
            IrisHydrology config = new IrisHydrology().setEnabled(true);
            config.getUnderground().getCavePools().setDensity(8D).setFreeboard(new KList<>(0, 0));
            HydrologySettings settings = HydrologySettings.compile(config, SEED, 63, 0, 384);
            SeaInletRasterizer rasterizer = new SeaInletRasterizer();
            for (int chunkZ : chunkZs) {
                for (SeaInlet inlet : complex.getHydrologyRuntime().inlets(0, chunkZ)) {
                    rasterizer.write(inlet, 0, chunkZ, writer, HydrologyCaveCell.SEA_FLUID, "", SeaInletRasterizer.SubterrainFluid.NOWHERE);
                }
                new CavePoolFiller(settings).fill(0, chunkZ, new MantleCarvedTerrain(writer, complex, engine, 0, chunkZ), writer, SEED);
            }
            return cells;
        }

        private Matter[] chunk(int chunkX, int chunkZ) {
            mantleChunk(chunkX, chunkZ);
            return sections.get(key(chunkX, chunkZ));
        }

        @SuppressWarnings("unchecked")
        private MantleChunk<Matter> mantleChunk(int chunkX, int chunkZ) {
            return chunks.computeIfAbsent(key(chunkX, chunkZ), ignored -> {
                Matter[] layers = new Matter[HEIGHT >> 4];
                MantleChunk<Matter> chunk = mock(MantleChunk.class);
                for (int section = 0; section < layers.length; section++) {
                    layers[section] = new IrisMatter(16, 16, 16);
                    when(chunk.exists(section)).thenReturn(true);
                    when(chunk.get(section)).thenReturn(layers[section]);
                }
                when(chunk.isFlagged(any(MantleFlag.class))).thenReturn(false);
                when(chunk.isFlagged(MantleFlag.CARVED)).thenReturn(true);
                sections.put(key(chunkX, chunkZ), layers);
                return chunk;
            });
        }

        private void flag(int chunkX, int chunkZ, MantleFlag flag) {
            when(mantleChunk(chunkX, chunkZ).isFlagged(flag)).thenReturn(true);
        }

        private void unflag(int chunkX, int chunkZ, MantleFlag flag) {
            when(mantleChunk(chunkX, chunkZ).isFlagged(flag)).thenReturn(false);
        }

        private static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }
    }
}
