package art.arcane.iris.core.loader;

import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.object.noise.IrisStyledRange;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.LogLevel;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class IrisDataSnippetListReadTest {
    private static final Type STYLED_RANGES = new TypeToken<List<IrisStyledRange>>() {
    }.getType();
    private static final long READ_TIMEOUT_MILLIS = 10_000;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<String> errors = new ArrayList<>();
    private IrisPlatform previousPlatform;
    private IrisData data;

    @Before
    public void openDataAndCaptureErrors() throws Exception {
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            if (invocation.getArgument(0, LogLevel.class) == LogLevel.ERROR) {
                errors.add(invocation.getArgument(1, String.class));
            }
            return null;
        }).when(platform).log(any(LogLevel.class), anyString());
        IrisPlatforms.bind(platform);
        data = IrisData.openRuntime(temporaryFolder.newFolder("pack"));
        errors.clear();
    }

    @After
    public void closeDataAndRestorePlatform() {
        data.close();
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
        IrisServices.clear();
    }

    @Test(timeout = READ_TIMEOUT_MILLIS)
    public void badListEntryFallsBackToDefaultsAndTheRestOfTheListLoads() {
        String json = "[{\"min\": 1, \"max\": 2}, {\"min\": \"not a number\"}, {\"min\": 3, \"max\": 4}]";

        List<IrisStyledRange> ranges = data.getGson().fromJson(json, STYLED_RANGES);

        assertEquals(3, ranges.size());
        assertEquals(1, ranges.get(0).getMin(), 0);
        assertEquals(new IrisStyledRange().getMin(), ranges.get(1).getMin(), 0);
        assertEquals(3, ranges.get(2).getMin(), 0);
        assertEquals(4, ranges.get(2).getMax(), 0);
    }

    @Test(timeout = READ_TIMEOUT_MILLIS)
    public void badEntryErrorNamesTheTypeThePathAndTheException() {
        data.getGson().fromJson("[{\"min\": 1}, {\"min\": \"not a number\"}]", STYLED_RANGES);

        String error = errors.getFirst();
        assertTrue(error, error.contains("IrisStyledRange"));
        assertTrue(error, error.contains("$[1]"));
        assertTrue(error, error.contains("not a number"));
    }
}
