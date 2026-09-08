/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interned Bukkit adapter for a neutral block state backed by BlockData.
 */
public final class BukkitBlockState implements PlatformBlockState {
    private static final Map<String, BlockFace> CUSTOM_NAMED_FACES = Map.of(
            "north", BlockFace.NORTH, "south", BlockFace.SOUTH,
            "east", BlockFace.EAST, "west", BlockFace.WEST,
            "up", BlockFace.UP, "down", BlockFace.DOWN);
    private static final List<BlockFace> CUSTOM_ROTATION_FACES = List.of(
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST);
    private static final ConcurrentHashMap<String, BukkitBlockState> CACHE = new ConcurrentHashMap<>();
    // Front cache keyed on the BlockData itself (CraftBlockData equals/hashCode delegate to
    // the canonical NMS state): a hit skips getAsString(), which built the full property
    // string on EVERY of() call — the dominant cost of the 99.9% hit case.
    private static final ConcurrentHashMap<BlockData, BukkitBlockState> DATA_CACHE = new ConcurrentHashMap<>();

    private final BlockData data;
    private final String key;
    private final String namespace;
    private volatile String materialKey;
    private volatile Boolean air;
    private volatile Boolean solid;
    private volatile Boolean occluding;
    private volatile Boolean fluid;
    private volatile Boolean water;
    private volatile Boolean waterLogged;
    private volatile Boolean lit;
    private volatile Boolean updatable;
    private volatile Boolean foliage;
    private volatile Boolean treeBlock;
    private volatile Boolean foliagePlantable;
    private volatile Boolean decorant;
    private volatile Boolean storage;
    private volatile Boolean storageChest;
    private volatile Boolean ore;
    private volatile Boolean deepSlate;
    private volatile Boolean vineBlock;
    private volatile Boolean tileEntity;

    private BukkitBlockState(BlockData data, String key) {
        this.data = data;
        this.key = key;
        this.namespace = parseNamespace(key);
    }

    public static BukkitBlockState of(BlockData data) {
        if (data instanceof IrisCustomData custom) {
            return new BukkitBlockState(data, custom.getCustom().toString());
        }
        BukkitBlockState fast = DATA_CACHE.get(data);
        if (fast != null) {
            return fast;
        }
        String key = data.getAsString();
        BukkitBlockState state = CACHE.computeIfAbsent(key, (String k) -> new BukkitBlockState(data, k));
        DATA_CACHE.putIfAbsent(data, state);
        return state;
    }

