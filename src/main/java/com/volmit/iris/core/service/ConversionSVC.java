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

package com.volmit.iris.core.service;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Consumer2;
import com.volmit.iris.util.io.Converter;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.io.NamedTag;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.IntTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Jigsaw;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class ConversionSVC implements IrisService {
    private KList<Converter> converters;
    private File folder;

    @Override
    public void onEnable() {
        folder = Iris.instance.getDataFolder("convert");
        converters = new KList<>();

        J.s(() ->
                J.attemptAsync(() ->
                {

                }), 5);
    }

    @Override
    public void onDisable() {

    }

    private String toPoolName(String poolReference) {
        return poolReference.split("\\Q:\\E")[1];
    }

    public void convertStructures(File in, File out, VolmitSender s) {
        KMap<String, IrisJigsawPool> pools = new KMap<>();
        KList<File> roots = new KList<>();
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger at = new AtomicInteger(0);
        File destPools = new File(out.getAbsolutePath() + "/jigsaw-pools");
        destPools.mkdirs();
        findAllNBT(in, (folder, file) -> {
            total.getAndIncrement();
            if (roots.addIfMissing(folder)) {
                String b = in.toURI().relativize(folder.toURI()).getPath();
                if (b.startsWith("/")) {
                    b = b.substring(1);
                }

                if (b.endsWith("/")) {
                    b = b.substring(0, b.length() - 1);
                }

                pools.put(b, new IrisJigsawPool());
            }
        });
        findAllNBT(in, (folder, file) -> {
            at.getAndIncrement();
            String b = in.toURI().relativize(folder.toURI()).getPath();
            if (b.startsWith("/")) {
                b = b.substring(1);
            }

            if (b.endsWith("/")) {
                b = b.substring(0, b.length() - 1);
            }
            IrisJigsawPool jpool = pools.get(b);
            File destObjects = new File(out.getAbsolutePath() + "/objects/" + in.toURI().relativize(folder.toURI()).getPath());
            File destPieces = new File(out.getAbsolutePath() + "/jigsaw-pieces/" + in.toURI().relativize(folder.toURI()).getPath());
            destObjects.mkdirs();
            destPieces.mkdirs();

            try {
                NamedTag tag = NBTUtil.read(file);
                CompoundTag compound = (CompoundTag) tag.getTag();

                if (compound.containsKey("blocks") && compound.containsKey("palette") && compound.containsKey("size")) {
                    String id = in.toURI().relativize(folder.toURI()).getPath() + file.getName().split("\\Q.\\E")[0];
                    @SuppressWarnings("unchecked") ListTag<IntTag> size = (ListTag<IntTag>) compound.getListTag("size");
                    int w = size.get(0).asInt();
                    int h = size.get(1).asInt();
                    int d = size.get(2).asInt();
                    KList<BlockData> palette = new KList<>();
                    @SuppressWarnings("unchecked") ListTag<CompoundTag> paletteList = (ListTag<CompoundTag>) compound.getListTag("palette");
                    for (int i = 0; i < paletteList.size(); i++) {
                        CompoundTag cp = paletteList.get(i);
                        palette.add(NBTWorld.getBlockData(cp));
                    }
                    IrisJigsawPiece piece = new IrisJigsawPiece();
                    IrisObject object = new IrisObject(w, h, d);
                    @SuppressWarnings("unchecked") ListTag<CompoundTag> blockList = (ListTag<CompoundTag>) compound.getListTag("blocks");
                    for (int i = 0; i < blockList.size(); i++) {
                        CompoundTag cp = blockList.get(i);
                        @SuppressWarnings("unchecked") ListTag<IntTag> pos = (ListTag<IntTag>) cp.getListTag("pos");
                        int x = pos.get(0).asInt();
                        int y = pos.get(1).asInt();
                        int z = pos.get(2).asInt();
                        BlockData bd = palette.get(cp.getInt("state")).clone();

                        if (bd.getMaterial().equals(Material.JIGSAW) && cp.containsKey("nbt")) {
                            piece.setObject(in.toURI().relativize(folder.toURI()).getPath() + file.getName().split("\\Q.\\E")[0]);
                            IrisPosition spos = new IrisPosition(object.getSigned(x, y, z));
                            CompoundTag nbt = cp.getCompoundTag("nbt");
                            CompoundTag finalState = new CompoundTag();
                            finalState.putString("Name", nbt.getString("final_state"));
                            BlockData jd = bd.clone();
                            bd = NBTWorld.getBlockData(finalState);
                            String joint = nbt.getString("joint");
                            String pool = nbt.getString("pool");
                            String poolId = toPoolName(pool);
                            String name = nbt.getString("name");
                            String target = nbt.getString("target");
                            pools.computeIfAbsent(poolId, (k) -> new IrisJigsawPool());
                            IrisJigsawPieceConnector connector = new IrisJigsawPieceConnector();
                            connector.setName(name);
                            connector.setTargetName(target);
                            connector.setRotateConnector(false);
                            connector.setPosition(spos);
                            connector.getPools().add(poolId);
                            connector.setDirection(IrisDirection.getDirection(((Jigsaw) jd).getOrientation()));

                            if (target.equals("minecraft:building_entrance")) {
                                connector.setInnerConnector(true);
                            }

                            piece.getConnectors().add(connector);
                        }

                        if (!bd.getMaterial().equals(Material.STRUCTURE_VOID) && !bd.getMaterial().equals(Material.AIR)) {
                            object.setUnsigned(x, y, z, bd);
                        }
                    }

                    jpool.getPieces().addIfMissing(id);
                    object.write(new File(destObjects, file.getName().split("\\Q.\\E")[0] + ".iob"));
                    IO.writeAll(new File(destPieces, file.getName().split("\\Q.\\E")[0] + ".json"), new JSONObject(new Gson().toJson(piece)).toString(4));
                    Iris.info("[Jigsaw]: (" + Form.pc((double) at.get() / (double) total.get(), 0) + ") Exported Piece: " + id);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                Iris.reportError(e);
            }
        });

        for (String i : pools.k()) {
            try {
                IO.writeAll(new File(destPools, i + ".json"), new JSONObject(new Gson().toJson(pools.get(i))).toString(4));
            } catch (IOException e) {
                e.printStackTrace();
                Iris.reportError(e);
            }
        }

        Iris.info("Done! Exported " + Form.f((total.get() * 2) + pools.size()) + " Files!");
    }

    public void findAllNBT(File path, Consumer2<File, File> inFile) {
        if (path == null) {
            return;
        }

        if (path.isFile() && path.getName().endsWith(".nbt")) {
            inFile.accept(path.getParentFile(), path);
            return;
        }

        for (File i : path.listFiles()) {
            if (i.isDirectory()) {
                findAllNBT(i, inFile);
            } else if (i.isFile() && i.getName().endsWith(".nbt")) {
                inFile.accept(path, i);
            }
        }
    }

    public void check(VolmitSender s) {
        int m = 0;
        Iris.instance.getDataFolder("convert");

        for (File i : folder.listFiles()) {
            for (Converter j : converters) {
                if (i.getName().endsWith("." + j.getInExtension())) {
                    File out = new File(folder, i.getName().replaceAll("\\Q." + j.getInExtension() + "\\E", "." + j.getOutExtension()));
                    m++;
                    j.convert(i, out);
                    s.sendMessage("Converted " + i.getName() + " -> " + out.getName());
                }
            }

            if (i.isDirectory() && i.getName().equals("structures")) {
                File f = new File(folder, "jigsaw");

                if (!f.exists()) {
                    s.sendMessage("Converting NBT Structures into Iris Jigsaw Structures...");
                    f.mkdirs();
                    J.a(() -> convertStructures(i, f, s));
                }
            }
        }

        s.sendMessage("Converted " + m + " File" + (m == 1 ? "" : "s"));
    }
}
