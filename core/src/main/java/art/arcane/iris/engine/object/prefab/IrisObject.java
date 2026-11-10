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

import com.volmit.object.PlaceableObject;
import com.volmit.object.ScaleMethod;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.ObjectGeometry;
import art.arcane.iris.core.modules.PlatformBlockStates;
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
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A voxel volume loaded from a .iob file. Instances are loader cached and shared across generation threads, so all
 * volume access goes through the read/write lock pair below.
 * <p>
 * The heavy behaviour lives in package-private collaborators that read this state directly:
 * {@link IrisObjectIO} (binary persistence), {@link IrisObjectShaping} (boring, shrinkwrap, block classification),
 * {@link IrisObjectTransforms} (rotation, scaling, interpolated upscaling). Placing an object in a world with the
 * pack's placement rules is the placement stages' job, not this class's.
 */
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegistrant implements PlaceableObject {
    protected static final IrisVector HALF = new IrisVector(0.5, 0.5, 0.5);
    /** The name of an object made in code without a load key. */
    public static final String UNNAMED = "unnamed";
    public static final class States {
        public static final PlatformBlockState AIR = B.getState("CAVE_AIR");
        public static final PlatformBlockState STONE = B.getState("STONE");
        public static final PlatformBlockState VAIR = B.getState("VOID_AIR");
        public static final PlatformBlockState VAIR_DEBUG = B.getState("COBWEB");
        public static final PlatformBlockState[] SNOW_LAYERS = new PlatformBlockState[]{B.getState("minecraft:snow[layers=1]"), B.getState("minecraft:snow[layers=2]"), B.getState("minecraft:snow[layers=3]"), B.getState("minecraft:snow[layers=4]"), B.getState("minecraft:snow[layers=5]"), B.getState("minecraft:snow[layers=6]"), B.getState("minecraft:snow[layers=7]"), B.getState("minecraft:snow[layers=8]")};
    }
    public transient final Lock readLock;
    protected transient final Lock writeLock;
    private transient final AtomicCache<IrisObject> smartBoredCopy = new AtomicCache<>();
    @Setter
    protected transient AtomicCache<AxisAlignedBB> aabb = new AtomicCache<>();
    transient final AtomicCache<KList<IrisBlockVector>> surfaceSupportOffsets = new AtomicCache<>();
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

    public IrisObject copy() {
        readLock.lock();
        try {
            IrisObject o = new IrisObject(w, h, d);
            o.setLoadKey(getLoadKey());
            o.setLoader(getLoader());
            o.setLoadFile(getLoadFile());
            o.setCenter(getCenter().clone());

            blocks.forEach((i, v) -> o.blocks.put(i.clone(), v));
            states.forEach((i, v) -> o.states.put(i.clone(), v.clone()));

            return o;
        } finally {
            readLock.unlock();
        }
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

    /**
     * Writes the object's blocks and their block entities around ({@code x}, {@code y}, {@code z}) turned by {@code turn},
     * with no placement rule: the object's own offsets count from that spot, air is left out, and a block that cannot
     * face the new way is dropped. This is what a paste does; placement rules belong to the placement stages.
     *
     * @return how many blocks were written
     */
    public int paste(int x, int y, int z, IObjectPlacer placer, ObjectTurn turn) {
        int written = 0;
        readLock.lock();
        try {
            for (var entry : blocks) {
                PlatformBlockState state = BlockStateRotation.rotate(turn, entry.getValue());
                if (state == null || state.isAir()) {
                    continue;
                }
                IrisBlockVector turned = turn.rotate(entry.getKey());
                int blockX = x + (int) Math.round(turned.getX());
                int blockY = y + (int) Math.round(turned.getY());
                int blockZ = z + (int) Math.round(turned.getZ());
                placer.set(blockX, blockY, blockZ, state);
                TileData tile = states.get(entry.getKey());
                if (tile != null) {
                    placer.setTile(blockX, blockY, blockZ, tile);
                }
                written++;
            }
        } finally {
            readLock.unlock();
        }
        return written;
    }

    /**
     * Stores {@code state} at the unsigned position, as the platform builds it from its key.
     */
    public void setUnsignedState(int x, int y, int z, BlockState state) {
        setUnsigned(x, y, z, B.getState(state.key()));
    }

    /** Stores {@code data} as the block entity at the unsigned position. */
    public void setUnsignedBlockEntity(int x, int y, int z, BlockEntityData data) {
        setUnsignedTile(x, y, z, BlockEntities.tileOf(data));
    }

    /**
     * The stored blocks and block entities as IrisApi values at their unsigned positions, in storage order, for tools that
     * edit an object's contents without the platform's block types. A block Iris cannot read is cave air, as Iris places
     * it. Hand the edited values back through {@link #fromApiContents}.
     */
    public ApiContents apiContents() {
        readLock.lock();
        try {
            PlatformBlockStates conversion = new PlatformBlockStates(B::getState);
            Map<BlockPosition, BlockState> apiBlocks = new LinkedHashMap<>();
            Set<BlockPosition> customBlocks = new LinkedHashSet<>();
            Set<BlockPosition> blocksNeedingEntity = new LinkedHashSet<>();
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : blocks) {
                BlockPosition position = unsignedPositionOf(entry.getKey());
                PlatformBlockState stored = entry.getValue();
                apiBlocks.put(position, stored == null ? ApiContents.UNREADABLE_BLOCK : conversion.toApi(stored));
                if (stored != null && stored.isCustom()) {
                    customBlocks.add(position);
                }
                if (stored != null && stored.hasTileEntity()) {
                    blocksNeedingEntity.add(position);
                }
            }
            Map<BlockPosition, BlockEntityData> apiEntities = new LinkedHashMap<>();
            for (Map.Entry<IrisBlockVector, TileData> entry : states) {
                apiEntities.put(unsignedPositionOf(entry.getKey()), BlockEntities.of(entry.getValue()));
            }
            return new ApiContents(conversion, apiBlocks, apiEntities, customBlocks, blocksNeedingEntity);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * An object of the given size holding {@code contents}, which came from {@link #apiContents} of some object and may
     * have been edited since. A block that was read from the platform is written back as that same platform state.
     */
    public static IrisObject fromApiContents(int w, int h, int d, ApiContents contents) {
        IrisObject object = new IrisObject(w, h, d);
        contents.blocks().forEach((position, state) ->
                object.setUnsigned(position.x(), position.y(), position.z(), contents.platformStateOf(state)));
        contents.blockEntities().forEach((position, entity) ->
                object.setUnsignedBlockEntity(position.x(), position.y(), position.z(), entity));
        return object;
    }

    private BlockPosition unsignedPositionOf(IrisBlockVector signed) {
        return new BlockPosition(
                signed.getBlockX() + center.getBlockX(),
                signed.getBlockY() + center.getBlockY(),
                signed.getBlockZ() + center.getBlockZ());
    }

    /**
     * The contents of an object as IrisApi values, at unsigned positions.
     *
     * @param blocks          the stored blocks, which a tool may change; an edited map goes back through {@link #with}
     * @param blockEntities   the stored block entities
     * @param customBlocks    where the stored block is a custom-content block that only the platform can place
     * @param blocksNeedingEntity where the stored block is of a kind that carries a block entity
     */
    public static final class ApiContents {
        private static final BlockState UNREADABLE_BLOCK = BlockState.parse("minecraft:cave_air");

        private final PlatformBlockStates conversion;
        private final Map<BlockPosition, BlockState> blocks;
        private final Map<BlockPosition, BlockEntityData> blockEntities;
        private final Set<BlockPosition> customBlocks;
        private final Set<BlockPosition> blocksNeedingEntity;

        private ApiContents(PlatformBlockStates conversion, Map<BlockPosition, BlockState> blocks,
                            Map<BlockPosition, BlockEntityData> blockEntities, Set<BlockPosition> customBlocks,
                            Set<BlockPosition> blocksNeedingEntity) {
            this.conversion = conversion;
            this.blocks = blocks;
            this.blockEntities = blockEntities;
            this.customBlocks = customBlocks;
            this.blocksNeedingEntity = blocksNeedingEntity;
        }

        public Map<BlockPosition, BlockState> blocks() {
            return blocks;
        }

        public Map<BlockPosition, BlockEntityData> blockEntities() {
            return blockEntities;
        }

        public Set<BlockPosition> customBlocks() {
            return customBlocks;
        }

        public Set<BlockPosition> blocksNeedingEntity() {
            return blocksNeedingEntity;
        }

        /** These contents with other blocks and block entities, such as a resized copy; the platform states stay the same ones. */
        public ApiContents with(Map<BlockPosition, BlockState> blocks, Map<BlockPosition, BlockEntityData> blockEntities) {
            return new ApiContents(conversion, blocks, blockEntities, customBlocks, blocksNeedingEntity);
        }

        private PlatformBlockState platformStateOf(BlockState state) {
            return conversion.toPlatform(state);
        }
    }

    /** This object as IrisApi geometry, read once until its blocks change. */
    public GeneratedObject getGeometry() {
        return geometry.aquireOrThrow(() -> ObjectGeometry.of(this));
    }

    /**
     * This object with air in its rooms and pockets, a copy made once until the blocks change: this object keeps its blocks,
     * and asking again costs nothing. The copy is its own smart bored copy.
     */
    public IrisObject smartBoredCopy() {
        return smartBoredCopy.aquireOrThrow(() -> {
            IrisObject bored = IrisObjectShaping.smartBoredCopyOf(this);
            bored.smartBoredCopy.aquireOrThrow(() -> bored);
            return bored;
        });
    }

    /** Drops what is computed from the blocks, after they change. */
    void forgetDerivedShapes() {
        surfaceSupportOffsets.reset();
        geometry.reset();
        smartBoredCopy.reset();
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

    public IrisObject rotateCopy(ObjectTurn turn) {
        return IrisObjectTransforms.rotateCopy(this, turn);
    }

    public IrisObject scaled(double scale, ScaleMethod interpolation) {
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
    public String name() {
        String key = getLoadKey();
        return key == null ? UNNAMED : key;
    }

    @Override
    public com.volmit.geometry.BlockPosition center() {
        return new com.volmit.geometry.BlockPosition(center.getX(), center.getY(), center.getZ());
    }

    @Override
    public GeneratedObject geometry() {
        return getGeometry();
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
