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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.StructurePlacementGrid;
import art.arcane.iris.engine.framework.StructurePlacementScope;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.volmlib.util.math.RNG;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Where the pack's {@code nativeStructures} placements start platform structures: for one chunk, every placement in
 * scope whose grid starts there picks a weighted source and a base height. When several placements start the same
 * structure in one chunk, the placement with the lowest {@link StructurePlacementGrid#placementIdentity} (unsigned)
 * wins. Seeded from the mantle seed with the selection domain the planner always used, so worlds keep their starts.
 */
public final class NativeStartPlanner {
    private static final long NATIVE_SELECTION_DOMAIN = 0x4E41544956454A53L;

    private NativeStartPlanner() {
    }

    /** One start a {@code nativeStructures} placement makes: the placement, its chosen source and world base Y. */
    public record Plan(StructurePlacement placement, NativeStructureSource source, int chunkX, int chunkZ, int baseY) {
    }

    public static List<Plan> plansAt(Engine engine, int chunkX, int chunkZ) {
        Map<String, Plan> plansByStructure = new LinkedHashMap<>();
        for (StructurePlacement placement : StructurePlacementScope.placementsAt(engine, chunkX, chunkZ)) {
            Plan plan = planAt(engine, placement, chunkX, chunkZ);
            if (plan == null) {
                continue;
            }
            String structureKey = normalize(plan.source().structure());
            Plan current = plansByStructure.get(structureKey);
            if (current == null || comparePlacementPriority(plan.placement(), current.placement()) < 0) {
                plansByStructure.put(structureKey, plan);
            }
        }
        return List.copyOf(plansByStructure.values());
    }

    public static Plan planAt(Engine engine, StructurePlacement placement, int chunkX, int chunkZ) {
        validateBackend(placement);
        if (!placement.hasNativeStructures()) {
            return null;
        }
        Engine activeEngine = Objects.requireNonNull(engine, "Native structure planner requires an engine");
        if (activeEngine.getSeedManager() == null) {
            throw new IllegalStateException("Native structure planner requires a bound seed manager");
        }
        long mantleSeed = activeEngine.getSeedManager().getMantle();
        if (!StructurePlacementGrid.startsInChunk(placement, chunkX, chunkZ, mantleSeed)) {
            return null;
        }
        RNG placementRng = StructurePlacementGrid.placementRng(
                placement, chunkX, chunkZ, mantleSeed).nextParallelRNG(NATIVE_SELECTION_DOMAIN);
        NativeStructureSource source = selectSource(placement.nativeStructures(), placementRng);
        Integer baseY = resolveBaseY(activeEngine, placement, chunkX, chunkZ, placementRng);
        if (baseY == null) {
            return null;
        }
        return new Plan(placement, source, chunkX, chunkZ, baseY);
    }

    public static void validateBackend(StructurePlacement placement) {
        if (placement == null) {
            throw new IllegalStateException("Structure placement must not be null");
        }
        boolean iris = placement.hasIrisStructures();
        boolean nativeStructure = placement.hasNativeStructures();
        if (iris == nativeStructure) {
            throw new IllegalStateException("Structure placement must declare exactly one backend: "
                    + "structures or nativeStructures");
        }
        if (nativeStructure && placement.anchor() != StructureAnchorMode.LEGACY) {
            throw new IllegalStateException("Native structure placements do not support the Iris anchor field");
        }
    }

    public static boolean isSubmerged(Engine engine, int blockX, int blockZ) {
        return engine.getHeight(blockX, blockZ, true) < engine.getDimension().getFluidHeight();
    }

    static NativeStructureSource selectSource(List<NativeStructureSource> sources, RNG rng) {
        Objects.requireNonNull(sources, "Native structure sources must not be null");
        Objects.requireNonNull(rng, "Native structure selection requires an RNG");
        long totalWeight = 0L;
        for (NativeStructureSource source : sources) {
            if (source == null || source.structure() == null || source.structure().isBlank()) {
                throw new IllegalStateException("Native structure placement contains a blank source");
            }
            if (source.weight() < 1) {
                throw new IllegalStateException("Native structure source '" + source.structure()
                        + "' has invalid weight " + source.weight());
            }
            totalWeight = Math.addExact(totalWeight, source.weight());
        }
        if (totalWeight > Integer.MAX_VALUE) {
            throw new IllegalStateException("Native structure source weights exceed " + Integer.MAX_VALUE);
        }
        int roll = rng.nextInt((int) totalWeight);
        for (NativeStructureSource source : sources) {
            roll -= source.weight();
            if (roll < 0) {
                return source;
            }
        }
        throw new IllegalStateException("Native structure source selection produced no result");
    }

    private static Integer resolveBaseY(Engine engine, StructurePlacement placement,
                                        int chunkX, int chunkZ, RNG rng) {
        int worldMin = engine.getMinHeight() + 1;
        int worldMax = engine.getMinHeight() + engine.getHeight() - 1;
        if (worldMin > worldMax) {
            throw new IllegalStateException("Native structure placement has invalid world height bounds");
        }
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        if (!placement.underwater() && isSubmerged(engine, blockX, blockZ)) {
            return null;
        }
        if (!placement.underground()) {
            int surfaceY = engine.getHeight(blockX, blockZ, true) + engine.getMinHeight();
            if (surfaceY < placement.minHeight() || surfaceY > placement.maxHeight()) {
                return null;
            }
            return surfaceY;
        }
        int bandMin = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        if (bandMin > bandMax) {
            return null;
        }
        return bandMin == bandMax ? bandMin : bandMin + rng.nextInt((bandMax - bandMin) + 1);
    }

    private static int comparePlacementPriority(StructurePlacement left, StructurePlacement right) {
        long leftIdentity = StructurePlacementGrid.placementIdentity(left);
        long rightIdentity = StructurePlacementGrid.placementIdentity(right);
        return Long.compareUnsigned(leftIdentity, rightIdentity);
    }

    private static String normalize(String structureKey) {
        return structureKey == null ? "" : structureKey.toLowerCase(Locale.ROOT);
    }
}
