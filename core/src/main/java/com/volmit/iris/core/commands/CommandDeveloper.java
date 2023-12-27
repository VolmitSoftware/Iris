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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.IrisEngineSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.plugin.VolmitSender;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Decree(name = "Developer", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {

    @Decree(description = "Get Loaded TectonicPlates Count", origin = DecreeOrigin.BOTH, sync = true)
    public void EngineStatus(
            @Param(description = "World")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }

        Engine engine = IrisToolbelt.access(world).getEngine();
        if(engine != null) {
            long lastUseSize = engine.getMantle().getLastUseMapMemoryUsage();

            Iris.info("-------------------------");
            Iris.info(C.DARK_PURPLE + "Engine Status");
            Iris.info(C.DARK_PURPLE + "Tectonic Limit: " + C.LIGHT_PURPLE + IrisEngineSVC.getTectonicLimit());
            Iris.info(C.DARK_PURPLE + "Tectonic Loaded Plates: " + C.LIGHT_PURPLE + engine.getMantle().getLoadedRegionCount());
            Iris.info(C.DARK_PURPLE + "Tectonic Plates: " + C.LIGHT_PURPLE + engine.getMantle().getNotClearedLoadedRegions());
            Iris.info(C.DARK_PURPLE + "Tectonic ToUnload: " + C.LIGHT_PURPLE + engine.getMantle().getToUnload());
            Iris.info(C.DARK_PURPLE + "Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration((long) engine.getMantle().getTectonicDuration()));
            Iris.info(C.DARK_PURPLE + "Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
            Iris.info(C.DARK_PURPLE + "LastUse Size: " + C.LIGHT_PURPLE + Form.mem(lastUseSize));
            Iris.info("-------------------------");
        } else {
            Iris.info(C.RED + "Engine is null!");
        }
    }
    @Decree(description = "Test", origin = DecreeOrigin.BOTH)
    public void test() {
        Iris.info("Test Developer CMD Executed");
    }

    @Decree(description = "Test the compression algorithms")
    public void compression(
            @Param(description = "base IrisWorld") World world,
            @Param(description = "raw TectonicPlate File") String path,
            @Param(description = "Algorithm to Test") String algorithm,
            @Param(description = "Amount of Tests") int amount) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }

        File file = new File(path);
        if (!file.exists()) return;

        Engine engine = IrisToolbelt.access(world).getEngine();
        if(engine != null) {
            int height = engine.getTarget().getHeight();
            ExecutorService service = Executors.newFixedThreadPool(1);
            VolmitSender sender = sender();
            service.submit(() -> {
                try {
                    DataInputStream raw = new DataInputStream(new FileInputStream(file));
                    TectonicPlate plate = new TectonicPlate(height, raw);
                    raw.close();

                    double d1 = 0;
                    double d2 = 0;
                    long size = 0;
                    File folder = new File("tmp");
                    folder.mkdirs();
                    for (int i = 0; i < amount; i++) {
                        File tmp = new File(folder, RandomStringUtils.randomAlphanumeric(10) + "." + algorithm + ".bin");
                        DataOutputStream dos = createOutput(tmp, algorithm);
                        long start = System.currentTimeMillis();
                        plate.write(dos);
                        dos.close();
                        d1 += System.currentTimeMillis() - start;
                        if (size == 0)
                            size = tmp.length();
                        start = System.currentTimeMillis();
                        DataInputStream din = createInput(tmp, algorithm);
                        new TectonicPlate(height, din);
                        din.close();
                        d2 += System.currentTimeMillis() - start;
                        tmp.delete();
                    }
                    IO.delete(folder);
                    sender.sendMessage(algorithm + " is " + Form.fileSize(size) + " big after compression");
                    sender.sendMessage(algorithm + " Took " + d2/amount + "ms to read");
                    sender.sendMessage(algorithm + " Took " + d1/amount + "ms to write");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            service.shutdown();
		} else {
            Iris.info(C.RED + "Engine is null!");
        }
    }

    private DataInputStream createInput(File file, String algorithm) throws Throwable {
        FileInputStream in = new FileInputStream(file);

        return new DataInputStream(switch (algorithm) {
            case "gzip" -> new GZIPInputStream(in);
            case "lz4f" -> new LZ4FrameInputStream(in);
            case "lz4b" -> new LZ4BlockInputStream(in);
            default -> throw new IllegalStateException("Unexpected value: " + algorithm);
        });
    }

    private DataOutputStream createOutput(File file, String algorithm) throws Throwable {
        FileOutputStream out = new FileOutputStream(file);

        return new DataOutputStream(switch (algorithm) {
            case "gzip" -> new GZIPOutputStream(out);
            case "lz4f" -> new LZ4FrameOutputStream(out);
            case "lz4b" -> new LZ4BlockOutputStream(out);
            default -> throw new IllegalStateException("Unexpected value: " + algorithm);
        });
    }
}


