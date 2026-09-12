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

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;

import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.Objects;

public final class NativeStructureGenerationPolicy {
    private NativeStructureGenerationPolicy() {
    }

    public static IrisNativeStructureDecision resolve(Engine engine, String structureKey,
                                                       boolean undergroundStep) {
        Engine activeEngine = Objects.requireNonNull(engine, "Native structure policy requires an engine");
        IrisDimension dimension = Objects.requireNonNull(activeEngine.getDimension(),
                "Native structure policy requires a bound dimension");
        IrisImportedStructureControl control = Objects.requireNonNull(
                dimension.getImportedStructures(),
                "Dimension importedStructures must not be null");
        IrisNativeStructureDecision decision = control.resolve(structureKey, undergroundStep);
        if (!decision.generate()) {
            // A disabled key with an active Iris placement is the "blanket-disable, re-place
            // explicitly" pattern: the placement planner ignores the disable list, so generation
            // places it — report REPLACED_BY_IRIS so find/goto/verify locate the placement.
            if (decision.status() == NativeStructureGenerationStatus.DISABLED_BY_PACK
                    && IrisStructureLocator.isPlaced(activeEngine, structureKey)) {
                return decision.withStatus(NativeStructureGenerationStatus.REPLACED_BY_IRIS);
            }
            return decision;
        }
        if (IrisStructureLocator.suppressesVanilla(activeEngine, structureKey)) {
            return decision.withStatus(NativeStructureGenerationStatus.REPLACED_BY_IRIS);
        }
        return decision;
    }

    public static String generationStatusMessage(String structureKey,
                                                 NativeStructureGenerationStatus status) {
        String key = structureKey == null ? "" : structureKey.trim();
        return switch (Objects.requireNonNull(status, "Native structure status must not be null")) {
            case GENERATE_NATIVE -> "Native structure " + key + " generates natively.";
            case DISABLED_BY_PACK -> "Native structure " + key
                    + " is disabled by this dimension's importedStructures settings.";
            case REPLACED_BY_IRIS -> "Native structure " + key
                    + " is replaced by an Iris placement in this pack and locates through that explicit replacement.";
            case INVALID_REGISTRY_KEY -> "Native structure registry key is invalid: " + key;
        };
    }
}
