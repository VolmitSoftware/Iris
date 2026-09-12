package art.arcane.iris.studio;

import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.format.Form;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PackDownloadProgressReporterTest {
    @Test
    public void determinateBarAlwaysContainsTwentyFourCells() {
        String bar = PackDownloadProgressReporter.determinateBar(0.5D);

        assertEquals("[" + "|".repeat(24) + "]", C.stripColor(bar));
        assertEquals(12, occurrences(bar, C.GREEN.toString()));
    }

    @Test
    public void indeterminateBarMovesFiveCellSegment() {
        String first = PackDownloadProgressReporter.indeterminateBar(0L);
        String moved = PackDownloadProgressReporter.indeterminateBar(1_000L);

        assertEquals("[" + "|".repeat(24) + "]", C.stripColor(first));
        assertEquals("[" + "|".repeat(24) + "]", C.stripColor(moved));
        assertEquals(5, occurrences(first, C.AQUA.toString()));
        assertEquals(5, occurrences(moved, C.AQUA.toString()));
        assertFalse(first.equals(moved));
    }

    @Test
    public void indeterminateProgressLineAnimatesWithoutAnotherTransferEvent() {
        PackDownloader.DownloadProgress progress = new PackDownloader.DownloadProgress(
                PackDownloader.DownloadPhase.VALIDATING,
                1_000_000L,
                -1L,
                2_000L,
                false
        );

        String first = PackDownloadProgressReporter.progressLine(progress, 0L);
        String moved = PackDownloadProgressReporter.progressLine(progress, 1_000L);

        assertFalse(first.equals(moved));
        assertFalse(PackDownloadProgressReporter.indeterminateProgress(0L)
                == PackDownloadProgressReporter.indeterminateProgress(1_000L));
    }

    @Test
    public void progressLinesIncludeTransferTotalsAndRate() {
        String determinate = C.stripColor(PackDownloadProgressReporter.progressLine(
                new PackDownloader.DownloadProgress(
                        PackDownloader.DownloadPhase.DOWNLOADING,
                        1_000_000L,
                        2_000_000L,
                        2_000L,
                        false
                )
        ));
        String indeterminate = C.stripColor(PackDownloadProgressReporter.progressLine(
                new PackDownloader.DownloadProgress(
                        PackDownloader.DownloadPhase.DOWNLOADING,
                        1_000_000L,
                        -1L,
                        2_000L,
                        false
                )
        ));

        assertTrue(determinate.contains("50%"));
        assertTrue(determinate.contains(Form.fileSize(1_000_000L) + "/" + Form.fileSize(2_000_000L)));
        assertTrue(determinate.contains(Form.fileSize(500_000L) + "/s"));
        assertTrue(indeterminate.contains(Form.fileSize(1_000_000L)));
        assertTrue(indeterminate.contains(Form.fileSize(500_000L) + "/s"));
        assertFalse(indeterminate.contains("%"));
    }

    @Test
    public void terminalPublishingEventDoesNotEraseTransferSummary() {
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(false);
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(sender, "overworld");
        reporter.start();
        reporter.onProgress(new PackDownloader.DownloadProgress(
                PackDownloader.DownloadPhase.DOWNLOADING,
                1_500_000L,
                2_000_000L,
                2_000L,
                false
        ));
        reporter.onProgress(new PackDownloader.DownloadProgress(
                PackDownloader.DownloadPhase.PUBLISHING,
                0L,
                -1L,
                0L,
                true
        ));
        reporter.succeed(new PackDownloader.PackInstallResult("overworld", true, false));

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(messages.capture());
        List<String> allMessages = messages.getAllValues();
        String completion = allMessages.getLast();
        assertTrue(C.stripColor(completion).contains(Form.fileSize(1_500_000L)));
        assertTrue(C.stripColor(completion).contains(Form.duration(2_000L, 1)));
    }

    @Test
    public void actionEmissionIsLimitedToFourUpdatesPerSecond() {
        assertFalse(PackDownloadProgressReporter.mayEmitAction(1_000L, 1_249L));
        assertTrue(PackDownloadProgressReporter.mayEmitAction(1_000L, 1_250L));
    }

    @Test
    public void executionCompletionCancelsReporterThatNeverEnteredWorker() {
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(false);
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(sender, "overworld");

        reporter.start();
        reporter.executionComplete();

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).sendMessage(messages.capture());
        assertTrue(C.stripColor(messages.getAllValues().getLast()).contains("cancelled"));
    }

    @Test
    public void signedRemoteUrlIsRedactedFromDownloaderDetails() {
        String signedUrl = "https://packs.example.test/world.zip?token=secret&expires=soon";
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(false);
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(
                sender,
                "Remote ZIP",
                signedUrl
        );

        reporter.detail("Downloading https://packs.example.test/world.zip?token=secret＆expires=soon");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).sendMessage(message.capture());
        String rendered = C.stripColor(message.getValue());
        assertTrue(rendered.contains("Remote ZIP"));
        assertFalse(rendered.contains("secret"));
        assertFalse(rendered.contains("https://"));
    }

    @Test
    public void listenerDisablesItselfAfterFirstDeliveryFailure() {
        VolmitSender sender = mock(VolmitSender.class);
        when(sender.isPlayer()).thenReturn(false);
        doThrow(new IllegalStateException("delivery unavailable")).when(sender).sendMessage(anyString());
        PackDownloadProgressReporter reporter = new PackDownloadProgressReporter(sender, "overworld");
        PackDownloader.DownloadProgress connecting = new PackDownloader.DownloadProgress(
                PackDownloader.DownloadPhase.CONNECTING,
                0L,
                -1L,
                0L,
                false
        );

        assertThrows(IllegalStateException.class, () -> reporter.onProgress(connecting));
        reporter.onProgress(connecting);

        verify(sender, times(1)).sendMessage(anyString());
    }

    @Test
    public void allDownloadPhasesHaveLocalizedLabels() {
        for (PackDownloader.DownloadPhase phase : PackDownloader.DownloadPhase.values()) {
            assertFalse(PackDownloadProgressReporter.phaseLabel(phase).isBlank());
        }
    }

    private static int occurrences(String value, String match) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(match, offset)) >= 0) {
            count++;
            offset += match.length();
        }
        return count;
    }
}
