package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public final class ShoreBiomeLineages {
    private ShoreBiomeLineages() {
    }

    public static KSet<String> placed(IrisDimension dimension, Collection<IrisRegion> regions, DataProvider provider) {
        KSet<String> keys = new KSet<>();
        if (enabled(dimension)) {
            visit(dimension, regions, provider, (lineage, region) -> addPlaced(keys, lineage, region, dimension));
        }
        return keys;
    }

    static KSet<String> placedIn(IrisDimension dimension, IrisRegion region, DataProvider provider) {
        KSet<String> keys = new KSet<>();
        IrisData data = provider == null ? null : provider.getData();
        if (enabled(dimension) && data != null && data.getBiomeLoader() != null) {
            visitRegion(region, data, (lineage, area) -> addPlaced(keys, lineage, area, dimension));
        }
        return keys;
    }

    public static void visit(IrisDimension dimension, Collection<IrisRegion> regions, DataProvider provider,
                             BiConsumer<List<IrisBiome>, IrisRegion> visitor) {
        IrisData data = provider == null ? null : provider.getData();
        if (data == null || data.getBiomeLoader() == null) {
            return;
        }
        for (IrisRegion region : regions) {
            if (region != null) {
                visitRegion(region, data, visitor);
            }
        }
        List<IrisBiome> direct = new ArrayList<>();
        for (String key : dimension.getImageMapBiomeKeys(provider)) {
            addLoaded(direct, data, key);
        }
        addLoaded(direct, data, dimension.getFocus());
        for (IrisBiome biome : direct) {
            List<IrisBiome> lineage = List.of(biome);
            for (IrisRegion region : regions) {
                if (region != null) {
                    visitor.accept(lineage, region);
                }
            }
            visitor.accept(lineage, null);
        }
    }

    private static boolean enabled(IrisDimension dimension) {
        IrisHydrology hydrology = dimension.getHydrology();
        return hydrology != null && hydrology.isEnabled();
    }

    private static void visitRegion(IrisRegion region, IrisData data, BiConsumer<List<IrisBiome>, IrisRegion> visitor) {
        List<IrisBiome> roots = new ArrayList<>();
        for (String key : region.getLandBiomes()) {
            addLoaded(roots, data, key);
        }
        for (String key : region.getSeaBiomes()) {
            addLoaded(roots, data, key);
        }
        ArrayDeque<IrisBiome> chain = new ArrayDeque<>();
        for (IrisBiome root : roots) {
            descend(root, chain, region, data, visitor);
        }
    }

    private static void descend(IrisBiome biome, ArrayDeque<IrisBiome> chain, IrisRegion region, IrisData data,
                                BiConsumer<List<IrisBiome>, IrisRegion> visitor) {
        chain.addFirst(biome);
        visitor.accept(List.copyOf(chain), region);
        if (chain.size() <= IrisBiome.CHILD_HOPS) {
            List<IrisBiome> children = new ArrayList<>();
            for (String key : biome.getChildren()) {
                addLoaded(children, data, key);
            }
            for (IrisBiome child : children) {
                if (child != biome) {
                    descend(child, chain, region, data, visitor);
                }
            }
        }
        chain.removeFirst();
    }

    private static void addLoaded(List<IrisBiome> biomes, IrisData data, String key) {
        IrisBiome biome = key == null || key.isBlank() ? null : data.getBiomeLoader().load(key);
        if (biome != null && !biome.isCompatExcluded()) {
            biomes.add(biome);
        }
    }

    private static void addPlaced(KSet<String> keys, List<IrisBiome> lineage, IrisRegion region, IrisDimension dimension) {
        IrisHydrologyPolicy.ShoreBand band = IrisHydrologyPolicy.shoreBand(lineage, region, dimension);
        if (band == null) {
            return;
        }
        for (String key : band.biomes()) {
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
    }
}
