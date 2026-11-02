package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ExportedBiomes {
    private final SortedMap<String, IrisBiome> byId;
    private final Map<String, String> loadKeys;
    private final Map<String, Integer> codeOrder;
    private final Map<String, IrisRegion> regionByLoadKey;

    private ExportedBiomes(SortedMap<String, IrisBiome> byId, Map<String, String> loadKeys, Map<String, Integer> codeOrder,
                           Map<String, IrisRegion> regionByLoadKey) {
        this.byId = Collections.unmodifiableSortedMap(byId);
        this.loadKeys = Map.copyOf(loadKeys);
        this.codeOrder = Map.copyOf(codeOrder);
        this.regionByLoadKey = Map.copyOf(regionByLoadKey);
    }

    public static ExportedBiomes load(PackCompileContext context) {
        SortedMap<String, IrisBiome> byId = new TreeMap<>();
        Map<String, String> loadKeys = new HashMap<>();
        for (Map.Entry<String, String> entry : context.biomeCodes().loadKeyByBiomeId().entrySet()) {
            IrisBiome biome = Objects.requireNonNull(context.data().getBiomeLoader().load(entry.getValue(), false),
                    "Biome '" + entry.getValue() + "' could not be loaded");
            byId.put(entry.getKey(), biome);
            loadKeys.put(entry.getKey(), entry.getValue());
        }
        return new ExportedBiomes(byId, loadKeys, codeOrder(context.biomeCodes().layout(), byId.keySet()), regions(context));
    }

    public SortedMap<String, IrisBiome> byId() {
        return byId;
    }

    public SortedSet<String> ids() {
        return new TreeSet<>(byId.keySet());
    }

    public SortedSet<String> idsFor(Collection<String> loadKeys) {
        SortedSet<String> ids = new TreeSet<>();
        for (Map.Entry<String, String> entry : this.loadKeys.entrySet()) {
            if (loadKeys.contains(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    public String loadKey(String biomeId) {
        return loadKeys.get(biomeId);
    }

    public String resource(String biomeId) {
        return "biomes/" + loadKeys.get(biomeId);
    }

    public IrisRegion region(String biomeId) {
        return regionByLoadKey.get(loadKeys.get(biomeId));
    }

    public List<String> inCodeOrder(Iterable<String> biomeIds) {
        List<String> ordered = new ArrayList<>();
        for (String biomeId : biomeIds) {
            ordered.add(biomeId);
        }
        ordered.sort(Comparator.comparingInt((String id) -> codeOrder.getOrDefault(id, Integer.MAX_VALUE)).thenComparing(Comparator.naturalOrder()));
        return ordered;
    }

    private static Map<String, Integer> codeOrder(ClimateLayout layout, Iterable<String> biomeIds) {
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>(layout.entries());
        entries.sort(Comparator.comparingInt((ClimateLayout.ClimateEntry entry) -> entry.axis().ordinal())
                .thenComparingDouble(ClimateLayout.ClimateEntry::min)
                .thenComparing(ClimateLayout.ClimateEntry::biomeId));
        Map<String, Integer> order = new LinkedHashMap<>();
        for (ClimateLayout.ClimateEntry entry : entries) {
            order.putIfAbsent(entry.biomeId(), order.size());
        }
        for (String biomeId : biomeIds) {
            order.putIfAbsent(biomeId, order.size());
        }
        return order;
    }

    private static Map<String, IrisRegion> regions(PackCompileContext context) {
        Map<String, IrisRegion> regions = new HashMap<>();
        for (IrisRegion region : context.dimension().getAllRegions(() -> context.data())) {
            if (region == null) {
                continue;
            }
            for (IrisBiome biome : region.getAllBiomes(context.dimension(), () -> context.data())) {
                if (biome != null && biome.getLoadKey() != null) {
                    regions.putIfAbsent(biome.getLoadKey(), region);
                }
            }
        }
        return regions;
    }
}
