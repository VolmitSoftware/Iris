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
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
        for (File schem : fileList) {
            try {
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
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    AtomicInteger fv = new AtomicInteger(0);
                    if (mv > 500_000) {
                        largeObject = true;
                        Iris.info(C.GRAY + "Converting.. "+ schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        Iris.info(C.GRAY + "- It may take a while");
                        if (sender.isPlayer()) {
                            J.a(() -> {
//                                while (v.get() != mv) {
//                                    double pr = ((double) v.get() / (double ) mv);
//                                    sender.sendProgress(pr, "Converting");
//                                    J.sleep(16);
//                                }
                            });
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
                    int b = 0;
                    int a = 0;
                    Map<Integer, Byte> y = new HashMap<>();
                    Map<Integer, Byte> x = new HashMap<>();
                    Map<Integer, Byte> z = new HashMap<>();

                    // Height adjustments
                    for (int h = 0; h < objH; h++) {
                        if (b == 0) {
                            y.put(h, (byte) 0);
                        }
                        if (b > 0) {
                            y.put(h, (byte) 1);
                        }
                        a = 0;
                        b = 0;
                        for (int d = 0; d < objD; d++) {
                            for (int w = 0; w < objW; w++) {
                                BlockData db = blockmap.get((int) originalBlockArray[fv.get()]);
                                if(db.getAsString().contains("minecraft:air")) {
                                    a++;
                                } else {
                                    b++;
                                }
                                fv.getAndAdd(1);
                            }
                        }
                    }
                    fv.set(0);

                    // Width adjustments
                    for (int w = 0; w < objW; w++) {
                        if (b == 0) {
                            x.put(w, (byte) 0);
                        }
                        if (b > 0) {
                            x.put(w, (byte) 1);
                        }
                        a = 0;
                        b = 0;
                        for (int h = 0; h < objH; h++) {
                            for (int d = 0; d < objD; d++) {
                                BlockData db = blockmap.get((int) originalBlockArray[fv.get()]);
                                if(db.getAsString().contains("minecraft:air")) {
                                    a++;
                                } else {
                                    b++;
                                }
                                fv.getAndAdd(1);
                            }
                        }
                    }
                    fv.set(0);

                    // Depth adjustments
                    for (int d = 0; d < objD; d++) {
                        if (b == 0) {
                            z.put(d, (byte) 0);
                        }
                        if (b > 0) {
                            z.put(d, (byte) 1);
                        }
                        a = 0;
                        b = 0;
                        for (int h = 0; h < objH; h++) {
                            for (int w = 0; w < objW; w++) {
                                BlockData db = blockmap.get((int) originalBlockArray[fv.get()]);
                                if(db.getAsString().contains("minecraft:air")) {
                                    a++;
                                } else {
                                    b++;
                                }
                                fv.getAndAdd(1);
                            }
                        }
                    }
                    fv.set(0);
                    int CorrectObjH = getCorrectY(y, objH);
                    int CorrectObjW = getCorrectX(x, objW);
                    int CorrectObjD = getCorrectZ(z, objD);

                    //IrisObject object = new IrisObject(CorrectObjW, CorrectObjH, CorrectObjH);
                    IrisObject object = new IrisObject(objW, objH, objD);
                    Vector originalVector = new Vector(objW,objH,objD);


                    int[] yc = null;
                    int[] xc = null;
                    int[] zc = null;


                    int fo = 0;
                    int so = 0;
                    int o = 0;
                    int c = 0;
                    for (Integer i : y.keySet()) {
                        if (y.get(i) == 0) {
                            o++;
                        }
                        if (y.get(i) == 1) {
                            c++;
                            if (c == 1) {
                                fo = o;
                            }
                            o = 0;
                        }
                    }
                    so = o;
                    yc = new int[]{fo, so};

                    fo = 0;
                    so = 0;
                    o = 0;
                    c = 0;
                    for (Integer i : x.keySet()) {
                        if (x.get(i) == 0) {
                            o++;
                        }
                        if (x.get(i) == 1) {
                            c++;
                            if (c == 1) {
                                fo = o;
                            }
                            o = 0;
                        }
                    }
                    so = o;
                    xc = new int[]{fo, so};

                    fo = 0;
                    so = 0;
                    o = 0;
                    c = 0;
                    for (Integer i : z.keySet()) {
                        if (z.get(i) == 0) {
                            o++;
                        }
                        if (z.get(i) == 1) {
                            c++;
                            if (c == 1) {
                                fo = o;
                            }
                            o = 0;
                        }
                    }
                    so = o;
                    zc = new int[]{fo, so};

                    int h1, h2, w1, w2, v1 = 0, volume = objW * objH * objD;
                    Map<Integer, Integer> blockLocationMap = new LinkedHashMap<>();
                    boolean hasAir = false;
                    int pos = 0;
                    for (int i : originalBlockArray) {
                        blockLocationMap.put(pos, i);
                        pos++;
                    }



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


                    try {
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
                  //  schem.delete();
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
        });
    }

    public static boolean isNewPointFurther(int[] originalPoint, int[] oldPoint, int[] newPoint) {
        int oX = oldPoint[1];
        int oY = oldPoint[2];
        int oZ = oldPoint[3];

        int nX = newPoint[1];
        int nY = newPoint[2];
        int nZ = newPoint[3];

        int orX = originalPoint[1];
        int orY = originalPoint[2];
        int orZ = originalPoint[3];

        double oldDistance = Math.sqrt(Math.pow(oX - orX, 2) + Math.pow(oY - orY, 2) + Math.pow(oZ - orZ, 2));
        double newDistance = Math.sqrt(Math.pow(nX - orX, 2) + Math.pow(nY - orY, 2) + Math.pow(nZ - orZ, 2));

        if (newDistance > oldDistance) {
            return true;
        }
        return false;
    }

    public static int[] getCoordinates(int pos, int obX, int obY, int obZ) {
        int z = 0;
        int[] coords = new int[4];
        for (int h = 0; h < obY; h++) {
            for (int d = 0; d < obZ; d++) {
                for (int w = 0; w < obX; w++) {
                    if (z == pos) {
                        coords[1] = w;
                        coords[2] = h;
                        coords[3] = d;
                        return coords;
                    }
                    z++;
                }
            }
        }
        return null;
    }

    public static int getCorrectY(Map<Integer, Byte> y, int H) {
        int fo = 0;
        int so = 0;
        int o = 0;
        int c = 0;
        for (Integer i : y.keySet()) {
            if (y.get(i) == 0) {
                o++;
            }
            if (y.get(i) == 1) {
                c++;
                if(c == 1){
                    fo = o;
                }
                o = 0;
            }
        }
        so = o;
        return H = H - (fo + so);
    }

    public static int getCorrectX(Map<Integer, Byte> x, int W) {
        int fo = 0;
        int so = 0;
        int o = 0;
        int c = 0;
        for (Integer i : x.keySet()) {
            if (x.get(i) == 0) {
                o++;
            }
            if (x.get(i) == 1) {
                c++;
                if(c == 1){
                    fo = o;
                }
                o = 0;
            }
        }
        so = o;
        return W = W - (fo + so);
    }

    public static int getCorrectZ(Map<Integer, Byte> z, int D) {
        int fo = 0;
        int so = 0;
        int o = 0;
        int c = 0;
        for (Integer i : z.keySet()) {
            if (z.get(i) == 0) {
                o++;
            }
            if (z.get(i) == 1) {
                c++;
                if(c == 1){
                    fo = o;
                }
                o = 0;
            }
        }
        so = o;
        return D = D - (fo + so);
    }
}



