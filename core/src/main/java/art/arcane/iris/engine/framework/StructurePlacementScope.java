package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.engine.framework.structure.StructureWorld;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The structure placements that apply at a chunk: its biome's, its cave biome's cave-anchored ones, its region's and
 * the dimension's. Each world keeps which of those levels declare placements at all, so chunks skip the biome and
 * region lookups a pack never needs.
 */
public final class StructurePlacementScope {
    private static final int CHUNK_CENTER_OFFSET_BLOCKS = 8;

    private StructurePlacementScope() {
    }

    /** The placements {@code settings} declare; empty for none. */
    public static List<StructurePlacement> placementsOf(Optional<StructurePlacementSettings> settings) {
        return settings.map(StructurePlacementSettings::placements).orElse(List.of());
    }

    public static List<StructurePlacement> placementsAt(PackWorld world, int chunkX, int chunkZ) {
        RunningWorld running = StructureWorld.running(world);
        int blockX = (chunkX << 4) + CHUNK_CENTER_OFFSET_BLOCKS;
        int blockZ = (chunkZ << 4) + CHUNK_CENTER_OFFSET_BLOCKS;
        List<StructurePlacement> placements = new ArrayList<>();
        Set<StructurePlacement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ScopeIndex index = world.state(ScopeIndex.class, StructurePlacementScope::buildScopeIndex);
        if (index.declaresBelowDimension()) {
            addColumnPlacements(placements, seen, running, index, blockX, blockZ);
        }
        addUnique(placements, seen, index.dimensionPlacements());
        return placements;
    }

    private static void addColumnPlacements(List<StructurePlacement> placements, Set<StructurePlacement> seen,
                                            RunningWorld running, ScopeIndex index, int blockX, int blockZ) {
        SettingsStack<StructurePlacementSettings> declared = running.settings().at(StructurePlacementSettings.class, blockX, blockZ);
        if (index.biome()) {
            addUnique(placements, seen, placementsOf(declared.level(Level.BIOME)));
        }
        if (index.caveBiome()) {
            addCaveUnique(placements, seen, placementsOf(caveBiomeSettings(running, blockX, blockZ)));
        }
        if (index.region()) {
            addUnique(placements, seen, placementsOf(declared.level(Level.REGION)));
        }
    }

    private static Optional<StructurePlacementSettings> caveBiomeSettings(RunningWorld running, int blockX, int blockZ) {
        TerrainSampler layout = running.layout();
        ResourceId surfaceBiome = layout.surfaceBiome(blockX, blockZ);
        Optional<ResourceId> caveBiome = layout.caveBiome(blockX, layout.bounds().minY(), blockZ)
                .filter(cave -> !cave.equals(surfaceBiome));
        FeatureSettings settings = running.settings();
        return caveBiome.flatMap(cave -> settings.declaredBy(cave, StructurePlacementSettings.class));
    }

    private static ScopeIndex buildScopeIndex(PackWorld world) {
        boolean biome = false;
        boolean caveBiome = false;
        boolean region = false;
        List<StructurePlacement> dimensionPlacements = List.of();
        for (var entry : world.settings().declared(StructurePlacementSettings.class)) {
            List<StructurePlacement> placements = entry.value().placements();
            if (placements.isEmpty()) {
                continue;
            }
            switch (entry.level()) {
                case BIOME -> {
                    biome = true;
                    caveBiome |= placements.stream().anyMatch(placement -> placement.resolvedAnchor().isCave());
                }
                case REGION -> region = true;
                case DIMENSION -> dimensionPlacements = placements;
                default -> {
                }
            }
        }
        return new ScopeIndex(biome, caveBiome, region, dimensionPlacements);
    }

    private static void addUnique(List<StructurePlacement> destination, Set<StructurePlacement> seen,
                                  List<StructurePlacement> source) {
        for (StructurePlacement placement : source) {
            if (placement != null && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    private static void addCaveUnique(List<StructurePlacement> destination, Set<StructurePlacement> seen,
                                      List<StructurePlacement> source) {
        for (StructurePlacement placement : source) {
            if (placement != null && placement.resolvedAnchor().isCave() && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    record ScopeIndex(boolean biome, boolean caveBiome, boolean region, List<StructurePlacement> dimensionPlacements) {
        boolean declaresBelowDimension() {
            return biome || caveBiome || region;
        }
    }
}
