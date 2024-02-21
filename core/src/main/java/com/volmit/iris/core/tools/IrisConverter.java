package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.io.NamedTag;
import com.volmit.iris.util.nbt.tag.*;
import com.volmit.iris.util.plugin.VolmitPlugin;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import javassist.bytecode.ByteArray;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

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
        Map<Integer, BlockData> blockmap = new HashMap<>();
        File folder = Iris.instance.getDataFolder("convert");

        FilenameFilter filter = (dir, name) -> name.endsWith(".schem");
        File[] fileList = folder.listFiles(filter);
        for (File schem : fileList) {
            try {
                ExecutorService executorService = Executors.newFixedThreadPool(1);
                executorService.submit(() -> {
                    PrecisionStopwatch p = new PrecisionStopwatch();
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
                    IrisObject object = new IrisObject(objW, objH, objD);
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    if (mv > 100000) {
                        largeObject = true;
                        Iris.info(C.GRAY + "Converting.. "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        Iris.info(C.GRAY + "- It may take a while");
                        if (sender.isPlayer()) {
                            J.a(() -> {
                                while (v.get() != mv) {
                                    double pr = ((double) v.get() / (double ) mv);
                                    sender.sendProgress(pr, "Converting");
                                    J.sleep(16);
                                }
                            });
                        }
                    }
                    CompoundTag paletteTag = (CompoundTag) compound.get("Palette");
                    for (Map.Entry<String, Tag<?>> entry : paletteTag.getValue().entrySet()) {
                        String blockName = entry.getKey();
                        BlockData bd = Bukkit.createBlockData(blockName);
                        Tag<?> blockTag = entry.getValue();
                        int blockId = ((IntTag) blockTag).getValue();
                        blockmap.put(blockId, bd);
                    }

                    ByteArrayTag byteArray = (ByteArrayTag) compound.get("BlockData");
                    byte[] l = byteArray.getValue();

                    for (int h = 0; h < objH; h++) {
                        for (int d = 0; d < objD; d++) {
                            for (int w = 0; w < objW; w++) {
                                BlockData db = blockmap.get((int) l[v.get()]);
                                object.setUnsigned(w, h, d, db);
                                v.getAndAdd(1);
                            }
                        }
                    }

                    try {
                        object.write(new File(folder, schem.getName().replace(".schem", ".iob")));
                    } catch (IOException e) {
                        Iris.info(C.RED + "Failed to save: " + schem.getName());
                        throw new RuntimeException(e);
                    }
                    if (sender.isPlayer()) {

                    }
                    if (largeObject) {
                        Iris.info(C.GRAY + "Converted "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                    } else {
                        Iris.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                    }
                  //  schem.delete();
                }
                });
            } catch (Exception e) {
                Iris.info(C.RED + "Failed to convert: " + schem.getName());
                e.printStackTrace();
                Iris.reportError(e);
            }
        }
    }
}



