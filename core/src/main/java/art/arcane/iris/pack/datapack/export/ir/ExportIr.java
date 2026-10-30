package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ExportIr(
        String namespace,
        DataVersion target,
        WorldgenExportMode mode,
        DimensionDef dimension,
        List<NoiseDef> noises,
        RouterDef router,
        ClimateLayout climate,
        List<BiomeDef> biomes,
        MaterialProgram material,
        List<ExportDiagnostic> diagnostics,
        List<JsonResource> jsonResources,
        List<BinaryResource> binaryResources
) {
    public ExportIr {
        VanillaResourceIdentifier.requireNamespace(namespace, "export namespace");
        Objects.requireNonNull(target);
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
        Objects.requireNonNull(mode);
        Objects.requireNonNull(dimension);
        noises = List.copyOf(noises);
        Objects.requireNonNull(router);
        Objects.requireNonNull(climate);
        biomes = List.copyOf(biomes);
        Objects.requireNonNull(material);
        diagnostics = List.copyOf(diagnostics);
        jsonResources = List.copyOf(jsonResources);
        binaryResources = List.copyOf(binaryResources);
        Set<String> noiseIds = new HashSet<>(noises.size());
        for (NoiseDef noise : noises) {
            requireUnique(noiseIds, noise.id(), "noise id");
        }
        Set<String> biomeIds = new HashSet<>(biomes.size());
        for (BiomeDef biome : biomes) {
            requireUnique(biomeIds, biome.id(), "biome id");
        }
        String localPrefix = namespace + ":";
        for (ClimateLayout.ClimateEntry entry : climate.entries()) {
            if (entry.biomeId().startsWith(localPrefix) && !biomeIds.contains(entry.biomeId().substring(localPrefix.length()))) {
                throw new IllegalArgumentException("Climate entry references undefined biome '" + entry.biomeId() + "'");
            }
        }
        Set<String> resourcePaths = new HashSet<>(jsonResources.size() + binaryResources.size());
        for (JsonResource resource : jsonResources) {
            requireUnique(resourcePaths, resource.path(), "resource path");
        }
        for (BinaryResource resource : binaryResources) {
            requireUnique(resourcePaths, resource.path(), "resource path");
        }
    }

    private static void requireUnique(Set<String> seen, String value, String role) {
        if (!seen.add(value)) {
            throw new IllegalArgumentException("Duplicate " + role + " '" + value + "'");
        }
    }
}
