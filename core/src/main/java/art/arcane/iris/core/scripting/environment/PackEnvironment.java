package art.arcane.iris.core.scripting.environment;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.scripting.kotlin.environment.IrisPackExecutionEnvironment;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.math.RNG;
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

    EngineEnvironment with(@NonNull Engine engine);
}