package art.arcane.iris.core.pack;

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInModulesJar;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.resource.ResourceId;
import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The gate for exporting structures through the generic {@link ModuleResourceClosure}: it must carry every structure,
 * pool, piece, loot table and object that {@link StructurePackageClosure} and the placements' object list carried.
 * Set {@code IRIS_EXPORT_COMPARISON_PACK} to a pack folder to compare on a real pack; it is copied first.
 */
public class ExportClosureComparisonTest {
    private static final String COMPARISON_PACK_VARIABLE = "IRIS_EXPORT_COMPARISON_PACK";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void theGenericClosureCarriesWhatTheStructureClosureAndThePlacementsCarried() throws Exception {
        Comparison comparison = compare(fixturePack(), "main");

        assertEquals(Set.of("castle"), comparison.generic.structures);
        assertEquals(comparison.previous.structures, comparison.generic.structures);
        assertEquals(comparison.previous.pools, comparison.generic.pools);
        assertEquals(comparison.previous.pieces, comparison.generic.pieces);
        assertEquals(comparison.previous.loot, comparison.generic.loot);
        assertEquals(Set.of("castle/keep", "tree"), comparison.generic.objects);
        assertEquals(comparison.previous.objects, comparison.generic.objects);
    }

    @Test
    public void theGenericClosureWritesTheStructureGraphAsThePackWroteIt() throws Exception {
        Path pack = fixturePack();
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            ModuleResourceClosure closure = new ModuleResourceClosure(data);
            closure.followSettingsOf(data.getDimensionLoader().load("main"));
            Path target = temporaryFolder.newFolder("export").toPath();

            closure.writeTo(target.toFile(), false);

            assertEquals("castle/start", new JSONObject(Files.readString(target.resolve("structures/castle.json"))).getString("startPool"));
            assertEquals("castle/end", new JSONObject(Files.readString(target.resolve("jigsaw-pools/castle/start.json"))).getString("fallback"));
            JSONObject piece = new JSONObject(Files.readString(target.resolve("jigsaw-pieces/castle/keep.json")));
            assertEquals("castle/keep", piece.getString("object"));
            assertEquals("castle/end", piece.getJSONArray("connectors").getJSONObject(0).getString("pool"));
            assertTrue(Files.isRegularFile(target.resolve("loot/castle/chest.json")));
            assertFalse(Files.exists(target.resolve("structures/unused.json")));
        } finally {
            data.close();
        }
    }

    @Test
    public void theGenericClosureCarriesWhatTheStructureClosureCarriedOnARealPack() throws Exception {
        String packFolder = comparisonPackFolder();
        Comparison comparison = compare(copyOfComparisonPack(packFolder), Path.of(packFolder).getFileName().toString());
        report("export of " + packFolder, comparison);

        assertTrue(String.join("; ", comparison.previous.errors), comparison.previous.isValid);
        assertContains("structures", comparison.generic.structures, comparison.previous.structures);
        assertContains("pools", comparison.generic.pools, comparison.previous.pools);
        assertContains("pieces", comparison.generic.pieces, comparison.previous.pieces);
        assertContains("loot", comparison.generic.loot, comparison.previous.loot);
        assertContains("objects", comparison.generic.objects, comparison.previous.objects);
    }

    @Test
    public void theGenericClosureFollowsEveryStructureOfARealPackLikeTheStructureClosure() throws Exception {
        String packFolder = comparisonPackFolder();
        Path copy = copyOfComparisonPack(packFolder);
        IrisData data = IrisData.openValidation(copy.toFile());
        try {
            IrisPackResources resources = data.getPackResources();
            List<String> structureKeys = resources.ids(StructureResources.STRUCTURES).stream().map(ResourceId::path).toList();
            ModuleResourceClosure closure = new ModuleResourceClosure(data);
            for (ResourceId id : resources.ids(StructureResources.STRUCTURES)) {
                closure.follow(resources.find(StructureResources.STRUCTURES, id).orElseThrow());
            }
            Exported generic = new Exported(new TreeSet<>(structureKeys), new TreeSet<>(closure.keysOf(IrisJigsawPool.class)),
                    new TreeSet<>(closure.keysOf(IrisJigsawPiece.class)), new TreeSet<>(closure.keysOf(LootTable.class)),
                    new TreeSet<>(closure.objectKeys()), true, List.of());
            StructurePackageClosure structures = StructurePackageClosure.collect(copy.toFile(), structureKeys);
            Exported previous = new Exported(new TreeSet<>(structures.structures()), new TreeSet<>(structures.pools()),
                    new TreeSet<>(structures.pieces()), new TreeSet<>(structures.loot()), new TreeSet<>(structures.objects()),
                    structures.isValid(), structures.errors());

            report("every structure of " + packFolder, new Comparison(generic, previous));

            assertTrue(String.join("; ", previous.errors), previous.isValid);
            assertEquals(previous.pools, generic.pools);
            assertEquals(previous.pieces, generic.pieces);
            assertEquals(previous.loot, generic.loot);
            assertEquals(previous.objects, generic.objects);
        } finally {
            data.close();
        }
    }

    private static String comparisonPackFolder() {
        String packFolder = System.getenv(COMPARISON_PACK_VARIABLE);
        Assume.assumeTrue(COMPARISON_PACK_VARIABLE + " names no pack", packFolder != null && !packFolder.isBlank());
        return packFolder;
    }

    private Path copyOfComparisonPack(String packFolder) throws Exception {
        Path copy = temporaryFolder.newFolder("real-pack").toPath();
        copyTree(Path.of(packFolder), copy);
        BuiltInModulesJar.writeInto(copy);
        return copy;
    }

    /** Prints the sizes of both sides and every key only the generic closure carries, for the reviewer of the gate. */
    private static void report(String what, Comparison comparison) {
        Set<String> onlyGenericObjects = new TreeSet<>(comparison.generic.objects);
        onlyGenericObjects.removeAll(comparison.previous.objects);
        Set<String> onlyGenericLoot = new TreeSet<>(comparison.generic.loot);
        onlyGenericLoot.removeAll(comparison.previous.loot);
        System.out.println("Export comparison, " + what + ": " + comparison.generic.summary() + "; previously "
                + comparison.previous.summary() + "; only the generic closure carries objects " + onlyGenericObjects
                + " and loot " + onlyGenericLoot);
    }

    private static void assertContains(String what, Set<String> generic, Set<String> previous) {
        Set<String> missing = new TreeSet<>(previous);
        missing.removeAll(generic);
        assertTrue("The generic closure misses " + what + " " + missing, missing.isEmpty());
    }

    /** Follows the dimension {@code dimensionKey} of {@code pack} the way the packager does, both ways. */
    private static Comparison compare(Path pack, String dimensionKey) {
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
            Set<IrisRegion> regions = new LinkedHashSet<>(dimension.getAllRegions(() -> data));
            regions.remove(null);
            Set<IrisBiome> biomes = new LinkedHashSet<>();
            regions.forEach(region -> biomes.addAll(region.getAllBiomes(() -> data)));
            biomes.addAll(dimension.getReachableBiomes(() -> data));
            biomes.remove(null);
            List<ObjectPlacement> placements = new ArrayList<>();
            regions.forEach(region -> placements.addAll(ObjectPlacements.all(region)));
            biomes.forEach(biome -> placements.addAll(ObjectPlacements.all(biome)));

            ModuleResourceClosure closure = new ModuleResourceClosure(data);
            closure.followSettingsOf(dimension);
            regions.forEach(closure::followSettingsOf);
            biomes.forEach(closure::followSettingsOf);
            closure.follow(placements);
            Exported generic = new Exported(
                    new TreeSet<>(closure.keysOf(StructureResources.STRUCTURES.id())),
                    new TreeSet<>(closure.keysOf(StructureResources.POOLS.id())),
                    new TreeSet<>(closure.keysOf(StructureResources.PIECES.id())),
                    new TreeSet<>(closure.keysOf(LootTable.class)),
                    new TreeSet<>(closure.objectKeys()), true, List.of());

            StructurePackageClosure structures = StructurePackageClosure.collect(pack.toFile(), generic.structures);
            Set<String> previousObjects = new TreeSet<>(structures.objects());
            placements.forEach(placement -> placement.place().stream().filter(key -> key != null && !key.isBlank()).forEach(previousObjects::add));
            Exported previous = new Exported(new TreeSet<>(structures.structures()), new TreeSet<>(structures.pools()),
                    new TreeSet<>(structures.pieces()), new TreeSet<>(structures.loot()), previousObjects,
                    structures.isValid(), structures.errors());
            return new Comparison(generic, previous);
        } finally {
            data.close();
        }
    }

    private Path fixturePack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"],\"structures\":[{\"structures\":[\"castle\"]}]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"objects\":[{\"place\":[\"tree\"]}]}");
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"loot\":[\"castle/chest\"]}");
        write(pack, "structures/unused.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"castle/keep\"}],\"fallback\":\"castle/end\"}");
        write(pack, "jigsaw-pools/castle/end.json", "{\"pieces\":[]}");
        write(pack, "jigsaw-pieces/castle/keep.json", "{\"object\":\"castle/keep\",\"connectors\":[{\"pool\":\"castle/end\"}]}");
        write(pack, "loot/castle/chest.json", "{\"name\":\"Chest\",\"loot\":[{\"type\":\"stick\"}]}");
        write(pack, "objects/castle/keep.iob", "object");
        write(pack, "objects/tree.iob", "object");
        BuiltInModulesJar.writeInto(pack.toPath());
        return pack.toPath();
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path copied = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(copied);
                } else {
                    Files.copy(file, copied, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private record Exported(Set<String> structures, Set<String> pools, Set<String> pieces, Set<String> loot,
                            Set<String> objects, boolean isValid, List<String> errors) {
        String summary() {
            return structures.size() + " structures, " + pools.size() + " pools, " + pieces.size() + " pieces, "
                    + loot.size() + " loot tables, " + objects.size() + " objects";
        }
    }

    private record Comparison(Exported generic, Exported previous) {
    }
}
