package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeRegistryDefinitions;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class NativeFabricBiomeDefinitionTest {
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    private static final String BIOME_KEY = "iris:biomes/fabric_modified";
    private static final String SOURCE_JSON = "{\"has_precipitation\":true,\"temperature\":0.8,\"downfall\":0.4,"
            + "\"effects\":{\"water_color\":\"#3f76e4\"},\"carvers\":[],\"features\":[]}";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void generatedBiomeDefinitionExcludesFabricBiomeModifications() throws Exception {
        MappedRegistry<PlacedFeature> features = new MappedRegistry<>(Registries.PLACED_FEATURE, Lifecycle.stable());
        Holder.Reference<PlacedFeature> ore = features.register(
                ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse("iris:modded_ore")),
                new PlacedFeature(Holder.direct(mock(Feature.class)), List.of()),
                RegistrationInfo.BUILT_IN);
        features.freeze();
        MappedRegistry<WorldCarver> carvers = new MappedRegistry<>(Registries.CARVER, Lifecycle.stable());
        carvers.freeze();
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, features, carvers));
        Biome source = Biome.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access),
                JsonParser.parseString(SOURCE_JSON)).getOrThrow();
        NativeFabricLoader loader = new NativeFabricLoader(new NativeLoaderOptions("irisworldgen", "treefeller",
                BooleanSupplier::getAsBoolean));
        loader.recordOriginalBiome(source);
        Holder.Reference<Biome> biome = biomes.register(
                ResourceKey.create(Registries.BIOME, Identifier.parse(BIOME_KEY)), source, RegistrationInfo.BUILT_IN);
        biomes.freeze();
        addFeatureInPlace(biome.value().getGenerationSettings(), ore);
        warmInPlace(biome.value());
        NativeRegistryDefinitions definitions = new NativeRegistryDefinitions(() -> access, loader::unmodifiedBiome);

        assertEquals(0.1F, biome.value().getBaseTemperature(), 0.0F);
        assertTrue(biome.value().getGenerationSettings().features()
                .get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()).contains(ore));
        assertEquals(JsonParser.parseString(definitions.canonicalDefinition(BIOME_REGISTRY, BIOME_KEY, SOURCE_JSON).value()),
                JsonParser.parseString(definitions.generatedDefinition(BIOME_REGISTRY, BIOME_KEY).value()));
    }

    /**
     * What a Fabric biome modification that adds an ore does: it replaces the feature list of the registered biome's
     * own generation settings.
     */
    private static void addFeatureInPlace(BiomeGenerationSettings settings, Holder<PlacedFeature> feature)
            throws ReflectiveOperationException {
        List<HolderSet<PlacedFeature>> steps = new ArrayList<>(settings.features());
        while (steps.size() <= GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()) {
            steps.add(HolderSet.direct());
        }
        steps.set(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(), HolderSet.direct(feature));
        Field features = BiomeGenerationSettings.class.getDeclaredField("features");
        features.setAccessible(true);
        features.set(settings, List.copyOf(steps));
    }

    /**
     * What a Fabric weather modification does: it replaces the registered biome's climate settings.
     */
    private static void warmInPlace(Biome biome) throws ReflectiveOperationException {
        Constructor<Biome.ClimateSettings> climate = Biome.ClimateSettings.class.getDeclaredConstructor(
                boolean.class, float.class, Biome.TemperatureModifier.class, float.class);
        climate.setAccessible(true);
        Field climateSettings = Biome.class.getDeclaredField("climateSettings");
        climateSettings.setAccessible(true);
        climateSettings.set(biome, climate.newInstance(true, 0.1F, Biome.TemperatureModifier.NONE, 0.4F));
    }
}
