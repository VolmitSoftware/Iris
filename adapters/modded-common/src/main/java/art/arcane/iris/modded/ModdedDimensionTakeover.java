/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import java.nio.file.Path;
import java.util.regex.Pattern;

public record ModdedDimensionTakeover(String levelKey, String pack, String packDimension) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
    private static final Pattern PACK = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern PACK_DIMENSION = Pattern.compile("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*");

    public static ModdedDimensionTakeover parse(String levelKey, String packRef) {
        String level = requireLevelKey(levelKey);
        String reference = packRef == null ? "" : packRef.trim();
        int packColon = reference.indexOf(':');
        String pack = packColon >= 0 ? reference.substring(0, packColon) : reference;
        String packDimension = packColon >= 0 ? reference.substring(packColon + 1) : reference;
        if (!PACK.matcher(pack).matches()
                || !PACK_DIMENSION.matcher(packDimension).matches()
                || pack.contains("..")
                || packDimension.contains("..")) {
            throw new IllegalArgumentException("Invalid takeover pack reference '" + packRef
                    + "' for " + level + ". Use pack or pack:dimensionKey.");
        }
        return new ModdedDimensionTakeover(level, pack, packDimension);
    }

    public String packRef() {
        return pack.equals(packDimension) ? pack : pack + ":" + packDimension;
    }

    public String namespace() {
        return levelKey.substring(0, levelKey.indexOf(':'));
    }

    public String path() {
        return levelKey.substring(levelKey.indexOf(':') + 1);
    }

    public Path storageFolder(Path worldRoot) {
        return storageFolder(worldRoot, levelKey);
    }

    public static Path storageFolder(Path worldRoot, String levelKey) {
        String level = requireLevelKey(levelKey);
        int colon = level.indexOf(':');
        Path folder = worldRoot.resolve("dimensions").resolve(level.substring(0, colon));
        for (String segment : level.substring(colon + 1).split("/")) {
            folder = folder.resolve(segment);
        }
        return folder;
    }

    private static String requireLevelKey(String levelKey) {
        String level = levelKey == null ? "" : levelKey.trim();
        int colon = level.indexOf(':');
        if (colon <= 0
                || !NAMESPACE.matcher(level.substring(0, colon)).matches()
                || !PATH.matcher(level.substring(colon + 1)).matches()
                || level.contains("..")) {
            throw new IllegalArgumentException("Invalid takeover dimension key '" + levelKey
                    + "'. Use namespace:path, for example aether:the_aether.");
        }
        return level;
    }
}
