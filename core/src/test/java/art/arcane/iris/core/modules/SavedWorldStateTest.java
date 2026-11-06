package art.arcane.iris.core.modules;

import art.arcane.iris.core.lifecycle.VanishedWorldStorage;
import com.volmit.ModuleLog;
import com.volmit.data.Codec;
import com.volmit.world.SavedKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** What modules keep with a running world: read when first asked for, written on save, and never blocking the world when the stored data is bad. */
public class SavedWorldStateTest {
    private static final int CODEC_VERSION = 2;
    private static final int INITIAL_COUNT = 5;

    @Rule
    public TemporaryFolder worldFolder = new TemporaryFolder();

    private ModuleLog log;
    private SavedWorldState state;

    @Before
    public void startWorld() {
        VanishedWorldStorage.reset();
        log = mock(ModuleLog.class);
        state = new SavedWorldState(worldFolder::getRoot, log);
    }

    @After
    public void forgetVanishedWorlds() {
        VanishedWorldStorage.reset();
    }

    /** The count a module keeps, stored as one int and rejecting every other version. */
    private static final class Count {
        private final AtomicInteger value;

        private Count(int value) {
            this.value = new AtomicInteger(value);
        }
    }

    private static final Codec<Count> COUNT_CODEC = new Codec<>() {
        @Override
        public int version() {
            return CODEC_VERSION;
        }

        @Override
        public void write(DataOutput output, Count count) throws IOException {
            output.writeInt(count.value.get());
        }

        @Override
        public Count read(DataInput input, int storedVersion) throws IOException {
            if (storedVersion != CODEC_VERSION) {
                throw new IOException("Unsupported version " + storedVersion);
            }
            return new Count(input.readInt());
        }
    };

    private static final SavedKey<Count> COUNT = new SavedKey<>("test:counts/waves", Count.class, COUNT_CODEC,
            () -> new Count(INITIAL_COUNT));

    @Test
    public void aWorldWithNothingStoredStartsWithTheInitialValueAndKeepsOneInstance() {
        Count first = state.saved(COUNT);

        assertEquals(INITIAL_COUNT, first.value.get());
        assertSame(first, state.saved(COUNT));
    }

    @Test
    public void whatTheModuleChangedIsReadBackByTheWorldStartedAfterAStop() {
        state.saved(COUNT).value.set(42);
        state.saveAll();

        SavedWorldState restarted = new SavedWorldState(worldFolder::getRoot, log);

        assertEquals(42, restarted.saved(COUNT).value.get());
    }

    @Test
    public void aValueIsStoredBelowTheWorldsModuleDataByNamespaceAndPath() throws IOException {
        state.saved(COUNT);
        state.saveAll();

        File stored = new File(worldFolder.getRoot(), "iris/module-data/test/counts/waves.bin");
        byte[] codecVersionThenPayload = {0, 0, 0, CODEC_VERSION, 0, 0, 0, INITIAL_COUNT};
        assertArrayEquals(codecVersionThenPayload, Files.readAllBytes(stored.toPath()));
    }

    @Test
    public void aPayloadTheCodecRejectsIsLoggedAndReplacedByTheInitialValue() throws IOException {
        store(new byte[]{0, 0, 0, 1, 0, 0, 0, 9});

        Count loaded = state.saved(COUNT);

        assertEquals(INITIAL_COUNT, loaded.value.get());
        verify(log).warn(contains("test:counts/waves"));
    }

    @Test
    public void aTruncatedOrEmptyFileIsRejectedLikeAnyOtherBadPayload() throws IOException {
        store(new byte[]{0, 0, 0, CODEC_VERSION, 0, 0});
        assertEquals(INITIAL_COUNT, state.saved(COUNT).value.get());

        SavedKey<Count> other = new SavedKey<>("test:counts/other", Count.class, COUNT_CODEC, () -> new Count(INITIAL_COUNT));
        store("other", new byte[0]);
        assertEquals(INITIAL_COUNT, state.saved(other).value.get());
    }

    @Test
    public void aRejectedPayloadIsOverwrittenByTheNextSave() throws IOException {
        store(new byte[]{1, 2, 3});
        state.saved(COUNT).value.set(7);

        state.saveAll();

        assertEquals(7, new SavedWorldState(worldFolder::getRoot, log).saved(COUNT).value.get());
    }

    @Test
    public void aCodecThatReadsNothingIsRejected() throws IOException {
        Codec<Count> nothing = new Codec<>() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public void write(DataOutput output, Count count) {
            }

            @Override
            public Count read(DataInput input, int storedVersion) {
                return null;
            }
        };
        SavedKey<Count> key = new SavedKey<>("test:counts/nothing", Count.class, nothing, () -> new Count(INITIAL_COUNT));
        store("nothing", new byte[]{0, 0, 0, 1});

        assertEquals(INITIAL_COUNT, state.saved(key).value.get());
        verify(log).warn(contains("test:counts/nothing"));
    }

    @Test
    public void theSameIdAskedForAnotherTypeIsRefused() {
        state.saved(COUNT);
        Codec<String> text = new Codec<>() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public void write(DataOutput output, String value) throws IOException {
                output.writeUTF(value);
            }

            @Override
            public String read(DataInput input, int storedVersion) throws IOException {
                return input.readUTF();
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> state.saved(new SavedKey<>("test:counts/waves", String.class, text, () -> "")));
    }

    @Test
    public void savingWritesNothingWhenNoValueWasAskedFor() {
        state.saveAll();

        assertFalse(new File(worldFolder.getRoot(), "iris").exists());
    }

    @Test
    public void aFailingCodecIsReportedAndDoesNotStopTheOtherValuesFromBeingSaved() {
        Codec<Count> failing = new Codec<>() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public void write(DataOutput output, Count count) throws IOException {
                throw new IOException("cannot write");
            }

            @Override
            public Count read(DataInput input, int storedVersion) {
                return new Count(0);
            }
        };
        state.saved(new SavedKey<>("test:counts/broken", Count.class, failing, () -> new Count(0)));
        state.saved(COUNT).value.set(9);

        state.saveAll();

        verify(log).error(contains("test:counts/broken"), any(IOException.class));
        assertEquals(9, new SavedWorldState(worldFolder::getRoot, log).saved(COUNT).value.get());
        assertFalse(new File(worldFolder.getRoot(), "iris/module-data/test/counts/broken.bin").exists());
    }

    @Test
    public void aWorldWhoseFolderIsGoneIsNotRebuiltBySaving() {
        File vanishedFolder = new File(worldFolder.getRoot(), "gone");
        SavedWorldState inVanishedWorld = new SavedWorldState(() -> vanishedFolder, log);
        inVanishedWorld.saved(COUNT);

        inVanishedWorld.saveAll();

        assertFalse(vanishedFolder.exists());
        verify(log, never()).error(any(), any());
    }

    private void store(byte[] payload) throws IOException {
        store("waves", payload);
    }

    private void store(String name, byte[] payload) throws IOException {
        File file = new File(worldFolder.getRoot(), "iris/module-data/test/counts/" + name + ".bin");
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), payload);
    }
}
