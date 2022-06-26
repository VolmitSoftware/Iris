package Iris.extensions.org.bukkit.entity.Player;

import com.volmit.iris.platform.bukkit.wrapper.BukkitPlayer;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.entity.Player;

@Extension
public class XPlayer {
    public static BukkitPlayer bukkitPlayer(@This Player self) {
        return BukkitPlayer.of(self);
    }
}