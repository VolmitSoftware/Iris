package com.volmit.iris.platform.bukkit.wrapper;

import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;
import org.bukkit.block.Biome;

@Data
public class BukkitBiome implements PlatformBiome {
    private final Biome delegate;

    private BukkitBiome(Biome delegate) {
        this.delegate = delegate;
    }

    @Override
    public PlatformNamespaceKey getKey() {
        return BukkitKey.of(delegate.getKey());
    }

    public static BukkitBiome of(Biome biome) {
        return new BukkitBiome(biome);
    }
}
