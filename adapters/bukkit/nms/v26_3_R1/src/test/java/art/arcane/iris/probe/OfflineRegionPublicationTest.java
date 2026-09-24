package art.arcane.iris.probe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public final class OfflineRegionPublicationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void unpublishedRuntimeRejectsWorldRootsOutsideItsStagingDirectory() throws Exception {
        Path staging = temporary.newFolder("staging").toPath();
        Path outside = temporary.getRoot().toPath().resolve("outside");
        try (HeadlessNativeRuntime runtime = new HeadlessNativeRuntime(staging)) {
            assertThrows(IOException.class, () -> runtime.createHistory(
                    new RealPackProbeSupport.HistoryRequest(outside, null, null, 1L)));
        }
        assertFalse(Files.exists(outside));
    }

    @Test
    public void publishesOnlyAfterEveryFileAndDirectoryIsForced() throws Exception {
        for (boolean fail : List.of(true, false)) {
            Path output = temporary.newFolder().toPath();
            Path staged = Files.createDirectory(output.resolve("incomplete"));
            Path nested = Files.createDirectory(staged.resolve("world"));
            Path data = Files.writeString(nested.resolve("data"), "checkpoint");
            Path target = output.resolve("native-terrain-checkpoint");
            List<Path> forced = new ArrayList<>();
            try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
                if (invocation.getMethod().getParameterCount() != 2) {
                    return invocation.callRealMethod();
                }
                FileChannel actual = (FileChannel) invocation.callRealMethod();
                Path path = invocation.getArgument(0);
                FileChannel channel = mock(FileChannel.class, delegatesTo(actual));
                doAnswer(force -> {
                    if (path.equals(output)) {
                        assertTrue(Files.isDirectory(target));
                        assertFalse(Files.exists(staged));
                    } else {
                        assertFalse(Files.exists(target));
                    }
                    if (fail && path.equals(data)) {
                        throw new IOException("Expected publication sync failure");
                    }
                    actual.force(true);
                    forced.add(path);
                    return null;
                }).when(channel).force(true);
                return channel;
            })) {
                if (fail) {
                    assertThrows(IOException.class, () -> OfflineRegionGenerator.publish(staged, target));
                    assertTrue(Files.isRegularFile(data));
                    assertFalse(Files.exists(target));
                } else {
                    OfflineRegionGenerator.publish(staged, target);
                    assertEquals(List.of(data, nested, staged, output), forced);
                    assertEquals("checkpoint", Files.readString(target.resolve("world/data")));
                }
            }
        }
    }
}
