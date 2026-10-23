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

package art.arcane.iris.engine.framework.vanillafeatures;

import art.arcane.iris.builtin.vanillafeatures.ImportedFeaturesSettings;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.DecorationStep;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What a dimension's {@code importedFeatures} decide about the platform's own placed features. */
public class ImportedFeaturesTest {
    private static final ResourceId DIAMONDS = ResourceId.parse("minecraft:ore_diamond");
    private static final ResourceId PLAINS_TREES = ResourceId.parse("minecraft:trees_plains");
    private static final ImportedFeatures FEATURES = new ImportedFeatures();

    @Test
    public void aDimensionWithoutTheSettingsRunsNoFeatures() {
        assertFalse(FEATURES.generates(worldWith(null)));
        assertFalse(FEATURES.generates(worldWith(new ImportedFeaturesSettings())));
    }

    @Test
    public void enabledSettingsWithoutFiltersAllowEveryStepAndFeature() {
        PackWorld world = worldWith(enabled(List.of(), List.of(), List.of()));

        assertTrue(FEATURES.generates(world));
        for (DecorationStep step : DecorationStep.values()) {
            assertTrue(step.name(), FEATURES.generatesStep(world, Optional.of(step)));
        }
        assertTrue(FEATURES.generatesFeature(world, DIAMONDS));
    }

    @Test
    public void disabledPatternsMatchWholeFamiliesAtABoundaryOnly() {
        PackWorld world = worldWith(enabled(List.of("minecraft:ore", "minecraft:trees"), List.of(), List.of()));

        assertFalse(FEATURES.generatesFeature(world, DIAMONDS));
        assertFalse(FEATURES.generatesFeature(world, PLAINS_TREES));
        assertTrue(FEATURES.generatesFeature(world, ResourceId.parse("minecraft:orefoo")));
        assertTrue(FEATURES.generatesFeature(world, ResourceId.parse("minecraft:spring_water")));
    }

    @Test
    public void stepsNarrowGenerationAndDisabledStepsApplyAfterThem() {
        PackWorld narrowed = worldWith(enabled(List.of(), List.of(DecorationStep.UNDERGROUND_ORES), List.of()));
        PackWorld overruled = worldWith(enabled(List.of(),
                List.of(DecorationStep.UNDERGROUND_ORES, DecorationStep.VEGETAL_DECORATION), List.of(DecorationStep.VEGETAL_DECORATION)));

        assertTrue(FEATURES.generatesStep(narrowed, Optional.of(DecorationStep.UNDERGROUND_ORES)));
        assertFalse(FEATURES.generatesStep(narrowed, Optional.of(DecorationStep.VEGETAL_DECORATION)));
        assertFalse(FEATURES.generatesStep(narrowed, Optional.of(DecorationStep.LAKES)));
        assertTrue(FEATURES.generatesStep(overruled, Optional.of(DecorationStep.UNDERGROUND_ORES)));
        assertFalse(FEATURES.generatesStep(overruled, Optional.of(DecorationStep.VEGETAL_DECORATION)));
    }

    @Test
    public void aStepTheApiDoesNotNameGeneratesUnlessTheStepsWereNarrowed() {
        PackWorld open = worldWith(enabled(List.of(), List.of(), List.of()));
        PackWorld narrowed = worldWith(enabled(List.of(), List.of(DecorationStep.UNDERGROUND_ORES), List.of()));

        assertTrue(FEATURES.generatesStep(open, Optional.empty()));
        assertFalse(FEATURES.generatesStep(narrowed, Optional.empty()));
    }

    @Test
    public void listsWrittenAsNullReadAsEmpty() {
        PackWorld world = worldWith(enabled(null, null, null));

        assertTrue(FEATURES.generatesStep(world, Optional.of(DecorationStep.LAKES)));
        assertTrue(FEATURES.generatesFeature(world, DIAMONDS));
    }

    private static ImportedFeaturesSettings enabled(List<String> disabled, List<DecorationStep> steps,
                                                    List<DecorationStep> disabledSteps) {
        return TestSettings.with(new ImportedFeaturesSettings(),
                "enabled", true, "disabled", disabled, "steps", steps, "disabledSteps", disabledSteps);
    }

    /** A world whose dimension declares {@code settings}, or nothing when null. */
    private static PackWorld worldWith(ImportedFeaturesSettings settings) {
        PackWorld world = mock(PackWorld.class);
        when(world.settings()).thenReturn(new PackSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                if (settings == null || featureType != ImportedFeaturesSettings.class) {
                    return SettingsStack.empty();
                }
                return SettingsStack.of(List.of((Entry<F>) new Entry<>(Level.DIMENSION, ResourceId.parse("test:overworld"), settings)));
            }
        });
        return world;
    }
}
