/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Reference;
import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.ValidationContext;
import com.volmit.vanilla.JigsawOverrides;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks of the structure placements the pack's dimensions, regions and biomes declare that their types cannot state:
 * each picks exactly one backend, names structures that exist (the pack's, or the platform's when Iris runs on one),
 * lays out a grid that can generate, and is told apart from every other placement. Shapes, enum names and bounds
 * are the decoder's.
 */
final class StructurePlacementValidation {
    private static final String PLACEMENTS_KEY = "structures";
    private static final Pattern REGISTRY_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<StructureTerrainMode> NATIVE_ONLY_TERRAIN_MODES =
            EnumSet.of(StructureTerrainMode.VACUUM, StructureTerrainMode.ENCASE);

    private final ValidationContext pack;
    private final Set<String> packStructureKeys;
    private final Map<String, Site> placementIds = new HashMap<>();
    private final Map<GridIdentity, Site> anonymousGrids = new HashMap<>();

    private StructurePlacementValidation(ValidationContext pack) {
        this.pack = pack;
        this.packStructureKeys = pack.resources().ids(StructureResources.STRUCTURES).stream()
                .map(ResourceId::path)
                .collect(Collectors.toUnmodifiableSet());
    }

    static void validate(ValidationContext pack) {
        new StructurePlacementValidation(pack).validateDeclaredPlacements();
    }

    private void validateDeclaredPlacements() {
        for (Entry<StructurePlacementSettings> entry : pack.settings().declared(StructurePlacementSettings.class)) {
            Subject subject = Subject.resource(entry.source());
            List<StructurePlacement> placements = entry.value().placements();
            for (int index = 0; index < placements.size(); index++) {
                validatePlacement(new Site(subject, PLACEMENTS_KEY + "[" + index + "]"), placements.get(index));
            }
        }
    }

    private void validatePlacement(Site site, StructurePlacement placement) {
        validateGrid(site, placement);
        validateIdentity(site, placement);
        if (placement.hasIrisStructures() == placement.hasNativeStructures()) {
            error(site, " must declare exactly one non-empty backend: structures or nativeStructures");
            return;
        }
        if (placement.hasNativeStructures()) {
            validateNativeSources(site, placement.nativeStructures());
            return;
        }
        validateEditableStructures(site, placement);
    }

    private void validateGrid(Site site, StructurePlacement placement) {
        if (placement.separation() >= placement.spacing()) {
            error(site, ".separation must be smaller than spacing");
        }
        if (placement.minHeight() > placement.maxHeight()) {
            error(site, " has an inverted height band: minHeight must not exceed maxHeight");
        }
        if (placement.distribution() == StructureDistribution.CONCENTRIC_RINGS && ringRadius(placement) > Integer.MAX_VALUE) {
            error(site, " concentric ring radius exceeds the supported chunk coordinate range");
        }
    }

    private static long ringRadius(StructurePlacement placement) {
        return (long) Math.ceilDiv(placement.ringCount(), placement.ringSpread()) * placement.ringDistance();
    }

    private void validateIdentity(Site site, StructurePlacement placement) {
        String placementId = placement.placementId();
        if (!placementId.isEmpty() && placementId.isBlank()) {
            error(site, ".placementId must be omitted or non-blank");
            return;
        }
        if (!placementId.equals(placementId.trim())) {
            error(site, ".placementId must not contain leading or trailing whitespace");
            return;
        }
        if (!placementId.isEmpty()) {
            requireUnique(placementIds, placementId, site, ".placementId duplicates '" + placementId + "' already declared by ");
            return;
        }
        requireUnique(anonymousGrids, GridIdentity.of(placement), site,
                " duplicates an anonymous placement grid already declared by ", "; give distinct placements unique placementId values");
    }

    private <K> void requireUnique(Map<K, Site> declared, K key, Site site, String message) {
        requireUnique(declared, key, site, message, "");
    }

    private <K> void requireUnique(Map<K, Site> declared, K key, Site site, String message, String advice) {
        Site first = declared.putIfAbsent(key, site);
        if (first != null) {
            error(site, message + first + advice);
        }
    }

