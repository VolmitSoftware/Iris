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
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.data.B;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

import java.util.Map;

@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents Block Data")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBlockData extends IrisRegistrant {
    @RegistryListBlockType
    @Required
    @Desc("The block to use")
    private String block = "air";

    @Desc("Debug this block by printing it to the console when it's used. Must have debug turned on in settings.")
    private boolean debug = false;

    @Desc("The resource key. Typically Minecraft")
    private String key = "minecraft";

    @MinNumber(1)
    @MaxNumber(1000)
    @Desc("The weight is used when this block data is inside of a list of blockdata. A weight of two is just as if you placed two of the same block data values in the same list making it more common when randomly picked.")
    private int weight = 1;

    @Desc("If the block cannot be created on this version, Iris will attempt to use this backup block data instead.")
    private IrisBlockData backup = null;

    @Desc("Optional properties for this block data such as 'waterlogged': true")
    private KMap<String, Object> data = new KMap<>();

    private final transient AtomicCache<BlockData> blockdata = new AtomicCache<>();
    private final transient AtomicCache<String> realProperties = new AtomicCache<>();

    public IrisBlockData(String b) {
        this.block = b;
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

    public BlockData getBlockData(IrisData data) {
        return blockdata.aquire(() ->
        {
            BlockData b = null;

            IrisBlockData customData = data.getBlockLoader().load(getBlock(), false);

            if (customData != null) {
                b = customData.getBlockData(data);

                if (b != null) {
                    b = b.clone();

                    String st = b.getAsString(true);

                    if (st.contains("[")) {
                        st = st.split("\\Q[\\E")[0];
                    }

                    KMap<String, Object> cdata = customData.getData().copy();

                    for (String i : getData().keySet()) {
                        cdata.put(i, getData().get(i));
                    }

                    String sx = getKey() + ":" + st.split("\\Q:\\E")[1] + computeProperties(cdata);

                    if (debug) {
                        Iris.debug("Block Data used " + sx + " (CUSTOM)");
                    }

                    BlockData bx = B.get(sx);

                    if (bx != null) {
                        return bx;
                    }

                    if (b != null) {
                        return b;
                    }
                }
            }

            String ss = getKey() + ":" + getBlock() + computeProperties();
            b = B.get(ss);

            if (debug) {
                Iris.debug("Block Data used " + ss);
            }

            if (b != null) {
                return b;
            }

            if (backup != null) {
                return backup.getBlockData(data);
            }

            return B.get("AIR");
        });
    }

    public static IrisBlockData from(String j) {
        IrisBlockData b = new IrisBlockData();
        String m = j.toLowerCase().trim();

        if (m.contains(":")) {
            b.setKey(m.split("\\Q:\\E")[0]);
            String v = m.split("\\Q:\\E")[1];

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
        } else {
            b.setBlock(m);
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
            return Integer.valueOf(string);
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        try {
            return Double.valueOf(string).intValue();
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return string;
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
