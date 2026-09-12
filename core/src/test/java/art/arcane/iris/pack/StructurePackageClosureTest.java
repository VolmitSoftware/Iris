package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StructurePackageClosureTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void collectsAndWritesCompleteReachableGraph() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"loot\":[\"castle/chests\"]}");
        write(pack, "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"castle/start\",\"weight\":1}],\"fallback\":\"castle/end\"}");
        write(pack, "jigsaw-pools/castle/branch.json", "{\"pieces\":[{\"piece\":\"castle/end\",\"weight\":1}]}");
        write(pack, "jigsaw-pools/castle/end.json", "{\"pieces\":[{\"piece\":\"castle/end\",\"weight\":1}]}");
        write(pack, "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[{\"pool\":\"castle/branch\"}]}");
        write(pack, "jigsaw-pieces/castle/end.json", "{\"object\":\"castle/end\",\"connectors\":[]}");
        write(pack, "objects/castle/start.iob", "start-object");
        write(pack, "objects/castle/end.iob", "end-object");
        write(pack, "loot/castle/chests.json", "{\"tables\":[]}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("castle"));

        assertTrue(closure.errors().toString(), closure.isValid());
        assertEquals(Set.of("castle"), closure.structures());
        assertEquals(Set.of("castle/start", "castle/branch", "castle/end"), closure.pools());
        assertEquals(Set.of("castle/start", "castle/end"), closure.pieces());
        assertEquals(Set.of("castle/start", "castle/end"), closure.objects());
        assertEquals(Set.of("castle/chests"), closure.loot());

        File output = temporaryFolder.newFolder("output");
        closure.writeTo(output, true);

        assertTrue(new File(output, "structures/castle.json").isFile());
        assertTrue(new File(output, "jigsaw-pools/castle/branch.json").isFile());
        assertTrue(new File(output, "jigsaw-pieces/castle/end.json").isFile());
        assertTrue(new File(output, "objects/castle/end.iob").isFile());
        assertTrue(new File(output, "loot/castle/chests.json").isFile());
    }

    @Test
    public void excludesUnreachableStructures() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        writeSinglePieceStructure(pack, "selected");
        writeSinglePieceStructure(pack, "unrelated");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("selected"));

        assertTrue(closure.errors().toString(), closure.isValid());
        assertEquals(Set.of("selected"), closure.structures());
        assertFalse(closure.objects().contains("unrelated/piece"));
    }

    @Test
    public void rejectsMissingDependenciesBeforeWriting() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/broken.json", "{\"startPool\":\"broken/missing\"}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("broken"));

        assertFalse(closure.isValid());
        assertEquals(List.of("Missing jigsaw-pools resource 'broken/missing'."), closure.errors());
        try {
            closure.writeTo(temporaryFolder.newFolder("output"), false);
            fail("Invalid closure must not be written");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("broken/missing"));
        }
    }

    @Test
    public void rejectsTraversalKeys() throws Exception {
        File pack = temporaryFolder.newFolder("pack");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("../outside"));

        assertFalse(closure.isValid());
        assertEquals(List.of("Missing or invalid structure."), closure.errors());
        assertTrue(closure.structures().isEmpty());
    }

    @Test
    public void rejectsNonCanonicalAndNonPortableKeys() throws Exception {
        List<String> invalidKeys = List.of(
                " leading",
                "trailing ",
                "folder\\structure",
                "minecraft:structure",
                "folder/con",
                "folder/trailing.",
                "folder/control\u007fkey"
        );

        for (int index = 0; index < invalidKeys.size(); index++) {
            File pack = temporaryFolder.newFolder("invalid-key-" + index);
            StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of(invalidKeys.get(index)));

            assertFalse(invalidKeys.get(index), closure.isValid());
            assertEquals(invalidKeys.get(index), List.of("Missing or invalid structure."), closure.errors());
        }
    }

    @Test
    public void rejectsCaseInsensitiveResourceCollisions() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        writeSinglePieceStructure(pack, "Castle");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("Castle", "castle"));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().stream()
                .anyMatch(error -> error.contains("Case-insensitive structures resource collision")));
    }

    @Test
    public void rejectsMismatchedSourcePathCasing() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "Structures/case.json", "{\"startPool\":\"case/start\"}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("case"));

        assertFalse(closure.isValid());
    }

    @Test
    public void rejectsWrongStructureAndPoolFieldShapes() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/broken.json", "{\"startPool\":7,\"loot\":{}}");
        write(pack, "structures/pool-shapes.json", "{\"startPool\":\"pool-shapes/start\"}");
        write(pack, "jigsaw-pools/pool-shapes/start.json", "{\"pieces\":{},\"fallback\":[]}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("broken", "pool-shapes"));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().contains("Structure 'broken' requires string field 'startPool'."));
        assertTrue(closure.errors().toString(), closure.errors().contains("Structure 'broken' requires array field 'loot'."));
        assertTrue(closure.errors().toString(), closure.errors().contains("Jigsaw pool 'pool-shapes/start' requires array field 'pieces'."));
        assertTrue(closure.errors().toString(), closure.errors().contains("Jigsaw pool 'pool-shapes/start' requires string field 'fallback'."));
    }

    @Test
    public void rejectsMalformedPoolEntryShapes() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/broken.json", "{\"startPool\":\"broken/start\"}");
        write(pack, "jigsaw-pools/broken/start.json",
                "{\"pieces\":[{},{\"empty\":\"true\"},{\"empty\":true,\"piece\":\"broken/piece\"},"
                        + "{\"empty\":true,\"piece\":7}]}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("broken"));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Piece entry 0 in jigsaw pool 'broken/start' requires string field 'piece'."));
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Piece entry 1 in jigsaw pool 'broken/start' requires boolean field 'empty'."));
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Empty piece entry 2 in jigsaw pool 'broken/start' cannot define non-empty field 'piece'."));
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Empty piece entry 3 in jigsaw pool 'broken/start' requires string field 'piece' when defined."));
    }

    @Test
    public void acceptsExplicitEmptyPoolEntry() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/empty.json", "{\"startPool\":\"empty/start\"}");
        write(pack, "jigsaw-pools/empty/start.json",
                "{\"pieces\":[{\"empty\":true,\"weight\":3},{\"empty\":true,\"piece\":\"\"},"
                        + "{\"empty\":true,\"piece\":\"   \"}]}");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("empty"));

        assertTrue(closure.errors().toString(), closure.isValid());
        assertEquals(Set.of("empty/start"), closure.pools());
        assertTrue(closure.pieces().isEmpty());
        assertTrue(closure.objects().isEmpty());
    }

    @Test
    public void rejectsWrongPieceAndConnectorFieldShapes() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "structures/broken.json", "{\"startPool\":\"broken/start\"}");
        write(pack, "jigsaw-pools/broken/start.json", "{\"pieces\":[{\"piece\":\"broken/shape\",\"weight\":1},{\"piece\":\"broken/connector\",\"weight\":1}]}");
        write(pack, "jigsaw-pieces/broken/shape.json", "{\"object\":{},\"connectors\":{}}");
        write(pack, "jigsaw-pieces/broken/connector.json", "{\"object\":\"broken/connector\",\"connectors\":[{},7]}");
        write(pack, "objects/broken/connector.iob", "object");

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("broken"));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().contains("Jigsaw piece 'broken/shape' requires string field 'object'."));
        assertTrue(closure.errors().toString(), closure.errors().contains("Jigsaw piece 'broken/shape' requires array field 'connectors'."));
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Connector 0 in jigsaw piece 'broken/connector' requires string field 'pool'."));
        assertTrue(closure.errors().toString(), closure.errors().contains(
                "Jigsaw piece 'broken/connector' has a non-object connector at index 1."));
    }

    @Test
    public void rejectsSourceSymlinksOutsidePack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        File outside = temporaryFolder.newFolder("outside");
        write(outside, "escape.json", "{\"startPool\":\"escape/start\"}");
        Path link = pack.toPath().resolve("structures/escape.json");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outside.toPath().resolve("escape.json"));

        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("escape"));

        assertFalse(closure.isValid());
        assertTrue(closure.errors().toString(), closure.errors().stream()
                .anyMatch(error -> error.contains("escapes the structure package through a symbolic link")));
    }

    @Test
    public void rejectsTargetSymlinks() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        writeSinglePieceStructure(pack, "selected");
        StructurePackageClosure closure = StructurePackageClosure.collect(pack, List.of("selected"));
        File output = temporaryFolder.newFolder("output");
        File outside = temporaryFolder.newFolder("outside");
        Files.createSymbolicLink(output.toPath().resolve("structures"), outside.toPath());

        try {
            closure.writeTo(output, true);
            fail("Symbolic link target must not be written");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("symbolic link"));
        }
        assertEquals(0, outside.list().length);
    }

    private void writeSinglePieceStructure(File pack, String key) throws Exception {
        write(pack, "structures/" + key + ".json", "{\"startPool\":\"" + key + "/start\"}");
        write(pack, "jigsaw-pools/" + key + "/start.json", "{\"pieces\":[{\"piece\":\"" + key + "/piece\",\"weight\":1}]}");
        write(pack, "jigsaw-pieces/" + key + "/piece.json", "{\"object\":\"" + key + "/piece\",\"connectors\":[]}");
        write(pack, "objects/" + key + "/piece.iob", key);
    }

    private void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