    private void validateEditableStructures(Site site, StructurePlacement placement) {
        StructureTerrainMode terrainMode = placement.resolvedTerrain().resolvedMode();
        if (NATIVE_ONLY_TERRAIN_MODES.contains(terrainMode)) {
            error(site, ".terrain.mode " + terrainMode + " cannot target editable Iris structures; use nativeStructures or"
                    + " importedStructures.adjustments for native terrain preparation");
        }
        Set<String> seenKeys = new HashSet<>();
        List<String> structures = placement.structures();
        for (int index = 0; index < structures.size(); index++) {
            validateEditableStructure(site.at(".structures[" + index + "]"), structures.get(index), seenKeys);
        }
    }

    private void validateEditableStructure(Site site, String structureKey, Set<String> seenKeys) {
        if (structureKey == null || structureKey.isBlank()) {
            error(site, " must name a non-blank Iris structure");
            return;
        }
        if (!seenKeys.add(structureKey.trim().toLowerCase(Locale.ROOT))) {
            error(site, " duplicates Iris structure '" + structureKey + "'");
            return;
        }
        if (!packStructureKeys.contains(structureKey)) {
            error(site, " references missing structure '" + structureKey + "'");
        }
    }

    private void validateNativeSources(Site site, List<NativeStructureSource> sources) {
        Set<String> seenKeys = new HashSet<>();
        long totalWeight = 0L;
        for (int index = 0; index < sources.size(); index++) {
            NativeStructureSource source = sources.get(index);
            Site sourceSite = site.at(".nativeStructures[" + index + "]");
            validateNativeStructureKey(sourceSite, source.structure().trim(), seenKeys);
            source.jigsaw().ifPresent(overrides -> validateJigsawOverrides(sourceSite.at(".jigsaw"), overrides));
            totalWeight += source.weight();
        }
        if (totalWeight > Integer.MAX_VALUE) {
            error(site, ".nativeStructures total weight exceeds " + Integer.MAX_VALUE);
        }
    }

    private void validateNativeStructureKey(Site site, String structureKey, Set<String> seenKeys) {
        if (!REGISTRY_KEY.matcher(structureKey).matches()) {
            error(site, ".structure must be a namespaced registry key");
            return;
        }
        if (!seenKeys.add(structureKey.toLowerCase(Locale.ROOT))) {
            error(site, ".structure duplicates native source '" + structureKey + "'");
            return;
        }
        if (isUnregistered(Reference.STRUCTURE, ResourceId.ofPlatformKey(structureKey))) {
            error(site, ".structure '" + structureKey + "' is not a registered structure");
        }
    }

    private void validateJigsawOverrides(Site site, JigsawOverrides overrides) {
        Optional<ResourceId> startPool;
        try {
            startPool = overrides.startPool();
        } catch (IllegalArgumentException invalid) {
            error(site, ".startPool must be a namespaced registry key");
            return;
        }
        startPool.filter(pool -> isUnregistered(Reference.TEMPLATE_POOL, pool))
                .ifPresent(pool -> error(site, ".startPool '" + pool + "' is not a registered template pool"));
    }

    /** Whether the platform registers no {@code id} under {@code registry}; false without a platform, which cannot tell. */
    private boolean isUnregistered(Reference registry, ResourceId id) {
        Set<ResourceId> registered = pack.platform().map(platform -> platform.ids(registry)).orElse(Set.of());
        return !registered.isEmpty() && !registered.contains(id);
    }

    private void error(Site site, String message) {
        pack.report(Problem.error(site.subject(), site.path() + message));
    }

    /** Where in the pack a check looks: a dimension, region or biome and the option path inside it. */
    private record Site(Subject subject, String path) {
        Site at(String suffix) {
            return new Site(subject, path + suffix);
        }

        @Override
        public String toString() {
            return subject + " " + path;
        }
    }

    /** What makes two placements without a placementId start the same structures on the same grid. */
    private record GridIdentity(StructureDistribution distribution, int salt, int spacing, int separation, long densityBits,
                                int ringCount, int ringDistance, int ringSpread, int minHeight, int maxHeight,
                                boolean underground, boolean underwater, List<String> irisStructures,
                                List<String> nativeSources) {
        static GridIdentity of(StructurePlacement placement) {
            return new GridIdentity(placement.distribution(), placement.salt(), placement.spacing(), placement.separation(),
                    Double.doubleToLongBits(placement.density()), placement.ringCount(), placement.ringDistance(),
                    placement.ringSpread(), placement.minHeight(), placement.maxHeight(), placement.underground(),
                    placement.underwater(), placement.structures().stream().sorted().toList(),
                    placement.nativeStructures().stream().map(source -> source.structure() + "@" + source.weight()).sorted().toList());
        }
    }
}
