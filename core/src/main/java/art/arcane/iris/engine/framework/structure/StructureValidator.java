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

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import com.volmit.config.FeatureSettings;
import com.volmit.validation.Problem;
import com.volmit.validation.ValidationContext;
import com.volmit.validation.Validator;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module {@code iris:structure}'s check of a pack: every structure a dimension, region or biome places must assemble
 * ({@link StructureGraphValidation}), and a dimension that replaces a vanilla structure with an Iris one may only use
 * structures that always assemble and fit ({@link NativeReplacementValidation}), every structure, pool and piece names
 * resources that exist ({@link StructureReferenceValidation}), every structure placement can generate
 * ({@link StructurePlacementValidation}), and its {@code importedStructures} name real keys
 * ({@link ImportedStructuresValidation}). Graph diagnostics keep their
 * severity; everything else is an error, as when Iris's own pack validator ran these checks.
 */
public final class StructureValidator implements Validator {
    @Override
    public void validate(ValidationContext pack) {
        File packFolder = pack.settings().extension(File.class).orElse(null);
        StructureGraphValidation.Validation graphs = packFolder == null
                ? new StructureGraphValidation.Validation(List.of(), Set.of(), Map.of())
                : StructureGraphValidation.validate(pack.resources(), packFolder, placedStructureKeys(pack));
        graphs.problems().forEach(pack::report);
        reportUnlessAssemblyReported(pack, StructureReferenceValidation.validate(pack.resources()), graphs.problems());
        NativeReplacementValidation.validate(packFolder, graphs.replacementOutputStructures(), graphs.sampledVerticalEnvelopes())
                .forEach(pack::report);
        StructurePlacementValidation.validate(pack);
        ImportedStructuresValidation.validate(pack);
    }

    /** Reports {@code references} whose message assembling a placed structure did not already report. */
    private static void reportUnlessAssemblyReported(ValidationContext pack, List<Problem> references, List<Problem> assembly) {
        Set<String> reported = assembly.stream().map(Problem::message).collect(Collectors.toSet());
        references.stream().filter(problem -> !reported.contains(problem.message())).forEach(pack::report);
    }

    /** The keys of the structures the dimensions, regions and biomes of the pack place, whether or not a dimension reaches them. */
    public static Set<String> placedStructureKeys(ValidationContext pack) {
        Set<String> structureKeys = new LinkedHashSet<>();
        for (FeatureSettings.Entry<StructurePlacementSettings> declared : pack.settings().declared(StructurePlacementSettings.class).entries()) {
            for (StructurePlacement placement : declared.value().placements()) {
                placement.structures().stream().filter(key -> key != null && !key.isBlank()).forEach(structureKeys::add);
            }
        }
        return Set.copyOf(structureKeys);
    }
}