    public static BlockData rotateCustomData(IrisObjectRotation objectRotation, IrisCustomData custom, int spinxx, int spinyy, int spinzz) {
        Pair<Identifier, KMap<String, String>> parsed = ExternalDataSVC.parseState(custom.getCustom());
        KMap<String, String> original = parsed.getB();
        if (original.isEmpty()) {
            return null;
        }
        KMap<String, String> rotated = new KMap<>(original);
        int spinx = (int) (90D * Math.ceil(Math.abs((spinxx % 360D) / 90D)));
        int spiny = (int) (90D * Math.ceil(Math.abs((spinyy % 360D) / 90D)));
        int spinz = (int) (90D * Math.ceil(Math.abs((spinzz % 360D) / 90D)));
        boolean oriented = false;
        String facing = original.get("facing");
        if (facing != null && CUSTOM_NAMED_FACES.containsKey(facing)) {
            BlockFace face = objectRotation.getFace(rotateFace(objectRotation, CUSTOM_NAMED_FACES.get(facing), spinx, spiny, spinz));
            rotated.put("facing", face.name().toLowerCase(Locale.ROOT));
            oriented = true;
        }
        Axis axis = switch (original.getOrDefault("axis", "")) {
            case "x" -> Axis.X;
            case "y" -> Axis.Y;
            case "z" -> Axis.Z;
            default -> null;
        };
        if (axis != null) {
            Axis result = objectRotation.getAxis(rotateFace(objectRotation, objectRotation.faceForAxis(axis), spinx, spiny, spinz));
            rotated.put("axis", result.name().toLowerCase(Locale.ROOT));
            oriented = true;
        }
        int rotation = rotationIndex(original.get("rotation"));
        if (rotation >= 0) {
            BlockFace face = objectRotation.getHexFace(rotateFace(objectRotation, CUSTOM_ROTATION_FACES.get(rotation), spinx, spiny, spinz));
            int result = CUSTOM_ROTATION_FACES.indexOf(face);
            if (result >= 0) {
                rotated.put("rotation", Integer.toString(result));
            }
            oriented = true;
        }
        for (Map.Entry<String, BlockFace> entry : CUSTOM_NAMED_FACES.entrySet()) {
            String value = original.get(entry.getKey());
            if (value == null) {
                continue;
            }
            String destination = objectRotation.getFace(rotateFace(objectRotation, entry.getValue(), spinx, spiny, spinz)).name().toLowerCase(Locale.ROOT);
            if (original.containsKey(destination)) {
                rotated.put(destination, value);
            }
            oriented = true;
        }
        if (!oriented) {
            return null;
        }
        if (rotated.equals(original)) {
            return custom;
        }
        BlockData resolved = BukkitBlockResolution.resolveOrNull(ExternalDataSVC.buildState(parsed.getA(), rotated).toString());
        return resolved instanceof IrisCustomData ? resolved : custom;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BukkitBlockState state)) {
            return false;
        }
        return data.equals(state.data);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    private static String parseNamespace(String key) {
        String base = key;
        int bracket = base.indexOf('[');
        if (bracket >= 0) {
            base = base.substring(0, bracket);
        }
        int colon = base.indexOf(':');
        return colon >= 0 ? base.substring(0, colon) : "minecraft";
    }

    private static String mergeProperty(String key, String name, String value) {
        int bracket = key.indexOf('[');
        if (bracket < 0) {
            return key + "[" + name + "=" + value + "]";
        }
        String base = key.substring(0, bracket);
        String body = key.substring(bracket + 1, key.lastIndexOf(']'));
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (String entry : body.split(",")) {
            int equals = entry.indexOf('=');
            if (equals < 0) {
                continue;
            }
            properties.put(entry.substring(0, equals).trim(), entry.substring(equals + 1).trim());
        }
        properties.put(name, value);
        StringBuilder merged = new StringBuilder(base).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                merged.append(',');
            }
            merged.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return merged.append(']').toString();
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public String materialKey() {
        String cached = materialKey;
        if (cached == null) {
            int bracket = key.indexOf('[');
            cached = bracket < 0 ? key : key.substring(0, bracket);
            materialKey = cached;
        }
        return cached;
    }

    @Override
    public boolean isAir() {
        Boolean cached = air;
        if (cached == null) {
            cached = BukkitBlockResolution.isAir(data);
            air = cached;
        }
        return cached;
    }

    @Override
    public boolean isSolid() {
        Boolean cached = solid;
        if (cached == null) {
            cached = BukkitBlockResolution.isSolid(data);
            solid = cached;
        }
        return cached;
    }

    @Override
    public boolean isOccluding() {
        Boolean cached = occluding;
        if (cached == null) {
            cached = data.getMaterial().isOccluding();
            occluding = cached;
        }
        return cached;
    }

    @Override
    public boolean isCustom() {
        return data instanceof IrisCustomData;
    }

    @Override
    public String deferredPlacementKey() {
        return data instanceof IrisCustomData custom ? custom.getCustom().toString() : null;
    }

    @Override
    public PlatformBlockState placementBaseState() {
        return data instanceof IrisCustomData custom ? of(custom.getBase()) : this;
    }

    @Override
    public boolean isFluid() {
        Boolean cached = fluid;
        if (cached == null) {
            cached = BukkitBlockResolution.isFluid(data);
            fluid = cached;
        }
        return cached;
    }

    @Override
    public boolean isWater() {
        Boolean cached = water;
        if (cached == null) {
            cached = BukkitBlockResolution.isWater(data);
            water = cached;
        }
        return cached;
    }

    @Override
    public boolean isWaterLogged() {
        Boolean cached = waterLogged;
        if (cached == null) {
            cached = BukkitBlockResolution.isWaterLogged(data);
            waterLogged = cached;
        }
        return cached;
    }

    @Override
    public boolean isLit() {
        Boolean cached = lit;
        if (cached == null) {
            cached = BukkitBlockResolution.isLit(data);
            lit = cached;
        }
        return cached;
    }

    @Override
    public boolean isUpdatable() {
        Boolean cached = updatable;
        if (cached == null) {
            cached = BukkitBlockResolution.isUpdatable(data);
            updatable = cached;
        }
        return cached;
    }

    @Override
    public boolean isFoliage() {
        Boolean cached = foliage;
        if (cached == null) {
            cached = BukkitBlockResolution.isFoliage(data);
            foliage = cached;
        }
        return cached;
    }

    @Override
    public boolean isTreeBlock() {
        Boolean cached = treeBlock;
        if (cached == null) {
            cached = Tag.LOGS.isTagged(data.getMaterial()) || Tag.LEAVES.isTagged(data.getMaterial());
            treeBlock = cached;
        }
        return cached;
    }

    @Override
    public boolean isFoliagePlantable() {
        Boolean cached = foliagePlantable;
        if (cached == null) {
            cached = BukkitBlockResolution.isFoliagePlantable(data);
            foliagePlantable = cached;
        }
        return cached;
    }

    @Override
    public boolean isDecorant() {
        Boolean cached = decorant;
        if (cached == null) {
            cached = BukkitBlockResolution.isDecorant(data);
            decorant = cached;
        }
        return cached;
    }

    @Override
    public boolean isStorage() {
        Boolean cached = storage;
        if (cached == null) {
            cached = BukkitBlockResolution.isStorage(data);
            storage = cached;
        }
        return cached;
    }

    @Override
    public boolean isStorageChest() {
        Boolean cached = storageChest;
        if (cached == null) {
            cached = BukkitBlockResolution.isStorageChest(data);
            storageChest = cached;
        }
        return cached;
    }

    @Override
    public boolean isOre() {
        Boolean cached = ore;
        if (cached == null) {
            cached = BukkitBlockResolution.isOre(data);
            ore = cached;
        }
        return cached;
    }

    @Override
    public boolean isDeepSlate() {
        Boolean cached = deepSlate;
        if (cached == null) {
            cached = BukkitBlockResolution.isDeepSlate(data);
            deepSlate = cached;
        }
        return cached;
    }

    @Override
    public boolean isVineBlock() {
        Boolean cached = vineBlock;
        if (cached == null) {
            cached = BukkitBlockResolution.isVineBlock(data);
            vineBlock = cached;
        }
        return cached;
    }

    @Override
    public boolean canPlaceOnto(PlatformBlockState onto) {
        return BukkitBlockResolution.canPlaceOnto(data.getMaterial(), ((BlockData) onto.nativeHandle()).getMaterial());
    }

    @Override
    public boolean matches(PlatformBlockState state) {
        return data.matches((BlockData) state.nativeHandle());
    }

    @Override
    public boolean hasTileEntity() {
        Boolean cached = tileEntity;
        if (cached == null) {
            cached = INMS.get().hasTile(data.getMaterial());
            tileEntity = cached;
        }
        return cached;
    }

    @Override
    public boolean isAirOrFluid() {
        return isAir() || isFluid();
    }

    @Override
    public PlatformBlockState withProperty(String name, String value) {
        if (data instanceof IrisCustomData custom) {
            if (ExternalDataSVC.parseState(custom.getCustom()).getB().containsKey(name)) {
                String merged = mergeProperty(key, name, value);
                BlockData resolved = BukkitBlockResolution.resolveOrNull(merged);
                if (!(resolved instanceof IrisCustomData)) {
                    throw new IllegalArgumentException("Cannot resolve custom block state " + merged);
                }
                return of(resolved);
            }
            // Re-attach the custom identity (as the proxy's own merge/clone cases do) after
            // editing the base block, so auto-waterlogging cannot turn custom blocks into vanilla.
            String merged = mergeProperty(custom.getBase().getAsString(), name, value);
            BlockData resolved = Bukkit.createBlockData(merged);
            return of(IrisCustomData.of(resolved, custom.getCustom()));
        }
        return of(Bukkit.createBlockData(mergeProperty(key, name, value)));
    }

    @Override
    public Object nativeHandle() {
        return data;
    }

    private static IrisBlockVector rotateFace(IrisObjectRotation objectRotation, BlockFace face, int spinx, int spiny, int spinz) {
        return objectRotation.rotate(new IrisBlockVector(face.getModX(), face.getModY(), face.getModZ()), spinx, spiny, spinz);
    }

    private static int rotationIndex(String value) {
        if (value == null) {
            return -1;
        }
        try {
            int rotation = Integer.parseInt(value);
            return rotation >= 0 && rotation < CUSTOM_ROTATION_FACES.size() ? rotation : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
