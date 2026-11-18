package art.arcane.iris.pack;

import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.pack.validation.PackCompatReport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Console output for pack validation at startup, shared by every platform so the boot log reads the same. */
public final class PackBootReporter {
    private static final int BOOT_ITEM_LIMIT = 5;
    private static final int COMPAT_KEYS_SHOWN = 2;
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\\[])");

    private PackBootReporter() {
    }

    public static void logBroken(PackValidationResult result, String minecraftVersion) {
        String pack = result.getPackName();
        logReport(ChatReport.Tone.ERROR,
                "Pack '" + pack + "' failed validation, worlds and Studio refuse it",
                result, minecraftVersion, result.getBlockingErrors());
    }

    public static void logValidated(PackValidationResult result, String minecraftVersion, boolean announceClean) {
        String pack = result.getPackName();
        int warnings = result.getWarnings().size();
        if (warnings > 0) {
            logReport(ChatReport.Tone.WARNING,
                    "Pack '" + pack + "': " + warnings + (warnings == 1 ? " warning" : " warnings"),
                    result, minecraftVersion, result.getWarnings());
            return;
        }
        if (!result.getCompatFindings().isEmpty()) {
            logReport(ChatReport.Tone.WARNING, "Pack '" + pack + "': content unavailable",
                    result, minecraftVersion, List.of());
            return;
        }
        if (announceClean) {
            ChatReport.of(ChatReport.Tone.SUCCESS, "Pack '" + pack + "' validated").build().log();
        }
    }

    private static void logReport(ChatReport.Tone tone, String title, PackValidationResult result,
                                  String minecraftVersion, List<String> reasons) {
        String pack = result.getPackName();
        List<String> items = new ArrayList<>(reasons.size() + 1);
        reasons.forEach(reason -> items.add(firstSentence(reason)));
        String details = "/iris pack validate " + pack;
        if (!result.getCompatFindings().isEmpty()) {
            items.add(PackCompatReport.of(result.getCompatFindings())
                    .bootItem(PackValidator.versionOf(result, minecraftVersion), COMPAT_KEYS_SHOWN));
            details += ", /iris pack compat " + pack;
        }
        ChatReport.of(tone, title).items(items).limit(BOOT_ITEM_LIMIT).hint("Details: " + details, null).build().log();
    }

    private static String firstSentence(String reason) {
        return SENTENCE_BREAK.split(reason, 2)[0];
    }
}
