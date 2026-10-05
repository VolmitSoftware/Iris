package com.volmit.host;

import com.volmit.support.TestModuleJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class PackJarSnapshotTest {
    private static final String NO_MEMBERS = "";
    private static final String NO_STAGES = "";
    private static final String UNUSED_CLASS = "pack.gamma.Unused";
    private static final long EXITED_PROCESS = Long.MAX_VALUE;

    @TempDir
    Path pack;

    @TempDir
    Path temporaryRoot;

    private static String origin(String jarName) {
        return Path.of(PackModules.MODULES_DIRECTORY, jarName).toString();
    }

    private Path writeGamma() {
        return module("pack.gamma.GammaModule", "gamma:core", NO_MEMBERS, NO_STAGES)
                .source(UNUSED_CLASS, "package pack.gamma; final class Unused {}")
                .writeInto(pack, "gamma.jar");
    }

    private PackModules load() {
        return PackModules.load(pack, List.of(), temporaryRoot);
    }

    private List<Path> snapshotDirectories() throws IOException {
        try (var entries = Files.list(temporaryRoot)) {
            return entries.filter(entry -> entry.getFileName().toString().startsWith(JarSnapshot.DIRECTORY_PREFIX)).toList();
        }
    }

    private Path staleDirectory(String name, String ownerRecord) throws IOException {
        var directory = Files.createDirectory(temporaryRoot.resolve(JarSnapshot.DIRECTORY_PREFIX + name));
        Files.writeString(directory.resolve(JarSnapshot.OWNER_FILE), ownerRecord);
        Files.write(directory.resolve("moss.jar"), new byte[] {1, 2, 3});
        return directory;
    }

    @Test
    void loaderReadsFromACopySoTheOriginalCanBeReplacedWhileLoaded() throws Exception {
        var original = writeGamma();

        try (var packModules = load()) {
            var module = packModules.modules().getFirst();
            var location = Path.of(module.module().getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            assertEquals(1, snapshotDirectories().size());
            assertEquals(snapshotDirectories().getFirst().resolve("gamma.jar"), location);
            assertEquals(origin("gamma.jar"), module.origin());

            Files.delete(original);
            Files.write(original, new byte[] {0});
            assertNotNull(module.module().getClass().getClassLoader().loadClass(UNUSED_CLASS));
        }
    }

    @Test
    void copiesAreDeletedWhenThePackCloses() throws Exception {
        writeGamma();

        try (var ignored = load()) {
            assertEquals(1, snapshotDirectories().size());
        }

        assertTrue(snapshotDirectories().isEmpty());
    }

    @Test
    void copiesAreDeletedWhenTheLoadFails() throws Exception {
        writeGamma();
        new TestModuleJar().writeInto(pack, "broken.jar");
        assertThrows(IllegalArgumentException.class, this::load);
        assertTrue(snapshotDirectories().isEmpty(), "failure while reading jars");

        Files.delete(pack.resolve(PackModules.MODULES_DIRECTORY).resolve("broken.jar"));
        module("pack.throwing.Throwing", "throwing:core", "public Throwing() { throw new IllegalStateException(\"boom\"); }", NO_STAGES)
                .writeInto(pack, "throwing.jar");
        assertThrows(IllegalArgumentException.class, this::load);
        assertTrue(snapshotDirectories().isEmpty(), "failure after the class loader exists");
    }

    @Test
    void errorsNameTheOriginalJarNeverItsCopy() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES).writeInto(pack, "alpha.jar");
        new TestModuleJar().entryPoint("pack.alpha.Alpha").writeInto(pack, "beta.jar");

        var message = assertThrows(IllegalArgumentException.class, this::load).getMessage();

        assertEquals("Module jar " + origin("beta.jar") + " has a broken entry point pack.alpha.Alpha: class is loaded from "
                + origin("alpha.jar") + ", not from this jar", message);
        assertFalse(message.contains(temporaryRoot.toString()), message);
    }

    @Test
    void loadRemovesCopiesOfExitedProcessesOnly() throws Exception {
        var exited = staleDirectory("exited", JarSnapshot.ownerRecord(EXITED_PROCESS));
        var running = staleDirectory("running", JarSnapshot.ownerRecord(ProcessHandle.current().pid()));
        var unmarked = Files.createDirectory(temporaryRoot.resolve(JarSnapshot.DIRECTORY_PREFIX + "unmarked"));
        var foreignHeader = staleDirectory("foreign", "Something else\n" + EXITED_PROCESS + "\n");
        var unexpectedContent = staleDirectory("unexpected", JarSnapshot.ownerRecord(EXITED_PROCESS));
        Files.writeString(unexpectedContent.resolve("notes.txt"), "not ours");
        var otherName = Files.createDirectory(temporaryRoot.resolve("other-" + JarSnapshot.DIRECTORY_PREFIX));
        Files.writeString(otherName.resolve(JarSnapshot.OWNER_FILE), JarSnapshot.ownerRecord(EXITED_PROCESS));

        try (var ignored = load()) {
            assertFalse(Files.exists(exited));
            for (var kept : List.of(running, unmarked, foreignHeader, unexpectedContent, otherName)) {
                assertTrue(Files.exists(kept), kept.toString());
            }
        }
    }
}
