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

package art.arcane.iris.core.structure.authoring;

import com.google.gson.GsonBuilder;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.StructureLocator;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.ToolPack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class StructureIndexService {
    private static final String INDEX_PATH = ".iris/structure-index.json";
    private static final String PLATFORM_NAMESPACE_PREFIX = "minecraft:";

    private StructureIndexService() {
    }

    /** Writes the index of every structure the platform registers and the pack's modules know into the pack, and returns its file. */
    public static File write(ToolPack pack, PlatformStructures platform) throws IOException {
        List<String> structures = keysOf(platform.structures());
        List<String> sets = keysOf(platform.structureSets());
        List<String> iris = irisStructureKeys(pack);
        return pack.edit(root -> writeIndex(pack, indexFile(root.toFile()), structures, sets, iris));
    }

    private static List<String> keysOf(List<ResourceId> ids) {
        return ids.stream().map(ResourceId::toString).toList();
    }

    private static List<String> irisStructureKeys(ToolPack pack) {
        PackResources resources = pack.resources();
        Set<String> keys = new TreeSet<>();
        for (StructureLocator provider : pack.services(StructureLocator.class)) {
            for (ResourceId structure : provider.structures(resources)) {
                keys.add(resources.idOf(structure.path()).equals(structure) ? structure.path() : structure.toString());
            }
        }
        return new ArrayList<>(keys);
    }

    private static File writeIndex(ToolPack pack, File file, List<String> structures, List<String> sets, List<String> iris) {
        List<String> vanilla = new ArrayList<>();
        List<String> datapack = new ArrayList<>();
        for (String k : structures) {
            if (k.startsWith(PLATFORM_NAMESPACE_PREFIX)) {
                vanilla.add(k);
            } else {
                datapack.add(k);
            }
        }

        List<String> vanillaSets = new ArrayList<>();
        List<String> datapackSets = new ArrayList<>();
        for (String k : sets) {
            if (k.startsWith(PLATFORM_NAMESPACE_PREFIX)) {
                vanillaSets.add(k);
            } else {
                datapackSets.add(k);
            }
        }

        Collections.sort(vanilla);
        Collections.sort(datapack);
        Collections.sort(vanillaSets);
        Collections.sort(datapackSets);

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

        try {
            file.getParentFile().mkdirs();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            pack.log().error("Could not write the structure index " + file, e);
        }
        return file;
    }

    static File indexFile(File dataFolder) {
        return new File(dataFolder, INDEX_PATH);
    }
}
