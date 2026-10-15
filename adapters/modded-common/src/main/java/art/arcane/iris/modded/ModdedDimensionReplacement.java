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

import art.arcane.volmlib.util.json.JSONObject;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public record ModdedDimensionReplacement(String levelKey, String pack, String packDimension, OptionalLong seed) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
    private static final Pattern PACK = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern PACK_DIMENSION = Pattern.compile("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*");
    private static final String TYPE = "type";
    private static final String SEED = "seed";

    public ModdedDimensionReplacement {
        Objects.requireNonNull(levelKey, "levelKey");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(packDimension, "packDimension");
        Objects.requireNonNull(seed, "seed");
    }

    public static ModdedDimensionReplacement parse(String levelKey, String type, OptionalLong seed) {
        String level = requireLevelKey(levelKey);
        String reference = type == null ? "" : type.trim();
        int packColon = reference.indexOf(':');
        String pack = packColon >= 0 ? reference.substring(0, packColon) : reference;
        String packDimension = packColon >= 0 ? reference.substring(packColon + 1) : reference;
        if (!PACK.matcher(pack).matches()
                || !PACK_DIMENSION.matcher(packDimension).matches()
                || pack.contains("..")
                || packDimension.contains("..")) {
            throw new IllegalArgumentException("Invalid replacement type '" + type
                    + "' for " + level + ". Use pack or pack:dimensionKey.");
        }
        OptionalLong replacementSeed = Objects.requireNonNull(seed, "seed");
        if (replacementSeed.isPresent()) {
            requireUsableSeed(replacementSeed.getAsLong());
        }
        return new ModdedDimensionReplacement(level, pack, packDimension, replacementSeed);
    }

    public static long requireUsableSeed(long seed) {
        if (seed == Long.MIN_VALUE) {
            throw new IllegalArgumentException("Seed " + Long.MIN_VALUE
                    + " is reserved on mod loaders; choose any other signed 64-bit integer");
        }
        return seed;
    }

    public static ModdedDimensionReplacement fromJson(String levelKey, Object value) {
        if (!(value instanceof JSONObject entry)) {
            throw new IllegalArgumentException("the replacement must be an object with a \"type\" and an optional \"seed\"");
        }
        Object type = entry.opt(TYPE);
        if (!(type instanceof String typeRef)) {
            throw new IllegalArgumentException("the replacement \"type\" must be a pack or pack:dimensionKey string");
        }
        if (!entry.has(SEED) || entry.isNull(SEED)) {
            return parse(levelKey, typeRef, OptionalLong.empty());
        }
        Object seed = entry.opt(SEED);
        if (!(seed instanceof Integer) && !(seed instanceof Long)) {
            throw new IllegalArgumentException("the replacement \"seed\" must be a signed 64-bit integer");
        }
        return parse(levelKey, typeRef, OptionalLong.of(((Number) seed).longValue()));
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put(TYPE, packRef());
        if (seed.isPresent()) {
            json.put(SEED, seed.getAsLong());
        }
        return json;
    }

    public String packRef() {
        return pack.equals(packDimension) ? pack : pack + ":" + packDimension;
    }

    public ModdedDimensionReplacement withSeed(long replacementSeed) {
        return new ModdedDimensionReplacement(levelKey, pack, packDimension, OptionalLong.of(replacementSeed));
    }

    public ModdedDimensionReplacement withPack(String canonicalPack, String canonicalPackDimension) {
        return new ModdedDimensionReplacement(levelKey, canonicalPack, canonicalPackDimension, seed);
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

    public static String requireLevelKey(String levelKey) {
        String level = levelKey == null ? "" : levelKey.trim();
        int colon = level.indexOf(':');
        if (colon <= 0
                || !NAMESPACE.matcher(level.substring(0, colon)).matches()
                || !PATH.matcher(level.substring(colon + 1)).matches()
                || level.contains("..")) {
            throw new IllegalArgumentException("Invalid dimension key '" + levelKey
                    + "'. Use namespace:path, for example examplemod:skylands.");
        }
        return level;
    }
}
