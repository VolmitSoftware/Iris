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

import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.modules.VanillaFeatureAnswers;

import java.util.Objects;

/**
 * Single decision point for native placed-feature generation, mirroring {@link NativeStructureGenerationPolicy}: what
 * the pack's modules say through {@link com.volmit.service.VanillaFeatures}, kept for the engine's world. Both platform
 * generators call only through here so the Bukkit and modded feature passes cannot drift.
 */
public final class NativeFeatureGenerationPolicy {
    private NativeFeatureGenerationPolicy() {
    }

    /**
     * True when a module turned the native feature pass on for this world. Checked before any feature machinery is
     * built so a world without it never allocates a feature table.
     */
    public static boolean isEnabled(Engine engine) {
        return answers(engine).generates();
    }

    /** Whether the features of the platform's decoration step {@code stepIndex} (in the platform's order) generate. */
    public static boolean shouldGenerateStep(Engine engine, int stepIndex) {
        return answers(engine).generatesStep(stepIndex);
    }

    /** Whether the placed feature {@code placedFeatureKey}, such as {@code minecraft:ore_diamond}, generates. */
    public static boolean shouldGenerate(Engine engine, String placedFeatureKey) {
        return answers(engine).generatesFeature(placedFeatureKey);
    }

    private static VanillaFeatureAnswers answers(Engine engine) {
        return ModuleServices.vanillaFeatures(Objects.requireNonNull(engine, "Native feature policy requires an engine"));
    }
}
