package art.arcane.iris.testsupport;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Binds a mock platform that records every log line and reported stack trace. {@link #close()} unbinds it,
 * which also clears the JVM-global warn-once keys, so tests using it do not depend on order.
 */
public final class CapturedLog implements AutoCloseable {
    private final List<LogLevel> levels = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> reportedErrors = new ArrayList<>();

    private CapturedLog() {
    }

    public static CapturedLog bind() {
        return bind(null);
    }

    /**
     * @param dataRoot where the platform keeps its data files (settings are read from there on first use);
     *                 null for a platform that has none
     */
    public static CapturedLog bind(File dataRoot) {
        IrisPlatforms.unbind();
        CapturedLog log = new CapturedLog();
        IrisPlatform platform = mock(IrisPlatform.class);
        if (dataRoot != null) {
            doReturn(dataRoot).when(platform).dataFolder();
            doAnswer(invocation -> {
                Object[] segments = invocation.getArguments();
                return new File(dataRoot, String.join(File.separator, Arrays.copyOf(segments, segments.length, String[].class)));
            }).when(platform).dataFile(any(String[].class));
        }
        doAnswer(invocation -> {
            log.levels.add(invocation.getArgument(0, LogLevel.class));
            log.messages.add(invocation.getArgument(1, String.class));
            return null;
        }).when(platform).log(any(LogLevel.class), anyString());
        doAnswer(invocation -> {
            log.reportedErrors.add(invocation.getArgument(0, Throwable.class));
            return null;
        }).when(platform).reportError(any(Throwable.class));
        IrisPlatforms.bind(platform);
        return log;
    }

    /** Messages logged at WARN that start with {@code prefix}. */
    public List<String> warnings(String prefix) {
        List<String> found = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (levels.get(index) == LogLevel.WARN && messages.get(index).startsWith(prefix)) {
                found.add(messages.get(index));
            }
        }
        return found;
    }

    public List<String> messages() {
        return messages;
    }

    public List<Throwable> reportedErrors() {
        return reportedErrors;
    }

    @Override
    public void close() {
        IrisPlatforms.unbind();
    }
}
