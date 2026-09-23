package art.arcane.iris.probe;

import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.function.Supplier;

record HeadlessNativeBiomeWriter(Supplier<HolderLookup.Provider> registries) implements PlatformBiomeWriter {
    @Override
    public int biomeIdFor(String key) {
        Registry<Biome> registry = registry();
        Biome biome = registry.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(key))).value();
        return registry.getId(biome);
    }

    @Override
    public List<NativeBiome> allBiomes() {
        return registry().listElements().map(holder -> (NativeBiome) ModdedBiome.of(
                holder.value(), holder.key().identifier().toString())).toList();
    }

    private Registry<Biome> registry() {
        return (Registry<Biome>) registries.get().lookupOrThrow(Registries.BIOME);
    }
}
