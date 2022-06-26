package com.volmit.iris.platform.bukkit.transformers;

import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.engine.object.biome.NativeBiome;
import com.volmit.iris.platform.PlatformDataTransformer;
import com.volmit.iris.platform.PlatformTransformer;
import com.volmit.iris.platform.bukkit.IrisBukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;

import java.util.Arrays;
import java.util.stream.Stream;

public class BukkitBiomeTransformer implements PlatformDataTransformer<Biome, NativeBiome> {
    @Override
    public Stream<Biome> getRegistry() {
        return Arrays.stream(Biome.values()).parallel().filter((i) -> i != Biome.CUSTOM);
    }

    @Override
    public NSKey getKey(Biome nativeType) {
        return IrisBukkit.getInstance().getNamespaceTransformer().toIris(nativeType.getKey());
    }

    @Override
    public String getTypeName() {
        return "Block";
    }

    @Override
    public NativeBiome toIris(Biome biome) {
        PlatformTransformer<NamespacedKey, NSKey> transformer = IrisBukkit.getInstance().getNamespaceTransformer();
        return new NativeBiome(transformer.toIris(biome.getKey()));
    }

    @Override
    public Biome toNative(NativeBiome nativeBiome) {
        PlatformTransformer<NamespacedKey, NSKey> transformer = IrisBukkit.getInstance().getNamespaceTransformer();
        return Biome.values().stream().filter((i) -> transformer.toIris(i.getKey()).equals(nativeBiome.getKey())).findFirst().get();
    }
}
