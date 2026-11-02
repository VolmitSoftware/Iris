package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class RiverStage implements CompileStage {
    private static final String ESTIMATE_TAG = "estimate";
    private static final int MAX_CHILD_HOPS = 4;

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
            for (List<IrisBiome> lineage : lineages(region, data, exported)) {
                areas.add(new RiverCompiler.Area(region, lineage));
            }
        }
        return areas;
    }

    private static Collection<List<IrisBiome>> lineages(IrisRegion region, IrisData data, Set<String> exported) {
        List<String> roots = new ArrayList<>();
        roots.addAll(region.getLandBiomes());
        roots.addAll(region.getSeaBiomes());
        roots.addAll(region.getShoreBiomes());
        SortedMap<String, List<IrisBiome>> lineages = new TreeMap<>();
        for (String key : roots) {
            IrisBiome root = exportedBiome(key, data, exported);
            if (root != null) {
                addLineages(List.of(root), data, exported, lineages);
            }
        }
        return lineages.values();
    }

    private static void addLineages(List<IrisBiome> leafFirst, IrisData data, Set<String> exported, SortedMap<String, List<IrisBiome>> lineages) {
        StringBuilder key = new StringBuilder(leafFirst.getFirst().getLoadKey());
        for (int index = 1; index < leafFirst.size(); index++) {
            key.append('<').append(leafFirst.get(index).getLoadKey());
        }
        if (lineages.putIfAbsent(key.toString(), leafFirst) != null || leafFirst.size() > MAX_CHILD_HOPS) {
            return;
        }
        IrisBiome leaf = leafFirst.getFirst();
        for (String childKey : leaf.getChildren()) {
            IrisBiome child = exportedBiome(childKey, data, exported);
            if (child == null || child.getLoadKey().equals(leaf.getLoadKey())) {
                continue;
            }
            List<IrisBiome> lineage = new ArrayList<>(leafFirst.size() + 1);
            lineage.add(child);
            lineage.addAll(leafFirst);
            addLineages(List.copyOf(lineage), data, exported, lineages);
        }
    }

    private static IrisBiome exportedBiome(String key, IrisData data, Set<String> exported) {
        IrisBiome biome = key == null ? null : data.getBiomeLoader().load(key);
        return biome == null || biome.isCompatExcluded() || !exported.contains(biome.getLoadKey()) ? null : biome;
    }
}
