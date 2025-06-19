package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.io.NamedTag;
import com.volmit.iris.util.nbt.tag.*;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.reflect.V;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import de.crazydev22.platformutils.scheduler.task.Task;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.FileUtil;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                    Task i = null;
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    if (mv > 500_000) {
                        largeObject = true;
                        Iris.info(C.GRAY + "Converting.. "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        Iris.info(C.GRAY + "- It may take a while");
                        if (sender.isPlayer()) {
                            i = J.ar(() -> sender.sendProgress((double) v.get() / mv, "Converting"), 0);
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
                    if (i != null) i.cancel();
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



