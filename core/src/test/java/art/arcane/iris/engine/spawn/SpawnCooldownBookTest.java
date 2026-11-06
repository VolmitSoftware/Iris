package art.arcane.iris.engine.spawn;

import com.volmit.data.Codec;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** When spawners last fired: counted per spawner in the world and in each chunk, forgotten once spent, and kept across saves. */
public class SpawnCooldownBookTest {
    private static final long TEN_MINUTES = 600_000L;
    private static final long START = 1_000_000L;

    private AtomicLong now;
    private SpawnCooldownBook book;

    @Before
    public void openBook() {
        now = new AtomicLong(START);
        book = new SpawnCooldownBook(now::get);
    }

    @Test
    public void aSpawnerThatNeverFiredMayFire() {
        assertTrue(book.canSpawn("wolves", TEN_MINUTES));
        assertTrue(book.canSpawnInChunk(3, 4, "wolves", TEN_MINUTES));
    }

    @Test
    public void aSpawnerMayFireAgainOnceItsIntervalHasPassed() {
        book.spawned("wolves");

        assertFalse(book.canSpawn("wolves", TEN_MINUTES));
        now.addAndGet(TEN_MINUTES);
        assertFalse("the interval must pass, not just be reached", book.canSpawn("wolves", TEN_MINUTES));
        now.addAndGet(1);
        assertTrue(book.canSpawn("wolves", TEN_MINUTES));
    }

    @Test
    public void theWorldAndEveryChunkCountSeparatelyPerSpawner() {
        book.spawnedInChunk(3, 4, "wolves");

        assertFalse(book.canSpawnInChunk(3, 4, "wolves", TEN_MINUTES));
        assertTrue(book.canSpawnInChunk(3, 5, "wolves", TEN_MINUTES));
        assertTrue(book.canSpawnInChunk(3, 4, "bats", TEN_MINUTES));
        assertTrue("a chunk's spawn is not the world's", book.canSpawn("wolves", TEN_MINUTES));
    }

    @Test
    public void forgettingKeepsCooldownsStillRunningAndDropsSpentOnesAndSpawnersThePackLacks() {
        book.spawned("wolves");
        book.spawnedInChunk(3, 4, "wolves");
        book.spawned("gone");
        book.spawnedInChunk(3, 4, "gone");
        book.spawned("quick");
        now.addAndGet(1_000L);

        book.forgetExpired(spawner -> switch (spawner) {
            case "wolves" -> TEN_MINUTES;
            case "quick" -> 500L;
            default -> -1L;
        });

        assertFalse(book.canSpawn("wolves", TEN_MINUTES));
        assertFalse(book.canSpawnInChunk(3, 4, "wolves", TEN_MINUTES));
        assertTrue(book.canSpawn("gone", TEN_MINUTES));
        assertTrue(book.canSpawnInChunk(3, 4, "gone", TEN_MINUTES));
        assertTrue(book.canSpawn("quick", 500L));
    }

    @Test
    public void aSavedBookKeepsWhatFiredInTheWorldAndInChunks() throws IOException {
        book.spawned("wolves");
        book.spawnedInChunk(-3, 4, "wolves");
        book.spawnedInChunk(-3, 4, "bats");

        SpawnCooldownBook restored = roundTrip(book);

        assertFalse(restored.canSpawn("wolves", TEN_MINUTES));
        assertFalse(restored.canSpawnInChunk(-3, 4, "wolves", TEN_MINUTES));
        assertFalse(restored.canSpawnInChunk(-3, 4, "bats", TEN_MINUTES));
        assertTrue(restored.canSpawn("bats", TEN_MINUTES));
        assertTrue(restored.canSpawnInChunk(3, 4, "wolves", TEN_MINUTES));
    }

    @Test
    public void aSavedBookCountsTheTimeThatPassedWhileTheWorldWasStopped() throws IOException {
        book.spawned("wolves");
        SpawnCooldownBook restored = roundTrip(book);

        now.addAndGet(TEN_MINUTES + 1);

        assertTrue(restored.canSpawn("wolves", TEN_MINUTES));
    }

    @Test
    public void anEmptyBookIsSavedAndRestoredEmpty() throws IOException {
        assertTrue(roundTrip(book).canSpawn("wolves", TEN_MINUTES));
    }

    @Test
    public void aBookOfAnotherFormatVersionIsRejected() {
        byte[] stored = write(book);

        assertThrows(IOException.class, () -> codec().read(new DataInputStream(new ByteArrayInputStream(stored)), codec().version() + 1));
    }

    @Test
    public void aTruncatedBookIsRejected() {
        book.spawned("wolves");
        byte[] stored = write(book);
        byte[] truncated = java.util.Arrays.copyOf(stored, stored.length - 1);

        assertThrows(EOFException.class, () -> codec().read(new DataInputStream(new ByteArrayInputStream(truncated)), codec().version()));
    }

    @Test
    public void aNegativeEntryCountIsRejected() {
        byte[] negativeCount = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};

        assertThrows(IOException.class, () -> codec().read(new DataInputStream(new ByteArrayInputStream(negativeCount)), codec().version()));
    }

    @Test
    public void theBookTheWorldKeepsIsSavedWithTheSavedKey() {
        assertTrue(SpawnCooldownBook.SAVED.id().startsWith("iris:spawn/"));
        assertTrue(SpawnCooldownBook.SAVED.initial().get().canSpawn("wolves", TEN_MINUTES));
    }

    private Codec<SpawnCooldownBook> codec() {
        return SpawnCooldownBook.codec(now::get);
    }

    private SpawnCooldownBook roundTrip(SpawnCooldownBook source) throws IOException {
        return codec().read(new DataInputStream(new ByteArrayInputStream(write(source))), codec().version());
    }

    private byte[] write(SpawnCooldownBook source) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            codec().write(output, source);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return bytes.toByteArray();
    }
}
