package art.arcane.iris.core.safeguard.task;

import art.arcane.iris.Iris;
import art.arcane.iris.util.common.format.C;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

public class Diagnostic {
    private final Logger logger;
    private final String message;
    private final Throwable exception;

    public Diagnostic(String message) {
        this(Logger.ERROR, message, null);
    }

    public Diagnostic(Logger logger, String message) {
        this(logger, message, null);
    }

    public Diagnostic(Logger logger, String message, Throwable exception) {
        this.logger = logger;
        this.message = message;
        this.exception = exception;
    }

    public Logger getLogger() {
        return logger;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getException() {
        return exception;
    }

    public void log() {
        log(true, false);
    }

    public void log(boolean withException) {
        log(withException, false);
    }

    public void log(boolean withException, boolean withStackTrace) {
        logger.print(render(withException, withStackTrace));
    }

    public String render() {
        return render(true, false);
    }

    public String render(boolean withException) {
        return render(withException, false);
    }

    public String render(boolean withException, boolean withStackTrace) {
        StringBuilder builder = new StringBuilder();
        builder.append(message);
        if (withException && exception != null) {
            builder.append(": ");
            builder.append(exception);
            if (withStackTrace) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(os);
                exception.printStackTrace(ps);
                ps.flush();
                builder.append("\n");
                builder.append(os);
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return C.strip(render());
    }

    public enum Logger {
        DEBUG(Iris::debug),
        RAW(Iris::msg),
        INFO(Iris::info),
        WARN(Iris::warn),
        ERROR(Iris::error);

        private final Consumer<String> logger;

        Logger(Consumer<String> logger) {
            this.logger = logger;
        }

        public void print(String message) {
            String[] lines = message.split("\\n");
            for (String line : lines) {
                logger.accept(line);
            }
        }

        public Diagnostic create(String message) {
            return create(message, null);
        }

        public Diagnostic create(String message, Throwable exception) {
            return new Diagnostic(this, message, exception);
        }
    }
}
