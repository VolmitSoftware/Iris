package IrisBukkit.extensions.org.bukkit.NamespacedKey;

import com.volmit.iris.platform.bukkit.wrapper.BukkitKey;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.NamespacedKey;

@Extension
public class XNamespacedKey {
    public static BukkitKey bukkitKey(@This NamespacedKey self) {
        return BukkitKey.of(self);
    }
}