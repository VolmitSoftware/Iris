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

package art.arcane.iris.core.pack;

import art.arcane.iris.core.pack.PackJsonText.Edit;
import art.arcane.iris.core.pack.PackJsonText.Item;
import art.arcane.iris.core.pack.PackJsonText.Member;
import com.google.gson.JsonParser;
import com.volmit.world.Weighted;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Converts a pack from rarity to weighted list entries, keeping the share every list entry had in its pick:
 * <pre>
 * "landBiomes": [{"biome": "plains", "weight": 3}, ...]        (also seaBiomes, shoreBiomes, caveBiomes)
 * "regions":    [{"region": "temperate", "weight": 2}, ...]     (in the dimension)
 * "children":   [{"biome": "forest", "weight": 2}, {"biome": "plains", "weight": 5}, ...]
 * "carving":    [{"id": "deep", "children": [{"id": "lush", "weight": 2}, {"id": "deep", "weight": 5}]}]
 * </pre>
 * Region, biome and dimension lists were picked with a share of 1 / rarity per entry, so an entry's weight is the least
 * common multiple of the list's rarities divided by its rarity, reduced by the greatest common divisor. Children were
 * picked from rounded slots, the rarer a biome the fewer, alternating from both ends, with the parent among them; the
 * parent is listed among its children wherever it kept the spot, in half slots, since the first and last slot only
 * held half a slot of noise. A carving entry's children pick the same way by the rarity of the entries' biomes. The
 * top-level {@code rarity} of biomes and regions is removed; nested rarities (loot, spawns, objects) stay.
 * <p>
 * Files are edited as text, so formatting elsewhere stays; a rewritten list gets one entry per line. A list that would need
 * a weight above {@link Weighted#MAX_WEIGHT} stops the conversion. A pack with lists already converted and lists not yet
 * converted cannot be told apart from rarity 1 and is refused.
 * <p>
 * {@code PackWeightConverterCli} in the test sources runs it from the command line.
 */
public final class PackWeightConverter {
    private static final String INDENT_STEP = "    ";
    private static final String JSON_EXTENSION = ".json";
    private static final int DEFAULT_RARITY = 1;
    private static final List<String> BIOME_LISTS = List.of("landBiomes", "seaBiomes", "shoreBiomes", "caveBiomes");

    /** The files a conversion changed (relative to the pack) and what it could not carry over exactly. */
    public record Result(List<String> changedFiles, List<String> warnings) {
    }

    /** A pack the converter cannot convert while keeping every pick. */
    public static final class ConversionException extends RuntimeException {
        ConversionException(String message) {
            super(message);
        }
    }

    private record Entry(String key, int weight) {
    }

    private record Option(String key, int rarity) {
    }

    private record CarvingBand(String biome, boolean enabled) {
    }

    private final Path pack;
    private final Map<String, Integer> biomeRarities;
    private final Map<String, Integer> regionRarities;
    private final TreeSet<String> warnings = new TreeSet<>();
    private String currentFile = "";

    private PackWeightConverter(Path pack) throws IOException {
        this.pack = pack;
        this.biomeRarities = rarities("biomes");
        this.regionRarities = rarities("regions");
    }

    public static Result convert(Path pack, boolean dryRun) throws IOException {
        PackWeightConverter converter = new PackWeightConverter(pack);
        converter.refusePartlyConverted();
        Map<Path, String> converted = new LinkedHashMap<>();
        for (String folder : List.of("biomes", "regions", "dimensions")) {
            for (Path file : converter.files(folder)) {
                String text = converter.convertFile(folder, file);
                if (text != null) {
                    converted.put(file, text);
                }
            }
        }

        List<String> changed = new ArrayList<>();
        for (Map.Entry<Path, String> file : converted.entrySet()) {
            changed.add(relative(pack, file.getKey()));
            if (!dryRun) {
                Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8);
            }
        }
        return new Result(changed, List.copyOf(converter.warnings));
    }

    /** The converted text of the file; null when nothing in it changes. */
    private String convertFile(String folder, Path file) throws IOException {
        currentFile = relative(pack, file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String converted = switch (folder) {
            case "biomes" -> convertBiome(keyOf(pack.resolve(folder), file), text);
            case "regions" -> convertRegion(text);
            default -> convertDimension(text);
        };
        if (converted.equals(text)) {
            return null;
        }

        verifyParses(converted);
        return converted;
    }

    private static String relative(Path pack, Path file) {
        return pack.relativize(file).toString().replace('\\', '/');
    }

    private String convertBiome(String key, String text) {
        List<Member> members = rootMembers(text);
        List<Edit> edits = new ArrayList<>();
        Member children = PackJsonText.member(members, "children");
        List<String> childKeys = stringItems(text, children);
        if (childKeys != null) {
            edits.add(replaceList(text, children, "biome", childEntries(key, childKeys)));
        }
        addRarityRemoval(text, members, edits);
        return PackJsonText.apply(text, edits);
    }

    private String convertRegion(String text) {
        List<Member> members = rootMembers(text);
        List<Edit> edits = new ArrayList<>();
        for (String list : BIOME_LISTS) {
            Member member = PackJsonText.member(members, list);
            List<String> keys = stringItems(text, member);
            if (keys != null) {
                edits.add(replaceList(text, member, "biome", listEntries(keys, biomeRarities)));
            }
        }
        addRarityRemoval(text, members, edits);
        return PackJsonText.apply(text, edits);
    }

    private String convertDimension(String text) {
        List<Member> members = rootMembers(text);
        List<Edit> edits = new ArrayList<>();
        Member regions = PackJsonText.member(members, "regions");
        List<String> regionKeys = stringItems(text, regions);
        if (regionKeys != null) {
            edits.add(replaceList(text, regions, "region", listEntries(regionKeys, regionRarities)));
        }

        Member carving = PackJsonText.member(members, "carving");
        if (carving != null && text.charAt(carving.valueStart()) == '[') {
            edits.addAll(convertCarving(text, carving));
        }
        return PackJsonText.apply(text, edits);
    }

    private List<Edit> convertCarving(String text, Member carving) {
        List<List<Member>> bands = new ArrayList<>();
        for (Item item : PackJsonText.items(text, carving.valueStart())) {
            bands.add(text.charAt(item.start()) == '{' ? PackJsonText.members(text, item.start()) : List.of());
        }

        Map<String, CarvingBand> bandsById = new HashMap<>();
        for (List<Member> members : bands) {
            String id = stringValue(text, PackJsonText.member(members, "id")).trim();
            if (!id.isEmpty()) {
                bandsById.put(id, carvingBand(text, members));
            }
        }

        List<Edit> edits = new ArrayList<>();
        for (List<Member> members : bands) {
            Member children = PackJsonText.member(members, "children");
            List<String> childIds = stringItems(text, children);
            if (childIds != null) {
                edits.add(replaceList(text, children, "id", carvingChildEntries(text, members, childIds, bandsById)));
            }
        }
        return edits;
    }

    private CarvingBand carvingBand(String text, List<Member> members) {
        Member enabled = PackJsonText.member(members, "enabled");
        boolean isEnabled = enabled == null || !text.startsWith("false", enabled.valueStart());
        return new CarvingBand(stringValue(text, PackJsonText.member(members, "biome")).trim(), isEnabled);
    }

    private List<Entry> carvingChildEntries(String text, List<Member> members, List<String> childIds, Map<String, CarvingBand> bandsById) {
        String parentId = stringValue(text, PackJsonText.member(members, "id")).trim();
        if (parentId.isEmpty()) {
            throw new ConversionException(currentFile + ": a carving entry with children needs an id, so that it can list itself among them");
        }

        List<Option> options = new ArrayList<>();
        for (String listed : childIds) {
            String childId = listed.trim();
            CarvingBand child = bandsById.get(childId);
            if (child == null || !child.enabled() || !biomeRarities.containsKey(child.biome())) {
                warnings.add(currentFile + ": the carving child '" + childId + "' is not an enabled entry with a biome of this pack; Iris skipped it");
                continue;
            }
            options.add(new Option(childId, biomeRarities.get(child.biome())));
        }

        CarvingBand parent = bandsById.get(parentId);
        options.add(new Option(parentId, biomeRarities.getOrDefault(parent.biome(), DEFAULT_RARITY)));
        return halfSlotRuns(oldChildSlots(options));
    }

    private List<Entry> childEntries(String parent, List<String> children) {
        List<Option> options = new ArrayList<>();
        for (String child : children) {
            if (!biomeRarities.containsKey(child)) {
                warnings.add(currentFile + ": the child '" + child + "' has no file in this pack; Iris skipped it");
                continue;
            }
            options.add(new Option(child, biomeRarities.get(child)));
        }
        options.add(new Option(parent, biomeRarities.getOrDefault(parent, DEFAULT_RARITY)));
        return halfSlotRuns(oldChildSlots(options));
    }

    private List<Entry> listEntries(List<String> keys, Map<String, Integer> rarities) {
        List<Integer> loadedRarities = new ArrayList<>();
        for (String key : keys) {
            if (rarities.containsKey(key)) {
                loadedRarities.add(rarities.get(key));
            } else {
                warnings.add(currentFile + ": '" + key + "' is listed but has no file in this pack; Iris skips it");
            }
        }

        List<Integer> weights = shareWeights(loadedRarities);
        List<Entry> entries = new ArrayList<>();
        int loaded = 0;
        for (String key : keys) {
            entries.add(new Entry(key, rarities.containsKey(key) ? weights.get(loaded++) : Weighted.MIN_WEIGHT));
        }
        return entries;
    }

    /** Weights proportional to 1 / rarity: the least common multiple of the rarities divided by each, reduced. */
    private List<Integer> shareWeights(List<Integer> rarities) {
        if (rarities.size() <= 1) {
            return rarities.stream().map(rarity -> Weighted.MIN_WEIGHT).toList();
        }

        long commonMultiple = 1;
        for (int rarity : rarities) {
            commonMultiple = commonMultiple / gcd(commonMultiple, rarity) * rarity;
            if (commonMultiple > Integer.MAX_VALUE) {
                throw new ConversionException(currentFile + ": the rarities " + rarities + " share no weights up to " + Weighted.MAX_WEIGHT);
            }
        }

        long divisor = 0;
        for (int rarity : rarities) {
            divisor = gcd(divisor, commonMultiple / rarity);
        }

        List<Integer> weights = new ArrayList<>();
        for (int rarity : rarities) {
            long weight = commonMultiple / rarity / divisor;
            if (weight > Weighted.MAX_WEIGHT) {
                throw new ConversionException(currentFile + ": the rarities " + rarities + " need a weight of " + weight
                        + ", above the maximum of " + Weighted.MAX_WEIGHT + "; list them with weights by hand");
            }
            weights.add((int) weight);
        }
        return weights;
    }

    /** The old child picker's slots: rarer options get fewer, and each option adds its slots to alternating ends. */
    private static List<String> oldChildSlots(List<Option> options) {
        int most = options.stream().mapToInt(option -> Math.max(DEFAULT_RARITY, option.rarity())).max().orElse(DEFAULT_RARITY) + 1;
        List<String> slots = new ArrayList<>();
        boolean atEnd = false;
        for (Option option : options) {
            for (int copy = 0; copy < most - Math.max(DEFAULT_RARITY, option.rarity()); copy++) {
                atEnd = !atEnd;
                if (atEnd) {
                    slots.add(option.key());
                } else {
                    slots.addFirst(option.key());
                }
            }
        }
        return slots;
    }

    /** The old picker rounded the noise onto the slots, so the first and last slot had half a slot of noise. */
    private List<Entry> halfSlotRuns(List<String> slots) {
        List<String> keys = new ArrayList<>();
        List<Integer> halves = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            int slotHalves = index == 0 || index == slots.size() - 1 ? 1 : 2;
            if (!keys.isEmpty() && keys.getLast().equals(slots.get(index))) {
                halves.set(halves.size() - 1, halves.getLast() + slotHalves);
            } else {
                keys.add(slots.get(index));
                halves.add(slotHalves);
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (int run = 0; run < keys.size(); run++) {
            if (halves.get(run) > Weighted.MAX_WEIGHT) {
                throw new ConversionException(currentFile + ": the children of one biome need a weight of " + halves.get(run)
                        + ", above the maximum of " + Weighted.MAX_WEIGHT + "; list them with weights by hand");
            }
            entries.add(new Entry(keys.get(run), halves.get(run)));
        }
        return entries;
    }

    private Edit replaceList(String text, Member list, String field, List<Entry> entries) {
        String indent = PackJsonText.indentAt(text, list.keyStart());
        StringBuilder replacement = new StringBuilder("[\n");
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            replacement.append(indent).append(INDENT_STEP)
                    .append("{\"").append(field).append("\": \"").append(entry.key()).append("\", \"weight\": ").append(entry.weight()).append('}')
                    .append(index < entries.size() - 1 ? ",\n" : "\n");
        }
        replacement.append(indent).append(']');
        return new Edit(list.valueStart(), list.valueEnd(), replacement.toString());
    }

    private void addRarityRemoval(String text, List<Member> members, List<Edit> edits) {
        Member rarity = PackJsonText.member(members, "rarity");
        if (rarity != null) {
            edits.add(PackJsonText.removal(text, rarity));
        }
    }

    /** The strings of a list, or null when the list is missing, empty or already holds entries. */
    private List<String> stringItems(String text, Member list) {
        if (list == null || text.charAt(list.valueStart()) != '[') {
            return null;
        }

        List<Item> items = PackJsonText.items(text, list.valueStart());
        if (items.isEmpty() || items.stream().anyMatch(item -> text.charAt(item.start()) != '"')) {
            return null;
        }
        return items.stream().map(item -> plainString(text.substring(item.start() + 1, item.end() - 1))).toList();
    }

    private String stringValue(String text, Member member) {
        if (member == null || text.charAt(member.valueStart()) != '"') {
            return "";
        }
        return plainString(text.substring(member.valueStart() + 1, member.valueEnd() - 1));
    }

    private String plainString(String content) {
        if (content.indexOf('\\') >= 0) {
            throw new ConversionException(currentFile + ": the key \"" + content + "\" holds an escape; rewrite it by hand");
        }
        return content;
    }

    private List<Member> rootMembers(String text) {
        int start = PackJsonText.skipBlank(text, 0);
        if (start >= text.length() || text.charAt(start) != '{') {
            throw new ConversionException(currentFile + ": the file is not a JSON object");
        }

        try {
            return PackJsonText.members(text, start);
        } catch (IllegalArgumentException unreadable) {
            throw new ConversionException(currentFile + ": " + unreadable.getMessage());
        }
    }

    private void verifyParses(String converted) {
        try {
            JsonParser.parseString(converted);
        } catch (RuntimeException broken) {
            throw new ConversionException(currentFile + ": the converted file does not parse: " + broken.getMessage());
        }
    }

    private Map<String, Integer> rarities(String folder) throws IOException {
        Map<String, Integer> rarities = new HashMap<>();
        for (Path file : files(folder)) {
            currentFile = relative(pack, file);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Member rarity = PackJsonText.member(rootMembers(text), "rarity");
            rarities.put(keyOf(pack.resolve(folder), file), rarity == null ? DEFAULT_RARITY : rarityOf(text, rarity));
        }
        return rarities;
    }

    private int rarityOf(String text, Member rarity) {
        try {
            return (int) Math.round(Double.parseDouble(text.substring(rarity.valueStart(), rarity.valueEnd())));
        } catch (NumberFormatException notANumber) {
            throw new ConversionException(currentFile + ": the rarity is not a number");
        }
    }

    private void refusePartlyConverted() throws IOException {
        boolean convertedLists = false;
        boolean legacyLists = false;
        for (String folder : List.of("biomes", "regions", "dimensions")) {
            for (Path file : files(folder)) {
                currentFile = relative(pack, file);
                String text = Files.readString(file, StandardCharsets.UTF_8);
                List<Member> members = rootMembers(text);
                for (String list : listKeys(folder)) {
                    Member member = PackJsonText.member(members, list);
                    if (member == null || text.charAt(member.valueStart()) != '[') {
                        continue;
                    }
                    List<Item> items = PackJsonText.items(text, member.valueStart());
                    boolean hasStrings = items.stream().anyMatch(item -> text.charAt(item.start()) == '"');
                    boolean hasEntries = items.stream().anyMatch(item -> text.charAt(item.start()) == '{');
                    legacyLists |= hasStrings;
                    convertedLists |= hasEntries;
                }
            }
        }

        if (convertedLists && legacyLists) {
            throw new ConversionException("some lists are weighted entries already and some are not; the rarities of the converted"
                    + " files are gone, so restore the pack and convert it in one run");
        }
    }

    private static List<String> listKeys(String folder) {
        return switch (folder) {
            case "biomes" -> List.of("children");
            case "regions" -> BIOME_LISTS;
            default -> List.of("regions");
        };
    }

    private List<Path> files(String folder) throws IOException {
        Path root = pack.resolve(folder);
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(JSON_EXTENSION)).sorted().toList();
        }
    }

    private static String keyOf(Path folder, Path file) {
        String relative = folder.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - JSON_EXTENSION.length());
    }

    private static long gcd(long first, long second) {
        return second == 0 ? first : gcd(second, first % second);
    }
}
