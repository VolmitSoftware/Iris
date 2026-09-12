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

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisImportedFeatureControlTest {
    private static KList<String> keys(String... values) {
        KList<String> list = new KList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static KList<IrisDecorationStep> steps(IrisDecorationStep... values) {
        KList<IrisDecorationStep> list = new KList<>();
        for (IrisDecorationStep value : values) {
            list.add(value);
        }
        return list;
    }

    @Test
    public void defaultControlIsOffAndGeneratesNothing() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl();

        assertFalse(control.isEnabled());
        assertFalse(control.shouldGenerateFeatures());
        assertFalse(control.shouldGenerate("minecraft:ore_diamond"));
        assertFalse(control.shouldGenerateStep(IrisDecorationStep.UNDERGROUND_ORES));
        assertEquals(NativeFeatureGenerationStatus.FEATURES_DISABLED,
                control.resolve("minecraft:ore_diamond", IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void enabledControlGeneratesEveryStepAndKeyByDefault() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl().setEnabled(true);

        assertTrue(control.shouldGenerateFeatures());
        assertTrue(control.shouldGenerate("minecraft:ore_diamond"));
        assertTrue(control.shouldGenerate("somemod:weird_ore"));
        for (IrisDecorationStep step : IrisDecorationStep.values()) {
            assertTrue(step.name(), control.shouldGenerateStep(step));
        }
        assertEquals(NativeFeatureGenerationStatus.GENERATE_NATIVE,
                control.resolve("minecraft:ore_diamond", IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void disabledKeyPrefixMatchesOnFamilyBoundariesOnly() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl()
                .setEnabled(true)
                .setDisabled(keys("minecraft:ore"));

        assertFalse(control.shouldGenerate("minecraft:ore_diamond"));
        assertFalse(control.shouldGenerate("minecraft:ore"));
        assertFalse(control.shouldGenerate("minecraft:ore/deep"));
        assertTrue(control.shouldGenerate("minecraft:orebody"));
        assertTrue(control.shouldGenerate("somemod:ore_diamond"));
        assertEquals(NativeFeatureGenerationStatus.DISABLED_BY_PACK,
                control.resolve("minecraft:ore_diamond", IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void stepAllowListNarrowsGenerationToListedStepsOnly() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl()
                .setEnabled(true)
                .setSteps(steps(IrisDecorationStep.UNDERGROUND_ORES));

        assertTrue(control.shouldGenerateStep(IrisDecorationStep.UNDERGROUND_ORES));
        assertFalse(control.shouldGenerateStep(IrisDecorationStep.VEGETAL_DECORATION));
        assertFalse(control.shouldGenerateStep(IrisDecorationStep.LAKES));
        assertEquals(NativeFeatureGenerationStatus.STEP_DISABLED,
                control.resolve("minecraft:trees_plains", IrisDecorationStep.VEGETAL_DECORATION));
        assertEquals(NativeFeatureGenerationStatus.GENERATE_NATIVE,
                control.resolve("minecraft:ore_diamond", IrisDecorationStep.UNDERGROUND_ORES));
    }

    @Test
    public void disabledStepsWinOverTheAllowList() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl()
                .setEnabled(true)
                .setSteps(steps(IrisDecorationStep.UNDERGROUND_ORES, IrisDecorationStep.VEGETAL_DECORATION))
                .setDisabledSteps(steps(IrisDecorationStep.VEGETAL_DECORATION));

        assertTrue(control.shouldGenerateStep(IrisDecorationStep.UNDERGROUND_ORES));
        assertFalse(control.shouldGenerateStep(IrisDecorationStep.VEGETAL_DECORATION));
    }

    @Test
    public void unknownStepGeneratesUnlessAnAllowListNarrowedGeneration() {
        IrisImportedFeatureControl open = new IrisImportedFeatureControl().setEnabled(true);
        IrisImportedFeatureControl narrowed = new IrisImportedFeatureControl()
                .setEnabled(true)
                .setSteps(steps(IrisDecorationStep.UNDERGROUND_ORES));

        assertTrue(open.shouldGenerateStep(null));
        assertFalse(narrowed.shouldGenerateStep(null));
    }

    @Test
    public void nullStepInResolveSkipsTheStepGate() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl()
                .setEnabled(true)
                .setSteps(steps(IrisDecorationStep.UNDERGROUND_ORES));

        assertEquals(NativeFeatureGenerationStatus.GENERATE_NATIVE,
                control.resolve("minecraft:trees_plains", null));
    }

    @Test
    public void blankKeyIsInvalidRatherThanGenerated() {
        IrisImportedFeatureControl control = new IrisImportedFeatureControl().setEnabled(true);

        assertEquals(NativeFeatureGenerationStatus.INVALID_REGISTRY_KEY, control.resolve(null, null));
        assertEquals(NativeFeatureGenerationStatus.INVALID_REGISTRY_KEY, control.resolve("   ", null));
        assertFalse(control.shouldGenerate(null));
    }

    @Test
    public void nullCollectionsFailLoudlyNamingTheField() {
        IrisImportedFeatureControl nullDisabled = new IrisImportedFeatureControl()
                .setEnabled(true).setDisabled(null);
        IrisImportedFeatureControl nullSteps = new IrisImportedFeatureControl()
                .setEnabled(true).setSteps(null);
        IrisImportedFeatureControl nullDisabledSteps = new IrisImportedFeatureControl()
                .setEnabled(true).setDisabledSteps(null);

        assertTrue(assertThrows(NullPointerException.class,
                () -> nullDisabled.shouldGenerate("minecraft:ore_diamond"))
                .getMessage().contains("importedFeatures.disabled"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> nullSteps.shouldGenerateStep(IrisDecorationStep.LAKES))
                .getMessage().contains("importedFeatures.steps"));
        assertTrue(assertThrows(NullPointerException.class,
                () -> nullDisabledSteps.shouldGenerateStep(IrisDecorationStep.LAKES))
                .getMessage().contains("importedFeatures.disabledSteps"));
    }

    @Test
    public void decorationStepOrdinalsMatchTheVanillaTable() {
        // Verified against MC 26.2 GenerationStep.Decoration. The platform converts by ordinal, so a drift
        // here silently mislabels every step gate.
        String[] expected = {
                "raw_generation", "lakes", "local_modifications", "underground_structures",
                "surface_structures", "strongholds", "underground_ores", "underground_decoration",
                "fluid_springs", "vegetal_decoration", "top_layer_modification"
        };

        assertEquals(expected.length, IrisDecorationStep.values().length);
        for (int ordinal = 0; ordinal < expected.length; ordinal++) {
            IrisDecorationStep step = IrisDecorationStep.byOrdinal(ordinal);
            assertEquals(expected[ordinal], step.getSerializedName());
            assertEquals(ordinal, step.ordinal());
            assertEquals(step, IrisDecorationStep.byKey(expected[ordinal]));
            assertEquals(step, IrisDecorationStep.byKey(step.name()));
        }
        assertEquals(null, IrisDecorationStep.byOrdinal(expected.length));
        assertEquals(null, IrisDecorationStep.byOrdinal(-1));
        assertEquals(null, IrisDecorationStep.byKey("not_a_step"));
        assertEquals(null, IrisDecorationStep.byKey(null));
    }
}
