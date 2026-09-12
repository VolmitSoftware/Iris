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

package art.arcane.iris.generation.block;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.reflect.KeyedType;
import art.arcane.volmlib.util.collection.KMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("ALL")
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TileData implements Cloneable {
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.LENIENT).setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE).create();
    /**
     * Memoized {@link #resolveMaterial()}. Pasting one tile resolves its material at least twice
     * ({@link #isApplicable(BlockData)} then {@link #toBukkit(Block)}), and matchMaterial normalizes the string and
     * then hits the Bukkit registry - an object with thousands of tiles paid for that thousands of times.
     * <p>
     * Static and keyed by the stored key rather than an instance field for two reasons: distinct tiles overwhelmingly
     * repeat a handful of keys, so the cache is far more effective shared; and an instance field of a Bukkit type -
     * transient or not - fails the pack purity gate, because {@code Class#getDeclaredFields()} resolves every declared
     * field type eagerly, so one such field makes this class unloadable on Fabric/Forge/NeoForge. Static fields are
     * never part of that walk. Bounded by the number of distinct material keys a pack can name.
     */
    private static final Map<String, Material> RESOLVED_MATERIALS = new ConcurrentHashMap<>();
    private static volatile TileReader PLATFORM_READER = null;
    private static volatile TileFactory PLATFORM_FACTORY = null;

    public interface TileReader {
        TileData read(DataInputStream in) throws IOException;
    }

    public interface TileFactory {
        TileData create(PlatformBlockState state, KMap<String, Object> properties);
    }

    public static synchronized TileReader bindPlatformReader(TileReader reader) {
        TileReader previous = PLATFORM_READER;
        PLATFORM_READER = Objects.requireNonNull(reader, "reader");
        return previous;
    }

    public static synchronized TileFactory bindPlatformFactory(TileFactory factory) {
        TileFactory previous = PLATFORM_FACTORY;
        PLATFORM_FACTORY = Objects.requireNonNull(factory, "factory");
        return previous;
    }

    public static synchronized void restorePlatformReader(TileReader reader) {
        PLATFORM_READER = reader;
    }

    public static synchronized void restorePlatformFactory(TileFactory factory) {
        PLATFORM_FACTORY = factory;
    }

    /**
     * The block key this tile belongs to, stored as text so the field type never drags
     * org.bukkit.Material onto a Gson field walk or into generated equals/hashCode/toString.
     * <p>
     * Gson data-compat: Material serialized as its enum name, so the same JSON member name with a
     * String type parses every existing pack byte-for-byte. Persisted values are either a legacy
     * uppercase enum name ("CHEST") or a namespaced key ("minecraft:chest"); both are accepted at
     * the Bukkit resolution edge in {@link #resolveMaterial()}.
     */
    @Getter(AccessLevel.NONE)
    @NonNull
    private String material;
    @NonNull
    private KMap<String, Object> properties;

    /**
     * The platform-neutral block key. Safe on every platform - use this instead of resolving a
     * Material anywhere outside the Bukkit adapter.
     */
    public String getMaterialKey() {
        return material;
    }

    public static boolean setTileState(Block block, TileData data) {
        if (block.getState() instanceof TileState && data.isApplicable(block.getBlockData()))
            return data.toBukkitTry(block);
        return false;
    }

    public static TileData getTileState(Block block, boolean useLegacy) {
        if (!BukkitPlatform.hasTile(block.getType()))
            return null;
        if (useLegacy) {
            LegacyTileData legacy = LegacyTileData.fromBukkit(block.getState());
            if (legacy != null)
                return legacy;
        }

        return new TileData().fromBukkit(block);
    }

    public static TileData of(PlatformBlockState state, KMap<String, Object> properties) {
        TileFactory factory = PLATFORM_FACTORY;
        if (factory != null) {
            return factory.create(state, properties);
        }
        Object handle = state.nativeHandle();
        if (!(handle instanceof BlockData blockData)) {
            return null;
        }
        return new TileData(materialKey(blockData.getMaterial()), properties);
    }

    public static TileData read(DataInputStream in) throws IOException {
        TileReader reader = PLATFORM_READER;
        if (reader != null) {
            return reader.read(in);
        }
        if (!in.markSupported())
            throw new IOException("Mark not supported");
        in.mark(Integer.MAX_VALUE);
        try {
            // Resolving the material is the modern/legacy stream discriminator: an unresolvable
            // first UTF means these bytes are a LegacyTileData record, not a modern one.
            Material resolved = Material.matchMaterial(in.readUTF());
            if (resolved == null)
                throw new IOException("Not a modern tile record");
            return new TileData(materialKey(resolved), gson.fromJson(in.readUTF(), KMap.class));
        } catch (Throwable e) {
            in.reset();
            return new LegacyTileData(in);
        } finally {
            in.mark(0);
        }
    }

    /**
     * Bukkit resolution edge: canonicalizes a Material to the namespaced key form that
     * {@link #toBinary(DataOutputStream)} has always written, falling back to the enum name.
     */
    static String materialKey(Material material) {
        if (material == null) {
            return "";
        }
        NamespacedKey key = KeyedType.getKey(material);
        return key == null ? material.name() : key.toString();
    }

    /**
     * Bukkit resolution edge. Accepts both persisted forms: the legacy uppercase enum name
     * ("CHEST") and the namespaced key ("minecraft:chest") - matchMaterial handles both.
     */
    public Material resolveMaterial() {
        if (material == null || material.isEmpty()) {
            return null;
        }

        Material cached = RESOLVED_MATERIALS.get(material);

        if (cached != null) {
            return cached;
        }

        Material resolved = Material.matchMaterial(material);

        if (resolved != null) {
            RESOLVED_MATERIALS.put(material, resolved);
        }

        return resolved;
    }

    public boolean isApplicable(BlockData data) {
        Material resolved = resolveMaterial();
        return resolved != null && data.getMaterial() == resolved;
    }

    public void toBukkit(Block block) {
        Material resolved = resolveMaterial();
        if (resolved == null) throw new IllegalStateException("Material not set: " + material);
        if (block.getType() != resolved)
            throw new IllegalStateException("Material mismatch: " + block.getType() + " vs " + resolved);
        BukkitPlatform.deserializeTile(properties, block.getLocation());
    }

    public TileData fromBukkit(Block block) {
        if (material != null && !material.isEmpty()) {
            Material resolved = resolveMaterial();
            if (block.getType() != resolved)
                throw new IllegalStateException("Material mismatch: " + block.getType() + " vs " + material);
        } else {
            material = materialKey(block.getType());
        }
        properties = BukkitPlatform.serializeTile(block.getLocation());
        return this;
    }

    public boolean toBukkitTry(Block block) {
        try {
            //noinspection unchecked
            toBukkit(block);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return false;
    }

    public boolean fromBukkitTry(Block block) {
        try {
            //noinspection unchecked
            fromBukkit(block);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);

        }

        return false;
    }

    public void toBinary(DataOutputStream out) throws IOException {
        // The field already holds the canonical key form that this stream has always carried.
        out.writeUTF(material == null ? "" : material);
        out.writeUTF(gson.toJson(properties));
    }

    @Override
    public TileData clone() {
        TileData clone = new TileData();
        clone.material = material;
        clone.properties = properties.copy(); //TODO make a deep copy
        return clone;
    }

    @Override
    public String toString() {
        return String.valueOf(material) + gson.toJson(properties);
    }
}
