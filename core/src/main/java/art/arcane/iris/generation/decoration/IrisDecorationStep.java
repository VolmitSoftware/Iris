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

package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.documentation.Description;

import java.util.Locale;

/**
 * Pure-JVM mirror of Minecraft's decoration generation steps, in registry (ordinal) order. The ordinals and
 * serialized names match {@code GenerationStep.Decoration} on MC 26.2 so a platform can convert either way
 * without core depending on a Minecraft type. Verified against MC 26.2 GenerationStep.Decoration.
 */
@Description("A vanilla decoration generation step. Placed features are grouped into these steps and run in this order.")
public enum IrisDecorationStep {
    @Description("Raw generation - the earliest step, before lakes.")
    RAW_GENERATION("raw_generation"),

    @Description("Lakes.")
    LAKES("lakes"),

    @Description("Local modifications such as amethyst geodes, icebergs and dripstone clusters.")
    LOCAL_MODIFICATIONS("local_modifications"),

    @Description("Underground structure features (not the structure system - features tagged as underground structures).")
    UNDERGROUND_STRUCTURES("underground_structures"),

    @Description("Surface structure features.")
    SURFACE_STRUCTURES("surface_structures"),

    @Description("Stronghold step.")
    STRONGHOLDS("strongholds"),

    @Description("Underground ores. This is the step that carries vanilla and mod ore veins.")
    UNDERGROUND_ORES("underground_ores"),

    @Description("Underground decoration such as glow lichen, sculk patches and cave vegetation.")
    UNDERGROUND_DECORATION("underground_decoration"),

    @Description("Fluid springs - the small water and lava spring features.")
    FLUID_SPRINGS("fluid_springs"),

    @Description("Vegetal decoration - trees, grass, flowers, kelp and most surface plant life.")
    VEGETAL_DECORATION("vegetal_decoration"),

    @Description("Top layer modification - freezing and snow placement.")
    TOP_LAYER_MODIFICATION("top_layer_modification");

    private final String serializedName;

    IrisDecorationStep(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Resolves a step by its vanilla ordinal. Returns null when the running Minecraft version declares more
     * decoration steps than this enum knows about, which the callers treat as "unknown step, generate it".
     */
    public static IrisDecorationStep byOrdinal(int ordinal) {
        IrisDecorationStep[] values = values();
        return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
    }

    public static IrisDecorationStep byKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (IrisDecorationStep step : values()) {
            if (step.serializedName.equals(normalized) || step.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return step;
            }
        }
        return null;
    }
}
