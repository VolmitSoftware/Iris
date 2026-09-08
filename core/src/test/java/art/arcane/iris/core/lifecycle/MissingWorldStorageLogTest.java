package art.arcane.iris.core.lifecycle;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Paper runs the plugin bootstrap before any plugin logger exists, so a warning raised there reaches the
 * console and the runtime log but never the instance's logs/latest.log - the only log most operators read.
 */
public class MissingWorldStorageLogTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final List<String> emitted = new ArrayList<>();
    private final List<LogLevel> levels = new ArrayList<>();
    private IrisPlatform previousPlatform;

    @Before
    public void captureLog() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        MissingWorldStorageLog.reset();
        emitted.clear();
        levels.clear();
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
        MissingWorldStorageLog.reset();
    }

    @Test
    public void bootstrapWarningsAreReplayedOnceThePlatformLogIsUp() {
        MissingWorldStorageLog.warnOnce("world_iris_gone", Path.of("/srv/world/dimensions/iris/gone"));
        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_gone"));

        bindCapturingPlatform();
        MissingWorldStorageLog.replayToPlatformLog();

        assertEquals(2, emitted.size());
        assertTrue(emitted.get(0), emitted.get(0).contains("world_iris_gone"));
        assertTrue(emitted.get(0),
                emitted.get(0).contains(Path.of("/srv/world/dimensions/iris/gone").toString()));
    }

    @Test
    public void replayIsIdempotentAndNeverRepeatsAWarningThePlatformAlreadySaw() {
        MissingWorldStorageLog.warnOnce("world_iris_gone", Path.of("/srv/world/dimensions/iris/gone"));
        bindCapturingPlatform();
        MissingWorldStorageLog.replayToPlatformLog();
        emitted.clear();

        MissingWorldStorageLog.replayToPlatformLog();
        MissingWorldStorageLog.warnOnce("world_iris_later", Path.of("/srv/world/dimensions/iris/later"));
        int afterLiveWarning = emitted.size();
        MissingWorldStorageLog.replayToPlatformLog();

        assertEquals(afterLiveWarning, emitted.size());
    }

    @Test
    public void anEmptyWorldFolderIsReportedWithItsOwnRemedy() {
        bindCapturingPlatform();

        MissingWorldStorageLog.warnEmptyOnce("world_iris_husk", Path.of("/srv/world/dimensions/iris/husk"));

        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_husk"));
        assertEquals(2, emitted.size());
        assertTrue(emitted.get(0),
                emitted.get(0).contains(Path.of("/srv/world/dimensions/iris/husk").toString()));
        assertTrue(emitted.get(1), emitted.get(1).contains("world_iris_husk"));
        assertFalse("an empty folder is deleted, not restored", emitted.get(1).contains("Restore"));
    }

    /**
     * An orphan report is what an operator scans logs/latest.log at WARN level for; emitting it at INFO makes
     * it invisible to exactly the search that would find it.
     */
    @Test
    public void everyOrphanReportIsRaisedAtWarnLevel() {
        bindCapturingPlatform();

        MissingWorldStorageLog.warnOnce("world_iris_gone", Path.of("/srv/world/dimensions/iris/gone"));
        MissingWorldStorageLog.warnEmptyOnce("world_iris_husk", Path.of("/srv/world/dimensions/iris/husk"));

        assertEquals(4, levels.size());
        assertTrue(levels.toString(), levels.stream().allMatch(level -> level == LogLevel.WARN));
    }

    @Test
    public void replayedOrphanReportsKeepTheirWarnLevel() {
        MissingWorldStorageLog.warnOnce("world_iris_gone", Path.of("/srv/world/dimensions/iris/gone"));
        bindCapturingPlatform();

        MissingWorldStorageLog.replayToPlatformLog();

        assertEquals(2, levels.size());
        assertTrue(levels.toString(), levels.stream().allMatch(level -> level == LogLevel.WARN));
    }

    private void bindCapturingPlatform() {
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            levels.add(invocation.getArgument(0, LogLevel.class));
            emitted.add(invocation.getArgument(1, String.class));
            return null;
        }).when(platform).log(org.mockito.ArgumentMatchers.any(LogLevel.class), org.mockito.ArgumentMatchers.anyString());
        IrisPlatforms.bind(platform);
    }
}
