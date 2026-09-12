package art.arcane.iris.pack;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PackDirectoryResolverTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesOnlyExistingDirectChildren() throws Exception {
        File packs = temporaryFolder.newFolder("packs");
        File overworld = new File(packs, "overworld");
        Files.createDirectory(overworld.toPath());

        assertEquals(overworld.getAbsoluteFile(), PackDirectoryResolver.resolveExisting(packs, "overworld"));
        assertEquals(overworld.getAbsoluteFile(), PackDirectoryResolver.resolveExisting(packs, "./overworld"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "missing"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, ""));
    }

    @Test
    public void rejectsTraversalAbsoluteAndNestedPaths() throws Exception {
        File packs = temporaryFolder.newFolder("pack-root");
        File outside = temporaryFolder.newFolder("outside");
        File nested = new File(packs, "nested/pack");
        Files.createDirectories(nested.toPath());

        assertNull(PackDirectoryResolver.resolveExisting(packs, "../outside"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, outside.getAbsolutePath()));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "nested/pack"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "."));
    }

    @Test
    public void acceptsSafeSymbolicLinkPackRoots() throws Exception {
        File packs = temporaryFolder.newFolder("symlink-root");
        File outside = temporaryFolder.newFolder("symlink-target");
        Path link = new File(packs, "linked").toPath();
        try {
            Files.createSymbolicLink(link, outside.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException(e);
        }

        assertEquals(link.toFile().getAbsoluteFile(), PackDirectoryResolver.resolveExisting(packs, "linked"));
        assertTrue(PackDirectoryResolver.listVisiblePackDirectories(packs).contains(link.toFile()));
        PackDirectoryResolver.requireSafePackTree(link.toFile());
    }

    @Test
    public void excludesEveryHiddenTransactionDirectory() throws Exception {
        File packs = temporaryFolder.newFolder("transaction-root");
        File visible = new File(packs, "overworld");
        Files.createDirectory(visible.toPath());
        Files.createDirectory(new File(packs, ".iris-import-123").toPath());
        Files.createDirectory(new File(packs, ".overworld.backup-123").toPath());
        Files.createDirectory(new File(packs, ".importing-123").toPath());
        Files.createDirectory(new File(packs, ".custom-stage").toPath());

        List<File> listed = PackDirectoryResolver.listVisiblePackDirectories(packs);

        assertEquals(List.of(visible), listed);
        assertTrue(PackDirectoryResolver.isVisiblePackDirectory(visible));
        assertNull(PackDirectoryResolver.resolveExisting(packs, ".custom-stage"));
    }

    @Test
    public void listsPacksThroughSymbolicLinkWorkspace() throws Exception {
        File sharedPacks = temporaryFolder.newFolder("shared-packs");
        File overworld = new File(sharedPacks, "overworld");
        Files.createDirectory(overworld.toPath());
        Path workspace = temporaryFolder.getRoot().toPath().resolve("packs");
        try {
            Files.createSymbolicLink(workspace, sharedPacks.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        assertEquals(List.of(workspace.resolve("overworld").toFile()),
                PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(workspace.toFile()));
    }

    @Test
    public void missingWorkspaceListsNoPacks() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing-packs");

        assertEquals(List.of(), PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(missing));
    }

    @Test
    public void rejectsDanglingSymbolicLinkWorkspace() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("dangling-packs");
        try {
            Files.createSymbolicLink(workspace, temporaryFolder.getRoot().toPath().resolve("missing-target"));
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        assertThrows(IOException.class,
                () -> PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(workspace.toFile()));
    }

    @Test
    public void rejectsSymbolicLinksInsidePackTrees() throws Exception {
        File packs = temporaryFolder.newFolder("nested-link-root");
        File pack = new File(packs, "overworld");
        Files.createDirectories(pack.toPath().resolve("dimensions"));
        Path outside = temporaryFolder.newFile("outside-dimension.json").toPath();
        Path link = pack.toPath().resolve("dimensions/overworld.json");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException(exception);
        }

        try {
            PackDirectoryResolver.requireSafePackTree(pack);
            fail("Pack trees containing symbolic links must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("symbolic link"));
        }
    }
}
