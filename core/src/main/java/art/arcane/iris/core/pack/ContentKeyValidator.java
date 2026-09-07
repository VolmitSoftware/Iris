/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.core.pack;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.object.IrisObjectIO;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class ContentKeyValidator {
    private static final int MAX_SUGGESTION_SCANS = 4096;
    private static final int MAX_OBJECT_PALETTE_SCANS = 20_000;
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String STRICT_PROPERTY = "iris.strictContent";
    private static final AtomicBoolean WARNED_EMPTY_REGISTRIES = new AtomicBoolean();

    private ContentKeyValidator() {
    }

    public enum ContentRegistry {
        BLOCK,
        ITEM,
        ENTITY;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record ContentKeyError(String key, ContentRegistry registry, boolean namespaceLoaded, String suggestion) {
        public String message() {
            StringBuilder sb = new StringBuilder(64);
            sb.append("Unknown ").append(registry.label()).append(" key '").append(key).append('\'');
            sb.append(" (missing from the ").append(registry.label()).append(" registry");
            if (!namespaceLoaded) {
                sb.append("; namespace '").append(namespaceOf(key)).append("' is not loaded on this server");
            }
            sb.append(')');
            if (suggestion != null) {
                sb.append(" - did you mean '").append(suggestion).append("'?");
            }
            return sb.toString();
        }
    }

    public static List<ContentKeyError> validate(PlatformRegistries registries,
                                                 Collection<String> referencedBlocks,
                                                 Collection<String> referencedItems,
                                                 Collection<String> referencedEntities) {
        if (registries == null) {
            return List.of();
        }

        List<String> blockKeys = normalizeKeyList(registries.blockTypeKeys());
        List<String> itemKeys = normalizeKeyList(registries.itemKeys());
        List<String> entityKeys = normalizeKeyList(registries.entityKeys());

        Set<String> knownBlocks = new HashSet<>(blockKeys);
        Set<String> knownItems = new HashSet<>(itemKeys);
        Set<String> knownEntities = new HashSet<>(entityKeys);

        Set<String> loadedNamespaces = new HashSet<>();
        addNamespaces(blockKeys, loadedNamespaces);
        addNamespaces(itemKeys, loadedNamespaces);
        addNamespaces(entityKeys, loadedNamespaces);

        Map<String, ContentKeyError> errors = new LinkedHashMap<>();
        validateCategory(referencedBlocks, ContentRegistry.BLOCK, knownBlocks, blockKeys, loadedNamespaces, errors);
        validateCategory(referencedItems, ContentRegistry.ITEM, knownItems, itemKeys, loadedNamespaces, errors);
        validateCategory(referencedEntities, ContentRegistry.ENTITY, knownEntities, entityKeys, loadedNamespaces, errors);
        return List.copyOf(errors.values());
    }

    /**
     * Validates the {@code [prop=value,...]} section of every referenced block state against the platform's declared
     * properties for that block.
     * <p>
     * Only blocks the platform declares at least one property for are checked: a block key that is absent from
     * {@link PlatformRegistries#blockStateProperties()}, or present with an empty list, carries no property knowledge
     * (pack-registered blocks and mod provider blocks land there) and a typo cannot be told from a valid custom
     * property.
     */
    public static List<String> validateBlockStateProperties(PlatformRegistries registries,
                                                            Collection<String> referencedBlockStates) {
        if (registries == null || referencedBlockStates == null || referencedBlockStates.isEmpty()) {
            return List.of();
        }
        Map<String, List<PlatformBlockProperty>> declared = registries.blockStateProperties();
        if (declared == null || declared.isEmpty()) {
            return List.of();
        }

        List<String> messages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : referencedBlockStates) {
            String properties = propertySectionOf(raw);
            if (properties == null || properties.isBlank()) {
                continue;
            }
            String base = normalizeKey(raw);
            if (base == null) {
                continue;
            }
            List<PlatformBlockProperty> known = declared.get(base);
            if (known == null || known.isEmpty()) {
                continue;
            }
            for (String pair : properties.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String name = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String message = describeProperty(base, name, value, known);
                if (message != null && seen.add(message)) {
                    messages.add(message);
                }
            }
        }
        return List.copyOf(messages);
    }

    private static String describeProperty(String base, String name, String value, List<PlatformBlockProperty> known) {
        PlatformBlockProperty match = null;
        List<String> names = new ArrayList<>(known.size());
        for (PlatformBlockProperty property : known) {
            names.add(property.name());
            if (property.name().equalsIgnoreCase(name)) {
                match = property;
            }
        }

        if (match == null) {
            String suggestion = nearestKey(name, names);
            StringBuilder sb = new StringBuilder(96);
            sb.append("Block '").append(base).append("' has no property '").append(name).append('\'');
            if (suggestion != null) {
                sb.append(" - did you mean '").append(suggestion).append("'?");
            } else {
                sb.append(" (has: ").append(String.join(", ", names)).append(')');
            }
            return sb.toString();
        }

        List<Object> allowed = match.allowedValues();
        if (allowed == null || allowed.isEmpty()) {
            // The adapters disagree on how a numeric property describes itself: modded enumerates every legal
            // integer into allowedValues, Bukkit leaves that empty and publishes bounds instead. Validating the
            // range here is what makes 'minecraft:water[level=99]' report the same thing on both platforms.
            return describeNumericRange(base, match, value);
        }
        List<String> allowedText = new ArrayList<>(allowed.size());
        for (Object candidate : allowed) {
            String text = String.valueOf(candidate);
            allowedText.add(text);
            if (text.equalsIgnoreCase(value)) {
                return null;
            }
        }
        return "Block '" + base + "' property '" + match.name() + "' does not accept '" + value
                + "' (allowed: " + String.join(", ", allowedText) + ")";
    }

    /**
     * Range check for a property that declares bounds but cannot enumerate its values. A value that is not a number
     * at all is reported too - a numeric property never accepts one.
     */
    private static String describeNumericRange(String base, PlatformBlockProperty match, String value) {
        if (!match.hasNumericRange()) {
            return null;
        }
        PlatformNumericRange range = match.numericRange();
        String bounds = "expected "
                + (range.exclusiveMinimum() ? "greater than " : "at least ") + describeBound(range.minimum())
                + " and " + (range.exclusiveMaximum() ? "less than " : "at most ") + describeBound(range.maximum());

        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return "Block '" + base + "' property '" + match.name() + "' is numeric and does not accept '" + value
                    + "' (" + bounds + ")";
        }

        boolean belowMinimum = range.exclusiveMinimum() ? parsed <= range.minimum() : parsed < range.minimum();
        boolean aboveMaximum = range.exclusiveMaximum() ? parsed >= range.maximum() : parsed > range.maximum();
        if (!belowMinimum && !aboveMaximum) {
            return null;
        }
        return "Block '" + base + "' property '" + match.name() + "' does not accept '" + value + "' (" + bounds + ")";
    }

    private static String describeBound(double bound) {
        return bound == Math.rint(bound) && !Double.isInfinite(bound)
                ? String.valueOf((long) bound)
                : String.valueOf(bound);
    }

    static String propertySectionOf(String raw) {
        if (raw == null) {
            return null;
        }
        int open = raw.indexOf('[');
        if (open < 0) {
            return null;
        }
        int close = raw.indexOf(']', open);
        return close < 0 ? raw.substring(open + 1) : raw.substring(open + 1, close);
    }

    /**
     * Whether unresolved pack content keys are blocking errors instead of warnings. Enabled by
     * {@code -Diris.strictContent} or {@code general.strictContentKeys} in iris.json; the system property wins.
     */
    public static boolean strictContent() {
        String property = System.getProperty(STRICT_PROPERTY);
        if (property != null) {
            return property.isEmpty() || Boolean.parseBoolean(property);
        }
        try {
            return IrisPlatforms.isBound() && IrisSettings.get().getGeneral().isStrictContentKeys();
        } catch (Throwable e) {
            return false;
        }
    }

    static String namespaceOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? DEFAULT_NAMESPACE : key.substring(0, colon);
    }

    private static void validateCategory(Collection<String> referenced,
                                         ContentRegistry registry,
                                         Set<String> known,
                                         List<String> candidatePool,
                                         Set<String> loadedNamespaces,
                                         Map<String, ContentKeyError> errors) {
        if (referenced == null || referenced.isEmpty()) {
            return;
        }
        for (String raw : referenced) {
            String normalized = normalizeKey(raw);
            if (normalized == null || known.contains(normalized)) {
                continue;
            }
            String dedupKey = registry.name() + '|' + normalized;
            if (errors.containsKey(dedupKey)) {
                continue;
            }
            boolean namespaceLoaded = loadedNamespaces.contains(namespaceOf(normalized));
            String suggestion = nearestKey(normalized, candidatePool);
            errors.put(dedupKey, new ContentKeyError(normalized, registry, namespaceLoaded, suggestion));
        }
    }

    private static List<String> normalizeKeyList(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = key.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                out.add(value.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ':' + value : value);
            }
        }
        return out;
    }

    private static void addNamespaces(List<String> keys, Set<String> namespaces) {
        for (String key : keys) {
            namespaces.add(namespaceOf(key));
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int bracket = value.indexOf('[');
        if (bracket >= 0) {
            value = value.substring(0, bracket).trim();
        }
        if (value.isEmpty()) {
            return null;
        }
        return value.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ':' + value : value;
    }

    private static String nearestKey(String key, List<String> candidatePool) {
        String path = pathOf(key);
        int maxDistance = Math.max(2, path.length() / 3);
        int bestDistance = Integer.MAX_VALUE;
        String best = null;
        int scans = 0;

        for (String candidate : candidatePool) {
            String candidatePath = pathOf(candidate);
            if (Math.abs(candidatePath.length() - path.length()) > maxDistance) {
                continue;
            }
            if (scans++ >= MAX_SUGGESTION_SCANS) {
                break;
            }
            int distance = boundedLevenshtein(path, candidatePath, maxDistance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
                if (distance <= 1) {
                    break;
                }
            }
        }

        return bestDistance <= maxDistance ? best : null;
    }

    private static String pathOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    private static int boundedLevenshtein(String a, String b, int max) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > max) {
            return max + 1;
        }
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) {
                    rowMin = curr[j];
                }
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb];
    }

    /**
     * Unresolved-key and block-property issues for a pack, split by how hard they are allowed to bite.
     *
     * @param strict   issues the caller may promote to blocking errors under {@link #strictContent()}
     * @param advisory issues that stay warnings whatever the mode - see {@link #collectContentKeyIssues(File)}
     */
    record ContentKeyIssues(List<String> strict, List<String> advisory) {
        static ContentKeyIssues none() {
            return new ContentKeyIssues(List.of(), List.of());
        }
    }

    /**
     * Collects unresolved-key and block-property issues for a pack. Messages come back in report order; the caller
     * decides whether the {@link ContentKeyIssues#strict()} half is warnings or blocking errors (see
     * {@link #strictContent()}).
     * <p>
     * Findings whose only source is an object palette are {@link ContentKeyIssues#advisory()} and never blocking. A
     * {@code .iob} palette is a build artifact, not authored text: it carries whatever key the block had when the
     * object was saved, so a decade-old community object legitimately names {@code minecraft:grass} or
     * {@code minecraft:grass_path}. Those resolve at generation time through the Bukkit compatibility rewrite table
     * and are already reported as warnings; refusing to load the pack over them would reject most of the object
     * library over keys the author cannot edit and the engine handles.
     */
    static ContentKeyIssues collectContentKeyIssues(File packFolder) {
        List<String> strict = new ArrayList<>();
        List<String> advisory = new ArrayList<>();
        try {
            if (!IrisPlatforms.isBound()) {
                return ContentKeyIssues.none();
            }
            PlatformRegistries registries = IrisPlatforms.get().registries();
            if (registries == null) {
                return ContentKeyIssues.none();
            }
            List<String> blockKeys = registries.blockTypeKeys();
            List<String> itemKeys = registries.itemKeys();
            List<String> entityKeys = registries.entityKeys();
            if (blockKeys == null || blockKeys.isEmpty() || itemKeys == null || itemKeys.isEmpty() || entityKeys == null || entityKeys.isEmpty()) {
                if (WARNED_EMPTY_REGISTRIES.compareAndSet(false, true)) {
                    IrisLogging.warn("Content-key validation skipped: platform registries are empty (blocks="
                            + size(blockKeys) + " items=" + size(itemKeys) + " entities=" + size(entityKeys) + ")");
                }
                return ContentKeyIssues.none();
            }

            ReferencedContentKeys referenced = collectReferencedContentKeys(packFolder);
            List<ContentKeyValidator.ContentKeyError> errors = ContentKeyValidator.validate(
                    registries, referenced.blocks(), referenced.items(), referenced.entities());
            for (ContentKeyValidator.ContentKeyError error : errors) {
                if (referenced.paletteOnlyBlocks().contains(error.key())) {
                    advisory.add(error.message());
                } else {
                    strict.add(error.message());
                }
            }
            strict.addAll(validateBlockStateProperties(registries, referenced.blockStates()));
        } catch (Throwable e) {
            IrisLogging.reportError("PackValidator content-key validation failed for pack '" + packFolder.getName() + "'", e);
        }
        return new ContentKeyIssues(List.copyOf(strict), List.copyOf(advisory));
    }

    private static int size(List<String> keys) {
        return keys == null ? 0 : keys.size();
    }

    private static ReferencedContentKeys collectReferencedContentKeys(File packFolder) {
        Set<String> blocks = new HashSet<>();
        Set<String> items = new HashSet<>();
        Set<String> entities = new HashSet<>();
        Set<String> blockStates = new HashSet<>();
        Set<String> customBlocks = deriveRegistrantKeys(new File(packFolder, "blocks"));

        try (Stream<Path> stream = Files.walk(packFolder.toPath())) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(PackValidationIo::isScannableJsonPath)
                    .toList();
            for (Path path : files) {
                String relative = packFolder.toPath().relativize(path).toString().replace(File.separatorChar, '/');
                boolean inLoot = relative.startsWith("loot/");
                boolean inEntities = relative.startsWith("entities/");
                JSONObject json;
                try {
                    json = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
                } catch (Throwable ignored) {
                    continue;
                }
                collectFromNode(json, blocks, inLoot ? items : null, inEntities ? entities : null, customBlocks, blockStates);
            }
        } catch (Throwable e) {
            IrisLogging.reportError("PackValidator failed to walk pack for content-key extraction", e);
        }

        Set<String> jsonBlocks = Set.copyOf(blocks);
        collectObjectPaletteKeys(new File(packFolder, PackValidator.OBJECTS_FOLDER), blocks, blockStates, customBlocks);

        // Keys the JSON scan never saw came only out of an .iob palette. Those stay advisory - the pack author has
        // no text to fix.
        Set<String> paletteOnly = new HashSet<>(blocks);
        paletteOnly.removeAll(jsonBlocks);

        return new ReferencedContentKeys(blocks, items, entities, blockStates, paletteOnly);
    }

    /**
     * Adds the V2 {@code .iob} palette keys under {@code objects/} to the referenced block keys. Object palettes are
     * the largest source of block keys in a pack and are invisible to the JSON scan.
     */
    private static void collectObjectPaletteKeys(File objectsFolder, Set<String> blocks, Set<String> blockStates, Set<String> customBlocks) {
        if (!objectsFolder.isDirectory()) {
            return;
        }
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(objectsFolder.toPath())) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".iob"))
                    .toList();
            for (Path path : files) {
                if (scanned++ >= MAX_OBJECT_PALETTE_SCANS) {
                    IrisLogging.debug("Content-key validation stopped object palette scan at " + MAX_OBJECT_PALETTE_SCANS
                            + " objects (" + files.size() + " present)");
                    break;
                }
                for (String key : IrisObjectIO.readPaletteKeys(path.toFile())) {
                    addBlockRef(key, blocks, customBlocks, blockStates);
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError("PackValidator failed to scan object palettes for content-key extraction", e);
        }
    }

    private static void collectFromNode(Object node, Set<String> blocks, Set<String> items, Set<String> entities, Set<String> customBlocks, Set<String> blockStates) {
        if (node instanceof JSONObject obj) {
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                if (value instanceof String str) {
                    if ("block".equals(key)) {
                        addBlockRef(str, blocks, customBlocks, blockStates);
                    } else if (items != null && "type".equals(key)) {
                        addSimpleRef(str, items);
                    } else if (entities != null && "type".equals(key)) {
                        addSimpleRef(str, entities);
                    }
                } else {
                    collectFromNode(value, blocks, items, entities, customBlocks, blockStates);
                }
            }
        } else if (node instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                collectFromNode(arr.get(i), blocks, items, entities, customBlocks, blockStates);
            }
        }
    }

    private static void addBlockRef(String raw, Set<String> blocks, Set<String> customBlocks, Set<String> blockStates) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int bracket = value.indexOf('[');
        String base = bracket >= 0 ? value.substring(0, bracket).trim() : value;
        if (base.isEmpty() || customBlocks.contains(base)) {
            return;
        }
        blocks.add(base);
        if (bracket >= 0 && blockStates != null) {
            blockStates.add(value);
        }
    }

    private static void addSimpleRef(String raw, Set<String> target) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.isEmpty()) {
            target.add(value);
        }
    }

    private static Set<String> deriveRegistrantKeys(File folder) {
        Set<String> keys = new HashSet<>();
        if (!folder.isDirectory()) {
            return keys;
        }
        for (File file : PackValidationIo.listJsonRecursive(folder)) {
            String key = PackValidationIo.deriveKey(folder, file);
            if (key != null && !key.isBlank()) {
                keys.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    static Set<String> deriveRegistrantKeysExact(File folder) {
        Set<String> keys = new HashSet<>();
        if (!folder.isDirectory()) {
            return keys;
        }
        for (File file : PackValidationIo.listJsonRecursive(folder)) {
            String key = PackValidationIo.deriveKey(folder, file);
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    static Set<String> deriveObjectKeysExact(File folder) {
        Set<String> keys = new HashSet<>();
        if (!folder.isDirectory()) {
            return keys;
        }
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".iob"))
                    .forEach(path -> {
                        Path relative = folder.toPath().relativize(path);
                        String key = relative.toString().replace(File.separatorChar, '/');
                        keys.add(key.substring(0, key.length() - ".iob".length()));
                    });
        } catch (IOException ignored) {
        }
        return keys;
    }

    /**
     * @param paletteOnlyBlocks the subset of {@code blocks} that no pack JSON names - contributed purely by an
     *                          object palette, so findings about them cannot be blocking
     */
    private record ReferencedContentKeys(Set<String> blocks, Set<String> items, Set<String> entities,
                                         Set<String> blockStates, Set<String> paletteOnlyBlocks) {
    }
}
