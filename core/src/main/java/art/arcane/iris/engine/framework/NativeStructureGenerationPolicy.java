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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.modules.ModuleVanillaStructures;
import art.arcane.iris.engine.object.structure.vanilla.IrisImportedStructureControl;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureGenerationStatus;
import com.volmit.vanilla.VanillaStructureRule;

import java.util.Objects;

/**
 * Whether the platform generates one of its own structures in an engine's world, the same on every platform: the
 * dimension's {@code importedStructures} settings first, then the rule the pack's modules give the structure through
 * their vanilla structure hooks ({@link ModuleVanillaStructures}). A structure a module blocks does not generate; one a
 * module places itself is reported as replaced, so find, goto and verify locate the module's placement.
 */
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
        if (decision.status() == NativeStructureGenerationStatus.INVALID_REGISTRY_KEY) {
            return decision;
        }
        VanillaStructureRule modules = ModuleVanillaStructures.ruleFor(activeEngine, structureKey);
        if (!decision.generate()) {
            // A key the dimension disables that a module places is the "blanket-disable, re-place explicitly"
            // pattern: report REPLACED_BY_IRIS so find/goto/verify locate the module's placement.
            return modules.placedByModule() ? decision.withStatus(NativeStructureGenerationStatus.REPLACED_BY_IRIS) : decision;
        }
        if (!modules.blocksPlatform()) {
            return decision;
        }
        return decision.withStatus(modules.placedByModule()
                ? NativeStructureGenerationStatus.REPLACED_BY_IRIS
                : NativeStructureGenerationStatus.DISABLED_BY_PACK);
    }

    public static String generationStatusMessage(String structureKey,
                                                 NativeStructureGenerationStatus status) {
        String key = structureKey == null ? "" : structureKey.trim();
        return switch (Objects.requireNonNull(status, "Native structure status must not be null")) {
            case GENERATE_NATIVE -> "Native structure " + key + " generates natively.";
            case DISABLED_BY_PACK -> "Native structure " + key
                    + " is disabled by this dimension's importedStructures settings or a module of the pack.";
            case REPLACED_BY_IRIS -> "Native structure " + key
                    + " is replaced by an Iris placement in this pack and locates through that explicit replacement.";
            case INVALID_REGISTRY_KEY -> "Native structure registry key is invalid: " + key;
        };
    }
}
