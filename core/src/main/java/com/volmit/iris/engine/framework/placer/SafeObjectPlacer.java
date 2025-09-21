package com.volmit.iris.engine.framework.placer;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IObjectPlacer;
import com.volmit.iris.engine.object.TileData;
import lombok.EqualsAndHashCode;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import static com.volmit.iris.Iris.platform;

@EqualsAndHashCode
public class SafeObjectPlacer implements IObjectPlacer {
    private final World world;
    private final IObjectPlacer placer;

    public SafeObjectPlacer(World world, IObjectPlacer placer) {
        this.world = world;
        this.placer = placer;
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            return platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> getHighest(x, z, data))
                    .getResult()
                    .join();
        }
        return placer.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            return platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> getHighest(x, z, data, ignoreFluid))
                    .getResult()
                    .join();
        }
        return placer.getHighest(x, z, data);
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> set(x, y, z, d)).getResult().join();
        } else placer.set(x, y, z, d);
    }

    @Override
    public BlockData get(int x, int y, int z) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            return platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> get(x, y, z)).getResult().join();
        }
        return placer.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return placer.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return placer.isCarved(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            return platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> isSolid(x, y, z)).getResult().join();
        }
        return placer.isSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        if (!platform.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            return platform.getRegionScheduler().run(world, x >> 4, z >> 4, () -> isUnderwater(x, z)).getResult().join();
        }
        return placer.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return placer.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return placer.isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        if (!platform.isOwnedByCurrentRegion(world, xx >> 4, zz >> 4)) {
            if (platform.isTickThread()) throw new IllegalStateException("Cannot run async on primary thread!");
            platform.getRegionScheduler().run(world, xx >> 4, zz >> 4, () -> setTile(xx, yy, zz, tile)).getResult().join();
        } else placer.setTile(xx, yy, zz, tile);
    }

    @Override
    public Engine getEngine() {
        return placer.getEngine();
    }
}
