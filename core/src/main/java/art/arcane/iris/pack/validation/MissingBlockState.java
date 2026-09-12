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
package art.arcane.iris.pack.validation;

import art.arcane.iris.spi.PlatformBlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Placeholder for a block key the running server does not have. It keeps the missing key so an object placement's
 * {@code edit} rule can still match and rewrite it (the type-replace rescue), and behaves as the platform's air
 * everywhere else: every physical predicate and {@link #nativeHandle()} come from the real air state, so a placeholder
 * that does reach the world places air and never throws.
 */
public final class MissingBlockState implements PlatformBlockState {
    private final String key;
    private final String materialKey;
    private final String namespace;
    private final Map<String, String> properties;
    private final PlatformBlockState air;

    private MissingBlockState(String key, PlatformBlockState air) {
        this.key = key;
        this.materialKey = ContentGate.baseKey(key);
        int colon = materialKey.indexOf(':');
        this.namespace = colon < 0 ? "minecraft" : materialKey.substring(0, colon);
        this.properties = properties(key);
        this.air = air;
    }

    /** @param normalizedKey the missing key as {@link ContentGate#normalizeState(String)} returns it */
    public static MissingBlockState of(String normalizedKey, PlatformBlockState air) {
        return new MissingBlockState(Objects.requireNonNull(normalizedKey, "key"), Objects.requireNonNull(air, "air"));
    }

    public static boolean isPlaceholder(PlatformBlockState state) {
        return state instanceof MissingBlockState;
    }

    /** The missing key, without properties. */
    public String missingKey() {
        return materialKey;
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
        return materialKey;
    }

    @Override
    public boolean isAir() {
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isOccluding() {
        return false;
    }

    @Override
    public boolean isCustom() {
        return false;
    }

    @Override
    public boolean isFluid() {
        return false;
    }

    @Override
    public boolean isWater() {
        return false;
    }

    @Override
    public boolean isWaterLogged() {
        return false;
    }

    @Override
    public boolean isLit() {
        return false;
    }

    @Override
    public boolean isUpdatable() {
        return false;
    }

    @Override
    public boolean isFoliage() {
        return false;
    }

    @Override
    public boolean isTreeBlock() {
        return false;
    }

    @Override
    public boolean isFoliagePlantable() {
        return false;
    }

    @Override
    public boolean isDecorant() {
        return false;
    }

    @Override
    public boolean isStorage() {
        return false;
    }

    @Override
    public boolean isStorageChest() {
        return false;
    }

    @Override
    public boolean isOre() {
        return false;
    }

    @Override
    public boolean isDeepSlate() {
        return false;
    }

    @Override
    public boolean isVineBlock() {
        return false;
    }

    @Override
    public boolean canPlaceOnto(PlatformBlockState onto) {
        return air.canPlaceOnto(onto);
    }

    /**
     * Same missing block, and every property this placeholder names is present with the same value on {@code state} -
     * the partial-match contract the adapters use for exact {@code find} entries.
     */
    @Override
    public boolean matches(PlatformBlockState state) {
        if (!(state instanceof MissingBlockState other) || !materialKey.equals(other.materialKey)) {
            return false;
        }
        for (Map.Entry<String, String> required : properties.entrySet()) {
            if (!required.getValue().equals(other.properties.get(required.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasTileEntity() {
        return false;
    }

    @Override
    public PlatformBlockState withProperty(String name, String value) {
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.put(name, value);
        StringBuilder rebuilt = new StringBuilder(materialKey).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (!first) {
                rebuilt.append(',');
            }
            rebuilt.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return new MissingBlockState(rebuilt.append(']').toString(), air);
    }

    @Override
    public Object nativeHandle() {
        return air.nativeHandle();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof MissingBlockState state && key.equals(state.key));
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "missing:" + key;
    }

    private static Map<String, String> properties(String key) {
        int open = key.indexOf('[');
        if (open < 0) {
            return Map.of();
        }
        int close = key.lastIndexOf(']');
        String body = key.substring(open + 1, close < open ? key.length() : close);
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                parsed.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return parsed;
    }
}
