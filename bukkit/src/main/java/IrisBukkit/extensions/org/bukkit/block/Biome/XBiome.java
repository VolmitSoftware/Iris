package IrisBukkit.extensions.org.bukkit.block.Biome;

import com.volmit.iris.platform.bukkit.wrapper.BukkitBiome;
import com.volmit.iris.platform.bukkit.wrapper.BukkitKey;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.block.Biome;

@Extension
public class XBiome {
    public static BukkitKey bukkitKey(@This Biome self) {
        return self.getKey().bukkitKey();
    }

    public static BukkitBiome bukkitBiome(@This Biome self) {
        return BukkitBiome.of(self);
    }
}