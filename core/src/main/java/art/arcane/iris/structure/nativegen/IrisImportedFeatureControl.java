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

import art.arcane.iris.generation.decoration.IrisDecorationStep;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Controls native vanilla, mod, and ingested datapack PLACED FEATURE generation for this dimension (set as the dimension's 'importedFeatures' field). Placed features are ores, trees, plants, springs, geodes and every other decoration entry a biome declares. This is OFF by default: with 'enabled' false Iris generates exactly the terrain it always has and no native feature runs. With it true, every placed feature the biome's vanilla derivative declares runs over Iris terrain, in vanilla step order, on the same worldgen thread vanilla uses. Carvers are never imported - Iris has no NoiseGeneratorSettings, so there is nothing for a carver to carve against. Family matching on 'disabled' uses namespace, slash, or underscore boundaries, so 'minecraft:ore' covers every vanilla ore feature without matching unrelated names.")
@Data
public class IrisImportedFeatureControl {
    @Description("Master switch. False (the default) means no native placed feature generates and chunk output is identical to a pack without this block at all. True runs the vanilla decoration feature pass over Iris terrain.")
    private boolean enabled = false;

    @ArrayType(type = String.class, min = 1)
    @Description("Placed feature keys to deny explicitly, e.g. 'minecraft:ore_diamond'. A namespace:path prefix also matches, so 'minecraft:ore' denies every vanilla ore and 'minecraft:trees' denies every tree placement. Every key not matched here generates while 'enabled' is true.")
    private KList<String> disabled = new KList<>();

    @ArrayType(type = IrisDecorationStep.class, min = 1)
    @Description("Restrict feature generation to these decoration steps only. Empty (the default) means every step is eligible. Use this to import ores without importing vegetation: set it to UNDERGROUND_ORES.")
    private KList<IrisDecorationStep> steps = new KList<>();

    @ArrayType(type = IrisDecorationStep.class, min = 1)
    @Description("Decoration steps to deny. Applied after 'steps', so a step listed here never generates even if 'steps' allows it. VEGETAL_DECORATION is the usual entry for packs that grow their own trees.")
    private KList<IrisDecorationStep> disabledSteps = new KList<>();

    /**
     * True when this dimension wants the native feature pass at all. Every other accessor on this class is
     * meaningless while this is false, and the platform must not build any feature machinery.
     */
    public boolean shouldGenerateFeatures() {
        return enabled;
    }

    public boolean shouldGenerateStep(IrisDecorationStep step) {
        if (!enabled) {
            return false;
        }
        KList<IrisDecorationStep> allowed = Objects.requireNonNull(
                steps, "importedFeatures.steps must not be null");
        KList<IrisDecorationStep> denied = Objects.requireNonNull(
                disabledSteps, "importedFeatures.disabledSteps must not be null");
        if (step == null) {
            // A step this Iris build does not know about (newer Minecraft): allow it unless the pack
            // narrowed generation to an explicit step list.
            return allowed.isEmpty();
        }
        if (!allowed.isEmpty() && !allowed.contains(step)) {
            return false;
        }
        return !denied.contains(step);
    }

    public boolean shouldGenerate(String placedFeatureKey) {
        return resolve(placedFeatureKey, null) == NativeFeatureGenerationStatus.GENERATE_NATIVE;
    }

    /**
     * Resolves one placed feature against this control. A null step skips the step gate, which is what the
     * key-only query does.
     */
    public NativeFeatureGenerationStatus resolve(String placedFeatureKey, IrisDecorationStep step) {
        if (!enabled) {
            return NativeFeatureGenerationStatus.FEATURES_DISABLED;
        }
        if (placedFeatureKey == null || placedFeatureKey.isBlank()) {
            return NativeFeatureGenerationStatus.INVALID_REGISTRY_KEY;
        }
        if (step != null && !shouldGenerateStep(step)) {
            return NativeFeatureGenerationStatus.STEP_DISABLED;
        }
        KList<String> deniedKeys = Objects.requireNonNull(
                disabled, "importedFeatures.disabled must not be null");
        for (String entry : deniedKeys) {
            if (IrisImportedStructureControl.matchesKey(entry, placedFeatureKey)) {
                return NativeFeatureGenerationStatus.DISABLED_BY_PACK;
            }
        }
        return NativeFeatureGenerationStatus.GENERATE_NATIVE;
    }
}
