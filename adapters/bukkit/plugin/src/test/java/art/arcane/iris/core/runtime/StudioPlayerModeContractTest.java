package art.arcane.iris.core.runtime;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioPlayerModeContractTest {
    @Test
    public void autoStartedStudioUsesSpectatorMode() throws IOException {
        String plugin = Files.readString(Path.of("src/main/java/art/arcane/iris/Iris.java")).replace("\r\n", "\n");
        String commands = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/commands/CommandStudio.java")).replace("\r\n", "\n");

        assertFalse(plugin.contains("GameMode.CREATIVE"));
        assertFalse(commands.contains("GameMode.CREATIVE"));
        assertTrue(plugin.contains("GameMode.SPECTATOR"));
    }

    @Test
    public void tpStudioUsesThePreparedCoordinatorEntry() throws IOException {
        String commands = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/commands/CommandStudio.java")).replace("\r\n", "\n");
        int methodStart = commands.indexOf("public void tpstudio()");
        int methodEnd = commands.indexOf("\n    @Director", methodStart);
        String method = commands.substring(methodStart, methodEnd);

        assertTrue(method.contains("StudioSVC studioService = Iris.service(StudioSVC.class)"));
        assertTrue(method.contains("studioService.teleportToActiveProject(player)"));
        assertFalse(method.contains("setGameMode("));
        assertFalse(method.contains("getActiveProject()"));
        assertFalse(method.contains("BukkitPlatform.teleportAsync"));
        assertFalse(method.contains("BukkitWorldBinding.spawnLocation"));
    }

    @Test
    public void editingStudiosRemainCreative() throws IOException {
        String objectCommands = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/commands/CommandObject.java")).replace("\r\n", "\n");
        String jigsawCommands = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/commands/CommandJigsaw.java")).replace("\r\n", "\n");

        assertTrue(objectCommands.contains("GameMode.CREATIVE"));
        assertTrue(objectCommands.contains("StudioOpenCoordinator.StudioOpenKind.OBJECT"));
        assertTrue(jigsawCommands.contains("player.creative(true)"));
        assertFalse(objectCommands.contains("GameMode.SPECTATOR"));
        assertFalse(jigsawCommands.contains("GameMode.SPECTATOR"));    }
}
