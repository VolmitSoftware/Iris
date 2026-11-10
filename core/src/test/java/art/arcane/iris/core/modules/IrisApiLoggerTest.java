package art.arcane.iris.core.modules;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Iris loads one pack many times while starting; its module summary belongs in the log once. */
public class IrisApiLoggerTest {
    private static final String SUMMARY = "Pack natureplanet uses 22 modules:";

    private final List<LogLevel> levels = new ArrayList<>();
    private IrisPlatform previousPlatform;

    @Before
    public void rememberPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void aRepeatedMessageIsOnlyADebugLine() {
        bindCapturingPlatform();
        System.Logger log = IrisApiLogger.showingEachMessageOnce("test");

        log.log(System.Logger.Level.INFO, SUMMARY);
        log.log(System.Logger.Level.INFO, SUMMARY);

        assertEquals(List.of(LogLevel.INFO, LogLevel.DEBUG), levels);
    }

    @Test
    public void aMessageLoggedBeforeThePlatformLogExistsIsShownAgain() {
        System.Logger log = IrisApiLogger.showingEachMessageOnce("test");
        log.log(System.Logger.Level.INFO, SUMMARY);

        bindCapturingPlatform();
        log.log(System.Logger.Level.INFO, SUMMARY);

        assertEquals(List.of(LogLevel.INFO), levels);
    }

    @Test
    public void differentMessagesAreEachShown() {
        bindCapturingPlatform();
        System.Logger log = IrisApiLogger.showingEachMessageOnce("test");

        log.log(System.Logger.Level.INFO, SUMMARY);
        log.log(System.Logger.Level.INFO, "Pack natureplanet uses 23 modules:");

        assertEquals(List.of(LogLevel.INFO, LogLevel.INFO), levels);
    }

    private void bindCapturingPlatform() {
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> levels.add(invocation.getArgument(0, LogLevel.class)))
                .when(platform).log(any(LogLevel.class), anyString());
        IrisPlatforms.bind(platform);
    }
}
