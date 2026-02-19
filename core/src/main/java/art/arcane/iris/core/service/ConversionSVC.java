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

package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.nms.INMS;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.Converter;
import art.arcane.volmlib.util.io.IO;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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

    private void findAllNBT(File path, KList<File> found) {
        if (path == null) {
            return;
        }

        if (path.isFile() && path.getName().endsWith(".nbt")) {
            found.add(path);
            return;
        }

        File[] children = path.listFiles();
        if (children == null) {
            return;
        }

        for (File i : children) {
            if (i.isDirectory()) {
                findAllNBT(i, found);
            } else if (i.isFile() && i.getName().endsWith(".nbt")) {
                found.add(i);
            }
        }
    }

    private void ensurePackMetadata(File datapackRoot) throws IOException {
        File mcmeta = new File(datapackRoot, "pack.mcmeta");
        if (mcmeta.exists()) {
            return;
        }
        int format = INMS.get().getDataVersion().getPackFormat();
        String meta = """
                {
                    "pack": {
                        "description": "Iris loose structure ingestion pack",
                        "pack_format": {}
                    }
                }
                """.replace("{}", String.valueOf(format));
        IO.writeAll(mcmeta, meta);
    }

    private int ingestLooseNbtStructures(File source, File datapackRoot) {
        KList<File> nbtFiles = new KList<>();
        findAllNBT(source, nbtFiles);
        if (nbtFiles.isEmpty()) {
            return 0;
        }

        File structureRoot = new File(datapackRoot, "data/iris_loose/structures");
        structureRoot.mkdirs();

        int copied = 0;
        try {
            ensurePackMetadata(datapackRoot);
            for (File nbt : nbtFiles) {
                String relative = source.toURI().relativize(nbt.toURI()).getPath();
                if (relative == null || relative.isEmpty()) {
                    continue;
                }
                File output = new File(structureRoot, relative);
                File parent = output.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                Files.copy(nbt.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
        return copied;
    }

    public void check(VolmitSender s) {
        int m = 0;
        Iris.instance.getDataFolder("convert");

        File[] files = folder.listFiles();
        if (files == null) {
            s.sendMessage("Converted 0 Files");
            return;
        }

        for (File i : files) {
            for (Converter j : converters) {
                if (i.getName().endsWith("." + j.getInExtension())) {
                    File out = new File(folder, i.getName().replaceAll("\\Q." + j.getInExtension() + "\\E", "." + j.getOutExtension()));
                    m++;
                    j.convert(i, out);
                    s.sendMessage("Converted " + i.getName() + " -> " + out.getName());
                }
            }
        }

        File loose = new File(folder, "structures");
        if (loose.isDirectory()) {
            File datapackRoot = Iris.instance.getDataFolder("datapacks", "iris-loose-structures");
            int ingested = ingestLooseNbtStructures(loose, datapackRoot);
            s.sendMessage("Ingested " + ingested + " loose NBT structure" + (ingested == 1 ? "" : "s") + " into " + datapackRoot.getName());
        }

        s.sendMessage("Converted " + m + " File" + (m == 1 ? "" : "s"));
    }
}
