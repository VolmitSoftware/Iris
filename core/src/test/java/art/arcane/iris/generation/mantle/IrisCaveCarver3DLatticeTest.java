package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class IrisCaveCarver3DLatticeTest {
    private static final long CARVE_SEED = 7_340_119_802_557L;
    private static final int WORLD_HEIGHT = 384;

    @Test
    public void batchedLatticePointsMatchScalarPlaneDensityBitForBit() throws Exception {
        Engine engine = createEngine();
        CaveDensityGrid grid = new CaveDensityGrid(256);
        IrisCaveProfile[] profiles = {
                profile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D, NoiseStyle.PERLIN_IRIS, 0.72D,
                        module(NoiseStyle.FRACTAL_FBM_SIMPLEX, 0.24D, 10D, 140D, false),
                        module(NoiseStyle.CELLULAR_BILINEAR, 1.32D, 4D, 300D, true)),
                profile(NoiseStyle.PERLIN_IRIS, 0.35D, 2.4D, NoiseStyle.IRIS_DOUBLE, 0.68D,
                        module(NoiseStyle.VASCULAR_THIN, 1.28D, 8D, 220D, false)),
                profile(NoiseStyle.SIMPLEX, 0.5D, 0D, NoiseStyle.FRACTAL_BILLOW_SIMPLEX, 0.656D)
        };
        Method sampleLatticePoints = IrisCaveCarver3D.class.getDeclaredMethod("sampleLatticePoints",
                CaveLatticeBatch.class, CaveDensityLattice.class, int.class, int.class, int.class);
        sampleLatticePoints.setAccessible(true);
        Method samplePlaneDensity = IrisCaveCarver3D.class.getDeclaredMethod("samplePlaneDensity",
                CaveCarveScratch.class, int.class, int.class, int.class);
        samplePlaneDensity.setAccessible(true);
        Field warpOwnerField = IrisCaveCarver3D.class.getDeclaredField("warpOwner");
        warpOwnerField.setAccessible(true);
        Random random = new Random(91_337L);
        for (IrisCaveProfile profile : profiles) {
            IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile, grid);
            CaveDensityGrid.Owner warpOwner = (CaveDensityGrid.Owner) warpOwnerField.get(carver);
            CaveLatticeBatch batch = new CaveLatticeBatch();
            CaveDensityLattice lattice = new CaveDensityLattice();
            CaveCarveScratch reference = new CaveCarveScratch();
            for (int trial = 0; trial < 24; trial++) {
                int latticeX = random.nextInt(4001) - 2000;
                int latticeZ = random.nextInt(4001) - 2000;
                int x0 = latticeX << CaveDensityGrid.LATTICE_SHIFT;
                int z0 = latticeZ << CaveDensityGrid.LATTICE_SHIFT;
                for (int corner = 0; corner < CaveDensityLattice.CORNERS; corner++) {
                    lattice.warpColumns[corner] = (trial + corner) % 3 == 0 ? null
                            : grid.warpColumn(warpOwner, WORLD_HEIGHT + 1, latticeX + corner / 3, latticeZ + corner % 3);
                }
                int count = selectRows(random, batch);
                for (int pass = 0; pass < 2; pass++) {
                    sampleLatticePoints.invoke(carver, batch, lattice, x0, z0, count);
                    for (int corner = 0; corner < CaveDensityLattice.CORNERS; corner++) {
                        int x = x0 + (corner / 3) * 8;
                        int z = z0 + (corner % 3) * 8;
                        for (int index = batch.cornerStart[corner]; index < batch.cornerStart[corner + 1]; index++) {
                            int y = batch.y[index];
                            double expected = (double) samplePlaneDensity.invoke(carver, reference, x, y, z);
                            assertEquals("x=" + x + " y=" + y + " z=" + z + " pass=" + pass,
                                    Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(batch.density[index]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void identicalFieldsShareGridOwnersAcrossProfiles() throws Exception {
        Engine engine = createEngine();
        CaveDensityGrid grid = new CaveDensityGrid(256);
        Field densityOwnerField = IrisCaveCarver3D.class.getDeclaredField("densityOwner");
        densityOwnerField.setAccessible(true);
        Field warpOwnerField = IrisCaveCarver3D.class.getDeclaredField("warpOwner");
        warpOwnerField.setAccessible(true);
        IrisCaveCarver3D first = new IrisCaveCarver3D(engine, profile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D,
                NoiseStyle.PERLIN_IRIS, 0.72D, module(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 4D, 90D, true)), grid);
        IrisCaveCarver3D sameDensity = new IrisCaveCarver3D(engine, profile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D,
                NoiseStyle.PERLIN_IRIS, 0.72D, module(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 4D, 90D, true))
                .setDensityThreshold(new IrisStyledRange(0.2D, 0.2D, new IrisGeneratorStyle(NoiseStyle.FLAT))), grid);
        IrisCaveCarver3D sameWarp = new IrisCaveCarver3D(engine, profile(NoiseStyle.FRACTAL_WATER, 0.44D, 1.7D,
                NoiseStyle.FRACTAL_BILLOW_SIMPLEX, 0.656D), grid);
        IrisCaveCarver3D otherWarp = new IrisCaveCarver3D(engine, profile(NoiseStyle.FRACTAL_WATER, 0.46D, 3.2D,
                NoiseStyle.PERLIN_IRIS, 0.72D, module(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 4D, 90D, true)), grid);
        IrisCaveCarver3D otherModule = new IrisCaveCarver3D(engine, profile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D,
                NoiseStyle.PERLIN_IRIS, 0.72D, module(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 4D, 91D, true)), grid);
        assertSame(densityOwnerField.get(first), densityOwnerField.get(sameDensity));
        assertSame(warpOwnerField.get(first), warpOwnerField.get(sameDensity));
        assertSame(warpOwnerField.get(first), warpOwnerField.get(sameWarp));
        assertSame(warpOwnerField.get(first), warpOwnerField.get(otherModule));
        assertNotSame(densityOwnerField.get(first), densityOwnerField.get(sameWarp));
        assertNotSame(densityOwnerField.get(first), densityOwnerField.get(otherModule));
        assertNotSame(densityOwnerField.get(first), densityOwnerField.get(otherWarp));
        assertNotSame(warpOwnerField.get(first), warpOwnerField.get(otherWarp));
    }

    private static int selectRows(Random random, CaveLatticeBatch batch) {
        batch.ensure(WORLD_HEIGHT * CaveDensityLattice.CORNERS);
        int count = 0;
        for (int corner = 0; corner < CaveDensityLattice.CORNERS; corner++) {
            batch.cornerStart[corner] = count;
            if (random.nextInt(4) == 0) {
                continue;
            }
            int start = random.nextInt(40);
            int end = start + random.nextInt(WORLD_HEIGHT - start);
            for (int y = start; y <= end; y++) {
                if (random.nextInt(5) != 0) {
                    batch.y[count++] = y;
                }
            }
        }
        batch.cornerStart[CaveDensityLattice.CORNERS] = count;
        return count;
    }

    private static IrisCaveProfile profile(NoiseStyle warp, double warpZoom, double warpStrength, NoiseStyle base,
                                           double baseZoom, IrisCaveFieldModule... modules) {
        IrisCaveProfile profile = new IrisCaveProfile();
        profile.setEnabled(true);
        profile.setVerticalRange(new IrisRange(4D, 360D));
        profile.setWarpStyle(new IrisGeneratorStyle(warp).zoomed(warpZoom));
        profile.setWarpStrength(warpStrength);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(base).zoomed(baseZoom));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.5D));
        profile.setSurfaceBreakStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.82D));
        profile.setBaseWeight(0.82D);
        profile.setDetailWeight(0.15D);
        profile.setDensityThreshold(new IrisStyledRange(-0.1D, 0.02D, new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.78D)));
        profile.setSampleStep(2);
        profile.setModules(new KList<>(modules));
        return profile;
    }

    private static IrisCaveFieldModule module(NoiseStyle style, double zoom, double minY, double maxY, boolean invert) {
        return new IrisCaveFieldModule(new IrisGeneratorStyle(style).zoomed(zoom), 0.07D, 0.04D, new IrisRange(minY, maxY), invert);
    }

    private static Engine createEngine() {
        Engine engine = mock(Engine.class, withSettings().stubOnly());
        IrisData data = mock(IrisData.class, withSettings().stubOnly());
        IrisDimension dimension = mock(IrisDimension.class, withSettings().stubOnly());
        SeedManager seedManager = mock(SeedManager.class, withSettings().stubOnly());
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(CARVE_SEED).when(seedManager).getCarve();
        doReturn(new EngineMetrics(16)).when(engine).getMetrics();
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(WORLD_HEIGHT - 64).build()).when(engine).getWorld();
        doReturn(WORLD_HEIGHT).when(engine).getHeight();
        doReturn(200).when(engine).getHeight(anyInt(), anyInt());
        doReturn(20).when(dimension).getCaveLavaHeight();
        doReturn(64).when(dimension).getFluidHeight();
        doReturn(1).when(dimension).getCaveDensitySamplingStep();
        return engine;
    }
}
