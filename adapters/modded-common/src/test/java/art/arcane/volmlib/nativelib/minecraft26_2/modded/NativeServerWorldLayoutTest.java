package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeServerWorldLayoutTest {
    @Test
    public void universeOptionIsReadInBothArgumentForms() {
        assertEquals("worlds", NativeServerWorldLayout.commandLineOption(
                List.of("nogui", "--universe", "worlds"), "universe"));
        assertEquals("worlds", NativeServerWorldLayout.commandLineOption(
                List.of("--universe=worlds", "nogui"), "universe"));
        assertEquals("survival", NativeServerWorldLayout.commandLineOption(
                List.of("--universe", "worlds", "--world", "survival"), "world"));
    }

    @Test
    public void missingOrValuelessOptionsResolveToNull() {
        assertNull(NativeServerWorldLayout.commandLineOption(List.of("nogui"), "universe"));
        assertNull(NativeServerWorldLayout.commandLineOption(List.of(), "world"));
        assertNull(NativeServerWorldLayout.commandLineOption(List.of("nogui", "--world"), "world"));
    }

    @Test
    public void universeRootDefaultsToTheInstanceRootAndHonorsTheOption() throws IOException {
        Path instanceRoot = Files.createTempDirectory("iris-instance");
        Path elsewhere = Files.createTempDirectory("iris-elsewhere");

        assertEquals(instanceRoot, NativeServerWorldLayout.universeRoot(instanceRoot, null));
        assertEquals(instanceRoot, NativeServerWorldLayout.universeRoot(instanceRoot, ""));
        assertEquals(instanceRoot.resolve("worlds"), NativeServerWorldLayout.universeRoot(instanceRoot, "worlds"));
        assertEquals(elsewhere, NativeServerWorldLayout.universeRoot(instanceRoot, elsewhere.toString()));
    }

    @Test
    public void worldRootResolvesInsideTheUniverse() throws IOException {
        Path universe = Files.createTempDirectory("iris-universe");
        Path world = Files.createDirectory(universe.resolve("survival"));

        assertEquals(world.toAbsolutePath().normalize(),
                NativeServerWorldLayout.resolveWorldRoot(universe, "survival"));
    }

    @Test
    public void worldRootRefusesToEscapeTheUniverse() throws IOException {
        Path universe = Files.createTempDirectory("iris-universe");
        Files.createDirectory(universe.resolve("survival"));

        IOException escaped = assertThrows(IOException.class,
                () -> NativeServerWorldLayout.resolveWorldRoot(universe, "../survival"));
        IOException empty = assertThrows(IOException.class,
                () -> NativeServerWorldLayout.resolveWorldRoot(universe, "."));

        assertTrue(escaped.getMessage().contains("Unsafe world name"));
        assertTrue(empty.getMessage().contains("Unsafe world name"));
    }

    @Test
    public void worldRootReportsMissingDirectoriesAsTheRecoverableCase() throws IOException {
        Path universe = Files.createTempDirectory("iris-universe");

        NativeServerWorldLayout.MissingWorldRootException missingWorld = assertThrows(
                NativeServerWorldLayout.MissingWorldRootException.class,
                () -> NativeServerWorldLayout.resolveWorldRoot(universe, "survival"));
        NativeServerWorldLayout.MissingWorldRootException missingUniverse = assertThrows(
                NativeServerWorldLayout.MissingWorldRootException.class,
                () -> NativeServerWorldLayout.resolveWorldRoot(universe.resolve("absent"), "survival"));

        assertTrue(missingWorld.getMessage().contains("world directory does not exist"));
        assertEquals(universe.resolve("survival").toAbsolutePath().normalize(), missingWorld.path());
        assertTrue(missingUniverse.getMessage().contains("universe directory does not exist"));
        assertEquals(universe.resolve("absent"), missingUniverse.path());
    }

    @Test
    public void unsafeWorldNameIsNotTreatedAsAMissingWorldRoot() throws IOException {
        Path universe = Files.createTempDirectory("iris-universe");

        IOException escaped = assertThrows(IOException.class,
                () -> NativeServerWorldLayout.resolveWorldRoot(universe, "../survival"));

        assertFalse(escaped instanceof NativeServerWorldLayout.MissingWorldRootException);
    }
}
