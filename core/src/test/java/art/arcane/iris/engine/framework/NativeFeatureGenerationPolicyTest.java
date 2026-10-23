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

import art.arcane.iris.builtin.vanillafeatures.ImportedFeaturesSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.host.LoadedPack;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaFeatures;
import com.volmit.vanilla.DecorationStep;
import com.volmit.world.PackWorld;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The platform's placed features as the pack's modules decide them through {@link VanillaFeatures}, kept per world. */
public class NativeFeatureGenerationPolicyTest {
    private static final int UNDERGROUND_ORES = DecorationStep.UNDERGROUND_ORES.ordinal();
    private static final int VEGETAL_DECORATION = DecorationStep.VEGETAL_DECORATION.ordinal();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<LoadedPack> packs = new ArrayList<>();

    @After
    public void closePacks() {
        packs.forEach(LoadedPack::close);
    }

    @Test
    public void aDimensionWithoutImportedFeaturesRunsNone() throws IOException {
        Engine engine = engine(ModuleSettings.NONE, List.of());

        assertFalse(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, UNDERGROUND_ORES));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:ore_diamond"));
    }

    @Test
    public void theDimensionsImportedFeaturesDecideThroughTheBuiltInModule() throws IOException {
        Engine engine = engine(BuiltInPack.settings(TestSettings.with(new ImportedFeaturesSettings(),
                "enabled", true, "disabled", List.of("minecraft:trees"), "disabledSteps", List.of(DecorationStep.LAKES))), List.of());

        assertTrue(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertTrue(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, UNDERGROUND_ORES));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, DecorationStep.LAKES.ordinal()));
        assertTrue(NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:ore_diamond"));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:trees_plains"));
        assertFalse("a blank key is no feature", NativeFeatureGenerationPolicy.shouldGenerate(engine, " "));
    }

    @Test
    public void aStepPastTheOnesTheApiNamesGeneratesUnlessTheStepsWereNarrowed() throws IOException {
        Engine open = engine(BuiltInPack.settings(TestSettings.with(new ImportedFeaturesSettings(), "enabled", true)), List.of());
        Engine narrowed = engine(BuiltInPack.settings(TestSettings.with(new ImportedFeaturesSettings(),
                "enabled", true, "steps", List.of(DecorationStep.UNDERGROUND_ORES))), List.of());
        int newerStep = DecorationStep.values().length;

        assertTrue(NativeFeatureGenerationPolicy.shouldGenerateStep(open, newerStep));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(narrowed, newerStep));
    }

    @Test
    public void aPackModuleMayTurnFeaturesOnAndAnyModuleMayRefuseAStepOrAFeature() throws IOException {
        VanillaFeatures packModule = new VanillaFeatures() {
            @Override
            public boolean generates(PackWorld world) {
                return true;
            }

            @Override
            public boolean generatesStep(PackWorld world, Optional<DecorationStep> step) {
                return !step.equals(Optional.of(DecorationStep.VEGETAL_DECORATION));
            }

            @Override
            public boolean generatesFeature(PackWorld world, ResourceId feature) {
                return !feature.path().startsWith("ore_");
            }
        };
        Engine engine = engine(BuiltInPack.settings(TestSettings.with(new ImportedFeaturesSettings(),
                "disabled", List.of("minecraft:spring"))), List.of(new ProviderModule(packModule)));

        assertTrue(NativeFeatureGenerationPolicy.isEnabled(engine));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, VEGETAL_DECORATION));
        assertTrue(NativeFeatureGenerationPolicy.shouldGenerateStep(engine, UNDERGROUND_ORES));
        assertFalse(NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:ore_diamond"));
        assertFalse("the dimension's own list still applies", NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:spring_water"));
        assertTrue(NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:trees_plains"));
    }

    @Test
    public void eachStepAndFeatureIsAskedOncePerWorld() throws IOException {
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger features = new AtomicInteger();
        VanillaFeatures counting = new VanillaFeatures() {
            @Override
            public boolean generates(PackWorld world) {
                return true;
            }

            @Override
            public boolean generatesStep(PackWorld world, Optional<DecorationStep> step) {
                steps.incrementAndGet();
                return true;
            }

            @Override
            public boolean generatesFeature(PackWorld world, ResourceId feature) {
                features.incrementAndGet();
                return true;
            }
        };
        Engine engine = engine(ModuleSettings.NONE, List.of(new ProviderModule(counting)));

        for (int chunk = 0; chunk < 3; chunk++) {
            NativeFeatureGenerationPolicy.shouldGenerateStep(engine, UNDERGROUND_ORES);
            NativeFeatureGenerationPolicy.shouldGenerate(engine, "minecraft:ore_diamond");
        }

        assertEquals(1, steps.get());
        assertEquals(1, features.get());
    }

    /** The platforms convert their decoration steps by index, so a drift here silently mislabels every step gate. */
    @Test
    public void decorationStepsAreInTheOrderOfTheVanillaTable() {
        String[] vanilla = {
                "raw_generation", "lakes", "local_modifications", "underground_structures",
                "surface_structures", "strongholds", "underground_ores", "underground_decoration",
                "fluid_springs", "vegetal_decoration", "top_layer_modification"
        };
        assertEquals(vanilla.length, DecorationStep.values().length);
        for (int index = 0; index < vanilla.length; index++) {
            assertEquals(vanilla[index], DecorationStep.values()[index].name().toLowerCase(Locale.ROOT));
        }
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

    /** An engine whose dimension declares {@code dimensionSettings}, its pack loading the built-ins and {@code packModules}. */
    private Engine engine(ModuleSettings dimensionSettings, List<IrisModule> packModules) throws IOException {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        packs.add(BuiltInPack.serveWithResources(data, folder, packModules));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(dimension.getModuleSettings()).thenReturn(dimensionSettings);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }

    /** A pack module whose only part is a vanilla features provider. */
    private record ProviderModule(VanillaFeatures provider) implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("test:features", "Features", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.registry().registerService(VanillaFeatures.class, provider);
        }
    }
}
