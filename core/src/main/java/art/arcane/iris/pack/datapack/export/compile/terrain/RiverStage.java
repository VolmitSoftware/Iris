package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class RiverStage implements CompileStage {
    private static final String ESTIMATE_TAG = "estimate";

    @Override
    public String name() {
        return "rivers";
    }

    @Override
    public void apply(PackCompileContext context) {
        Field natural = context.naturalHeight();
        Field carved = new RiverCompiler(context.diagnostics()).compile(natural, context.dimension(), () -> areas(context), context.noises(), context.capabilities());
        context.setCarvedHeight(carved);
        context.setSurfaceEstimate(FieldMacros.substitute(carved, natural, context.climateHeight(), ESTIMATE_TAG));
    }

    private static List<RiverCompiler.Area> areas(PackCompileContext context) {
        IrisData data = context.data();
        Set<String> exported = new HashSet<>(context.baseBiomeCodes().loadKeyByBiomeId().values());
        List<IrisRegion> regions = new ArrayList<>();
        for (IrisRegion region : data.getRegionLoader().loadAll(context.dimension().getRegions())) {
            if (!region.isCompatExcluded()) {
                regions.add(region);
            }
        }
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey));
        List<RiverCompiler.Area> areas = new ArrayList<>();
        for (IrisRegion region : regions) {
            for (IrisBiome biome : surfaceBiomes(region, data, exported)) {
                areas.add(new RiverCompiler.Area(region, biome));
            }
        }
        return areas;
    }

    private static Collection<IrisBiome> surfaceBiomes(IrisRegion region, IrisData data, Set<String> exported) {
        Deque<String> pending = new ArrayDeque<>();
        pending.addAll(region.getLandBiomes());
        pending.addAll(region.getSeaBiomes());
        pending.addAll(region.getShoreBiomes());
        Set<String> visited = new HashSet<>();
        SortedMap<String, IrisBiome> biomes = new TreeMap<>();
        while (!pending.isEmpty()) {
            String key = pending.removeFirst();
            if (key == null || !visited.add(key)) {
                continue;
            }
            IrisBiome biome = data.getBiomeLoader().load(key);
            if (biome == null || biome.isCompatExcluded() || !exported.contains(biome.getLoadKey())) {
                continue;
            }
            biomes.put(biome.getLoadKey(), biome);
            pending.addAll(biome.getChildren());
        }
        return biomes.values();
    }
}
