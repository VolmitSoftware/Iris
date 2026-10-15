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
import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeatureControl;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Controls native vanilla, mod, and ingested datapack PLACED FEATURE generation for this dimension (set as the dimension's 'importedFeatures' field). Placed features are ores, trees, plants, springs, geodes and every other decoration entry a biome declares. This is OFF by default: with 'enabled' false Iris generates exactly the terrain it always has and no native feature runs. With it true, every placed feature the biome's vanilla derivative declares runs over Iris terrain, in vanilla step order, on the same worldgen thread vanilla uses. Carvers are never imported - Iris has no NoiseGeneratorSettings, so there is nothing for a carver to carve against. Family matching on 'disabled' uses namespace, slash, or underscore boundaries, so 'minecraft:ore' covers every vanilla ore feature without matching unrelated names, while 'disabledExact' compares normalized complete keys only.")
@Data
public class IrisImportedFeatureControl implements NativeImportedFeatureControl {
    @Description("Master switch. False (the default) means no native placed feature generates and chunk output is identical to a pack without this block at all. True runs the vanilla decoration feature pass over Iris terrain.")
    private boolean enabled = false;

    @ArrayType(type = String.class, min = 1)
    @Description("Placed feature keys to deny explicitly, e.g. 'minecraft:ore_diamond'. A namespace:path prefix also matches, so 'minecraft:ore' denies every vanilla ore and 'minecraft:trees' denies every tree placement. Every key not matched here generates while 'enabled' is true.")
    private KList<String> disabled = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Description("Exact placed feature keys to deny after trimming and case normalization. Unlike 'disabled', entries never match key families, so 'minecraft:ore_iron' does not disable 'minecraft:ore_iron_small'.")
    private KList<String> disabledExact = new KList<>();

    @ArrayType(type = IrisImportedFeatureBiomeRule.class, min = 1)
    @Description("Per-biome denials applied on top of 'disabled' and 'disabledExact'. Each rule names registry biomes and the placed features it denies in those biomes only, so a mod tree injected into vanilla forests can be denied there while the mod's own biomes keep it.")
    private KList<IrisImportedFeatureBiomeRule> biomeRules = new KList<>();

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
        KList<String> deniedExactKeys = Objects.requireNonNull(
                disabledExact, "importedFeatures.disabledExact must not be null");
        String normalizedKey = placedFeatureKey.trim().toLowerCase(Locale.ROOT);
        for (String entry : deniedExactKeys) {
            if (entry != null && entry.trim().toLowerCase(Locale.ROOT).equals(normalizedKey)) {
                return NativeFeatureGenerationStatus.DISABLED_BY_PACK;
            }
        }
        return NativeFeatureGenerationStatus.GENERATE_NATIVE;
    }
    @Override
    public boolean shouldGenerateStepOrdinal(int step) {
        return shouldGenerateStep(IrisDecorationStep.byOrdinal(step));
    }

    @Override
    public boolean hasBiomeFeatureRules() {
        return biomeRules != null && !biomeRules.isEmpty();
    }

    @Override
    public boolean shouldGenerateInBiome(String biomeKey, Set<String> biomeTags, String featureKey) {
        for (IrisImportedFeatureBiomeRule rule : Objects.requireNonNull(
                biomeRules, "importedFeatures.biomeRules must not be null")) {
            if (rule != null && rule.appliesTo(biomeKey, biomeTags) && rule.denies(featureKey)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasFeatureFilter() {
        return (disabled != null && !disabled.isEmpty()) || (disabledExact != null && !disabledExact.isEmpty());
    }

}
