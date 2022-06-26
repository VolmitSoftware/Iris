package Iris.extensions.org.bukkit.block.data.BlockData;

import com.volmit.iris.platform.bukkit.wrapper.BukkitBlock;
import com.volmit.iris.platform.bukkit.wrapper.BukkitKey;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.block.data.BlockData;

@Extension
public class XBlockData {
    public static BukkitKey bukkitKey(@This BlockData self) {
        return self.getMaterial().getKey().bukkitKey();
    }

    public static BukkitBlock bukkitBlock(@This BlockData self) {
        return BukkitBlock.of(self);
    }
}