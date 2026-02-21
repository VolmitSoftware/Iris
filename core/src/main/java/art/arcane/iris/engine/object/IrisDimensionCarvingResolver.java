package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.project.noise.CNG;

import java.util.List;
import java.util.Map;

public final class IrisDimensionCarvingResolver {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;

    private IrisDimensionCarvingResolver() {

    }

    public static IrisDimensionCarvingEntry resolveRootEntry(Engine engine, int worldY) {
        IrisDimension dimension = engine.getDimension();
        List<IrisDimensionCarvingEntry> entries = dimension.getCarving();
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        IrisDimensionCarvingEntry resolved = null;
        for (IrisDimensionCarvingEntry entry : entries) {
            if (!isRootCandidate(engine, entry, worldY)) {
                continue;
            }

            resolved = entry;
        }

        return resolved;
    }

    public static IrisDimensionCarvingEntry resolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ) {
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = resolveEntryBiome(engine, rootEntry);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.getChildRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        Map<String, IrisDimensionCarvingEntry> entryIndex = engine.getDimension().getCarvingEntryIndex();
        IrisDimensionCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            IrisDimensionCarvingEntry selected = selectChild(engine, current, worldX, worldZ, entryIndex);
            if (selected == null || selected == current) {
                break;
            }

            depth--;
            int childDepthLimit = clampDepth(selected.getChildRecursionDepth());
            if (childDepthLimit < depth) {
                depth = childDepthLimit;
            }
            current = selected;
        }

        return current;
    }

    public static IrisBiome resolveEntryBiome(Engine engine, IrisDimensionCarvingEntry entry) {
        if (entry == null) {
            return null;
        }

        return entry.getRealBiome(engine.getData());
    }

    private static boolean isRootCandidate(Engine engine, IrisDimensionCarvingEntry entry, int worldY) {
        if (entry == null || !entry.isEnabled()) {
            return false;
        }

        IrisRange worldYRange = entry.getWorldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return resolveEntryBiome(engine, entry) != null;
    }

    private static IrisDimensionCarvingEntry selectChild(
            Engine engine,
            IrisDimensionCarvingEntry parent,
            int worldX,
            int worldZ,
            Map<String, IrisDimensionCarvingEntry> entryIndex
    ) {
        KList<String> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
            return parent;
        }

        IrisBiome parentBiome = resolveEntryBiome(engine, parent);
        if (parentBiome == null) {
            return parent;
        }

        KList<CarvingChoice> options = new KList<>();
        for (String childId : children) {
            if (childId == null || childId.isBlank()) {
                continue;
            }

            IrisDimensionCarvingEntry child = entryIndex.get(childId.trim());
            if (child == null || !child.isEnabled()) {
                continue;
            }

            IrisBiome childBiome = resolveEntryBiome(engine, child);
            if (childBiome == null) {
                continue;
            }

            options.add(new CarvingChoice(child, rarity(childBiome)));
        }

        options.add(new CarvingChoice(parent, rarity(parentBiome)));
        if (options.size() <= 1) {
            return parent;
        }

        long seed = engine.getSeedManager().getCarve() ^ CHILD_SEED_SALT;
        CNG childGenerator = parent.getChildrenGenerator(seed, engine.getData());
        CarvingChoice selected = childGenerator.fitRarity(options, worldX, worldZ);
        if (selected == null || selected.entry == null) {
            return parent;
        }

        return selected.entry;
    }

    private static int rarity(IrisBiome biome) {
        if (biome == null) {
            return 1;
        }

        int rarity = biome.getRarity();
        return Math.max(rarity, 1);
    }

    private static int clampDepth(int depth) {
        if (depth <= 0) {
            return 0;
        }

        return Math.min(depth, MAX_CHILD_DEPTH);
    }

    private static final class CarvingChoice implements IRare {
        private final IrisDimensionCarvingEntry entry;
        private final int rarity;

        private CarvingChoice(IrisDimensionCarvingEntry entry, int rarity) {
            this.entry = entry;
            this.rarity = rarity;
        }

        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
