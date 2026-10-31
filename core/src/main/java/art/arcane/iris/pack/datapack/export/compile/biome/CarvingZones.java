package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.pack.value.IrisRaritySelection;
import art.arcane.volmlib.util.math.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

final class CarvingZones {
    static final int SHALLOW_ZONE = 0;
    static final int CAVE_ZONE = 1;
    static final int FIRST_CARVING_ZONE = 2;
    private static final int MAX_CHILD_DEPTH = 32;
    private static final double MINIMUM_CHILD_SHRINK_FACTOR = 0.0001;
    private static final long CARVING_SALT = 0x0CA4_7E00L;

    private final List<Integer> cuts;
    private final List<Integer> zoneByInterval;
    private final List<Machine> machines;
    private final SortedSet<String> biomes;

    private CarvingZones(List<Integer> cuts, List<Integer> zoneByInterval, List<Machine> machines, SortedSet<String> biomes) {
        this.cuts = cuts;
        this.zoneByInterval = zoneByInterval;
        this.machines = machines;
        this.biomes = biomes;
    }

    static CarvingZones plan(IrisDimension dimension, IrisData data, int deepTop) {
        List<IrisDimensionCarvingEntry> entries = new ArrayList<>();
        if (dimension.getCarving() != null) {
            for (IrisDimensionCarvingEntry entry : dimension.getCarving()) {
                if (entry != null && entry.isEnabled() && entry.getRealBiome(data) != null) {
                    entries.add(entry);
                }
            }
        }
        SortedSet<Integer> cutSet = new TreeSet<>();
        cutSet.add(deepTop + 1);
        for (IrisDimensionCarvingEntry entry : entries) {
            IrisRange range = entry.getWorldYRange();
            if (range != null && range.getMin() <= range.getMax()) {
                cutSet.add((int) Math.ceil(range.getMin()));
                cutSet.add((int) Math.floor(range.getMax()) + 1);
            }
        }
        List<Integer> cuts = List.copyOf(cutSet);
        Map<String, IrisDimensionCarvingEntry> byId = dimension.getCarvingEntryIndex();
        Map<ZoneKey, Integer> zones = new LinkedHashMap<>();
        List<Machine> machines = new ArrayList<>();
        SortedSet<String> biomes = new TreeSet<>();
        List<Integer> zoneByInterval = new ArrayList<>(cuts.size() + 1);
        for (int interval = 0; interval <= cuts.size(); interval++) {
            int y = interval == 0 ? cuts.getFirst() - 1 : cuts.get(interval - 1);
            if (y > deepTop) {
                zoneByInterval.add(SHALLOW_ZONE);
                continue;
            }
            IrisDimensionCarvingEntry root = null;
            List<Integer> inRange = new ArrayList<>();
            for (int position = 0; position < entries.size(); position++) {
                IrisDimensionCarvingEntry entry = entries.get(position);
                if (contains(entry, y)) {
                    root = entry;
                    inRange.add(position);
                }
            }
            if (root == null) {
                zoneByInterval.add(CAVE_ZONE);
                continue;
            }
            ZoneKey key = new ZoneKey(entries.indexOf(root), List.copyOf(inRange));
            Integer zone = zones.get(key);
            if (zone == null) {
                Machine machine = Machine.build(root, y, byId, data);
                zone = FIRST_CARVING_ZONE + machines.size();
                zones.put(key, zone);
                machines.add(machine);
                for (State state : machine.states()) {
                    biomes.add(state.entry().getRealBiome(data).getLoadKey());
                }
            }
            zoneByInterval.add(zone);
        }
        return new CarvingZones(cuts, List.copyOf(zoneByInterval), List.copyOf(machines), biomes);
    }

    SortedSet<String> biomes() {
        return biomes;
    }

    IndexedField zoneKeys(Field y) {
        List<Double> thresholds = new ArrayList<>(cuts.size());
        for (int cut : cuts) {
            thresholds.add((double) cut);
        }
        List<Integer> leaves = new ArrayList<>(zoneByInterval.size());
        for (int zone : zoneByInterval) {
            leaves.add(zone * 4);
        }
        return IndexedField.partition(y, thresholds, leaves);
    }

    List<IndexedField> slots(SelectorCompiler selectors, IrisData data, ToIntFunction<String> undergroundSlot) {
        List<IndexedField> slots = new ArrayList<>(machines.size());
        for (int zone = 0; zone < machines.size(); zone++) {
            slots.add(machines.get(zone).slot("biome/carving/" + zone, selectors, data, undergroundSlot));
        }
        return slots;
    }

    private static boolean contains(IrisDimensionCarvingEntry entry, int y) {
        IrisRange range = entry.getWorldYRange();
        return range == null || range.contains(y);
    }

