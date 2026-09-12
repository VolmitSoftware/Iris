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

package art.arcane.iris.structure;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.loading.IrisData;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureIndexService {
    private static final String INDEX_PATH = ".iris/structure-index.json";
    private static final Set<String> GENERATED = ConcurrentHashMap.newKeySet();

    private StructureIndexService() {
    }

    public static File writeOnce(IrisData data) {
        if (data == null || !IrisPlatforms.isBound() || IrisPlatforms.get().structureHooks() == null) {
            return null;
        }

        File dataFolder = data.getDataFolder();
        String key = dataFolder == null ? null : dataFolder.getAbsolutePath();
        if (key == null || !GENERATED.add(key)) {
            return null;
        }

        try {
            return write(data);
        } catch (Throwable e) {
            GENERATED.remove(key);
            IrisLogging.reportError(e);
            return null;
        }
    }

    public static File write(IrisData data) {
        List<String> structures = IrisPlatforms.get().structureHooks().structureKeys();
        List<String> sets = IrisPlatforms.get().structureHooks().structureSetKeys();

        List<String> vanilla = new ArrayList<>();
        List<String> datapack = new ArrayList<>();
        for (String k : structures) {
            if (k.startsWith("minecraft:")) {
                vanilla.add(k);
            } else {
                datapack.add(k);
            }
        }

        List<String> vanillaSets = new ArrayList<>();
        List<String> datapackSets = new ArrayList<>();
        for (String k : sets) {
            if (k.startsWith("minecraft:")) {
                vanillaSets.add(k);
            } else {
                datapackSets.add(k);
            }
        }

        List<String> iris = new ArrayList<>();
        if (data.getStructureLoader() != null) {
            Collections.addAll(iris, data.getStructureLoader().getPossibleKeys());
        }

        Collections.sort(vanilla);
        Collections.sort(datapack);
        Collections.sort(vanillaSets);
        Collections.sort(datapackSets);
        Collections.sort(iris);

        Map<String, Object> structuresNode = new LinkedHashMap<>();
        structuresNode.put("vanilla", vanilla);
        structuresNode.put("datapack", datapack);

        Map<String, Object> setsNode = new LinkedHashMap<>();
        setsNode.put("vanilla", vanillaSets);
        setsNode.put("datapack", datapackSets);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("vanillaStructures", vanilla.size());
        counts.put("datapackStructures", datapack.size());
        counts.put("structureSets", sets.size());
        counts.put("irisStructures", iris.size());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("note", "Generated index of every structure available at runtime. Toggle vanilla/datapack generation via the dimension importedStructures block (by structure key). Place structures via biome/region/dimension structures lists.");
        root.put("counts", counts);
        root.put("structures", structuresNode);
        root.put("structureSets", setsNode);
        root.put("iris", iris);

        File file = indexFile(data.getDataFolder());
        try {
            file.getParentFile().mkdirs();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        return file;
    }

    static File indexFile(File dataFolder) {
        return new File(dataFolder, INDEX_PATH);
    }

}
