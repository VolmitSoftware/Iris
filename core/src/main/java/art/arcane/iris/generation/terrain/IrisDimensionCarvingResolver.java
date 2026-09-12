package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.pack.value.IrisRaritySelection;

import art.arcane.volmlib.util.math.Rarity;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.CNG;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class IrisDimensionCarvingResolver {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final ThreadLocal<WeakReference<State>> THREAD_STATE =
            ThreadLocal.withInitial(() -> new WeakReference<>(null));

    private IrisDimensionCarvingResolver() {

    }

    public static IrisDimensionCarvingEntry resolveRootEntry(Engine engine, int worldY) {
        return resolveRootEntry(engine, worldY, threadState());
    }

    public static IrisDimensionCarvingEntry resolveRootEntry(Engine engine, int worldY, State state) {
        State resolvedState = state == null ? threadState() : state;
        IrisData data = resolvedState.bind(engine);
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
            if (!isRootCandidate(data, entry, worldY, resolvedState)) {
                continue;
            }

            resolved = entry;
        }

        resolvedState.rootEntriesByWorldY.put(worldY, resolved);
        return resolved;
    }

    public static IrisDimensionCarvingEntry resolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ) {
        return resolveFromRoot(engine, rootEntry, worldX, worldZ, threadState());
    }

    public static IrisDimensionCarvingEntry resolveFromRoot(Engine engine, IrisDimensionCarvingEntry rootEntry, int worldX, int worldZ, State state) {
        State resolvedState = state == null ? threadState() : state;
        IrisData data = resolvedState.bind(engine);
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = resolveBoundEntryBiome(data, rootEntry, resolvedState);
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
            IrisDimensionCarvingEntry selected = selectChild(engine, data, current, worldX, worldZ, entryIndex, resolvedState);
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

        return resolveBoundEntryBiome(state.bind(engine), entry, state);
    }

    private static IrisBiome resolveBoundEntryBiome(IrisData data, IrisDimensionCarvingEntry entry, State state) {
        if (state.biomeCache.containsKey(entry)) {
            return state.biomeCache.get(entry);
        }

        IrisBiome biome = entry.getRealBiome(data);
        state.biomeCache.put(entry, biome);
        return biome;
    }

    private static boolean isRootCandidate(IrisData data, IrisDimensionCarvingEntry entry, int worldY, State state) {
        if (entry == null || !entry.isEnabled()) {
            return false;
        }

        IrisRange worldYRange = entry.getWorldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return resolveBoundEntryBiome(data, entry, state) != null;
    }

    private static IrisDimensionCarvingEntry selectChild(
            Engine engine,
            IrisData data,
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

        IrisBiome parentBiome = resolveBoundEntryBiome(data, parent, state);
        if (parentBiome == null) {
            return parent;
        }

        IrisRaritySelection<CarvingChoice> selectionPlan = state.selectionPlans.get(parent);
        if (selectionPlan == null) {
            selectionPlan = buildSelectionPlan(data, parent, parentBiome, entryIndex, state);
            state.selectionPlans.put(parent, selectionPlan);
        }

        if (selectionPlan.size() <= 1L) {
            return parent;
        }

        long seed = resolveChildSeed(engine, state);
        CNG childGenerator = parent.getChildrenGenerator(seed, data);
        double sample = childGenerator.noiseFast2D(worldX, worldZ);
        CarvingChoice selected = selectionPlan.select(sample);
        if (selected == null || selected.entry == null) {
            return parent;
        }

        return selected.entry;
    }

    private static IrisRaritySelection<CarvingChoice> buildSelectionPlan(
            IrisData data,
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

                IrisBiome childBiome = resolveBoundEntryBiome(data, child, state);
                if (childBiome == null) {
                    continue;
                }

                options.add(new CarvingChoice(child, rarity(childBiome)));
            }
        }

        options.add(new CarvingChoice(parent, rarity(parentBiome)));
        return IrisRaritySelection.create(options);
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

    private static State threadState() {
        WeakReference<State> reference = THREAD_STATE.get();
        State state = reference.get();
        if (state != null) {
            return state;
        }
        State replacement = new State();
        THREAD_STATE.set(new WeakReference<>(replacement));
        return replacement;
    }

    public static final class State {
        private final Map<Integer, IrisDimensionCarvingEntry> rootEntriesByWorldY = new HashMap<>();
        private final Map<IrisDimensionCarvingEntry, IrisRaritySelection<CarvingChoice>> selectionPlans = new IdentityHashMap<>();
        private final Map<IrisDimensionCarvingEntry, IrisBiome> biomeCache = new IdentityHashMap<>();
        private WeakReference<Engine> engineIdentity;
        private WeakReference<IrisDimension> dimensionIdentity;
        private WeakReference<IrisData> dataIdentity;
        private Map<String, IrisDimensionCarvingEntry> entryIndex;
        private Long childSeed;

        private IrisData bind(Engine engine) {
            IrisDimension dimension = engine.getDimension();
            IrisData data = engine.getData();
            if (references(engineIdentity, engine)
                    && references(dimensionIdentity, dimension)
                    && references(dataIdentity, data)) {
                return data;
            }
            engineIdentity = new WeakReference<>(engine);
            dimensionIdentity = new WeakReference<>(dimension);
            dataIdentity = new WeakReference<>(data);
            rootEntriesByWorldY.clear();
            selectionPlans.clear();
            biomeCache.clear();
            entryIndex = null;
            childSeed = null;
            return data;
        }

        private static boolean references(WeakReference<?> identity, Object value) {
            return identity != null && identity.get() == value;
        }
    }

    private static final class CarvingChoice implements Rarity {
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
