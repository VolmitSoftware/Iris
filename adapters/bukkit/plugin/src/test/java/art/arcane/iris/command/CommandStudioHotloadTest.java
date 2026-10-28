package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.localization.LanguageAudience;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandStudioHotloadTest {
    @Test
    public void hotloadAndRefreshReportWhenNoStudioIsOpen() throws Exception {
        RecordingSender sender = new RecordingSender();
        StudioSVC studio = mock(StudioSVC.class);
        Method hotload = CommandStudio.class.getDeclaredMethod("hotload");
        assertTrue(List.of(hotload.getAnnotation(Director.class).aliases()).contains("refresh"));

        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            iris.when(() -> Iris.service(StudioSVC.class)).thenReturn(studio);
            hotload.invoke(new TestCommand(sender));
            assertTrue(sender.messages.getLast(), sender.messages.getLast().contains("No open studio"));
        }
    }

    @Test
    public void completionFeedbackWaitsForTheHotload() {
        assertCompletionFeedback(null, "Studio hotloaded.");
    }

    @Test
    public void failedHotloadReportsFailureAndLogsTheCause() {
        assertCompletionFeedback(new IllegalStateException("Invalid pack"), "Studio hotload failed: Invalid pack");
    }

    private void assertCompletionFeedback(Throwable failure, String expected) {
        RecordingSender sender = new RecordingSender();
        StudioSVC studio = mock(StudioSVC.class);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        IrisProject project = new IrisProject(new File("tutorial"));
        project.setActiveProvider(generator);
        CompletableFuture<Void> pending = new CompletableFuture<>();
        UUID audience = UUID.randomUUID();
        when(studio.getActiveProject()).thenReturn(project);
        when(generator.hotloadAsync()).thenReturn(pending);

        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            iris.when(() -> Iris.service(StudioSVC.class)).thenReturn(studio);
            LanguageAudience.run(audience, () -> new TestCommand(sender).hotload());
            assertNull(LanguageAudience.current());

            verify(generator).hotloadAsync();
            assertEquals(1, sender.messages.size());
            assertTrue(sender.messages.getFirst().contains("Hotloading Studio"));
            if (failure == null) {
                pending.complete(null);
            } else {
                pending.completeExceptionally(failure);
                iris.verify(() -> Iris.reportError(failure));
            }
            assertEquals(2, sender.messages.size());
            assertTrue(sender.messages.getLast(), sender.messages.getLast().contains(expected));
            assertEquals(List.of(audience, audience), sender.audiences);
            assertNull(LanguageAudience.current());
        }
    }

    private static final class TestCommand extends CommandStudio {
        private final VolmitSender sender;

        private TestCommand(VolmitSender sender) {
            this.sender = sender;
        }

        @Override
        public VolmitSender sender() {
            return sender;
        }
    }

    private static final class RecordingSender extends VolmitSender {
        private final List<String> messages = new ArrayList<>();
        private final List<UUID> audiences = new ArrayList<>();

        private RecordingSender() {
            super(null);
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
            audiences.add(LanguageAudience.current());
        }
    }
}
