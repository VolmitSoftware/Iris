package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.generation.BiomeBuffetLayout;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;

public final class RegionSelection {
    private RegionSelection() {
    }

    public static ProceduralStream<IrisRegion> select(Options options) {
        if (options.focused() != null) {
            return options.focused();
        }
        KList<IrisRegion> regions = new KList<>();
        for (IrisRegion region : options.data().getRegionLoader().loadAll(options.dimension().getRegions())) {
            if (!region.isCompatExcluded()) {
                regions.add(region);
            }
        }
        ProceduralStream<IrisRegion> selected = options.style().selectRarity(regions);
        if (!options.imageMaps().has(IrisImageMapApplication.REGION)) {
            return selected;
        }
        return selected.convertAware2D((region, x, z) -> {
            IrisRegion mapped = options.imageMaps().sampleRegion(x, z);
            return mapped == null ? region : mapped;
        });
    }

    public static ProceduralStream<IrisRegion> restore(Snapshot snapshot) {
        IrisData data = snapshot.data();
        IrisDimension dimension = snapshot.dimension();
        ProceduralStream<IrisRegion> focused = null;
        if (snapshot.buffet() && dimension.getStudioMode().biomeSizeChunks() > 0) {
            BiomeBuffetLayout layout = new BiomeBuffetLayout(dimension, () -> data);
            if (!layout.cells().isEmpty()) {
                focused = ProceduralStream.of((x, z) -> layout.terrain(x, z).region(),
                        Interpolated.of(value -> 0D, value -> layout.cells().getFirst().region()));
            }
        }
        if (focused == null) {
            IrisRegion focus = focusRegion(data, dimension);
            if (focus != null) {
                focused = ProceduralStream.of((x, z) -> focus, Interpolated.of(value -> 0D, value -> focus));
            }
        }
        return select(new Options(dimension, data,
                dimension.getRegionStyle().create(new RNG(snapshot.complexSeed()).nextParallelRNG(883), data)
                        .stream().zoom(dimension.getRegionZoom()),
                IrisImageMapRuntime.compile(data, dimension, dimension.getMinHeight()), focused));
    }

    private static IrisRegion focusRegion(IrisData data, IrisDimension dimension) {
        if (dimension.getFocus() != null && !dimension.getFocus().isBlank()) {
            IrisBiome focus = data.getBiomeLoader().load(dimension.getFocus());
            if (focus != null && !focus.isCompatExcluded()) {
                return dimension.resolveFocusRegion(focus.withInferredType(InferredType.LAND), () -> data);
            }
        }
        if (dimension.getFocusRegion() == null || dimension.getFocusRegion().isBlank()) {
            return null;
        }
        IrisRegion focus = data.getRegionLoader().load(dimension.getFocusRegion());
        return focus == null || focus.isCompatExcluded() ? null : focus;
    }

    public record Options(IrisDimension dimension, IrisData data, ProceduralStream<Double> style,
                          IrisImageMapRuntime imageMaps, ProceduralStream<IrisRegion> focused) {
    }

    public record Snapshot(IrisDimension dimension, IrisData data, long complexSeed, boolean buffet) {
    }
}
