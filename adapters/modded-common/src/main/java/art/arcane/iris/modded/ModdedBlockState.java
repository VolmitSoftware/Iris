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

package art.arcane.iris.modded;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformFace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ModdedBlockState implements PlatformBlockState {
    private static final ConcurrentHashMap<String, ModdedBlockState> CACHE = new ConcurrentHashMap<>();

    private final BlockState state;
    private final Map<Property<?>, Comparable<?>> parsedProperties;
    private final String key;
    private final String namespace;
    private final String deferredPlacementKey;
    private volatile String materialKey;
    private volatile Boolean air;
    private volatile Boolean solid;
    private volatile Boolean occluding;
    private volatile Boolean fluid;
    private volatile Boolean water;
    private volatile Boolean foliage;
    private volatile Boolean log;
    private volatile Boolean leaves;
    private volatile Boolean gravity;
    private volatile Boolean decorant;

    private ModdedBlockState(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties, String key, String deferredPlacementKey) {
        this.state = state;
        this.parsedProperties = parsedProperties;
        this.key = key;
        this.namespace = parseNamespace(key);
        this.deferredPlacementKey = deferredPlacementKey;
    }

    public static ModdedBlockState of(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties) {
        String key = serialize(state);
        return CACHE.computeIfAbsent(key, (String k) -> new ModdedBlockState(state, parsedProperties, k, null));
    }

    public static ModdedBlockState deferred(BlockState state, Map<Property<?>, Comparable<?>> parsedProperties, String placementKey) {
        return new ModdedBlockState(state, parsedProperties, serialize(state), Objects.requireNonNull(placementKey));
    }

    public static String serialize(BlockState state) {
        StringBuilder result = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.isSingletonState()) {
            result.append('[');
            result.append(state.getValues()
                    .map((Property.Value<?> value) -> value.property().getName() + "=" + value.valueName())
                    .collect(Collectors.joining(",")));
            result.append(']');
        }
        return result.toString();
    }

    public BlockState handle() {
        return state;
    }

    Map<Property<?>, Comparable<?>> parsedProperties() {
        return parsedProperties;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModdedBlockState blockState)) {
            return false;
        }
        return state.equals(blockState.state) && Objects.equals(deferredPlacementKey, blockState.deferredPlacementKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, deferredPlacementKey);
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
            cached = ModdedBlockResolution.isAir(state);
            air = cached;
        }
        return cached;
    }

    @Override
    public boolean isSolid() {
        Boolean cached = solid;
        if (cached == null) {
            cached = ModdedBlockResolution.isSolid(state);
            solid = cached;
        }
        return cached;
    }

    @Override
    public boolean isOccluding() {
        Boolean cached = occluding;
        if (cached == null) {
            cached = ModdedBlockResolution.isOccluding(state);
            occluding = cached;
        }
        return cached;
    }

    @Override
    public boolean isCustom() {
        return deferredPlacementKey != null;
    }

    @Override
    public String deferredPlacementKey() {
        return deferredPlacementKey;
    }

    @Override
    public PlatformBlockState placementBaseState() {
        return deferredPlacementKey == null ? this : of(state, parsedProperties);
    }

    @Override
    public boolean isFluid() {
        Boolean cached = fluid;
        if (cached == null) {
            cached = ModdedBlockResolution.isFluid(state);
            fluid = cached;
        }
        return cached;
    }

    @Override
    public boolean isWater() {
        Boolean cached = water;
        if (cached == null) {
            cached = ModdedBlockResolution.isWater(state);
            water = cached;
        }
        return cached;
    }

    @Override
    public boolean isWaterLogged() {
        return ModdedBlockResolution.isWaterLogged(state);
    }

    @Override
    public boolean isLit() {
        return ModdedBlockResolution.isLit(state);
    }

    @Override
    public boolean isUpdatable() {
        return ModdedBlockResolution.isUpdatable(state);
    }

    @Override
    public boolean isFoliage() {
        Boolean cached = foliage;
        if (cached == null) {
            cached = ModdedBlockResolution.isFoliage(state);
            foliage = cached;
        }
        return cached;
    }

    @Override
    public boolean isLog() {
        Boolean cached = log;
        if (cached == null) {
            cached = ModdedBlockResolution.isLog(state);
            log = cached;
        }
        return cached;
    }

    @Override
    public boolean isLeaves() {
        Boolean cached = leaves;
        if (cached == null) {
            cached = ModdedBlockResolution.isLeaves(state);
            leaves = cached;
        }
        return cached;
    }

    @Override
    public boolean hasGravity() {
        Boolean cached = gravity;
        if (cached == null) {
            cached = ModdedBlockResolution.hasGravity(state);
            gravity = cached;
        }
        return cached;
    }

    @Override
    public PlatformBlockState mirrorX() {
        return mirrored(Mirror.FRONT_BACK);
    }

    @Override
    public PlatformBlockState mirrorZ() {
        return mirrored(Mirror.LEFT_RIGHT);
    }

    private PlatformBlockState mirrored(Mirror mirror) {
        BlockState mirroredState = state.mirror(mirror);
        if (mirroredState == state) {
            return this;
        }
        return deferredPlacementKey == null
                ? of(mirroredState, null)
                : deferred(mirroredState, null, deferredPlacementKey);
    }

    @Override
    public boolean canAttach(PlatformBlockState support, PlatformFace face) {
        return ModdedBlockResolution.canAttach((BlockState) support.nativeHandle(), face);
    }

    @Override
    public boolean isFoliagePlantable() {
        return ModdedBlockResolution.isFoliagePlantable(state);
    }

    @Override
    public boolean isDecorant() {
        Boolean cached = decorant;
        if (cached == null) {
            cached = ModdedBlockResolution.isDecorant(state);
            decorant = cached;
        }
        return cached;
    }

    @Override
    public boolean isStorage() {
        return ModdedBlockResolution.isStorage(state);
    }

    @Override
    public boolean isStorageChest() {
        return ModdedBlockResolution.isStorageChest(state);
    }

    @Override
    public boolean isOre() {
        return ModdedBlockResolution.isOre(state);
    }

    @Override
    public boolean isDeepSlate() {
        return ModdedBlockResolution.isDeepSlate(state);
    }

    @Override
    public boolean isVineBlock() {
        return ModdedBlockResolution.isVineBlock(state);
    }

    @Override
    public boolean canPlaceOnto(PlatformBlockState onto) {
        return ModdedBlockResolution.canPlaceOnto(state.getBlock(), ((BlockState) onto.nativeHandle()).getBlock());
    }

    @Override
    public boolean matches(PlatformBlockState other) {
        if (!(other instanceof ModdedBlockState blockState)) {
            return false;
        }
        if (state.getBlock() != blockState.state.getBlock()) {
            return false;
        }
        if (state.equals(blockState.state)) {
            return true;
        }
        if (blockState.parsedProperties == null) {
            return false;
        }
        BlockState merged = state;
        for (Map.Entry<Property<?>, Comparable<?>> entry : blockState.parsedProperties.entrySet()) {
            merged = applyProperty(merged, entry.getKey(), entry.getValue());
        }
        return merged.equals(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    @Override
    public boolean hasTileEntity() {
        return ModdedBlockResolution.hasTileEntity(state);
    }

    @Override
    public PlatformBlockState withProperty(String name, String value) {
        String merged = mergeProperty(key, name, value);
        ModdedBlockState resolved = ModdedBlockResolution.strictParse(merged);
        return withHandle(resolved.handle(), resolved.parsedProperties());
    }

    ModdedBlockState withHandle(BlockState updated, Map<Property<?>, Comparable<?>> properties) {
        if (updated == state && properties == parsedProperties) {
            return this;
        }
        return deferredPlacementKey == null
                ? of(updated, properties)
                : deferred(updated, properties, deferredPlacementKey);
    }

    @Override
    public Object nativeHandle() {
        return state;
    }
}
