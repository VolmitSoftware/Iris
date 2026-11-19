package art.arcane.iris.diagnostics.splash;

import art.arcane.iris.BuildConstants;
import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.pack.BuiltInPackUpdates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class IrisSplashComposer {
    private static final String SPLASH_PADDING = " ".repeat(4);
    private static final int FIRST_SPARE_INFO_ROW = 7;
    private static final int SPARE_INFO_ROWS = 3;
    private static final int PACKS_SHOWN = 6;
    private static final int SHORT_COMMIT_LENGTH = 7;
    private static final int LABEL_WIDTH = 14;
    private static final String FACT_GAP = "   ";

    private IrisSplashComposer() {
    }

    public static String[] composeInfo(String version, String serverLine, InfoStyle style) {
        return composeInfo(version, serverLine, style, List.of());
    }

    public static String[] composeInfo(String version, String serverLine, InfoStyle style, List<List<Detail>> detailRows) {
        String[] info = composeHeader(version, serverLine, style);
        int rows = Math.min(detailRows.size(), SPARE_INFO_ROWS);
        for (int row = 0; row < rows; row++) {
            info[FIRST_SPARE_INFO_ROW + row] = style.linePrefix() + styledRow(detailRows.get(row), style);
        }
        return info;
    }

    public static String plainRow(List<Detail> row) {
        return styledRow(row, InfoStyle.PLAIN).strip();
    }

    public static String packSummary(List<IrisSplashPackScanner.SplashPackMetadata> packs) {
        if (packs.isEmpty()) {
            return "none";
        }
        String shown = packs.stream()
                .limit(PACKS_SHOWN)
                .map(pack -> pack.name() + " v" + pack.version())
                .collect(Collectors.joining(", "));
        int hidden = packs.size() - PACKS_SHOWN;
        return hidden > 0 ? shown + " +" + hidden + " more" : shown;
    }

    /** The first fact's value starts in the shared value column; further facts on the row follow after a gap. */
    private static String styledRow(List<Detail> row, InfoStyle style) {
        StringBuilder styled = new StringBuilder();
        for (Detail detail : row) {
            String label = styled.isEmpty()
                    ? " " + padRight(detail.label(), LABEL_WIDTH)
                    : FACT_GAP + detail.label() + " ";
            styled.append(style.label(label))
                    .append(detail.headline() ? style.headlineValue(detail.value()) : style.value(detail.value()));
        }
        return styled.toString();
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(1, width - text.length()));
    }

    private static String shortCommit() {
        String commit = BuildConstants.COMMIT;
        return commit.length() > SHORT_COMMIT_LENGTH ? commit.substring(0, SHORT_COMMIT_LENGTH) : commit;
    }

    private static String[] composeHeader(String version, String serverLine, InfoStyle style) {
        String releaseTrain = releaseTrain(version);
        String prefix = style.linePrefix();
        String java = javaVersion() < 0 ? "unknown" : String.valueOf(javaVersion());
        return new String[]{
                prefix + style.title(" Iris, ") + style.subtitle("Dimension Engine ") + style.tag("[" + releaseTrain + "]"),
                prefix + styledRow(List.of(Detail.headline("Version", version)), style),
                prefix + styledRow(List.of(new Detail("By", "Volmit Software (Arcane Arts)")), style),
                prefix + styledRow(List.of(new Detail("Web", "volmitsoftware.com")), style),
                prefix + styledRow(List.of(Detail.headline("Server", serverLine)), style),
                prefix + styledRow(List.of(new Detail("Java", java), new Detail("Date", startupDate())), style),
                prefix + styledRow(List.of(new Detail("Commit", shortCommit() + "/" + BuildConstants.ENVIRONMENT)), style),
                "",
                "",
                "",
                ""
        };
    }

    public static String compose(String[] splash, String[] info) {
        StringBuilder builder = new StringBuilder("\n\n");
        for (int i = 0; i < splash.length; i++) {
            builder.append(SPLASH_PADDING).append(splash[i]).append(info[i]).append('\n');
        }
        return builder.toString();
    }

    public static CompletableFuture<List<String>> composeUpdateLines(List<IrisSplashPackScanner.SplashPackMetadata> packs) {
        return BuiltInPackUpdates.check(packs.stream().map(IrisSplashPackScanner.SplashPackMetadata::name).toList())
                .thenApply(updates -> composeUpdateLines(packs, updates));
    }

    static List<String> composeUpdateLines(List<IrisSplashPackScanner.SplashPackMetadata> packs,
                                         Map<String, BuiltInPackUpdates.Update> updates) {
        List<String> lines = new ArrayList<>();
        for (IrisSplashPackScanner.SplashPackMetadata pack : packs) {
            BuiltInPackUpdates.Update update = updates.get(pack.name());
            if (update != null && update.newerThan(pack.version())) {
                lines.addAll(ChatReport.of(ChatReport.Tone.INFO, "Pack " + pack.name() + " v" + update.version()
                                + " is available. You have v" + pack.version() + ".")
                        .nextStep("Update it", "/iris download pack=" + pack.name() + " overwrite=true")
                        .build()
                        .consoleLines());
            }
        }
        return lines;
    }

    /**
     * Total parser: java.version values like "25-ea" or "26-internal" have no dot but a
     * non-numeric suffix, and a cosmetic banner must never be able to abort startup.
     *
     * @return the feature version, or -1 when it cannot be determined
     */
    public static int javaVersion() {
        String version = System.getProperty("java.version", "");
        if (version.startsWith("1.")) {
            version = version.length() > 2 ? version.substring(2, 3) : "";
        }
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        return end == 0 ? -1 : Integer.parseInt(version, 0, end, 10);
    }

    public static String releaseTrain(String version) {
        String value = version == null ? "unknown" : version;
        int suffixIndex = value.indexOf('-');
        if (suffixIndex >= 0) {
            value = value.substring(0, suffixIndex);
        }
        String[] split = value.split("\\.");
        if (split.length >= 2) {
            return split[0] + "." + split[1];
        }
        return value;
    }

    private static String startupDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** A headline fact is what support asks for first, so its value stands out from the rest. */
    public record Detail(String label, String value, boolean headline) {
        public Detail(String label, String value) {
            this(label, value, false);
        }

        public static Detail headline(String label, String value) {
            return new Detail(label, value, true);
        }
    }

    public interface InfoStyle {
        InfoStyle PLAIN = new InfoStyle() {
        };

        default String linePrefix() {
            return "";
        }

        default String title(String text) {
            return text;
        }

        default String subtitle(String text) {
            return text;
        }

        default String tag(String text) {
            return text;
        }

        default String label(String text) {
            return text;
        }

        default String value(String text) {
            return text;
        }

        default String headlineValue(String text) {
            return value(text);
        }
    }
}
