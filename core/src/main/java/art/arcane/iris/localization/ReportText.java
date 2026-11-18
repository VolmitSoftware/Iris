package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Styles plain report text so long reasons scan quickly: names and keys get their own color, the explanation after
 * the first sentence is dimmed, and reasons that differ only in their subject collapse into one entry.
 */
final class ReportText {
    private static final int GROUP_SUBJECTS_SHOWN = 4;
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\\[])");
    private static final String QUOTED_NAME = "(?<![A-Za-z])'[^']+'(?![A-Za-z])";
    private static final Pattern QUOTED = Pattern.compile(QUOTED_NAME);
    private static final Pattern TOKEN = Pattern.compile(
            "(?<quoted>" + QUOTED_NAME + ")"
                    + "|(?<command>/iris(?: [\\w:=./-]+)+)"
                    + "|(?<key>\\b[a-z0-9_.-]+:[a-z0-9_./-]+)"
                    + "|(?<path>\\b[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*|\\[\\d+])+)");
    private static final Pattern SUBJECT_REASON = Pattern.compile("^([A-Z][\\w ]*?) '([^']+)'(.+)$", Pattern.DOTALL);
    private static final Pattern LEADING_JOINER = Pattern.compile("^[.\\s]+");

    private ReportText() {
    }

    static String title(ChatReport.Tone tone, String plain) {
        String base = tone.color().toString() + C.BOLD;
        return base + highlightQuoted(plain, C.WHITE.toString() + C.BOLD, base);
    }

    static String body(String plain) {
        Matcher sentenceBreak = SENTENCE_BREAK.matcher(plain);
        if (!sentenceBreak.find()) {
            return C.GRAY + highlightTokens(plain, C.GRAY.toString());
        }
        String head = plain.substring(0, sentenceBreak.start());
        String tail = plain.substring(sentenceBreak.end());
        return C.GRAY + highlightTokens(head, C.GRAY.toString()) + " " + C.DARK_GRAY + tail;
    }

    /** Groups reasons shaped like {@code Kind 'subject' rest} that share kind and rest; other reasons stay single. */
    static List<List<String>> groupedBodies(List<String> plainReasons, String continuationIndent) {
        Map<String, SubjectGroup> groups = new LinkedHashMap<>();
        for (String reason : plainReasons) {
            Matcher matcher = SUBJECT_REASON.matcher(reason);
            if (!matcher.matches()) {
                groups.put("\u0000" + groups.size(), new SubjectGroup(null, reason));
                continue;
            }
            String kind = matcher.group(1);
            String rest = LEADING_JOINER.matcher(matcher.group(3)).replaceFirst("");
            groups.computeIfAbsent(kind + "\u0000" + rest, ignored -> new SubjectGroup(kind, rest))
                    .add(matcher.group(2), reason);
        }

        List<List<String>> entries = new ArrayList<>(groups.size());
        for (SubjectGroup group : groups.values()) {
            entries.add(group.render(continuationIndent));
        }
        return entries;
    }

    private static String highlightQuoted(String plain, String highlight, String base) {
        Matcher matcher = QUOTED.matcher(plain);
        StringBuilder styled = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(styled, Matcher.quoteReplacement(highlight + matcher.group() + base));
        }
        matcher.appendTail(styled);
        return styled.toString();
    }

    private static String highlightTokens(String plain, String base) {
        Matcher matcher = TOKEN.matcher(plain);
        StringBuilder styled = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(styled, Matcher.quoteReplacement(tokenColor(matcher) + matcher.group() + base));
        }
        matcher.appendTail(styled);
        return styled.toString();
    }

    private static String tokenColor(Matcher matcher) {
        if (matcher.group("quoted") != null) {
            return C.WHITE.toString();
        }
        if (matcher.group("command") != null) {
            return C.IRIS.toString();
        }
        return C.AQUA.toString();
    }

    static String moreLabel(int hidden) {
        return ChatReport.clean(IrisLanguage.plain(RuntimeUiMessages.REPORT_MORE, MessageArgument.trusted("count", hidden)));
    }

    private static String plural(String kind) {
        String lower = kind.toLowerCase(Locale.ROOT);
        if (lower.endsWith("s")) {
            return lower;
        }
        if (lower.endsWith("y") && !lower.endsWith("ey")) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        return lower + "s";
    }

    private static final class SubjectGroup {
        private final String kind;
        private final String rest;
        private final List<String> subjects = new ArrayList<>();
        private String singleReason;

        private SubjectGroup(String kind, String reasonOrRest) {
            this.kind = kind;
            this.rest = reasonOrRest;
            this.singleReason = kind == null ? reasonOrRest : null;
        }

        private void add(String subject, String reason) {
            subjects.add(subject);
            singleReason = subjects.size() == 1 ? reason : null;
        }

        private List<String> render(String continuationIndent) {
            if (singleReason != null) {
                return List.of(body(singleReason));
            }
            String head = C.WHITE + String.valueOf(subjects.size()) + " " + plural(kind) + C.DARK_GRAY + ": " + body(rest);
            return List.of(head, continuationIndent + subjectList());
        }

        private String subjectList() {
            int shown = Math.min(GROUP_SUBJECTS_SHOWN, subjects.size());
            StringBuilder list = new StringBuilder();
            for (int index = 0; index < shown; index++) {
                if (index > 0) {
                    list.append(C.DARK_GRAY).append(", ");
                }
                list.append(C.WHITE).append(subjects.get(index));
            }
            int hidden = subjects.size() - shown;
            if (hidden > 0) {
                list.append(C.DARK_GRAY).append(" ").append(moreLabel(hidden));
            }
            return list.toString();
        }
    }
}
