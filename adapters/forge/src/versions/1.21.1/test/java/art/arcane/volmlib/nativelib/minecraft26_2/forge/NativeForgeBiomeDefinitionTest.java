package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeRegistryDefinitions;
import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class NativeForgeBiomeDefinitionTest {
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    private static final String BIOME_KEY = "iris:biomes/forge_modified";
    private static final String SOURCE_JSON = "{\"has_precipitation\":true,\"temperature\":0.8,\"downfall\":0.4,"
            + "\"effects\":{\"fog_color\":12638463,\"water_color\":4159204,\"water_fog_color\":329011,\"sky_color\":7907327},"
            + "\"carvers\":{},\"features\":[],\"spawners\":{},\"spawn_costs\":{}}";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void generatedBiomeDefinitionExcludesForgeBiomeModifierOutput() {
        MappedRegistry<PlacedFeature> features = new MappedRegistry<>(Registries.PLACED_FEATURE, Lifecycle.stable());
        Holder.Reference<PlacedFeature> ore = features.register(
                ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.parse("iris:modded_ore")),
                new PlacedFeature(Holder.direct(mock(ConfiguredFeature.class)), List.of()),
                RegistrationInfo.BUILT_IN);
        features.freeze();
        MappedRegistry<ConfiguredWorldCarver<?>> carvers = new MappedRegistry<>(Registries.CONFIGURED_CARVER, Lifecycle.stable());
        carvers.freeze();
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes, features, carvers));
        Biome source = Biome.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access),
                JsonParser.parseString(SOURCE_JSON)).getOrThrow();
        Holder.Reference<Biome> biome = biomes.register(
                ResourceKey.create(Registries.BIOME, ResourceLocation.parse(BIOME_KEY)), source, RegistrationInfo.BUILT_IN);
        biomes.freeze();
        biome.value().modifiableBiomeInfo().applyBiomeModifiers(biome, List.of(new ModdedContent(ore)));
        NativeRegistryDefinitions definitions = new NativeRegistryDefinitions(() -> access,
                new NativeForgeLoader(new NativeLoaderOptions("irisworldgen", "treefeller",
                        BooleanSupplier::getAsBoolean))::unmodifiedBiome);

        assertEquals(0.1F, biome.value().getBaseTemperature(), 0.0F);
        assertEquals(0x102030, biome.value().getWaterColor() & 0xFFFFFF);
        assertTrue(biome.value().getGenerationSettings().hasFeature(ore.value()));
        assertTrue(biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .anyMatch(spawn -> spawn.type == EntityType.ZOMBIE));
        assertEquals(JsonParser.parseString(definitions.canonicalDefinition(BIOME_REGISTRY, BIOME_KEY, SOURCE_JSON).value()),
                JsonParser.parseString(definitions.generatedDefinition(BIOME_REGISTRY, BIOME_KEY).value()));
    }

    private record ModdedContent(Holder<PlacedFeature> ore) implements BiomeModifier {
        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.ADD) {
                return;
            }
            builder.getClimateSettings().setTemperature(0.1F);
            builder.getSpecialEffects().waterColor(0x102030);
            builder.getMobSpawnSettings().addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 5, 1, 2));
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ore);
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            throw new UnsupportedOperationException();
        }
    }
}
