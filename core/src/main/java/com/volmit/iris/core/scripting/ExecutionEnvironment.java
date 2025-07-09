package com.volmit.iris.core.scripting;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.core.scripting.kotlin.environment.IrisExecutionEnvironment;
import com.volmit.iris.core.scripting.kotlin.environment.IrisPackExecutionEnvironment;
import com.volmit.iris.core.scripting.kotlin.environment.IrisSimpleExecutionEnvironment;
import com.volmit.iris.util.math.RNG;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@UtilityClass
public class ExecutionEnvironment {

    @NonNull
    public static Engine createEngine(@NonNull com.volmit.iris.engine.framework.Engine engine) {
        return new IrisExecutionEnvironment(engine);
    }

    @NonNull
    public static Pack createPack(@NonNull IrisData data) {
        return new IrisPackExecutionEnvironment(data);
    }

    @NonNull
    public static Simple createSimple() {
        return new IrisSimpleExecutionEnvironment();
    }

    public interface Simple {
        void configureProject(@NonNull File projectDir);

        void execute(@NonNull String script);

        void execute(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

        @Nullable
        Object evaluate(@NonNull String script);

        @Nullable
        Object evaluate(@NonNull String script, @NonNull Class<?> type, @Nullable Map<@NonNull String, Object> vars);

        default void close() {

        }
    }

    public interface Pack extends Simple {
        @NonNull
        IrisData getData();

        @Nullable
        Object createNoise(@NonNull String script, @NonNull RNG rng);
    }

    public interface Engine extends Pack {
        @NonNull
        com.volmit.iris.engine.framework.Engine getEngine();

        @Nullable
        Object spawnMob(@NonNull String script, @NonNull Location location);

        void postSpawnMob(@NonNull String script, @NonNull Location location, @NonNull Entity mob);

        void preprocessObject(@NonNull String script, @NonNull IrisRegistrant object);
    }
}
