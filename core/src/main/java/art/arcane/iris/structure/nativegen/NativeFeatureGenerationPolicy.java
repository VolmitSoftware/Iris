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

import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.Objects;

/**
 * Single decision point for native placed-feature generation, mirroring {@link NativeStructureGenerationPolicy}.
 * Both platform generators call only through here so the Bukkit and modded feature passes cannot drift.
 */
public final class NativeFeatureGenerationPolicy {
    /**
     * Stand-in for a dimension that has no control block. Immutable in practice: nothing here hands it out for
     * mutation, and every default in {@link IrisImportedFeatureControl} is "off".
     */
    private static final IrisImportedFeatureControl DISABLED = new IrisImportedFeatureControl();

    private NativeFeatureGenerationPolicy() {
    }

    /**
     * The dimension's control block, or a disabled default when it has none.
     * <p>
     * An absent {@code importedFeatures} member deserializes to the field's initializer, but an explicit
     * {@code "importedFeatures": null} in dimension JSON overwrites that initializer with null - Gson assigns what
     * the document says. This runs on the generation path for every feature decision, so throwing there turns one
     * stray null in a pack into a failed chunk rather than a dimension that simply generates no native features.
     */
    public static IrisImportedFeatureControl control(Engine engine) {
        Engine activeEngine = Objects.requireNonNull(engine, "Native feature policy requires an engine");
        IrisDimension dimension = Objects.requireNonNull(activeEngine.getDimension(),
                "Native feature policy requires a bound dimension");
        IrisImportedFeatureControl control = dimension.getImportedFeatures();
        return control == null ? DISABLED : control;
    }

    /**
     * True when this dimension opted into the native feature pass. Checked before any feature machinery is
     * built so a disabled dimension never allocates a feature table.
     */
    public static boolean isEnabled(Engine engine) {
        return control(engine).shouldGenerateFeatures();
    }

    public static NativeFeatureGenerationStatus resolve(Engine engine, String placedFeatureKey,
                                                        IrisDecorationStep step) {
        return control(engine).resolve(placedFeatureKey, step);
    }

    public static boolean shouldGenerateStep(Engine engine, IrisDecorationStep step) {
        return control(engine).shouldGenerateStep(step);
    }

    public static String generationStatusMessage(String placedFeatureKey,
                                                 NativeFeatureGenerationStatus status) {
        String key = placedFeatureKey == null ? "" : placedFeatureKey.trim();
        return switch (Objects.requireNonNull(status, "Native feature status must not be null")) {
            case GENERATE_NATIVE -> "Native feature " + key + " generates natively.";
            case FEATURES_DISABLED -> "Native feature " + key
                    + " does not generate because this dimension's importedFeatures.enabled is false.";
            case STEP_DISABLED -> "Native feature " + key
                    + " does not generate because its decoration step is excluded by importedFeatures.";
            case DISABLED_BY_PACK -> "Native feature " + key
                    + " is disabled by this dimension's importedFeatures.disabled list.";
            case CYCLE_DEGRADED -> "Native feature " + key
                    + " does not generate because the registered features could not be ordered "
                    + "(feature order cycle); importedFeatures was degraded to off for this dimension.";
            case INVALID_REGISTRY_KEY -> "Native feature registry key is invalid: " + key;
        };
    }
}
