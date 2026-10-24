/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.engine.object.placement.FloatingObjectFootprint;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.terrain.carving.CarveResult;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleObjectPlacement;
import art.arcane.iris.core.modules.ObjectGeometry;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.data.VectorMap;
import art.arcane.iris.util.common.math.AxisAlignedBB;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.IrisVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.object.GeneratedObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
 * A voxel volume loaded from a .iob file. Instances are loader cached and shared across generation threads, so all
 * volume access goes through the read/write lock pair below.
 * <p>
 * The heavy behaviour lives in package-private collaborators that read this state directly:
 * {@link IrisObjectIO} (binary persistence), {@link IrisObjectShaping} (boring, shrinkwrap, block classification),
 * {@link IrisObjectTransforms} (rotation, scaling, interpolated upscaling) and {@link ModuleObjectPlacement}
 * (world placement).
 */
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegistrant implements PlaceableObject {
    protected static final IrisVector HALF = new IrisVector(0.5, 0.5, 0.5);
    public static final class States {
        public static final PlatformBlockState AIR = B.getState("CAVE_AIR");
        public static final PlatformBlockState STONE = B.getState("STONE");
        public static final PlatformBlockState VAIR = B.getState("VOID_AIR");
        public static final PlatformBlockState VAIR_DEBUG = B.getState("COBWEB");
        public static final PlatformBlockState[] SNOW_LAYERS = new PlatformBlockState[]{B.getState("minecraft:snow[layers=1]"), B.getState("minecraft:snow[layers=2]"), B.getState("minecraft:snow[layers=3]"), B.getState("minecraft:snow[layers=4]"), B.getState("minecraft:snow[layers=5]"), B.getState("minecraft:snow[layers=6]"), B.getState("minecraft:snow[layers=7]"), B.getState("minecraft:snow[layers=8]")};
    }
    public transient final Lock readLock;
    protected transient final Lock writeLock;
    @Getter
    @Setter
    protected transient volatile boolean smartBored = false;
    @Setter
    protected transient AtomicCache<AxisAlignedBB> aabb = new AtomicCache<>();
    transient final AtomicCache<KList<IrisBlockVector>> surfaceSupportOffsets = new AtomicCache<>();
    public transient final AtomicCache<FloatingObjectFootprint> floatingFootprint = new AtomicCache<>();
    private transient final AtomicCache<GeneratedObject> geometry = new AtomicCache<>();
    @Getter
    public VectorMap<PlatformBlockState> blocks;
    @Getter
    public VectorMap<TileData> states;
    @Getter
    @Setter
    public int w;
    @Getter
    @Setter
    public int d;
    @Getter
    @Setter
    public int h;
    @Getter
    @Setter
    transient Vector3i center;
    @Getter
    transient Vector3i shrinkOffset;

    public IrisObject(int w, int h, int d) {
        blocks = new VectorMap<>();
        states = new VectorMap<>();
        this.w = w;
        this.h = h;
        this.d = d;
        center = new Vector3i(w / 2, h / 2, d / 2);
        shrinkOffset = new Vector3i(0, 0, 0);
        var lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    public IrisObject() {
        this(0, 0, 0);
    }

    public static AxisAlignedBB getAABBFor(IrisBlockVector size) {
        IrisBlockVector center = new IrisBlockVector(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
        IrisVector min = new IrisBlockVector(0, 0, 0).subtract(center);
        IrisVector max = new IrisBlockVector(size.getX() - 1, size.getY() - 1, size.getZ() - 1).subtract(center);
        return new AxisAlignedBB(new IrisPosition(min.getBlockX(), min.getBlockY(), min.getBlockZ()),
                new IrisPosition(max.getBlockX(), max.getBlockY(), max.getBlockZ()));
    }

    public static IrisBlockVector sampleSize(File file) throws IOException {
        return IrisObjectIO.sampleSize(file);
    }

    public AxisAlignedBB getAABB() {
        return aabb.aquire(() -> getAABBFor(new IrisBlockVector(w, h, d)));
    }

    public synchronized IrisObject copy() {
        IrisObject o = new IrisObject(w, h, d);
        o.setLoadKey(getLoadKey());
        o.setLoader(getLoader());
        o.setLoadFile(getLoadFile());
        o.setCenter(getCenter().clone());

        blocks.forEach((i, v) -> o.blocks.put(i.clone(), v));
        states.forEach((i, v) -> o.states.put(i.clone(), v.clone()));

        return o;
    }

    public void readLegacy(InputStream in) throws IOException {
        IrisObjectIO.readLegacy(this, in);
    }

    public void read(InputStream in) throws Throwable {
        IrisObjectIO.read(this, in);
    }

    public void read(File file) throws IOException {
        IrisObjectIO.read(this, file);
    }

    public void write(OutputStream o) throws IOException {
        IrisObjectIO.write(this, o);
    }

    public void write(OutputStream o, VolmitSender sender) throws IOException {
        IrisObjectIO.write(this, o, sender);
    }

    public void write(File file) throws IOException {
        IrisObjectIO.write(this, file);
    }

    public void write(File file, VolmitSender sender) throws IOException {
        IrisObjectIO.write(this, file, sender);
    }

    public void shrinkwrap() {
        // Instances are loader cached and shared across generation threads; mutating the
        // volume without the write lock let a concurrent placement mix pre-shrink anchors
        // with post-shrink blocks. Rotation paths call the package-private statics while
        // already holding this (reentrant) lock.
        writeLock.lock();
        try {
            IrisObjectShaping.shrinkwrap(this);
        } finally {
            writeLock.unlock();
        }
    }

    public void clean() {
        writeLock.lock();
        try {
            IrisObjectShaping.clean(this);
        } finally {
            writeLock.unlock();
        }
    }

    public IrisBlockVector getSigned(int x, int y, int z) {
        if (x >= w || y >= h || z >= d) {
            throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
        }

        return new IrisBlockVector(x - center.getX(), y - center.getY(), z - center.getZ());
    }

    public void setUnsigned(int x, int y, int z, PlatformBlockState block) {
        IrisBlockVector v = getSigned(x, y, z);

        if (block == null) {
            blocks.remove(v);
            states.remove(v);
        } else {
            blocks.put(v, block);
        }

        forgetDerivedShapes();
    }

    public void setUnsignedTile(int x, int y, int z, TileData tile) {
        IrisBlockVector v = getSigned(x, y, z);

        if (tile == null) {
            states.remove(v);
        } else {
            states.put(v, tile);
        }

        forgetDerivedShapes();
    }

    public void setUnsigned(int x, int y, int z, Block block, boolean legacy) {
        IrisBlockVector v = getSigned(x, y, z);

        if (block == null) {
            blocks.remove(v);
            states.remove(v);
        } else {
            BlockData data = block.getBlockData();
            blocks.put(v, BukkitBlockState.of(data));
            TileData state = TileData.getTileState(block, legacy);
            if (state != null) {
                IrisLogging.debug("Saved State " + v);
                states.put(v, state);
            }
        }

        forgetDerivedShapes();
    }

    public int place(int x, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, IrisData rdata) {
        return place(x, -1, z, placer, config, rng, rdata);
    }

    public int place(int x, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, CarveResult c, IrisData rdata) {
        return place(x, -1, z, placer, config, rng, null, c, rdata);
    }

    public int place(int x, int yv, int z, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, IrisData rdata) {
        return place(x, yv, z, placer, config, rng, null, null, rdata);
    }

    public int place(Location loc, IObjectPlacer placer, IrisObjectPlacement config, RNG rng, IrisData rdata) {
        return place(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), placer, config, rng, rdata);
    }

    /** The height the object was placed at, or -1 when the placement rejected it. */
    public int place(int x, int yv, int z, IObjectPlacer oplacer, IrisObjectPlacement config, RNG rng, BiConsumer<BlockPosition, PlatformBlockState> listener, CarveResult c, IrisData rdata) {
        return ModuleObjectPlacement.place(this, x, yv, z, oplacer, config, rng, listener, c, rdata).heightOrRejected();
    }

    /** This object as IrisApi geometry, read once until its blocks change. */
    public GeneratedObject getGeometry() {
        return geometry.aquireOrThrow(() -> ObjectGeometry.of(this));
    }

    /** Drops what is computed from the blocks, after they change. */
    void forgetDerivedShapes() {
        surfaceSupportOffsets.reset();
        floatingFootprint.reset();
        geometry.reset();
    }

    public KList<IrisBlockVector> getSurfaceSupportOffsets() {
        return surfaceSupportOffsets.aquire(() -> {
            readLock.lock();
            try {
                int lowestY = Integer.MAX_VALUE;
                KList<IrisBlockVector> offsets = new KList<>();
                for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : blocks) {
                    PlatformBlockState state = entry.getValue();
                    if (state == null || !state.isSolid() || state.isFoliage()) {
                        continue;
                    }
                    IrisBlockVector position = entry.getKey();
                    int blockY = position.getBlockY();
                    if (blockY < lowestY) {
                        lowestY = blockY;
                        offsets.clear();
                    }
                    if (blockY == lowestY) {
                        offsets.add(position.clone());
                    }
                }
                return offsets;
            } finally {
                readLock.unlock();
            }
        });
    }

    public IrisObject rotateCopy(IrisObjectRotation rt) {
        return IrisObjectTransforms.rotateCopy(this, rt);
    }

    public IrisObject scaled(double scale, IrisObjectPlacementScaleInterpolator interpolation) {
        return IrisObjectTransforms.scaled(this, scale, interpolation);
    }

    public void place(Location at) {
        readLock.lock();
        try {
            for (var entry : blocks) {
                var i = entry.getKey();
                Block b = at.clone().add(0, getCenter().getY(), 0).add(i.getX(), i.getY(), i.getZ()).getBlock();
                b.setBlockData((BlockData) Objects.requireNonNull(entry.getValue()).nativeHandle(), false);

                if (states.containsKey(i)) {
                    IrisLogging.info(Objects.requireNonNull(states.get(i)).toString());
                    Objects.requireNonNull(states.get(i)).toBukkitTry(b);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    public void placeCenterY(Location at) {
        readLock.lock();
        try {
            for (var entry : blocks) {
                var i = entry.getKey();
                Block b = at.clone().add(getCenter().getX(), getCenter().getY(), getCenter().getZ()).add(i.getX(), i.getY(), i.getZ()).getBlock();
                b.setBlockData((BlockData) Objects.requireNonNull(entry.getValue()).nativeHandle(), false);

                if (states.containsKey(i)) {
                    Objects.requireNonNull(states.get(i)).toBukkitTry(b);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    public void unplaceCenterY(Location at) {
        readLock.lock();
        try {
            for (IrisBlockVector i : blocks.keys()) {
                at.clone().add(getCenter().getX(), getCenter().getY(), getCenter().getZ()).add(i.getX(), i.getY(), i.getZ()).getBlock().setBlockData((BlockData) States.AIR.nativeHandle(), false);
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String key() {
        return getLoadKey();
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public int depth() {
        return d;
    }

    public int volume() {
        return blocks.size();
    }

    @Override
    public String getFolderName() {
        return "objects";
    }

    @Override
    public String getTypeName() {
        return "Object";
    }
}
