package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.stream.RarityStream;
import art.arcane.iris.generation.terrain.ContinentGenerator;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRaritySelection;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IrisSelectionReference {
    private static final int MAX_IMPLODE = 3;

    private final IrisData data;
    private final double landChance;
    private final RarityStream<IrisRegion> regions;
    private final CNG continent;
    private final Map<IrisRegion, Map<InferredType, RarityStream<IrisBiome>>> picks = new HashMap<>();
    private final Map<String, IrisRaritySelection<IrisBiome>> plans = new HashMap<>();
    private final long childStyleSeed;

    IrisSelectionReference(IrisDimension dimension, IrisData data, long seed) {
        this.data = data;
        RNG rng = new RNG(seed);
        childStyleSeed = IrisBiome.childStyleSeed(rng, 123);
        landChance = dimension.getLandChance();
        KList<IrisRegion> loaded = new KList<>();
        for (IrisRegion region : data.getRegionLoader().loadAll(dimension.getRegions())) {
            if (!region.isCompatExcluded()) {
                loaded.add(region);
            }
        }
        regions = new RarityStream<>(dimension.getRegionStyle().create(rng.nextParallelRNG(883), data).stream().zoom(dimension.getRegionZoom()), loaded);
        continent = ContinentGenerator.create(dimension, rng, data);
        for (IrisRegion region : loaded) {
            Map<InferredType, RarityStream<IrisBiome>> streams = new EnumMap<>(InferredType.class);
            streams.put(InferredType.LAND, new RarityStream<>(dimension.getLandBiomeStyle().create(rng.nextParallelRNG(InferredType.LAND.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom()).zoom(dimension.getLandZoom()).zoom(region.getLandBiomeZoom()), pool(region.getLandBiomes())));
            streams.put(InferredType.SEA, new RarityStream<>(dimension.getSeaBiomeStyle().create(rng.nextParallelRNG(InferredType.SEA.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom()).zoom(dimension.getSeaZoom()).zoom(region.getSeaBiomeZoom()), pool(region.getSeaBiomes())));
            streams.put(InferredType.SHORE, new RarityStream<>(dimension.getShoreBiomeStyle().create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom()).zoom(region.getShoreBiomeZoom()), pool(region.getShoreBiomes())));
            picks.put(region, streams);
        }
    }

    String surfaceBiome(double x, double z, double height, double fluid) {
        IrisRegion region = regions.get(x, z);
        boolean landBase = continent.noise(x, z) < landChance;
        IrisBiome base = implode(pick(region, landBase ? InferredType.LAND : InferredType.SEA, x, z), x, z, MAX_IMPLODE);
        InferredType relabel = relabel(height, fluid + region.getShoreHeight(x, z), fluid, landBase);
        if (relabel == null) {
            return base.getLoadKey();
        }
        return implode(pick(region, relabel, x, z), x, z, MAX_IMPLODE).getLoadKey();
    }

    private IrisBiome pick(IrisRegion region, InferredType type, double x, double z) {
        IrisBiome picked = picks.get(region).get(type).get(x, z);
        if (picked != null) {
            return picked;
        }
        return switch (type) {
            case LAND -> picks.get(region).get(InferredType.SEA).get(x, z);
            case SEA -> picks.get(region).get(InferredType.LAND).get(x, z);
            default -> pick(region, continent.noise(x, z) < landChance ? InferredType.LAND : InferredType.SEA, x, z);
        };
    }

    private IrisBiome implode(IrisBiome biome, double x, double z, int remaining) {
        if (remaining < 0 || biome.getChildren().isEmpty()) {
            return biome;
        }
        IrisRaritySelection<IrisBiome> selection = plans.computeIfAbsent(biome.getLoadKey(), ignored -> plan(biome));
        long maximum = selection.size() - 1L;
        IrisBiome selected = maximum <= 0L
                ? selection.get(0L)
                : selection.get(biome.getChildrenGenerator(childStyleSeed, biome.getChildShrinkFactor()).fit2D(0, (int) maximum, x, z));
        if (selected.getLoadKey().equals(biome.getLoadKey())) {
            return biome;
        }
        return implode(selected, x, z, remaining - 1);
    }

    private IrisRaritySelection<IrisBiome> plan(IrisBiome biome) {
        KList<IrisBiome> options = new KList<>();
        for (IrisBiome child : biome.getRealChildren(() -> data)) {
            if (child != null) {
                options.add(child);
            }
        }
        options.add(biome);
        return IrisRaritySelection.create(options);
    }

    private List<IrisBiome> pool(KList<String> keys) {
        List<IrisBiome> pool = new ArrayList<>();
        for (IrisBiome biome : data.getBiomeLoader().loadAll(keys)) {
            if (!biome.isCompatExcluded()) {
                pool.add(biome);
            }
        }
        return pool;
    }

    private static InferredType relabel(double height, double bandTop, double fluid, boolean landBase) {
        boolean above = height > bandTop;
        if (height >= fluid - 1) {
            if (!above) {
                return InferredType.SHORE;
            }
            if (!landBase) {
                return InferredType.LAND;
            }
        } else if (!landBase && above) {
            return InferredType.LAND;
        }
        if (height < fluid && landBase) {
            return InferredType.SEA;
        }
        if (height == fluid) {
            return InferredType.SHORE;
        }
        return null;
    }
}
