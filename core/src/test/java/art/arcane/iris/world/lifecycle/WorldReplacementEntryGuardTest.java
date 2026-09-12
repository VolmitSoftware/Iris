package art.arcane.iris.world.lifecycle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldReplacementEntryGuardTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stagesAndRefreshesCurrentPlayerReceipts() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("level-refresh").toPath();
        Path stagedWorld = temporaryFolder.newFolder("stage-refresh").toPath();
        writePlayer(levelRoot, FIRST_PLAYER, ".dat");
        writePlayer(levelRoot, SECOND_PLAYER, ".dat_old");
        Files.writeString(levelRoot.resolve("players/data/.DS_Store"), "ignored");

        WorldReplacementEntryGuard.Entry staged = WorldReplacementEntryGuard.stage(
                levelRoot,
                stagedWorld,
                TRANSACTION_ID
        );

        assertEquals(Set.of(FIRST_PLAYER), staged.pendingPlayers());
        writePlayer(levelRoot, SECOND_PLAYER, ".dat");

        WorldReplacementEntryGuard.Entry refreshed = WorldReplacementEntryGuard.refreshPlayers(
                levelRoot,
                stagedWorld,
                TRANSACTION_ID
        );

        assertEquals(Set.of(FIRST_PLAYER, SECOND_PLAYER), refreshed.pendingPlayers());
        assertEquals(refreshed, WorldReplacementEntryGuard.load(stagedWorld).orElseThrow());
    }

    @Test
    public void keepsFinalReceiptUntilSafeSpawnAllowsMarkerRetirement() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("level-retire").toPath();
        Path stagedWorld = temporaryFolder.newFolder("stage-retire").toPath();
        writePlayer(levelRoot, FIRST_PLAYER, ".dat");
        WorldReplacementEntryGuard.stage(levelRoot, stagedWorld, TRANSACTION_ID);

        assertFalse(WorldReplacementEntryGuard.retireIfEmpty(stagedWorld, TRANSACTION_ID));

        Optional<WorldReplacementEntryGuard.Entry> completed = WorldReplacementEntryGuard.completePlayer(
                stagedWorld,
                TRANSACTION_ID,
                FIRST_PLAYER
        );

        assertTrue(completed.isPresent());
        assertTrue(completed.orElseThrow().pendingPlayers().isEmpty());
        assertTrue(Files.isRegularFile(marker(stagedWorld)));
        assertTrue(WorldReplacementEntryGuard.retireIfEmpty(stagedWorld, TRANSACTION_ID));
        assertFalse(Files.exists(marker(stagedWorld)));
    }

    @Test
    public void rejectsAReceiptFromAnotherTransactionWithoutChangingTheMarker() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("level-mismatch").toPath();
        Path stagedWorld = temporaryFolder.newFolder("stage-mismatch").toPath();
        writePlayer(levelRoot, FIRST_PLAYER, ".dat");
        WorldReplacementEntryGuard.stage(levelRoot, stagedWorld, TRANSACTION_ID);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementEntryGuard.completePlayer(
                        stagedWorld,
                        OTHER_TRANSACTION_ID,
                        FIRST_PLAYER
                )
        );

        assertTrue(failure.getMessage().contains("another transaction"));
        assertEquals(
                Set.of(FIRST_PLAYER),
                WorldReplacementEntryGuard.load(stagedWorld).orElseThrow().pendingPlayers()
        );
    }

    private static void writePlayer(Path levelRoot, UUID playerId, String suffix) throws IOException {
        Path playerData = levelRoot.resolve("players/data");
        Files.createDirectories(playerData);
        Files.writeString(playerData.resolve(playerId + suffix), "player");
    }

    private static Path marker(Path worldDirectory) {
        return worldDirectory.resolve("iris").resolve(WorldReplacementEntryGuard.MARKER_NAME);
    }
}
