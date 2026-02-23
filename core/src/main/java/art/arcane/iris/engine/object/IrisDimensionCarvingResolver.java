package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.project.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.project.noise.CNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class IrisDimensionCarvingResolver {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;

    private IrisDimensionCarvingResolver() {

    }

    public static IrisDimensionCarvingEntry resolveRootEntry(Engine engine, int worldY) {
        return resolveRootEntry(engine, worldY, new State());
    }

    public static IrisDimensionCarvingEntry resolveRootEntry(Engine engine, int worldY, State state) {
        State resolvedState = state == null ? new State() : state;
        if (resolvedState.rootEntriesByWorldY.containsKey(worldY)) {
            return resolvedState.rootEntriesByWorldY.get(worldY);
        }

        IrisDimension dimension = engine.getDimension();
        List<IrisDimensionCarvingEntry> entries = dimension.getCarving();
        if (entries == null || entries.isEmpty()) {
            resolvedState.rootEntriesByWorldY.put(worldY, null);
            return null;
        }

        IrisDimensionCarvingEntry resolved = null;
        for (IrisDimensionCarvingEntry entry : entries) {
            if (!isRootCandidate(engine, entry, worldY, resolvedState)) {
                continue;
            }

            resolved = entry;
        }

        resolvedState.rootEntriesByWorldY.put(worldY, resolved);
        return resolved;
    }

    public static IrisDimensionCarvingEntry resolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ) {
        return resolveFromRoot(engine, rootEntry, worldX, worldZ, new State());
    }

    public static IrisDimensionCarvingEntry resolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ, State state) {
        State resolvedState = state == null ? new State() : state;
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = resolveEntryBiome(engine, rootEntry, resolvedState);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.getChildRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        Map<String, IrisDimensionCarvingEntry> entryIndex = resolveEntryIndex(engine, resolvedState);
        IrisDimensionCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            IrisDimensionCarvingEntry selected = selectChild(engine, current, worldX, worldZ, entryIndex, resolvedState);
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
        return resolveEntryBiome(engine, entry, null);
    }

    public static IrisBiome resolveEntryBiome(Engine engine, IrisDimensionCarvingEntry entry, State state) {
        if (entry == null) {
            return null;
        }

        if (state == null) {
            return entry.getRealBiome(engine.getData());
        }

        if (state.biomeCache.containsKey(entry)) {
            return state.biomeCache.get(entry);
        }

        IrisBiome biome = entry.getRealBiome(engine.getData());
        state.biomeCache.put(entry, biome);
        return biome;
    }

    private static boolean isRootCandidate(Engine engine, IrisDimensionCarvingEntry entry, int worldY, State state) {
        if (entry == null || !entry.isEnabled()) {
            return false;
        }

        IrisRange worldYRange = entry.getWorldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return resolveEntryBiome(engine, entry, state) != null;
    }

    private static IrisDimensionCarvingEntry selectChild(
            Engine engine,
            IrisDimensionCarvingEntry parent,
            int worldX,
            int worldZ,
            Map<String, IrisDimensionCarvingEntry> entryIndex,
            State state
    ) {
        KList<String> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
            return parent;
        }

        IrisBiome parentBiome = resolveEntryBiome(engine, parent, state);
        if (parentBiome == null) {
            return parent;
        }

        ParentSelectionPlan selectionPlan = state.selectionPlans.get(parent);
        if (selectionPlan == null) {
            selectionPlan = buildSelectionPlan(engine, parent, parentBiome, entryIndex, state);
            state.selectionPlans.put(parent, selectionPlan);
        }

        if (selectionPlan.parentOnly) {
            return parent;
        }

        long seed = resolveChildSeed(engine, state);
        CNG childGenerator = parent.getChildrenGenerator(seed, engine.getData());
        double sample = childGenerator.noiseFast2D(worldX, worldZ);
        int selectedIndex = (int) Math.round(IrisInterpolation.lerp(0, selectionPlan.maxIndex, sample));
        CarvingChoice selected = selectionPlan.get(selectedIndex);
        if (selected == null || selected.entry == null) {
            return parent;
        }

        return selected.entry;
    }

    private static ParentSelectionPlan buildSelectionPlan(
            Engine engine,
            IrisDimensionCarvingEntry parent,
            IrisBiome parentBiome,
            Map<String, IrisDimensionCarvingEntry> entryIndex,
            State state
    ) {
        List<CarvingChoice> options = new ArrayList<>();
        KList<String> children = parent.getChildren();
        if (children != null) {
            for (String childId : children) {
                if (childId == null || childId.isBlank()) {
                    continue;
                }

                IrisDimensionCarvingEntry child = entryIndex.get(childId.trim());
                if (child == null || !child.isEnabled()) {
                    continue;
                }

                IrisBiome childBiome = resolveEntryBiome(engine, child, state);
                if (childBiome == null) {
                    continue;
                }

                options.add(new CarvingChoice(child, rarity(childBiome)));
            }
        }

        options.add(new CarvingChoice(parent, rarity(parentBiome)));
        if (options.size() <= 1) {
            return ParentSelectionPlan.parentOnly();
        }

        CarvingChoice[] mappedChoices = buildRarityMappedChoices(options);
        if (mappedChoices.length == 0) {
            return ParentSelectionPlan.parentOnly();
        }

        return new ParentSelectionPlan(mappedChoices);
    }

    private static CarvingChoice[] buildRarityMappedChoices(List<CarvingChoice> choices) {
        int max = 1;
        for (CarvingChoice choice : choices) {
            if (choice.rarity > max) {
                max = choice.rarity;
            }
        }

        max++;
        List<CarvingChoice> mapped = new ArrayList<>();
        boolean flip = false;
        for (CarvingChoice choice : choices) {
            int count = max - choice.rarity;
            for (int index = 0; index < count; index++) {
                flip = !flip;
                if (flip) {
                    mapped.add(choice);
                } else {
                    mapped.add(0, choice);
                }
            }
        }

        return mapped.toArray(new CarvingChoice[0]);
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

    private static Map<String, IrisDimensionCarvingEntry> resolveEntryIndex(Engine engine, State state) {
        if (state.entryIndex == null) {
            state.entryIndex = engine.getDimension().getCarvingEntryIndex();
        }

        return state.entryIndex;
    }

    private static long resolveChildSeed(Engine engine, State state) {
        if (state.childSeed == null) {
            state.childSeed = engine.getSeedManager().getCarve() ^ CHILD_SEED_SALT;
        }

        return state.childSeed;
    }

    public static final class State {
        private final Map<Integer, IrisDimensionCarvingEntry> rootEntriesByWorldY = new HashMap<>();
        private final Map<IrisDimensionCarvingEntry, ParentSelectionPlan> selectionPlans = new IdentityHashMap<>();
        private final Map<IrisDimensionCarvingEntry, IrisBiome> biomeCache = new IdentityHashMap<>();
        private Map<String, IrisDimensionCarvingEntry> entryIndex;
        private Long childSeed;
    }

    private static final class ParentSelectionPlan {
        private final CarvingChoice[] mappedChoices;
        private final int maxIndex;
        private final boolean parentOnly;

        private ParentSelectionPlan(CarvingChoice[] mappedChoices) {
            this.mappedChoices = mappedChoices;
            this.maxIndex = mappedChoices.length - 1;
            this.parentOnly = false;
        }

        private ParentSelectionPlan() {
            this.mappedChoices = null;
            this.maxIndex = -1;
            this.parentOnly = true;
        }

        private static ParentSelectionPlan parentOnly() {
            return new ParentSelectionPlan();
        }

        private CarvingChoice get(int index) {
            if (mappedChoices == null || mappedChoices.length == 0) {
                return null;
            }

            if (index < 0) {
                return mappedChoices[0];
            }

            if (index >= mappedChoices.length) {
                return mappedChoices[mappedChoices.length - 1];
            }

            return mappedChoices[index];
        }
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
