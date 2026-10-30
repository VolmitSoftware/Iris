package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class BiomeDecisionCompiler {
    private static final long REGION_SALT = 883L;
    private static final long CONTINENT_SALT = 234_234_565L;
    private static final long PICK_SALT = 1_000L;
    private static final long CHILD_SALT = 123L;
    private static final long SHORE_SALT = 3_458_612L;
    private static final double SURFACE_SPAN = 1.0;
    private static final double UNDERGROUND_MIN = 1.25;
    private static final double UNDERGROUND_MAX = 2.0;
    private static final double MINIMUM_CAVE_DEPTH = 8.0;
    private static final double NO_CAVE_DEPTH = 100_000.0;
    private static final int DEEP_FLOOR_Y = -8;
    private static final int DEEP_ABOVE_MIN_Y = 40;

    private final StyleCompiler styles;

    public BiomeDecisionCompiler(StyleCompiler styles) {
        this.styles = Objects.requireNonNull(styles);
    }

    public BiomeDecision compile(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        IrisData data = context.data();
        DimensionDef dimensionDef = context.dimensionDef();
        BiomePools pools = BiomePools.load(dimension, data);
        CarvingZones carving = CarvingZones.plan(dimension, data, Math.max(DEEP_FLOOR_Y, dimensionDef.minY() + DEEP_ABOVE_MIN_Y));
        List<String> surfaceKeys = List.copyOf(pools.surface());
        SortedSet<String> undergroundSet = new TreeSet<>(pools.cave());
        undergroundSet.addAll(carving.biomes());
        List<String> undergroundKeys = List.copyOf(undergroundSet);
        Map<String, Integer> surfaceIndex = indexOf(surfaceKeys);
        Map<String, Integer> undergroundIndex = indexOf(undergroundKeys);
        Map<String, String> ids = biomeIds(context.namespace(), surfaceKeys, undergroundKeys);
        double[] surfaceCodes = codes(-SURFACE_SPAN, SURFACE_SPAN, surfaceKeys.size());
        double[] undergroundCodes = codes(UNDERGROUND_MIN, UNDERGROUND_MAX, undergroundKeys.size());
        Build build = new Build(pools, surfaceKeys, surfaceIndex,
                new SelectorCompiler(styles.selectors(), new SelectorCompiler.Sources(data, context.noises(), context.capabilities())));
        IndexedField region = build.region(dimension);
        IndexedField bridge = build.selectors.scaled(dimension.getContinentalStyle(), 1.0 / dimension.getContinentZoom(), CONTINENT_SALT)
                .partition(List.of(dimension.getLandChance()), List.of(BiomeDecision.LAND, BiomeDecision.SEA))
                .shared("biome/bridge");
        RegionPicks picks = build.regionPicks(dimension, bridge, undergroundIndex, context.diagnostics());
        List<IndexedField> chains = List.of(
                build.implode("biome/land", IndexedField.select(region, picks.land()).shared("biome/land/pick")),
                build.implode("biome/sea", IndexedField.select(region, picks.sea()).shared("biome/sea/pick")),
                build.implode("biome/shore", IndexedField.select(region, picks.shore()).shared("biome/shore/pick")));
        IndexedField baseSurface = IndexedField.select(bridge, chains.subList(0, 2));
        IndexedField caveSlot = IndexedField.select(region, picks.caves()).shared("biome/cave");
        Field caveCode = caveSlot.lookup(value -> value == BiomeDecision.SURFACE_SLOT ? 0.0 : undergroundCodes[value - 1]);
        Field requiredDepth = caveSlot.lookup(value -> value == BiomeDecision.SURFACE_SLOT
                ? NO_CAVE_DEPTH
                : Math.max(MINIMUM_CAVE_DEPTH, pools.biome(undergroundKeys.get(value - 1)).getCaveMinDepthBelowSurface()));
        Field y = blockY(dimensionDef);
        List<IndexedField> carvingSlots = carving.slots(build.selectors, data, key -> undergroundIndex.get(key) + 1);
        BiomeCodes base = new BiomeCodes(
                new Field.Shared(BiomeCodes.BASE_CODE, baseSurface.lookup(index -> surfaceCodes[index])),
                caveCode,
                new Field.Const(0.0),
                layout(ids, surfaceKeys, surfaceCodes, undergroundKeys, undergroundCodes),
                loadKeys(ids));
        BiomeDecision.Parts parts = new BiomeDecision.Parts(
                dimensionDef.fluidHeight(),
                bridge,
                shoreHeight(region, picks.shoreHeights()),
                chains,
                surfaceCodes,
                y,
                carving.zoneKeys(y),
                caveSlot,
                requiredDepth,
                carvingSlots,
                undergroundCodes);
        return new BiomeDecision(base, parts);
    }

    private static Field shoreHeight(IndexedField region, List<Field> heights) {
        if (region.isConstant()) {
            return heights.get(region.values().getFirst());
        }
        if (new HashSet<>(heights).size() == 1) {
            return heights.getFirst();
        }
        return new Field.Shared("biome/shore_height", new Field.Mux(region.field(), heights));
    }

    private static SortedMap<String, String> loadKeys(Map<String, String> ids) {
        SortedMap<String, String> loadKeys = new TreeMap<>();
        for (Map.Entry<String, String> entry : ids.entrySet()) {
            loadKeys.put(entry.getValue(), entry.getKey());
        }
        return loadKeys;
    }

    private static Map<String, Integer> indexOf(List<String> keys) {
        Map<String, Integer> index = new HashMap<>(keys.size() * 2);
        for (int position = 0; position < keys.size(); position++) {
            index.put(keys.get(position), position);
        }
        return index;
    }

    private static Map<String, String> biomeIds(String namespace, List<String> surfaceKeys, List<String> undergroundKeys) {
        SortedSet<String> all = new TreeSet<>(surfaceKeys);
        all.addAll(undergroundKeys);
        Set<String> taken = new HashSet<>();
        Map<String, String> ids = new TreeMap<>();
        for (String key : all) {
            ids.put(key, BiomeIdSanitizer.sanitize(namespace, key, taken));
        }
        return ids;
    }

    private static double[] codes(double min, double max, int count) {
        double[] codes = new double[count];
        double half = (max - min) / (2.0 * Math.max(1, count));
        for (int index = 0; index < count; index++) {
            codes[index] = min + (2 * index + 1) * half;
        }
        return codes;
    }

    private static ClimateLayout layout(Map<String, String> ids, List<String> surfaceKeys, double[] surfaceCodes, List<String> undergroundKeys, double[] undergroundCodes) {
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>(surfaceKeys.size() + undergroundKeys.size());
        double surfaceHalf = SURFACE_SPAN / Math.max(1, surfaceKeys.size());
        for (int index = 0; index < surfaceKeys.size(); index++) {
            entries.add(new ClimateLayout.ClimateEntry(ids.get(surfaceKeys.get(index)), ClimateLayout.ClimateAxis.TEMPERATURE,
                    surfaceCodes[index] - surfaceHalf, surfaceCodes[index] + surfaceHalf));
        }
        double undergroundHalf = (UNDERGROUND_MAX - UNDERGROUND_MIN) / (2.0 * Math.max(1, undergroundKeys.size()));
        for (int index = 0; index < undergroundKeys.size(); index++) {
            entries.add(new ClimateLayout.ClimateEntry(ids.get(undergroundKeys.get(index)), ClimateLayout.ClimateAxis.DEPTH,
                    undergroundCodes[index] - undergroundHalf, Math.min(UNDERGROUND_MAX, undergroundCodes[index] + undergroundHalf)));
        }
        return new ClimateLayout(entries);
    }

    private static Field blockY(DimensionDef dimension) {
        double bottom = dimension.minY();
        double top = dimension.minY() + dimension.height();
        return new Field.Coord(Axis.Y, Field.Tiling.CLAMP, bottom, top, bottom, top);
    }

    private static final class Build {
        private final BiomePools pools;
        private final List<String> surfaceKeys;
        private final Map<String, Integer> surfaceIndex;
        private final SelectorCompiler selectors;
        private final Map<String, IndexedField> hops = new HashMap<>();

        private Build(BiomePools pools, List<String> surfaceKeys, Map<String, Integer> surfaceIndex, SelectorCompiler selectors) {
            this.pools = pools;
            this.surfaceKeys = surfaceKeys;
            this.surfaceIndex = surfaceIndex;
            this.selectors = selectors;
        }

        private IndexedField region(IrisDimension dimension) {
            List<IrisRegion> regions = new ArrayList<>(pools.regions().size());
            List<Integer> leaves = new ArrayList<>(pools.regions().size());
            for (BiomePools.RegionPools regionPools : pools.regions()) {
                leaves.add(regions.size());
                regions.add(regionPools.region());
            }
            if (regions.size() == 1) {
                return IndexedField.constant(0);
            }
            return selectors.zoomed(dimension.getRegionStyle(), dimension.getRegionZoom(), REGION_SALT)
                    .partition(BiomePools.rarityCuts(regions), leaves)
                    .shared("biome/region");
        }

        private RegionPicks regionPicks(IrisDimension dimension, IndexedField bridge, Map<String, Integer> undergroundIndex, List<ExportDiagnostic> diagnostics) {
            int count = pools.regions().size();
            RegionPicks picks = new RegionPicks(new ArrayList<>(count), new ArrayList<>(count), new ArrayList<>(count), new ArrayList<>(count), new ArrayList<>(count));
            for (int position = 0; position < count; position++) {
                BiomePools.RegionPools regionPools = pools.regions().get(position);
                IrisRegion region = regionPools.region();
                IndexedField landPick = pick(dimension, regionPools, InferredType.LAND, surfaceIndex::get, 0);
                IndexedField seaPick = pick(dimension, regionPools, InferredType.SEA, surfaceIndex::get, 0);
                IndexedField land = landPick == null ? seaPick : landPick;
                IndexedField sea = seaPick == null ? landPick : seaPick;
                IndexedField shore = pick(dimension, regionPools, InferredType.SHORE, surfaceIndex::get, 0);
                IndexedField cave = pick(dimension, regionPools, InferredType.CAVE, undergroundIndex::get, 1);
                picks.land().add(land);
                picks.sea().add(sea);
                picks.shore().add(shore == null ? IndexedField.select(bridge, List.of(land, sea)) : shore);
                picks.caves().add(cave == null ? IndexedField.constant(BiomeDecision.SURFACE_SLOT) : cave);
                picks.shoreHeights().add(shoreHeight(region, position));
                if (region.getShoreMinimumWidth() > 0.0) {
                    diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.SHORE_WIDTH, "regions/" + region.getLoadKey(),
                            "Shore widening by slope is not exported; the shore band follows the shore height only"));
                }
            }
            return picks;
        }

        private IndexedField pick(IrisDimension dimension, BiomePools.RegionPools regionPools, InferredType type, Function<String, Integer> index, int offset) {
            List<IrisBiome> pool = regionPools.of(type);
            if (pool.isEmpty()) {
                return null;
            }
            List<Integer> leaves = new ArrayList<>(pool.size());
            for (IrisBiome biome : pool) {
                leaves.add(index.apply(biome.getLoadKey()) + offset);
            }
            if (pool.size() == 1) {
                return IndexedField.constant(leaves.getFirst());
            }
            IrisRegion region = regionPools.region();
            double zoom = switch (type) {
                case LAND -> dimension.getBiomeZoom() * dimension.getLandZoom() * region.getLandBiomeZoom();
                case SEA -> dimension.getBiomeZoom() * dimension.getSeaZoom() * region.getSeaBiomeZoom();
                case SHORE -> dimension.getBiomeZoom() * region.getShoreBiomeZoom();
                case CAVE -> dimension.getBiomeZoom() * region.getCaveBiomeZoom();
            };
            IrisGeneratorStyle style = switch (type) {
                case LAND -> dimension.getLandBiomeStyle();
                case SEA -> dimension.getSeaBiomeStyle();
                case SHORE -> dimension.getShoreBiomeStyle();
                case CAVE -> dimension.getCaveBiomeStyle();
            };
            return selectors.zoomed(style, zoom, PICK_SALT + type.ordinal()).partition(BiomePools.rarityCuts(pool), leaves);
        }

        private Field shoreHeight(IrisRegion region, int position) {
            if (region.getShoreHeightMin() == region.getShoreHeightMax()) {
                return new Field.Const(region.getShoreHeightMin());
            }
            return selectors.sampled(NoiseStyle.IRIS.style(), region.getShoreHeightZoom(), SHORE_SALT + position, region::getShoreHeight).curve();
        }

        private IndexedField implode(String name, IndexedField start) {
            IndexedField current = start;
            for (int hop = 0; hop < BiomePools.MAX_CHILD_HOPS; hop++) {
                List<Integer> movers = new ArrayList<>();
                for (int value : current.values()) {
                    BiomePools.ChildPlan plan = pools.plan(surfaceKeys.get(value));
                    if (plan != null && plan.moves()) {
                        movers.add(value);
                    }
                }
                if (movers.isEmpty()) {
                    return current;
                }
                Map<Integer, IndexedField> moved = new HashMap<>(movers.size() * 2);
                for (int mover : movers) {
                    moved.put(mover, hops.computeIfAbsent(surfaceKeys.get(mover), this::hop));
                }
                current = IndexedField.advance(current, moved).shared(name + "/hop" + (hop + 1));
            }
            return current;
        }

        private IndexedField hop(String parent) {
            IrisBiome biome = pools.biome(parent);
            BiomePools.ChildPlan plan = pools.plan(parent);
            List<Integer> leaves = new ArrayList<>(plan.choiceByIndex().size());
            for (String choice : plan.choiceByIndex()) {
                leaves.add(surfaceIndex.get(choice));
            }
            return selectors.scaled(biome.getChildStyle(), biome.getChildShrinkFactor(), CHILD_SALT)
                    .partition(BiomePools.selectionCuts(plan.choiceByIndex().size()), leaves);
        }
    }

    private record RegionPicks(List<IndexedField> land, List<IndexedField> sea, List<IndexedField> shore, List<IndexedField> caves, List<Field> shoreHeights) {
    }
}
