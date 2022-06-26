package Iris.extensions.org.bukkit.Chunk;

import com.volmit.iris.platform.bukkit.wrapper.BukkitChunk;
import manifold.ext.rt.api.Extension;
import manifold.ext.rt.api.This;
import org.bukkit.Chunk;

@Extension
public class XChunk {
    public static BukkitChunk bukkitChunk(@This Chunk self) {
        return BukkitChunk.of(self);
    }
}