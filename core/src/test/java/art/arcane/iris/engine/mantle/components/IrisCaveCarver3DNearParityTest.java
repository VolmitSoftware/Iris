package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.engine.object.IrisStyledRange;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCaveCarver3DNearParityTest {
    @BeforeClass
    public static void setupBukkit() {
        if (Bukkit.getServer() != null) {
            return;
        }

        Server server = mock(Server.class);
        BlockData emptyBlockData = mock(BlockData.class);
        doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
        doReturn("IrisTestServer").when(server).getName();
        doReturn("1.0").when(server).getVersion();
        doReturn("1.0").when(server).getBukkitVersion();
        doReturn(emptyBlockData).when(server).createBlockData(any(Material.class));
        doReturn(emptyBlockData).when(server).createBlockData(anyString());
        Bukkit.setServer(server);
    }

    @Test
    public void carvedCellDistributionStableAcrossEquivalentCarvers() {
        Engine engine = createEngine(128, 92);

        IrisCaveCarver3D firstCarver = new IrisCaveCarver3D(engine, createProfile());
        WriterCapture firstCapture = createWriterCapture(128);
        int firstCarved = firstCarver.carve(firstCapture.writer, 7, -3);

        IrisCaveCarver3D secondCarver = new IrisCaveCarver3D(engine, createProfile());
        WriterCapture secondCapture = createWriterCapture(128);
        int secondCarved = secondCarver.carve(secondCapture.writer, 7, -3);

        assertTrue(firstCarved > 0);
        assertEquals(firstCarved, secondCarved);
        assertEquals(firstCapture.carvedCells, secondCapture.carvedCells);
    }

    @Test
    public void latticePathCarvesChunkEdgesAndRespectsWorldHeightClipping() {
        Engine engine = createEngine(48, 46);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, createProfile());
        WriterCapture capture = createWriterCapture(48);
        double[] columnWeights = new double[256];
        Arrays.fill(columnWeights, 1D);
        int[] precomputedSurfaceHeights = new int[256];
        Arrays.fill(precomputedSurfaceHeights, 46);

        int carved = carver.carve(capture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        assertTrue(carved > 0);
        assertTrue(hasX(capture.carvedCells, 14));
        assertTrue(hasX(capture.carvedCells, 15));
        assertTrue(hasZ(capture.carvedCells, 14));
        assertTrue(hasZ(capture.carvedCells, 15));
        assertTrue(maxY(capture.carvedCells) <= 47);
        assertTrue(minY(capture.carvedCells) >= 0);
    }

    private Engine createEngine(int worldHeight, int sampledHeight) {
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        SeedManager seedManager = new SeedManager(942_337_445L);
        EngineMetrics metrics = new EngineMetrics(16);
        IrisWorld world = IrisWorld.builder().minHeight(0).maxHeight(worldHeight).build();

        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(metrics).when(engine).getMetrics();
        doReturn(world).when(engine).getWorld();
        doReturn(sampledHeight).when(engine).getHeight(anyInt(), anyInt());

        doReturn(18).when(dimension).getCaveLavaHeight();
        doReturn(64).when(dimension).getFluidHeight();

        return engine;
    }

    private IrisCaveProfile createProfile() {
        IrisCaveProfile profile = new IrisCaveProfile();
        profile.setEnabled(true);
        profile.setVerticalRange(new IrisRange(0D, 120D));
        profile.setVerticalEdgeFade(14);
        profile.setVerticalEdgeFadeStrength(0.21D);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.07D));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.17D));
        profile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.12D));
        profile.setSurfaceBreakStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.09D));
        profile.setBaseWeight(1D);
        profile.setDetailWeight(0.48D);
        profile.setWarpStrength(0.37D);
        profile.setDensityThreshold(new IrisStyledRange(1D, 1D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
        profile.setThresholdBias(0D);
        profile.setSampleStep(2);
        profile.setMinCarveCells(0);
        profile.setRecoveryThresholdBoost(0D);
        profile.setSurfaceClearance(5);
        profile.setAllowSurfaceBreak(true);
        profile.setSurfaceBreakNoiseThreshold(0.16D);
        profile.setSurfaceBreakDepth(12);
        profile.setSurfaceBreakThresholdBoost(0.17D);
        profile.setAllowWater(true);
        profile.setWaterMinDepthBelowSurface(8);
        profile.setWaterRequiresFloor(false);
        profile.setAllowLava(true);
        return profile;
    }

    private WriterCapture createWriterCapture(int worldHeight) {
        MantleWriter writer = mock(MantleWriter.class);
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        @SuppressWarnings("unchecked")
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Map<Integer, Matter> sections = new HashMap<>();
        Map<Integer, Map<Integer, MatterCavern>> sectionCells = new HashMap<>();
        Set<String> carvedCells = new HashSet<>();

        doReturn(mantle).when(writer).getMantle();
        doReturn(worldHeight).when(mantle).getWorldHeight();
        doReturn(chunk).when(writer).acquireChunk(anyInt(), anyInt());
        doAnswer(invocation -> {
            int sectionIndex = invocation.getArgument(0);
            Matter section = sections.get(sectionIndex);
            if (section != null) {
                return section;
            }

            Matter created = createSection(sectionIndex, sectionCells, carvedCells);
            sections.put(sectionIndex, created);
            return created;
        }).when(chunk).getOrCreate(anyInt());

        return new WriterCapture(writer, carvedCells);
    }

    private Matter createSection(int sectionIndex, Map<Integer, Map<Integer, MatterCavern>> sectionCells, Set<String> carvedCells) {
        Matter matter = mock(Matter.class);
        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> slice = mock(MatterSlice.class);
        Map<Integer, MatterCavern> localCells = sectionCells.computeIfAbsent(sectionIndex, key -> new HashMap<>());

        doReturn(slice).when(matter).slice(MatterCavern.class);
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            return localCells.get(packLocal(localX, localY, localZ));
        }).when(slice).get(anyInt(), anyInt(), anyInt());
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            MatterCavern value = invocation.getArgument(3);
            localCells.put(packLocal(localX, localY, localZ), value);
            int worldY = (sectionIndex << 4) + localY;
            carvedCells.add(cellKey(localX, worldY, localZ));
            return null;
        }).when(slice).set(anyInt(), anyInt(), anyInt(), any(MatterCavern.class));

        return matter;
    }

    private int packLocal(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    private String cellKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private boolean hasX(Set<String> carvedCells, int x) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[0]) == x) {
                return true;
            }
        }

        return false;
    }

    private boolean hasZ(Set<String> carvedCells, int z) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[2]) == z) {
                return true;
            }
        }

        return false;
    }

    private int maxY(Set<String> carvedCells) {
        int max = Integer.MIN_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y > max) {
                max = y;
            }
        }
        return max;
    }

    private int minY(Set<String> carvedCells) {
        int min = Integer.MAX_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y < min) {
                min = y;
            }
        }
        return min;
    }

    private static final class WriterCapture {
        private final MantleWriter writer;
        private final Set<String> carvedCells;

        private WriterCapture(MantleWriter writer, Set<String> carvedCells) {
            this.writer = writer;
            this.carvedCells = carvedCells;
        }
    }
}
