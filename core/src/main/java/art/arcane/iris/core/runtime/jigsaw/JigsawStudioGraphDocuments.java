package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.engine.framework.structure.PlanarJigsawWorkcellResolver;
import art.arcane.iris.engine.object.IrisJigsawMode;
import art.arcane.iris.engine.object.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.IrisStructure;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JigsawStudioGraphDocuments {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JigsawStudioGraphDocuments() {
    }

    static Map<String, JigsawPlanarArchetype> expectedThemeSetSources(
            IrisStructure structure,
            Set<String> requestedStableIds
    ) throws IOException {
        Map<String, JigsawPlanarArchetype> expected = new LinkedHashMap<>();
        if (structure.resolvedMode() == IrisJigsawMode.SPATIAL_JIGSAW) {
            if (requestedStableIds.isEmpty()) {
                throw new IOException("A spatial theme set requires at least one workcell source");
            }
            List<String> stableIds = new ArrayList<>(requestedStableIds);
            stableIds.sort(Comparator.naturalOrder());
            for (String stableId : stableIds) {
                if (!stableId.equals(JigsawStudioLayout.SPATIAL_WORKCELL_ID)
                        && !stableId.startsWith(JigsawStudioLayout.SPATIAL_WORKCELL_ID + "/")) {
                    throw new IOException("Invalid spatial workcell source '" + stableId + "'");
                }
                expected.put(stableId, null);
            }
            return expected;
        }
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells;
        try {
            workcells = PlanarJigsawWorkcellResolver.resolve(structure);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Planar workcell configuration is invalid: "
                    + exception.getMessage(), exception);
        }
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell = workcells.get(archetype.modelArchetype());
            if (workcell != null && workcell.enabled()) {
                expected.put(archetype.stableId(), archetype);
            }
        }
        if (expected.isEmpty()) {
            throw new IOException("A planar theme set requires at least one enabled workcell");
        }
        return expected;
    }

    static void requireExactThemeSetSources(
            Map<String, String> requestedSources,
            Set<String> expectedStableIds
    ) throws IOException {
        Set<String> missing = new LinkedHashSet<>(expectedStableIds);
        missing.removeAll(requestedSources.keySet());
        Set<String> unexpected = new LinkedHashSet<>(requestedSources.keySet());
        unexpected.removeAll(expectedStableIds);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new IOException("Theme-set sources must match enabled workcells exactly; missing="
                    + missing + ", unexpected=" + unexpected);
        }
    }

    static byte[] appendThemeSet(
            byte[] content,
            String themeKey,
            String structureResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structureResource);
        }
        JsonObject structure = parsed.getAsJsonObject();
        JsonArray themeSets;
        if (!structure.has("themeSets")) {
            themeSets = new JsonArray();
            structure.add("themeSets", themeSets);
        } else if (!structure.get("themeSets").isJsonArray()) {
            throw new IOException("Jigsaw structure has an invalid themeSets value: " + structureResource);
        } else {
            themeSets = structure.getAsJsonArray("themeSets");
        }
        for (JsonElement element : themeSets) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("key")
                    && themeKey.equals(element.getAsJsonObject().get("key").getAsString())) {
                throw new IOException("Jigsaw structure already declares theme '" + themeKey + "'");
            }
        }
        JsonObject theme = new JsonObject();
        theme.addProperty("key", themeKey);
        theme.addProperty("weight", 1);
        themeSets.add(theme);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] duplicatePieceForTheme(
            byte[] content,
            String targetPieceKey,
            String themeKey,
            String sourcePieceResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw piece is not a JSON object: " + sourcePieceResource);
        }
        JsonObject piece = parsed.getAsJsonObject().deepCopy();
        piece.addProperty("object", targetPieceKey);
        JsonArray themes = new JsonArray();
        themes.add(themeKey);
        piece.add("themes", themes);
        return (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] duplicatePieceForVariant(
            byte[] content,
            String targetPieceKey,
            String sourcePieceResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw piece is not a JSON object: " + sourcePieceResource);
        }
        JsonObject piece = parsed.getAsJsonObject().deepCopy();
        piece.addProperty("object", targetPieceKey);
        return (GSON.toJson(piece) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static PoolMembershipDuplication duplicatePoolMemberships(
            byte[] content,
            Map<String, String> targetPieceKeysBySource,
            String poolResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolResource);
        }
        JsonObject pool = parsed.getAsJsonObject();
        JsonArray pieces = pool.getAsJsonArray("pieces");
        if (pieces == null) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolResource);
        }
        JsonArray expanded = new JsonArray();
        int duplicatedEntries = 0;
        for (JsonElement element : pieces) {
            expanded.add(element.deepCopy());
            if (!element.isJsonObject() || !element.getAsJsonObject().has("piece")) {
                continue;
            }
            String sourcePieceKey = element.getAsJsonObject().get("piece").getAsString();
            String targetPieceKey = targetPieceKeysBySource.get(sourcePieceKey);
            if (targetPieceKey == null) {
                continue;
            }
            JsonObject duplicate = element.getAsJsonObject().deepCopy();
            duplicate.addProperty("piece", targetPieceKey);
            expanded.add(duplicate);
            duplicatedEntries++;
        }
        if (duplicatedEntries == 0) {
            return new PoolMembershipDuplication(content, 0);
        }
        pool.add("pieces", expanded);
        return new PoolMembershipDuplication(
                (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8),
                duplicatedEntries);
    }

    static List<String> normalizeThemes(List<String> themes) {
        Objects.requireNonNull(themes, "Jigsaw Studio piece themes");
        Set<String> normalized = new LinkedHashSet<>();
        for (String theme : themes) {
            Objects.requireNonNull(theme, "Jigsaw Studio piece theme");
            String key = theme.trim();
            if (key.isEmpty() || !key.equals(theme)) {
                throw new IllegalArgumentException(
                        "Jigsaw Studio piece themes must be non-blank and whitespace-normalized");
            }
            if (!normalized.add(key)) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio piece theme '" + key + "'");
            }
        }
        return List.copyOf(normalized);
    }

    static PoolEntryRemoval removePoolEntries(
            byte[] content,
            String pieceKey,
            String poolResource
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolResource);
        }
        JsonObject pool = parsed.getAsJsonObject();
        JsonArray pieces = pool.getAsJsonArray("pieces");
        if (pieces == null) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolResource);
        }
        int removed = 0;
        for (int index = pieces.size() - 1; index >= 0; index--) {
            JsonElement entry = pieces.get(index);
            if (entry.isJsonObject()
                    && entry.getAsJsonObject().has("piece")
                    && pieceKey.equals(entry.getAsJsonObject().get("piece").getAsString())) {
                pieces.remove(index);
                removed++;
            }
        }
        if (removed == 0) {
            return new PoolEntryRemoval(content, 0);
        }
        return new PoolEntryRemoval(
                (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8),
                removed);
    }

    static String resourceKey(String relativePath, String prefix, String suffix) {
        return relativePath.substring(prefix.length(), relativePath.length() - suffix.length());
    }

    static byte[] addPoolEntry(
            byte[] content,
            String pieceKey,
            int weight,
            Path poolPath
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolPath);
        }
        JsonObject pool = parsed.getAsJsonObject();
        if (!pool.has("pieces") || !pool.get("pieces").isJsonArray()) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolPath);
        }
        JsonArray pieces = pool.getAsJsonArray("pieces");
        for (JsonElement element : pieces) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("piece")
                    && pieceKey.equals(element.getAsJsonObject().get("piece").getAsString())) {
                throw new IOException("Pool already references piece '" + pieceKey + "'");
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("piece", pieceKey);
        entry.addProperty("weight", weight);
        pieces.add(entry);
        return (GSON.toJson(pool) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim();
        if (normalized.equalsIgnoreCase("none")) {
            return "";
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Jigsaw connector channels cannot exceed 128 characters");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) {
                throw new IllegalArgumentException("Jigsaw connector channels cannot contain whitespace");
            }
        }
        return normalized;
    }

    record PoolEntryRemoval(byte[] content, int removedEntries) {
        PoolEntryRemoval {
            Objects.requireNonNull(content, "Jigsaw Studio pool entry removal content");
            if (removedEntries < 0) {
                throw new IllegalArgumentException("Removed jigsaw pool entry count cannot be negative");
            }
        }
    }

    record PoolMembershipDuplication(byte[] content, int duplicatedEntries) {
        PoolMembershipDuplication {
            Objects.requireNonNull(content, "Jigsaw Studio duplicated pool content");
            if (duplicatedEntries < 0) {
                throw new IllegalArgumentException("Duplicated pool membership count cannot be negative");
            }
        }
    }
}
