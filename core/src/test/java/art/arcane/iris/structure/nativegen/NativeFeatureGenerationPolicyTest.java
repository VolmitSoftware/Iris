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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeFeatureGenerationPolicyTest {
    private static Engine engineWith(IrisDimension dimension) {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        return engine;
    }

    @Test
    public void dimensionDefaultsToFeaturesOff() {
        Engine engine = engineWith(new IrisDimension());

        assertFalse(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(engine,
                IrisDecorationStep.UNDERGROUND_ORES));
        assertEquals(NativeFeatureGenerationStatus.FEATURES_DISABLED,
                NativeFeatureGenerationPolicy.resolve(engine, "minecraft:ore_diamond",
                        IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void enabledDimensionResolvesThroughItsControl() {
        KList<String> disabled = new KList<>();
        disabled.add("minecraft:trees");
        IrisDimension dimension = new IrisDimension();
        dimension.setImportedFeatures(new IrisImportedFeatureControl()
                .setEnabled(true)
                .setDisabled(disabled));
        Engine engine = engineWith(dimension);

        assertTrue(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertEquals(NativeFeatureGenerationStatus.GENERATE_NATIVE,
                NativeFeatureGenerationPolicy.resolve(engine, "minecraft:ore_diamond",
                        IrisDecorationStep.UNDERGROUND_ORES));
        assertEquals(NativeFeatureGenerationStatus.DISABLED_BY_PACK,
                NativeFeatureGenerationPolicy.resolve(engine, "minecraft:trees_plains",
                        IrisDecorationStep.VEGETAL_DECORATION));
    }

    @Test
    public void missingEngineOrDimensionFailsLoudly() {
        assertTrue(assertThrows(NullPointerException.class,
                () -> NativeFeatureGenerationPolicy.control(null))
                .getMessage().contains("requires an engine"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> NativeFeatureGenerationPolicy.control(engineWith(null)))
                .getMessage().contains("requires a bound dimension"));
    }

    /**
     * The field initializer gives every dimension a control block, but an explicit
     * {@code "importedFeatures": null} in dimension JSON overwrites it - Gson assigns what the document says. This
     * policy is consulted per feature decision on the generation path, so a stray null must disable native features,
     * not fail the chunk.
     */
    @Test
    public void nullControlBlockDisablesNativeFeaturesInsteadOfFailingGeneration() {
        IrisDimension dimension = new IrisDimension();
        dimension.setImportedFeatures(null);
        Engine engine = engineWith(dimension);

        assertFalse(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, IrisDecorationStep.VEGETAL_DECORATION));
        assertEquals(NativeFeatureGenerationStatus.FEATURES_DISABLED,
                NativeFeatureGenerationPolicy.resolve(engine, "minecraft:ore_diamond",
                        IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void generationStatusMessagesAreSharedAcrossPlatforms() {
        assertEquals("Native feature minecraft:ore_diamond is disabled by this dimension's"
                        + " importedFeatures.disabled list.",
                NativeFeatureGenerationPolicy.generationStatusMessage("minecraft:ore_diamond",
                        NativeFeatureGenerationStatus.DISABLED_BY_PACK));
        assertEquals("Native feature minecraft:ore_diamond does not generate because this dimension's"
                        + " importedFeatures.enabled is false.",
                NativeFeatureGenerationPolicy.generationStatusMessage("minecraft:ore_diamond",
                        NativeFeatureGenerationStatus.FEATURES_DISABLED));
        assertEquals("Native feature minecraft:ore_diamond does not generate because its decoration step is"
                        + " excluded by importedFeatures.",
                NativeFeatureGenerationPolicy.generationStatusMessage("minecraft:ore_diamond",
                        NativeFeatureGenerationStatus.STEP_DISABLED));
    }

    /**
     * The generation-settings getter on both platforms maps a custom biome onto the owning Iris biome's
     * vanilla derivative key. This is that resolution rule, which is what decides whose features an Iris
     * custom biome inherits.
     */
    @Test
    public void customBiomeSettingsFollowTheVanillaDerivativeKey() {
        IrisBiome derivativeOnly = new IrisBiome();
        derivativeOnly.setDerivative("minecraft:plains");
        assertEquals("minecraft:plains", derivativeOnly.getVanillaDerivativeKey());

        IrisBiome overridden = new IrisBiome();
        overridden.setDerivative("minecraft:plains");
        overridden.setVanillaDerivative("minecraft:desert");
        assertEquals("minecraft:desert", overridden.getVanillaDerivativeKey());

        IrisBiome unnamespaced = new IrisBiome();
        unnamespaced.setDerivative("forest");
        assertEquals("minecraft:forest", unnamespaced.getVanillaDerivativeKey());

        IrisBiome modded = new IrisBiome();
        modded.setDerivative("somemod:alien_waste");
        assertEquals("somemod:alien_waste", modded.getVanillaDerivativeKey());
    }
}
