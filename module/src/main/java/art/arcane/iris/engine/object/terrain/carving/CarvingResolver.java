/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.carve.CarvingEntry;
import art.arcane.iris.modules.carve.CarvingSettings;
import com.volmit.ModuleLog;
import com.volmit.config.Range;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the carve module keeps for one world about the cave biome bands its dimension declares ({@link CarvingSettings}): the
 * entries made ready ({@link ResolvedCarvingEntry}), in the order the dimension lists them and by id, and the picks among them.
 * A pick is the dimension's last enabled root entry whose world Y band holds a height, then, through the entries' children, the
 * one a noise pattern selects at a column.
 */
public final class CarvingResolver {
    private static final int MAX_CHILD_DEPTH = 32;
    private static final long CHILD_SEED_SALT = 0x9E3779B97F4A7C15L;

    private final PackResources resources;
    private final ModuleLog log;
    private final int minHeight;
    private final long childSeed;
    private final List<ResolvedCarvingEntry> entries;
    private final Map<String, ResolvedCarvingEntry> index;
    private final Map<Integer, Optional<ResolvedCarvingEntry>> rootEntriesByWorldY = new ConcurrentHashMap<>();
    private final Map<ResolvedCarvingEntry, ParentSelectionPlan> selectionPlans = new ConcurrentHashMap<>();

    CarvingResolver(PackWorld world, List<CarvingEntry> declared) {
        this.resources = world.resources();
        this.log = world.log();
        this.minHeight = world.minWorldY();
        this.childSeed = world.seed(IrisSeedPurposes.CARVE) ^ CHILD_SEED_SALT;
        this.entries = declared.stream().filter(Objects::nonNull).map(ResolvedCarvingEntry::new).toList();
        this.index = indexById(entries);
    }

    /** The bands of the world {@code world} generates now. */
    public static CarvingResolver of(PackWorld world) {
        return world.state(CarvingResolver.class, generating -> new CarvingResolver(generating, dimensionEntries(generating)));
    }

    private static List<CarvingEntry> dimensionEntries(PackWorld world) {
        return world.settings().declared(CarvingSettings.class).dimension().map(CarvingSettings::entries).orElse(List.of());
    }

    /** Every declared entry, enabled or not. */
    public List<ResolvedCarvingEntry> entries() {
        return entries;
    }

    /** The entries by their trimmed ids; entries without an id are not in it, and the last of several with one id is. */
    public Map<String, ResolvedCarvingEntry> index() {
        return index;
    }

    /** The root entry for a height above the dimension's bottom; null where no enabled entry with a biome holds it. */
    public ResolvedCarvingEntry rootEntryAt(int y) {
        return rootEntryAtWorldY(y + minHeight);
    }

    /** The root entry for a world Y as packs write them; null where no enabled entry with a biome holds it. */
    public ResolvedCarvingEntry rootEntryAtWorldY(int worldY) {
        return rootEntriesByWorldY.computeIfAbsent(worldY, this::pickRootEntry).orElse(null);
    }

    private Optional<ResolvedCarvingEntry> pickRootEntry(int worldY) {
        ResolvedCarvingEntry resolved = null;
        for (ResolvedCarvingEntry entry : entries) {
            if (isRootCandidate(entry, worldY)) {
                resolved = entry;
            }
        }
        return Optional.ofNullable(resolved);
    }

    public ResolvedCarvingEntry resolveFromRoot(ResolvedCarvingEntry rootEntry, int worldX, int worldZ) {
        if (rootEntry == null) {
            return null;
        }

        PackBiome rootBiome = entryBiome(rootEntry);
        if (rootBiome == null) {
            return null;
        }

        int remainingDepth = clampDepth(rootEntry.childRecursionDepth());
        if (remainingDepth <= 0) {
            return rootEntry;
        }

        ResolvedCarvingEntry current = rootEntry;
        int depth = remainingDepth;
        while (depth > 0) {
            ResolvedCarvingEntry selected = selectChild(current, worldX, worldZ);
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

    /** The pack biome the entry names; null when it names none or the pack has no such biome. */
    public PackBiome entryBiome(ResolvedCarvingEntry entry) {
        return entry == null ? null : entry.realBiome(resources, log);
    }

    private boolean isRootCandidate(ResolvedCarvingEntry entry, int worldY) {
        if (entry == null || !entry.enabled()) {
            return false;
        }

        Range worldYRange = entry.worldYRange();
        if (worldYRange != null && !worldYRange.contains(worldY)) {
            return false;
        }

        return entryBiome(entry) != null;
    }

    private ResolvedCarvingEntry selectChild(ResolvedCarvingEntry parent, int worldX, int worldZ) {
        List<String> children = parent.children();
        if (children.isEmpty()) {
            return parent;
        }

        PackBiome parentBiome = entryBiome(parent);
        if (parentBiome == null) {
            return parent;
        }

        ParentSelectionPlan selectionPlan = selectionPlans.computeIfAbsent(parent, key -> buildSelectionPlan(key, parentBiome));
        if (selectionPlan.parentOnly) {
            return parent;
        }

        NoiseSampler childGenerator = parent.childrenGenerator(childSeed, resources);
        double sample = childGenerator.sample(worldX, worldZ);
        int selectedIndex = (int) Math.round(sample * selectionPlan.maxIndex);
        CarvingChoice selected = selectionPlan.get(selectedIndex);
        if (selected == null || selected.entry == null) {
            return parent;
        }

        return selected.entry;
    }

    private ParentSelectionPlan buildSelectionPlan(ResolvedCarvingEntry parent, PackBiome parentBiome) {
        List<CarvingChoice> options = new ArrayList<>();
        for (String childId : parent.children()) {
            if (childId == null || childId.isBlank()) {
                continue;
            }

            ResolvedCarvingEntry child = index.get(childId.trim());
            if (child == null || !child.enabled()) {
                continue;
            }

            PackBiome childBiome = entryBiome(child);
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

    private static int rarity(PackBiome biome) {
        if (biome == null) {
            return 1;
        }

        return Math.max(biome.rarity(), 1);
    }

    private static int clampDepth(int depth) {
        if (depth <= 0) {
            return 0;
        }

        return Math.min(depth, MAX_CHILD_DEPTH);
    }

    private static Map<String, ResolvedCarvingEntry> indexById(List<ResolvedCarvingEntry> entries) {
        Map<String, ResolvedCarvingEntry> index = new HashMap<>();
        for (ResolvedCarvingEntry entry : entries) {
            if (!entry.id().isBlank()) {
                index.put(entry.id().trim(), entry);
            }
        }
        return Map.copyOf(index);
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

    private record CarvingChoice(ResolvedCarvingEntry entry, int rarity) {
    }
}
