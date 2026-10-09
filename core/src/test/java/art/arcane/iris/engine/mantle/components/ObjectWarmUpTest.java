package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.procedural.IrisProceduralPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Object placing builds its placements' noise and its procedural objects' variants before generation asks. */
public class ObjectWarmUpTest {
    @Test
    public void everyPlacementAndProceduralObjectOfBiomesAndRegionsIsBuilt() {
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        IrisObjectPlacement biomePlacement = mock(IrisObjectPlacement.class);
        IrisObjectPlacement regionPlacement = mock(IrisObjectPlacement.class);
        IrisProceduralPlacement biomeTree = mock(IrisProceduralPlacement.class);
        IrisProceduralPlacement regionTree = mock(IrisProceduralPlacement.class);
        KList<IrisObjectPlacement> biomePlacements = list(biomePlacement, null);
        KList<IrisObjectPlacement> regionPlacements = list(regionPlacement);
        IrisProceduralObjects biomeProcedural = procedural(biomeTree);
        IrisProceduralObjects regionProcedural = procedural(regionTree);
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn("biome");
        when(biome.getObjects()).thenReturn(biomePlacements);
        when(biome.getProceduralObjects()).thenReturn(biomeProcedural);
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn("region");
        when(region.getObjects()).thenReturn(regionPlacements);
        when(region.getProceduralObjects()).thenReturn(regionProcedural);
        when(engine.getAllBiomes()).thenReturn(new KList<>(List.of(biome)));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>(List.of(region)));

        new ObjectWarmUp(engine).warm();

        for (IrisObjectPlacement placement : List.of(biomePlacement, regionPlacement)) {
            verify(placement).getSurfaceWarp(any(RNG.class), eq(data), eq(engine));
            verify(placement).getDensity(any(RNG.class), anyDouble(), anyDouble(), eq(data));
        }
        verify(biomeTree).getVariantObject(eq(data), any(RNG.class));
        verify(regionTree).getVariantObject(eq(data), any(RNG.class));
    }

    private static KList<IrisObjectPlacement> list(IrisObjectPlacement... placements) {
        KList<IrisObjectPlacement> list = new KList<>();
        for (IrisObjectPlacement placement : placements) {
            list.add(placement);
        }
        return list;
    }

    private static IrisProceduralObjects procedural(IrisProceduralPlacement placement) {
        IrisProceduralObjects objects = mock(IrisProceduralObjects.class);
        KList<IrisProceduralPlacement> placements = new KList<>(List.of(placement));
        when(objects.getAllPlacements()).thenReturn(placements);
        return objects;
    }
}
