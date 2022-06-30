package IrisBukkit.extensions.org.bukkit.World;

import com.volmit.iris.platform.bukkit.wrapper.BukkitWorld;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.World;

@Extension
public class XWorld {
    public static BukkitWorld bukkitWorld(@This World self) {
        return BukkitWorld.of(self);
    }
}