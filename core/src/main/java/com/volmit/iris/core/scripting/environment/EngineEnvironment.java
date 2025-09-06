package com.volmit.iris.core.scripting.environment;

import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.core.scripting.kotlin.environment.IrisExecutionEnvironment;
import com.volmit.iris.engine.framework.Engine;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface EngineEnvironment extends PackEnvironment {
    static EngineEnvironment create(@NonNull Engine engine) {
        return new IrisExecutionEnvironment(engine);
    }

    @NonNull
    Engine getEngine();

    @Nullable
    Object spawnMob(@NonNull String script, @NonNull Location location);

    void postSpawnMob(@NonNull String script, @NonNull Location location, @NonNull Entity mob);

    void preprocessObject(@NonNull String script, @NonNull IrisRegistrant object);
}