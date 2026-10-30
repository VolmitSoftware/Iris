package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public record CaveSelection(Field index, List<CaveSource> profiles, boolean uncovered, List<CaveSource> bands) {
    private static final int UNCOVERED = -1;
    private static final Field NO_INDEX = new Field.Const(0.0);
    private static final Comparator<ClimateLayout.ClimateEntry> CODE_ORDER = Comparator
            .comparingDouble(ClimateLayout.ClimateEntry::min)
            .thenComparingDouble(ClimateLayout.ClimateEntry::max)
            .thenComparing(ClimateLayout.ClimateEntry::biomeId);

    public CaveSelection {
        Objects.requireNonNull(index);
        profiles = List.copyOf(profiles);
        bands = List.copyOf(bands);
    }

    public static CaveSelection resolve(CaveInputs inputs, IrisDimension dimension, IrisData data, BiomeCodes codes) {
        if (!dimension.isCarvingEnabled() || dimension.getDisabledComponents().contains(ReservedFlag.CARVED)) {
            return new CaveSelection(NO_INDEX, List.of(), false, List.of());
        }
        Map<IrisCaveProfile, Integer> caseByProfile = new LinkedHashMap<>();
        List<CaveSource> sources = new ArrayList<>();
        IrisCaveProfile dimensionProfile = dimension.getCaveProfile();
        int fallback = enabled(dimensionProfile)
                ? caseOf(source(inputs, inputs.resource(), dimensionProfile), caseByProfile, sources)
                : UNCOVERED;
        List<Double> thresholds = new ArrayList<>();
        List<Integer> leaves = new ArrayList<>();
        leaves.add(fallback);
        List<ClimateLayout.ClimateEntry> entries = caveEntries(codes, regionCaveBiomes(dimension, data));
        for (int position = 0; position < entries.size(); position++) {
            ClimateLayout.ClimateEntry entry = entries.get(position);
            Optional<CaveSource> source = entrySource(inputs, dimension, data, codes.loadKeyByBiomeId().get(entry.biomeId()));
            int selected = source.isPresent() ? caseOf(source.get(), caseByProfile, sources) : fallback;
            append(thresholds, leaves, entry.min(), selected);
            double nextMin = position + 1 < entries.size() ? entries.get(position + 1).min() : Double.POSITIVE_INFINITY;
            if (entry.max() < nextMin) {
                append(thresholds, leaves, entry.max(), fallback);
            }
        }
        boolean uncovered = leaves.contains(UNCOVERED);
        int uncoveredCase = sources.size();
        List<Field> constants = new ArrayList<>(leaves.size());
        for (int leaf : leaves) {
            constants.add(new Field.Const(leaf == UNCOVERED ? uncoveredCase : leaf));
        }
        Field index = thresholds.isEmpty() ? constants.getFirst() : new Field.Partition(codes.caveCode(), thresholds, constants);
        return new CaveSelection(index, sources, uncovered, bands(inputs, dimension, data));
    }

    public boolean isEmpty() {
        return profiles.isEmpty() && bands.isEmpty();
    }

    public List<CaveSource> sources() {
        List<CaveSource> all = new ArrayList<>(profiles.size() + bands.size());
        all.addAll(profiles);
        all.addAll(bands);
        return all;
    }

    public Field select(Function<CaveSource, Field> compiler, Field uncoveredCase) {
        List<Field> cases = new ArrayList<>(profiles.size() + 1);
        for (CaveSource source : profiles) {
            cases.add(compiler.apply(source));
        }
        if (uncovered || cases.isEmpty()) {
            cases.add(uncoveredCase);
        }
        return cases.size() == 1 ? cases.getFirst() : new Field.Mux(index, cases);
    }

    private static void append(List<Double> thresholds, List<Integer> leaves, double start, int selected) {
        if (leaves.getLast() == selected) {
            return;
        }
        thresholds.add(start);
        leaves.add(selected);
    }

    private static List<ClimateLayout.ClimateEntry> caveEntries(BiomeCodes codes, Set<String> caveBiomes) {
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>();
        for (ClimateLayout.ClimateEntry entry : codes.layout().entries()) {
            if (entry.axis() == ClimateLayout.ClimateAxis.DEPTH && caveBiomes.contains(codes.loadKeyByBiomeId().get(entry.biomeId()))) {
                entries.add(entry);
            }
        }
        entries.sort(CODE_ORDER);
        return entries;
    }

    private static Set<String> regionCaveBiomes(IrisDimension dimension, IrisData data) {
        Set<String> caveBiomes = new HashSet<>();
        for (String regionKey : dimension.getRegions()) {
            IrisRegion region = regionKey == null ? null : data.getRegionLoader().load(regionKey, false);
            if (region != null && !region.isCompatExcluded()) {
                caveBiomes.addAll(region.getCaveBiomes());
            }
        }
        return caveBiomes;
    }

    private static Optional<CaveSource> entrySource(CaveInputs inputs, IrisDimension dimension, IrisData data, String loadKey) {
        IrisBiome biome = loadKey == null ? null : data.getBiomeLoader().load(loadKey, false);
        if (biome == null || biome.isCompatExcluded()) {
            return Optional.empty();
        }
        if (enabled(biome.getCaveProfile())) {
            return Optional.of(source(inputs, "biomes/" + biome.getLoadKey(), biome.getCaveProfile()));
        }
        for (String regionKey : dimension.getRegions()) {
            IrisRegion region = regionKey == null ? null : data.getRegionLoader().load(regionKey, false);
            if (region == null || region.isCompatExcluded() || !region.getCaveBiomes().contains(loadKey) || !enabled(region.getCaveProfile())) {
                continue;
            }
            return Optional.of(source(inputs, "regions/" + region.getLoadKey(), region.getCaveProfile()));
        }
        return Optional.empty();
    }

    private static List<CaveSource> bands(CaveInputs inputs, IrisDimension dimension, IrisData data) {
        List<CaveSource> bands = new ArrayList<>();
        List<IrisDimensionCarvingEntry> entries = dimension.getCarving();
        if (entries == null) {
            return bands;
        }
        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }
            IrisBiome biome = entry.getRealBiome(data);
            if (biome == null || !enabled(biome.getCaveProfile())) {
                continue;
            }
            IrisRange range = entry.getWorldYRange();
            int low = range == null ? inputs.minY() : Math.max(inputs.minY(), (int) Math.ceil(range.getMin()));
            int high = range == null ? inputs.maxY() : Math.min(inputs.maxY(), (int) Math.floor(range.getMax()));
            if (high >= low) {
                bands.add(new CaveSource("biomes/" + biome.getLoadKey(), biome.getCaveProfile(), low, high));
            }
        }
        return bands;
    }

    private static int caseOf(CaveSource source, Map<IrisCaveProfile, Integer> caseByProfile, List<CaveSource> sources) {
        Integer existing = caseByProfile.get(source.profile());
        if (existing != null) {
            return existing;
        }
        caseByProfile.put(source.profile(), sources.size());
        sources.add(source);
        return sources.size() - 1;
    }

    private static CaveSource source(CaveInputs inputs, String resource, IrisCaveProfile profile) {
        return new CaveSource(resource, profile, inputs.minY(), inputs.maxY());
    }

    private static boolean enabled(IrisCaveProfile profile) {
        return profile != null && profile.isEnabled();
    }
}
