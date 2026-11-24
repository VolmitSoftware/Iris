package art.arcane.iris.structure;

import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat output for a bulk import. Per-item successes only update the action bar, per-item problems are held back,
 * and {@link #finish(String)} sends one summary report that lists the problems.
 */
public final class ImportFeed {
    private static final int CONSOLE_PROGRESS_INTERVAL = 50;

    private final VolmitSender sender;
    private final List<String> problems = new ArrayList<>();

    public ImportFeed(VolmitSender sender) {
        this.sender = sender;
    }

    public void start(String message) {
        sender.sendInfo(message);
    }

    public void success(String message) {
        if (sender.isPlayer()) {
            sender.sendAction(ChatReport.Tone.SUCCESS.paint(message));
        }
    }

    public void problem(String message) {
        problems.add(message);
        if (sender.isPlayer()) {
            sender.sendAction(ChatReport.Tone.WARNING.paint(message));
        }
    }

    public void progress(int processed, int total, String message) {
        if (sender.isPlayer()) {
            sender.sendProgress(total <= 0 ? 1D : (double) processed / total, message);
            return;
        }
        if (processed % CONSOLE_PROGRESS_INTERVAL == 0) {
            sender.sendInfo(message);
        }
    }

    public void finish(String summary) {
        ChatReport.Tone tone = problems.isEmpty() ? ChatReport.Tone.SUCCESS : ChatReport.Tone.WARNING;
        sender.sendReport(ChatReport.of(tone, summary).items(problems).build());
        problems.clear();
    }
}
