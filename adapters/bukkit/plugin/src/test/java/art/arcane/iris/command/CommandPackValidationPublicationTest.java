package art.arcane.iris.command;

import art.arcane.iris.pack.PackFingerprints;

import art.arcane.iris.Iris;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class CommandPackValidationPublicationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void clearValidation() {
        PackValidationRegistry.clear();
    }

    @Test
    public void changedCachedPackCannotBePersistedAsFreshValidation() throws Exception {
        File packs = temporaryFolder.newFolder("packs");
        File root = new File(packs, "terrain");
        Files.createDirectories(root.toPath());
        File content = new File(root, "dimension.json");
        Files.writeString(content.toPath(), "original");
        File cacheFile = new File(temporaryFolder.getRoot(), "validation-cache.json");
        Iris previousPlugin = Iris.instance;
        Iris plugin = mock(Iris.class);
        Iris.instance = plugin;
        when(plugin.getDataFile("cache", "pack-validation.json")).thenReturn(cacheFile);
        try (MockedStatic<Iris> iris = mockStatic(Iris.class);
             MockedStatic<PackValidationCache> cache = mockStatic(PackValidationCache.class, CALLS_REAL_METHODS)) {
            cache.when(PackValidationCache::contextFingerprint).thenReturn("context");
            PackValidationResult valid = new PackValidationResult(root.getName(), List.of(), List.of(), 1L);
            PackValidationRegistry.publish(valid);
            PackValidationRegistry.publish(root.toPath(), valid,
                    PackFingerprints.computePackTreeFingerprint(root), "context");
            Files.writeString(content.toPath(), "changed");
            Method persist = CommandPack.class.getDeclaredMethod("persistValidationCache", File.class);
            persist.setAccessible(true);

            persist.invoke(new CommandPack(), packs);

            assertFalse(cacheFile.exists());
            Files.writeString(content.toPath(), "original");

            persist.invoke(new CommandPack(), packs);

            assertTrue(cacheFile.exists());
            assertTrue(PackValidationCache.load(cacheFile.toPath(),
                    PackFingerprints.computePackContentSnapshot(packs).content(), "context",
                    List.of(root.getName())).orElseThrow().getFirst().isLoadable());
        } finally {
            Iris.instance = previousPlugin;
        }
    }

    @Test
    public void commandPublishesCanonicalRootValidation() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        PackValidationResult valid = new PackValidationResult(root.getName(), List.of(), List.of(), 1L);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(root)).thenReturn(valid);

            assertSame(valid, new CommandPack().runValidate(mock(VolmitSender.class), root));

            assertSame(valid, PackValidationRegistry.get(root.toPath()));
            assertSame(valid, PackValidationRegistry.get(root.getName()));
        }
    }

    @Test
    public void mutationDuringValidationCannotRepublishStaleResult() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            validator.when(() -> PackValidator.validate(root)).thenAnswer(invocation -> {
                try (PackValidationRegistry.RootMutation mutation = PackValidationRegistry.beginRootMutation(root.toPath())) {
                    return new PackValidationResult(root.getName(), List.of(), List.of(), 1L);
                }
            });

            assertFalse(new CommandPack().runValidate(mock(VolmitSender.class), root).isLoadable());
            assertNull(PackValidationRegistry.get(root.toPath()));
            assertNull(PackValidationRegistry.get(root.getName()));
        }
    }

    @Test
    public void externalEditDuringValidationPublishesBlockedResult() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        File content = new File(root, "dimension.json");
        Files.writeString(content.toPath(), "original");
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            validator.when(() -> PackValidator.validate(root)).thenAnswer(invocation -> {
                Files.writeString(content.toPath(), "changed");
                return new PackValidationResult(root.getName(), List.of(), List.of(), 1L);
            });

            assertFalse(new CommandPack().runValidate(mock(VolmitSender.class), root).isLoadable());
            assertFalse(PackValidationRegistry.get(root.toPath()).isLoadable());
        }
    }
}
