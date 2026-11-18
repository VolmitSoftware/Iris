package art.arcane.iris.pack;

import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.spi.IrisLogging;

import java.util.List;

/** Console output for pack validation at startup, shared by every platform so the boot log reads the same. */
public final class PackBootReporter {
    private static final int BOOT_ITEM_LIMIT = 5;
    private static final int COMPAT_SUBJECTS_PER_KEY = 3;

    private PackBootReporter() {
    }

    public static void logBroken(PackValidationResult result) {
        String pack = result.getPackName();
        logCapped(ChatReport.Tone.ERROR,
                "Pack '" + pack + "' failed validation; world and Studio creation with it are refused",
                pack, result.getBlockingErrors());
    }

    public static void logValidated(PackValidationResult result, String minecraftVersion, boolean announceClean) {
        String pack = result.getPackName();
        String compatSummary = PackValidator.compatSummary(result, minecraftVersion);
        String compatSuffix = compatSummary.isEmpty() ? "" : " " + compatSummary;
        if (!result.getWarnings().isEmpty()) {
            logCapped(ChatReport.Tone.WARNING,
                    "Pack '" + pack + "' validated with " + result.getWarnings().size() + " warning(s)." + compatSuffix,
                    pack, result.getWarnings());
            return;
        }
        if (announceClean || !compatSummary.isEmpty()) {
            IrisLogging.info("Pack '" + pack + "' validated." + compatSuffix);
        }
    }

    public static void logCompat(PackValidationResult result, String minecraftVersion) {
        if (result == null || result.getCompatFindings().isEmpty()) {
            return;
        }
        PackCompatReport.of(result.getCompatFindings())
                .bootReport(result.getPackName(), PackValidator.versionOf(result, minecraftVersion),
                        COMPAT_SUBJECTS_PER_KEY, BOOT_ITEM_LIMIT)
                .log();
    }

    private static void logCapped(ChatReport.Tone tone, String title, String pack, List<String> items) {
        ChatReport.Builder report = ChatReport.of(tone, title).items(items).limit(BOOT_ITEM_LIMIT);
        if (items.size() > BOOT_ITEM_LIMIT) {
            report.hint("/iris pack validate " + pack + " lists everything", null);
        }
        report.build().log();
    }
}
