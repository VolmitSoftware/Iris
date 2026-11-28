package art.arcane.iris.pack.loading;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class IrisDataSnippetMalformedValueTest {
    private static final long RUNAWAY_READ_TIMEOUT_MILLISECONDS = 20_000;
    private static final double DEFAULT_RANGE_MIN = 16;
    private static final double DEFAULT_RANGE_MAX = 32;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private IrisSettings previousSettings;
    private Path pack;
    private IrisData data;

    public static class RangeListHolder {
        public List<IrisRange> ranges;
        public KList<IrisRange> keyedRanges;
    }

    public static class RangeHolder {
        public IrisRange range;
    }

    @Before
    public void createData() throws IOException {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        pack = temporary.newFolder("pack").toPath();
        data = IrisData.get(new File(pack.toFile(), "."));
    }

    @After
    public void closeData() {
        data.close();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test(timeout = RUNAWAY_READ_TIMEOUT_MILLISECONDS)
    public void numberInAListOfSnippetTypesIsReplacedByTheDefaultAndReadingMovesOn() {
        RangeListHolder holder = data.getGson().fromJson(
                "{\"ranges\":[5,{\"min\":1,\"max\":2}],\"keyedRanges\":[true]}", RangeListHolder.class);

        assertEquals(2, holder.ranges.size());
        assertDefaultRange(holder.ranges.get(0));
        assertEquals(1, holder.ranges.get(1).getMin(), 0);
        assertEquals(1, holder.keyedRanges.size());
        assertDefaultRange(holder.keyedRanges.get(0));
    }

    @Test(timeout = RUNAWAY_READ_TIMEOUT_MILLISECONDS)
    public void nestedArrayInAListOfSnippetTypesIsReplacedByTheDefault() {
        RangeListHolder holder = data.getGson().fromJson("{\"ranges\":[[1,2],[[3]]]}", RangeListHolder.class);

        assertEquals(2, holder.ranges.size());
        assertDefaultRange(holder.ranges.get(0));
        assertDefaultRange(holder.ranges.get(1));
    }

    @Test(timeout = RUNAWAY_READ_TIMEOUT_MILLISECONDS)
    public void malformedFieldKeepsTheDefaultAndTheValuesAfterIt() {
        RangeHolder holder = data.getGson().fromJson("{\"range\":7}", RangeHolder.class);

        assertDefaultRange(holder.range);
    }

    @Test(timeout = RUNAWAY_READ_TIMEOUT_MILLISECONDS)
    public void wrongTypedEntryOfAListSnippetIsReplacedByTheDefault() throws IOException {
        Path snippet = pack.resolve("snippet/range/broken.json");
        Files.createDirectories(snippet.getParent());
        Files.writeString(snippet, "[[1]]");

        RangeListHolder holder = data.getGson().fromJson("{\"ranges\":[\"snippet/broken\"]}", RangeListHolder.class);

        assertEquals(1, holder.ranges.size());
        assertDefaultRange(holder.ranges.get(0));
    }

    private static void assertDefaultRange(IrisRange range) {
        assertNotNull(range);
        assertEquals(DEFAULT_RANGE_MIN, range.getMin(), 0);
        assertEquals(DEFAULT_RANGE_MAX, range.getMax(), 0);
    }
}
