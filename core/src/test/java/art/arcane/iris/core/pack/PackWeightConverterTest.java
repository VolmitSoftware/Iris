package art.arcane.iris.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.world.Weighted;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PackWeightConverterTest {
    private static final String FIXTURES = "/art/arcane/iris/core/modules/";
    private static final List<String> FIXTURE_PACKS = List.of("pack-before-the-port", "pack-before-loot-and-spawn", "pack-terrain");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void listsGetTheWeightsThatKeepEachEntrysShareOfOneOverItsRarity() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/plains.json", "{\"name\": \"Plains\", \"rarity\": 2}");
        write(pack, "biomes/forest.json", "{\"name\": \"Forest\", \"rarity\": 3}");
        write(pack, "biomes/ocean.json", "{\"name\": \"Ocean\"}");
        write(pack, "regions/temperate.json", """
                {
                    "name": "Temperate",
                    "rarity": 4,
                    "landBiomes": ["plains", "forest", "gone"],
                    "seaBiomes": ["ocean"]
                }
                """);

        PackWeightConverter.Result result = PackWeightConverter.convert(pack, false);

        assertEquals("""
                {
                    "name": "Temperate",
                    "landBiomes": [
                        {"biome": "plains", "weight": 3},
                        {"biome": "forest", "weight": 2},
                        {"biome": "gone", "weight": 1}
                    ],
                    "seaBiomes": [
                        {"biome": "ocean", "weight": 1}
                    ]
                }
                """, read(pack, "regions/temperate.json"));
        assertEquals(List.of("biomes/forest.json", "biomes/plains.json", "regions/temperate.json"), result.changedFiles());
        assertEquals("{\"name\": \"Plains\"}", read(pack, "biomes/plains.json"));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().contains("'gone'"));
    }

    @Test
    public void theDimensionsRegionsAreConvertedLikeAnyList() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "regions/hot.json", "{\"rarity\": 3}");
        write(pack, "regions/mushroom.json", "{\"rarity\": 15}");
        write(pack, "regions/swamp.json", "{\"rarity\": 2}");
        write(pack, "regions/terralost.json", "{\"rarity\": 5}");
        write(pack, "dimensions/overworld.json", "{\"regions\": [\"hot\", \"mushroom\", \"swamp\", \"terralost\"]}");

        PackWeightConverter.convert(pack, false);

        assertEquals(List.of(10, 2, 15, 6), weightsOf(pack, "dimensions/overworld.json", "regions"));
    }

    @Test
    public void aListThatNeedsAWeightAboveTheMaximumStopsTheConversion() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/a.json", "{\"rarity\": 512}");
        write(pack, "biomes/b.json", "{\"rarity\": 511}");
        write(pack, "biomes/c.json", "{\"rarity\": 509}");
        write(pack, "regions/wide.json", "{\"landBiomes\": [\"a\", \"b\", \"c\"]}");

        PackWeightConverter.ConversionException refusal =
                assertThrows(PackWeightConverter.ConversionException.class, () -> PackWeightConverter.convert(pack, false));

        assertTrue(refusal.getMessage(), refusal.getMessage().startsWith("regions/wide.json"));
        assertTrue(refusal.getMessage(), refusal.getMessage().contains("above the maximum of " + Weighted.MAX_WEIGHT));
        assertEquals("{\"landBiomes\": [\"a\", \"b\", \"c\"]}", read(pack, "regions/wide.json"));
        assertEquals("{\"rarity\": 512}", read(pack, "biomes/a.json"));
    }

    @Test
    public void childrenKeepTheirPicksThroughHalfSlotsWithTheParentListedAmongThem() throws IOException {
        List<List<Integer>> scenarios = List.of(List.of(1, 1, 3, 5, 2), List.of(2, 7, 2, 2, 6, 1), List.of(4, 2), List.of(3, 1), List.of(1, 1, 1));
        for (List<Integer> rarities : scenarios) {
            Path pack = folder.newFolder().toPath();
            List<String> childKeys = new ArrayList<>();
            for (int child = 1; child < rarities.size(); child++) {
                write(pack, "biomes/child" + child + ".json", "{\"rarity\": " + rarities.get(child) + "}");
                childKeys.add("\"child" + child + "\"");
            }
            write(pack, "biomes/root.json", "{\"rarity\": " + rarities.getFirst() + ", \"children\": [" + String.join(", ", childKeys) + "]}");

            PackWeightConverter.convert(pack, false);

            List<Weighted<String>> converted = entriesOf(pack, "biomes/root.json", "children", "biome");
            List<String> oldSlots = oldSlots(rarities);
            for (int step = 0; step <= 1000; step++) {
                double noise = step / 1000D;
                String oldPick = oldSlots.get((int) Math.round(noise * (oldSlots.size() - 1)));
                assertEquals("rarities " + rarities + " noise " + noise, oldPick, Weighted.pick(converted, noise, Weighted.Edge.LATER));
            }
        }
    }

    @Test
    public void aParentWithoutLoadedChildrenListsItselfOnce() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/yellow.json", "{\"rarity\": 1, \"children\": [\"dark\"]}");

        PackWeightConverter.Result result = PackWeightConverter.convert(pack, false);

        assertEquals(List.of(new Weighted<>("yellow", 1)), entriesOf(pack, "biomes/yellow.json", "children", "biome"));
        assertTrue(result.warnings().getFirst().contains("'dark'"));
    }

    @Test
    public void carvingChildrenFollowTheRarityOfTheirEntrysBiome() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/deep.json", "{\"rarity\": 1}");
        write(pack, "biomes/lush.json", "{\"rarity\": 2}");
        write(pack, "dimensions/overworld.json", """
                {
                    "carving": [
                        {"id": "deep", "biome": "deep", "children": ["lush", " off ", "ghost"]},
                        {"id": "lush", "biome": "lush"},
                        {"id": "off", "biome": "lush", "enabled": false},
                        {"id": "ghost", "biome": "missing"}
                    ]
                }
                """);

        PackWeightConverter.Result result = PackWeightConverter.convert(pack, false);

        JsonObject dimension = JsonParser.parseString(read(pack, "dimensions/overworld.json")).getAsJsonObject();
        JsonObject deep = dimension.getAsJsonArray("carving").get(0).getAsJsonObject();
        assertEquals(List.of(new Weighted<>("deep", 1), new Weighted<>("lush", 2), new Weighted<>("deep", 1)), entriesOf(deep.getAsJsonArray("children"), "id"));
        assertEquals(2, result.warnings().size());
    }

    @Test
    public void commentsTrailingCommasAndFormattingStayAndRarityGoesWhereverItIs() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/b.json", "{\"name\": \"B\"}");
        write(pack, "regions/r.json", "{\n  // the region\n  \"name\": \"R\", /* inline */\n  \"landBiomes\": [\"b\",],\n  \"rarity\": 7\n}\n");

        PackWeightConverter.convert(pack, false);

        assertEquals("{\n  // the region\n  \"name\": \"R\", /* inline */\n  \"landBiomes\": [\n      {\"biome\": \"b\", \"weight\": 1}\n  ]\n}\n",
                read(pack, "regions/r.json"));
    }

    @Test
    public void aDryRunChangesNothingAndAConvertedPackStaysAsItIs() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/b.json", "{\"rarity\": 2}");
        write(pack, "regions/r.json", "{\"landBiomes\": [\"b\"]}");

        PackWeightConverter.Result dry = PackWeightConverter.convert(pack, true);
        assertEquals(2, dry.changedFiles().size());
        assertEquals("{\"rarity\": 2}", read(pack, "biomes/b.json"));

        PackWeightConverter.convert(pack, false);
        assertTrue(PackWeightConverter.convert(pack, false).changedFiles().isEmpty());
    }

    @Test
    public void aPackWithSomeListsConvertedAndSomeNotIsRefused() throws IOException {
        Path pack = folder.newFolder("pack").toPath();
        write(pack, "biomes/b.json", "{}");
        write(pack, "regions/done.json", "{\"landBiomes\": [{\"biome\": \"b\", \"weight\": 1}]}");
        write(pack, "regions/todo.json", "{\"landBiomes\": [\"b\"]}");

        assertThrows(PackWeightConverter.ConversionException.class, () -> PackWeightConverter.convert(pack, false));
        assertEquals("{\"landBiomes\": [\"b\"]}", read(pack, "regions/todo.json"));
    }

    @Test
    public void theFixturePacksAreAlreadyWhatTheConverterWouldWrite() throws IOException, URISyntaxException {
        for (String fixture : FIXTURE_PACKS) {
            Path copy = folder.newFolder().toPath();
            copyTree(Path.of(getClass().getResource(FIXTURES + fixture).toURI()), copy);

            PackWeightConverter.Result result = PackWeightConverter.convert(copy, true);

            assertEquals(fixture, List.of(), result.changedFiles());
        }
    }

    private static List<String> oldSlots(List<Integer> rarities) {
        int most = rarities.stream().mapToInt(Integer::intValue).max().orElse(1) + 1;
        List<String> options = new ArrayList<>();
        for (int child = 1; child < rarities.size(); child++) {
            options.add("child" + child);
        }
        options.add("root");
        List<String> slots = new ArrayList<>();
        boolean atEnd = false;
        for (String option : options) {
            int rarity = option.equals("root") ? rarities.getFirst() : rarities.get(Integer.parseInt(option.substring("child".length())));
            for (int copy = 0; copy < most - rarity; copy++) {
                atEnd = !atEnd;
                if (atEnd) {
                    slots.add(option);
                } else {
                    slots.addFirst(option);
                }
            }
        }
        return slots;
    }

    private static List<Integer> weightsOf(Path pack, String file, String list) throws IOException {
        return entriesOf(pack, file, list, "region").stream().map(Weighted::weight).toList();
    }

    private static List<Weighted<String>> entriesOf(Path pack, String file, String list, String field) throws IOException {
        JsonObject json = JsonParser.parseString(read(pack, file)).getAsJsonObject();
        return entriesOf(json.getAsJsonArray(list), field);
    }

    private static List<Weighted<String>> entriesOf(JsonArray array, String field) {
        List<Weighted<String>> entries = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Weighted<>(entry.get(field).getAsString(), entry.get("weight").getAsInt()));
        }
        return entries;
    }

    private static void write(Path pack, String file, String content) throws IOException {
        Path target = pack.resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static String read(Path pack, String file) throws IOException {
        return Files.readString(pack.resolve(file));
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : walk.filter(Files::isRegularFile).toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
    }
}
