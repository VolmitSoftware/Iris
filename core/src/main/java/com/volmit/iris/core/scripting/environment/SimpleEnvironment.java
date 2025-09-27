package com.volmit.iris.core.scripting.environment;

import com.volmit.iris.core.scripting.kotlin.environment.IrisSimpleExecutionEnvironment;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

public interface SimpleEnvironment {
    static SimpleEnvironment create() {
        return new IrisSimpleExecutionEnvironment();
    }

    static SimpleEnvironment create(@NonNull File projectDir) {
        return new IrisSimpleExecutionEnvironment(projectDir);
    }

    void configureProject();

    void execute(@NonNull String script);

    void execute(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

    @Nullable
    Object evaluate(@NonNull String script);

    @Nullable
    Object evaluate(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

    default void close() {

    }
}