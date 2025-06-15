package com.volmit.iris.util.sentry;

import com.volmit.iris.Iris;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

public class IrisLogger implements ILogger {
    @Override
    public void log(@NotNull SentryLevel level, @NotNull String message, @Nullable Object... args) {
        Iris.msg(String.format("%s: %s", level, String.format(message, args)));
    }

    @Override
    public void log(@NotNull SentryLevel level, @NotNull String message, @Nullable Throwable throwable) {
        if (throwable == null) {
            log(level, message);
        } else {
            Iris.msg(String.format("%s: %s\n%s", level, String.format(message, throwable), captureStackTrace(throwable)));
        }
    }

    @Override
    public void log(@NotNull SentryLevel level, @Nullable Throwable throwable, @NotNull String message, @Nullable Object... args) {
        if (throwable == null) {
            log(level, message, args);
        } else {
            Iris.msg(String.format("%s: %s\n%s", level, String.format(message, throwable), captureStackTrace(throwable)));
        }
    }

    @Override
    public boolean isEnabled(@Nullable SentryLevel level) {
        return true;
    }

    private @NotNull String captureStackTrace(@NotNull Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
