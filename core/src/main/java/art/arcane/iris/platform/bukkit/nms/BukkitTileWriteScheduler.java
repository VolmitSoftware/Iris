package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.world.task.J;
import art.arcane.volmlib.nativelib.terrain.TileWriteScheduler;
import org.bukkit.Location;

public enum BukkitTileWriteScheduler implements TileWriteScheduler {
    INSTANCE;

    @Override
    public boolean owns(Location location) {
        return J.isOwnedByCurrentRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public boolean schedule(Location location, Runnable update) {
        return J.runAt(location, update);
    }
}
