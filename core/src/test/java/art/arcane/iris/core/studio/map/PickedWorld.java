package art.arcane.iris.core.studio.map;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.biome.IrisBiomeEntry;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.region.IrisRegionEntry;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.world.Weighted;

import java.awt.Color;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A world that picks the same way at every spot: a dry region (weight 1 against 3, so the last quarter of the region
 * noise) picked at 0.9; its land list picks plains at 0.2; plains' child noise at 0.1 lands in its first quarter,
 * meadow; meadow's at 0.8 lands in its last half, itself.
 */
final class PickedWorld {
    final Engine engine = mock(Engine.class);
    final IrisComplex complex = mock(IrisComplex.class);
    final IrisBiome plains = biome("plains", "Plains");
    final IrisBiome forest = biome("forest", "Forest");
    final IrisBiome meadow = biome("meadow", "Meadow");
    final IrisBiome glade = biome("glade", "Glade");
    final IrisRegion warm = region("warm", "Warm");
    final IrisRegion dry = region("dry", "Dry");
    final IrisImageMapRuntime imageMap = mock(IrisImageMapRuntime.class);

    PickedWorld() {
        when(engine.getComplex()).thenReturn(complex);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getRegions()).thenReturn(new KList<>(List.of(new IrisRegionEntry("warm", 3), new IrisRegionEntry("dry", 1))));
        when(engine.getDimension()).thenReturn(dimension);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        when(regionLoader.load("warm")).thenReturn(warm);
        when(regionLoader.load("dry")).thenReturn(dry);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(engine.getData()).thenReturn(data);
        when(complex.getImageMapRuntime()).thenReturn(imageMap);

        ProceduralStream<IrisRegion> regions = stream(dry);
        ProceduralStream<Double> regionNoise = stream(0.9D);
        ProceduralStream<InferredType> continents = stream(InferredType.LAND);
        ProceduralStream<Double> landNoise = stream(0.2D);
        ProceduralStream<IrisBiome> landPicks = stream(plains);
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getRegionStyleStream()).thenReturn(regionNoise);
        when(complex.getBridgeStream()).thenReturn(continents);
        when(complex.pickableBiomes(dry, InferredType.LAND)).thenReturn(new KList<>(List.of(new Weighted<>(plains, 1), new Weighted<>(forest, 1))));
        when(complex.biomePickNoise(dry, InferredType.LAND)).thenReturn(landNoise);
        when(complex.biomePicks(dry, InferredType.LAND)).thenReturn(landPicks);
        children(plains, List.of(new Weighted<>(meadow, 1), new Weighted<>(plains, 3)), 0.1D);
        children(meadow, List.of(new Weighted<>(glade, 1), new Weighted<>(meadow, 1)), 0.8D);
    }

    private void children(IrisBiome parent, List<Weighted<IrisBiome>> options, double noise) {
        when(parent.getChildren()).thenReturn(new KList<>(List.of(new IrisBiomeEntry("child", 1))));
        when(complex.childOptions(parent)).thenReturn(new KList<>(options));
        CNG childNoise = mock(CNG.class);
        when(childNoise.noiseFast2D(anyDouble(), anyDouble())).thenReturn(noise);
        when(complex.childNoise(parent)).thenReturn(childNoise);
    }

    private IrisBiome biome(String key, String name) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        when(biome.getName()).thenReturn(name);
        when(biome.getChildren()).thenReturn(new KList<>());
        when(biome.getInferredType()).thenReturn(InferredType.LAND);
        when(biome.withInferredType(any(InferredType.class))).thenReturn(biome);
        when(biome.getColor(eq(engine), any(RenderType.class))).thenReturn(Color.GREEN);
        return biome;
    }

    private IrisRegion region(String key, String name) {
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn(key);
        when(region.getName()).thenReturn(name);
        when(region.getColor(complex, RenderType.REGION)).thenReturn(Color.ORANGE);
        return region;
    }

    @SuppressWarnings("unchecked")
    static <T> ProceduralStream<T> stream(T value) {
        ProceduralStream<T> stream = mock(ProceduralStream.class);
        when(stream.get(anyDouble(), anyDouble())).thenReturn(value);
        return stream;
    }
}
