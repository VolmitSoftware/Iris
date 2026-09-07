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

package art.arcane.iris.engine.object;


import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListBlockType;
import art.arcane.iris.engine.object.annotations.RegistryMapBlockState;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.data.B;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Map;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents Block Data")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBlockData extends IrisRegistrant {
    private final transient AtomicCache<PlatformBlockState> blockdata = new AtomicCache<>();
    private final transient AtomicCache<String> realProperties = new AtomicCache<>();
    @RegistryListBlockType
    @Required
    @Desc("The block to use")
    private String block = "air";
    @Desc("Debug this block by printing it to the console when it's used. Must have debug turned on in settings.")
    private boolean debug = false;
    @MinNumber(1)
    @MaxNumber(1000)
    @Desc("The weight is used when this block data is inside of a list of blockdata. A weight of two is just as if you placed two of the same block data values in the same list making it more common when randomly picked.")
    private int weight = 1;
    @Desc("If the block cannot be created on this version, Iris will attempt to use this backup block data instead.")
    private IrisBlockData backup = null;
    @RegistryMapBlockState("block")
    @Desc("Optional properties for this block data such as 'waterlogged': true")
    private KMap<String, Object> data = new KMap<>();
    @Desc("Optional tile data for this block data")
    private KMap<String, Object> tileData = new KMap<>();

    public IrisBlockData(String b) {
        this.block = b;
    }

    public static IrisBlockData from(String j) {
        IrisBlockData b = new IrisBlockData();
        String v = j.trim();
        int bracket = v.indexOf('[');
        String block = (bracket < 0 ? v : v.substring(0, bracket)).toLowerCase(Locale.ROOT);
        b.setBlock(block);

        if (bracket >= 0) {
            KList<String> props = new KList<>();
            String rp = v.substring(bracket + 1).replace("]", "");
            if (!block.contains(":") || block.startsWith("minecraft:")) {
                rp = rp.toLowerCase(Locale.ROOT);
            }

            if (rp.contains(",")) {
                props.add(rp.split("\\Q,\\E"));
            } else {
                props.add(rp);
            }

            for (String i : props) {
                Object kg = filter(i.split("\\Q=\\E")[1]);
                b.data.put(i.split("\\Q=\\E")[0], kg);
            }
        }

        return b;
    }

    private static Object filter(String string) {
        if (string.equals("true")) {
            return true;
        }

        if (string.equals("false")) {
            return false;
        }

        try {
            return Integer.parseInt(string);
        } catch (Throwable ignored) {
            // Checks
        }

        try {
            double value = Double.parseDouble(string);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE && value == Math.rint(value)) {
                return (int) value;
            }
            return value;
        } catch (Throwable ignored) {
            // Checks
        }

        return string;
    }

    public String computeProperties(KMap<String, Object> data) {
        if (data.isEmpty()) {
            return "";
        }

        KList<String> r = new KList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            r.add(entry.getKey() + "=" + filter(entry.getValue().toString()));
        }

        return "[" + r.toString(",") + "]";
    }

    public String computeProperties() {
        return computeProperties(getData());
    }

    /** The full state this entry names, {@code namespace:block[props]}, as the gate and the registries expect it. */
    public String stateKey() {
        return keyify(getBlock()) + computeProperties();
    }

    public PlatformBlockState getBlockData(IrisData data) {
        return blockdata.aquire(() ->
        {
            IrisBlockData customData = data.getBlockLoader() == null ? null : data.getBlockLoader().load(getBlock(), false);

            if (customData != null) {
                PlatformBlockState customState = customData.getBlockData(data);

                if (customState != null) {
                    if (getData().isEmpty()) {
                        return customState;
                    }

                    IrisBlockData merged = IrisBlockData.from(customState.key());
                    merged.getData().putAll(getData());
                    String sx = merged.stateKey();

                    if (debug) {
                        IrisLogging.debug("Block Data used " + sx + " (CUSTOM)");
                    }

                    PlatformBlockState bx = resolve(data, sx);

                    if (bx != null) {
                        return bx;
                    }

                    return customState;
                }
            }

            String ss = stateKey();
            PlatformBlockState resolved = resolve(data, ss);

            if (debug) {
                IrisLogging.debug("Block Data used " + ss);
            }

            if (resolved != null) {
                return resolved;
            }

            if (backup != null) {
                return backup.getBlockData(data);
            }

            IrisLogging.warnOnce("block-missing:" + ss, "Block " + ss + " does not exist on this server and has no backup; generating air");
            return B.getAirState();
        });
    }

    /**
     * {@link #getBlockData(IrisData)} for match sides such as {@code edit} find lists: a key missing on this server
     * (after custom blocks, the gate chain and every backup) yields a {@link art.arcane.iris.core.compat.MissingBlockState}
     * carrying the key instead of air, so a palette placeholder with the same key can still be matched and rewritten.
     */
    public PlatformBlockState getBlockDataOrPlaceholder(IrisData data) {
        ContentGate gate = data.getContentGate();
        String state = stateKey();

        if (!gate.ready() || hasCustomBlock(data) || gate.resolveBlock(state) != null) {
            return getBlockData(data);
        }

        if (backup != null) {
            return backup.getBlockDataOrPlaceholder(data);
        }

        PlatformBlockState placeholder = gate.resolveBlockOrPlaceholder(state);
        return placeholder == null ? getBlockData(data) : placeholder;
    }

    private boolean hasCustomBlock(IrisData data) {
        return data.getBlockLoader() != null && data.getBlockLoader().load(getBlock(), false) != null;
    }

    /**
     * Registry, legacy rename table, then dimension blockFallbacks; null when the state is missing on this server.
     * <p>
     * A gate that is not ready (its registry cannot be enumerated, so every status is
     * {@link art.arcane.iris.core.compat.KeyStatus#UNKNOWN}) gates nothing and answers null for every key, so this
     * keeps the plain registry lookup in that case - otherwise every block in the pack would resolve to air.
     */
    private static PlatformBlockState resolve(IrisData data, String state) {
        ContentGate gate = data.getContentGate();

        if (gate == null || !gate.ready()) {
            return B.getStateOrNull(state, false);
        }

        ContentGate.BlockResolution resolution = gate.resolveBlock(state);
        return resolution == null ? null : resolution.state();
    }

    public TileData tryGetTile(IrisData data) {
        //TODO Do like a registry thing with the tile data registry. Also update the parsing of data to include **block** entities.
        PlatformBlockState state = getBlockData(data);
        String stateKey = state.key();
        int bracket = stateKey.indexOf('[');
        String blockKey = bracket >= 0 ? stateKey.substring(0, bracket) : stateKey;
        if (blockKey.equals("minecraft:spawner") && this.data.containsKey("entitySpawn")) {
            String id = (String) this.data.get("entitySpawn");
            if (tileData == null) tileData = new KMap<>();
            KMap<String, Object> spawnData = (KMap<String, Object>) tileData.computeIfAbsent("SpawnData", k -> new KMap<>());
            KMap<String, Object> entity = (KMap<String, Object>) spawnData.computeIfAbsent("entity", k -> new KMap<>());
            entity.putIfAbsent("id", Identifier.fromString(id).toString());
        }

        if (tileData == null || tileData.isEmpty() || !state.hasTileEntity())
            return null;
        return TileData.of(state, this.tileData);
    }

    private String keyify(String dat) {
        if (dat.contains(":")) {
            return dat;
        }

        return "minecraft:" + dat;
    }

    @Override
    public String getFolderName() {
        return "blocks";
    }

    @Override
    public String getTypeName() {
        return "Block";
    }
}
