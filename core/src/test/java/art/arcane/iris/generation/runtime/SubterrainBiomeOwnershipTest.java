package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SubterrainBiomeOwnershipTest {
    @Test
    public void occupiedFeatureOwnsExactVolumeAndYWithoutSurfaceDepthGate() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("basin").setBiome("basin-biome").setProbability(1);
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 1191L, -64, 320, (x, z) -> "test-region"));
        SubterrainPlan plan = planner.plansForBounds(0, 0, 512, 512).getFirst();
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisComplex complex = mock(IrisComplex.class);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        IrisBiome biome = new IrisBiome();
        IrisDimension dimension = new IrisDimension().setSubterrainFeatures(new KList<>(feature));
        doReturn(dimension).when(engine).getDimension();
        doReturn(complex).when(engine).getComplex();
        doReturn(planner).when(complex).getSubterrainPlanner();
        doReturn(data).when(engine).getData();
        doReturn(mock(DimensionStackContext.class)).when(engine).getDimensionStackContext();
        doReturn(loader).when(data).getBiomeLoader();
        doReturn(biome).when(loader).load("basin-biome");
        doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(engine).getWorld();
        SubterrainPosition anchor = plan.anchor();
        int internalY = anchor.y() + 64;
        assertSame(biome, engine.getBiome(anchor.x(), internalY, anchor.z()));
        assertSame(biome, engine.getBiomeOrMantle(anchor.x(), internalY, anchor.z()));
        assertEquals(planner.sample(anchor.x(), anchor.y(), anchor.z()), engine.getSubterrainCell(anchor.x(), internalY, anchor.z()));
        assertNull(engine.getSubterrainBiome(anchor.x(), plan.bounds().maxY() + 65, anchor.z()));
        assertNull(engine.getSubterrainBiome(plan.bounds().maxX() + 1, internalY, anchor.z()));
        for (int y = plan.bounds().minY(); y <= plan.bounds().maxY(); y++) {
            SubterrainCell cell = planner.sample(anchor.x(), y, anchor.z());
            if (cell.solid()) {
                assertNull(engine.getSubterrainBiome(anchor.x(), y + 64, anchor.z()));
            }
        }
        verify(engine, never()).getMantle();
        verify(engine, never()).getDimensionStackContext();
    }
}
