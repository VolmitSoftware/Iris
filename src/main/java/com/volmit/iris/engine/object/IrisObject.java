/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.placer.HeightmapObjectPlacer;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.AxisAlignedBB;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.IrisLock;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegistrant {
    protected static final Vector HALF = new Vector(0.5, 0.5, 0.5);
    protected static final BlockData AIR = B.get("CAVE_AIR");
    protected static final BlockData VAIR = B.get("VOID_AIR");
    protected static final BlockData VAIR_DEBUG = B.get("COBWEB");
    protected static final BlockData[] SNOW_LAYERS = new BlockData[]{B.get("minecraft:snow[layers=1]"), B.get("minecraft:snow[layers=2]"), B.get("minecraft:snow[layers=3]"), B.get("minecraft:snow[layers=4]"), B.get("minecraft:snow[layers=5]"), B.get("minecraft:snow[layers=6]"), B.get("minecraft:snow[layers=7]"), B.get("minecraft:snow[layers=8]")};
    protected transient final IrisLock readLock = new IrisLock("read-conclock");
    @Getter
    @Setter
    protected transient volatile boolean smartBored = false;
    @Getter
    @Setter
    protected transient IrisLock lock = new IrisLock("Preloadcache");
    @Setter
    protected transient AtomicCache<AxisAlignedBB> aabb = new AtomicCache<>();
    private KMap<BlockVector, BlockData> blocks;
    private KMap<BlockVector, TileData<? extends TileState>> states;
    @Getter
    @Setter
    private int w;
    @Getter
    @Setter
    private int d;
    @Getter
    @Setter
    private int h;
    @Getter
    @Setter
    private transient BlockVector center;

    public IrisObject(int w, int h, int d) {
        blocks = new KMap<>();
        states = new KMap<>();
        this.w = w;
        this.h = h;
        this.d = d;
        center = new BlockVector(w / 2, h / 2, d / 2);
    }

    public IrisObject() {
        this(0, 0, 0);
    }

    public static BlockVector getCenterForSize(BlockVector size) {
        return new BlockVector(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
    }

    public static AxisAlignedBB getAABBFor(BlockVector size) {
        BlockVector center = new BlockVector(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
        return new AxisAlignedBB(new IrisPosition(new BlockVector(0, 0, 0).subtract(center).toBlockVector()),
                new IrisPosition(new BlockVector(size.getX() - 1, size.getY() - 1, size.getZ() - 1).subtract(center).toBlockVector()));
    }

    @SuppressWarnings({"resource", "RedundantSuppression"})
    public static BlockVector sampleSize(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        DataInputStream din = new DataInputStream(in);
        BlockVector bv = new BlockVector(din.readInt(), din.readInt(), din.readInt());
        Iris.later(din::close);
        return bv;
    }

    private static List<BlockVector> blocksBetweenTwoPoints(Vector loc1, Vector loc2) {
        List<BlockVector> locations = new ArrayList<>();
        int topBlockX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int bottomBlockX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int topBlockY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int bottomBlockY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int topBlockZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
        int bottomBlockZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        for (int x = bottomBlockX; x <= topBlockX; x++) {
            for (int z = bottomBlockZ; z <= topBlockZ; z++) {
                for (int y = bottomBlockY; y <= topBlockY; y++) {
                    locations.add(new BlockVector(x, y, z));
                }
            }
        }
        return locations;
    }

    public AxisAlignedBB getAABB() {
        return aabb.aquire(() -> getAABBFor(new BlockVector(w, h, d)));
    }

    public void ensureSmartBored(boolean debug) {
        if (smartBored) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        BlockData vair = debug ? VAIR_DEBUG : VAIR;
        lock.lock();
        AtomicInteger applied = new AtomicInteger();
        if (getBlocks().isEmpty()) {
            lock.unlock();
            Iris.warn("Cannot Smart Bore " + getLoadKey() + " because it has 0 blocks in it.");
            smartBored = true;
            return;
        }

        BlockVector max = new BlockVector(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE);
        BlockVector min = new BlockVector(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);

        for (BlockVector i : getBlocks().keySet()) {
            max.setX(Math.max(i.getX(), max.getX()));
            min.setX(Math.min(i.getX(), min.getX()));
            max.setY(Math.max(i.getY(), max.getY()));
            min.setY(Math.min(i.getY(), min.getY()));
            max.setZ(Math.max(i.getZ(), max.getZ()));
            min.setZ(Math.min(i.getZ(), min.getZ()));
        }

        BurstExecutor burst = MultiBurst.burst.burst();

        // Smash X
        for (int rayY = min.getBlockY(); rayY <= max.getBlockY(); rayY++) {
            int finalRayY = rayY;
            burst.queue(() -> {
                for (int rayZ = min.getBlockZ(); rayZ <= max.getBlockZ(); rayZ++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockX(); ray <= max.getBlockX(); ray++) {
                        if (getBlocks().containsKey(new BlockVector(ray, finalRayY, rayZ))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            BlockVector v = new BlockVector(i, finalRayY, rayZ);

                            if (!B.isAir(getBlocks().get(v))) {
                                getBlocks().computeIfAbsent(v, (vv) -> vair);
                                applied.getAndIncrement();
                            }
                        }
                    }
                }
            });
        }

        // Smash Y
        for (int rayX = min.getBlockX(); rayX <= max.getBlockX(); rayX++) {
            int finalRayX = rayX;
            burst.queue(() -> {
                for (int rayZ = min.getBlockZ(); rayZ <= max.getBlockZ(); rayZ++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockY(); ray <= max.getBlockY(); ray++) {
                        if (getBlocks().containsKey(new BlockVector(finalRayX, ray, rayZ))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            BlockVector v = new BlockVector(finalRayX, i, rayZ);

                            if (!B.isAir(getBlocks().get(v))) {
                                getBlocks().computeIfAbsent(v, (vv) -> vair);
                                applied.getAndIncrement();
                            }
                        }
                    }
                }
            });
        }

        // Smash Z
        for (int rayX = min.getBlockX(); rayX <= max.getBlockX(); rayX++) {
            int finalRayX = rayX;
            burst.queue(() -> {
                for (int rayY = min.getBlockY(); rayY <= max.getBlockY(); rayY++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockZ(); ray <= max.getBlockZ(); ray++) {
                        if (getBlocks().containsKey(new BlockVector(finalRayX, rayY, ray))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            BlockVector v = new BlockVector(finalRayX, rayY, i);

                            if (!B.isAir(getBlocks().get(v))) {
                                getBlocks().computeIfAbsent(v, (vv) -> vair);
                                applied.getAndIncrement();
                            }
                        }
                    }
                }
            });
        }

        burst.complete();
        smartBored = true;
        lock.unlock();
        Iris.debug("Smart Bore: " + getLoadKey() + " in " + Form.duration(p.getMilliseconds(), 2) + " (" + Form.f(applied.get()) + ")");
    }

    public synchronized IrisObject copy() {
        IrisObject o = new IrisObject(w, h, d);
        o.setLoadKey(o.getLoadKey());
        o.setCenter(getCenter().clone());

        for (BlockVector i : getBlocks().keySet()) {
            o.getBlocks().put(i.clone(), Objects.requireNonNull(getBlocks().get(i)).clone());
        }

        for (BlockVector i : getStates().keySet()) {
            o.getStates().put(i.clone(), Objects.requireNonNull(getStates().get(i)).clone());
        }

        return o;
    }

    public void readLegacy(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        this.w = din.readInt();
        this.h = din.readInt();
        this.d = din.readInt();
        center = new BlockVector(w / 2, h / 2, d / 2);
        int s = din.readInt();

        for (int i = 0; i < s; i++) {
            getBlocks().put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), B.get(din.readUTF()));
        }

        try {
            int size = din.readInt();

            for (int i = 0; i < size; i++) {
                getStates().put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), TileData.read(din));
            }
        } catch (Throwable e) {
            Iris.reportError(e);

        }
    }

    public void read(InputStream in) throws Throwable {
        DataInputStream din = new DataInputStream(in);
        this.w = din.readInt();
        this.h = din.readInt();
        this.d = din.readInt();
        if (!din.readUTF().equals("Iris V2 IOB;")) {
            return;
        }
        center = new BlockVector(w / 2, h / 2, d / 2);
        int s = din.readShort();
        int i;
        KList<String> palette = new KList<>();

        for (i = 0; i < s; i++) {
            palette.add(din.readUTF());
        }

        s = din.readInt();

        for (i = 0; i < s; i++) {
            getBlocks().put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), B.get(palette.get(din.readShort())));
        }

        s = din.readInt();

        for (i = 0; i < s; i++) {
            getStates().put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), TileData.read(din));
        }
    }

    public void write(OutputStream o) throws IOException {
        DataOutputStream dos = new DataOutputStream(o);
        dos.writeInt(w);
        dos.writeInt(h);
        dos.writeInt(d);
        dos.writeUTF("Iris V2 IOB;");
        KList<String> palette = new KList<>();

        for (BlockData i : getBlocks().values()) {
            palette.addIfMissing(i.getAsString());
        }

        dos.writeShort(palette.size());

        for (String i : palette) {
            dos.writeUTF(i);
        }

        dos.writeInt(getBlocks().size());

        for (BlockVector i : getBlocks().keySet()) {
            dos.writeShort(i.getBlockX());
            dos.writeShort(i.getBlockY());
            dos.writeShort(i.getBlockZ());
            dos.writeShort(palette.indexOf(getBlocks().get(i).getAsString()));
        }

        dos.writeInt(getStates().size());
        for (BlockVector i : getStates().keySet()) {
            dos.writeShort(i.getBlockX());
            dos.writeShort(i.getBlockY());
            dos.writeShort(i.getBlockZ());
            getStates().get(i).toBinary(dos);
        }
    }

    public void read(File file) throws IOException {
        FileInputStream fin = new FileInputStream(file);
        try {
            read(fin);
            fin.close();
        } catch (Throwable e) {
            Iris.reportError(e);
            fin.close();
            fin = new FileInputStream(file);
            readLegacy(fin);
            fin.close();
        }
    }

    public void write(File file) throws IOException {
        if (file == null) {
            return;
        }

        FileOutputStream out = new FileOutputStream(file);
        write(out);
        out.close();
    }

    public void shrinkwrap() {
        BlockVector min = new BlockVector();
        BlockVector max = new BlockVector();

        for (BlockVector i : getBlocks().keySet()) {
            min.setX(Math.min(min.getX(), i.getX()));
            min.setY(Math.min(min.getY(), i.getY()));
            min.setZ(Math.min(min.getZ(), i.getZ()));
            max.setX(Math.max(max.getX(), i.getX()));
            max.setY(Math.max(max.getY(), i.getY()));
            max.setZ(Math.max(max.getZ(), i.getZ()));
        }

        w = max.getBlockX() - min.getBlockX();
        h = max.getBlockY() - min.getBlockY();
        d = max.getBlockZ() - min.getBlockZ();
    }

    public void clean() {
        KMap<BlockVector, BlockData> d = new KMap<>();

        for (BlockVector i : getBlocks().keySet()) {
            d.put(new BlockVector(i.getBlockX(), i.getBlockY(), i.getBlockZ()), Objects.requireNonNull(getBlocks().get(i)));
        }

        KMap<BlockVector, TileData<? extends TileState>> dx = new KMap<>();

        for (BlockVector i : getBlocks().keySet()) {
            d.put(new BlockVector(i.getBlockX(), i.getBlockY(), i.getBlockZ()), Objects.requireNonNull(getBlocks().get(i)));
        }

        for (BlockVector i : getStates().keySet()) {
            dx.put(new BlockVector(i.getBlockX(), i.getBlockY(), i.getBlockZ()), Objects.requireNonNull(getStates().get(i)));
        }

        blocks = d;
        states = dx;
    }

    public BlockVector getSigned(int x, int y, int z) {
        if (x >= w || y >= h || z >= d) {
            throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
        }

        return new BlockVector(x, y, z).subtract(center).toBlockVector();
    }

    public void setUnsigned(int x, int y, int z, BlockData block) {
        BlockVector v = getSigned(x, y, z);

        if (block == null) {
            getBlocks().remove(v);
            getStates().remove(v);
        } else {
            getBlocks().put(v, block);
        }
    }

    public void setUnsigned(int x, int y, int z, Block block) {
        BlockVector v = getSigned(x, y, z);

        if (block == null) {
            getBlocks().remove(v);
            getStates().remove(v);
        } else {
            BlockData data = block.getBlockData();
            getBlocks().put(v, data);
            TileData<? extends TileState> state = TileData.getTileState(block);
            if (state != null) {
                Iris.info("Saved State " + v);
                getStates().put(v, state);
            }
        }
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

    public int place(int x, int yv, int z, IObjectPlacer oplacer, IrisObjectPlacement config, RNG rng, Consumer<BlockPosition> listener, CarveResult c, IrisData rdata) {
        IObjectPlacer placer = (config.getHeightmap() != null) ? new HeightmapObjectPlacer(oplacer.getEngine() == null ? IrisContext.get().getEngine() : oplacer.getEngine(), rng, x, yv, z, config, oplacer) : oplacer;

        if (config.isSmartBore()) {
            ensureSmartBored(placer.isDebugSmartBore());
        }

        boolean warped = !config.getWarp().isFlat();
        boolean stilting = (config.getMode().equals(ObjectPlaceMode.STILT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT));
        KMap<Position2, Integer> heightmap = config.getSnow() > 0 ? new KMap<>() : null;
        int spinx = rng.imax() / 1000;
        int spiny = rng.imax() / 1000;
        int spinz = rng.imax() / 1000;
        int rty = config.getRotation().rotate(new BlockVector(0, getCenter().getBlockY(), 0), spinx, spiny, spinz).getBlockY();
        int ty = config.getTranslate().translate(new BlockVector(0, getCenter().getBlockY(), 0), config.getRotation(), spinx, spiny, spinz).getBlockY();
        int y = -1;
        int xx, zz;
        int yrand = config.getTranslate().getYRandom();
        yrand = yrand > 0 ? rng.i(0, yrand) : yrand < 0 ? rng.i(yrand, 0) : yrand;
        boolean bail = false;

        if (yv < 0) {
            if (config.getMode().equals(ObjectPlaceMode.CENTER_HEIGHT)) {
                y = (c != null ? c.getSurface() : placer.getHighest(x, z, getLoader(), config.isUnderwater())) + rty;
                if (placer.isCarved(x, y, z) || placer.isCarved(x, y - 1, z) || placer.isCarved(x, y - 2, z) || placer.isCarved(x, y - 3, z)) {
                    bail = true;
                }
            } else if (config.getMode().equals(ObjectPlaceMode.MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.STILT)) {
                BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
                BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

                for (int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++) {
                    for (int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++) {
                        int h = placer.getHighest(i, j, getLoader(), config.isUnderwater()) + rty;

                        if (placer.isCarved(i, h, j) || placer.isCarved(i, h - 1, j) || placer.isCarved(i, h - 2, j) || placer.isCarved(i, h - 3, j)) {
                            bail = true;
                            break;
                        }

                        if (h > y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.FAST_MAX_HEIGHT) || config.getMode().equals(ObjectPlaceMode.FAST_STILT)) {
                BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
                BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

                for (int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2) + 1) {
                    for (int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2) + 1) {
                        int h = placer.getHighest(i, j, getLoader(), config.isUnderwater()) + rty;

                        if (placer.isCarved(i, h, j) || placer.isCarved(i, h - 1, j) || placer.isCarved(i, h - 2, j) || placer.isCarved(i, h - 3, j)) {
                            bail = true;
                            break;
                        }

                        if (h > y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.MIN_HEIGHT)) {
                y = 257;
                BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
                BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

                for (int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i++) {
                    for (int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j++) {
                        int h = placer.getHighest(i, j, getLoader(), config.isUnderwater()) + rty;
                        if (placer.isCarved(i, h, j) || placer.isCarved(i, h - 1, j) || placer.isCarved(i, h - 2, j) || placer.isCarved(i, h - 3, j)) {
                            bail = true;
                            break;
                        }
                        if (h < y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.FAST_MIN_HEIGHT)) {
                y = 257;
                BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
                BlockVector rotatedDimensions = config.getRotation().rotate(new BlockVector(getW(), getH(), getD()), spinx, spiny, spinz).clone();

                for (int i = x - (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i <= x + (rotatedDimensions.getBlockX() / 2) + offset.getBlockX(); i += (rotatedDimensions.getBlockX() / 2) + 1) {
                    for (int j = z - (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j <= z + (rotatedDimensions.getBlockZ() / 2) + offset.getBlockZ(); j += (rotatedDimensions.getBlockZ() / 2) + 1) {
                        int h = placer.getHighest(i, j, getLoader(), config.isUnderwater()) + rty;
                        if (placer.isCarved(i, h, j) || placer.isCarved(i, h - 1, j) || placer.isCarved(i, h - 2, j) || placer.isCarved(i, h - 3, j)) {
                            bail = true;
                            break;
                        }
                        if (h < y) {
                            y = h;
                        }
                    }
                }
            } else if (config.getMode().equals(ObjectPlaceMode.PAINT)) {
                y = placer.getHighest(x, z, getLoader(), config.isUnderwater()) + rty;
                if (placer.isCarved(x, y, z) || placer.isCarved(x, y - 1, z) || placer.isCarved(x, y - 2, z) || placer.isCarved(x, y - 3, z)) {
                    bail = true;
                }
            }
        } else {
            y = yv;
            if (placer.isCarved(x, y, z) || placer.isCarved(x, y - 1, z) || placer.isCarved(x, y - 2, z) || placer.isCarved(x, y - 3, z)) {
                bail = true;
            }
        }

        if (yv >= 0 && config.isBottom()) {
            y += Math.floorDiv(h, 2);
            bail = placer.isCarved(x, y, z) || placer.isCarved(x, y - 1, z) || placer.isCarved(x, y - 2, z) || placer.isCarved(x, y - 3, z);
        }

        if (bail) {
            return -1;
        }

        if (yv < 0) {
            if (!config.isUnderwater() && !config.isOnwater() && placer.isUnderwater(x, z)) {
                return -1;
            }
        }

        if (c != null && Math.max(0, h + yrand + ty) + 1 >= c.getHeight()) {
            return -1;
        }

        if (config.isUnderwater() && y + rty + ty >= placer.getFluidHeight()) {
            return -1;
        }

        if (!config.getClamp().canPlace(y + rty + ty, y - rty + ty)) {
            return -1;
        }

        if (config.isBore()) {
            BlockVector offset = new BlockVector(config.getTranslate().getX(), config.getTranslate().getY(), config.getTranslate().getZ());
            for (int i = x - Math.floorDiv(w, 2) + (int) offset.getX(); i <= x + Math.floorDiv(w, 2) - (w % 2 == 0 ? 1 : 0) + (int) offset.getX(); i++) {
                for (int j = y - Math.floorDiv(h, 2) - config.getBoreExtendMinY() + (int) offset.getY(); j <= y + Math.floorDiv(h, 2) + config.getBoreExtendMaxY() - (h % 2 == 0 ? 1 : 0) + (int) offset.getY(); j++) {
                    for (int k = z - Math.floorDiv(d, 2) + (int) offset.getZ(); k <= z + Math.floorDiv(d, 2) - (d % 2 == 0 ? 1 : 0) + (int) offset.getX(); k++) {
                        placer.set(i, j, k, AIR);
                    }
                }
            }
        }

        int lowest = Integer.MAX_VALUE;
        y += yrand;
        readLock.lock();

        KMap<BlockVector, String> markers = null;

        try {
            if (config.getMarkers().isNotEmpty() && placer.getEngine() != null) {
                markers = new KMap<>();
                for (IrisObjectMarker j : config.getMarkers()) {
                    IrisMarker marker = getLoader().getMarkerLoader().load(j.getMarker());

                    if (marker == null) {
                        continue;
                    }

                    int max = j.getMaximumMarkers();

                    for (BlockVector i : getBlocks().k().shuffle()) {
                        if (max <= 0) {
                            break;
                        }

                        BlockData data = getBlocks().get(i);

                        for (BlockData k : j.getMark(rdata)) {
                            if (max <= 0) {
                                break;
                            }

                            if (j.isExact() ? k.matches(data) : k.getMaterial().equals(data.getMaterial())) {
                                boolean a = !blocks.containsKey(new BlockVector(i.clone().add(new BlockVector(0, 1, 0))));
                                boolean fff = !blocks.containsKey(new BlockVector(i.clone().add(new BlockVector(0, 2, 0))));

                                if ((marker.isEmptyAbove() && a && fff) || !marker.isEmptyAbove()) {
                                    markers.put(i, j.getMarker());
                                    max--;
                                }
                            }
                        }
                    }
                }
            }

            for (BlockVector g : getBlocks().keySet()) {
                BlockData d;
                TileData<? extends TileState> tile = null;

                try {
                    d = getBlocks().get(g);
                    tile = getStates().get(g);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + getLoadKey() + " (cme)");
                    d = AIR;
                }

                if (d == null) {
                    Iris.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + getLoadKey() + " (null)");
                    d = AIR;
                }

                BlockVector i = g.clone();
                BlockData data = d.clone();
                i = config.getRotation().rotate(i.clone(), spinx, spiny, spinz).clone();
                i = config.getTranslate().translate(i.clone(), config.getRotation(), spinx, spiny, spinz).clone();

                if (stilting && i.getBlockY() < lowest && !B.isAir(data)) {
                    lowest = i.getBlockY();
                }

                if (placer.isPreventingDecay() && (data) instanceof Leaves && !((Leaves) (data)).isPersistent()) {
                    ((Leaves) data).setPersistent(true);
                }

                for (IrisObjectReplace j : config.getEdit()) {
                    if (rng.chance(j.getChance())) {
                        for (BlockData k : j.getFind(rdata)) {
                            if (j.isExact() ? k.matches(data) : k.getMaterial().equals(data.getMaterial())) {
                                BlockData newData = j.getReplace(rng, i.getX() + x, i.getY() + y, i.getZ() + z, rdata).clone();

                                if (newData.getMaterial() == data.getMaterial()) {
                                    data = data.merge(newData);
                                } else {
                                    data = newData;
                                }
                            }
                        }
                    }
                }

                data = config.getRotation().rotate(data, spinx, spiny, spinz);
                xx = x + (int) Math.round(i.getX());
                int yy = y + (int) Math.round(i.getY());
                zz = z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += config.warp(rng, i.getX() + x, i.getY() + y, i.getZ() + z, getLoader());
                    zz += config.warp(rng, i.getZ() + z, i.getY() + y, i.getX() + x, getLoader());
                }

                if (yv < 0 && (config.getMode().equals(ObjectPlaceMode.PAINT))) {
                    yy = (int) Math.round(i.getY()) + Math.floorDiv(h, 2) + placer.getHighest(xx, zz, getLoader(), config.isUnderwater());
                }

                if (heightmap != null) {
                    Position2 pos = new Position2(xx, zz);

                    if (!heightmap.containsKey(pos)) {
                        heightmap.put(pos, yy);
                    }

                    if (heightmap.get(pos) < yy) {
                        heightmap.put(pos, yy);
                    }
                }

                if (config.isMeld() && !placer.isSolid(xx, yy, zz)) {
                    continue;
                }

                if (config.isWaterloggable() && yy <= placer.getFluidHeight() && data instanceof Waterlogged) {
                    ((Waterlogged) data).setWaterlogged(true);
                }

                if (listener != null) {
                    listener.accept(new BlockPosition(xx, yy, zz));
                }

                if (markers != null && markers.containsKey(g)) {
                    placer.getEngine().getMantle().getMantle().set(xx, yy, zz, new MatterMarker(markers.get(g)));
                }

                if (!data.getMaterial().equals(Material.AIR) && !data.getMaterial().equals(Material.CAVE_AIR)) {
                    placer.set(xx, yy, zz, data);

                    if (tile != null) {
                        placer.setTile(xx, yy, zz, tile);
                    }
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
        }
        readLock.unlock();

        if (stilting) {
            readLock.lock();
            for (BlockVector g : getBlocks().keySet()) {
                BlockData d;

                try {
                    d = getBlocks().get(g);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + getLoadKey() + " (stilt cme)");
                    d = AIR;
                }

                if (d == null) {
                    Iris.warn("Failed to read block node " + g.getBlockX() + "," + g.getBlockY() + "," + g.getBlockZ() + " in object " + getLoadKey() + " (stilt null)");
                    d = AIR;
                }

                BlockVector i = g.clone();
                i = config.getRotation().rotate(i.clone(), spinx, spiny, spinz).clone();
                i = config.getTranslate().translate(i.clone(), config.getRotation(), spinx, spiny, spinz).clone();

                if (i.getBlockY() != lowest) {
                    continue;
                }


                if (d == null || B.isAir(d)) {
                    continue;
                }

                xx = x + (int) Math.round(i.getX());
                zz = z + (int) Math.round(i.getZ());

                if (warped) {
                    xx += config.warp(rng, i.getX() + x, i.getY() + y, i.getZ() + z, getLoader());
                    zz += config.warp(rng, i.getZ() + z, i.getY() + y, i.getX() + x, getLoader());
                }

                int yg = placer.getHighest(xx, zz, getLoader(), config.isUnderwater());

                if (yv >= 0 && config.isBottom()) {
                    y += Math.floorDiv(h, 2);
                }

                for (int j = lowest + y; j > yg - config.getOverStilt() - 1; j--) {
                    placer.set(xx, j, zz, d);
                }
            }

            readLock.unlock();
        }

        if (heightmap != null) {
            RNG rngx = rng.nextParallelRNG(3468854);

            for (Position2 i : heightmap.k()) {
                int vx = i.getX();
                int vy = heightmap.get(i);
                int vz = i.getZ();

                if (config.getSnow() > 0) {
                    int height = rngx.i(0, (int) (config.getSnow() * 7));
                    placer.set(vx, vy + 1, vz, SNOW_LAYERS[Math.max(Math.min(height, 7), 0)]);
                }
            }
        }

        return y;
    }

    public IrisObject rotateCopy(IrisObjectRotation rt) {
        IrisObject copy = copy();
        copy.rotate(rt, 0, 0, 0);
        return copy;
    }

    public void rotate(IrisObjectRotation r, int spinx, int spiny, int spinz) {
        KMap<BlockVector, BlockData> d = new KMap<>();

        for (BlockVector i : getBlocks().keySet()) {
            d.put(r.rotate(i.clone(), spinx, spiny, spinz), r.rotate(getBlocks().get(i).clone(),
                    spinx, spiny, spinz));
        }

        KMap<BlockVector, TileData<? extends TileState>> dx = new KMap<>();

        for (BlockVector i : getStates().keySet()) {
            dx.put(r.rotate(i.clone(), spinx, spiny, spinz), getStates().get(i));
        }

        blocks = d;
        states = dx;
    }

    public void place(Location at) {
        for (BlockVector i : getBlocks().keySet()) {
            Block b = at.clone().add(0, getCenter().getY(), 0).add(i).getBlock();
            b.setBlockData(Objects.requireNonNull(getBlocks().get(i)), false);

            if (getStates().containsKey(i)) {
                Iris.info(Objects.requireNonNull(states.get(i)).toString());
                BlockState st = b.getState();
                Objects.requireNonNull(getStates().get(i)).toBukkitTry(st);
                st.update();
            }
        }
    }

    public void placeCenterY(Location at) {
        for (BlockVector i : getBlocks().keySet()) {
            Block b = at.clone().add(getCenter().getX(), getCenter().getY(), getCenter().getZ()).add(i).getBlock();
            b.setBlockData(Objects.requireNonNull(getBlocks().get(i)), false);

            if (getStates().containsKey(i)) {
                Objects.requireNonNull(getStates().get(i)).toBukkitTry(b.getState());
            }
        }
    }

    public synchronized KMap<BlockVector, BlockData> getBlocks() {
        return blocks;
    }

    public synchronized KMap<BlockVector, TileData<? extends TileState>> getStates() {
        return states;
    }

    public void unplaceCenterY(Location at) {
        for (BlockVector i : getBlocks().keySet()) {
            at.clone().add(getCenter().getX(), getCenter().getY(), getCenter().getZ()).add(i).getBlock().setBlockData(AIR, false);
        }
    }

    public IrisObject scaled(double scale, IrisObjectPlacementScaleInterpolator interpolation) {
        Vector sm1 = new Vector(scale - 1, scale - 1, scale - 1);
        scale = Math.max(0.001, Math.min(50, scale));
        if (scale < 1) {
            scale = scale - 0.0001;
        }

        IrisPosition l1 = getAABB().max();
        IrisPosition l2 = getAABB().min();
        @SuppressWarnings({"unchecked", "rawtypes"}) HashMap<BlockVector, BlockData> placeBlock = new HashMap();

        Vector center = getCenter();
        if (getH() == 2) {
            center = center.setY(center.getBlockY() + 0.5);
        }
        if (getW() == 2) {
            center = center.setX(center.getBlockX() + 0.5);
        }
        if (getD() == 2) {
            center = center.setZ(center.getBlockZ() + 0.5);
        }

        IrisObject oo = new IrisObject((int) Math.ceil((w * scale) + (scale * 2)), (int) Math.ceil((h * scale) + (scale * 2)), (int) Math.ceil((d * scale) + (scale * 2)));

        for (Map.Entry<BlockVector, BlockData> entry : blocks.entrySet()) {
            BlockData bd = entry.getValue();
            placeBlock.put(entry.getKey().clone().add(HALF).subtract(center)
                    .multiply(scale).add(sm1).toBlockVector(), bd);
        }

        for (Map.Entry<BlockVector, BlockData> entry : placeBlock.entrySet()) {
            BlockVector v = entry.getKey();
            if (scale > 1) {
                for (BlockVector vec : blocksBetweenTwoPoints(v.clone().add(center), v.clone().add(center).add(sm1))) {
                    oo.getBlocks().put(vec, entry.getValue());
                }
            } else {
                oo.setUnsigned(v.getBlockX(), v.getBlockY(), v.getBlockZ(), entry.getValue());
            }
        }

        if (scale > 1) {
            switch (interpolation) {
                case TRILINEAR -> oo.trilinear((int) Math.round(scale));
                case TRICUBIC -> oo.tricubic((int) Math.round(scale));
                case TRIHERMITE -> oo.trihermite((int) Math.round(scale));
            }
        }

        return oo;
    }

    public void trilinear(int rad) {
        KMap<BlockVector, BlockData> v = getBlocks().copy();
        KMap<BlockVector, BlockData> b = new KMap<>();
        BlockVector min = getAABB().minbv();
        BlockVector max = getAABB().maxbv();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (IrisInterpolation.getTrilinear(x, y, z, rad, (xx, yy, zz) -> {
                        BlockData data = v.get(new BlockVector((int) xx, (int) yy, (int) zz));

                        if (data == null || data.getMaterial().isAir()) {
                            return 0;
                        }

                        return 1;
                    }) >= 0.5) {
                        b.put(new BlockVector(x, y, z), nearestBlockData(x, y, z));
                    } else {
                        b.put(new BlockVector(x, y, z), AIR);
                    }
                }
            }
        }

        blocks = b;
    }

    public void tricubic(int rad) {
        KMap<BlockVector, BlockData> v = getBlocks().copy();
        KMap<BlockVector, BlockData> b = new KMap<>();
        BlockVector min = getAABB().minbv();
        BlockVector max = getAABB().maxbv();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (IrisInterpolation.getTricubic(x, y, z, rad, (xx, yy, zz) -> {
                        BlockData data = v.get(new BlockVector((int) xx, (int) yy, (int) zz));

                        if (data == null || data.getMaterial().isAir()) {
                            return 0;
                        }

                        return 1;
                    }) >= 0.5) {
                        b.put(new BlockVector(x, y, z), nearestBlockData(x, y, z));
                    } else {
                        b.put(new BlockVector(x, y, z), AIR);
                    }
                }
            }
        }

        blocks = b;
    }

    public void trihermite(int rad) {
        trihermite(rad, 0D, 0D);
    }

    public void trihermite(int rad, double tension, double bias) {
        KMap<BlockVector, BlockData> v = getBlocks().copy();
        KMap<BlockVector, BlockData> b = new KMap<>();
        BlockVector min = getAABB().minbv();
        BlockVector max = getAABB().maxbv();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    if (IrisInterpolation.getTrihermite(x, y, z, rad, (xx, yy, zz) -> {
                        BlockData data = v.get(new BlockVector((int) xx, (int) yy, (int) zz));

                        if (data == null || data.getMaterial().isAir()) {
                            return 0;
                        }

                        return 1;
                    }, tension, bias) >= 0.5) {
                        b.put(new BlockVector(x, y, z), nearestBlockData(x, y, z));
                    } else {
                        b.put(new BlockVector(x, y, z), AIR);
                    }
                }
            }
        }

        blocks = b;
    }

    private BlockData nearestBlockData(int x, int y, int z) {
        BlockVector vv = new BlockVector(x, y, z);
        BlockData r = getBlocks().get(vv);

        if (r != null && !r.getMaterial().isAir()) {
            return r;
        }

        double d = Double.MAX_VALUE;

        for (Map.Entry<BlockVector, BlockData> entry : blocks.entrySet()) {
            BlockData dat = entry.getValue();

            if (dat.getMaterial().isAir()) {
                continue;
            }

            double dx = entry.getKey().distanceSquared(vv);

            if (dx < d) {
                d = dx;
                r = dat;
            }
        }

        return r;
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

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