    private static int clampDepth(int depth) {
        return depth <= 0 ? 0 : Math.min(depth, MAX_CHILD_DEPTH);
    }

    private record ZoneKey(int root, List<Integer> inRange) {
    }

    private record State(IrisDimensionCarvingEntry entry, int depth) {
    }

    private record Move(IrisDimensionCarvingEntry entry, List<Integer> nextByIndex) {
    }

    private record CarvingOption(IrisDimensionCarvingEntry entry, int rarity) implements Rarity {
        @Override
        public int getRarity() {
            return rarity;
        }
    }

    private record Machine(List<State> states, Map<Integer, Move> moves, int hops) {
        static Machine build(IrisDimensionCarvingEntry root, int y, Map<String, IrisDimensionCarvingEntry> byId, IrisData data) {
            List<State> states = new ArrayList<>();
            Map<State, Integer> ids = new HashMap<>();
            Map<Integer, Move> moves = new HashMap<>();
            int hops = clampDepth(root.getChildRecursionDepth());
            State start = new State(root, terminal(root, byId, data) ? 0 : hops);
            states.add(start);
            ids.put(start, 0);
            for (int cursor = 0; cursor < states.size(); cursor++) {
                State state = states.get(cursor);
                if (state.depth() <= 0) {
                    continue;
                }
                IrisRaritySelection<CarvingOption> selection = selection(state.entry(), byId, data);
                if (selection.size() <= 1L) {
                    continue;
                }
                List<Integer> nextByIndex = new ArrayList<>((int) selection.size());
                for (long index = 0; index < selection.size(); index++) {
                    IrisDimensionCarvingEntry chosen = selection.get(index).entry();
                    State next = chosen == state.entry() || !contains(chosen, y)
                            ? new State(state.entry(), 0)
                            : new State(chosen, Math.min(state.depth() - 1, clampDepth(chosen.getChildRecursionDepth())));
                    Integer id = ids.get(next);
                    if (id == null) {
                        id = states.size();
                        ids.put(next, id);
                        states.add(next);
                    }
                    nextByIndex.add(id);
                }
                moves.put(cursor, new Move(state.entry(), List.copyOf(nextByIndex)));
            }
            return new Machine(List.copyOf(states), Map.copyOf(moves), hops);
        }

        IndexedField slot(String name, SelectorCompiler selectors, IrisData data, ToIntFunction<String> undergroundSlot) {
            IndexedField current = IndexedField.constant(0);
            for (int hop = 0; hop < hops; hop++) {
                List<Integer> movers = new ArrayList<>();
                for (int state : current.values()) {
                    if (moves.containsKey(state)) {
                        movers.add(state);
                    }
                }
                if (movers.isEmpty()) {
                    break;
                }
                Map<Integer, IndexedField> moved = new HashMap<>(movers.size() * 2);
                for (int state : movers) {
                    Move move = moves.get(state);
                    IrisDimensionCarvingEntry entry = move.entry();
                    SelectorCompiler.Selector selector = selectors.scaled(
                            entry.getChildStyle(),
                            Math.max(MINIMUM_CHILD_SHRINK_FACTOR, entry.getChildShrinkFactor()),
                            CARVING_SALT + Objects.toString(entry.getId(), "").trim().hashCode());
                    moved.put(state, selector.partition(BiomePools.selectionCuts(move.nextByIndex().size()), move.nextByIndex()));
                }
                current = IndexedField.advance(current, moved).shared(name + "/hop" + (hop + 1));
            }
            return current.remap(state -> undergroundSlot.applyAsInt(states.get(state).entry().getRealBiome(data).getLoadKey()));
        }

        private static boolean terminal(IrisDimensionCarvingEntry entry, Map<String, IrisDimensionCarvingEntry> byId, IrisData data) {
            return entry.getChildren() == null || entry.getChildren().isEmpty() || selection(entry, byId, data).size() <= 1L;
        }

        private static IrisRaritySelection<CarvingOption> selection(IrisDimensionCarvingEntry parent, Map<String, IrisDimensionCarvingEntry> byId, IrisData data) {
            List<CarvingOption> options = new ArrayList<>();
            if (parent.getChildren() != null) {
                for (String childId : parent.getChildren()) {
                    if (childId == null || childId.isBlank()) {
                        continue;
                    }
                    IrisDimensionCarvingEntry child = byId.get(childId.trim());
                    if (child == null || !child.isEnabled()) {
                        continue;
                    }
                    IrisBiome childBiome = child.getRealBiome(data);
                    if (childBiome != null) {
                        options.add(new CarvingOption(child, Math.max(childBiome.getRarity(), 1)));
                    }
                }
            }
            options.add(new CarvingOption(parent, Math.max(parent.getRealBiome(data).getRarity(), 1)));
            return IrisRaritySelection.create(options);
        }
    }
}
