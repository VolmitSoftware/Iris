import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.ExecOperations;
import org.gradle.workers.WorkAction;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public abstract class IrisModdedBuildAction implements WorkAction<IrisModdedBuildParameters> {
    private static final Logger LOGGER = Logging.getLogger(IrisModdedBuildAction.class);
    private static final int LOG_TAIL_BYTES = 16 * 1024;
    private static final int LOG_TAIL_LINES = 40;

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void execute() {
        IrisModdedBuildParameters parameters = getParameters();
        String label = parameters.getLabel().get();
        Path log = parameters.getLogFile().get().getAsFile().toPath();
        long started = System.nanoTime();
        LOGGER.lifecycle("{} started; log: {}", label, log);
        try {
            Files.createDirectories(log.getParent());
            try (OutputStream output = Files.newOutputStream(log)) {
                getExecOperations().exec(spec -> {
                    spec.setWorkingDir(parameters.getWorkingDirectory().get().getAsFile());
                    spec.commandLine(parameters.getCommandLine().get());
                    spec.environment(parameters.getEnvironment().get());
                    spec.setStandardOutput(output);
                    spec.setErrorOutput(output);
                }).assertNormalExitValue();
            }
            ModdedArtifactVerifier.verify(parameters.getArtifactFile().get().getAsFile(),
                    parameters.getRequiredEntries().get(), parameters.getForbiddenCoreClasses().get());
            ModdedArtifactVerifier.verifyJavaRuntime(parameters.getArtifactFile().get().getAsFile(),
                    parameters.getJavaRuntime().get());
            LOGGER.lifecycle("{} succeeded in {} ms", label,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (Exception failure) {
            appendFailure(log, failure);
            String details;
            try {
                details = logTail(log);
            } catch (IOException unreadable) {
                failure.addSuppressed(unreadable);
                details = "Log could not be read.";
            }
            LOGGER.error(label + " failed; log: " + log + System.lineSeparator() + details, failure);
            throw new GradleException(label + " failed; log: " + log, failure);
        }
    }

    static String logTail(Path log) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(log.toFile(), "r")) {
            long offset = Math.max(0L, input.length() - LOG_TAIL_BYTES);
            input.seek(offset);
            byte[] bytes = new byte[(int) (input.length() - offset)];
            input.readFully(bytes);
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R");
            return String.join(System.lineSeparator(), Arrays.copyOfRange(lines,
                    Math.max(0, lines.length - LOG_TAIL_LINES), lines.length));
        }
    }

    private static void appendFailure(Path log, Exception failure) {
        try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            failure.printStackTrace(output);
        } catch (IOException unwritable) {
            failure.addSuppressed(unwritable);
        }
    }
}
