package art.arcane.iris.modded;

import art.arcane.iris.modded.command.ModdedPackCommands;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class ModdedPackValidationPublicationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void clearValidation() {
        PackValidationRegistry.clear();
    }

    @Test
    public void repeatedSnapshotValidationParsesUnchangedPackOnce() throws Exception {
        File root = temporaryFolder.newFolder("snapshot");
        PackValidationResult result = loadable(root);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(root)).thenReturn(result);
            assertSame(result, ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            assertSame(result, ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            validator.verify(() -> PackValidator.validate(root), times(1));
        }
    }

    @Test
    public void snapshotValidationRejectsChangedBytesWithPreservedTimestamp() throws Exception {
        File root = temporaryFolder.newFolder("snapshot");
        File content = new File(root, "content.json");
        Files.writeString(content.toPath(), "valid");
        FileTime timestamp = Files.getLastModifiedTime(content.toPath());
        PackValidationResult broken = new PackValidationResult(root.getName(), List.of("invalid"), List.of(), 1L);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(root)).thenReturn(loadable(root), broken);
            ModdedGenerationHistoryStorage.validatePack(root.toPath(), null);
            Files.writeString(content.toPath(), "wrong");
            Files.setLastModifiedTime(content.toPath(), timestamp);
            assertThrows(BrokenPackException.class,
                    () -> ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            assertThrows(BrokenPackException.class,
                    () -> ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            validator.verify(() -> PackValidator.validate(root), times(2));
        }
    }

    @Test
    public void snapshotValidationReparsesChangedRegistryContext() throws Exception {
        File root = temporaryFolder.newFolder("snapshot");
        AtomicReference<String> context = new AtomicReference<>("first");
        PackValidationResult first = loadable(root);
        PackValidationResult second = loadable(root);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<PackValidationCache> cache = mockStatic(PackValidationCache.class)) {
            cache.when(PackValidationCache::contextFingerprint).thenAnswer(ignored -> context.get());
            validator.when(() -> PackValidator.validate(root)).thenReturn(first, second);
            assertSame(first, ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            context.set("second");
            assertSame(second, ModdedGenerationHistoryStorage.validatePack(root.toPath(), null));
            validator.verify(() -> PackValidator.validate(root), times(2));
        }
    }

    @Test
    public void validationPublishesTheCanonicalRootAndPackName() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        File equivalent = new File(root, ".");
        PackValidationResult result = loadable(root);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(equivalent)).thenReturn(result);

            assertSame(result, ModdedStartup.validatePack(equivalent));

            assertSame(result, PackValidationRegistry.requireLoadable(root.toPath()));
            assertSame(result, PackValidationRegistry.requireLoadable(root.getName()));
        }
    }

    @Test
    public void rootInvalidationCannotReusePackNameValidation() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        PackValidationRegistry.publish(loadable(root));
        PackValidationResult replacement = loadable(root);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<ModdedPackCommands> commands = mockStatic(ModdedPackCommands.class)) {
            commands.when(ModdedPackCommands::packsRoot).thenReturn(root.getParentFile());
            validator.when(() -> PackValidator.validate(root)).thenReturn(replacement);

            assertSame(replacement, ModdedStartup.requirePackForWorldCreation(root.getName()));

            validator.verify(() -> PackValidator.validate(root));
            assertSame(replacement, PackValidationRegistry.requireLoadable(root.toPath()));
        }
    }

    @Test
    public void changedContentWithPreservedTimestampCannotReuseValidation() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        File content = new File(root, "dimension.json");
        Files.writeString(content.toPath(), "valid");
        PackValidationResult original = loadable(root);
        PackValidationResult broken = new PackValidationResult(root.getName(), List.of("invalid"), List.of(),
                System.currentTimeMillis());
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<ModdedPackCommands> commands = mockStatic(ModdedPackCommands.class)) {
            commands.when(ModdedPackCommands::packsRoot).thenReturn(root.getParentFile());
            validator.when(() -> PackValidator.validate(root)).thenReturn(original, broken);
            ModdedStartup.validatePack(root);
            Files.writeString(content.toPath(), "wrong");
            Files.setLastModifiedTime(content.toPath(), FileTime.fromMillis(1L));
            Files.setLastModifiedTime(root.toPath(), FileTime.fromMillis(1L));

            assertThrows(BrokenPackException.class,
                    () -> ModdedStartup.requirePackForWorldCreation(root.getName()));
        }
    }

    @Test
    public void externalEditDuringValidationCannotPublishMixedSnapshot() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        File content = new File(root, "dimension.json");
        Files.writeString(content.toPath(), "original");
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(root)).thenAnswer(invocation -> {
                Files.writeString(content.toPath(), "changed");
                return loadable(root);
            });

            assertThrows(BrokenPackException.class, () -> ModdedStartup.validatePack(root));
            assertNull(PackValidationRegistry.get(root.toPath()));
        }
    }

    @Test
    public void mutationDuringValidationPreventsStalePublication() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(root)).thenAnswer(invocation -> {
                try (PackValidationRegistry.RootMutation mutation = PackValidationRegistry.beginRootMutation(root.toPath())) {
                    return loadable(root);
                }
            });

            try {
                ModdedStartup.validatePack(root);
                fail("Changed pack must reject stale validation");
            } catch (BrokenPackException expected) {
                assertNull(PackValidationRegistry.get(root.toPath()));
                assertNull(PackValidationRegistry.get(root.getName()));
            }
        }
    }

    @Test
    public void validationFailurePublishesBlockedRoot() throws Exception {
        File root = temporaryFolder.newFolder("terrain");
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            validator.when(() -> PackValidator.validate(root)).thenThrow(new IllegalStateException("Invalid terrain"));

            PackValidationResult result = ModdedStartup.validatePack(root);

            assertFalse(result.isLoadable());
            assertSame(result, PackValidationRegistry.get(root.toPath()));
            assertSame(result, PackValidationRegistry.get(root.getName()));
        }
    }

    @Test
    public void snapshotReusesTheValidationOfAnIdenticalValidatedSource() throws Exception {
        File source = temporaryFolder.newFolder("overworld");
        Files.writeString(new File(source, "dimension.json").toPath(), "terrain");
        File snapshot = temporaryFolder.newFolder("epoch", "pack");
        Files.writeString(new File(snapshot, "dimension.json").toPath(), "terrain");
        PackValidationResult sourceResult = loadable(source);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(source)).thenReturn(sourceResult);
            ModdedStartup.validatePack(source);

            assertSame(sourceResult, ModdedGenerationHistoryStorage.validatePack(snapshot.toPath(), source.toPath()));

            validator.verify(() -> PackValidator.validate(snapshot), never());
            assertSame(sourceResult, PackValidationRegistry.requireLoadable(snapshot.toPath()));
        }
    }

    @Test
    public void snapshotThatDiffersFromItsSourceIsValidatedOnItsOwn() throws Exception {
        File source = temporaryFolder.newFolder("overworld");
        Files.writeString(new File(source, "dimension.json").toPath(), "terrain");
        File snapshot = temporaryFolder.newFolder("epoch", "pack");
        Files.writeString(new File(snapshot, "dimension.json").toPath(), "older terrain");
        PackValidationResult snapshotResult = loadable(snapshot);
        try (MockedStatic<PackValidator> validator = mockStatic(PackValidator.class)) {
            validator.when(() -> PackValidator.validate(source)).thenReturn(loadable(source));
            validator.when(() -> PackValidator.validate(snapshot)).thenReturn(snapshotResult);
            ModdedStartup.validatePack(source);

            assertSame(snapshotResult, ModdedGenerationHistoryStorage.validatePack(snapshot.toPath(), source.toPath()));

            validator.verify(() -> PackValidator.validate(snapshot), times(1));
        }
    }

    private static PackValidationResult loadable(File root) {
        return new PackValidationResult(root.getName(), List.of(), List.of(), System.currentTimeMillis());
    }
}
