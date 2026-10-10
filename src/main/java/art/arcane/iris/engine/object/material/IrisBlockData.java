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

package art.arcane.iris.engine.object.material;


import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.volmlib.util.cache.AtomicCache;
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
        String v = j.toLowerCase().trim();

        if (v.contains("[")) {
            KList<String> props = new KList<>();
            String rp = v.split("\\Q[\\E")[1].replaceAll("\\Q]\\E", "");
            b.setBlock(v.split("\\Q[\\E")[0]);

            if (rp.contains(",")) {
                props.add(rp.split("\\Q,\\E"));
            } else {
                props.add(rp);
            }

            for (String i : props) {
                Object kg = filter(i.split("\\Q=\\E")[1]);
                b.data.put(i.split("\\Q=\\E")[0], kg);
            }
        } else {
            b.setBlock(v);
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
            return Double.valueOf(string).intValue();
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

    public PlatformBlockState getBlockData(IrisData data) {
        return blockdata.aquire(() ->
        {
            IrisBlockData customData = data.getBlockLoader().load(getBlock(), false);

            if (customData != null) {
                PlatformBlockState customState = customData.getBlockData(data);

                if (customState != null) {
                    String st = customState.key();

                    if (st.contains("[")) {
                        st = st.split("\\Q[\\E")[0];
                    }

                    KMap<String, Object> cdata = customData.getData().copy();

                    for (String i : getData().keySet()) {
                        cdata.put(i, getData().get(i));
                    }

                    String sx = keyify(st) + computeProperties(cdata);

                    if (debug) {
                        IrisLogging.debug("Block Data used " + sx + " (CUSTOM)");
                    }

                    PlatformBlockState bx = B.getState(sx);

                    if (bx != null) {
                        return bx;
                    }

                    return customState;
                }
            }

            String ss = keyify(getBlock()) + computeProperties();
            PlatformBlockState resolved = B.getState(ss);

            if (debug) {
                IrisLogging.debug("Block Data used " + ss);
            }

            if (resolved != null) {
                return resolved;
            }

            if (backup != null) {
                return backup.getBlockData(data);
            }

            return B.getState("AIR");
        });
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
