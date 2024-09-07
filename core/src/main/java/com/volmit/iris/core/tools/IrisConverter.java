/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.io.NamedTag;
import com.volmit.iris.util.nbt.tag.*;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class IrisConverter {
    public static void convertSchematics(VolmitSender sender) {
        File folder = Iris.instance.getDataFolder("convert");

        FilenameFilter filter = (dir, name) -> name.endsWith(".schem");
        File[] fileList = folder.listFiles(filter);
        if (fileList == null) {
            sender.sendMessage("No schematic files to convert found in " + folder.getAbsolutePath());
            return;
        }
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
        for (File schem : fileList) {
            try {
                    PrecisionStopwatch p = PrecisionStopwatch.start();
                    boolean largeObject = false;
                    NamedTag tag = null;
                    try {
                        tag = NBTUtil.read(schem);
                    } catch (IOException e) {
                        Iris.info(C.RED + "Failed to read: " + schem.getName());
                        throw new RuntimeException(e);
                    }
                    CompoundTag compound = (CompoundTag) tag.getTag();

                if (compound.containsKey("Palette") && compound.containsKey("Width") && compound.containsKey("Height") && compound.containsKey("Length")) {
                    int objW = ((ShortTag) compound.get("Width")).getValue();
                    int objH = ((ShortTag) compound.get("Height")).getValue();
                    int objD = ((ShortTag) compound.get("Length")).getValue();
                    int i = -1;
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    if (mv > 500_000) {
                        largeObject = true;
                        Iris.info(C.GRAY + "Converting.. "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        Iris.info(C.GRAY + "- It may take a while");
                        if (sender.isPlayer()) {
                            i = J.ar(() -> {
                                sender.sendProgress((double) v.get() / mv, "Converting");
                            }, 0);
                        }
                    }

                    CompoundTag paletteTag = (CompoundTag) compound.get("Palette");
                    Map<Integer, BlockData> blockmap = new HashMap<>(paletteTag.size(), 0.9f);
                    for (Map.Entry<String, Tag<?>> entry : paletteTag.getValue().entrySet()) {
                        String blockName = entry.getKey();
                        BlockData bd = Bukkit.createBlockData(blockName);
                        Tag<?> blockTag = entry.getValue();
                        int blockId = ((IntTag) blockTag).getValue();
                        blockmap.put(blockId, bd);
                    }

                    ByteArrayTag byteArray = (ByteArrayTag) compound.get("BlockData");
                    byte[] originalBlockArray = byteArray.getValue();

                    IrisObject object = new IrisObject(objW, objH, objD);
                    for (int h = 0; h < objH; h++) {
                        for (int d = 0; d < objD; d++) {
                            for (int w = 0; w < objW; w++) {
                                BlockData bd = blockmap.get((int) originalBlockArray[v.get()]);
                                if (!bd.getMaterial().isAir()) {
                                    object.setUnsigned(w, h, d, bd);
                                }
                                v.getAndAdd(1);
                            }
                        }
                    }
                    if (i != -1) J.car(i);
                    try {
                        object.shrinkwrap();
                        object.write(new File(folder, schem.getName().replace(".schem", ".iob")));
                    } catch (IOException e) {
                        Iris.info(C.RED + "Failed to save: " + schem.getName());
                        throw new RuntimeException(e);
                    }
                    if (sender.isPlayer()) {
                        if (largeObject) {
                            sender.sendMessage(C.IRIS + "Converted "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                        } else {
                            sender.sendMessage(C.IRIS + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        }
                    }
                    if (largeObject) {
                        Iris.info(C.GRAY + "Converted "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                    } else {
                        Iris.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                    }
                    FileUtils.delete(schem);
                }
            } catch (Exception e) {
                Iris.info(C.RED + "Failed to convert: " + schem.getName());
                if (sender.isPlayer()) {
                    sender.sendMessage(C.RED + "Failed to convert: " + schem.getName());
                }
                e.printStackTrace();
                Iris.reportError(e);
            }
        }
        sender.sendMessage(C.GRAY + "converted: " + fileList.length);
        });
    }

}



