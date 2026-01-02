package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.data.Varint;
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

import java.io.*;
import java.util.*;
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

        AtomicInteger counter = new AtomicInteger(0);
        var stopwatch = PrecisionStopwatch.start();
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
            for (File schem : fileList) {
                try {
                    PrecisionStopwatch p = PrecisionStopwatch.start();
                    IrisObject object;
                    boolean largeObject = false;
                    NamedTag tag;
                    try {
                        tag = NBTUtil.read(schem);
                    } catch (IOException e) {
                        Iris.info(C.RED + "Failed to read: " + schem.getName());
                        throw new RuntimeException(e);
                    }
                    CompoundTag compound = (CompoundTag) tag.getTag();
                    int version = resolveVersion(compound);
                    if (!(version == 2 || version == 3))
                        throw new RuntimeException(C.RED + "Unsupported schematic version: " + version);

                    compound = version == 3 ? (CompoundTag) compound.get("Schematic") : compound;
                    int objW = ((ShortTag) compound.get("Width")).getValue();
                    int objH = ((ShortTag) compound.get("Height")).getValue();
                    int objD = ((ShortTag) compound.get("Length")).getValue();
                    int i = -1;
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    if (mv > 2_000_000) {
                        largeObject = true;
                        Iris.info(C.GRAY + "Converting.. " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        Iris.info(C.GRAY + "- It may take a while");
                        if (sender.isPlayer()) {
                            i = J.ar(() -> {
                                sender.sendProgress((double) v.get() / mv, "Converting");
                            }, 0);
                        }
                    }

                    compound = version == 3 ? (CompoundTag) compound.get("Blocks") : compound;
                    CompoundTag paletteTag = (CompoundTag) compound.get("Palette");
                    Map<Integer, BlockData> blockmap = new HashMap<>(paletteTag.size(), 0.9f);
                    for (Map.Entry<String, Tag<?>> entry : paletteTag.getValue().entrySet()) {
                        String blockName = entry.getKey();
                        BlockData bd = Bukkit.createBlockData(blockName);
                        Tag<?> blockTag = entry.getValue();
                        int blockId = ((IntTag) blockTag).getValue();
                        blockmap.put(blockId, bd);
                    }

                    boolean isBytes = version == 3 ? compound.getByteArrayTag("Data").length() < 128 : ((IntTag) compound.get("PaletteMax")).getValue() < 128;
                    ByteArrayTag byteArray = version == 3 ? (ByteArrayTag) compound.get("Data") : (ByteArrayTag) compound.get("BlockData");
                    byte[] originalBlockArray = byteArray.getValue();
                    var din = new DataInputStream(new ByteArrayInputStream(originalBlockArray));
                    object = new IrisObject(objW, objH, objD);
                    for (int h = 0; h < objH; h++) {
                        for (int d = 0; d < objD; d++) {
                            for (int w = 0; w < objW; w++) {
                                int blockIndex = isBytes ? din.read() & 0xFF : Varint.readUnsignedVarInt(din);
                                BlockData bd = blockmap.get(blockIndex);
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
                        counter.incrementAndGet();
                        if (sender.isPlayer()) {
                            if (largeObject) {
                                sender.sendMessage(C.IRIS + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                            } else {
                                sender.sendMessage(C.IRIS + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                            }
                        }
                        if (largeObject) {
                            Iris.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                        } else {
                            Iris.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        }
                        FileUtils.delete(schem);
                    } catch (IOException e) {
                        sender.sendMessage(C.RED + "Failed to save: " + schem.getName());
                        throw new IOException(e);
                    }


                } catch (Exception e) {
                    sender.sendMessage(C.RED + "Failed to convert: " + schem.getName());
                    e.printStackTrace();
                }
            }
            stopwatch.end();
            if (counter.get() != 0) {
                sender.sendMessage(C.GRAY + "Converted: " + counter.get() + " in " + Form.duration(stopwatch.getMillis()));
            }
            if (counter.get() < fileList.length) {
                sender.sendMessage(C.RED + "Some schematics failed to convert. Check the console for details.");
            }
        });
    }

    private static int resolveVersion(CompoundTag compound) throws Exception {
        try {

            IntTag root = compound.getIntTag("Version");
            if (root != null) {
                return root.getValue();
            }
            CompoundTag schematic = (CompoundTag) compound.get("Schematic");
            return schematic.getIntTag("Version").getValue();
        } catch (NullPointerException e) {
            throw new Exception("Cannot resolve schematic version", e);
        }
    }
}


