package art.arcane.iris.studio.workspace;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisProjectCopierTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copiesTransformsAndAtomicallyPublishesProject() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Files.createDirectories(source.resolve("biomes"));
        Files.writeString(source.resolve("biomes/plains.json"), "{}");
        Files.createDirectories(source.resolve(".git"));
        Files.writeString(source.resolve(".git/config"), "private");
        Files.writeString(source.resolve("source.code-workspace"), "private");
        Path target = workspace.resolve("target-pack");

        IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "source",
                "target-pack"
        );

        assertTrue(Files.isDirectory(target));
        assertTrue(Files.isRegularFile(target.resolve("biomes/plains.json")));
        assertTrue(Files.isRegularFile(target.resolve("dimensions/target-pack.json")));
        assertFalse(Files.exists(target.resolve("dimensions/source.json")));
        assertFalse(Files.exists(target.resolve(".git")));
        assertFalse(Files.exists(target.resolve("source.code-workspace")));
        JSONObject dimension = new JSONObject(Files.readString(target.resolve("dimensions/target-pack.json")));
        assertEquals("Target Pack", dimension.getString("name"));
        assertNoStages(workspace, "target-pack");
    }

    @Test
    public void sourceFolderAndSelectedDimensionMayHaveDifferentKeys() throws Exception {
        Path workspace = temporaryFolder.newFolder("folder-key-mismatch").toPath();
        Path source = workspace.resolve("template-pack");
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/overworld.json"), "{\"name\":\"Source\"}");
        Path target = workspace.resolve("new-project");

        IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "overworld",
                "new-project"
        );

        assertTrue(Files.isRegularFile(target.resolve("dimensions/new-project.json")));
        assertFalse(Files.exists(target.resolve("dimensions/overworld.json")));
    }

    @Test
    public void retainsSupportingDimensionsWhileRenamingTheSelectedDimension() throws Exception {
        Path workspace = temporaryFolder.newFolder("multiple-dimensions").toPath();
        Path source = workspace.resolve("template-pack");
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/overworld.json"), "{\"name\":\"Source\"}");
        Files.writeString(source.resolve("dimensions/the_nether.json"), "{\"name\":\"Nether\"}");
        Path target = workspace.resolve("new-project");

        IrisProjectCopier.copyProject(source.toFile(), workspace.toFile(), "overworld", "new-project");

        assertTrue(Files.isRegularFile(target.resolve("dimensions/new-project.json")));
        assertTrue(Files.isRegularFile(target.resolve("dimensions/the_nether.json")));
    }

    @Test
    public void rewritesDimensionStackReferencesAcrossCopiedDimensionsWhenRenaming() throws Exception {
        Path workspace = temporaryFolder.newFolder("dimension-stack-reference").toPath();
        Path source = workspace.resolve("template-pack");
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/overworld.json"), """
                {
                  "name": "Source",
                  "dimensionStack": {
                    "dimensions": ["layers/sky", "overworld"],
                    "spacer": 24
                  }
                }
                """);
        Files.createDirectories(source.resolve("dimensions/layers"));
        Files.writeString(source.resolve("dimensions/layers/sky.json"), """
                {
                  "name": "Sky",
                  "dimensionStack": {
                    "dimensions": ["overworld", "layers/sky"]
                  }
                }
                """);

        IrisProjectCopier.copyProject(source.toFile(), workspace.toFile(), "overworld", "new-project");

        JSONObject dimension = new JSONObject(Files.readString(
                workspace.resolve("new-project/dimensions/new-project.json")));
        assertEquals("layers/sky", dimension.getJSONObject("dimensionStack")
                .getJSONArray("dimensions").getString(0));
        assertEquals("new-project", dimension.getJSONObject("dimensionStack")
                .getJSONArray("dimensions").getString(1));
        JSONObject supportingDimension = new JSONObject(Files.readString(
                workspace.resolve("new-project/dimensions/layers/sky.json")));
        assertEquals("new-project", supportingDimension.getJSONObject("dimensionStack")
                .getJSONArray("dimensions").getString(0));
        assertEquals("layers/sky", supportingDimension.getJSONObject("dimensionStack")
                .getJSONArray("dimensions").getString(1));
    }

    @Test
    public void existingTargetRemainsUnchanged() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Path target = workspace.resolve("target");
        Files.createDirectories(target);
        Path sentinel = target.resolve("sentinel.txt");
        Files.writeString(sentinel, "keep-me");

        assertThrows(FileAlreadyExistsException.class, () -> IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "source",
                "target"
        ));

        assertEquals("keep-me", Files.readString(sentinel));
        assertEquals(1L, countEntries(target));
        assertNoStages(workspace, "target");
    }

    @Test
    public void rejectsInvalidProjectKeysAndEscapedTargets() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Path target = workspace.resolve("target");
        List<String> invalidKeys = List.of(
                "",
                ".",
                "..",
                "nested/project",
                "nested\\project",
                workspace.resolve("absolute").toAbsolutePath().toString()
        );

        for (String invalidKey : invalidKeys) {
            assertThrows(IOException.class, () -> IrisProjectCopier.copyProject(
                    source.toFile(),
                    workspace.toFile(),
                    "source",
                    invalidKey
            ));
        }

        assertNoStages(workspace, "target");
    }

    @Test
    public void rejectsMissingSelectedDimension() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Path target = workspace.resolve("target");

        assertThrows(IOException.class, () -> IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "different-source",
                "target"
        ));

        assertFalse(Files.exists(target));
        assertNoStages(workspace, "target");
    }

    @Test
    public void acceptsSafeSymbolicLinkSource() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path realSource = workspace.resolve("real-source");
        Files.createDirectories(realSource.resolve("dimensions"));
        Files.writeString(realSource.resolve("dimensions/source.json"), "{\"name\":\"Source\"}");
        Path linkedSource = workspace.resolve("source");
        try {
            Files.createSymbolicLink(linkedSource, realSource.getFileName());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        }
        Path target = workspace.resolve("target");

        IrisProjectCopier.copyProject(
                linkedSource.toFile(),
                workspace.toFile(),
                "source",
                "target"
        );

        assertTrue(Files.isRegularFile(target.resolve("dimensions/target.json")));
        assertNoStages(workspace, "target");
    }

    @Test
    public void injectedMidCopyFailureLeavesNoTargetOrStage() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Files.createDirectories(source.resolve("objects"));
        Files.writeString(source.resolve("objects/fail.iob"), "failure-point");
        Path target = workspace.resolve("target");

        assertThrows(IOException.class, () -> IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "source",
                "target",
                (Path input, Path output) -> {
                    if ("fail.iob".equals(input.getFileName().toString())) {
                        throw new IOException("injected copy failure");
                    }
                }
        ));

        assertFalse(Files.exists(target));
        assertNoStages(workspace, "target");
    }

    @Test
    public void rejectsSymbolicLinksInsideSourceTreeAndCleansStage() throws Exception {
        Path workspace = temporaryFolder.newFolder("packs").toPath();
        Path source = createSource(workspace, "source");
        Path external = temporaryFolder.newFile("outside.txt").toPath();
        Path link = source.resolve("objects-link");
        try {
            Files.createSymbolicLink(link, external);
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException(e);
        }
        Path target = workspace.resolve("target");

        assertThrows(IOException.class, () -> IrisProjectCopier.copyProject(
                source.toFile(),
                workspace.toFile(),
                "source",
                "target"
        ));

        assertFalse(Files.exists(target));
        assertNoStages(workspace, "target");
    }

    private static Path createSource(Path workspace, String key) throws IOException {
        Path source = workspace.resolve(key);
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions").resolve(key + ".json"), "{\"name\":\"Source\"}");
        return source;
    }

    private static long countEntries(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.count();
        }
    }

    private static void assertNoStages(Path workspace, String targetKey) throws IOException {
        try (Stream<Path> entries = Files.list(workspace)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(
                    "." + targetKey + ".importing-"
            )));
        }
    }
}
