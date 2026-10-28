/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.builtin.objects;

import art.arcane.iris.builtin.prepare.ObjectTranslate;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.noise.NoiseStyle;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementRequest;
import com.volmit.placement.PlacementRun;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.PackWorld;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One object placed through the pack's placement stages with an object placement's settings: the input Iris's built-in
 * placement stages read ({@link PlacementRun#input}), and what they report back, the height the object was anchored at
 * and which of the written blocks are the object's own. Made for one placement; not thread-safe.
 */
public final class ObjectRun {
    private static final int NOT_ANCHORED = Integer.MIN_VALUE;
    private static final int NEIGHBOR_READ_BLOCKS = 1;
    private static final long X_MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long Z_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;
    private static final long Y_MULTIPLIER = 0x165667B19E3779F9L;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private PlaceableObject object;
    private final ObjectPlacement settings;
    private final int x;
    private final int requestedY;
    private final int z;
    private final Cave cave;
    private final List<BlockPosition> objectBlocks = new ArrayList<>();
    private int anchorY = NOT_ANCHORED;

    private ObjectRun(PlaceableObject object, ObjectPlacement settings, int x, int requestedY, int z, Cave cave) {
        this.object = Objects.requireNonNull(object, "object");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.x = x;
        this.requestedY = requestedY;
        this.z = z;
        this.cave = cave;
    }

    /** @param requestedY the height to place at, or a negative value for the stages to sample it */
    public static ObjectRun of(PlaceableObject object, ObjectPlacement settings, int x, int requestedY, int z) {
        return new ObjectRun(object, settings, x, requestedY, z, null);
    }

    /** A placement inside {@code cave}, which the stages anchor in and keep the object's height within. */
    public static ObjectRun inCave(PlaceableObject object, ObjectPlacement settings, int x, int requestedY, int z, Cave cave) {
        return new ObjectRun(object, settings, x, requestedY, z, Objects.requireNonNull(cave, "cave"));
    }

    /**
     * The cave an object is placed in.
     *
     * @param surface the height of its floor
     * @param height  how many blocks high it is
     */
    public record Cave(int surface, int height) {
    }

    /** Places the object into {@code terrain} through the pack's placement stages of {@code world} ({@link #toRun}). */
    public PlacementOutcome placeIn(PackWorld world, PlacementTerrain terrain) {
        return world.placements().place(toRun(terrain, world));
    }

    /**
     * The run of this placement in {@code terrain}. Its seed comes from the world's seed and the placement's identity, the
     * object's name and requested position, so the same placement draws the same in every run. Its area is every column
     * the object reaches in any rotation around its anchor, moved by its translation and surface warp, and one block more
     * for neighbor reads. A placement that smart bores bores its object first, so the stages see the bored air.
     */
    public PlacementRun toRun(PlacementTerrain terrain, PackWorld world) {
        return toRun(terrain, world.seed(), world.resources().objects());
    }

    /** As {@link #toRun(PlacementTerrain, PackWorld)}, for a world of seed {@code worldSeed} whose pack's objects are {@code objects}. */
    public PlacementRun toRun(PlacementTerrain terrain, long worldSeed, PackObjects objects) {
        if (settings.smartBore()) {
            object = objects.smartBored(object);
        }
        PlacementRequest request = new PlacementRequest(object.geometry(), new BlockPosition(x, requestedY, z));
        return new PlacementRun(request, object.name(), Optional.ofNullable(settings.json()), area(), terrain, this,
                placementSeed(worldSeed));
    }

    public PlaceableObject object() {
        return object;
    }

    /** The settings as the caller passed them; the stages derive what they change, such as the rotation, themselves. */
    public ObjectPlacement settings() {
        return settings;
    }

    public int x() {
        return x;
    }

    /** The height the caller asked for, negative when the stages sample it. */
    public int requestedY() {
        return requestedY;
    }

    public int z() {
        return z;
    }

    /** The cave the object is placed in; empty outside caves. */
    public Optional<Cave> cave() {
        return Optional.ofNullable(cave);
    }

    /** Reports the height the object was anchored at. */
    public void anchoredAt(int y) {
        anchorY = y;
    }

    /** The height the object was anchored at; empty until a stage anchors it. */
    public OptionalInt anchorY() {
        return anchorY == NOT_ANCHORED ? OptionalInt.empty() : OptionalInt.of(anchorY);
    }

    /** Reports a written block as one of the object's own, as opposed to boring, stilts, snow or a terrain bend. */
    public void objectBlockWritten(BlockPosition position) {
        objectBlocks.add(position);
    }

    /** The object's own blocks the stages wrote, in the order they wrote them; they reach the world only if it is placed. */
    public List<BlockPosition> objectBlocks() {
        return Collections.unmodifiableList(objectBlocks);
    }

    private BlockBox area() {
        int objectRadius = Math.max(rotationRadius(object.geometry().bounds()), rotationRadius(declaredBox()));
        int reach = objectRadius + translationLength(settings.translate()) + warpMargin(settings.warp()) + NEIGHBOR_READ_BLOCKS;
        return new BlockBox(x - reach, Integer.MIN_VALUE, z - reach, x + reach + 1, Integer.MAX_VALUE, z + reach + 1);
    }

    /** The object's declared size around its center, which its block offsets count from. */
    private BlockBox declaredBox() {
        BlockPosition center = object.center();
        return new BlockBox(-center.x(), -center.y(), -center.z(), Math.max(1, object.width()) - center.x(),
                Math.max(1, object.height()) - center.y(), Math.max(1, object.depth()) - center.z());
    }

    /** The farthest any block of {@code bounds} lies from the origin, which rotations turn around. */
    private static int rotationRadius(BlockBox bounds) {
        long farthestX = farthest(bounds.minX(), bounds.maxX());
        long farthestY = farthest(bounds.minY(), bounds.maxY());
        long farthestZ = farthest(bounds.minZ(), bounds.maxZ());
        return (int) Math.ceil(Math.sqrt(farthestX * farthestX + farthestY * farthestY + farthestZ * farthestZ));
    }

    private static long farthest(int min, int maxExclusive) {
        return Math.max(Math.abs((long) min), Math.abs((long) maxExclusive - 1));
    }

    private static int translationLength(ObjectTranslate translate) {
        long translateX = translate.x();
        long translateY = translate.y();
        long translateZ = translate.z();
        return (int) Math.ceil(Math.sqrt(translateX * translateX + translateY * translateY + translateZ * translateZ));
    }

    private static int warpMargin(NoiseStyle warp) {
        return warps(warp) ? (int) Math.ceil(Math.abs(warp.multiplier()) / 2D) : 0;
    }

    /**
     * Whether {@code warp} moves the placement's columns: any base style but {@link NoiseStyle#FLAT}. Unlike
     * {@link NoiseStyle#isFlat()}, a flat style with an expression still does not warp, as Iris always read it.
     */
    public static boolean warps(NoiseStyle warp) {
        return !NoiseStyle.FLAT.equals(warp.style());
    }

    private long placementSeed(long worldSeed) {
        return worldSeed ^ fnv1a(object.name()) ^ (x * X_MULTIPLIER) ^ (requestedY * Y_MULTIPLIER) ^ (z * Z_MULTIPLIER);
    }

    private static long fnv1a(String text) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (value & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }
}
