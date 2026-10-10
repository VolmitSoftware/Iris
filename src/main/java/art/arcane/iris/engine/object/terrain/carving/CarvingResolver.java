package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.interpolation.IrisInterpolation;
import art.arcane.iris.util.project.noise.CNG;
import com.volmit.config.Range;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the dimension's carving entry for a position: the last enabled root entry whose world Y band holds the height,
 * then, through the entries' children, the one a noise pattern selects at the column. The state a caller passes (or, when
 * it passes none, the thread's) keeps the picks and the entries' biomes, so a caller resolving many positions reuses them.
 */
public final class CarvingResolver {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;
    private static final ThreadLocal<WeakReference<State>> THREAD_STATE =
            ThreadLocal.withInitial(() -> new WeakReference<>(null));

    private CarvingResolver() {

    }

    public static ResolvedCarvingEntry resolveRootEntry(Engine engine, int worldY) {
        return resolveRootEntry(engine, worldY, threadState());
    }

    public static ResolvedCarvingEntry resolveRootEntry(Engine engine, int worldY, State state) {
        State resolvedState = state == null ? threadState() : state;
        resolvedState.bind(engine);
        if (resolvedState.rootEntriesByWorldY.containsKey(worldY)) {
            return resolvedState.rootEntriesByWorldY.get(worldY);
        }

        List<ResolvedCarvingEntry> entries = resolvedState.bands.entries();
        if (entries.isEmpty()) {
            resolvedState.rootEntriesByWorldY.put(worldY, null);
            return null;
        }

        ResolvedCarvingEntry resolved = null;
        for (ResolvedCarvingEntry entry : entries) {
            if (!isRootCandidate(engine, entry, worldY, resolvedState)) {
                continue;
            }

            resolved = entry;
        }

        resolvedState.rootEntriesByWorldY.put(worldY, resolved);
        return resolved;
    }

    public static ResolvedCarvingEntry resolveFromRoot(Engine engine, ResolvedCarvingEntry rootEntry, int worldX, int worldZ) {
        return resolveFromRoot(engine, rootEntry, worldX, worldZ, threadState());
    }

    public static ResolvedCarvingEntry resolveFromRoot(Engine engine, ResolvedCarvingEntry rootEntry, int worldX, int worldZ, State state) {
        State resolvedState = state == null ? threadState() : state;
        resolvedState.bind(engine);
        if (rootEntry == null) {
            return null;
        }

        IrisBiome rootBiome = resolveEntryBiome(engine, rootEntry, resolvedState);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.childRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        Map<String, ResolvedCarvingEntry> entryIndex = resolvedState.bands.index();
        ResolvedCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            ResolvedCarvingEntry selected = selectChild(engine, current, worldX, worldZ, entryIndex, resolvedState);
            if (selected == null || selected == current) {
                break;
            }

            depth--;
            int childDepthLimit = clampDepth(selected.childRecursionDepth());
            if (childDepthLimit < depth) {
                depth = childDepthLimit;
            }
            current = selected;
        }

        return current;
    }

    public static IrisBiome resolveEntryBiome(Engine engine, ResolvedCarvingEntry entry) {
        return resolveEntryBiome(engine, entry, null);
    }

    public static IrisBiome resolveEntryBiome(Engine engine, ResolvedCarvingEntry entry, State state) {
        if (entry == null) {
            return null;
        }

        if (state == null) {
            return entry.realBiome(engine.getData());
        }

        state.bind(engine);
        if (state.biomeCache.containsKey(entry)) {
            return state.biomeCache.get(entry);
        }

        IrisBiome biome = entry.realBiome(engine.getData());
        state.biomeCache.put(entry, biome);
        return biome;
    }

    private static boolean isRootCandidate(Engine engine, ResolvedCarvingEntry entry, int worldY, State state) {
        if (entry == null || !entry.enabled()) {
            return false;
        }

        Range worldYRange = entry.worldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return resolveEntryBiome(engine, entry, state) != null;
    }

    private static ResolvedCarvingEntry selectChild(
            Engine engine,
            ResolvedCarvingEntry parent,
            int worldX,
            int worldZ,
            Map<String, ResolvedCarvingEntry> entryIndex,
            State state
    ) {
        List<String> children = parent.children();
        if (children.isEmpty()) {
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
        CNG childGenerator = parent.childrenGenerator(seed, engine.getData());
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
            ResolvedCarvingEntry parent,
            IrisBiome parentBiome,
            Map<String, ResolvedCarvingEntry> entryIndex,
            State state
    ) {
        List<CarvingChoice> options = new ArrayList<>();
        for (String childId : parent.children()) {
            if (childId == null || childId.isBlank()) {
                continue;
            }

            ResolvedCarvingEntry child = entryIndex.get(childId.trim());
            if (child == null || !child.enabled()) {
                continue;
            }

            IrisBiome childBiome = resolveEntryBiome(engine, child, state);
            if (childBiome == null) {
                continue;
            }

            options.add(new CarvingChoice(child, rarity(childBiome)));
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
        private final Map<Integer, ResolvedCarvingEntry> rootEntriesByWorldY = new HashMap<>();
        private final Map<ResolvedCarvingEntry, ParentSelectionPlan> selectionPlans = new IdentityHashMap<>();
        private final Map<ResolvedCarvingEntry, IrisBiome> biomeCache = new IdentityHashMap<>();
        private WeakReference<Engine> engineIdentity;
        private WeakReference<IrisDimension> dimensionIdentity;
        private WeakReference<IrisData> dataIdentity;
        private CarvingBands bands;
        private Long childSeed;

        private void bind(Engine engine) {
            IrisDimension dimension = engine.getDimension();
            IrisData data = engine.getData();
            if (references(engineIdentity, engine)
                    && references(dimensionIdentity, dimension)
                    && references(dataIdentity, data)) {
                return;
            }
            engineIdentity = new WeakReference<>(engine);
            dimensionIdentity = new WeakReference<>(dimension);
            dataIdentity = new WeakReference<>(data);
            bands = CarvingBands.of(engine);
            rootEntriesByWorldY.clear();
            selectionPlans.clear();
            biomeCache.clear();
            childSeed = null;
        }

        private static boolean references(WeakReference<?> identity, Object value) {
            return identity != null && identity.get() == value;
        }
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
        private final ResolvedCarvingEntry entry;
        private final int rarity;

        private CarvingChoice(ResolvedCarvingEntry entry, int rarity) {
            this.entry = entry;
            this.rarity = rarity;
        }

        @Override
        public int getRarity() {
            return rarity;
        }
    }
}
