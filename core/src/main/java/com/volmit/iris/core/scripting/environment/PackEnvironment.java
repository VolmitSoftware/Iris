package com.volmit.iris.core.scripting.environment;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.scripting.kotlin.environment.IrisPackExecutionEnvironment;
import com.volmit.iris.util.math.RNG;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public interface PackEnvironment extends SimpleEnvironment {
    static PackEnvironment create(@NonNull IrisData data) {
        return new IrisPackExecutionEnvironment(data);
    }

    @NonNull
    IrisData getData();

    @Nullable
    Object createNoise(@NonNull String script, @NonNull RNG rng);
}