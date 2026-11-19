package art.arcane.iris.world.safeguard;

import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.ChatReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IrisSafeguard {
    private static final String GENERIC_LOCK_REASON =
            "An Iris startup check failed. Resolve the startup errors and restart the server.";

    private static Map<Task, CheckResult> results = Collections.emptyMap();
    private static Map<String, String> context = Collections.emptyMap();
    private static Map<String, List<String>> attachment = Collections.emptyMap();
    private static Mode mode = Mode.STABLE;
    private static int count = 0;

    private IrisSafeguard() {
    }

    public static void execute() {
        IrisStartupValidation.beginRuntimeValidation();
        List<Task> tasks = Tasks.getTasks();
        LinkedHashMap<Task, CheckResult> resultValues = new LinkedHashMap<>(tasks.size());
        LinkedHashMap<String, String> contextValues = new LinkedHashMap<>(tasks.size());
        LinkedHashMap<String, List<String>> attachmentValues = new LinkedHashMap<>(tasks.size());
        Mode currentMode = Mode.STABLE;
        String lockReason = null;
        int issueCount = 0;

        for (Task task : tasks) {
            CheckResult result;
            try {
                result = task.run();
            } catch (Throwable e) {
                IrisLogging.reportError("Iris startup check \"" + task.getId() + "\" failed to run.", e);
                Diagnostic diagnostic = new Diagnostic(Diagnostic.Logger.ERROR,
                        "Error while running task " + task.getId(), e);
                result = task.failureMode() == Mode.UNSTABLE
                        ? CheckResult.danger(failureLockReason(task), diagnostic)
                        : CheckResult.warning(diagnostic);
            }

            currentMode = currentMode.highest(result.mode());
            if (result.mode() == Mode.UNSTABLE && lockReason == null) {
                lockReason = result.lockReason();
            }
            resultValues.put(task, result);
            contextValues.put(task.getId(), result.mode().getId());

            List<String> lines = new ArrayList<>();
            for (Diagnostic diagnostic : result.diagnostics()) {
                Collections.addAll(lines, diagnostic.toString().split("\\n"));
            }
            attachmentValues.put(task.getId(), lines);

            if (result.mode() != Mode.STABLE) {
                issueCount++;
            }
        }

        results = Collections.unmodifiableMap(resultValues);
        context = Collections.unmodifiableMap(contextValues);
        attachment = Collections.unmodifiableMap(attachmentValues);
        mode = currentMode;
        count = issueCount;

        if (currentMode == Mode.UNSTABLE) {
            IrisStartupValidation.markRuntimeInvalid(lockReason == null ? GENERIC_LOCK_REASON : lockReason);
            return;
        }

        IrisStartupValidation.markRuntimeReady();
    }

    public static Mode mode() {
        return mode;
    }

    public static Map<String, String> asContext() {
        return context;
    }

    public static Map<String, List<String>> asAttachment() {
        return attachment;
    }

    public static String debugReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("Startup safeguard: ").append(mode.getId())
                .append(" (").append(count).append(" issues)");
        for (Map.Entry<String, String> entry : context.entrySet()) {
            builder.append('\n').append("  ").append(entry.getKey()).append(": ").append(entry.getValue());
            for (String line : attachment.getOrDefault(entry.getKey(), List.of())) {
                builder.append('\n').append("    ").append(line);
            }
        }
        return builder.toString();
    }

    public static void printReports() {
        switch (mode) {
            case STABLE -> {
            }
            case WARNING -> IrisLogging.warn(issueCountLine());
            case UNSTABLE -> IrisLogging.error(issueCountLine());
        }

        for (CheckResult value : results.values()) {
            if (value.mode() == Mode.STABLE) {
                continue;
            }
            // Without the stack trace: Diagnostic.Logger splits on newlines, so a trace became one log
            // record per frame at the diagnostic's own severity. Traces go through reportError.
            value.log(true, false);
        }
    }

    private static String issueCountLine() {
        return count == 1 ? "1 startup issue" : count + " startup issues";
    }

    public static void printFooter() {
        switch (mode) {
            case STABLE -> IrisLogging.info(ChatReport.Tone.SUCCESS.marked(ChatReport.Tone.SUCCESS.paint("Stable, no conflicts")));
            case WARNING -> warning();
            case UNSTABLE -> unstable();
        }
    }

    private static String failureLockReason(Task task) {
        String declared = task.failureLockReason();
        return declared == null || declared.isBlank() ? GENERIC_LOCK_REASON : declared;
    }

    // A log record carries a level, so a blank record renders as an empty [WARN] line and a rule of
    // dashes renders as a [SEVERE] one. Spacing belongs to a console, not to the server log.
    private static void warning() {
        ChatReport.of(ChatReport.Tone.WARNING, "Iris is running in Warning Mode")
                .item("Some startup checks need attention. The warnings above suggest fixes.")
                .item("Startup continues.")
                .build()
                .log();
    }

    private static void unstable() {
        ChatReport.of(ChatReport.Tone.ERROR, "Iris is running in Danger Mode")
                .item("Critical startup checks failed. Fix the errors above.")
                .build()
                .log();
        // No startup sleep: blocking the boot thread protected nothing — world creation and
        // player admission are already gated by IrisStartupValidation.
    }
}
