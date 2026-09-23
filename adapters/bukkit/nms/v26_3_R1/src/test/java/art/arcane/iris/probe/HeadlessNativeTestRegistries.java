package art.arcane.iris.probe;

import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.pack.datapack.IDataFixer;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

final class HeadlessNativeTestRegistries {
    private static RegistryAccess registries;

    private HeadlessNativeTestRegistries() {
    }

    static synchronized RegistryAccess get() {
        if (registries != null) {
            return registries;
        }
        HeadlessNativeBootstrap.initialize();
        HolderLookup.Provider vanilla = VanillaRegistries.createWorldLookup();
        String plains = biome(vanilla, "minecraft:plains");
        String swamp = biome(vanilla, "minecraft:swamp");
        Map<String, String> definitions = Map.of(
                "data/iris/worldgen/biome/native_test.json", generatedBiome(),
                "data/iris/dimension_type/native_test.json", generatedDimension(),
                "data/iris/worldgen/biome/biomes/test.json", plains,
                "data/iris/worldgen/biome/headless/test.json", plains,
                "data/iris/worldgen/biome/coherent_swamp.json", swamp,
                "data/minecraft/tags/worldgen/biome/has_structure/swamp_hut.json",
                "{\"replace\":false,\"values\":[\"iris:coherent_swamp\"]}");
        try (HeadlessNativeRegistries loaded = HeadlessNativeRegistries.load(definitions)) {
            registries = loaded.registries();
            return registries;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static String generatedBiome() {
        return new IrisBiomeCustom().setId("native_test").generateJson(new DataFixerV263());
    }

    static String generatedDimension() {
        return new IrisDimensionType(IDataFixer.Dimension.OVERWORLD,
                new IrisDimensionTypeOptions(), 768, 768, -256).toJson(new DataFixerV263());
    }

    private static String biome(HolderLookup.Provider vanilla, String key) {
        Biome biome = vanilla.lookupOrThrow(Registries.BIOME).getOrThrow(
                ResourceKey.create(Registries.BIOME, Identifier.parse(key))).value();
        return Biome.DIRECT_CODEC.encodeStart(vanilla.createSerializationContext(JsonOps.INSTANCE), biome)
                .getOrThrow().toString();
    }
}
